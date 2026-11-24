/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.playback;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Camera modes in a replay, in the order the camera key cycles through them. Client thread only.
 *
 * <ul>
 *   <li>{@link Mode#FREE}: the spectator camera, flown with the normal movement keys.
 *   <li>{@link Mode#POV}: the recorded player's own view (first person).
 *   <li>{@link Mode#FOLLOW}: a camera that glides behind the recorded player ({@link
 *       FollowCamera}).
 * </ul>
 */
public final class ReplayCamera {
  public enum Mode {
    FREE,
    POV,
    FOLLOW
  }

  private static Mode mode = Mode.FREE;

  private ReplayCamera() {}

  public static Mode mode() {
    return mode;
  }

  /** Switches to the next mode. Returns the new mode. */
  public static Mode cycle(Minecraft mc) {
    Mode next = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
    set(mc, next);
    return mode;
  }

  /** Applies a mode. POV and FOLLOW fall back to FREE while the recorded player is not present. */
  public static void set(Minecraft mc, Mode wanted) {
    Entity target = recordedPlayer(mc);
    mode = target == null ? Mode.FREE : wanted;
    switch (mode) {
      case FREE -> {
        mc.setCameraEntity(mc.player);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
      }
      case POV -> {
        mc.setCameraEntity(target);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
      }
      case FOLLOW -> {
        FollowCamera.reset();
        mc.setCameraEntity(target);
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
      }
    }
  }

  /** Called every client tick during a replay: keeps the camera valid as entities come and go. */
  public static void tick(Minecraft mc) {
    if (mode == Mode.FREE || mc.player == null) {
      return;
    }
    Entity camera = mc.getCameraEntity();
    if (camera == mc.player) {
      // Left the player's view some other way (for example vanilla spectator controls).
      set(mc, Mode.FREE);
      return;
    }
    Entity target = recordedPlayer(mc);
    if (target == null) {
      set(mc, Mode.FREE); // e.g. after a dimension change, until the player is shown again
    } else if (camera != target) {
      set(mc, mode); // the recorded player entity was replaced
    }
  }

  /** Back to the default when a replay ends or starts. */
  public static void reset() {
    mode = Mode.FREE;
    FollowCamera.reset();
  }

  private static Entity recordedPlayer(Minecraft mc) {
    ReplayServer server = ReplayPlayback.current();
    if (server == null || server.view() == null || mc.level == null) {
      return null;
    }
    Entity e = mc.level.getEntity(server.view().recordedEntityId());
    return e instanceof Player ? e : null;
  }
}
