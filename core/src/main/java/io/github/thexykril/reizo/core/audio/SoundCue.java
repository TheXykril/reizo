/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.audio;

import java.util.List;

/**
 * One sound that started during an export, with where the listener (the camera) was at that moment.
 * A sound that moves, changes volume or pitch, or loops also carries a {@link #path()} sampled once
 * per video frame, and the time it stopped.
 *
 * @param timeMs when it starts, in ms from the start of the video
 * @param clip key of the decoded clip (the sound file's resource path)
 * @param gain volume after the game's sound settings, 0 to 1
 * @param pitch playback speed, 0.5 to 2
 * @param positional false for sounds heard the same everywhere (UI, music, relative sounds)
 * @param range distance in blocks at which the sound fades out completely (linear attenuation)
 * @param listenerYaw the camera's yaw in degrees (Minecraft convention: 0 looks to +Z)
 * @param loop the clip repeats until {@code endMs}
 * @param endMs when the sound was stopped, in video ms; infinite when it played to the end
 * @param path the sound's state over time, sorted by time; empty when it never changed
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
    float listenerYaw,
    boolean loop,
    double endMs,
    List<Point> path) {

  /** The state of a followed sound and of the listener at one video time. */
  public record Point(
      double timeMs,
      float gain,
      float pitch,
      double x,
      double y,
      double z,
      float range,
      double listenerX,
      double listenerY,
      double listenerZ,
      float listenerYaw) {

    /** Linear blend towards {@code o}; yaw takes the short way round. */
    Point lerp(Point o, double f) {
      float dyaw = ((o.listenerYaw - listenerYaw) % 360 + 540) % 360 - 180;
      return new Point(
          timeMs + (o.timeMs - timeMs) * f,
          (float) (gain + (o.gain - gain) * f),
          (float) (pitch + (o.pitch - pitch) * f),
          x + (o.x - x) * f,
          y + (o.y - y) * f,
          z + (o.z - z) * f,
          (float) (range + (o.range - range) * f),
          listenerX + (o.listenerX - listenerX) * f,
          listenerY + (o.listenerY - listenerY) * f,
          listenerZ + (o.listenerZ - listenerZ) * f,
          (float) (listenerYaw + dyaw * f));
    }

    /** Gain after distance attenuation, 0 to 1. */
    public double distanceGain(boolean positional) {
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
    public double pan(boolean positional) {
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

  public SoundCue {
    path = List.copyOf(path);
  }

  /** A sound that plays once, unchanged. */
  public SoundCue(
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
    this(
        timeMs,
        clip,
        gain,
        pitch,
        positional,
        x,
        y,
        z,
        range,
        listenerX,
        listenerY,
        listenerZ,
        listenerYaw,
        false,
        Double.POSITIVE_INFINITY,
        List.of());
  }

  /** The state when the sound started. */
  public Point start() {
    return new Point(
        timeMs, gain, pitch, x, y, z, range, listenerX, listenerY, listenerZ, listenerYaw);
  }

  /** The state at a video time: the start, blended along the path, holding after its end. */
  public Point at(double t) {
    if (path.isEmpty() || t <= timeMs) {
      return start();
    }
    Point first = path.get(0);
    if (t <= first.timeMs()) {
      return start().lerp(first, fraction(timeMs, first.timeMs(), t));
    }
    int lo = 0;
    int hi = path.size() - 1;
    if (t >= path.get(hi).timeMs()) {
      return path.get(hi);
    }
    while (hi - lo > 1) {
      int mid = (lo + hi) >>> 1;
      if (path.get(mid).timeMs() <= t) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    Point a = path.get(lo);
    Point b = path.get(hi);
    return a.lerp(b, fraction(a.timeMs(), b.timeMs(), t));
  }

  private static double fraction(double a, double b, double t) {
    return b <= a ? 1 : Math.max(0, Math.min(1, (t - a) / (b - a)));
  }

  /** A copy that loops, stops at {@code endMs} and follows {@code path}. */
  public SoundCue followed(boolean loop, double endMs, List<Point> path) {
    return new SoundCue(
        timeMs,
        clip,
        gain,
        pitch,
        positional,
        x,
        y,
        z,
        range,
        listenerX,
        listenerY,
        listenerZ,
        listenerYaw,
        loop,
        endMs,
        path);
  }

  /** Gain after distance attenuation at the start, 0 to 1. */
  public double distanceGain() {
    return start().distanceGain(positional);
  }

  /** Left-right position at the start, -1 (left) to 1 (right); 0 for non-positional sounds. */
  public double pan() {
    return start().pan(positional);
  }
}
