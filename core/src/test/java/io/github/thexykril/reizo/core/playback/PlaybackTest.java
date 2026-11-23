/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.playback;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thexykril.reizo.core.format.Frame;
import io.github.thexykril.reizo.core.format.FrameType;
import io.github.thexykril.reizo.core.format.ProtocolPhase;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PlaybackTest {
  private static final long MS = 1_000_000L;

  @Test
  void clockStartsPausedAndFollowsSpeed() {
    AtomicLong now = new AtomicLong(5_000 * MS);
    PlaybackClock clock = new PlaybackClock(now::get);
    now.addAndGet(100 * MS);
    assertEquals(0, clock.timeMs());
    assertFalse(clock.isRunning());

    clock.resume();
    now.addAndGet(100 * MS);
    assertEquals(100, clock.timeMs());

    clock.setSpeed(2.0);
    now.addAndGet(100 * MS);
    assertEquals(300, clock.timeMs());

    clock.pause();
    now.addAndGet(1_000 * MS);
    assertEquals(300, clock.timeMs());

    clock.resume();
    now.addAndGet(50 * MS);
    assertEquals(400, clock.timeMs());
    assertThrows(IllegalArgumentException.class, () -> clock.setSpeed(0));
  }

  @Test
  void clockSeeksKeepingRunStateAndSpeed() {
    AtomicLong now = new AtomicLong(0);
    PlaybackClock clock = new PlaybackClock(now::get);
    clock.seek(5_000);
    now.addAndGet(100 * MS);
    assertEquals(5_000, clock.timeMs(), "paused clock stays at the seek target");
    clock.resume();
    clock.setSpeed(2.0);
    now.addAndGet(100 * MS);
    assertEquals(5_200, clock.timeMs());
    clock.seek(1_000);
    now.addAndGet(100 * MS);
    assertEquals(1_200, clock.timeMs(), "running clock continues from the target at speed");
    clock.seek(-5);
    assertEquals(0, clock.timeMs());
  }

  @Test
  void scriptRewindsForBackwardSeeks() {
    ReplayScript script =
        ReplayScript.of(
            List.of(
                Frame.packet(0, ProtocolPhase.CONFIGURATION, new byte[] {1}),
                Frame.packet(0, ProtocolPhase.PLAY, new byte[] {10}),
                Frame.packet(500, ProtocolPhase.PLAY, new byte[] {11})));
    assertEquals(2, script.due(1_000).size());
    assertTrue(script.finished());
    script.rewind();
    assertFalse(script.finished());
    assertEquals(1, script.due(100).size());
    assertEquals(1, script.due(600).size());
  }

  @Test
  void scriptStartsAtTheFirstPlayPacket() {
    // The clock starts with the configuration phase; the world arrives later.
    ReplayScript script =
        ReplayScript.of(
            List.of(
                Frame.packet(0, ProtocolPhase.CONFIGURATION, new byte[] {1}),
                Frame.packet(800, ProtocolPhase.PLAY, new byte[] {10}),
                Frame.packet(2000, ProtocolPhase.PLAY, new byte[] {11})));
    assertEquals(800, script.startMs());
    assertEquals(0, script.due(700).size(), "nothing to show before the world starts");
  }

  @Test
  void scriptSplitsPhasesAndReleasesPacketsByTime() {
    List<Frame> frames =
        List.of(
            Frame.of(0, FrameType.LOCAL_PLAYER, new byte[] {9}),
            Frame.packet(0, ProtocolPhase.CONFIGURATION, new byte[] {1}),
            Frame.packet(0, ProtocolPhase.CONFIGURATION, new byte[] {2}),
            Frame.packet(0, ProtocolPhase.PLAY, new byte[] {10}),
            Frame.of(0, FrameType.LIVE_START, new byte[0]),
            Frame.packet(50, ProtocolPhase.PLAY, new byte[] {11}),
            Frame.of(55, FrameType.LOCAL_PLAYER, new byte[] {7}),
            Frame.of(58, FrameType.PLAYER_HUD, new byte[] {8}),
            Frame.of(59, FrameType.PLAYER_SCREEN, new byte[] {6}),
            Frame.of(60, FrameType.MARKER, new byte[] {0}),
            Frame.packet(100, ProtocolPhase.PLAY, new byte[] {12}));
    ReplayScript script = ReplayScript.of(frames);

    assertEquals(2, script.configuration().size());
    assertArrayEquals(new byte[] {2}, script.configuration().get(1));
    assertEquals(3, script.playPacketCount());
    assertEquals(100, script.durationMs());

    List<ReplayScript.Entry> first = script.due(0);
    assertEquals(1, first.size());
    assertArrayEquals(new byte[] {10}, first.get(0).bytes());
    assertEquals(0, script.due(49).size());
    List<ReplayScript.Entry> rest = script.due(1_000);
    assertEquals(5, rest.size());
    assertEquals(ReplayScript.Kind.LOCAL_PLAYER, rest.get(1).kind());
    assertArrayEquals(new byte[] {7}, rest.get(1).bytes());
    assertEquals(ReplayScript.Kind.PLAYER_HUD, rest.get(2).kind());
    assertArrayEquals(new byte[] {8}, rest.get(2).bytes());
    assertEquals(ReplayScript.Kind.PLAYER_SCREEN, rest.get(3).kind());
    assertTrue(script.finished());
  }

  @Test
  void serverSwitchBecomesAReconfigureEntry() {
    List<Frame> frames =
        List.of(
            Frame.packet(0, ProtocolPhase.CONFIGURATION, new byte[] {1}),
            Frame.packet(0, ProtocolPhase.PLAY, new byte[] {10}),
            Frame.packet(40, ProtocolPhase.PLAY, new byte[] {11}),
            Frame.packet(50, ProtocolPhase.CONFIGURATION, new byte[] {3}),
            Frame.of(55, FrameType.LOCAL_PLAYER, new byte[] {7}),
            Frame.packet(60, ProtocolPhase.CONFIGURATION, new byte[] {4}),
            Frame.packet(70, ProtocolPhase.PLAY, new byte[] {12}),
            Frame.packet(90, ProtocolPhase.CONFIGURATION, new byte[] {5}));
    ReplayScript script = ReplayScript.of(frames);
    assertEquals(2, script.due(45).size());
    assertEquals(ReplayScript.Kind.RECONFIGURE, script.due(50).get(0).kind());
    List<ReplayScript.Entry> login = script.throughNextPlayPacket();
    assertEquals(1, login.size());
    assertArrayEquals(new byte[] {12}, login.get(0).bytes(), "the new world's first packet");
    assertTrue(script.finished());
    script.rewind();
    assertEquals(1, script.configuration().size(), "only the first phase is sent up front");
    assertEquals(1, script.reconfigureCount());
    assertEquals(3, script.playPacketCount());
    List<ReplayScript.Entry> all = script.due(1_000);
    // The sample while configuring (no world) and the trailing phase without a world are left out.
    assertEquals(4, all.size());
    assertEquals(ReplayScript.Kind.RECONFIGURE, all.get(2).kind());
    assertEquals(50, all.get(2).timeMs());
    assertEquals(2, all.get(2).configuration().size());
    assertArrayEquals(new byte[] {4}, all.get(2).configuration().get(1));
    assertArrayEquals(new byte[] {12}, all.get(3).bytes());
  }

  @Test
  void scriptRejectsRecordingsWithoutPlayPackets() {
    List<Frame> frames = List.of(Frame.packet(0, ProtocolPhase.CONFIGURATION, new byte[] {1}));
    assertThrows(IllegalArgumentException.class, () -> ReplayScript.of(frames));
  }
}
