/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.format;

/**
 * Kinds of frames stored in a recording stream.
 *
 * <p>The numeric {@link #id()} is part of the on-disk format. Never renumber or reuse an id; add
 * new constants with new ids and bump {@link StreamFormat#VERSION} if readers must understand them.
 * Readers skip frames with unknown ids, so adding a type is backward compatible for old readers.
 */
public enum FrameType {
  /**
   * A clientbound packet exactly as the client decoded it: payload is {@code [protocol byte][packet
   * id + body]}. See {@link ProtocolPhase}.
   */
  PACKET(1),
  /**
   * The local player's own state for one client tick. The server never echoes this back to the
   * client, so it has to be recorded separately. Payload layout is owned by the game adapter.
   */
  LOCAL_PLAYER(2),
  /** A user marker (bookmark). Payload is a UTF-8 label. */
  MARKER(3),
  /**
   * Marks that frames before this point were synthesized from a cache, not observed live (for
   * example configuration data replayed when a recording starts mid-session). Payload empty.
   */
  LIVE_START(4),
  /**
   * What the local player's HUD showed (hotbar, selected slot, health, food, armor, experience,
   * air), written whenever it changes. Payload: {@link PlayerHudState}.
   */
  PLAYER_HUD(5),
  /**
   * The inventory or container screen the local player has open, with its slots and the mouse,
   * written whenever it changes. Payload: {@link PlayerScreenState}.
   */
  PLAYER_SCREEN(6);

  private final int id;

  FrameType(int id) {
    this.id = id;
  }

  public int id() {
    return id;
  }

  /** Returns the type for an id, or {@code null} if this reader does not know it. */
  public static FrameType byId(int id) {
    for (FrameType type : values()) {
      if (type.id == id) {
        return type;
      }
    }
    return null;
  }
}
