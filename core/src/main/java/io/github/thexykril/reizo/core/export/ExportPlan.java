/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.export;

/**
 * The frames of an export: a fixed frame rate over a replay time range. Frame {@code i} shows the
 * replay at {@link #timeOfFrame(int)}, computed from the index (never accumulated), so long exports
 * do not drift.
 */
public record ExportPlan(long startMs, long endMs, int fps) {
  public ExportPlan {
    if (fps < 1 || fps > 240) {
      throw new IllegalArgumentException("fps out of range: " + fps);
    }
    if (startMs < 0 || endMs <= startMs) {
      throw new IllegalArgumentException("bad range " + startMs + ".." + endMs);
    }
  }

  /** Number of frames; the last frame is at or before the end. */
  public int frameCount() {
    return (int) ((endMs - startMs) * fps / 1000) + 1;
  }

  /** Replay time of a frame, in milliseconds (fractional). */
  public double timeOfFrame(int frame) {
    return startMs + frame * 1000.0 / fps;
  }

  /** Progress after a number of frames, 0..1. */
  public double progress(int framesDone) {
    return Math.min(1.0, (double) framesDone / frameCount());
  }
}
