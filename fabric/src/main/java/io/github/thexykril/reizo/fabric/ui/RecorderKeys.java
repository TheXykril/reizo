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
import org.lwjgl.glfw.GLFW;

/**
 * Recorder and replay key bindings, all rebindable in Controls. Of the recorder keys only toggle
 * has a default; the replay keys act only while a replay plays.
 */
public final class RecorderKeys {
  private static KeyMapping toggle;
  private static KeyMapping pause;
  private static KeyMapping marker;
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
            new KeyMapping("key.reizo.toggle", GLFW.GLFW_KEY_R, category));
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

    // Replay keys act only while a replay plays. None of these keys is used by vanilla.
    KeyMapping.Category replay =
        KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(RecorderClient.MOD_ID, "replay"));
    replayCamera =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.replay_camera", GLFW.GLFW_KEY_V, replay));
    replayPause =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.replay_pause", GLFW.GLFW_KEY_K, replay));
    replaySlower =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.replay_slower", GLFW.GLFW_KEY_LEFT_BRACKET, replay));
    replayFaster =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.replay_faster", GLFW.GLFW_KEY_RIGHT_BRACKET, replay));
    replayBack =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.replay_back", GLFW.GLFW_KEY_LEFT, replay));
    replayForward =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.replay_forward", GLFW.GLFW_KEY_RIGHT, replay));
    replayPrevMarker =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.replay_prev_marker", GLFW.GLFW_KEY_COMMA, replay));
    replayNextMarker =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.replay_next_marker", GLFW.GLFW_KEY_PERIOD, replay));
    // Camera path keys; N, B and J are unused by vanilla (P is Social Interactions).
    keyframeAdd =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.keyframe_add", GLFW.GLFW_KEY_N, replay));
    keyframeRemove =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.keyframe_remove", GLFW.GLFW_KEY_DELETE, replay));
    pathToggle =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.path_toggle", GLFW.GLFW_KEY_B, replay));
    pathSmoothing =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.path_smoothing", GLFW.GLFW_KEY_J, replay));
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
    mc.gui.setOverlayMessage(message, false);
  }
}
