/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.format;

/**
 * Network protocol phase a recorded packet belongs to. Stored as the first byte of a {@link
 * FrameType#PACKET} payload, so the packet id can be decoded with the right codec on playback.
 *
 * <p>Only phases that carry world state are recorded. Login (encryption, compression, profile) and
 * status are never written.
 */
public enum ProtocolPhase {
  CONFIGURATION(1),
  PLAY(2);

  private final int id;

  ProtocolPhase(int id) {
    this.id = id;
  }

  public int id() {
    return id;
  }

  public static ProtocolPhase byId(int id) {
    for (ProtocolPhase phase : values()) {
      if (phase.id == id) {
        return phase;
      }
    }
    throw new IllegalArgumentException("Unknown protocol phase id " + id);
  }
}
