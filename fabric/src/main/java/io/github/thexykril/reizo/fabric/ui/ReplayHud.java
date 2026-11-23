/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.fabric.compat.Compat;
import io.github.thexykril.reizo.core.format.RecordingMetadata;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.playback.ReplayCamera;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.playback.ReplayServer;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * Replay bar at the bottom of the screen while a replay plays: play state, time, length, speed,
 * camera mode, a progress bar and the recording's markers. Hidden with F1 like the rest of the HUD.
 */
public final class ReplayHud {
  private static final int BACKGROUND = 0xA0000000;
  private static final int TRACK = 0xFF3A3A3A;
  private static final int PROGRESS = 0xFFE5484D;
  private static final int MARKER = 0xFFF5A524;
  private static final int KEYFRAME = 0xFF4CC9F0;
  private static final int TEXT = 0xFFFFFFFF;
  private static final int DIM = 0xFFB0B0B0;

  /** Space the replay bar takes at the bottom of the screen, margin included. */
  public static final int HEIGHT = 28;

  private ReplayHud() {}

  public static void register() {
    HudElementRegistry.addLast(
        Identifier.fromNamespaceAndPath(RecorderClient.MOD_ID, "replay_bar"),
        (graphics, tickCounter) -> render(graphics));
  }

  private static void render(GuiGraphics g) {
    ReplayServer server = ReplayPlayback.current();
    Minecraft mc = Minecraft.getInstance();
    if (server == null || Compat.hudHidden(mc) || mc.screen instanceof TimelineScreen) {
      return;
    }
    long duration = Math.max(1, server.script().durationMs());
    long time = Math.min(server.replayTimeMs(), duration);
    Font font = mc.font;
    int width = Math.min(360, g.guiWidth() - 20);
    int x = (g.guiWidth() - width) / 2;
    int height = HEIGHT - 4;
    int y = g.guiHeight() - HEIGHT;
    g.fill(x, y, x + width, y + height, BACKGROUND);

    String state = server.clock().isRunning() ? "▶" : "❚❚";
    String left =
        state
            + "  "
            + RecordingIndicator.formatElapsed(time)
            + " / "
            + RecordingIndicator.formatElapsed(duration);
    String right =
        speed(server.clock().speed())
            + "x  ·  "
            + (io.github.thexykril.reizo.fabric.playback.ReplayEditor.isPathPlaying()
                ? "path"
                : ReplayCamera.mode().name().toLowerCase(Locale.ROOT));
    g.drawString(font, left, x + 6, y + 4, TEXT, false);
    g.drawString(font, right, x + width - 6 - font.width(right), y + 4, DIM, false);

    int barX = x + 6;
    int barW = width - 12;
    int barY = y + height - 7;
    g.fill(barX, barY, barX + barW, barY + 3, TRACK);
    g.fill(barX, barY, barX + (int) (barW * time / duration), barY + 3, PROGRESS);
    for (var k : io.github.thexykril.reizo.fabric.playback.ReplayEditor.path().keyframes()) {
      int kx = barX + (int) (barW * Math.min(k.timeMs(), duration) / duration);
      g.fill(kx - 1, barY - 1, kx + 2, barY + 4, KEYFRAME);
    }
    RecordingMetadata meta = ReplayPlayback.metadata();
    if (meta != null) {
      for (RecordingMetadata.Marker m : meta.markers) {
        int mx = barX + (int) (barW * Math.min(m.timeMs, duration) / duration);
        g.fill(mx, barY - 2, mx + 1, barY + 5, MARKER);
      }
    }
  }

  public static String time(long ms) {
    return RecordingIndicator.formatElapsed(ms);
  }

  static String speed(double speed) {
    return speed == Math.rint(speed)
        ? Integer.toString((int) speed)
        : String.format(Locale.ROOT, "%.3f", speed).replaceAll("0+$", "");
  }
}
