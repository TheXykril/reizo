/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CameraPathTest {
  private static CameraKeyframe k(long t, double x, float yaw) {
    return new CameraKeyframe(t, x, 64, 0, yaw, 0, 0, 70);
  }

  @Test
  void emptyAndSingleKeyframe() {
    CameraPath path = new CameraPath();
    assertNull(path.sample(100));
    path.put(k(1000, 5, 0));
    assertEquals(5, path.sample(0).x());
    assertEquals(5, path.sample(5000).x());
    assertEquals(5000, path.sample(5000).timeMs());
  }

  @Test
  void keyframesStaySortedAndReplaceAtSameTime() {
    CameraPath path = new CameraPath();
    path.put(k(2000, 2, 0));
    path.put(k(0, 0, 0));
    path.put(k(1000, 1, 0));
    path.put(k(1000, 9, 0));
    assertEquals(3, path.size());
    assertEquals(9, path.keyframes().get(1).x());
    assertEquals(0, path.startMs());
    assertEquals(2000, path.endMs());
    assertTrue(path.remove(1000));
    assertFalse(path.remove(1000));
    assertEquals(2, path.size());
    assertEquals(2000, path.removeNearest(1900, 200).timeMs());
    assertNull(path.removeNearest(5000, 200));
  }

  @Test
  void linearInterpolatesAndHoldsOutside() {
    CameraPath path = new CameraPath();
    path.setInterpolation(CameraPath.Interpolation.LINEAR);
    path.put(k(1000, 0, 0));
    path.put(k(2000, 10, 0));
    assertEquals(0, path.sample(500).x(), 1e-9);
    assertEquals(5, path.sample(1500).x(), 1e-9);
    assertEquals(10, path.sample(2000).x(), 1e-9);
    assertEquals(10, path.sample(9000).x(), 1e-9);
  }

  @Test
  void catmullRomPassesThroughKeyframesSmoothly() {
    CameraPath path = new CameraPath();
    path.put(k(0, 0, 0));
    path.put(k(1000, 10, 0));
    path.put(k(2000, 0, 0));
    path.put(k(3000, 10, 0));
    for (CameraKeyframe key : path.keyframes()) {
      assertEquals(key.x(), path.sample(key.timeMs()).x(), 1e-9);
    }
    // Overshoots a little past 10 near the turn, unlike straight lines.
    assertTrue(path.sample(1100).x() > path.sample(900).x() - 1);
    double v1 = path.sample(1001).x() - path.sample(1000).x();
    double v0 = path.sample(1000).x() - path.sample(999).x();
    assertEquals(v0, v1, 0.01, "velocity is continuous through a keyframe");
  }

  @Test
  void yawTurnsTheShortWay() {
    CameraPath path = new CameraPath();
    path.setInterpolation(CameraPath.Interpolation.LINEAR);
    path.put(k(0, 0, 170));
    path.put(k(1000, 0, -170));
    float mid = path.sample(500).yaw();
    assertEquals(180, Math.abs(mid), 1e-3, "crosses 180, not 0: " + mid);
  }

  @Test
  void easingSlowsSegmentEnds() {
    CameraPath path = new CameraPath();
    path.setInterpolation(CameraPath.Interpolation.LINEAR);
    path.setEasing(CameraPath.Easing.EASE_IN_OUT);
    path.put(k(0, 0, 0));
    path.put(k(1000, 10, 0));
    assertTrue(path.sample(100).x() < 1.0);
    assertEquals(5, path.sample(500).x(), 1e-9);
  }

  @Test
  void rejectsBadKeyframes() {
    assertThrows(
        IllegalArgumentException.class, () -> new CameraKeyframe(-1, 0, 0, 0, 0, 0, 0, 70));
    assertThrows(IllegalArgumentException.class, () -> new CameraKeyframe(0, 0, 0, 0, 0, 0, 0, 0));
  }

  @Test
  void wrapsAngles() {
    assertEquals(-170, CameraPath.wrap(190), 1e-9);
    assertEquals(170, CameraPath.wrap(-190), 1e-9);
    assertEquals(-180, CameraPath.wrap(180), 1e-9);
  }
}
