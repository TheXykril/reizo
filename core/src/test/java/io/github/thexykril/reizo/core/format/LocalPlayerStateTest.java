/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LocalPlayerStateTest {
  @Test
  void roundTrip() throws FormatException {
    LocalPlayerState state =
        new LocalPlayerState(
            42,
            100.5,
            64.0,
            -3.25,
            90f,
            -10f,
            91f,
            88f,
            5,
            LocalPlayerState.FLAG_ON_GROUND | LocalPlayerState.FLAG_SPRINTING,
            3);
    LocalPlayerState decoded = LocalPlayerState.decode(state.encode());
    assertEquals(state, decoded);
    assertTrue(decoded.has(LocalPlayerState.FLAG_SPRINTING));
    assertFalse(decoded.has(LocalPlayerState.FLAG_SNEAKING));
  }

  @Test
  void rejectsTruncatedAndUnknownLayouts() {
    byte[] bytes = new LocalPlayerState(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0).encode();
    assertThrows(
        FormatException.class,
        () -> LocalPlayerState.decode(Arrays.copyOf(bytes, bytes.length - 1)));
    bytes[0] = 99;
    assertThrows(FormatException.class, () -> LocalPlayerState.decode(bytes));
  }
}
