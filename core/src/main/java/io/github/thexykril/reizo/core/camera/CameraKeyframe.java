/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.camera;

/**
 * Camera state at one replay time. Angles are in degrees (Minecraft's convention: yaw 0 looks
 * south, pitch positive looks down); fov is the vertical field of view in degrees.
 */
public record CameraKeyframe(
    long timeMs, double x, double y, double z, float yaw, float pitch, float roll, float fov) {

  public static final float DEFAULT_FOV = 70f;

  public CameraKeyframe {
    if (timeMs < 0) {
      throw new IllegalArgumentException("timeMs < 0");
    }
    if (!(fov > 1 && fov < 179)) {
      throw new IllegalArgumentException("fov out of range: " + fov);
    }
  }

  /** The same pose at another time. */
  public CameraKeyframe at(long newTimeMs) {
    return new CameraKeyframe(newTimeMs, x, y, z, yaw, pitch, roll, fov);
  }
}
