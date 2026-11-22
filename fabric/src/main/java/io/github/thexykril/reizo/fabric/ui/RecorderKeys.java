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

  private RecorderKeys() {}

  public static void register() {
    KeyMapping.Category category =
        KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(RecorderClient.MOD_ID, "recorder"));
    // F9 is unused by vanilla. Lunar's Shift+R needs modifier support, which vanilla key
    // mappings lack; tracked in the roadmap.
    toggle =
        KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.reizo.toggle", GLFW.GLFW_KEY_F9, category));
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
  }

  /** Called once per client tick. */
  public static void handle(RecorderClient client) {
    while (toggle.consumeClick()) {
      client.toggleRecording();
    }
    while (pause.consumeClick()) {
      client.togglePause();
    }
    while (marker.consumeClick()) {
      client.addMarker("Marker");
    }
    handleReplay(Minecraft.getInstance());
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
