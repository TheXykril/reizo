/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.export;

/**
 * The frames of an export: a fixed frame rate over a replay time range. Frame {@code i} shows the
 * replay at {@link #timeOfFrame(int)}, computed from the index (never accumulated), so long exports
 * do not drift.
 *
 * <p>With motion blur, each frame is the average of {@code samples} renders spread over the {@code
 * shutter} part of the frame interval (0.5 = a 180° shutter, like film cameras).
 */
public record ExportPlan(long startMs, long endMs, int fps, int samples, double shutter) {
  public ExportPlan(long startMs, long endMs, int fps) {
    this(startMs, endMs, fps, 1, 0);
  }

  public ExportPlan {
    if (samples < 1 || samples > 32) {
      throw new IllegalArgumentException("samples out of range: " + samples);
    }
    if (!(shutter >= 0 && shutter <= 1)) {
      throw new IllegalArgumentException("shutter out of range: " + shutter);
    }
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

  /** Number of renders: {@link #samples()} per frame. */
  public int sampleCount() {
    return frameCount() * samples;
  }

  /** Replay time of render {@code i}: frame {@code i / samples}, opened by the shutter. */
  public double timeOfSample(int i) {
    int k = i % samples;
    return timeOfFrame(i / samples) + k * (shutter * 1000.0 / fps) / samples;
  }

  /** Progress after a number of frames, 0..1. */
  public double progress(int framesDone) {
    return Math.min(1.0, (double) framesDone / frameCount());
  }
}
