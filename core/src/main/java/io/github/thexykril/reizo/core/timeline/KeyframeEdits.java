/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.timeline;

import io.github.thexykril.reizo.core.camera.CameraKeyframe;
import io.github.thexykril.reizo.core.camera.CameraPath;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Timeline edits on a camera path that work on several keyframes at once. */
public final class KeyframeEdits {
  private KeyframeEdits() {}

  /**
   * Moves the keyframes at the given times by {@code deltaMs}, as a group. The delta is limited so
   * that every moved keyframe stays within 0..duration. A moved keyframe replaces an unmoved one it
   * lands on. Returns the new times of the moved keyframes.
   */
  public static Set<Long> move(
      CameraPath path, Collection<Long> times, long deltaMs, long durationMs) {
    List<CameraKeyframe> moving = new ArrayList<>();
    for (CameraKeyframe k : path.keyframes()) {
      if (times.contains(k.timeMs())) {
        moving.add(k);
      }
    }
    if (moving.isEmpty()) {
      return Set.of();
    }
    long min = moving.get(0).timeMs();
    long max = moving.get(moving.size() - 1).timeMs();
    long delta = Math.max(-min, Math.min(deltaMs, durationMs - max));
    for (CameraKeyframe k : moving) {
      path.remove(k.timeMs());
    }
    Set<Long> moved = new LinkedHashSet<>();
    for (CameraKeyframe k : moving) {
      CameraKeyframe shifted = k.at(k.timeMs() + delta);
      path.put(shifted);
      moved.add(shifted.timeMs());
    }
    return moved;
  }

  /** Removes the keyframes at the given times. Returns how many were removed. */
  public static int remove(CameraPath path, Collection<Long> times) {
    int n = 0;
    for (long t : times) {
      if (path.remove(t)) {
        n++;
      }
    }
    return n;
  }

  /** The keyframes at the given times, in time order (for copying). */
  public static List<CameraKeyframe> copy(CameraPath path, Collection<Long> times) {
    List<CameraKeyframe> out = new ArrayList<>();
    for (CameraKeyframe k : path.keyframes()) {
      if (times.contains(k.timeMs())) {
        out.add(k);
      }
    }
    return out;
  }

  /**
   * Pastes copied keyframes so that the first lands at {@code atMs}, keeping their spacing. Ones
   * that would fall after the duration are dropped. Returns the times of the pasted keyframes.
   */
  public static Set<Long> paste(
      CameraPath path, List<CameraKeyframe> copied, long atMs, long durationMs) {
    Set<Long> pasted = new LinkedHashSet<>();
    if (copied.isEmpty()) {
      return pasted;
    }
    long first = copied.get(0).timeMs();
    for (CameraKeyframe k : copied) {
      long t = Math.max(0, atMs + k.timeMs() - first);
      if (t <= durationMs) {
        path.put(k.at(t));
        pasted.add(t);
      }
    }
    return pasted;
  }

  /** Times of the keyframes between two times (inclusive, in either order), for box selection. */
  public static Set<Long> between(CameraPath path, long a, long b) {
    long lo = Math.min(a, b);
    long hi = Math.max(a, b);
    Set<Long> out = new LinkedHashSet<>();
    for (CameraKeyframe k : path.keyframes()) {
      if (k.timeMs() >= lo && k.timeMs() <= hi) {
        out.add(k.timeMs());
      }
    }
    return out;
  }
}
