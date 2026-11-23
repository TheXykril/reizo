/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.format;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Payload of a {@link FrameType#PLAYER_SCREEN} frame: the inventory or container screen the
 * recording player had open, its slots and where the mouse was. Playback draws the same screen in
 * first-person view. Written only when something changes; the slots only when they changed, since
 * the mouse moves far more often.
 *
 * @param kind {@link #NONE}, {@link #INVENTORY}, {@link #CREATIVE} or {@link #CONTAINER}
 * @param menuType the menu type's registry id for {@link #CONTAINER} (for example {@code
 *     minecraft:generic_9x3}), else empty
 * @param title the screen title, encoded by the game adapter; empty for the player inventory
 * @param mouseX mouse x as a fraction of the window width, 0 to 1
 * @param mouseY mouse y as a fraction of the window height, 0 to 1
 * @param items every slot of the menu in order, then the stack on the cursor, encoded by the game
 *     adapter; {@code null} when unchanged since the previous frame
 */
public record PlayerScreenState(
    int kind, String menuType, byte[] title, float mouseX, float mouseY, byte[] items) {

  /** Payload layout version, first byte of the payload. */
  public static final int LAYOUT_VERSION = 1;

  public static final int NONE = 0;
  public static final int INVENTORY = 1;
  public static final int CREATIVE = 2;
  public static final int CONTAINER = 3;

  public static final PlayerScreenState CLOSED =
      new PlayerScreenState(NONE, "", new byte[0], 0, 0, new byte[0]);

  public PlayerScreenState {
    menuType = menuType == null ? "" : menuType;
    title = title == null ? new byte[0] : title;
  }

  public boolean open() {
    return kind != NONE;
  }

  /** Same screen (kind, menu and title), whatever the mouse and slots. */
  public boolean sameScreen(PlayerScreenState o) {
    return o != null
        && kind == o.kind
        && menuType.equals(o.menuType)
        && Arrays.equals(title, o.title);
  }

  /** This state with the given slots, for filling in an unchanged {@code items}. */
  public PlayerScreenState withItems(byte[] slots) {
    return new PlayerScreenState(kind, menuType, title, mouseX, mouseY, slots);
  }

  public byte[] encode() {
    byte[] type = menuType.getBytes(StandardCharsets.UTF_8);
    int itemsLength = items == null ? 0 : items.length;
    ByteBuffer buf =
        ByteBuffer.allocate(1 + 1 + 2 + type.length + 4 + title.length + 4 + 4 + 4 + itemsLength);
    buf.put((byte) LAYOUT_VERSION)
        .put((byte) kind)
        .putShort((short) type.length)
        .put(type)
        .putInt(title.length)
        .put(title)
        .putFloat(mouseX)
        .putFloat(mouseY)
        .putInt(items == null ? -1 : items.length);
    if (items != null) {
      buf.put(items);
    }
    return buf.array();
  }

  public static PlayerScreenState decode(byte[] payload) throws FormatException {
    try {
      ByteBuffer buf = ByteBuffer.wrap(payload);
      int version = buf.get() & 0xFF;
      if (version != LAYOUT_VERSION) {
        throw new FormatException("Unknown player screen layout " + version);
      }
      int kind = buf.get() & 0xFF;
      byte[] type = new byte[buf.getShort() & 0xFFFF];
      buf.get(type);
      byte[] title = new byte[length(buf)];
      buf.get(title);
      float mouseX = buf.getFloat();
      float mouseY = buf.getFloat();
      int itemsLength = buf.getInt();
      byte[] items = null;
      if (itemsLength >= 0) {
        if (itemsLength > buf.remaining()) {
          throw new FormatException("Player screen slots past the end of the payload");
        }
        items = new byte[itemsLength];
        buf.get(items);
      }
      if (buf.hasRemaining()) {
        throw new FormatException("Trailing bytes in player screen payload");
      }
      return new PlayerScreenState(
          kind, new String(type, StandardCharsets.UTF_8), title, mouseX, mouseY, items);
    } catch (BufferUnderflowException e) {
      throw new FormatException("Truncated player screen payload", e);
    }
  }

  private static int length(ByteBuffer buf) throws FormatException {
    int n = buf.getInt();
    if (n < 0 || n > buf.remaining()) {
      throw new FormatException("Bad length " + n + " in player screen payload");
    }
    return n;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof PlayerScreenState s
        && sameScreen(s)
        && Float.compare(mouseX, s.mouseX) == 0
        && Float.compare(mouseY, s.mouseY) == 0
        && Arrays.equals(items, s.items);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        kind,
        menuType,
        Arrays.hashCode(title),
        Float.hashCode(mouseX),
        Float.hashCode(mouseY),
        Arrays.hashCode(items));
  }

  @Override
  public String toString() {
    return "PlayerScreenState[kind="
        + kind
        + ", menuType="
        + menuType
        + ", title="
        + title.length
        + " bytes, mouse="
        + mouseX
        + ","
        + mouseY
        + ", items="
        + (items == null ? "unchanged" : items.length + " bytes")
        + "]";
  }
}
