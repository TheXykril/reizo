/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.settings;

import io.github.thexykril.reizo.core.effects.PropertyTracks;
import io.github.thexykril.reizo.fabric.playback.ReplayWorldLook;
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
        worldLook(
            "time_of_day",
            "reizo.settings.time_of_day",
            ReplayWorldLook.DAY,
            mc -> {
              //? if >=26.1 {
              /*long t = mc.level.getDefaultClockTime();
              *///?} else {
              long t = mc.level.getDayTime();
              //?}
              return Math.floorMod(t, ReplayWorldLook.DAY);
            },
            ReplayWorldLook::setTimeOfDay,
            ReplayWorldLook::clock),
        worldLook(
            "rain",
            "reizo.settings.rain",
            1,
            mc -> mc.level.getRainLevel(1f),
            ReplayWorldLook::setRain,
            v -> Math.round(v * 100) + "%"),
        worldLook(
            "thunder",
            "reizo.settings.thunder",
            1,
            mc ->
                ReplayWorldLook.thunderOverridden()
                    ? ReplayWorldLook.thunder()
                    : mc.level.getRainLevel(1f) > 0
                        ? mc.level.getThunderLevel(1f) / mc.level.getRainLevel(1f)
                        : 0,
            ReplayWorldLook::setThunder,
            v -> Math.round(v * 100) + "%"),
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

  /**
   * A replay's time of day or weather. Setting it overrides the recorded value; once its track goes
   * away the replay follows the recording again.
   */
  private static KeyframedSetting worldLook(
      String id,
      String key,
      double max,
      java.util.function.ToDoubleFunction<Minecraft> get,
      java.util.function.DoubleConsumer set,
      java.util.function.DoubleFunction<String> format) {
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
        return PropertyTracks.Kind.NUMBER;
      }

      @Override
      public double min() {
        return 0;
      }

      @Override
      public double max() {
        return max;
      }

      @Override
      public double get() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : get.applyAsDouble(mc);
      }

      @Override
      public void set(double value) {
        set.accept(value);
      }

      @Override
      public void restore(double original) {
        set.accept(Double.NaN);
      }

      @Override
      public String format(double value) {
        return format.apply(value);
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
