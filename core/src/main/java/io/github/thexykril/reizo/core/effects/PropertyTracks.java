/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.effects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Keyframed values of game and mod settings over replay time, one track per setting id (for example
 * {@code minecraft:gamma} or {@code oneconfig:<config>/<path>}). Numbers blend linearly between
 * keyframes; toggles switch at each keyframe. Before the first and after the last keyframe the
 * value holds. The ids mean nothing here: the game side resolves them.
 */
public final class PropertyTracks {
  /** How a setting's values move between keyframes. */
  public enum Kind {
    /** Linear blend. */
    NUMBER,
    /** Holds each keyframe's value until the next one (0 = off, anything else = on). */
    TOGGLE
  }

  /** One keyframe of one setting. */
  public record Keyframe(String property, Kind kind, long timeMs, double value) {}

  private static final class Track {
    Kind kind;
    final TreeMap<Long, Double> keys = new TreeMap<>();

    Track(Kind kind) {
      this.kind = kind;
    }
  }

  private final TreeMap<String, Track> tracks = new TreeMap<>();

  /** Sets a keyframe, replacing one at the same time. A different kind replaces the track's. */
  public void put(String property, Kind kind, long timeMs, double value) {
    Track t = tracks.computeIfAbsent(property, p -> new Track(kind));
    t.kind = kind;
    t.keys.put(timeMs, value);
  }

  /** Removes a keyframe; a track without keyframes is dropped. */
  public boolean remove(String property, long timeMs) {
    Track t = tracks.get(property);
    if (t == null || t.keys.remove(timeMs) == null) {
      return false;
    }
    if (t.keys.isEmpty()) {
      tracks.remove(property);
    }
    return true;
  }

  /** Drops a setting's whole track. */
  public boolean removeProperty(String property) {
    return tracks.remove(property) != null;
  }

  public void clear() {
    tracks.clear();
  }

  public boolean isEmpty() {
    return tracks.isEmpty();
  }

  /** The settings that have keyframes, sorted. */
  public Set<String> properties() {
    return Collections.unmodifiableSet(tracks.keySet());
  }

  public boolean has(String property) {
    return tracks.containsKey(property);
  }

  /** The kind of a setting's track, or null without one. */
  public Kind kind(String property) {
    Track t = tracks.get(property);
    return t == null ? null : t.kind;
  }

  /** Every keyframe, sorted by setting, then time. */
  public List<Keyframe> keyframes() {
    List<Keyframe> out = new ArrayList<>();
    tracks.forEach(
        (p, t) -> t.keys.forEach((time, v) -> out.add(new Keyframe(p, t.kind, time, v))));
    return Collections.unmodifiableList(out);
  }

  /** One setting's keyframes, sorted by time. */
  public List<Keyframe> keyframes(String property) {
    Track t = tracks.get(property);
    if (t == null) {
      return List.of();
    }
    List<Keyframe> out = new ArrayList<>();
    t.keys.forEach((time, v) -> out.add(new Keyframe(property, t.kind, time, v)));
    return Collections.unmodifiableList(out);
  }

  /** Replaces everything with these keyframes. */
  public void setAll(List<Keyframe> keyframes) {
    tracks.clear();
    for (Keyframe k : keyframes) {
      put(k.property(), k.kind(), k.timeMs(), k.value());
    }
  }

  /** The keyframe time of a setting within {@code toleranceMs} of {@code timeMs}, or -1. */
  public long nearest(String property, long timeMs, long toleranceMs) {
    Track t = tracks.get(property);
    if (t == null) {
      return -1;
    }
    Long below = t.keys.floorKey(timeMs);
    Long above = t.keys.ceilingKey(timeMs);
    long best = -1;
    long bestDist = Long.MAX_VALUE;
    for (Long k : new Long[] {below, above}) {
      if (k != null && Math.abs(k - timeMs) <= toleranceMs && Math.abs(k - timeMs) < bestDist) {
        best = k;
        bestDist = Math.abs(k - timeMs);
      }
    }
    return best;
  }

  /** A setting's value at a replay time, or NaN when it has no keyframes. */
  public double valueAt(String property, double timeMs) {
    Track t = tracks.get(property);
    if (t == null) {
      return Double.NaN;
    }
    Map.Entry<Long, Double> before = t.keys.floorEntry((long) Math.floor(timeMs));
    if (before == null) {
      return t.keys.firstEntry().getValue();
    }
    if (t.kind == Kind.TOGGLE) {
      return before.getValue();
    }
    Map.Entry<Long, Double> after = t.keys.ceilingEntry((long) Math.ceil(timeMs));
    if (after == null || after.getKey().equals(before.getKey())) {
      return before.getValue();
    }
    double f = (timeMs - before.getKey()) / (double) (after.getKey() - before.getKey());
    return before.getValue() + (after.getValue() - before.getValue()) * f;
  }
}
