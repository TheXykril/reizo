/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui.kit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Line icons drawn at the window's real pixel size, anti-aliased, so they stay sharp at any GUI
 * scale. Reizo's own drawings, on a 24-unit grid with 2-unit strokes. Each icon is a list of
 * commands:
 *
 * <ul>
 *   <li>{@code M x y L x y ...}: a stroked polyline ({@code Z} closes it)
 *   <li>{@code P x y x y ...}: a filled polygon
 *   <li>{@code O cx cy r}: a stroked circle; {@code D cx cy r}: a filled disc
 *   <li>{@code A cx cy r from to}: a stroked arc, angles in degrees (0 = right, 90 = down)
 * </ul>
 *
 * Rasterised once per icon and pixel size, then drawn as runs of fills.
 */
public final class LineIcons {
  private static final Map<String, String> ICONS = new HashMap<>();

  static {
    ICONS.put("play", "P 7 4.5 19.5 12 7 19.5");
    ICONS.put("pause", "P 6 5 10 5 10 19 6 19 P 14 5 18 5 18 19 14 19");
    ICONS.put("prev", "P 19 5 9 12 19 19 M 6 5 L 6 19");
    ICONS.put("next", "P 5 5 15 12 5 19 M 18 5 L 18 19");
    ICONS.put("back", "P 12 6 3.5 12 12 18 P 20.5 6 12 12 20.5 18");
    ICONS.put("forward", "P 3.5 6 12 12 3.5 18 P 12 6 20.5 12 12 18");
    ICONS.put("undo", "M 9 14 L 4 9 L 9 4 M 4 9 L 14.5 9 A 14.5 14 5 -90 90 M 14.5 19 L 10 19");
    ICONS.put("redo", "M 15 14 L 20 9 L 15 4 M 20 9 L 9.5 9 A 9.5 14 5 270 90 M 9.5 19 L 14 19");
    ICONS.put(
        "eye",
        "M 2 12 L 5 8.2 L 8.5 5.8 L 12 5 L 15.5 5.8 L 19 8.2 L 22 12 L 19 15.8 L 15.5 18.2"
            + " L 12 19 L 8.5 18.2 L 5 15.8 Z O 12 12 3");
    ICONS.put(
        "eye_off",
        "M 2 12 L 5 8.2 L 8.5 5.8 L 12 5 L 15.5 5.8 L 19 8.2 L 22 12 L 19 15.8 L 15.5 18.2"
            + " L 12 19 L 8.5 18.2 L 5 15.8 Z M 3 3 L 21 21");
    ICONS.put(
        "sliders",
        "M 3 7 L 6.5 7 O 9 7 2.5 M 11.5 7 L 21 7 M 3 17 L 12.5 17 O 15 17 2.5 M 17.5 17 L 21 17");
    ICONS.put("sparkle", "P 12 2.5 14.2 9.8 21.5 12 14.2 14.2 12 21.5 9.8 14.2 2.5 12 9.8 9.8");
    ICONS.put("panels", "M 3 5 L 21 5 L 21 19 L 3 19 Z M 9 5 L 9 19 M 15 5 L 15 19");
    ICONS.put(
        "cut", "O 6 6 3 O 6 18 3 M 20 4 L 8.2 15.8 M 14.5 14.5 L 20 20 M 8.2 8.2 L 11.8 11.8");
    ICONS.put("plus", "M 12 5 L 12 19 M 5 12 L 19 12");
    ICONS.put("minus", "M 5 12 L 19 12");
    ICONS.put("key", "P 12 3.5 20.5 12 12 20.5 3.5 12");
    ICONS.put("key_add", "M 10 3.5 L 16.5 10 L 10 16.5 L 3.5 10 Z M 18 14 L 18 22 M 14 18 L 22 18");
    ICONS.put("close", "M 6 6 L 18 18 M 18 6 L 6 18");
    ICONS.put(
        "trash",
        "M 4 7 L 20 7 M 9 7 L 9 4 L 15 4 L 15 7 M 6 7 L 7 20 L 17 20 L 18 7 M 10 11 L 10 16"
            + " M 14 11 L 14 16");
    ICONS.put(
        "wave",
        "M 2.5 15 L 4.5 11 L 7 8.5 L 9.5 8.8 L 12 12 L 14.5 15.2 L 17 15.5 L 19.5 13 L 21.5 9");
    ICONS.put("in", "M 10 4 L 6 4 L 6 20 L 10 20 M 11 12 L 19 12 M 15.5 8.5 L 19 12 L 15.5 15.5");
    ICONS.put("out", "M 14 4 L 18 4 L 18 20 L 14 20 M 13 12 L 5 12 M 8.5 8.5 L 5 12 L 8.5 15.5");
    ICONS.put(
        "fit",
        "M 4 9 L 4 4 L 9 4 M 15 4 L 20 4 L 20 9 M 20 15 L 20 20 L 15 20 M 9 20 L 4 20 L 4 15");
    ICONS.put("search", "O 10.5 10.5 6 M 20 20 L 15 15");
    ICONS.put("flag", "M 6 21 L 6 4 L 17.5 4 L 15 8 L 17.5 12 L 6 12");
    ICONS.put("camera", "M 2 7 L 15 7 L 15 17 L 2 17 Z M 15 11 L 22 7.5 L 22 16.5 L 15 13");
    ICONS.put(
        "path",
        "M 3 18 L 5 15 L 7.5 13 L 10 13.2 L 12.5 14 L 14.5 12.5 L 16.5 10 L 18.5 8 L 21 6"
            + " D 3 18 2 D 21 6 2");
    ICONS.put("down", "M 6 9 L 12 15 L 18 9");
    ICONS.put("right", "M 9 6 L 15 12 L 9 18");
    ICONS.put("text", "M 5 7 L 5 4 L 19 4 L 19 7 M 12 4 L 12 20 M 9 20 L 15 20");
    ICONS.put("cursor", "P 4.5 3 11.5 20.5 13.8 13.8 20.5 11.5");
    ICONS.put("film", "M 3 5 L 21 5 L 21 19 L 3 19 Z M 7 5 L 7 19 M 17 5 L 17 19");
    ICONS.put("magnet", "M 6 4 L 6 12 A 12 12 6 180 0 M 18 12 L 18 4 M 3.5 8 L 8.5 8 M 15.5 8 L 20.5 8");
    ICONS.put("grip", "D 9 7 1.5 D 15 7 1.5 D 9 12 1.5 D 15 12 1.5 D 9 17 1.5 D 15 17 1.5");
  }

  /** Runs of one alpha: y, x0, x1, alpha (0-255), in icon pixels. */
  private record Run(int y, int x0, int x1, int alpha) {}

  private static final Map<String, List<Run>> CACHE = new HashMap<>();

  private LineIcons() {}

  public static boolean has(String name) {
    return ICONS.containsKey(name);
  }

  /** Draws {@code name} centred on (cx, cy), {@code size} GUI pixels wide. */
  public static void draw(GuiGraphics g, String name, int cx, int cy, int size, int color) {
    String spec = ICONS.get(name);
    if (spec == null) {
      return;
    }
    int s = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
    int px = size * s;
    List<Run> runs = CACHE.computeIfAbsent(name + "@" + px, k -> rasterise(spec, px));
    int ox = cx * s - px / 2;
    int oy = cy * s - px / 2;
    if (s % 2 == 1 || size % 2 == 1) {
      // Centre odd sizes on the GUI pixel's middle.
      ox = (cx * s + s / 2) - px / 2;
      oy = (cy * s + s / 2) - px / 2;
    }
    int alpha = (color >>> 24) & 0xFF;
    g.pose().pushMatrix();
    g.pose().scale(1f / s, 1f / s);
    for (Run r : runs) {
      int a = r.alpha * alpha / 255;
      g.fill(ox + r.x0, oy + r.y, ox + r.x1, oy + r.y + 1, Theme.alpha(color, a));
    }
    g.pose().popMatrix();
  }

  // --- Rasterising ------------------------------------------------------------------------

  private static List<Run> rasterise(String spec, int px) {
    List<double[]> segments = new ArrayList<>(); // x0 y0 x1 y1, stroked
    List<double[]> circles = new ArrayList<>(); // cx cy r, stroked
    List<double[]> discs = new ArrayList<>(); // cx cy r, filled
    List<double[]> polygons = new ArrayList<>(); // x y x y ..., filled
    String[] t = spec.trim().split("\\s+");
    int i = 0;
    double startX = 0;
    double startY = 0;
    double lastX = 0;
    double lastY = 0;
    while (i < t.length) {
      String c = t[i++];
      switch (c) {
        case "M" -> {
          lastX = startX = Double.parseDouble(t[i++]);
          lastY = startY = Double.parseDouble(t[i++]);
        }
        case "L" -> {
          double x = Double.parseDouble(t[i++]);
          double y = Double.parseDouble(t[i++]);
          segments.add(new double[] {lastX, lastY, x, y});
          lastX = x;
          lastY = y;
        }
        case "Z" -> {
          segments.add(new double[] {lastX, lastY, startX, startY});
          lastX = startX;
          lastY = startY;
        }
        case "O", "D" -> {
          double[] circle = {
            Double.parseDouble(t[i++]), Double.parseDouble(t[i++]), Double.parseDouble(t[i++])
          };
          (c.equals("O") ? circles : discs).add(circle);
        }
        case "A" -> {
          double cx = Double.parseDouble(t[i++]);
          double cy = Double.parseDouble(t[i++]);
          double r = Double.parseDouble(t[i++]);
          double from = Math.toRadians(Double.parseDouble(t[i++]));
          double to = Math.toRadians(Double.parseDouble(t[i++]));
          int steps = 16;
          double px0 = cx + r * Math.cos(from);
          double py0 = cy + r * Math.sin(from);
          for (int k = 1; k <= steps; k++) {
            double a = from + (to - from) * k / steps;
            double x = cx + r * Math.cos(a);
            double y = cy + r * Math.sin(a);
            segments.add(new double[] {px0, py0, x, y});
            px0 = x;
            py0 = y;
          }
          lastX = px0;
          lastY = py0;
        }
        case "P" -> {
          List<Double> pts = new ArrayList<>();
          while (i < t.length && !Character.isLetter(t[i].charAt(0))) {
            pts.add(Double.parseDouble(t[i++]));
          }
          double[] poly = new double[pts.size()];
          for (int k = 0; k < poly.length; k++) {
            poly[k] = pts.get(k);
          }
          polygons.add(poly);
        }
        default -> throw new IllegalArgumentException("bad icon command " + c);
      }
    }
    double unit = px / 24.0; // pixels per icon unit
    double half = Math.max(0.55, 1.0 * unit) ; // half the stroke width, in pixels
    List<Run> runs = new ArrayList<>();
    for (int y = 0; y < px; y++) {
      int runStart = -1;
      int runAlpha = 0;
      for (int x = 0; x <= px; x++) {
        int a = 0;
        if (x < px) {
          double ux = (x + 0.5) / unit;
          double uy = (y + 0.5) / unit;
          double cover = 0;
          for (double[] s : segments) {
            double d = segmentDistance(ux, uy, s[0], s[1], s[2], s[3]) * unit;
            cover = Math.max(cover, half + 0.5 - d);
          }
          for (double[] c : circles) {
            double d = Math.abs(Math.hypot(ux - c[0], uy - c[1]) - c[2]) * unit;
            cover = Math.max(cover, half + 0.5 - d);
          }
          for (double[] c : discs) {
            double d = (Math.hypot(ux - c[0], uy - c[1]) - c[2]) * unit;
            cover = Math.max(cover, 0.5 - d);
          }
          for (double[] p : polygons) {
            double d = polygonEdgeDistance(ux, uy, p) * unit;
            cover = Math.max(cover, inside(ux, uy, p) ? 0.5 + d : 0.5 - d);
          }
          // 16 levels, so neighbouring pixels merge into runs.
          a = (int) Math.round(Math.max(0, Math.min(1, cover)) * 16) * 255 / 16;
        }
        if (a != runAlpha || x == px) {
          if (runStart >= 0 && runAlpha > 0) {
            runs.add(new Run(y, runStart, x, runAlpha));
          }
          runStart = x;
          runAlpha = a;
        }
      }
    }
    return runs;
  }

  private static double segmentDistance(
      double px, double py, double x0, double y0, double x1, double y1) {
    double dx = x1 - x0;
    double dy = y1 - y0;
    double len = dx * dx + dy * dy;
    double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - x0) * dx + (py - y0) * dy) / len));
    return Math.hypot(px - (x0 + t * dx), py - (y0 + t * dy));
  }

  private static double polygonEdgeDistance(double px, double py, double[] p) {
    double best = Double.MAX_VALUE;
    int n = p.length / 2;
    for (int k = 0; k < n; k++) {
      int j = (k + 1) % n;
      best = Math.min(best, segmentDistance(px, py, p[2 * k], p[2 * k + 1], p[2 * j], p[2 * j + 1]));
    }
    return best;
  }

  private static boolean inside(double px, double py, double[] p) {
    boolean in = false;
    int n = p.length / 2;
    for (int k = 0, j = n - 1; k < n; j = k++) {
      double xk = p[2 * k];
      double yk = p[2 * k + 1];
      double xj = p[2 * j];
      double yj = p[2 * j + 1];
      if ((yk > py) != (yj > py) && px < (xj - xk) * (py - yk) / (yj - yk) + xk) {
        in = !in;
      }
    }
    return in;
  }
}
