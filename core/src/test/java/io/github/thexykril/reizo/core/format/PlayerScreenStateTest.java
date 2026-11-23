/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.format;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PlayerScreenStateTest {
  @Test
  void roundTripWithSlots() throws FormatException {
    PlayerScreenState s =
        new PlayerScreenState(
            PlayerScreenState.CONTAINER,
            "minecraft:generic_9x3",
            new byte[] {1, 2, 3},
            0.25f,
            0.75f,
            new byte[] {4, 5});
    PlayerScreenState decoded = PlayerScreenState.decode(s.encode());
    assertEquals(s, decoded);
    assertTrue(decoded.open());
    assertArrayEquals(new byte[] {4, 5}, decoded.items());
  }

  @Test
  void unchangedSlotsStayNull() throws FormatException {
    PlayerScreenState s =
        new PlayerScreenState(PlayerScreenState.INVENTORY, "", new byte[0], 0.5f, 0.5f, null);
    PlayerScreenState decoded = PlayerScreenState.decode(s.encode());
    assertNull(decoded.items());
    assertTrue(decoded.sameScreen(s));
    assertArrayEquals(new byte[] {9}, decoded.withItems(new byte[] {9}).items());
    assertFalse(PlayerScreenState.CLOSED.open());
    assertFalse(PlayerScreenState.CLOSED.sameScreen(s));
  }

  @Test
  void rejectsTruncatedAndUnknownLayouts() {
    byte[] bytes =
        new PlayerScreenState(PlayerScreenState.INVENTORY, "", new byte[] {1}, 0, 0, new byte[] {2})
            .encode();
    assertThrows(
        FormatException.class,
        () -> PlayerScreenState.decode(Arrays.copyOf(bytes, bytes.length - 1)));
    byte[] unknown = bytes.clone();
    unknown[0] = 99;
    assertThrows(FormatException.class, () -> PlayerScreenState.decode(unknown));
  }
}
