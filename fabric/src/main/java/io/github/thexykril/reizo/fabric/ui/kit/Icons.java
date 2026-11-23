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
      Map.ofEntries(
          Map.entry(
              "eye",
              new String[] {
            ".......", "..###..", ".#...#.", "#..#..#", ".#...#.", "..###..", "......."
          }),
          Map.entry(
              "sliders",
              new String[] {
            ".#.....", "#######", ".#.....", "....#..", "#######", "....#..", "......."
          }),
          Map.entry(
              "panels",
              new String[] {
            "#######", "#.#...#", "#.#...#", "#.#...#", "#.#...#", "#.#...#", "#######"
          }),
          Map.entry(
              "wave",
              new String[] {
            ".......", ".......", ".##...#", "#..#.#.", "....#..", ".......", "......."
          }),
          Map.entry(
              "undo",
              new String[] {
            "..#....", ".##....", "######.", ".##...#", "..#...#", "......#", "..####."
          }),
          Map.entry(
              "redo",
              new String[] {
            "....#..", "....##.", ".######", "#...##.", "#...#..", "#......", ".####.."
          }),
          Map.entry(
              "fit",
              new String[] {
            "###.###", "#.....#", "#.....#", ".......", "#.....#", "#.....#", "###.###"
          }),
          Map.entry(
              "open",
              new String[] {
            ".......", ".......", "#######", ".#####.", "..###..", "...#...", "......."
          }),
          Map.entry(
              "closed",
              new String[] {
            ".#.....", ".##....", ".###...", ".####..", ".###...", ".##....", ".#....."
          }),
          Map.entry(
              "sparkle",
              new String[] {
            "...#...", "...#...", "..###..", "#######", "..###..", "...#...", "...#..."
          }),
          Map.entry(
              "cut",
              new String[] {
            "#.....#", ".#...#.", "..#.#..", "...#...", ".##.##.", "#..#..#", ".##.##."
          }));

  private Icons() {}

  /** Whether {@code name} is an icon (a line icon, or a pixel icon). */
  public static boolean has(String name) {
    return MASKS.containsKey(name) || LineIcons.has(line(name));
  }

  /** The line icon standing in for a pixel icon's name. */
  private static String line(String name) {
    return switch (name) {
      case "open" -> "down";
      case "closed" -> "right";
      default -> name;
    };
  }

  /** Draws icon {@code name} centred on (cx, cy), 7 GUI pixels wide. */
  public static void draw(GuiGraphics g, String name, int cx, int cy, int color) {
    draw(g, name, cx, cy, 7, color);
  }

  /**
   * Draws icon {@code name} centred on (cx, cy), {@code size} GUI pixels wide: a sharp line icon,
   * or with OneConfig's pixel themes the 7x7 pixel icon where there is one.
   */
  public static void draw(GuiGraphics g, String name, int cx, int cy, int size, int color) {
    if (!(Theme.pixel() && MASKS.containsKey(name)) && LineIcons.has(line(name))) {
      LineIcons.draw(g, line(name), cx, cy, size, color);
      return;
    }
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
