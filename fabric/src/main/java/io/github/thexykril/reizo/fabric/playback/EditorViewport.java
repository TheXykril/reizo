/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.playback;

/**
 * Where the editor shows the world picture: a box beside its panels, like a video editor's
 * viewer. {@link EffectRenderer} scales the picture into the box and fills the rest with a
 * backdrop. Set by the editor screen each frame it is open.
 */
public final class EditorViewport {
  private static volatile float[] rect;
  private static volatile int backdrop = 0xFF0E1014;

  private EditorViewport() {}

  /**
   * Shows the world in a box, as fractions of the window from its top left corner, with {@code
   * argb} around it.
   */
  public static void set(float x, float y, float width, float height, int argb) {
    if (width <= 0 || height <= 0) {
      rect = null;
      return;
    }
    rect = new float[] {x, y, width, height};
    backdrop = argb;
  }

  /** Shows the world on the whole window again. */
  public static void clear() {
    rect = null;
  }

  /** The box (x, y from the top, width, height; fractions of the window), or null for none. */
  public static float[] rect() {
    return rect;
  }

  public static int backdrop() {
    return backdrop;
  }
}
