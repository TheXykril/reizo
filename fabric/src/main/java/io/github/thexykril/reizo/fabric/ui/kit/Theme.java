/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui.kit;

import io.github.thexykril.reizo.core.theme.Palette;
import io.github.thexykril.reizo.fabric.compat.OneConfigThemeSync;

/**
 * The editor's colours (ARGB) and one colour per kind of timeline track. They start as Reizo's own
 * dark theme (ADR 0015) and follow the theme selected in OneConfig when OneConfig is installed
 * ({@link #refresh}, called each frame by Reizo's screens and HUD).
 */
public final class Theme {
  /** Window ground behind panels. */
  public static int GROUND;
  /** Panel surface. */
  public static int SURFACE;
  /** Raised surface: track lanes, inputs, idle buttons. */
  public static int RAISED;
  /** Hovered raised surface. */
  public static int HOVER;
  /** Hairlines between panels. */
  public static int LINE;
  /** Empty slider and progress tracks. */
  public static int TRACK_BG;
  public static int TEXT;
  public static int DIM;
  public static int FAINT;
  public static int ACCENT;
  public static int ACCENT_HOVER;
  /** Accent at low alpha, for selections and regions. */
  public static int ACCENT_WASH;
  public static int DANGER;
  public static int WARN;

  // Track colours.
  public static int TRACK_MARKER;
  public static int TRACK_CAMERA;
  public static final int TRACK_LOOK = 0xFFA371F7;
  public static final int TRACK_WORLD = 0xFF2FBF9F;
  public static final int TRACK_ENTITY = 0xFFE879A6;
  public static final int TRACK_SETTINGS = 0xFF7FB4FF;
  public static int PLAYHEAD;

  private static Palette current;
  private static long checkedAt;

  static {
    apply(Palette.DEFAULT);
  }

  private Theme() {}

  /** The palette in use. */
  public static Palette palette() {
    return current;
  }

  /** Square corners and Minecraft's font (OneConfig's pixel themes). */
  public static boolean pixel() {
    return current.look() == Palette.Look.PIXEL;
  }

  /** Follows OneConfig's selected theme; checks at most twice a second. */
  public static void refresh() {
    long now = System.currentTimeMillis();
    if (now - checkedAt < 500) {
      return;
    }
    checkedAt = now;
    Palette p = OneConfigThemeSync.palette();
    apply(p == null ? Palette.DEFAULT : p);
  }

  public static void apply(Palette p) {
    if (p.equals(current)) {
      return;
    }
    current = p;
    GROUND = p.ground();
    SURFACE = p.surface();
    RAISED = p.raised();
    HOVER = p.hover();
    LINE = p.line();
    TRACK_BG = p.trackBg();
    TEXT = p.textColor();
    DIM = p.dim();
    FAINT = p.faint();
    ACCENT = p.accent();
    ACCENT_HOVER = p.accentHover();
    ACCENT_WASH = alpha(p.accent(), 0x40);
    DANGER = p.danger();
    WARN = p.warn();
    TRACK_MARKER = WARN;
    TRACK_CAMERA = ACCENT;
    PLAYHEAD = p.textColor();
  }

  /** {@code color} with its alpha replaced. */
  public static int alpha(int color, int alpha) {
    return (alpha << 24) | (color & 0xFFFFFF);
  }
}
