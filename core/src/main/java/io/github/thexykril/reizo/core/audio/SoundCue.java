/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.audio;

/**
 * One sound that started during an export, with where the listener (the camera) was at that moment.
 *
 * @param timeMs when it starts, in ms from the start of the video
 * @param clip key of the decoded clip (the sound file's resource path)
 * @param gain volume after the game's sound settings, 0 to 1
 * @param pitch playback speed, 0.5 to 2
 * @param positional false for sounds heard the same everywhere (UI, music, relative sounds)
 * @param range distance in blocks at which the sound fades out completely (linear attenuation)
 * @param listenerYaw the camera's yaw in degrees (Minecraft convention: 0 looks to +Z)
 */
public record SoundCue(
    double timeMs,
    String clip,
    float gain,
    float pitch,
    boolean positional,
    double x,
    double y,
    double z,
    float range,
    double listenerX,
    double listenerY,
    double listenerZ,
    float listenerYaw) {

  /** Gain after distance attenuation, 0 to 1. */
  public double distanceGain() {
    if (!positional) {
      return gain;
    }
    double dx = x - listenerX;
    double dy = y - listenerY;
    double dz = z - listenerZ;
    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
    return range <= 0 ? 0 : gain * Math.max(0, 1 - d / range);
  }

  /** Left-right position, -1 (left) to 1 (right); 0 for non-positional sounds. */
  public double pan() {
    if (!positional) {
      return 0;
    }
    double dx = x - listenerX;
    double dz = z - listenerZ;
    double len = Math.sqrt(dx * dx + dz * dz);
    if (len < 1e-6) {
      return 0;
    }
    double yaw = Math.toRadians(listenerYaw);
    // Minecraft: yaw 0 looks to +Z, yaw 90 to -X. Right of the view is (-cos, -sin) in (x, z).
    double rightX = -Math.cos(yaw);
    double rightZ = -Math.sin(yaw);
    return Math.max(-1, Math.min(1, (dx * rightX + dz * rightZ) / len));
  }
}
