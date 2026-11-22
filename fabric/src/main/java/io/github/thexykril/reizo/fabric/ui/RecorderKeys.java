/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.thexykril.reizo.fabric.RecorderClient;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Recorder key bindings. All are rebindable in Controls; only toggle has a default key. */
public final class RecorderKeys {
  private static KeyMapping toggle;
  private static KeyMapping pause;
  private static KeyMapping marker;

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
  }
}
