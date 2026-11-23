/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.core.recording.RecordingSession;
import io.github.thexykril.reizo.fabric.RecorderClient;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * Top-right HUD indicator: a red dot and elapsed time while recording, an amber dot while paused.
 * Draws nothing (and costs nothing) when idle.
 */
public final class RecordingIndicator {
  private static final int RED = 0xFFE5484D;
  private static final int AMBER = 0xFFF5A524;
  private static final int TEXT = 0xFFFFFFFF;
  private static final int BACKGROUND = 0x80000000;

  private RecordingIndicator() {}

  public static void register(RecorderClient client) {
    HudElementRegistry.addLast(
        Identifier.fromNamespaceAndPath(RecorderClient.MOD_ID, "recording_indicator"),
        (graphics, tickCounter) -> render(client, graphics));
  }

  private static void render(RecorderClient client, GuiGraphics graphics) {
    RecordingSession session = client.session();
    if (session == null || !client.config().showIndicator) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc.options.hideGui) {
      return;
    }
    RecordingSession.State state = session.state();
    boolean paused = state == RecordingSession.State.PAUSED;
    String label =
        switch (state) {
          case PAUSED -> "PAUSED " + formatElapsed(session.elapsedMillis());
          case STOPPING -> "SAVING";
          default -> "REC " + formatElapsed(session.elapsedMillis());
        };
    Font font = mc.font;
    int textWidth = font.width(label);
    int padding = 4;
    int dot = 6;
    int width = padding + dot + 4 + textWidth + padding;
    int height = font.lineHeight + 2 * padding - 2;
    int x = graphics.guiWidth() - width - 6;
    int y = 6;
    graphics.fill(x, y, x + width, y + height, BACKGROUND);
    // Blink the dot once per second while recording.
    boolean dotVisible = paused || (System.currentTimeMillis() / 500) % 2 == 0;
    int dotY = y + (height - dot) / 2;
    if (dotVisible) {
      graphics.fill(x + padding, dotY, x + padding + dot, dotY + dot, paused ? AMBER : RED);
    }
    graphics.drawString(font, label, x + padding + dot + 4, y + padding - 1, TEXT, false);
  }

  public static String formatElapsed(long millis) {
    long totalSeconds = millis / 1000;
    long hours = totalSeconds / 3600;
    long minutes = (totalSeconds / 60) % 60;
    long seconds = totalSeconds % 60;
    return hours > 0
        ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
  }
}
