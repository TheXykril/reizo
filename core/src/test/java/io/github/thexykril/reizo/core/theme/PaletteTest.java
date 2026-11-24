/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PaletteTest {
  @Test
  void decodesComposeSrgbColours() {
    long packed = (0xFF1878F1L) << 32;
    assertEquals(0xFF1878F1, Palette.composeToArgb(packed));
    assertNull(Palette.composeToArgb(packed | 5));
  }

  @Test
  void followsTheSelectedThemeColours() {
    Palette p =
        Palette.fromOneConfig(
            Map.of(
                "page",
                0xFFF5F5F5,
                "component",
                0xFFFFFFFF,
                "text",
                0xFF111111,
                "accent",
                0xFF00AA55),
            "Flat",
            true,
            Palette.DEFAULT);
    assertEquals(0xFF00AA55, p.accent());
    assertEquals(0xFF111111, p.textColor());
    assertEquals(0xFFFFFFFF, p.raised());
    assertEquals(0xF5F5F5, p.ground() & 0xFFFFFF);
    assertTrue(p.light());
    assertEquals(Palette.Look.FLAT, p.look());
    // Missing colours keep the fallback's.
    assertEquals(Palette.DEFAULT.danger(), p.danger());
  }

  @Test
  void glassIsTranslucentAndPixelKeepsItsLook() {
    Palette glass =
        Palette.fromOneConfig(Map.of("page", 0xFF101010), "Glass", false, Palette.DEFAULT);
    assertTrue((glass.ground() >>> 24) < 0xF0);
    assertEquals(Palette.Look.GLASS, glass.look());
    assertEquals(
        Palette.Look.PIXEL,
        Palette.fromOneConfig(Map.of(), "Pixel", false, Palette.DEFAULT).look());
  }

  @Test
  void mixBlendsEachChannel() {
    assertEquals(0xFF808080, Palette.mix(0xFF000000, 0xFFFFFFFF, 0.5));
  }
}
