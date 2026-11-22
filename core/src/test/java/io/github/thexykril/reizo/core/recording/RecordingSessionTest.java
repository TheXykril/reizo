/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thexykril.reizo.core.format.Frame;
import io.github.thexykril.reizo.core.format.FrameStreamReader;
import io.github.thexykril.reizo.core.format.FrameType;
import io.github.thexykril.reizo.core.format.ProtocolPhase;
import io.github.thexykril.reizo.core.format.RecordingMetadata;
import io.github.thexykril.reizo.core.storage.RecordingArchive;
import io.github.thexykril.reizo.core.storage.RecordingStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class RecordingSessionTest {
  @TempDir Path dir;

  /** Manually advanced clock so timing assertions are deterministic. */
  static final class FakeClock {
    final AtomicLong nanos = new AtomicLong(1_000_000_000L);

    long get() {
      return nanos.get();
    }

    void advanceMs(long ms) {
      nanos.addAndGet(ms * 1_000_000L);
    }
  }

  private static final RecorderSettings FAST = new RecorderSettings(4096, 20, 40, 100, 1024, 1000);

  private RecordingStorage storage() {
    return new RecordingStorage(dir, ZoneOffset.UTC);
  }

  private static RecordingMetadata meta() {
    return RecordingMetadata.create("test", 1_700_000_000_000L);
  }

  private static List<Frame> readArchive(Path archive) throws IOException {
    try (RecordingArchive a = RecordingArchive.open(archive);
        FrameStreamReader reader = a.openStream()) {
      return reader.readAll();
    }
  }

  @Test
  void recordsFramesWithClockTimestampsAndFinalizes() throws Exception {
    FakeClock clock = new FakeClock();
    RecordingSession session =
        RecordingSession.start(storage(), meta(), FAST, List.of(), clock::get);
    session.submitPacket(ProtocolPhase.PLAY, new byte[] {1});
    clock.advanceMs(50);
    session.submitPacket(ProtocolPhase.PLAY, new byte[] {2});
    clock.advanceMs(25);
    session.addMarker("here");
    Path archive = session.stop().get(10, TimeUnit.SECONDS);

    assertEquals(RecordingSession.State.FINISHED, session.state());
    assertTrue(archive.getFileName().toString().endsWith("." + RecordingArchive.EXTENSION));
    List<Frame> frames = readArchive(archive);
    // LIVE_START, two packets, one marker
    assertEquals(4, frames.size());
    assertEquals(FrameType.LIVE_START, frames.get(0).type());
    assertEquals(0, frames.get(1).timeMs());
    assertEquals(50, frames.get(2).timeMs());
    assertEquals(75, frames.get(3).timeMs());

    try (RecordingArchive a = RecordingArchive.open(archive)) {
      RecordingMetadata m = a.metadata();
      assertEquals(RecordingMetadata.State.COMPLETE, m.state);
      assertEquals(75, m.durationMs);
      assertEquals(4, m.frameCount);
      assertEquals(1, m.markers.size());
      assertEquals(75, m.markers.get(0).timeMs);
      assertEquals("here", m.markers.get(0).label);
    }
    // Working directory is cleaned up after packaging.
    assertTrue(storage().findAbandoned().isEmpty());
    assertFalse(Files.exists(dir.resolve(RecordingStorage.WORKING_DIR).resolve(session.id())));
  }

  @Test
  void pauseFreezesTheClockButKeepsCapturing() throws Exception {
    FakeClock clock = new FakeClock();
    RecordingSession session =
        RecordingSession.start(storage(), meta(), FAST, List.of(), clock::get);
    clock.advanceMs(100);
    session.pause();
    assertEquals(RecordingSession.State.PAUSED, session.state());
    clock.advanceMs(10_000);
    assertTrue(session.submitPacket(ProtocolPhase.PLAY, new byte[] {9}));
    assertEquals(100, session.elapsedMillis());
    session.resume();
    clock.advanceMs(30);
    session.submitPacket(ProtocolPhase.PLAY, new byte[] {10});
    Path archive = session.stop().get(10, TimeUnit.SECONDS);

    List<Frame> frames = readArchive(archive);
    assertEquals(100, frames.get(1).timeMs());
    assertEquals(130, frames.get(2).timeMs());
    try (RecordingArchive a = RecordingArchive.open(archive)) {
      assertEquals(130, a.metadata().durationMs);
    }
  }

  @Test
  void preludeIsWrittenAtTimeZeroBeforeLiveStart() throws Exception {
    FakeClock clock = new FakeClock();
    List<Frame> prelude =
        List.of(
            Frame.packet(999, ProtocolPhase.CONFIGURATION, new byte[] {1}),
            Frame.packet(999, ProtocolPhase.CONFIGURATION, new byte[] {2}));
    RecordingSession session = RecordingSession.start(storage(), meta(), FAST, prelude, clock::get);
    Path archive = session.stop().get(10, TimeUnit.SECONDS);
    List<Frame> frames = readArchive(archive);
    assertEquals(3, frames.size());
    assertEquals(0, frames.get(0).timeMs());
    assertEquals(ProtocolPhase.CONFIGURATION, frames.get(0).packetPhase());
    assertEquals(FrameType.LIVE_START, frames.get(2).type());
  }

  @Test
  void rejectsFramesAfterStopAndStopIsIdempotent() throws Exception {
    RecordingSession session =
        RecordingSession.start(storage(), meta(), FAST, List.of(), System::nanoTime);
    CompletableFuture<Path> first = session.stop();
    assertSame(first, session.stop());
    assertFalse(session.submitPacket(ProtocolPhase.PLAY, new byte[] {1}));
    first.get(10, TimeUnit.SECONDS);
    assertTrue(session.awaitTermination(5000), "writer thread must exit");
  }

  @Test
  void concurrentSubmittersProduceOrderedCompleteStream() throws Exception {
    RecordingSession session =
        RecordingSession.start(
            storage(),
            meta(),
            new RecorderSettings(8192, 20, 40, 100, 256, 5000),
            List.of(),
            System::nanoTime);
    int threads = 4;
    int perThread = 2000;
    CountDownLatch go = new CountDownLatch(1);
    List<Thread> workers = new ArrayList<>();
    for (int t = 0; t < threads; t++) {
      final int id = t;
      Thread w =
          new Thread(
              () -> {
                try {
                  go.await();
                } catch (InterruptedException e) {
                  return;
                }
                for (int i = 0; i < perThread; i++) {
                  session.submitPacket(ProtocolPhase.PLAY, new byte[] {(byte) id, (byte) i});
                }
              });
      w.start();
      workers.add(w);
    }
    go.countDown();
    for (Thread w : workers) {
      w.join();
    }
    Path archive = session.stop().get(20, TimeUnit.SECONDS);
    assertEquals(0, session.droppedFrames());
    List<Frame> frames = readArchive(archive);
    assertEquals(threads * perThread + 1, frames.size());
    for (int i = 1; i < frames.size(); i++) {
      assertTrue(frames.get(i).timeMs() >= frames.get(i - 1).timeMs(), "monotonic time");
    }
  }

  @Test
  void metadataIsRefreshedWhileRecording() throws Exception {
    FakeClock clock = new FakeClock();
    RecordingSession session =
        RecordingSession.start(storage(), meta(), FAST, List.of(), clock::get);
    clock.advanceMs(500);
    session.submitPacket(ProtocolPhase.PLAY, new byte[] {1});
    Path metaFile =
        dir.resolve(RecordingStorage.WORKING_DIR)
            .resolve(session.id())
            .resolve(RecordingStorage.META_FILE);
    long deadline = System.currentTimeMillis() + 5000;
    RecordingMetadata onDisk;
    do {
      Thread.sleep(20);
      onDisk = RecordingMetadata.fromJson(Files.readString(metaFile));
    } while (onDisk.durationMs < 500 && System.currentTimeMillis() < deadline);
    assertEquals(500, onDisk.durationMs);
    assertEquals(RecordingMetadata.State.RECORDING, onDisk.state);
    session.stop().get(10, TimeUnit.SECONDS);
  }

  @Test
  void ioFailureFailsSessionAndLeavesDirectoryForRecovery() throws Exception {
    RecordingStorage storage = storage();
    RecordingSession session =
        RecordingSession.start(storage, meta(), FAST, List.of(), System::nanoTime);
    session.submitPacket(ProtocolPhase.PLAY, new byte[] {1});
    Thread.sleep(100);
    // Make packaging impossible: a non-empty directory where the temporary archive must go.
    Path working = dir.resolve(RecordingStorage.WORKING_DIR).resolve(session.id());
    Path blocker = dir.resolve("test_2023-11-14_22-13-20." + RecordingArchive.EXTENSION + ".tmp");
    Files.createDirectories(blocker.resolve("x"));
    CompletableFuture<Path> result = session.stop();
    assertTrue(
        assertThrowsExecution(result), "stop must complete exceptionally when finalizing fails");
    assertEquals(RecordingSession.State.FAILED, session.state());
    assertTrue(Files.isDirectory(working), "working directory must be kept");
    assertEquals(List.of(working), storage.findAbandoned(), "lock must be released");
  }

  private static boolean assertThrowsExecution(CompletableFuture<?> f) throws Exception {
    try {
      f.get(10, TimeUnit.SECONDS);
      return false;
    } catch (java.util.concurrent.ExecutionException e) {
      return true;
    }
  }
}
