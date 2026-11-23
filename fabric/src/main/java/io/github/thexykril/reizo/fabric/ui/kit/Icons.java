/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui.kit;

import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Small pixel icons for the editor's toolbars, drawn from 7×7 masks. Reizo's own drawings; used
 * where the font's symbols come out too small.
 */
public final class Icons {
  private static final Map<String, String[]> MASKS =
      Map.of(
          "eye",
          new String[] {
            ".......", "..###..", ".#...#.", "#..#..#", ".#...#.", "..###..", "......."
          },
          "sliders",
          new String[] {
            ".#.....", "#######", ".#.....", "....#..", "#######", "....#..", "......."
          },
          "panels",
          new String[] {
            "#######", "#.#...#", "#.#...#", "#.#...#", "#.#...#", "#.#...#", "#######"
          },
          "wave",
          new String[] {
            ".......", ".......", ".##...#", "#..#.#.", "....#..", ".......", "......."
          },
          "undo",
          new String[] {
            "..#....", ".##....", "######.", ".##...#", "..#...#", "......#", "..####."
          },
          "redo",
          new String[] {
            "....#..", "....##.", ".######", "#...##.", "#...#..", "#......", ".####.."
          },
          "fit",
          new String[] {
            "###.###", "#.....#", "#.....#", ".......", "#.....#", "#.....#", "###.###"
          },
          "sparkle",
          new String[] {
            "...#...", "...#...", "..###..", "#######", "..###..", "...#...", "...#..."
          });

  private Icons() {}

  /** Whether {@code name} is a pixel icon. */
  public static boolean has(String name) {
    return MASKS.containsKey(name);
  }

  /** Draws icon {@code name} centred on (cx, cy). */
  public static void draw(GuiGraphics g, String name, int cx, int cy, int color) {
    String[] mask = MASKS.get(name);
    if (mask == null) {
      return;
    }
    int x0 = cx - 3;
    int y0 = cy - 3;
    for (int y = 0; y < mask.length; y++) {
      String row = mask[y];
      int x = 0;
      while (x < row.length()) {
        if (row.charAt(x) != '#') {
          x++;
          continue;
        }
        int start = x;
        while (x < row.length() && row.charAt(x) == '#') {
          x++;
        }
        g.fill(x0 + start, y0 + y, x0 + x, y0 + y + 1, color);
      }
    }
  }
}
