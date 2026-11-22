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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class CaptureControllerTest {
  @TempDir Path dir;

  private static final RecorderSettings FAST = new RecorderSettings(4096, 20, 40, 100, 1024, 1000);

  private CaptureController controller() {
    return new CaptureController(new RecordingStorage(dir, ZoneOffset.UTC), FAST, System::nanoTime);
  }

  private static byte[] b(int... v) {
    byte[] out = new byte[v.length];
    for (int i = 0; i < v.length; i++) {
      out[i] = (byte) v[i];
    }
    return out;
  }

  private static List<Frame> read(Path archive) throws Exception {
    try (RecordingArchive a = RecordingArchive.open(archive);
        FrameStreamReader r = a.openStream()) {
      return r.readAll();
    }
  }

  private static RecordingMetadata meta(Path archive) throws Exception {
    try (RecordingArchive a = RecordingArchive.open(archive)) {
      return a.metadata();
    }
  }

  @Test
  void midSessionStartWritesCachedConfigurationAndLoginAsPrelude() throws Exception {
    CaptureController c = controller();
    c.onPacket(ProtocolPhase.CONFIGURATION, b(1));
    c.onPacket(ProtocolPhase.CONFIGURATION, b(2));
    c.onPacket(ProtocolPhase.PLAY, b(10)); // login
    c.onPacket(ProtocolPhase.PLAY, b(11)); // chunk, not cached
    c.onPacket(ProtocolPhase.PLAY, b(12));

    c.start(RecordingMetadata.create("mid", 0));
    c.onPacket(ProtocolPhase.PLAY, b(13));
    Path archive = c.stop().get(10, TimeUnit.SECONDS);

    List<Frame> frames = read(archive);
    assertEquals(5, frames.size());
    assertArrayEquals(b(1), frames.get(0).packetBytes());
    assertArrayEquals(b(2), frames.get(1).packetBytes());
    assertArrayEquals(b(10), frames.get(2).packetBytes());
    assertEquals(FrameType.LIVE_START, frames.get(3).type());
    assertArrayEquals(b(13), frames.get(4).packetBytes());
    assertFalse(meta(archive).completeFromJoin, "chunks sent before start are missing");
  }

  @Test
  void startDuringConfigurationIsComplete() throws Exception {
    CaptureController c = controller();
    c.onPacket(ProtocolPhase.CONFIGURATION, b(1));
    c.start(RecordingMetadata.create("join", 0));
    c.onPacket(ProtocolPhase.CONFIGURATION, b(2));
    c.onPacket(ProtocolPhase.PLAY, b(10));
    Path archive = c.stop().get(10, TimeUnit.SECONDS);
    List<Frame> frames = read(archive);
    // prelude(1), LIVE_START, 2, 10
    assertEquals(4, frames.size());
    assertTrue(meta(archive).completeFromJoin);
  }

  @Test
  void newConfigurationPhaseResetsCache() {
    CaptureController c = controller();
    c.onPacket(ProtocolPhase.CONFIGURATION, b(1));
    c.onPacket(ProtocolPhase.PLAY, b(10));
    c.onPacket(ProtocolPhase.PLAY, b(11));
    assertEquals(2, c.cachedFrames());
    // Proxy server switch: back to configuration.
    c.onPacket(ProtocolPhase.CONFIGURATION, b(5));
    assertEquals(1, c.cachedFrames());
    c.onDisconnect();
    assertEquals(0, c.cachedFrames());
    assertFalse(c.canRecord());
  }

  @Test
  void oversizedCacheIsDroppedAndRecordingMarkedIncomplete() throws Exception {
    CaptureController c = controller();
    byte[] big = new byte[1024 * 1024];
    for (long sent = 0; sent <= CaptureController.MAX_CACHE_BYTES; sent += big.length) {
      c.onPacket(ProtocolPhase.CONFIGURATION, big);
    }
    assertEquals(0, c.cachedFrames());
    c.start(RecordingMetadata.create("big", 0));
    Path archive = c.stop().get(10, TimeUnit.SECONDS);
    assertFalse(meta(archive).completeFromJoin);
  }

  @Test
  void onlyOneSessionAtATime() throws Exception {
    CaptureController c = controller();
    c.onPacket(ProtocolPhase.CONFIGURATION, b(1));
    c.start(RecordingMetadata.create("a", 0));
    assertThrows(IllegalStateException.class, () -> c.start(RecordingMetadata.create("b", 0)));
    c.stop().get(10, TimeUnit.SECONDS);
    assertNull(c.session());
    assertNull(c.stop());
  }
}
