/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.playback;

import io.github.thexykril.reizo.core.camera.FollowRig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Drives the follow camera ({@link ReplayCamera.Mode#FOLLOW}) from a {@link FollowRig} every frame,
 * on the replay's clock, and keeps it out of walls. Client thread only.
 */
public final class FollowCamera {
  private static final FollowRig RIG = new FollowRig();

  /** Seeks and jumps longer than this place the camera again instead of gliding there. */
  private static final double JUMP_SECONDS = 0.5;

  private static long lastMs = Long.MIN_VALUE;
  private static int lastTarget = Integer.MIN_VALUE;
  private static float subjectDistance;

  private FollowCamera() {}

  /** Places the camera behind the subject on the next frame. */
  public static void reset() {
    RIG.reset();
    lastMs = Long.MIN_VALUE;
    subjectDistance = 0;
  }

  /**
   * Distance in blocks from the camera to the middle of the followed player on the last frame, or 0
   * when unknown. Automatic depth of field focuses here, so the subject stays sharp even when the
   * centre of the picture looks past them.
   */
  public static float subjectDistance() {
    return subjectDistance;
  }

  /**
   * The camera pose for this frame, as {x, y, z, yaw, pitch}, or null when the follow camera is not
   * on.
   */
  public static double[] poseNow(Entity target, float partialTick) {
    ReplayServer server = ReplayPlayback.current();
    Minecraft mc = Minecraft.getInstance();
    if (server == null || target == null || mc.level == null) {
      return null;
    }
    long now = server.replayTimeMs();
    double dt = lastMs == Long.MIN_VALUE ? 0 : (now - lastMs) / 1000.0;
    if (target.getId() != lastTarget || dt < 0 || dt > JUMP_SECONDS) {
      RIG.reset();
      dt = 0;
    }
    lastMs = now;
    lastTarget = target.getId();
    Vec3 eye = target.getEyePosition(partialTick);
    // Blocks per second over the last tick; remote entities report no velocity of their own.
    double[] velocity = {
      (target.getX() - target.xo) * 20, (target.getY() - target.yo) * 20, (target.getZ() - target.zo) * 20
    };
    RIG.update(new double[] {eye.x, eye.y, eye.z}, velocity, target.getYRot(), dt);
    double[] p = RIG.position();
    Vec3 cam = new Vec3(p[0], p[1], p[2]);
    // Keep a wall between the subject and the camera from hiding the subject: come in front of it.
    HitResult hit =
        mc.level.clip(
            new ClipContext(eye, cam, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, target));
    if (hit.getType() != HitResult.Type.MISS) {
      Vec3 back = cam.subtract(eye);
      double len = back.length();
      double keep = Math.max(0.3, eye.distanceTo(hit.getLocation()) - 0.25);
      cam = len < 1e-6 ? cam : eye.add(back.scale(Math.min(1, keep / len)));
    }
    subjectDistance =
        (float) cam.distanceTo(eye.add(0, -0.5 * target.getEyeHeight(), 0));
    double[] look = RIG.lookPoint();
    double dx = look[0] - cam.x;
    double dy = look[1] - cam.y;
    double dz = look[2] - cam.z;
    double yaw = Math.toDegrees(Math.atan2(-dx, dz));
    double pitch = Math.toDegrees(-Math.atan2(dy, Math.hypot(dx, dz)));
    return new double[] {cam.x, cam.y, cam.z, yaw, pitch};
  }
}
