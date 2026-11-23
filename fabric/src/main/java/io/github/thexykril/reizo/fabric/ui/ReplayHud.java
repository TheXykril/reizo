/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.fabric.compat.Compat;
import io.github.thexykril.reizo.core.format.RecordingMetadata;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.playback.ReplayCamera;
import io.github.thexykril.reizo.fabric.playback.ReplayEditor;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.playback.ReplayServer;
import io.github.thexykril.reizo.fabric.ui.kit.Draw;
import io.github.thexykril.reizo.fabric.ui.kit.Theme;
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
    Theme.refresh();
    long duration = Math.max(1, server.script().durationMs());
    long time = Math.min(server.replayTimeMs(), duration);
    Font font = mc.font;
    int width = Math.min(360, g.guiWidth() - 20);
    int x = (g.guiWidth() - width) / 2;
    int height = HEIGHT - 6;
    int y = g.guiHeight() - HEIGHT;
    Draw.round(g, x - 1, y - 1, x + width + 1, y + height + 1, 6, Theme.alpha(Theme.LINE, 0xC0));
    Draw.round(g, x, y, x + width, y + height, 5, Theme.alpha(Theme.GROUND, 0xE0));

    // Play state icon.
    int ix = x + 8;
    int iy = y + 4;
    if (server.clock().isRunning()) {
      g.fill(ix, iy, ix + 2, iy + 7, Theme.TEXT);
      g.fill(ix + 4, iy, ix + 6, iy + 7, Theme.TEXT);
    } else {
      for (int i = 0; i < 4; i++) {
        g.fill(ix + i, iy + i, ix + i + 1, iy + 7 - i, Theme.TEXT);
      }
    }
    String clock =
        RecordingIndicator.formatElapsed(time) + " / " + RecordingIndicator.formatElapsed(duration);
    Draw.label(g, font, clock, ix + 11, y + 3, Theme.TEXT);

    String mode =
        ReplayEditor.isPathPlaying() ? "path" : ReplayCamera.mode().name().toLowerCase(Locale.ROOT);
    String right = speed(server.clock().speed()) + "x  ·  " + mode;
    String hint = RecorderKeys.timelineKeyName() + "  Editor";
    int hw = Draw.width(font, hint) + 8;
    int hx = x + width - 6 - hw;
    Draw.round(g, hx, y + 2, hx + hw, y + 12, 3, Theme.RAISED);
    Draw.label(g, font, hint, hx + 4, y + 3, Theme.DIM);
    Draw.label(g, font, right, hx - 8 - Draw.width(font, right), y + 3, Theme.DIM);

    int barX = x + 8;
    int barW = width - 16;
    int barY = y + height - 6;
    Draw.round(g, barX, barY, barX + barW, barY + 3, 1, Theme.TRACK_BG);
    int filled = (int) (barW * time / duration);
    if (filled > 1) {
      Draw.round(g, barX, barY, barX + filled, barY + 3, 1, Theme.ACCENT);
    }
    RecordingMetadata meta = ReplayPlayback.metadata();
    if (meta != null) {
      for (RecordingMetadata.Marker m : meta.markers) {
        int mx = barX + (int) (barW * Math.min(m.timeMs, duration) / duration);
        g.fill(mx, barY - 2, mx + 1, barY + 5, Theme.TRACK_MARKER);
      }
    }
    for (var k : ReplayEditor.path().keyframes()) {
      int kx = barX + (int) (barW * Math.min(k.timeMs(), duration) / duration);
      Draw.diamond(g, kx, barY + 1, 2, Theme.TEXT);
    }
    int px = barX + filled;
    Draw.round(g, px - 2, barY - 2, px + 3, barY + 5, 2, Theme.TEXT);
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
