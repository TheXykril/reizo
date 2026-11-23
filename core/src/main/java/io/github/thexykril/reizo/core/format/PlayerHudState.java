/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.format;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Payload of a {@link FrameType#PLAYER_HUD} frame: what the recording player's HUD showed. Playback
 * draws the recorded player's HUD from it in first-person view. Written only when something
 * changes.
 *
 * @param selectedSlot hotbar slot in hand, 0 to 8
 * @param gameMode the game's own game mode id (0 survival, 1 creative, 2 adventure, 3 spectator)
 * @param flags {@link #FLAG_HARDCORE}
 * @param items the hotbar's nine stacks and the offhand stack, encoded by the game adapter
 */
public record PlayerHudState(
    int selectedSlot,
    int gameMode,
    int flags,
    float health,
    float maxHealth,
    float absorption,
    int food,
    float saturation,
    int armor,
    int experienceLevel,
    float experienceProgress,
    int air,
    int maxAir,
    byte[] items) {

  /** Payload layout version, first byte of the payload. */
  public static final int LAYOUT_VERSION = 1;

  public static final int FLAG_HARDCORE = 1;

  public boolean has(int flag) {
    return (flags & flag) != 0;
  }

  public byte[] encode() {
    ByteBuffer buf =
        ByteBuffer.allocate(1 + 3 + 4 * 3 + 1 + 4 + 1 + 4 + 4 + 2 + 2 + 4 + items.length);
    buf.put((byte) LAYOUT_VERSION)
        .put((byte) selectedSlot)
        .put((byte) gameMode)
        .put((byte) flags)
        .putFloat(health)
        .putFloat(maxHealth)
        .putFloat(absorption)
        .put((byte) Math.max(0, Math.min(255, food)))
        .putFloat(saturation)
        .put((byte) Math.max(0, Math.min(255, armor)))
        .putInt(experienceLevel)
        .putFloat(experienceProgress)
        .putShort((short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, air)))
        .putShort((short) Math.max(0, Math.min(Short.MAX_VALUE, maxAir)))
        .putInt(items.length)
        .put(items);
    return buf.array();
  }

  public static PlayerHudState decode(byte[] payload) throws FormatException {
    ByteBuffer buf = ByteBuffer.wrap(payload);
    try {
      int version = buf.get() & 0xFF;
      if (version != LAYOUT_VERSION) {
        throw new FormatException("Unsupported player HUD layout " + version);
      }
      int selected = buf.get() & 0xFF;
      int gameMode = buf.get() & 0xFF;
      int flags = buf.get() & 0xFF;
      float health = buf.getFloat();
      float maxHealth = buf.getFloat();
      float absorption = buf.getFloat();
      int food = buf.get() & 0xFF;
      float saturation = buf.getFloat();
      int armor = buf.get() & 0xFF;
      int level = buf.getInt();
      float progress = buf.getFloat();
      int air = buf.getShort();
      int maxAir = buf.getShort();
      int length = buf.getInt();
      if (length < 0 || length > buf.remaining()) {
        throw new FormatException("Bad player HUD item length " + length);
      }
      byte[] items = new byte[length];
      buf.get(items);
      return new PlayerHudState(
          selected,
          gameMode,
          flags,
          health,
          maxHealth,
          absorption,
          food,
          saturation,
          armor,
          level,
          progress,
          air,
          maxAir,
          items);
    } catch (BufferUnderflowException e) {
      throw new FormatException("Truncated player HUD state", e);
    }
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof PlayerHudState s
        && selectedSlot == s.selectedSlot
        && gameMode == s.gameMode
        && flags == s.flags
        && Float.compare(health, s.health) == 0
        && Float.compare(maxHealth, s.maxHealth) == 0
        && Float.compare(absorption, s.absorption) == 0
        && food == s.food
        && Float.compare(saturation, s.saturation) == 0
        && armor == s.armor
        && experienceLevel == s.experienceLevel
        && Float.compare(experienceProgress, s.experienceProgress) == 0
        && air == s.air
        && maxAir == s.maxAir
        && Arrays.equals(items, s.items);
  }

  @Override
  public int hashCode() {
    return 31 * java.util.Objects.hash(selectedSlot, health, food, experienceLevel, air)
        + Arrays.hashCode(items);
  }

  @Override
  public String toString() {
    return "PlayerHudState[slot="
        + selectedSlot
        + ", health="
        + health
        + ", food="
        + food
        + ", level="
        + experienceLevel
        + ", items="
        + items.length
        + " bytes]";
  }
}
