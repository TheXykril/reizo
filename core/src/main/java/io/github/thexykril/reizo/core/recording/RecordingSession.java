/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.recording;

import io.github.thexykril.reizo.core.format.Frame;
import io.github.thexykril.reizo.core.format.FrameStreamWriter;
import io.github.thexykril.reizo.core.format.FrameType;
import io.github.thexykril.reizo.core.format.ProtocolPhase;
import io.github.thexykril.reizo.core.format.RecordingMetadata;
import io.github.thexykril.reizo.core.storage.RecordingStorage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * One recording from start to stop.
 *
 * <p><b>Threading.</b> Any thread (the network thread, the client thread) may call {@link #submit}
 * and the control methods. Frames are timestamped and enqueued under one lock, so stream order and
 * timestamp order always agree. A dedicated writer thread owns all file I/O; game threads never
 * touch the disk. If the queue stays full for {@link RecorderSettings#submitTimeoutMs()}, the frame
 * is dropped and counted rather than stalling the game.
 *
 * <p><b>Pause semantics.</b> Pausing freezes the recording clock but keeps capturing packets. The
 * world state changes that happen during a pause are therefore applied instantly at the resume
 * point, so playback stays consistent. Dropping packets instead would desynchronize the world.
 *
 * <p><b>Lifecycle.</b> RECORDING ⇄ PAUSED → STOPPING → FINISHED, or FAILED on an I/O error. A
 * failed session leaves its working directory behind for crash recovery.
 */
public final class RecordingSession {
  /** Session lifecycle states. */
  public enum State {
    RECORDING,
    PAUSED,
    STOPPING,
    FINISHED,
    FAILED
  }

  private static final Frame STOP_SENTINEL = new Frame(0, Integer.MAX_VALUE, new byte[0]);

  private final RecordingStorage storage;
  private final RecordingStorage.WorkingDirectory working;
  private final RecordingMetadata meta;
  private final RecorderSettings settings;
  private final LongSupplier nanoClock;
  private final BlockingQueue<Frame> queue;
  private final ReentrantLock orderLock = new ReentrantLock();
  private final CompletableFuture<Path> result = new CompletableFuture<>();
  private final Thread writerThread;
  private final AtomicLong dropped = new AtomicLong();
  private final AtomicLong submitted = new AtomicLong();

  // Guarded by orderLock.
  private State state = State.RECORDING;
  private long accumulatedNanos;
  private long runningSinceNanos;

  // Written by the writer thread, read by anyone.
  private volatile long bytesOnDisk;
  private volatile Throwable failure;

  private RecordingSession(
      RecordingStorage storage,
      RecordingStorage.WorkingDirectory working,
      RecordingMetadata meta,
      RecorderSettings settings,
      LongSupplier nanoClock) {
    this.storage = storage;
    this.working = working;
    this.meta = meta;
    this.settings = settings;
    this.nanoClock = nanoClock;
    this.queue = new ArrayBlockingQueue<>(settings.queueCapacity());
    this.writerThread = new Thread(this::writerLoop, "Recorder-Writer-" + meta.id);
    this.writerThread.setDaemon(true);
    this.writerThread.setPriority(Thread.NORM_PRIORITY - 1);
  }

  /**
   * Starts a recording.
   *
   * @param prelude frames that describe state received before the recording started (for example
   *     cached configuration packets). They are written at time 0, followed by a {@link
   *     FrameType#LIVE_START} frame. Their own timestamps are ignored.
   */
  public static RecordingSession start(
      RecordingStorage storage,
      RecordingMetadata meta,
      RecorderSettings settings,
      List<Frame> prelude,
      LongSupplier nanoClock)
      throws IOException {
    Objects.requireNonNull(meta, "meta");
    meta.state = RecordingMetadata.State.RECORDING;
    RecordingStorage.WorkingDirectory working = storage.createWorking(meta);
    FrameStreamWriter writer;
    try {
      writer = FrameStreamWriter.create(working.streamFile(), settings.blockBytes());
      for (Frame f : prelude) {
        writer.write(new Frame(0, f.typeId(), f.payload()));
      }
      writer.write(Frame.of(0, FrameType.LIVE_START, new byte[0]));
      writer.flushBlock();
    } catch (IOException | RuntimeException e) {
      working.close();
      throw e;
    }
    RecordingSession session = new RecordingSession(storage, working, meta, settings, nanoClock);
    session.runningSinceNanos = nanoClock.getAsLong();
    session.writer = writer;
    session.writerThread.start();
    return session;
  }

  public static RecordingSession start(
      RecordingStorage storage, RecordingMetadata meta, RecorderSettings settings)
      throws IOException {
    return start(storage, meta, settings, List.of(), System::nanoTime);
  }

  // Owned by the writer thread after start().
  private FrameStreamWriter writer;

  /** Records a clientbound packet. Returns false if the frame was not accepted. */
  public boolean submitPacket(ProtocolPhase phase, byte[] packetBytes) {
    return enqueue(FrameType.PACKET.id(), null, phase, packetBytes);
  }

  /** Records a frame of the given type. Returns false if the frame was not accepted. */
  public boolean submit(FrameType type, byte[] payload) {
    return enqueue(type.id(), payload, null, null);
  }

  /** Adds a marker at the current recording time. */
  public boolean addMarker(String label) {
    return submit(FrameType.MARKER, label.getBytes(StandardCharsets.UTF_8));
  }

  private boolean enqueue(int typeId, byte[] payload, ProtocolPhase phase, byte[] packetBytes) {
    orderLock.lock();
    try {
      if (state != State.RECORDING && state != State.PAUSED) {
        return false;
      }
      int time = (int) Math.min(Integer.MAX_VALUE, elapsedNanosLocked() / 1_000_000L);
      Frame frame =
          phase != null ? Frame.packet(time, phase, packetBytes) : new Frame(time, typeId, payload);
      boolean accepted =
          settings.submitTimeoutMs() == 0
              ? queue.offer(frame)
              : queue.offer(frame, settings.submitTimeoutMs(), TimeUnit.MILLISECONDS);
      if (accepted) {
        submitted.incrementAndGet();
      } else {
        dropped.incrementAndGet();
      }
      return accepted;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      dropped.incrementAndGet();
      return false;
    } finally {
      orderLock.unlock();
    }
  }

  /**
   * Stores a PNG preview that is packed into the finished recording. Ignored once the session is
   * stopping, so it never races the archive being written.
   *
   * @return whether the thumbnail was stored
   */
  public boolean saveThumbnail(byte[] png) throws IOException {
    Objects.requireNonNull(png, "png");
    if (png.length > io.github.thexykril.reizo.core.storage.RecordingArchive.MAX_THUMBNAIL_BYTES) {
      return false;
    }
    orderLock.lock();
    try {
      if (state != State.RECORDING && state != State.PAUSED) {
        return false;
      }
      io.github.thexykril.reizo.core.util.AtomicFiles.write(working.thumbnailFile(), png);
      return true;
    } finally {
      orderLock.unlock();
    }
  }

  /** Freezes the recording clock. Packets keep being captured (see class docs). */
  public void pause() {
    orderLock.lock();
    try {
      if (state == State.RECORDING) {
        accumulatedNanos += nanoClock.getAsLong() - runningSinceNanos;
        state = State.PAUSED;
      }
    } finally {
      orderLock.unlock();
    }
  }

  public void resume() {
    orderLock.lock();
    try {
      if (state == State.PAUSED) {
        runningSinceNanos = nanoClock.getAsLong();
        state = State.RECORDING;
      }
    } finally {
      orderLock.unlock();
    }
  }

  /**
   * Stops accepting frames. The writer thread drains the queue, closes the stream and packages the
   * archive in the background; the returned future completes with the archive path. Calling stop
   * again returns the same future.
   */
  public CompletableFuture<Path> stop() {
    orderLock.lock();
    try {
      if (state == State.RECORDING || state == State.PAUSED) {
        if (state == State.RECORDING) {
          accumulatedNanos += nanoClock.getAsLong() - runningSinceNanos;
        }
        state = State.STOPPING;
        // The sentinel must get in even if the queue is full; the writer is draining it.
        // Stop spinning if the writer died; it will never drain the queue then.
        while (!queue.offer(STOP_SENTINEL) && failure == null) {
          Thread.onSpinWait();
        }
      }
      return result;
    } finally {
      orderLock.unlock();
    }
  }

  public State state() {
    orderLock.lock();
    try {
      return state;
    } finally {
      orderLock.unlock();
    }
  }

  /** Recording time so far, excluding pauses. */
  public long elapsedMillis() {
    orderLock.lock();
    try {
      return elapsedNanosLocked() / 1_000_000L;
    } finally {
      orderLock.unlock();
    }
  }

  private long elapsedNanosLocked() {
    return state == State.RECORDING
        ? accumulatedNanos + (nanoClock.getAsLong() - runningSinceNanos)
        : accumulatedNanos;
  }

  public long droppedFrames() {
    return dropped.get();
  }

  public long submittedFrames() {
    return submitted.get();
  }

  public long bytesOnDisk() {
    return bytesOnDisk;
  }

  public String id() {
    return meta.id;
  }

  public Throwable failure() {
    return failure;
  }

  public CompletableFuture<Path> result() {
    return result;
  }

  // ---- writer thread ----

  private void writerLoop() {
    long now = System.nanoTime();
    long nextFlush = now + ms(settings.flushIntervalMs());
    long nextSync = now + ms(settings.syncIntervalMs());
    long nextMeta = now + ms(settings.metadataIntervalMs());
    try {
      while (true) {
        long waitNanos = Math.max(0, Math.min(nextFlush, nextMeta) - System.nanoTime());
        Frame frame = queue.poll(waitNanos, TimeUnit.NANOSECONDS);
        if (frame == STOP_SENTINEL) {
          break;
        }
        if (frame != null) {
          writeFrame(frame);
          // Drain whatever else is ready without timed waits.
          Frame next;
          while ((next = queue.poll()) != null) {
            if (next == STOP_SENTINEL) {
              finish();
              return;
            }
            writeFrame(next);
          }
        }
        now = System.nanoTime();
        if (now >= nextFlush) {
          writer.flushBlock();
          bytesOnDisk = writer.bytesOnDisk();
          nextFlush = now + ms(settings.flushIntervalMs());
        }
        if (now >= nextSync) {
          writer.sync();
          nextSync = now + ms(settings.syncIntervalMs());
        }
        if (now >= nextMeta) {
          refreshMetadata();
          working.writeMetadata(meta);
          nextMeta = now + ms(settings.metadataIntervalMs());
        }
      }
      finish();
    } catch (Throwable t) {
      fail(t);
    }
  }

  private void writeFrame(Frame frame) throws IOException {
    writer.write(frame);
    if (frame.typeId() == FrameType.MARKER.id()) {
      meta.markers.add(
          new RecordingMetadata.Marker(
              frame.timeMs(), new String(frame.payload(), StandardCharsets.UTF_8)));
    }
  }

  private void refreshMetadata() {
    meta.frameCount = writer.framesWritten();
    meta.durationMs = writer.lastTimeMs();
    meta.droppedFrames = dropped.get();
  }

  private void finish() throws IOException {
    writer.sync();
    bytesOnDisk = writer.bytesOnDisk();
    writer.close();
    refreshMetadata();
    orderLock.lock();
    try {
      meta.durationMs =
          (int)
              Math.min(Integer.MAX_VALUE, Math.max(meta.durationMs, accumulatedNanos / 1_000_000));
    } finally {
      orderLock.unlock();
    }
    meta.state = RecordingMetadata.State.COMPLETE;
    working.writeMetadata(meta);
    working.close();
    Path archive = storage.finalizeRecording(working.dir());
    setState(State.FINISHED);
    result.complete(archive);
  }

  private void fail(Throwable t) {
    failure = t;
    // Clear before taking the lock: a stop() call may be waiting for queue space while holding it.
    queue.clear();
    setState(State.FAILED);
    try {
      writer.close();
    } catch (Throwable suppressed) {
      t.addSuppressed(suppressed);
    }
    try {
      // Releasing the lock lets crash recovery salvage the directory later.
      working.close();
    } catch (Throwable suppressed) {
      t.addSuppressed(suppressed);
    }
    result.completeExceptionally(t);
  }

  private void setState(State newState) {
    orderLock.lock();
    try {
      state = newState;
    } finally {
      orderLock.unlock();
    }
  }

  /** Waits for the writer thread to end. For tests and orderly shutdown. */
  public boolean awaitTermination(long timeoutMs) throws InterruptedException {
    writerThread.join(timeoutMs);
    return !writerThread.isAlive();
  }

  private static long ms(long millis) {
    return millis * 1_000_000L;
  }
}
