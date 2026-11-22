/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.format;

import java.util.Arrays;
import java.util.Objects;

/**
 * One timestamped entry in a recording stream.
 *
 * @param timeMs recording time in milliseconds since the recording started, excluding paused time
 * @param typeId raw {@link FrameType} id; kept raw so unknown future types survive a round trip
 * @param payload frame body; callers must not mutate it after construction
 */
public record Frame(int timeMs, int typeId, byte[] payload) {

  public Frame {
    if (timeMs < 0) {
      throw new IllegalArgumentException("timeMs must be >= 0, was " + timeMs);
    }
    if (typeId <= 0) {
      throw new IllegalArgumentException("typeId must be > 0, was " + typeId);
    }
    Objects.requireNonNull(payload, "payload");
  }

  public static Frame of(int timeMs, FrameType type, byte[] payload) {
    return new Frame(timeMs, type.id(), payload);
  }

  /** Builds a {@link FrameType#PACKET} frame: {@code [phase][packetBytes]}. */
  public static Frame packet(int timeMs, ProtocolPhase phase, byte[] packetBytes) {
    byte[] payload = new byte[packetBytes.length + 1];
    payload[0] = (byte) phase.id();
    System.arraycopy(packetBytes, 0, payload, 1, packetBytes.length);
    return new Frame(timeMs, FrameType.PACKET.id(), payload);
  }

  /** Returns the known type, or {@code null} for a type this build does not understand. */
  public FrameType type() {
    return FrameType.byId(typeId);
  }

  /** For {@link FrameType#PACKET} frames: the protocol phase. */
  public ProtocolPhase packetPhase() {
    requireType(FrameType.PACKET);
    return ProtocolPhase.byId(payload[0]);
  }

  /** For {@link FrameType#PACKET} frames: a copy of the raw packet bytes (id + body). */
  public byte[] packetBytes() {
    requireType(FrameType.PACKET);
    return Arrays.copyOfRange(payload, 1, payload.length);
  }

  private void requireType(FrameType expected) {
    if (typeId != expected.id()) {
      throw new IllegalStateException("Frame is " + type() + ", not " + expected);
    }
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Frame other
        && timeMs == other.timeMs
        && typeId == other.typeId
        && Arrays.equals(payload, other.payload);
  }

  @Override
  public int hashCode() {
    return 31 * (31 * timeMs + typeId) + Arrays.hashCode(payload);
  }

  @Override
  public String toString() {
    return "Frame[t=" + timeMs + "ms, type=" + typeId + ", " + payload.length + " bytes]";
  }
}
