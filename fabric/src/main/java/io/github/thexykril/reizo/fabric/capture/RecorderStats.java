/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.capture;

import java.util.concurrent.atomic.LongAdder;

/**
 * What recording costs the game: time spent handing packets to the recorder on the network thread,
 * and time spent sampling the player on the client thread. Cheap enough to always run (two {@code
 * nanoTime} calls per packet or tick); used by the performance test and for diagnostics.
 */
public final class RecorderStats {
  private static final LongAdder packetNanos = new LongAdder();
  private static final LongAdder packets = new LongAdder();
  private static final LongAdder tickNanos = new LongAdder();
  private static final LongAdder ticks = new LongAdder();

  /** A copy of the counters. */
  public record Snapshot(long packets, long packetNanos, long ticks, long tickNanos) {
    /** Mean cost per captured packet in microseconds. */
    public double microsPerPacket() {
      return packets == 0 ? 0 : packetNanos / 1000.0 / packets;
    }

    /** Mean cost per client tick in microseconds. */
    public double microsPerTick() {
      return ticks == 0 ? 0 : tickNanos / 1000.0 / ticks;
    }
  }

  private RecorderStats() {}

  static void packet(long nanos) {
    packetNanos.add(nanos);
    packets.increment();
  }

  public static void tick(long nanos) {
    tickNanos.add(nanos);
    ticks.increment();
  }

  public static Snapshot snapshot() {
    return new Snapshot(packets.sum(), packetNanos.sum(), ticks.sum(), tickNanos.sum());
  }

  public static void reset() {
    packetNanos.reset();
    packets.reset();
    tickNanos.reset();
    ticks.reset();
  }
}
