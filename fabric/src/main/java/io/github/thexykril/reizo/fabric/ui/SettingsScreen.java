/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.fabric.ClientConfig;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.ui.kit.KitScreen;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/**
 * Reizo's own settings screen, used without OneConfig; with it, Reizo's OneConfig page is the
 * settings screen. Both edit the same {@link ClientConfig} values.
 */
public final class SettingsScreen extends KitScreen {
  private final Screen parent;
  private int recordingY;
  private int shadowY;
  private int replayY;

  /**
   * Opens Reizo's settings: its page in OneConfig when OneConfig is installed (OneClient), else this
   * screen, returning to {@code parent}.
   */
  public static void open(Screen parent) {
    if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("oneconfig")) {
      try {
        io.github.thexykril.reizo.fabric.compat.OneConfigIntegration.openPage();
        return;
      } catch (Throwable t) {
        org.slf4j.LoggerFactory.getLogger("Reizo/Settings")
            .warn("Could not open Reizo's OneConfig page; using Reizo's own screen", t);
      }
    }
    net.minecraft.client.Minecraft.getInstance().setScreen(new SettingsScreen(parent));
  }

  public SettingsScreen(Screen parent) {
    super(Component.translatable("reizo.settings.title"));
    this.parent = parent;
  }

  @Override
  protected void init() {
    ClientConfig config = RecorderClient.get().config();
    card(320, 2 * PAD + TITLE_H + 3 * 12 + 7 * (ROW_H + 2) + 3 * GAP + 24);
    int x = innerX();
    int w = innerW();
    int half = (w - GAP) / 2;
    int y = contentY();

    recordingY = y;
    y += 12;
    toggle(
        x, y, w, Component.translatable("reizo.settings.auto_record"),
        () -> config.autoRecordOnJoin, v -> config.autoRecordOnJoin = v);
    y += ROW_H;
    toggle(
        x, y, w, Component.translatable("reizo.settings.indicator"),
        () -> config.showIndicator, v -> config.showIndicator = v);
    y += ROW_H;
    toggle(
        x, y, w, Component.translatable("reizo.settings.server_name"),
        () -> config.storeServerName, v -> config.storeServerName = v);
    y += ROW_H;
    cycle(
        x, y, w, Component.translatable("reizo.settings.toggle_modifier"),
        List.of(KeyModifier.values()),
        () -> KeyModifier.parse(config.toggleModifier),
        KeyModifier::label,
        v -> config.toggleModifier = v.configValue());
    y += ROW_H + GAP;

    shadowY = y;
    y += 12;
    toggle(
        x, y, half, Component.translatable("reizo.settings.shadow"),
        () -> config.shadowEnabled,
        v -> {
          config.shadowEnabled = v;
          RecorderClient.get().applyShadowSettings();
        });
    cycle(
        x + half + GAP, y, w - half - GAP, Component.translatable("reizo.settings.shadow_length"),
        RecorderClient.SHADOW_LENGTHS,
        () -> RecorderClient.SHADOW_LENGTHS.contains(config.shadowSeconds) ? config.shadowSeconds : 120,
        v -> Component.literal(ReplayHud.time(v * 1000L)),
        v -> {
          config.shadowSeconds = v;
          RecorderClient.get().applyShadowSettings();
        });
    y += ROW_H + GAP;

    replayY = y;
    y += 12;
    toggle(
        x, y, half, Component.translatable("reizo.settings.player_hud_short"),
        () -> config.showPlayerHud, v -> config.showPlayerHud = v);
    toggle(
        x + half + GAP, y, w - half - GAP,
        Component.translatable("reizo.settings.player_screens_short"),
        () -> config.showPlayerScreens, v -> config.showPlayerScreens = v);

    int fy = footerY();
    button(
        x, fy, half, Component.translatable("reizo.settings.keys"),
        () -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options)));
    accentButton(x + half + GAP, fy, w - half - GAP, Component.translatable("gui.done"), this::onClose);
  }

  @Override
  protected void renderCard(GuiGraphics g, int mouseX, int mouseY) {
    sectionLabel(g, Component.translatable("reizo.settings.section.recording").getString(), recordingY);
    sectionLabel(g, Component.translatable("reizo.settings.section.shadow").getString(), shadowY);
    sectionLabel(g, Component.translatable("reizo.settings.section.replay").getString(), replayY);
  }

  @Override
  public void removed() {
    super.removed();
    RecorderClient.get().saveConfig();
  }

  @Override
  public void onClose() {
    minecraft.setScreen(parent);
  }
}
