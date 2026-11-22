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
import io.github.thexykril.reizo.core.timeline.EditHistory;
import io.github.thexykril.reizo.core.timeline.TimelineState;
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
  private static io.github.thexykril.reizo.core.effects.EffectTrack effects =
      new io.github.thexykril.reizo.core.effects.EffectTrack();
  private static boolean pathPlaying;

  /** Roll (degrees) and FOV of the free camera while the path does not drive it; NaN = none. */
  private static float freeRoll;

  private static float freeFov = Float.NaN;

  /** Free camera FOV limits. */
  static final float MIN_FOV = 10;

  static final float MAX_FOV = 150;

  private static final EditHistory<TimelineState> history = new EditHistory<>(200);

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
    effects = project.effects();
    history.clear();
    resetFreeCamera();
  }

  static void close() {
    history.clear();
    resetFreeCamera();
    project = null;
    path = new CameraPath();
    effects = new io.github.thexykril.reizo.core.effects.EffectTrack();
    pathPlaying = false;
  }

  /** The effect keyframes of the open project. Call {@link #effectsChanged()} after a change. */
  public static io.github.thexykril.reizo.core.effects.EffectTrack effects() {
    return effects;
  }

  /** Stores the effect keyframes in the project and saves it. */
  public static void effectsChanged() {
    if (project != null) {
      project.setEffects(effects);
      save();
    }
  }

  /** The grade at the current replay time; neutral without a replay or effects. */
  public static io.github.thexykril.reizo.core.effects.Grade gradeNow() {
    ReplayServer server = ReplayPlayback.current();
    if (server == null || effects.isEmpty()) {
      return io.github.thexykril.reizo.core.effects.Grade.NEUTRAL;
    }
    return effects.at(server.replayTimeMs());
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
    long t = Math.min(server.replayTimeMs(), server.script().durationMs());
    recordUndo();
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
            freeRoll,
            Float.isNaN(freeFov) ? mc.options.fov().get().floatValue() : freeFov);
    path.put(k);
    save();
    return k;
  }

  /** Puts a keyframe as given (tests, future editor UI). */
  public static void putKeyframe(CameraKeyframe k) {
    recordUndo();
    path.put(k);
    save();
  }

  /** Removes the keyframe nearest to the current time. Returns it, or null. */
  public static CameraKeyframe removeNearestKeyframe() {
    ReplayServer server = ReplayPlayback.current();
    if (server == null) {
      return null;
    }
    CameraKeyframe near = nearest(server.replayTimeMs(), KEYFRAME_TOLERANCE_MS);
    if (near == null) {
      return null;
    }
    recordUndo();
    path.remove(near.timeMs());
    changed();
    return near;
  }

  /** Cycles interpolation, then easing: catmull-rom, linear, each with and without easing. */
  public static String cycleSmoothing() {
    recordUndo();
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

  /** Roll of the free camera in degrees (0 = level). */
  public static float freeRoll() {
    return freeRoll;
  }

  /** FOV of the free camera, or NaN for the FOV setting. */
  public static float freeFov() {
    return freeFov;
  }

  /** Rolls the free camera by some degrees, wrapped to -180..180. */
  public static void rollBy(float degrees) {
    float r = freeRoll + degrees;
    r = ((r + 180) % 360 + 360) % 360 - 180;
    freeRoll = r;
  }

  /** Widens (positive) or narrows the free camera's FOV, starting from the FOV setting. */
  public static void zoomBy(float degrees, float settingFov) {
    float base = Float.isNaN(freeFov) ? settingFov : freeFov;
    freeFov = Math.max(MIN_FOV, Math.min(MAX_FOV, base + degrees));
  }

  /** Levels the free camera and returns it to the FOV setting. */
  public static void resetFreeCamera() {
    freeRoll = 0;
    freeFov = Float.NaN;
  }

  /**
   * Roll in degrees to apply to the camera this frame: the path's while it plays, else the free
   * camera's in free mode, else NaN (leave the camera alone).
   */
  public static float rollNow() {
    CameraKeyframe k = cameraNow();
    if (k != null) {
      return k.roll();
    }
    return freeCameraActive() && freeRoll != 0 ? freeRoll : Float.NaN;
  }

  /** FOV to apply this frame, or NaN for the normal FOV. Same rules as {@link #rollNow()}. */
  public static float fovNow() {
    CameraKeyframe k = cameraNow();
    if (k != null) {
      return k.fov();
    }
    return freeCameraActive() ? freeFov : Float.NaN;
  }

  private static boolean freeCameraActive() {
    ReplayServer server = ReplayPlayback.current();
    return server != null && !server.isManual() && ReplayCamera.mode() == ReplayCamera.Mode.FREE;
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

  /** The keyframe nearest to a time within a tolerance, or null. */
  public static CameraKeyframe nearest(long timeMs, long toleranceMs) {
    CameraKeyframe best = null;
    for (CameraKeyframe k : path.keyframes()) {
      long d = Math.abs(k.timeMs() - timeMs);
      if (d <= toleranceMs && (best == null || d < Math.abs(best.timeMs() - timeMs))) {
        best = k;
      }
    }
    return best;
  }

  /** Everything the timeline edits, as an immutable snapshot. */
  public static TimelineState state() {
    return project == null
        ? TimelineState.of(path, -1, -1)
        : TimelineState.of(path, project.inMs, project.outMs);
  }

  /**
   * Records the current state for undo. Call once before a change (for a drag: once when it
   * starts), then {@link #changed()} after it.
   */
  public static void recordUndo() {
    history.record(state());
  }

  /** Saves after a change made directly on {@link #path()} or the project. */
  public static void changed() {
    if (path.isEmpty()) {
      pathPlaying = false;
    }
    save();
  }

  public static boolean undo() {
    return restore(history.undo(state()));
  }

  public static boolean redo() {
    return restore(history.redo(state()));
  }

  public static boolean canUndo() {
    return history.canUndo();
  }

  public static boolean canRedo() {
    return history.canRedo();
  }

  private static boolean restore(TimelineState s) {
    if (s == null) {
      return false;
    }
    s.applyTo(path);
    if (project != null) {
      project.inMs = s.inMs();
      project.outMs = s.outMs();
    }
    changed();
    return true;
  }

  /** Start of the export region, or -1 for the start of the recording. */
  public static long inMs() {
    return project == null ? -1 : project.inMs;
  }

  /** End of the export region, or -1 for the end of the recording. */
  public static long outMs() {
    return project == null ? -1 : project.outMs;
  }

  /**
   * Sets the export region's start (or end); -1 clears it. An end before the start (or a start
   * after the end) clears the other side, so the region is never inverted.
   */
  public static void setRegion(boolean start, long timeMs) {
    if (project == null) {
      return;
    }
    recordUndo();
    if (start) {
      project.inMs = timeMs;
      if (timeMs >= 0 && project.outMs >= 0 && project.outMs <= timeMs) {
        project.outMs = -1;
      }
    } else {
      project.outMs = timeMs;
      if (timeMs >= 0 && project.inMs >= 0 && project.inMs >= timeMs) {
        project.inMs = -1;
      }
    }
    changed();
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
