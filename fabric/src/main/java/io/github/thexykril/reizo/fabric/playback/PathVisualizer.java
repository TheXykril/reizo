/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.playback;

import io.github.thexykril.reizo.fabric.compat.Compat;
import io.github.thexykril.reizo.core.camera.CameraKeyframe;
import io.github.thexykril.reizo.core.camera.CameraPath;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the camera path into the replay world with Minecraft's gizmos: the curve the camera will
 * follow, a box and a view-direction arrow at each keyframe, and a dot where the path camera is at
 * the current replay time. Shown while a replay is open and the path is not driving the camera;
 * hidden with F1 and during export (which hides the GUI). Drawn every frame from Fabric's
 * end-of-extraction world render event, into the level renderer's per-frame gizmos. (Per-tick
 * gizmos would not do: Minecraft only takes them while the world ticks normally, so a paused replay
 * would show stale shapes.)
 */
public final class PathVisualizer {
  private static final int CURVE = 0xFF4CC9F0;
  private static final int KEYFRAME = 0xFFFFFFFF;
  private static final int LOOK = 0xFFF5A524;
  private static final int NOW = 0xFFE5484D;

  /** Curve sample spacing. */
  static final long STEP_MS = 50;

  /** Most segments drawn, so a long path stays cheap. */
  static final int MAX_SEGMENTS = 2_000;

  private static boolean visible = true;

  private PathVisualizer() {}

  public static boolean isVisible() {
    return visible;
  }

  /** Turns the path display on or off. Returns the new state. */
  public static boolean toggle() {
    visible = !visible;
    return visible;
  }

  /** Registers the per-frame drawing. */
  public static void register() {
    //? if >=26.1 {
    /*net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.END_EXTRACTION.register(
        context -> draw(Minecraft.getInstance()));
    *///?} else {
    net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_EXTRACTION.register(
        context -> draw(Minecraft.getInstance()));
    //?}
  }

  static void draw(Minecraft mc) {
    CameraPath path = ReplayEditor.path();
    ReplayServer server = ReplayPlayback.current();
    if (!visible
        || server == null
        || path.isEmpty()
        || ReplayEditor.isPathPlaying()
        || Compat.hudHidden(mc)
        || mc.level == null) {
      return;
    }
    // Add to the level renderer's own frame collection explicitly: frames rendered outside the
    // normal game loop (test screenshots, for one) have no collection open.
    //? if >=26.1 {
    /*var collection = mc.levelExtractor.collectPerFrameMainThreadGizmos();
    *///?} else {
    var collection = mc.levelRenderer.collectPerFrameGizmos();
    //?}
    try {
      drawPath(path, server);
    } finally {
      collection.close();
    }
  }

  private static void drawPath(CameraPath path, ReplayServer server) {
    List<CameraKeyframe> keys = path.keyframes();
    if (keys.size() > 1) {
      long start = path.startMs();
      long end = path.endMs();
      long step = Math.max(STEP_MS, (end - start) / MAX_SEGMENTS);
      Vec3 previous = pos(path.sample(start));
      for (long t = start + step; ; t += step) {
        long at = Math.min(t, end);
        Vec3 next = pos(path.sample(at));
        Gizmos.line(previous, next, CURVE, 2f);
        previous = next;
        if (at >= end) {
          break;
        }
      }
    }
    for (CameraKeyframe k : keys) {
      Vec3 p = pos(k);
      Gizmos.cuboid(new AABB(p, p).inflate(0.15), GizmoStyle.stroke(KEYFRAME, 2f));
      Gizmos.arrow(p, p.add(direction(k.yaw(), k.pitch())), LOOK, 2f);
    }
    long now = Math.min(server.replayTimeMs(), server.script().durationMs());
    CameraKeyframe here = path.sample(now);
    if (here != null) {
      Gizmos.point(pos(here), NOW, 8f);
    }
  }

  private static Vec3 pos(CameraKeyframe k) {
    return new Vec3(k.x(), k.y(), k.z());
  }

  /** Minecraft's look vector for a yaw and pitch, in degrees. */
  static Vec3 direction(float yaw, float pitch) {
    return Vec3.directionFromRotation(pitch, yaw);
  }
}
