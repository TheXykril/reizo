/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.playback;

import java.util.Locale;

/**
 * Time of day and weather chosen in the editor, shown instead of the recorded ones while a replay
 * plays (and in its exports). NaN means "as recorded". Set through keyframed settings; cleared when
 * their tracks go away or the replay closes. The recording itself is never changed: the game keeps
 * tracking the recorded time and weather underneath, so removing an override goes straight back to
 * them. Render (client) thread only.
 */
public final class ReplayWorldLook {
  /** Ticks in a Minecraft day. */
  public static final long DAY = 24_000;

  private static double timeOfDay = Double.NaN;
  private static double rain = Double.NaN;
  private static double thunder = Double.NaN;

  private ReplayWorldLook() {}

  /** Whether overrides apply now: only inside a replay. */
  private static boolean active() {
    return ReplayPlayback.isActive();
  }

  /** The day time to show for a recorded day time (ticks, counting days). */
  public static long dayTime(long recorded) {
    if (Double.isNaN(timeOfDay) || !active()) {
      return recorded;
    }
    // Keep the recorded day, so the moon phase stays as recorded.
    return Math.floorDiv(recorded, DAY) * DAY + Math.round(timeOfDay) % DAY;
  }

  /** The rain level to show, 0 to 1, for the recorded one. */
  public static float rain(float recorded) {
    return Double.isNaN(rain) || !active() ? recorded : (float) rain;
  }

  /** Whether thunder is overridden. */
  public static boolean thunderOverridden() {
    return !Double.isNaN(thunder) && active();
  }

  /** The thunder strength set in the editor, 0 to 1 (Minecraft scales it by the rain level). */
  public static float thunder() {
    return (float) thunder;
  }

  public static double timeOfDayOverride() {
    return timeOfDay;
  }

  public static double rainOverride() {
    return rain;
  }

  public static double thunderOverride() {
    return thunder;
  }

  public static void setTimeOfDay(double ticks) {
    timeOfDay = Double.isNaN(ticks) ? ticks : Math.max(0, Math.min(DAY, ticks));
  }

  public static void setRain(double level) {
    rain = Double.isNaN(level) ? level : Math.max(0, Math.min(1, level));
  }

  public static void setThunder(double level) {
    thunder = Double.isNaN(level) ? level : Math.max(0, Math.min(1, level));
  }

  /** Back to the recorded time and weather. */
  public static void clear() {
    timeOfDay = Double.NaN;
    rain = Double.NaN;
    thunder = Double.NaN;
  }

  /** A time of day in ticks as a clock time: tick 0 is 06:00, 6000 is noon, 18000 is midnight. */
  public static String clock(double ticks) {
    long minutes = Math.round((ticks / DAY) * 24 * 60 + 6 * 60) % (24 * 60);
    return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
  }
}
