/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.settings;

import io.github.thexykril.reizo.core.effects.PropertyTracks;

/**
 * A game or mod setting that the timeline can keyframe. Numbers are doubles; toggles are 0 and 1.
 * Client thread only.
 */
public interface KeyframedSetting {
  /** Stable id stored in projects, for example {@code minecraft:gamma}. */
  String id();

  /** Who owns the setting, shown as a heading: "Minecraft" or a mod's config name. */
  String group();

  /** The setting's name as its owner shows it. */
  String label();

  PropertyTracks.Kind kind();

  double min();

  double max();

  /** The live value. */
  double get();

  /** Sets the live value (clamped to the range by the caller). */
  void set(double value);

  /**
   * Puts the setting back once no track drives it, given the value it had before. Settings that
   * override something recorded (time of day, weather) go back to following the recording instead.
   */
  default void restore(double original) {
    set(original);
  }

  /** The value as the timeline shows it. */
  default String format(double value) {
    return String.format(java.util.Locale.ROOT, "%.2f", value);
  }
}
