/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.playback;

import io.github.thexykril.reizo.core.format.Frame;
import io.github.thexykril.reizo.core.format.FrameType;
import io.github.thexykril.reizo.core.format.ProtocolPhase;
import java.util.ArrayList;
import java.util.List;

/**
 * The content of a recording, split the way a replay uses it: the configuration phase first (all at
 * once, before the clock starts), then a timeline of play packets and local player samples.
 *
 * <p>Markers and other frame types are for the editor and are skipped here.
 */
public final class ReplayScript {
  /** One timeline entry: either a raw play packet or a local player sample. */
  public record Entry(int timeMs, Kind kind, byte[] bytes) {}

  public enum Kind {
    /** Raw play packet (id + body), as captured. */
    PLAY_PACKET,
    /** Encoded {@link io.github.thexykril.reizo.core.format.LocalPlayerState}. */
    LOCAL_PLAYER
  }

  private final List<byte[]> configuration;
  private final List<Entry> timeline;
  private final int playPacketCount;
  private int next;

  private ReplayScript(List<byte[]> configuration, List<Entry> timeline) {
    this.configuration = configuration;
    this.timeline = timeline;
    this.playPacketCount =
        (int) timeline.stream().filter(e -> e.kind() == Kind.PLAY_PACKET).count();
  }

  /**
   * Builds a script from a recording's frames, in stream order.
   *
   * @throws IllegalArgumentException if the recording has no configuration or no play packets
   */
  public static ReplayScript of(List<Frame> frames) {
    List<byte[]> configuration = new ArrayList<>();
    List<Entry> timeline = new ArrayList<>();
    boolean sawPlay = false;
    for (Frame f : frames) {
      FrameType type = f.type();
      if (type == FrameType.LOCAL_PLAYER) {
        if (sawPlay) {
          timeline.add(new Entry(f.timeMs(), Kind.LOCAL_PLAYER, f.payload()));
        }
        continue;
      }
      if (type != FrameType.PACKET) {
        continue;
      }
      if (f.packetPhase() == ProtocolPhase.CONFIGURATION) {
        if (sawPlay) {
          // A second configuration phase (server transfer) is not supported yet: stop there.
          break;
        }
        configuration.add(f.packetBytes());
      } else {
        sawPlay = true;
        timeline.add(new Entry(f.timeMs(), Kind.PLAY_PACKET, f.packetBytes()));
      }
    }
    if (configuration.isEmpty() || !sawPlay) {
      throw new IllegalArgumentException(
          "recording has no "
              + (configuration.isEmpty() ? "configuration" : "play")
              + " packets; it cannot be played back");
    }
    return new ReplayScript(List.copyOf(configuration), List.copyOf(timeline));
  }

  /** Configuration-phase packets (id + payload), in order. */
  public List<byte[]> configuration() {
    return configuration;
  }

  /** Number of play packets in the timeline. */
  public int playPacketCount() {
    return playPacketCount;
  }

  /** Time of the last timeline entry in milliseconds. */
  public long durationMs() {
    return timeline.get(timeline.size() - 1).timeMs();
  }

  /** True when every timeline entry has been handed out. */
  public synchronized boolean finished() {
    return next >= timeline.size();
  }

  /**
   * Returns the timeline entries due at {@code timeMs} that have not been handed out yet, in order.
   * Time never goes backwards here; seeking is a separate feature.
   */
  public synchronized List<Entry> due(long timeMs) {
    List<Entry> out = new ArrayList<>();
    while (next < timeline.size() && timeline.get(next).timeMs() <= timeMs) {
      out.add(timeline.get(next));
      next++;
    }
    return out;
  }
}
