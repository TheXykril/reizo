/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.settings;

import io.github.thexykril.reizo.core.effects.PropertyTracks;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;

/**
 * Minecraft's own video settings that make sense to change during a shot. Changes go through {@link
 * OptionInstance#set}, which does not write options.txt.
 */
final class VanillaSettings {
  private VanillaSettings() {}

  static List<KeyframedSetting> all() {
    return List.of(
        number("gamma", "options.gamma", () -> Minecraft.getInstance().options.gamma()),
        number(
            "screen_effect_scale",
            "options.screenEffectScale",
            () -> Minecraft.getInstance().options.screenEffectScale()),
        toggle(
            "entity_shadows",
            "options.entityShadows",
            () -> Minecraft.getInstance().options.entityShadows()),
        toggle(
            "view_bobbing",
            "options.viewBobbing",
            () -> Minecraft.getInstance().options.bobView()),
        playerHud());
  }

  /** Reizo's own toggle for the recorded player's HUD in POV, so a shot can show or hide it. */
  private static KeyframedSetting playerHud() {
    return new KeyframedSetting() {
      @Override
      public String id() {
        return "reizo:player_hud";
      }

      @Override
      public String group() {
        return "Reizo";
      }

      @Override
      public String label() {
        return Component.translatable("reizo.settings.player_hud").getString();
      }

      @Override
      public PropertyTracks.Kind kind() {
        return PropertyTracks.Kind.TOGGLE;
      }

      @Override
      public double min() {
        return 0;
      }

      @Override
      public double max() {
        return 1;
      }

      @Override
      public double get() {
        return io.github.thexykril.reizo.fabric.RecorderClient.get().config().showPlayerHud ? 1 : 0;
      }

      @Override
      public void set(double value) {
        io.github.thexykril.reizo.fabric.RecorderClient.get().config().showPlayerHud = value != 0;
      }
    };
  }

  private static KeyframedSetting number(
      String id, String key, Supplier<OptionInstance<Double>> option) {
    return new KeyframedSetting() {
      @Override
      public String id() {
        return "minecraft:" + id;
      }

      @Override
      public String group() {
        return "Minecraft";
      }

      @Override
      public String label() {
        return Component.translatable(key).getString();
      }

      @Override
      public PropertyTracks.Kind kind() {
        return PropertyTracks.Kind.NUMBER;
      }

      @Override
      public double min() {
        return 0;
      }

      @Override
      public double max() {
        return 1;
      }

      @Override
      public double get() {
        return option.get().get();
      }

      @Override
      public void set(double value) {
        option.get().set(Math.max(0, Math.min(1, value)));
      }
    };
  }

  private static KeyframedSetting toggle(
      String id, String key, Supplier<OptionInstance<Boolean>> option) {
    return new KeyframedSetting() {
      @Override
      public String id() {
        return "minecraft:" + id;
      }

      @Override
      public String group() {
        return "Minecraft";
      }

      @Override
      public String label() {
        return Component.translatable(key).getString();
      }

      @Override
      public PropertyTracks.Kind kind() {
        return PropertyTracks.Kind.TOGGLE;
      }

      @Override
      public double min() {
        return 0;
      }

      @Override
      public double max() {
        return 1;
      }

      @Override
      public double get() {
        return option.get().get() ? 1 : 0;
      }

      @Override
      public void set(double value) {
        option.get().set(value != 0);
      }
    };
  }
}
