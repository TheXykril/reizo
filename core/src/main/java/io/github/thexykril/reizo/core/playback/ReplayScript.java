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
 * once, before the clock starts), then a timeline of play packets and local player samples. A
 * server switch in the middle (a proxy sending the client back to configuration) is a {@link
 * Kind#RECONFIGURE} entry holding the next configuration phase.
 *
 * <p>Markers and other frame types are for the editor and are skipped here.
 */
public final class ReplayScript {
  /**
   * One timeline entry: a raw play packet, a local player sample, or a new configuration phase.
   *
   * @param configuration for {@link Kind#RECONFIGURE}, the configuration packets in order
   */
  public record Entry(int timeMs, Kind kind, byte[] bytes, List<byte[]> configuration) {
    public Entry(int timeMs, Kind kind, byte[] bytes) {
      this(timeMs, kind, bytes, List.of());
    }
  }

  public enum Kind {
    /** Raw play packet (id + body), as captured. */
    PLAY_PACKET,
    /** Encoded {@link io.github.thexykril.reizo.core.format.LocalPlayerState}. */
    LOCAL_PLAYER,
    /** The server sent the client back to configuration (a server switch); a new world follows. */
    RECONFIGURE,
    /** Encoded {@link io.github.thexykril.reizo.core.format.PlayerHudState}. */
    PLAYER_HUD,
    /** Encoded {@link io.github.thexykril.reizo.core.format.PlayerScreenState}. */
    PLAYER_SCREEN
  }

  private final List<byte[]> configuration;
  private final List<Entry> timeline;
  private final int playPacketCount;
  private final int reconfigureCount;
  private int next;

  private ReplayScript(List<byte[]> configuration, List<Entry> timeline) {
    this.configuration = configuration;
    this.timeline = timeline;
    this.playPacketCount =
        (int) timeline.stream().filter(e -> e.kind() == Kind.PLAY_PACKET).count();
    this.reconfigureCount =
        (int) timeline.stream().filter(e -> e.kind() == Kind.RECONFIGURE).count();
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
    List<byte[]> again = null; // a later configuration phase being collected
    int againAt = 0;
    for (Frame f : frames) {
      FrameType type = f.type();
      if (type == FrameType.LOCAL_PLAYER
          || type == FrameType.PLAYER_HUD
          || type == FrameType.PLAYER_SCREEN) {
        if (sawPlay && again == null) { // no world while configuring
          Kind kind =
              switch (type) {
                case LOCAL_PLAYER -> Kind.LOCAL_PLAYER;
                case PLAYER_HUD -> Kind.PLAYER_HUD;
                default -> Kind.PLAYER_SCREEN;
              };
          timeline.add(new Entry(f.timeMs(), kind, f.payload()));
        }
        continue;
      }
      if (type != FrameType.PACKET) {
        continue;
      }
      if (f.packetPhase() == ProtocolPhase.CONFIGURATION) {
        if (!sawPlay) {
          configuration.add(f.packetBytes());
        } else {
          if (again == null) {
            again = new ArrayList<>();
            againAt = f.timeMs();
          }
          again.add(f.packetBytes());
        }
      } else {
        if (again != null) {
          timeline.add(new Entry(againAt, Kind.RECONFIGURE, new byte[0], List.copyOf(again)));
          again = null;
        }
        sawPlay = true;
        timeline.add(new Entry(f.timeMs(), Kind.PLAY_PACKET, f.packetBytes()));
      }
    }
    // A recording that ends inside a configuration phase has no world after it: left out.
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

  /** Number of server switches (later configuration phases) in the timeline. */
  public int reconfigureCount() {
    return reconfigureCount;
  }

  /** Number of play packets in the timeline. */
  public int playPacketCount() {
    return playPacketCount;
  }

  /**
   * Time of the first timeline entry (the first play packet) in milliseconds. A recording's clock
   * starts at the configuration phase, so the world only exists from here on: seeks go no earlier.
   */
  public long startMs() {
    return timeline.get(0).timeMs();
  }

  /** Time of the last timeline entry in milliseconds. */
  public long durationMs() {
    return timeline.get(timeline.size() - 1).timeMs();
  }

  /** Starts handing out the timeline from the beginning again (for a backwards seek). */
  public synchronized void rewind() {
    next = 0;
  }

  /** True when every timeline entry has been handed out. */
  public synchronized boolean finished() {
    return next >= timeline.size();
  }

  /**
   * Hands out the entries up to and including the next play packet, whatever their time. After a
   * server switch this is the new world's login, which the client needs before it shows anything.
   */
  public synchronized List<Entry> throughNextPlayPacket() {
    List<Entry> out = new ArrayList<>();
    while (next < timeline.size()) {
      Entry e = timeline.get(next++);
      out.add(e);
      if (e.kind() == Kind.PLAY_PACKET) {
        break;
      }
    }
    return out;
  }

  /**
   * Returns the timeline entries due at {@code timeMs} that have not been handed out yet, in order.
   * Time never goes backwards here: to go back, {@link #rewind()} and hand out again from the
   * start.
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
