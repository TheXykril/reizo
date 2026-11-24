/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FollowRigTest {
  private static final double[] STILL = {0, 0, 0};

  @Test
  void startsBehindTheFacingAndLooksAtTheSubject() {
    FollowRig rig = new FollowRig();
    rig.update(new double[] {0, 65, 0}, STILL, 0, 0); // yaw 0 faces +z
    double[] p = rig.position();
    assertEquals(0, p[0], 1e-9);
    assertEquals(-rig.distance, p[2], 1e-9);
    assertEquals(65 + rig.height, p[1], 1e-9);
    assertEquals(0, rig.yaw(), 1e-3);
    assertTrue(rig.pitch() > 0, "looks down at the subject");
  }

  @Test
  void glidesAfterAMovedSubjectWithoutOvershooting() {
    FollowRig rig = new FollowRig();
    rig.update(new double[] {0, 65, 0}, STILL, 0, 0);
    double[] eye = {0, 65, 10};
    double last = rig.position()[2];
    for (int i = 0; i < 200; i++) {
      rig.update(eye, STILL, 0, 1 / 60.0);
      double z = rig.position()[2];
      assertTrue(z >= last - 1e-9, "moves only forward");
      assertTrue(z <= 10 - rig.distance + 1e-6, "never passes its place behind the subject");
      last = z;
    }
    assertEquals(10 - rig.distance, last, 0.01);
  }

  @Test
  void swingsBehindTheDirectionOfTravel() {
    FollowRig rig = new FollowRig();
    double[] eye = {0, 65, 0};
    rig.update(eye, STILL, 0, 0);
    double[] east = {5, 0, 0}; // +x is yaw -90
    for (int i = 0; i < 600; i++) {
      eye[0] += 5 / 60.0;
      rig.update(eye, east, 0, 1 / 60.0);
    }
    double[] p = rig.position();
    assertTrue(p[0] < eye[0] - rig.distance * 0.8, "behind the subject along its travel");
    assertEquals(eye[2], p[2], 0.5);
  }

  @Test
  void holdsStillWhenNoTimePasses() {
    FollowRig rig = new FollowRig();
    rig.update(new double[] {0, 65, 0}, STILL, 0, 0);
    double[] before = rig.position();
    rig.update(new double[] {0, 65, 20}, STILL, 0, 0);
    assertEquals(before[2], rig.position()[2], 1e-12);
  }

  @Test
  void movesAlikeAtDifferentFrameRates() {
    FollowRig slow = new FollowRig();
    FollowRig fast = new FollowRig();
    slow.update(new double[] {0, 65, 0}, STILL, 0, 0);
    fast.update(new double[] {0, 65, 0}, STILL, 0, 0);
    double[] eye = {0, 65, 8};
    for (int i = 0; i < 30; i++) {
      slow.update(eye, STILL, 0, 1 / 30.0);
    }
    for (int i = 0; i < 120; i++) {
      fast.update(eye, STILL, 0, 1 / 120.0);
    }
    assertEquals(slow.position()[2], fast.position()[2], 0.05);
  }
}
