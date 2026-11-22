/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.timeline;

/**
 * The visible part of a timeline: which span of replay time is shown across a given width in
 * pixels. Handles conversion between time and x, zooming around a point, and scrolling. The view
 * never shows time before 0 or after the duration, and never zooms in further than {@link
 * #MIN_SPAN_MS}.
 */
public final class TimelineView {
  public static final long MIN_SPAN_MS = 500;

  /** Candidate spacings for ruler ticks, smallest first. */
  private static final long[] TICK_STEPS = {
    100, 250, 500, 1_000, 2_000, 5_000, 10_000, 15_000, 30_000, 60_000, 120_000, 300_000, 600_000,
    1_800_000, 3_600_000
  };

  private final long durationMs;
  private int widthPx = 1;
  private double startMs;
  private double spanMs;

  public TimelineView(long durationMs) {
    this.durationMs = Math.max(1, durationMs);
    this.spanMs = this.durationMs;
  }

  public long durationMs() {
    return durationMs;
  }

  public long startMs() {
    return Math.round(startMs);
  }

  public long spanMs() {
    return Math.round(spanMs);
  }

  public long endMs() {
    return Math.round(startMs + spanMs);
  }

  public int widthPx() {
    return widthPx;
  }

  public void setWidth(int px) {
    widthPx = Math.max(1, px);
  }

  /** The x offset (from the timeline's left edge) of a time; may be outside 0..width. */
  public double timeToX(long timeMs) {
    return (timeMs - startMs) * widthPx / spanMs;
  }

  /** The time at an x offset, clamped to the recording. */
  public long xToTime(double x) {
    long t = Math.round(startMs + x * spanMs / widthPx);
    return Math.max(0, Math.min(t, durationMs));
  }

  /** Milliseconds covered by one pixel. */
  public double msPerPixel() {
    return spanMs / widthPx;
  }

  /** Zooms by a factor (above 1 zooms in) keeping the time under {@code anchorX} where it is. */
  public void zoom(double factor, double anchorX) {
    if (factor <= 0) {
      return;
    }
    double anchorTime = startMs + anchorX * spanMs / widthPx;
    double span = Math.max(MIN_SPAN_MS, Math.min(durationMs, spanMs / factor));
    startMs = anchorTime - anchorX * span / widthPx;
    spanMs = span;
    clamp();
  }

  /** Scrolls by a number of pixels (positive moves later in time). */
  public void scrollPx(double px) {
    startMs += px * spanMs / widthPx;
    clamp();
  }

  /** Shows the whole recording. */
  public void showAll() {
    startMs = 0;
    spanMs = durationMs;
  }

  /** Scrolls the least amount that brings a time into view. */
  public void ensureVisible(long timeMs) {
    if (timeMs < startMs) {
      startMs = timeMs;
    } else if (timeMs > startMs + spanMs) {
      startMs = timeMs - spanMs;
    }
    clamp();
  }

  /** The smallest tick spacing that keeps ticks at least {@code minPx} apart. */
  public long tickStepMs(int minPx) {
    for (long step : TICK_STEPS) {
      if (step / msPerPixel() >= minPx) {
        return step;
      }
    }
    return TICK_STEPS[TICK_STEPS.length - 1];
  }

  private void clamp() {
    spanMs = Math.max(Math.min(MIN_SPAN_MS, durationMs), Math.min(spanMs, durationMs));
    startMs = Math.max(0, Math.min(startMs, durationMs - spanMs));
  }
}
