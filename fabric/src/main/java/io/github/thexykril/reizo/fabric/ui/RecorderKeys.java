/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.playback.ReplayCamera;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import java.util.Locale;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Recorder and replay key bindings, all rebindable in Controls. Of the recorder keys only toggle
 * has a default; the replay keys act only while a replay plays.
 */
public final class RecorderKeys {
  private static KeyMapping toggle;
  private static KeyMapping pause;
  private static KeyMapping marker;
  private static KeyMapping saveShadow;
  private static KeyMapping replayCamera;
  private static KeyMapping replayPause;
  private static KeyMapping replaySlower;
  private static KeyMapping replayFaster;
  private static KeyMapping replayBack;
  private static KeyMapping replayForward;
  private static KeyMapping replayPrevMarker;
  private static KeyMapping replayNextMarker;
  private static KeyMapping keyframeAdd;
  private static KeyMapping keyframeRemove;
  private static KeyMapping pathToggle;
  private static KeyMapping pathSmoothing;
  private static KeyMapping timeline;

  /** The key that opens the editor, as the player bound it (for hints). */
  public static String timelineKeyName() {
    return timeline == null ? "Y" : timeline.getTranslatedKeyMessage().getString();
  }
  private static KeyMapping rollLeft;
  private static KeyMapping rollRight;
  private static KeyMapping fovIn;
  private static KeyMapping fovOut;
  private static KeyMapping cameraReset;
  private static KeyMapping pathDisplay;
  private static KeyMapping hideEntity;
  private static KeyMapping openReplays;

  /** Degrees per tick while a roll or FOV key is held. */
  static final float ROLL_STEP = 2f;

  static final float FOV_STEP = 1f;

  /** How far the seek keys jump. */
  static final long SEEK_STEP_MS = 5_000;

  private RecorderKeys() {}

  public static void register() {
    KeyMapping.Category category =
        KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(RecorderClient.MOD_ID, "recorder"));
    // Shift+R like Lunar: R is unused by vanilla, and the modifier is a Reizo setting because
    // vanilla key mappings have none (see KeyModifier).
    toggle =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.toggle", InputConstants.KEY_R, category));
    pause =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                "key.reizo.pause",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                category));
    marker =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                "key.reizo.marker",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                category));

    saveShadow =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                "key.reizo.save_shadow",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                category));

    // Opens the replay browser in game and on main menus (also one that replaces the title screen,
    // see MenuButtons). Unbound by default.
    openReplays =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                "key.reizo.open_replays",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                category));

    // Replay keys act only while a replay plays. None of these keys is used by vanilla.
    KeyMapping.Category replay =
        KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(RecorderClient.MOD_ID, "replay"));
    replayCamera =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.replay_camera", InputConstants.KEY_V, replay));
    replayPause =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.replay_pause", InputConstants.KEY_K, replay));
    replaySlower =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.replay_slower", InputConstants.KEY_LBRACKET, replay));
    replayFaster =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.replay_faster", InputConstants.KEY_RBRACKET, replay));
    replayBack =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.replay_back", InputConstants.KEY_LEFT, replay));
    replayForward =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.replay_forward", InputConstants.KEY_RIGHT, replay));
    replayPrevMarker =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.replay_prev_marker", InputConstants.KEY_COMMA, replay));
    replayNextMarker =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.replay_next_marker", InputConstants.KEY_PERIOD, replay));
    // Camera path keys; N, B and J are unused by vanilla (P is Social Interactions).
    keyframeAdd =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.keyframe_add", InputConstants.KEY_N, replay));
    keyframeRemove =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.keyframe_remove", InputConstants.KEY_DELETE, replay));
    pathToggle =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.path_toggle", InputConstants.KEY_B, replay));
    pathSmoothing =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.path_smoothing", InputConstants.KEY_J, replay));
    // Y is unused by vanilla.
    timeline =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.timeline", InputConstants.KEY_Y, replay));
    // Free camera roll and FOV (stored in new keyframes) and the path display. U, I, minus,
    // equals, 0 and G are unused by vanilla.
    rollLeft =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.roll_left", InputConstants.KEY_U, replay));
    rollRight =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.roll_right", InputConstants.KEY_I, replay));
    fovIn =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.fov_in", InputConstants.KEY_MINUS, replay));
    fovOut =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.fov_out", InputConstants.KEY_EQUALS, replay));
    cameraReset =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.camera_reset", InputConstants.KEY_0, replay));
    pathDisplay =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.path_display", InputConstants.KEY_G, replay));
    // H is unused by vanilla.
    hideEntity =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.hide_entity", InputConstants.KEY_H, replay));
  }

  /** The key that opens the replay browser (unbound by default). */
  public static KeyMapping openReplays() {
    return openReplays;
  }

  /** Called once per client tick. */
  public static void handle(RecorderClient client) {
    Minecraft mc = Minecraft.getInstance();
    KeyModifier modifier = KeyModifier.parse(client.config().toggleModifier);
    while (toggle.consumeClick()) {
      if (modifier.isDown(mc.getWindow())) {
        client.toggleRecording();
      }
    }
    while (pause.consumeClick()) {
      client.togglePause();
    }
    while (marker.consumeClick()) {
      client.addMarker("Marker");
    }
    while (saveShadow.consumeClick()) {
      client.saveShadow();
    }
    while (openReplays.consumeClick()) {
      if (mc.screen == null && !ReplayPlayback.isActive()) {
        mc.setScreen(ReplayBrowserScreen.create(null));
      }
    }
    handleReplay(mc);
  }

  private static void handleReplay(Minecraft mc) {
    boolean active = ReplayPlayback.isActive();
    while (replayCamera.consumeClick()) {
      if (active) {
        ReplayCamera.Mode mode = ReplayCamera.cycle(mc);
        status(
            mc,
            Component.translatable("reizo.replay.camera." + mode.name().toLowerCase(Locale.ROOT)));
      }
    }
    while (replayPause.consumeClick()) {
      if (active) {
        boolean paused = ReplayPlayback.togglePause();
        status(mc, Component.translatable(paused ? "reizo.replay.paused" : "reizo.replay.playing"));
      }
    }
    while (replaySlower.consumeClick()) {
      if (active) {
        speedStatus(mc, ReplayPlayback.stepSpeed(-1));
      }
    }
    while (replayFaster.consumeClick()) {
      if (active) {
        speedStatus(mc, ReplayPlayback.stepSpeed(1));
      }
    }
    seekKeys(mc, active);
    pathKeys(mc, active);
    cameraKeys(mc, active);
    while (timeline.consumeClick()) {
      if (active && mc.screen == null) {
        mc.setScreen(TimelineScreen.create());
      }
    }
  }

  private static void pathKeys(Minecraft mc, boolean active) {
    while (keyframeAdd.consumeClick()) {
      if (active) {
        var k = io.github.thexykril.reizo.fabric.playback.ReplayEditor.addKeyframe(mc);
        if (k != null) {
          status(
              mc,
              Component.translatable(
                  "reizo.path.keyframe_added",
                  ReplayHud.time(k.timeMs()),
                  io.github.thexykril.reizo.fabric.playback.ReplayEditor.path().size()));
        }
      }
    }
    while (keyframeRemove.consumeClick()) {
      if (active) {
        var k = io.github.thexykril.reizo.fabric.playback.ReplayEditor.removeNearestKeyframe();
        status(
            mc,
            k == null
                ? Component.translatable("reizo.path.no_keyframe")
                : Component.translatable(
                    "reizo.path.keyframe_removed", ReplayHud.time(k.timeMs())));
      }
    }
    while (pathToggle.consumeClick()) {
      if (active) {
        boolean on = io.github.thexykril.reizo.fabric.playback.ReplayEditor.togglePath();
        status(mc, Component.translatable(on ? "reizo.path.on" : "reizo.path.off"));
      }
    }
    while (hideEntity.consumeClick()) {
      if (active) {
        hideLookedAt(mc);
      }
    }
    while (pathSmoothing.consumeClick()) {
      if (active) {
        status(
            mc,
            Component.translatable(
                "reizo.path.smoothing",
                io.github.thexykril.reizo.fabric.playback.ReplayEditor.cycleSmoothing()));
      }
    }
  }

  /** Hides the entity the camera looks at from now on, or shows it again. */
  static void hideLookedAt(Minecraft mc) {
    var server = io.github.thexykril.reizo.fabric.playback.ReplayPlayback.current();
    var entity = io.github.thexykril.reizo.fabric.playback.ReplayEntityHider.lookedAt(mc);
    if (server == null || entity == null) {
      status(mc, Component.translatable("reizo.hide.none"));
      return;
    }
    long now = Math.min(server.replayTimeMs(), server.script().durationMs());
    boolean hidden = io.github.thexykril.reizo.fabric.playback.ReplayEntityHider.toggle(entity, now);
    status(
        mc,
        Component.translatable(
            hidden ? "reizo.hide.hidden" : "reizo.hide.shown", entity.getName()));
  }

  private static void cameraKeys(Minecraft mc, boolean active) {
    boolean rolled = false;
    boolean zoomed = false;
    if (active && mc.screen == null) {
      if (rollLeft.isDown()) {
        io.github.thexykril.reizo.fabric.playback.ReplayEditor.rollBy(-ROLL_STEP);
        rolled = true;
      }
      if (rollRight.isDown()) {
        io.github.thexykril.reizo.fabric.playback.ReplayEditor.rollBy(ROLL_STEP);
        rolled = true;
      }
      float setting = mc.options.fov().get().floatValue();
      if (fovIn.isDown()) {
        io.github.thexykril.reizo.fabric.playback.ReplayEditor.zoomBy(-FOV_STEP, setting);
        zoomed = true;
      }
      if (fovOut.isDown()) {
        io.github.thexykril.reizo.fabric.playback.ReplayEditor.zoomBy(FOV_STEP, setting);
        zoomed = true;
      }
    }
    // Drain the click queues so a held key does not fire again later.
    while (rollLeft.consumeClick() | rollRight.consumeClick()) {}
    while (fovIn.consumeClick() | fovOut.consumeClick()) {}
    if (rolled || zoomed) {
      float fov = io.github.thexykril.reizo.fabric.playback.ReplayEditor.freeFov();
      status(
          mc,
          Component.translatable(
              "reizo.camera.roll_fov",
              Math.round(io.github.thexykril.reizo.fabric.playback.ReplayEditor.freeRoll()),
              Math.round(Float.isNaN(fov) ? mc.options.fov().get() : fov)));
    }
    while (cameraReset.consumeClick()) {
      if (active) {
        io.github.thexykril.reizo.fabric.playback.ReplayEditor.resetFreeCamera();
        status(mc, Component.translatable("reizo.camera.reset"));
      }
    }
    while (pathDisplay.consumeClick()) {
      if (active) {
        boolean on = io.github.thexykril.reizo.fabric.playback.PathVisualizer.toggle();
        status(mc, Component.translatable(on ? "reizo.path.shown" : "reizo.path.hidden"));
      }
    }
  }

  private static void seekKeys(Minecraft mc, boolean active) {
    while (replayBack.consumeClick()) {
      if (active) {
        ReplayPlayback.seekBy(-SEEK_STEP_MS);
      }
    }
    while (replayForward.consumeClick()) {
      if (active) {
        ReplayPlayback.seekBy(SEEK_STEP_MS);
      }
    }
    while (replayPrevMarker.consumeClick()) {
      if (active && !ReplayPlayback.seekToMarker(-1)) {
        status(mc, Component.translatable("reizo.replay.no_marker"));
      }
    }
    while (replayNextMarker.consumeClick()) {
      if (active && !ReplayPlayback.seekToMarker(1)) {
        status(mc, Component.translatable("reizo.replay.no_marker"));
      }
    }
  }

  private static void speedStatus(Minecraft mc, double speed) {
    String text =
        speed == Math.rint(speed) ? Integer.toString((int) speed) : Double.toString(speed);
    status(mc, Component.translatable("reizo.replay.speed", text));
  }

  private static void status(Minecraft mc, Component message) {
    if (ReplayPlayback.isActive()) {
      ReplayHud.status(message);
    } else {
      mc.gui.setOverlayMessage(message, false);
    }
  }
}
