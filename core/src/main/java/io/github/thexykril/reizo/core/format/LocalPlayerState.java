/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.format;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * Payload of a {@link FrameType#LOCAL_PLAYER} frame: the recording player's own state for one tick.
 * Servers never send a client its own movement, so without this frame the recording player would be
 * invisible or frozen on playback.
 *
 * <p>{@code pose} is the Minecraft {@code Pose} ordinal of the recording's Minecraft version (see
 * {@link RecordingMetadata#minecraftVersion}); playback must map it per version.
 */
public record LocalPlayerState(
    int entityId,
    double x,
    double y,
    double z,
    float yRot,
    float xRot,
    float headYRot,
    float bodyYRot,
    int pose,
    int flags,
    int swingTime) {

  /** Payload layout version, first byte of the payload. */
  public static final int LAYOUT_VERSION = 1;

  public static final int FLAG_ON_GROUND = 1;
  public static final int FLAG_SPRINTING = 1 << 1;
  public static final int FLAG_SNEAKING = 1 << 2;
  public static final int FLAG_USING_ITEM = 1 << 3;
  public static final int FLAG_SWINGING = 1 << 4;
  public static final int FLAG_SWING_OFFHAND = 1 << 5;

  private static final int SIZE = 1 + 4 + 3 * 8 + 4 * 4 + 1 + 1 + 1;

  public boolean has(int flag) {
    return (flags & flag) != 0;
  }

  public byte[] encode() {
    ByteBuffer buf = ByteBuffer.allocate(SIZE);
    buf.put((byte) LAYOUT_VERSION)
        .putInt(entityId)
        .putDouble(x)
        .putDouble(y)
        .putDouble(z)
        .putFloat(yRot)
        .putFloat(xRot)
        .putFloat(headYRot)
        .putFloat(bodyYRot)
        .put((byte) pose)
        .put((byte) flags)
        .put((byte) Math.max(0, Math.min(255, swingTime)));
    return buf.array();
  }

  public static LocalPlayerState decode(byte[] payload) throws FormatException {
    ByteBuffer buf = ByteBuffer.wrap(payload);
    try {
      int version = buf.get() & 0xFF;
      if (version != LAYOUT_VERSION) {
        throw new FormatException("Unsupported local player layout " + version);
      }
      return new LocalPlayerState(
          buf.getInt(),
          buf.getDouble(),
          buf.getDouble(),
          buf.getDouble(),
          buf.getFloat(),
          buf.getFloat(),
          buf.getFloat(),
          buf.getFloat(),
          buf.get() & 0xFF,
          buf.get() & 0xFF,
          buf.get() & 0xFF);
    } catch (BufferUnderflowException e) {
      throw new FormatException("Truncated local player state", e);
    }
  }
}
