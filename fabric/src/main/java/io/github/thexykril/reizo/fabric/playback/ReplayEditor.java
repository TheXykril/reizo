/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.playback;

import io.github.thexykril.reizo.core.camera.CameraKeyframe;
import io.github.thexykril.reizo.core.camera.CameraPath;
import io.github.thexykril.reizo.core.format.RecordingMetadata;
import io.github.thexykril.reizo.core.project.Project;
import io.github.thexykril.reizo.core.project.ProjectStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The editing state of the replay that is playing: its project (loaded or created per recording)
 * and camera path. Every change is saved at once (autosave), atomically. Client thread only, except
 * {@link #cameraNow()}, which the render thread (the same thread in Minecraft) reads per frame.
 */
public final class ReplayEditor {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Editor");

  /** Keyframes this close to the current time are replaced or removed by the keys. */
  static final long KEYFRAME_TOLERANCE_MS = 500;

  private static ProjectStore store;
  private static Project project;
  private static CameraPath path = new CameraPath();
  private static boolean pathPlaying;

  private ReplayEditor() {}

  public static void init(ProjectStore projects) {
    store = projects;
  }

  public static ProjectStore store() {
    return store;
  }

  /** Opens (or creates) the project for a recording. Called when a replay starts. */
  static void open(Path archive, RecordingMetadata meta) {
    pathPlaying = false;
    String file = archive.getFileName().toString();
    try {
      project = store == null ? null : store.findForRecording(file, meta.id);
    } catch (IOException e) {
      LOGGER.warn("Could not read projects", e);
      project = null;
    }
    if (project == null) {
      project =
          Project.create(UUID.randomUUID().toString(), meta.name, file, System.currentTimeMillis());
      project.recordingId = meta.id;
    }
    path = project.cameraPath();
  }

  static void close() {
    project = null;
    path = new CameraPath();
    pathPlaying = false;
  }

  public static Project project() {
    return project;
  }

  public static CameraPath path() {
    return path;
  }

  public static boolean isPathPlaying() {
    return pathPlaying;
  }

  /** Sets camera path playback; it stays off while the path is empty. */
  public static void setPathPlaying(boolean on) {
    pathPlaying = on && !path.isEmpty();
  }

  /** Turns camera path playback on or off. Returns the new state. */
  public static boolean togglePath() {
    pathPlaying = !pathPlaying && !path.isEmpty();
    return pathPlaying;
  }

  /**
   * Adds a keyframe at the current replay time from the current camera, replacing one within the
   * tolerance. Returns the keyframe, or null when no replay is playing.
   */
  public static CameraKeyframe addKeyframe(Minecraft mc) {
    ReplayServer server = ReplayPlayback.current();
    Entity camera = mc.getCameraEntity();
    if (server == null || camera == null || project == null) {
      return null;
    }
    long t = Math.min(server.clock().timeMs(), server.script().durationMs());
    CameraKeyframe near = path.removeNearest(t, KEYFRAME_TOLERANCE_MS);
    long at = near != null ? near.timeMs() : t;
    CameraKeyframe k =
        new CameraKeyframe(
            at,
            camera.getX(),
            camera.getEyeY(),
            camera.getZ(),
            camera.getYRot(),
            camera.getXRot(),
            0f,
            mc.options.fov().get().floatValue());
    path.put(k);
    save();
    return k;
  }

  /** Puts a keyframe as given (tests, future editor UI). */
  public static void putKeyframe(CameraKeyframe k) {
    path.put(k);
    save();
  }

  /** Removes the keyframe nearest to the current time. Returns it, or null. */
  public static CameraKeyframe removeNearestKeyframe() {
    ReplayServer server = ReplayPlayback.current();
    if (server == null) {
      return null;
    }
    CameraKeyframe removed = path.removeNearest(server.clock().timeMs(), KEYFRAME_TOLERANCE_MS);
    if (removed != null) {
      if (path.isEmpty()) {
        pathPlaying = false;
      }
      save();
    }
    return removed;
  }

  /** Cycles interpolation, then easing: catmull-rom, linear, each with and without easing. */
  public static String cycleSmoothing() {
    if (path.easing() == CameraPath.Easing.LINEAR) {
      path.setEasing(CameraPath.Easing.EASE_IN_OUT);
    } else {
      path.setEasing(CameraPath.Easing.LINEAR);
      path.setInterpolation(
          path.interpolation() == CameraPath.Interpolation.CATMULL_ROM
              ? CameraPath.Interpolation.LINEAR
              : CameraPath.Interpolation.CATMULL_ROM);
    }
    save();
    return path.interpolation().name().toLowerCase(java.util.Locale.ROOT)
        + (path.easing() == CameraPath.Easing.EASE_IN_OUT ? " + ease" : "");
  }

  /**
   * The camera pose the path gives right now, or null when the path does not drive the camera (no
   * replay, path off, or a camera mode other than free).
   */
  public static CameraKeyframe cameraNow() {
    ReplayServer server = ReplayPlayback.current();
    if (server == null || !pathPlaying || ReplayCamera.mode() != ReplayCamera.Mode.FREE) {
      return null;
    }
    return path.sample(server.replayTimeMs());
  }

  /** Keeps the camera player (and so chunk loading and culling) where the path camera is. */
  static void tick(Minecraft mc) {
    CameraKeyframe k = cameraNow();
    if (k != null && mc.player != null) {
      mc.player.setPos(k.x(), k.y() - mc.player.getEyeHeight(), k.z());
      mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
      mc.player.setYRot(k.yaw());
      mc.player.setXRot(k.pitch());
    }
  }

  private static void save() {
    if (project == null || store == null) {
      return;
    }
    project.setCameraPath(path);
    try {
      store.save(project, System.currentTimeMillis());
    } catch (IOException e) {
      LOGGER.warn("Could not save project {}", project.id, e);
    }
  }
}
