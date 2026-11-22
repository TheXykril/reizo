/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.core.recording.RecordingSession;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Adds Reizo's buttons to vanilla menus: "Replays" on the title screen, and recording controls plus
 * "Replays" in the top-left corner of the pause menu (where Lunar shows its recording controls).
 */
public final class MenuButtons {
  private MenuButtons() {}

  public static void register() {
    ScreenEvents.AFTER_INIT.register(
        (client, screen, width, height) -> {
          if (screen instanceof TitleScreen) {
            // Left of the Multiplayer row, clear of the centered vanilla buttons.
            Screens.getButtons(screen)
                .add(
                    Button.builder(
                            Component.translatable("reizo.menu.replays"),
                            b -> client.setScreen(new ReplayBrowserScreen(screen)))
                        .bounds(width / 2 - 100 - 4 - 70, height / 4 + 48 + 24, 70, 20)
                        .build());
          } else if (screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
            addPauseButtons(screen);
          }
        });
  }

  private static void addPauseButtons(Screen screen) {
    var buttons = Screens.getButtons(screen);
    var client = net.minecraft.client.Minecraft.getInstance();
    int y = 4;
    if (!ReplayPlayback.isActive()) {
      RecorderClient recorder = RecorderClient.get();
      RecordingSession session = recorder.session();
      Component label =
          Component.translatable(
              session == null ? "reizo.menu.start_recording" : "reizo.menu.stop_recording");
      buttons.add(
          Button.builder(
                  label,
                  b -> {
                    recorder.toggleRecording();
                    client.setScreen(null);
                  })
              .bounds(4, y, 120, 20)
              .build());
      y += 24;
      if (session != null) {
        boolean paused = session.state() == RecordingSession.State.PAUSED;
        buttons.add(
            Button.builder(
                    Component.translatable(
                        paused ? "reizo.menu.resume_recording" : "reizo.menu.pause_recording"),
                    b -> {
                      recorder.togglePause();
                      client.setScreen(null);
                    })
                .bounds(4, y, 120, 20)
                .build());
        y += 24;
      }
    }
    if (ReplayPlayback.isActive()) {
      buttons.add(
          Button.builder(
                  Component.translatable("reizo.menu.timeline"),
                  b -> client.setScreen(new TimelineScreen()))
              .bounds(4, y, 120, 20)
              .build());
      y += 24;
      buttons.add(
          Button.builder(
                  Component.translatable("reizo.menu.export"),
                  b -> {
                    client.setScreen(null);
                    io.github.thexykril.reizo.fabric.export.ExportControls.exportCurrent(
                        client, null);
                  })
              .bounds(4, y, 120, 20)
              .build());
      y += 24;
    }
    buttons.add(
        Button.builder(
                Component.translatable("reizo.menu.replays"),
                b -> client.setScreen(new ReplayBrowserScreen(screen)))
            .bounds(4, y, 120, 20)
            .build());
  }
}
