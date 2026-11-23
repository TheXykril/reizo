/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.theme;

import java.util.Map;

/**
 * The editor's colours (ARGB) and look. {@link #DEFAULT} is Reizo's own dark theme; {@link
 * #fromOneConfig} builds one from the colours of the theme selected in OneConfig, so Reizo follows
 * it.
 */
public record Palette(
    int ground,
    int surface,
    int raised,
    int hover,
    int line,
    int trackBg,
    int textColor,
    int dim,
    int faint,
    int accent,
    int accentHover,
    int danger,
    int warn,
    boolean light,
    Look look) {

  /** How shapes and text are drawn. */
  public enum Look {
    /** Opaque rounded surfaces and the Inter font. */
    FLAT,
    /** Translucent rounded surfaces and the Inter font. */
    GLASS,
    /** Square corners and Minecraft's own font. */
    PIXEL
  }

  public static final Palette DEFAULT =
      new Palette(
          0xF011171C,
          0xF0171E24,
          0xFF1F272E,
          0xFF29323A,
          0xFF2A343D,
          0xFF36434F,
          0xFFE6EDF3,
          0xFF8B98A5,
          0xFF56626D,
          0xFF1878F1,
          0xFF3A8FF5,
          0xFFE5484D,
          0xFFF5A524,
          false,
          Look.FLAT);

  /**
   * ARGB of a Jetpack Compose colour value (the packed {@code long} Compose uses), or {@code null}
   * when it is not an sRGB colour.
   */
  public static Integer composeToArgb(long value) {
    // sRGB colours keep ARGB in the high 32 bits and colour space 0 in the low 6 bits.
    if ((value & 0x3F) != 0) {
      return null;
    }
    return (int) (value >>> 32);
  }

  /**
   * A palette from OneConfig theme colours by name (page, sidebar, component, popup, border, text,
   * textSecondary, muted, accent, error, warning, track); missing names keep {@code fallback}'s.
   */
  public static Palette fromOneConfig(
      Map<String, Integer> c, String style, boolean light, Palette fallback) {
    Look look =
        switch (style == null ? "" : style.toLowerCase(java.util.Locale.ROOT)) {
          case "glass" -> Look.GLASS;
          case "pixel" -> Look.PIXEL;
          default -> Look.FLAT;
        };
    int ground = c.getOrDefault("page", fallback.ground);
    int surface = c.getOrDefault("sidebar", c.getOrDefault("popup", fallback.surface));
    int raised = c.getOrDefault("component", fallback.raised);
    int text = c.getOrDefault("text", fallback.textColor);
    int dim = c.getOrDefault("textSecondary", fallback.dim);
    int faint = c.getOrDefault("muted", mix(dim, ground, 0.45));
    int accent = c.getOrDefault("accent", fallback.accent);
    int line = c.getOrDefault("border", mix(raised, text, 0.1));
    if (look != Look.GLASS) {
      // Reizo's widgets draw opaque pieces; keep surfaces readable over the world.
      ground = withAlpha(ground, Math.max(alpha(ground), 0xF0));
      surface = withAlpha(surface, Math.max(alpha(surface), 0xF0));
      raised = withAlpha(raised, 0xFF);
    } else {
      ground = withAlpha(ground, Math.min(Math.max(alpha(ground), 0xA0), 0xD0));
      surface = withAlpha(surface, Math.min(Math.max(alpha(surface), 0xA0), 0xD0));
      raised = withAlpha(raised, Math.max(alpha(raised), 0xC0));
    }
    return new Palette(
        ground,
        surface,
        raised,
        mix(raised, text, light ? 0.06 : 0.08),
        withAlpha(line, 0xFF),
        c.getOrDefault("track", mix(raised, text, 0.15)),
        withAlpha(text, 0xFF),
        withAlpha(dim, 0xFF),
        withAlpha(faint, 0xFF),
        withAlpha(accent, 0xFF),
        mix(withAlpha(accent, 0xFF), light ? 0xFF000000 : 0xFFFFFFFF, 0.15),
        c.getOrDefault("error", fallback.danger),
        c.getOrDefault("warning", fallback.warn),
        light,
        look);
  }

  /** {@code a} blended toward {@code b} by {@code t} per channel, alpha included. */
  public static int mix(int a, int b, double t) {
    int out = 0;
    for (int shift = 0; shift <= 24; shift += 8) {
      int ca = (a >>> shift) & 0xFF;
      int cb = (b >>> shift) & 0xFF;
      out |= ((int) Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
    }
    return out;
  }

  private static int alpha(int c) {
    return c >>> 24;
  }

  private static int withAlpha(int c, int a) {
    return (a << 24) | (c & 0xFFFFFF);
  }
}
