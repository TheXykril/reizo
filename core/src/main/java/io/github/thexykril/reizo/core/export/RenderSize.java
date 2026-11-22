/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.export;

/**
 * The size the game renders at during an export. With an output size set, the game renders at that
 * size (even, for 4:2:0 encoders) instead of the window size, so a 4K export has 4K detail rather
 * than an upscaled window. When the GPU cannot hold a target that large, it falls back to the
 * window size and FFmpeg scales.
 */
public record RenderSize(int width, int height) {
  /**
   * @param outWidth requested output width, 0 for the window size
   * @param maxTexture the GPU's largest texture side
   */
  public static RenderSize of(
      int outWidth, int outHeight, int windowWidth, int windowHeight, int maxTexture) {
    if (outWidth <= 0 || outHeight <= 0) {
      return new RenderSize(windowWidth, windowHeight);
    }
    int w = Math.max(2, outWidth - outWidth % 2);
    int h = Math.max(2, outHeight - outHeight % 2);
    if (w > maxTexture || h > maxTexture) {
      return new RenderSize(windowWidth, windowHeight);
    }
    return new RenderSize(w, h);
  }

  public boolean matches(int w, int h) {
    return width == w && height == h;
  }
}
