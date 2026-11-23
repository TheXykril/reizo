/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui.kit;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Drawing helpers for the editor kit. Only {@code fill} and {@code drawString} are used, so they
 * work the same on every Minecraft version Reizo builds for.
 */
public final class Draw {
  private Draw() {}

  /** A filled rectangle with corners cut by {@code radius} pixel steps (0 to 3). */
  public static void round(GuiGraphics g, int x0, int y0, int x1, int y1, int radius, int color) {
    if (x1 <= x0 || y1 <= y0) {
      return;
    }
    int r = Math.max(0, Math.min(radius, Math.min((x1 - x0) / 2, (y1 - y0) / 2)));
    if (r == 0) {
      g.fill(x0, y0, x1, y1, color);
      return;
    }
    g.fill(x0, y0 + r, x1, y1 - r, color);
    for (int i = 0; i < r; i++) {
      // Row i from the top and bottom: inset shrinks towards the middle (a stepped curve).
      int inset = r - i - (i > 0 && r == 3 ? 1 : 0);
      inset = Math.max(1, Math.min(r, inset));
      g.fill(x0 + inset, y0 + i, x1 - inset, y0 + i + 1, color);
      g.fill(x0 + inset, y1 - i - 1, x1 - inset, y1 - i, color);
    }
  }

  /** A one-pixel outline inside the rectangle. */
  public static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
    g.fill(x0, y0, x1, y0 + 1, color);
    g.fill(x0, y1 - 1, x1, y1, color);
    g.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
    g.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
  }

  /** A diamond of half-size {@code r} centred on (cx, cy). */
  public static void diamond(GuiGraphics g, int cx, int cy, int r, int color) {
    for (int i = -r; i <= r; i++) {
      int w = r - Math.abs(i);
      g.fill(cx - w, cy + i, cx + w + 1, cy + i + 1, color);
    }
  }

  /** A small filled circle-ish dot. */
  public static void dot(GuiGraphics g, int cx, int cy, int color) {
    g.fill(cx - 1, cy - 2, cx + 2, cy + 3, color);
    g.fill(cx - 2, cy - 1, cx + 3, cy + 2, color);
  }

  /** Text without a shadow. */
  public static void label(GuiGraphics g, Font font, String s, int x, int y, int color) {
    g.drawString(font, s, x, y, color, false);
  }

  /** Text centred on {@code cx}, without a shadow. */
  public static void centered(GuiGraphics g, Font font, String s, int cx, int y, int color) {
    g.drawString(font, s, cx - font.width(s) / 2, y, color, false);
  }

  /** Text cut to {@code width} pixels with an ellipsis. */
  public static String fit(Font font, String s, int width) {
    if (width <= 0) {
      return "";
    }
    if (font.width(s) <= width) {
      return s;
    }
    return font.plainSubstrByWidth(s, Math.max(0, width - font.width("…"))) + "…";
  }
}
