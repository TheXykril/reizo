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
 * The packets of a recording, split the way a replay sends them: the configuration phase first (all
 * at once, before the clock starts), then play packets in time order.
 *
 * <p>Only {@link FrameType#PACKET} frames take part. Other frame types (local player samples,
 * markers) are for the editor and are skipped here.
 */
public final class ReplayScript {
  private final List<byte[]> configuration;
  private final List<Timed> play;
  private int next;

  private ReplayScript(List<byte[]> configuration, List<Timed> play) {
    this.configuration = configuration;
    this.play = play;
  }

  /**
   * Builds a script from a recording's frames, in stream order.
   *
   * @throws IllegalArgumentException if the recording has no configuration or no play packets
   */
  public static ReplayScript of(List<Frame> frames) {
    List<byte[]> configuration = new ArrayList<>();
    List<Timed> play = new ArrayList<>();
    for (Frame f : frames) {
      if (f.type() != FrameType.PACKET) {
        continue;
      }
      if (f.packetPhase() == ProtocolPhase.CONFIGURATION) {
        if (!play.isEmpty()) {
          // A second configuration phase (server transfer) is not supported yet: stop there.
          break;
        }
        configuration.add(f.packetBytes());
      } else {
        play.add(new Timed(f.timeMs(), f.packetBytes()));
      }
    }
    if (configuration.isEmpty() || play.isEmpty()) {
      throw new IllegalArgumentException(
          "recording has no "
              + (configuration.isEmpty() ? "configuration" : "play")
              + " packets; it cannot be played back");
    }
    return new ReplayScript(List.copyOf(configuration), List.copyOf(play));
  }

  /** Configuration-phase packets (id + payload), in order. */
  public List<byte[]> configuration() {
    return configuration;
  }

  /** Number of play packets. */
  public int playPacketCount() {
    return play.size();
  }

  /** Time of the last play packet in milliseconds. */
  public long durationMs() {
    return play.get(play.size() - 1).timeMs();
  }

  /** True when every play packet has been handed out. */
  public synchronized boolean finished() {
    return next >= play.size();
  }

  /**
   * Returns the play packets (id + payload) due at {@code timeMs} that have not been handed out
   * yet, in order. Time never goes backwards here; seeking is a separate feature.
   */
  public synchronized List<byte[]> due(long timeMs) {
    List<byte[]> out = new ArrayList<>();
    while (next < play.size() && play.get(next).timeMs() <= timeMs) {
      out.add(play.get(next).bytes());
      next++;
    }
    return out;
  }

  private record Timed(int timeMs, byte[] bytes) {}
}
