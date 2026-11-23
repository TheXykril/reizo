/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PlayerHudStateTest {
  private static PlayerHudState sample(byte[] items) {
    return new PlayerHudState(
        4,
        0,
        PlayerHudState.FLAG_HARDCORE,
        17.5f,
        20f,
        4f,
        13,
        2.5f,
        15,
        30,
        0.25f,
        120,
        300,
        items);
  }

  @Test
  void roundTrip() throws FormatException {
    PlayerHudState state = sample(new byte[] {1, 2, 3, 4});
    PlayerHudState back = PlayerHudState.decode(state.encode());
    assertEquals(state, back);
    assertEquals(4, back.selectedSlot());
    assertEquals(17.5f, back.health());
    assertTrue(back.has(PlayerHudState.FLAG_HARDCORE));
    assertEquals(120, back.air());
  }

  @Test
  void negativeAirSurvives() throws FormatException {
    PlayerHudState drowning =
        new PlayerHudState(0, 0, 0, 1, 20, 0, 20, 0, 0, 0, 0, -10, 300, new byte[0]);
    assertEquals(-10, PlayerHudState.decode(drowning.encode()).air());
  }

  @Test
  void itemsTakePartInEquality() {
    assertNotEquals(sample(new byte[] {1}), sample(new byte[] {2}));
    assertEquals(sample(new byte[] {1}), sample(new byte[] {1}));
  }

  @Test
  void rejectsTruncatedOrUnknownPayloads() {
    byte[] bytes = sample(new byte[] {1, 2, 3}).encode();
    assertThrows(
        FormatException.class, () -> PlayerHudState.decode(Arrays.copyOf(bytes, bytes.length - 2)));
    bytes[0] = 99;
    assertThrows(FormatException.class, () -> PlayerHudState.decode(bytes));
  }
}
