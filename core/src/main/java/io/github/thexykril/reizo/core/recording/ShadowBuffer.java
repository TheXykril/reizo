/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.recording;

import io.github.thexykril.reizo.core.format.Frame;
import io.github.thexykril.reizo.core.format.FrameType;
import io.github.thexykril.reizo.core.format.ProtocolPhase;
import io.github.thexykril.reizo.core.format.RecordingMetadata;
import io.github.thexykril.reizo.core.storage.RecordingStorage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Shadow recording: keeps the last few minutes of play in memory so they can be saved after the
 * fact, like a replay buffer (ADR 0011).
 *
 * <p>A recording must start with the world as it was, so the buffer is a chain of segments. Each
 * segment starts with a snapshot of the client's world (supplied by the game adapter) followed by
 * every frame until the next segment. Old segments are dropped once the segment after them already
 * covers the window, so the buffer always holds at least the window and at most the window plus one
 * segment. Saving takes the oldest segment's snapshot and every frame since, and writes them
 * through a normal {@link RecordingSession} whose clock replays the frames' capture times.
 *
 * <p>Thread-safe: frames arrive on the network and client threads.
 */
public final class ShadowBuffer {
  /** A buffered frame: a packet (phase set) or another frame type. */
  record Entry(long nanos, int typeId, ProtocolPhase phase, byte[] bytes) {
    long size() {
      return bytes.length + 48L;
    }
  }

  private static final class Segment {
    final long startNanos;
    final List<byte[]> snapshot;
    final List<Entry> entries = new ArrayList<>();
    long bytes;

    Segment(long startNanos, List<byte[]> snapshot) {
      this.startNanos = startNanos;
      this.snapshot = List.copyOf(snapshot);
      for (byte[] b : snapshot) {
        bytes += b.length + 16L;
      }
    }
  }

  /** What a save writes: the oldest segment's snapshot, then every frame after it. */
  public record Contents(
      List<byte[]> snapshot, long startNanos, List<Entry> entries, long endNanos) {
    public long durationMillis() {
      return (endNanos - startNanos) / 1_000_000L;
    }
  }

  private final long windowNanos;
  private final long segmentNanos;
  private final long maxBytes;
  private final LongSupplier clock;

  // Guarded by "this".
  private final Deque<Segment> segments = new ArrayDeque<>();
  private long totalBytes;

  /**
   * @param windowSeconds how much play a save covers at least
   * @param segmentSeconds how often a new segment (with a world snapshot) starts
   * @param maxBytes memory budget; the oldest segments go first when it is exceeded
   */
  public ShadowBuffer(int windowSeconds, int segmentSeconds, long maxBytes, LongSupplier clock) {
    if (windowSeconds <= 0 || segmentSeconds <= 0 || maxBytes <= 0) {
      throw new IllegalArgumentException("window, segment and budget must be positive");
    }
    this.windowNanos = windowSeconds * 1_000_000_000L;
    this.segmentNanos = segmentSeconds * 1_000_000_000L;
    this.maxBytes = maxBytes;
    this.clock = clock;
  }

  /**
   * Segment length for a window: half of it, between 15 and 120 seconds. Each segment costs one
   * world snapshot (a short hitch on the game thread and its memory), so segments are long; a save
   * then covers between the window and one and a half windows.
   */
  public static int segmentSecondsFor(int windowSeconds) {
    return Math.max(15, Math.min(120, windowSeconds / 2));
  }

  public int windowSeconds() {
    return (int) (windowNanos / 1_000_000_000L);
  }

  /** True when a new segment should start (none yet, or the current one is old enough). */
  public synchronized boolean needsSegment() {
    Segment last = segments.peekLast();
    return last == null || clock.getAsLong() - last.startNanos >= segmentNanos;
  }

  /** Starts a segment with a snapshot of the world as the client has it now. */
  public synchronized void startSegment(List<byte[]> snapshot) {
    Segment s = new Segment(clock.getAsLong(), snapshot);
    segments.addLast(s);
    totalBytes += s.bytes;
    trim();
  }

  /** Buffers a clientbound packet. Ignored until the first segment starts. */
  public void onPacket(ProtocolPhase phase, byte[] packetBytes) {
    add(FrameType.PACKET.id(), phase, packetBytes);
  }

  /** Buffers a frame of another type (local player, marker). */
  public void onFrame(FrameType type, byte[] payload) {
    add(type.id(), null, payload);
  }

  private synchronized void add(int typeId, ProtocolPhase phase, byte[] bytes) {
    Segment last = segments.peekLast();
    if (last == null) {
      return;
    }
    Entry e = new Entry(clock.getAsLong(), typeId, phase, bytes);
    last.entries.add(e);
    last.bytes += e.size();
    totalBytes += e.size();
    trim();
  }

  private void trim() {
    long now = clock.getAsLong();
    while (segments.size() > 1) {
      Segment oldest = segments.peekFirst();
      Segment next = secondSegment();
      boolean coveredWithout = now - next.startNanos >= windowNanos;
      if (!coveredWithout && totalBytes <= maxBytes) {
        break;
      }
      segments.removeFirst();
      totalBytes -= oldest.bytes;
    }
  }

  private Segment secondSegment() {
    var it = segments.iterator();
    it.next();
    return it.next();
  }

  /** Drops everything (new connection, shadow turned off). */
  public synchronized void clear() {
    segments.clear();
    totalBytes = 0;
  }

  public synchronized boolean isEmpty() {
    return segments.isEmpty();
  }

  /** Approximate memory held, in bytes. */
  public synchronized long bytes() {
    return totalBytes;
  }

  /** Seconds of play a save would contain now. */
  public synchronized long bufferedSeconds() {
    trim();
    Segment first = segments.peekFirst();
    return first == null ? 0 : (clock.getAsLong() - first.startNanos) / 1_000_000_000L;
  }

  /** A consistent copy of what a save would write, or null when nothing is buffered. */
  public synchronized Contents contents() {
    trim();
    Segment first = segments.peekFirst();
    if (first == null) {
      return null;
    }
    List<Entry> entries = new ArrayList<>();
    for (Segment s : segments) {
      entries.addAll(s.entries);
    }
    return new Contents(first.snapshot, first.startNanos, entries, clock.getAsLong());
  }

  /**
   * Writes buffered contents as a recording. The prelude (cached configuration and login packets of
   * the connection) comes first, then the snapshot, then the frames at their capture times relative
   * to the snapshot. Blocks while writing; run it off the game thread.
   *
   * <p>{@code meta.startedAtEpochMs} should be the time of the save; it is moved back by the
   * buffered duration so it names when the saved play began.
   *
   * @return the future of the packaged archive
   */
  public static CompletableFuture<Path> save(
      Contents contents,
      List<Frame> prelude,
      RecordingStorage storage,
      RecordingMetadata meta,
      RecorderSettings settings)
      throws IOException {
    List<Frame> start = new ArrayList<>(prelude);
    for (byte[] packet : contents.snapshot()) {
      start.add(Frame.packet(0, ProtocolPhase.PLAY, packet));
    }
    meta.startedAtEpochMs -= contents.durationMillis();
    meta.completeFromJoin = false;
    meta.startSnapshot = true;
    AtomicLong now = new AtomicLong(contents.startNanos());
    // Block instead of dropping when the writer is behind: this is not a live game thread.
    RecorderSettings blocking =
        new RecorderSettings(
            settings.blockBytes(),
            settings.flushIntervalMs(),
            settings.syncIntervalMs(),
            settings.metadataIntervalMs(),
            settings.queueCapacity(),
            60_000);
    RecordingSession session = RecordingSession.start(storage, meta, blocking, start, now::get);
    for (Entry e : contents.entries()) {
      now.set(e.nanos());
      if (e.phase() != null) {
        session.submitPacket(e.phase(), e.bytes());
      } else {
        session.submit(FrameType.byId(e.typeId()), e.bytes());
      }
    }
    now.set(contents.endNanos());
    return session.stop();
  }
}
