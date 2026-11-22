/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.recording;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thexykril.reizo.core.format.Frame;
import io.github.thexykril.reizo.core.format.FrameStreamReader;
import io.github.thexykril.reizo.core.format.FrameType;
import io.github.thexykril.reizo.core.format.ProtocolPhase;
import io.github.thexykril.reizo.core.format.RecordingMetadata;
import io.github.thexykril.reizo.core.storage.RecordingArchive;
import io.github.thexykril.reizo.core.storage.RecordingStorage;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class ShadowBufferTest {
  private static final long S = 1_000_000_000L;
  private static final RecorderSettings FAST = new RecorderSettings(4096, 20, 40, 100, 1024, 1000);

  @TempDir Path dir;
  private final AtomicLong now = new AtomicLong(100 * S);

  private static byte[] b(int... v) {
    byte[] out = new byte[v.length];
    for (int i = 0; i < v.length; i++) {
      out[i] = (byte) v[i];
    }
    return out;
  }

  private void advance(long seconds) {
    now.addAndGet(seconds * S);
  }

  @Test
  void segmentLengthIsAQuarterOfTheWindowWithinBounds() {
    assertEquals(15, ShadowBuffer.segmentSecondsFor(30));
    assertEquals(60, ShadowBuffer.segmentSecondsFor(120));
    assertEquals(120, ShadowBuffer.segmentSecondsFor(600));
    assertEquals(120, new ShadowBuffer(120, 60, 1, now::get).windowSeconds());
    assertThrows(IllegalArgumentException.class, () -> new ShadowBuffer(0, 10, 1, now::get));
  }

  @Test
  void ignoresFramesBeforeTheFirstSegment() {
    ShadowBuffer buf = new ShadowBuffer(60, 15, 1 << 20, now::get);
    assertTrue(buf.needsSegment());
    buf.onPacket(ProtocolPhase.PLAY, b(1));
    assertTrue(buf.isEmpty());
    assertNull(buf.contents());
    buf.startSegment(List.of(b(9)));
    assertFalse(buf.needsSegment());
    advance(15);
    assertTrue(buf.needsSegment());
  }

  @Test
  void keepsAtLeastTheWindowAndDropsSegmentsBeyondIt() {
    ShadowBuffer buf = new ShadowBuffer(60, 15, 1 << 20, now::get);
    for (int seg = 0; seg < 10; seg++) {
      buf.startSegment(List.of(b(100 + seg)));
      for (int i = 0; i < 15; i++) {
        buf.onPacket(ProtocolPhase.PLAY, b(seg, i));
        advance(1);
      }
    }
    // Now 150 s after the first segment. The buffer covers the window plus at most one segment.
    long seconds = buf.bufferedSeconds();
    assertTrue(seconds >= 60 && seconds <= 75, "buffered " + seconds);
    ShadowBuffer.Contents c = buf.contents();
    assertEquals(seconds, c.durationMillis() / 1000);
    // The save starts with the snapshot of the oldest kept segment, and has every frame since.
    int firstSeg = c.entries().get(0).bytes()[0];
    assertArrayEquals(b(100 + firstSeg), c.snapshot().get(0));
    assertEquals((10 - firstSeg) * 15, c.entries().size());
    assertEquals(0, c.entries().get(0).bytes()[1]);
  }

  @Test
  void memoryBudgetDropsOldSegmentsButKeepsTheNewest() {
    ShadowBuffer buf = new ShadowBuffer(600, 15, 2_000, now::get);
    for (int seg = 0; seg < 4; seg++) {
      buf.startSegment(List.of(new byte[100]));
      for (int i = 0; i < 10; i++) {
        buf.onPacket(ProtocolPhase.PLAY, new byte[100]);
      }
      advance(15);
    }
    assertTrue(buf.bytes() <= 2_000 || buf.contents().entries().size() <= 10, "" + buf.bytes());
    assertEquals(10, buf.contents().entries().size(), "only the newest segment fits");
    buf.clear();
    assertTrue(buf.isEmpty());
    assertEquals(0, buf.bytes());
  }

  @Test
  void savedThroughControllerWithPreludeSnapshotAndTimes() throws Exception {
    CaptureController c =
        new CaptureController(new RecordingStorage(dir, ZoneOffset.UTC), FAST, now::get);
    ShadowBuffer buf = new ShadowBuffer(30, 15, 1 << 20, now::get);
    c.setShadow(buf);
    c.onPacket(ProtocolPhase.CONFIGURATION, b(1));
    c.onPacket(ProtocolPhase.PLAY, b(10)); // login, cached
    c.onPacket(ProtocolPhase.PLAY, b(11)); // before the first segment: not buffered
    buf.startSegment(List.of(b(20), b(21)));
    advance(2);
    c.onPacket(ProtocolPhase.PLAY, b(12));
    advance(1);
    buf.onFrame(FrameType.MARKER, "m".getBytes());
    advance(1);

    Path archive = c.saveShadow(RecordingMetadata.create("shadow", 0)).get(10, TimeUnit.SECONDS);
    List<Frame> frames;
    RecordingMetadata meta;
    try (RecordingArchive a = RecordingArchive.open(archive);
        FrameStreamReader r = a.openStream()) {
      frames = r.readAll();
      meta = a.metadata();
    }
    // config 1, login 10, snapshot 20 21, LIVE_START, 12 at 2 s, marker at 3 s
    assertEquals(7, frames.size(), frames.toString());
    assertArrayEquals(b(1), frames.get(0).packetBytes());
    assertArrayEquals(b(10), frames.get(1).packetBytes());
    assertArrayEquals(b(20), frames.get(2).packetBytes());
    assertArrayEquals(b(21), frames.get(3).packetBytes());
    assertEquals(FrameType.LIVE_START, frames.get(4).type());
    assertArrayEquals(b(12), frames.get(5).packetBytes());
    assertEquals(2000, frames.get(5).timeMs());
    assertEquals(FrameType.MARKER, frames.get(6).type());
    assertEquals(3000, frames.get(6).timeMs());
    assertTrue(meta.startSnapshot);
    assertFalse(meta.completeFromJoin);
    assertEquals(4000, meta.durationMs);
  }

  @Test
  void newConnectionClearsTheBuffer() throws Exception {
    CaptureController c =
        new CaptureController(new RecordingStorage(dir, ZoneOffset.UTC), FAST, now::get);
    ShadowBuffer buf = new ShadowBuffer(30, 15, 1 << 20, now::get);
    c.setShadow(buf);
    assertNull(c.saveShadow(RecordingMetadata.create("none", 0)), "nothing connected yet");
    c.onPacket(ProtocolPhase.CONFIGURATION, b(1));
    c.onPacket(ProtocolPhase.PLAY, b(10));
    buf.startSegment(List.of(b(20)));
    c.onPacket(ProtocolPhase.PLAY, b(12));
    c.onPacket(ProtocolPhase.CONFIGURATION, b(2)); // moved to another server
    assertTrue(buf.isEmpty());
    buf.startSegment(List.of(b(20)));
    c.onDisconnect();
    assertTrue(buf.isEmpty());
  }
}
