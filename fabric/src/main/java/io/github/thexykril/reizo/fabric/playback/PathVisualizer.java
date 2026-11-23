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
//? if >=1.21.11 {
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
//?}
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the camera path into the replay world with Minecraft's gizmos: the curve the camera will
 * follow, a box and a view-direction arrow at each keyframe, and a dot where the path camera is at
 * the current replay time. Shown while a replay is open and the path is not driving the camera;
 * hidden with F1 and during export (which hides the GUI). Drawn every frame from Fabric's
 * end-of-extraction world render event, into the level renderer's per-frame gizmos. (Per-tick
 * gizmos would not do: Minecraft only takes them while the world ticks normally, so a paused replay
 * would show stale shapes.) 1.21.10 has no gizmos, so there the same shapes are drawn as plain lines
 * just before the debug renderer.
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
    *///?} else if >=1.21.11 {
    net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_EXTRACTION.register(
        context -> draw(Minecraft.getInstance()));
    //?} else {
    /*net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.BEFORE_DEBUG_RENDER
        .register(context -> draw(Minecraft.getInstance(), context));
    *///?}
  }

  //? if >=1.21.11 {
  static void draw(Minecraft mc) {
  //?} else {
  /*static void draw(
      Minecraft mc, net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context) {
  *///?}
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
    //? if >=26.2 {
    /*var collection = mc.levelExtractor.collectPerFrameMainThreadGizmos();
    *///?} else if >=1.21.11 {
    var collection = mc.levelRenderer.collectPerFrameGizmos();
    //?}
    //? if >=1.21.11 {
    try {
      drawPath(path, server, new GizmoShapes());
    } finally {
      collection.close();
    }
    //?} else {
    /*drawPath(path, server, new LineShapes(mc, context));
    *///?}
  }

  /** Where the path's shapes go: Minecraft's gizmos, or plain lines where there are none. */
  private interface Shapes {
    void line(Vec3 from, Vec3 to, int color);

    void arrow(Vec3 from, Vec3 to, int color);

    void box(AABB box, int color);

    void point(Vec3 at, int color);
  }

  //? if >=1.21.11 {
  private static final class GizmoShapes implements Shapes {
    @Override
    public void line(Vec3 from, Vec3 to, int color) {
      Gizmos.line(from, to, color, 2f);
    }

    @Override
    public void arrow(Vec3 from, Vec3 to, int color) {
      Gizmos.arrow(from, to, color, 2f);
    }

    @Override
    public void box(AABB box, int color) {
      Gizmos.cuboid(box, GizmoStyle.stroke(color, 2f));
    }

    @Override
    public void point(Vec3 at, int color) {
      Gizmos.point(at, color, 8f);
    }
  }
  //?} else {
  /*private static final class LineShapes implements Shapes {
    private final com.mojang.blaze3d.vertex.PoseStack.Pose pose;
    private final com.mojang.blaze3d.vertex.VertexConsumer lines;
    private final Vec3 camera;

    LineShapes(
        Minecraft mc, net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context) {
      this.pose = context.matrices().last();
      this.lines = context.consumers().getBuffer(net.minecraft.client.renderer.RenderType.lines());
      this.camera = mc.gameRenderer.getMainCamera().getPosition();
    }

    @Override
    public void line(Vec3 from, Vec3 to, int color) {
      Vec3 a = from.subtract(camera);
      Vec3 b = to.subtract(camera);
      Vec3 n = b.subtract(a).normalize();
      vertex(a, n, color);
      vertex(b, n, color);
    }

    @Override
    public void arrow(Vec3 from, Vec3 to, int color) {
      line(from, to, color);
    }

    @Override
    public void box(AABB box, int color) {
      net.minecraft.client.renderer.ShapeRenderer.renderLineBox(
          pose,
          lines,
          box.move(camera.scale(-1)),
          ((color >> 16) & 0xFF) / 255f,
          ((color >> 8) & 0xFF) / 255f,
          (color & 0xFF) / 255f,
          ((color >>> 24) & 0xFF) / 255f);
    }

    @Override
    public void point(Vec3 at, int color) {
      box(new AABB(at, at).inflate(0.06), color);
    }

    private void vertex(Vec3 p, Vec3 n, int color) {
      lines
          .addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
          .setColor(color)
          .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }
  }
  *///?}

  private static void drawPath(CameraPath path, ReplayServer server, Shapes shapes) {
    List<CameraKeyframe> keys = path.keyframes();
    if (keys.size() > 1) {
      long start = path.startMs();
      long end = path.endMs();
      long step = Math.max(STEP_MS, (end - start) / MAX_SEGMENTS);
      Vec3 previous = pos(path.sample(start));
      for (long t = start + step; ; t += step) {
        long at = Math.min(t, end);
        Vec3 next = pos(path.sample(at));
        shapes.line(previous, next, CURVE);
        previous = next;
        if (at >= end) {
          break;
        }
      }
    }
    for (CameraKeyframe k : keys) {
      Vec3 p = pos(k);
      shapes.box(new AABB(p, p).inflate(0.15), KEYFRAME);
      shapes.arrow(p, p.add(direction(k.yaw(), k.pitch())), LOOK);
    }
    long now = Math.min(server.replayTimeMs(), server.script().durationMs());
    CameraKeyframe here = path.sample(now);
    if (here != null) {
      shapes.point(pos(here), NOW);
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
