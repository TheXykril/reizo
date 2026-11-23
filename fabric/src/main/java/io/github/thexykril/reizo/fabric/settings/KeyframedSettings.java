/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.settings;

import io.github.thexykril.reizo.core.effects.PropertyTracks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The settings the timeline can keyframe, and the driver that applies a project's setting tracks
 * while its replay plays: Minecraft's own video settings, plus every number and switch of every
 * OneConfig config when OneConfig is installed (OneClient's mods). The first time a track touches a
 * setting its live value is remembered; it is put back when the track goes away or the replay
 * closes, so a replay never leaves the player's settings changed. Render (client) thread only.
 */
public final class KeyframedSettings {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Settings");

  private static final Map<String, KeyframedSetting> byId = new HashMap<>();
  private static final Map<String, Double> originals = new HashMap<>();
  private static boolean oneConfigFailed;

  private KeyframedSettings() {}

  /**
   * Every setting, Minecraft's first, then OneConfig's by config. Looks them up again each call.
   */
  public static List<KeyframedSetting> all() {
    List<KeyframedSetting> out = new ArrayList<>(VanillaSettings.all());
    if (!oneConfigFailed && FabricLoader.getInstance().isModLoaded("oneconfig")) {
      try {
        out.addAll(io.github.thexykril.reizo.fabric.compat.OneConfigSettings.all());
      } catch (Throwable t) {
        // Another OneConfig version with a different API: keep Minecraft's settings working.
        oneConfigFailed = true;
        LOGGER.warn("Could not list OneConfig settings; only Minecraft's can be keyframed", t);
      }
    }
    byId.clear();
    for (KeyframedSetting s : out) {
      byId.putIfAbsent(s.id(), s);
    }
    return out;
  }

  /** The setting with this id, or null when it is not installed. */
  public static KeyframedSetting find(String id) {
    KeyframedSetting s = byId.get(id);
    if (s == null && byId.isEmpty()) {
      all();
      s = byId.get(id);
    }
    return s;
  }

  /** Whether a setting's value is currently driven by a track (and so saved to restore later). */
  public static boolean isDriven(String id) {
    return originals.containsKey(id);
  }

  /** The value the player had before the replay drove it, or the live value. */
  public static double original(KeyframedSetting s) {
    Double v = originals.get(s.id());
    return v != null ? v : s.get();
  }

  /** Applies the tracks at a replay time. Settings whose track went away are put back. */
  public static void apply(PropertyTracks tracks, double timeMs) {
    Set<String> ids = tracks.properties();
    originals
        .entrySet()
        .removeIf(
            e -> {
              if (ids.contains(e.getKey())) {
                return false;
              }
              KeyframedSetting s = find(e.getKey());
              if (s != null) {
                restoreSafely(s, e.getValue());
              }
              return true;
            });
    for (String id : ids) {
      KeyframedSetting s = find(id);
      if (s == null) {
        continue; // the mod is not installed now; the track stays in the project
      }
      double v = tracks.valueAt(id, timeMs);
      if (Double.isNaN(v)) {
        continue;
      }
      if (!originals.containsKey(id)) {
        originals.put(id, s.get());
      }
      if (s.get() != v) {
        setSafely(s, v);
      }
    }
  }

  /** Puts back every setting a replay changed. */
  public static void restoreAll() {
    if (originals.isEmpty()) {
      return;
    }
    for (Map.Entry<String, Double> e : originals.entrySet()) {
      KeyframedSetting s = find(e.getKey());
      if (s != null) {
        restoreSafely(s, e.getValue());
      }
    }
    originals.clear();
  }

  private static void setSafely(KeyframedSetting s, double v) {
    try {
      s.set(v);
    } catch (RuntimeException e) {
      LOGGER.warn("Could not set {}", s.id(), e);
    }
  }

  private static void restoreSafely(KeyframedSetting s, double v) {
    try {
      s.restore(v);
    } catch (RuntimeException e) {
      LOGGER.warn("Could not restore {}", s.id(), e);
    }
  }

  /** Clamps a value into the setting's range (and to 0 or 1 for a toggle). */
  public static double clamp(KeyframedSetting s, double v) {
    if (s.kind() == PropertyTracks.Kind.TOGGLE) {
      return v != 0 ? 1 : 0;
    }
    return Math.max(s.min(), Math.min(s.max(), v));
  }
}
