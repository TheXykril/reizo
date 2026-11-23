/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.core.recording.RecordingSession;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Adds Reizo's buttons to vanilla menus: "Replays" on the title screen, and recording controls plus
 * "Replays" in the top-left corner of the pause menu (where Lunar shows its recording controls). A
 * main menu that replaces the title screen (OneClient's) gets "Replays" in its top-left corner, and
 * the "Open replays" key works on every main menu.
 */
public final class MenuButtons {
  private MenuButtons() {}

  public static void register() {
    ScreenEvents.AFTER_INIT.register(
        (client, screen, width, height) -> {
          if (isMainMenu(client, screen)) {
            ScreenKeyboardEvents.afterKeyPress(screen)
                .register(
                    (s, key) -> {
                      if (RecorderKeys.openReplays() != null
                          && RecorderKeys.openReplays().matches(key)
                          && client.screen == s) {
                        client.setScreen(ReplayBrowserScreen.create(s));
                      }
                    });
          }
          if (!(screen instanceof TitleScreen) && isReplacementMainMenu(client, screen)) {
            Screens.getButtons(screen)
                .add(
                    Button.builder(
                            Component.translatable("reizo.menu.replays"),
                            b -> client.setScreen(ReplayBrowserScreen.create(screen)))
                        .bounds(4, 4, 70, 20)
                        .build());
          }
          if (screen instanceof TitleScreen) {
            // Left of the Multiplayer row, clear of the centered vanilla buttons.
            Screens.getButtons(screen)
                .add(
                    Button.builder(
                            Component.translatable("reizo.menu.replays"),
                            b -> client.setScreen(ReplayBrowserScreen.create(screen)))
                        .bounds(width / 2 - 100 - 4 - 70, height / 4 + 48 + 24, 70, 20)
                        .build());
          } else if (screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
            addPauseButtons(screen);
          }
        });
  }

  /** The title screen, or a main menu that replaces it. */
  static boolean isMainMenu(net.minecraft.client.Minecraft client, Screen screen) {
    return screen instanceof TitleScreen || isReplacementMainMenu(client, screen);
  }

  /**
   * A main menu from another mod that replaces the title screen: OneClient's, recognized by its
   * class name since it has no API for menu entries (docs/RESEARCH.md). Only outside a world.
   */
  static boolean isReplacementMainMenu(net.minecraft.client.Minecraft client, Screen screen) {
    if (client.level != null || screen instanceof TitleScreen) {
      return false;
    }
    return isReplacementMainMenuClass(screen.getClass().getName());
  }

  /** Whether a screen class name looks like OneClient's main menu. */
  public static boolean isReplacementMainMenuClass(String className) {
    String name = className.toLowerCase(java.util.Locale.ROOT);
    // org.polyfrost.oneclient.gui.OneClientMenuScreen as of 2026-11; kept loose for renames.
    return name.contains("oneclient") && (name.contains("menu") || name.contains("title"));
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
      var shadow = recorder.shadow();
      if (shadow != null) {
        Button save =
            Button.builder(
                    Component.translatable(
                        "reizo.menu.save_shadow", ReplayHud.time(shadow.bufferedSeconds() * 1000)),
                    b -> {
                      recorder.saveShadow();
                      client.setScreen(null);
                    })
                .bounds(4, y, 120, 20)
                .build();
        save.active = !shadow.isEmpty();
        buttons.add(save);
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
                  b -> client.setScreen(ExportSettingsScreen.create(screen)))
              .bounds(4, y, 120, 20)
              .build());
      y += 24;
    }
    buttons.add(
        Button.builder(
                Component.translatable("reizo.menu.replays"),
                b -> client.setScreen(ReplayBrowserScreen.create(screen)))
            .bounds(4, y, 120, 20)
            .build());
  }
}
