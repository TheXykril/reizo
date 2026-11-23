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
        reizoToggle(
            "player_hud",
            "reizo.settings.player_hud",
            c -> c.showPlayerHud,
            (c, v) -> c.showPlayerHud = v),
        reizoToggle(
            "player_screens",
            "reizo.settings.player_screens",
            c -> c.showPlayerScreens,
            (c, v) -> c.showPlayerScreens = v));
  }

  /** Reizo's own toggles for the recorded player's HUD and screens in POV, so a shot can hide them. */
  private static KeyframedSetting reizoToggle(
      String id,
      String key,
      java.util.function.Function<io.github.thexykril.reizo.fabric.ClientConfig, Boolean> get,
      java.util.function.BiConsumer<io.github.thexykril.reizo.fabric.ClientConfig, Boolean> set) {
    return new KeyframedSetting() {
      @Override
      public String id() {
        return "reizo:" + id;
      }

      @Override
      public String group() {
        return "Reizo";
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
        return get.apply(io.github.thexykril.reizo.fabric.RecorderClient.get().config()) ? 1 : 0;
      }

      @Override
      public void set(double value) {
        set.accept(io.github.thexykril.reizo.fabric.RecorderClient.get().config(), value != 0);
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
