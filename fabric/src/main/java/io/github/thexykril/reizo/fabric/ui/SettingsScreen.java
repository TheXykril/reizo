/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.fabric.ClientConfig;
import io.github.thexykril.reizo.fabric.RecorderClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/**
 * Reizo's own settings screen. It edits the same {@link ClientConfig} values that the optional
 * OneConfig page shows, so both stay in sync.
 */
public final class SettingsScreen extends Screen {
  private final Screen parent;

  public SettingsScreen(Screen parent) {
    super(Component.translatable("reizo.settings.title"));
    this.parent = parent;
  }

  @Override
  protected void init() {
    ClientConfig config = RecorderClient.get().config();
    int x = width / 2 - 155;
    int y = 40;
    addRenderableWidget(
        CycleButton.onOffBuilder(config.autoRecordOnJoin)
            .create(
                x,
                y,
                310,
                20,
                Component.translatable("reizo.settings.auto_record"),
                (b, v) -> config.autoRecordOnJoin = v));
    y += 24;
    addRenderableWidget(
        CycleButton.onOffBuilder(config.showIndicator)
            .create(
                x,
                y,
                310,
                20,
                Component.translatable("reizo.settings.indicator"),
                (b, v) -> config.showIndicator = v));
    y += 24;
    addRenderableWidget(
        CycleButton.onOffBuilder(config.showPlayerHud)
            .create(
                x,
                y,
                310,
                20,
                Component.translatable("reizo.settings.player_hud"),
                (b, v) -> config.showPlayerHud = v));
    y += 24;
    addRenderableWidget(
        CycleButton.onOffBuilder(config.storeServerName)
            .create(
                x,
                y,
                310,
                20,
                Component.translatable("reizo.settings.server_name"),
                (b, v) -> config.storeServerName = v));
    y += 24;
    addRenderableWidget(
        io.github.thexykril.reizo.fabric.compat.Compat.cycle(KeyModifier::label, KeyModifier.parse(config.toggleModifier))
            .withValues(KeyModifier.values())
            .create(
                x,
                y,
                310,
                20,
                Component.translatable("reizo.settings.toggle_modifier"),
                (b, v) -> config.toggleModifier = v.configValue()));
    y += 24;
    addRenderableWidget(
        CycleButton.onOffBuilder(config.shadowEnabled)
            .create(
                x,
                y,
                150,
                20,
                Component.translatable("reizo.settings.shadow"),
                (b, v) -> {
                  config.shadowEnabled = v;
                  RecorderClient.get().applyShadowSettings();
                }));
    addRenderableWidget(
        io.github.thexykril.reizo.fabric.compat.Compat.cycle(
                (Integer v) -> Component.literal(ReplayHud.time(v * 1000L)),
                RecorderClient.SHADOW_LENGTHS.contains(config.shadowSeconds)
                    ? config.shadowSeconds
                    : 120)
            .withValues(RecorderClient.SHADOW_LENGTHS)
            .create(
                x + 160,
                y,
                150,
                20,
                Component.translatable("reizo.settings.shadow_length"),
                (b, v) -> {
                  config.shadowSeconds = v;
                  RecorderClient.get().applyShadowSettings();
                }));
    y += 24;
    addRenderableWidget(
        Button.builder(
                Component.translatable("reizo.settings.keys"),
                b -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options)))
            .bounds(x, y, 310, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(width / 2 - 100, height - 28, 200, 20)
            .build());
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    super.render(g, mouseX, mouseY, partialTick);
    g.drawCenteredString(font, title, width / 2, 15, 0xFFFFFFFF);
  }

  @Override
  public void removed() {
    RecorderClient.get().saveConfig();
  }

  @Override
  public void onClose() {
    minecraft.setScreen(parent);
  }
}
