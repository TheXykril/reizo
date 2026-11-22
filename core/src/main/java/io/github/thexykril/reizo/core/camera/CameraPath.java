/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.camera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A camera path: keyframes sorted by time, sampled at any replay time. Between keyframes the
 * position uses the chosen {@link Interpolation}; angles and fov follow the same curve, with yaw
 * and roll unwrapped so the camera turns the short way. Each segment's timing is shaped by the
 * {@link Easing}. Before the first keyframe and after the last, the path holds still.
 *
 * <p>Not thread-safe.
 */
public final class CameraPath {
  /** How positions are joined between keyframes. */
  public enum Interpolation {
    /** Straight lines; the camera changes direction sharply at keyframes. */
    LINEAR,
    /** Centripetal-free uniform Catmull-Rom spline through every keyframe: smooth curves. */
    CATMULL_ROM
  }

  /** Timing within each segment. */
  public enum Easing {
    LINEAR,
    /** Slow start and end in every segment (smoothstep). */
    EASE_IN_OUT
  }

  private final List<CameraKeyframe> keyframes = new ArrayList<>();
  private Interpolation interpolation = Interpolation.CATMULL_ROM;
  private Easing easing = Easing.LINEAR;

  public List<CameraKeyframe> keyframes() {
    return Collections.unmodifiableList(keyframes);
  }

  public Interpolation interpolation() {
    return interpolation;
  }

  public void setInterpolation(Interpolation interpolation) {
    this.interpolation = interpolation;
  }

  public Easing easing() {
    return easing;
  }

  public void setEasing(Easing easing) {
    this.easing = easing;
  }

  /** Adds a keyframe, replacing one at the same time. */
  public void put(CameraKeyframe k) {
    int i = indexAtOrAfter(k.timeMs());
    if (i < keyframes.size() && keyframes.get(i).timeMs() == k.timeMs()) {
      keyframes.set(i, k);
    } else {
      keyframes.add(i, k);
    }
  }

  /** Removes the keyframe at exactly this time. Returns whether there was one. */
  public boolean remove(long timeMs) {
    int i = indexAtOrAfter(timeMs);
    if (i < keyframes.size() && keyframes.get(i).timeMs() == timeMs) {
      keyframes.remove(i);
      return true;
    }
    return false;
  }

  /** Removes the keyframe nearest to a time, within a tolerance. Returns it, or null. */
  public CameraKeyframe removeNearest(long timeMs, long toleranceMs) {
    CameraKeyframe best = null;
    for (CameraKeyframe k : keyframes) {
      long d = Math.abs(k.timeMs() - timeMs);
      if (d <= toleranceMs && (best == null || d < Math.abs(best.timeMs() - timeMs))) {
        best = k;
      }
    }
    if (best != null) {
      keyframes.remove(best);
    }
    return best;
  }

  public void clear() {
    keyframes.clear();
  }

  public boolean isEmpty() {
    return keyframes.isEmpty();
  }

  public int size() {
    return keyframes.size();
  }

  /** Time of the first and last keyframe; the path is only "playing" between them. */
  public long startMs() {
    return keyframes.isEmpty() ? 0 : keyframes.get(0).timeMs();
  }

  public long endMs() {
    return keyframes.isEmpty() ? 0 : keyframes.get(keyframes.size() - 1).timeMs();
  }

  /**
   * The camera at a replay time, or null when there are no keyframes. The time field of the result
   * is the requested time.
   */
  public CameraKeyframe sample(long timeMs) {
    int n = keyframes.size();
    if (n == 0) {
      return null;
    }
    if (n == 1 || timeMs <= startMs()) {
      return keyframes.get(0).at(Math.max(0, timeMs));
    }
    if (timeMs >= endMs()) {
      return keyframes.get(n - 1).at(timeMs);
    }
    int i = indexAtOrAfter(timeMs + 1) - 1; // segment start: last keyframe at or before timeMs
    CameraKeyframe a = keyframes.get(i);
    CameraKeyframe b = keyframes.get(i + 1);
    double t = (double) (timeMs - a.timeMs()) / (b.timeMs() - a.timeMs());
    if (easing == Easing.EASE_IN_OUT) {
      t = t * t * (3 - 2 * t);
    }
    CameraKeyframe p0 = i > 0 ? keyframes.get(i - 1) : a;
    CameraKeyframe p3 = i + 2 < n ? keyframes.get(i + 2) : b;

    // Unwrap angles relative to the segment start so interpolation takes the short way round.
    double yawA = a.yaw();
    double yawB = yawA + wrap(b.yaw() - a.yaw());
    double yaw0 = yawA - wrap(a.yaw() - p0.yaw());
    double yaw3 = yawB + wrap(p3.yaw() - b.yaw());
    double rollA = a.roll();
    double rollB = rollA + wrap(b.roll() - a.roll());
    double roll0 = rollA - wrap(a.roll() - p0.roll());
    double roll3 = rollB + wrap(p3.roll() - b.roll());

    return new CameraKeyframe(
        timeMs,
        curve(p0.x(), a.x(), b.x(), p3.x(), t),
        curve(p0.y(), a.y(), b.y(), p3.y(), t),
        curve(p0.z(), a.z(), b.z(), p3.z(), t),
        (float) wrap(curve(yaw0, yawA, yawB, yaw3, t)),
        (float) Math.max(-90, Math.min(90, curve(p0.pitch(), a.pitch(), b.pitch(), p3.pitch(), t))),
        (float) wrap(curve(roll0, rollA, rollB, roll3, t)),
        (float) Math.max(2, Math.min(178, curve(p0.fov(), a.fov(), b.fov(), p3.fov(), t))));
  }

  private double curve(double p0, double p1, double p2, double p3, double t) {
    if (interpolation == Interpolation.LINEAR) {
      return p1 + (p2 - p1) * t;
    }
    double t2 = t * t;
    double t3 = t2 * t;
    return 0.5
        * ((2 * p1)
            + (-p0 + p2) * t
            + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
            + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
  }

  /** Wraps an angle to [-180, 180). */
  static double wrap(double degrees) {
    double d = (degrees + 180) % 360;
    if (d < 0) {
      d += 360;
    }
    return d - 180;
  }

  private int indexAtOrAfter(long timeMs) {
    int lo = 0;
    int hi = keyframes.size();
    while (lo < hi) {
      int mid = (lo + hi) >>> 1;
      if (keyframes.get(mid).timeMs() < timeMs) {
        lo = mid + 1;
      } else {
        hi = mid;
      }
    }
    return lo;
  }
}
