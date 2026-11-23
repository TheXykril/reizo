/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui.kit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Drawing helpers for the editor kit. Text uses Reizo's UI font (Inter, falling back to
 * Minecraft's font for symbols). Rounded shapes and hairlines are drawn at the window's real pixel
 * size, with anti-aliased corners, so they stay smooth at any GUI scale. Only {@code fill}, {@code
 * drawString} and the pose stack are used, which every supported version has.
 */
public final class Draw {
  /** The editor's text style: Inter through the {@code reizo:ui} font. */
  public static final Style UI =
      Style.EMPTY.withFont(
          new FontDescription.Resource(Identifier.fromNamespaceAndPath("reizo", "ui")));

  private Draw() {}

  /** {@code s} in the UI font. */
  public static MutableComponent ui(String s) {
    return Component.literal(s).withStyle(style());
  }

  /** The text style in use: Inter, or Minecraft's font with OneConfig's pixel themes. */
  public static Style style() {
    return Theme.pixel() ? Style.EMPTY : UI;
  }

  /** {@code c} in the UI font. */
  public static MutableComponent ui(Component c) {
    return c.copy().withStyle(style());
  }

  /** Width of {@code s} in the UI font. */
  public static int width(Font font, String s) {
    return font.width(ui(s));
  }

  public static int width(Font font, Component c) {
    return font.width(ui(c));
  }

  private static int guiScale() {
    return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
  }

  /**
   * A filled rectangle with rounded corners of {@code radius} GUI pixels, anti-aliased at the
   * window's pixel size.
   */
  public static void round(GuiGraphics g, int x0, int y0, int x1, int y1, int radius, int color) {
    round(g, x0, y0, x1, y1, radius, color, true, true);
  }

  /** Colour {@code t} of the way from {@code a} to {@code b} (opaque result). */
  public static int mix(int a, int b, double t) {
    int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
    int gr = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
    int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
    return 0xFF000000 | (r << 16) | (gr << 8) | bl;
  }

  /**
   * Like {@link #round(GuiGraphics, int, int, int, int, int, int)}, rounding only the chosen ends,
   * so two pieces can sit side by side without overlapping.
   */
  public static void round(
      GuiGraphics g,
      int x0,
      int y0,
      int x1,
      int y1,
      int radius,
      int color,
      boolean roundLeft,
      boolean roundRight) {
    if (x1 <= x0 || y1 <= y0) {
      return;
    }
    int s = guiScale();
    int r =
        Theme.pixel() ? 0 : Math.max(0, Math.min(radius * s, Math.min((x1 - x0) * s / 2, (y1 - y0) * s / 2)));
    if (r == 0) {
      g.fill(x0, y0, x1, y1, color);
      return;
    }
    g.pose().pushMatrix();
    g.pose().scale(1f / s, 1f / s);
    roundPx(g, x0 * s, y0 * s, x1 * s, y1 * s, r, color, roundLeft, roundRight);
    g.pose().popMatrix();
  }

  /**
   * A card: a rounded rectangle with a one-window-pixel border. Both colours should be opaque (the
   * GUI renderer may reorder overlapping translucent fills).
   */
  public static void card(
      GuiGraphics g, int x0, int y0, int x1, int y1, int radius, int fill, int border) {
    if (x1 <= x0 || y1 <= y0) {
      return;
    }
    int s = guiScale();
    if (Theme.pixel()) {
      g.fill(x0, y0, x1, y1, border);
      g.pose().pushMatrix();
      g.pose().scale(1f / s, 1f / s);
      g.fill(x0 * s + 1, y0 * s + 1, x1 * s - 1, y1 * s - 1, fill);
      g.pose().popMatrix();
      return;
    }
    int r = Math.max(1, Math.min(radius * s, Math.min((x1 - x0) * s / 2, (y1 - y0) * s / 2)));
    g.pose().pushMatrix();
    g.pose().scale(1f / s, 1f / s);
    roundPx(g, x0 * s, y0 * s, x1 * s, y1 * s, r, border, true, true);
    roundPx(g, x0 * s + 1, y0 * s + 1, x1 * s - 1, y1 * s - 1, Math.max(0, r - 1), fill, true, true);
    g.pose().popMatrix();
  }

  /** A rounded rectangle in window pixels (the pose already scaled to them). */
  private static void roundPx(
      GuiGraphics g,
      int px0,
      int py0,
      int px1,
      int py1,
      int r,
      int color,
      boolean roundLeft,
      boolean roundRight) {
    if (r <= 0) {
      g.fill(px0, py0, px1, py1, color);
      return;
    }
    g.fill(px0, py0 + r, px1, py1 - r, color);
    int alpha = (color >>> 24) & 0xFF;
    for (int i = 0; i < r; i++) {
      double dy = r - (i + 0.5);
      double dx = r - Math.sqrt(Math.max(0, (double) r * r - dy * dy));
      int solid = (int) Math.ceil(dx);
      double cover = solid - dx; // coverage of the edge pixel
      int top = py0 + i;
      int bottom = py1 - 1 - i;
      int l = roundLeft ? solid : 0;
      int rr = roundRight ? solid : 0;
      g.fill(px0 + l, top, px1 - rr, top + 1, color);
      g.fill(px0 + l, bottom, px1 - rr, bottom + 1, color);
      if (solid > 0 && cover > 0.02) {
        int edge = Theme.alpha(color, (int) Math.round(alpha * cover));
        if (roundLeft) {
          g.fill(px0 + solid - 1, top, px0 + solid, top + 1, edge);
          g.fill(px0 + solid - 1, bottom, px0 + solid, bottom + 1, edge);
        }
        if (roundRight) {
          g.fill(px1 - solid, top, px1 - solid + 1, top + 1, edge);
          g.fill(px1 - solid, bottom, px1 - solid + 1, bottom + 1, edge);
        }
      }
    }
  }

  /** A one-window-pixel horizontal line at GUI y {@code y} from x0 to x1. */
  public static void hline(GuiGraphics g, int x0, int x1, int y, int color) {
    int s = guiScale();
    g.pose().pushMatrix();
    g.pose().scale(1f / s, 1f / s);
    g.fill(x0 * s, y * s, x1 * s, y * s + 1, color);
    g.pose().popMatrix();
  }

  /** A one-window-pixel vertical line at GUI x {@code x} from y0 to y1. */
  public static void vline(GuiGraphics g, int x, int y0, int y1, int color) {
    int s = guiScale();
    g.pose().pushMatrix();
    g.pose().scale(1f / s, 1f / s);
    g.fill(x * s, y0 * s, x * s + 1, y1 * s, color);
    g.pose().popMatrix();
  }

  /** A one-pixel outline inside the rectangle. */
  public static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
    hline(g, x0, x1, y0, color);
    vline(g, x0, y0, y1, color);
    int s = guiScale();
    g.pose().pushMatrix();
    g.pose().scale(1f / s, 1f / s);
    g.fill(x0 * s, y1 * s - 1, x1 * s, y1 * s, color);
    g.fill(x1 * s - 1, y0 * s, x1 * s, y1 * s, color);
    g.pose().popMatrix();
  }

  /** A diamond of half-size {@code r} GUI pixels centred on (cx, cy), drawn at window pixels. */
  public static void diamond(GuiGraphics g, int cx, int cy, int r, int color) {
    int s = guiScale();
    int pr = r * s;
    int pcx = cx * s + s / 2;
    int pcy = cy * s + s / 2;
    g.pose().pushMatrix();
    g.pose().scale(1f / s, 1f / s);
    for (int i = -pr; i <= pr; i++) {
      int w = pr - Math.abs(i);
      g.fill(pcx - w, pcy + i, pcx + w + 1, pcy + i + 1, color);
    }
    g.pose().popMatrix();
  }

  /** A small round dot centred on (cx, cy). */
  public static void dot(GuiGraphics g, int cx, int cy, int color) {
    round(g, cx - 2, cy - 2, cx + 3, cy + 3, 3, color);
  }

  /** Text in the UI font, without a shadow. */
  public static void label(GuiGraphics g, Font font, String s, int x, int y, int color) {
    g.drawString(font, ui(s), x, y, color, false);
  }

  /** Text in the UI font centred on {@code cx}, without a shadow. */
  public static void centered(GuiGraphics g, Font font, String s, int cx, int y, int color) {
    g.drawString(font, ui(s), cx - width(font, s) / 2, y, color, false);
  }

  /** Wrapped text in the UI font. Returns the height used. */
  public static int wrap(GuiGraphics g, Font font, Component c, int x, int y, int width, int color) {
    var lines = font.split(ui(c), width);
    for (int i = 0; i < lines.size(); i++) {
      g.drawString(font, lines.get(i), x, y + i * 10, color, false);
    }
    return lines.size() * 10;
  }

  /** Text cut to {@code width} pixels with an ellipsis, measured in the UI font. */
  public static String fit(Font font, String s, int width) {
    if (width <= 0) {
      return "";
    }
    if (width(font, s) <= width) {
      return s;
    }
    String cut = font.substrByWidth(ui(s), Math.max(0, width - width(font, "…"))).getString();
    return cut + "…";
  }
}
