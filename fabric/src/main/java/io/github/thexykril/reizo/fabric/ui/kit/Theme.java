/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui.kit;

/**
 * The editor's colours (ARGB): a dark ground with a blue accent, in the spirit of OneClient's look
 * (ADR 0015), and one colour per kind of timeline track.
 */
public final class Theme {
  /** Window ground behind panels. */
  public static final int GROUND = 0xF011171C;
  /** Panel surface. */
  public static final int SURFACE = 0xF0171E24;
  /** Raised surface: track lanes, inputs, idle buttons. */
  public static final int RAISED = 0xFF1F272E;
  /** Hovered raised surface. */
  public static final int HOVER = 0xFF29323A;
  /** Hairlines between panels. */
  public static final int LINE = 0xFF2A343D;
  /** Empty slider and progress tracks. */
  public static final int TRACK_BG = 0xFF36434F;
  public static final int TEXT = 0xFFE6EDF3;
  public static final int DIM = 0xFF8B98A5;
  public static final int FAINT = 0xFF56626D;
  public static final int ACCENT = 0xFF1878F1;
  public static final int ACCENT_HOVER = 0xFF3A8FF5;
  /** Accent at low alpha, for selections and regions. */
  public static final int ACCENT_WASH = 0x401878F1;
  public static final int DANGER = 0xFFE5484D;
  public static final int WARN = 0xFFF5A524;

  // Track colours.
  public static final int TRACK_MARKER = WARN;
  public static final int TRACK_CAMERA = ACCENT;
  public static final int TRACK_LOOK = 0xFFA371F7;
  public static final int TRACK_WORLD = 0xFF2FBF9F;
  public static final int TRACK_ENTITY = 0xFFE879A6;
  public static final int TRACK_SETTINGS = 0xFF7FB4FF;
  public static final int PLAYHEAD = DANGER;

  private Theme() {}

  /** {@code color} with its alpha replaced. */
  public static int alpha(int color, int alpha) {
    return (alpha << 24) | (color & 0xFFFFFF);
  }
}
