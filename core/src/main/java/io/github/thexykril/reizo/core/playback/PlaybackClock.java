/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.playback;

import java.util.function.LongSupplier;

/**
 * Replay time in milliseconds, driven by a nanosecond wall clock. Starts paused at 0. Speed and
 * pause changes keep the current replay time continuous. Thread-safe.
 */
public final class PlaybackClock {
  private final LongSupplier nanoClock;

  // Guarded by "this". Replay time = baseMs + (now - baseNanos) * speed while running.
  private double baseMs;
  private long baseNanos;
  private double speed = 1.0;
  private boolean running;

  public PlaybackClock(LongSupplier nanoClock) {
    this.nanoClock = nanoClock;
  }

  /** Current replay time in milliseconds. */
  public synchronized long timeMs() {
    return (long) currentMs();
  }

  public synchronized boolean isRunning() {
    return running;
  }

  public synchronized double speed() {
    return speed;
  }

  public synchronized void resume() {
    if (!running) {
      baseNanos = nanoClock.getAsLong();
      running = true;
    }
  }

  public synchronized void pause() {
    if (running) {
      baseMs = currentMs();
      running = false;
    }
  }

  /**
   * Sets the playback speed (1.0 = real time).
   *
   * @throws IllegalArgumentException unless 0 &lt; speed &lt;= 64
   */
  public synchronized void setSpeed(double newSpeed) {
    if (!(newSpeed > 0 && newSpeed <= 64)) {
      throw new IllegalArgumentException("speed out of range: " + newSpeed);
    }
    rebase();
    speed = newSpeed;
  }

  private void rebase() {
    baseMs = currentMs();
    baseNanos = nanoClock.getAsLong();
  }

  private double currentMs() {
    if (!running) {
      return baseMs;
    }
    return baseMs + (nanoClock.getAsLong() - baseNanos) / 1_000_000.0 * speed;
  }
}
