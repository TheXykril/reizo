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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Replay bar at the bottom of the screen while a replay plays: play state, time, length, speed,
 * camera mode, a progress bar and the recording's markers. Hidden with F1 like the rest of the HUD.
 */
public final class ReplayHud {
  /** Space the replay bar takes at the bottom of the screen, margin included. */
  public static final int HEIGHT = 28;

  /** How long a status message shows, fade included. */
  private static final long STATUS_MS = 1600;

  private static final long STATUS_FADE_MS = 300;

  private static Component statusText;
  private static long statusAt;

  private ReplayHud() {}

  /**
   * Shows a short message above the replay bar (speed, camera, marker changes), replacing the one
   * before it. Drawn like the bar instead of Minecraft's action bar.
   */
  public static void status(Component message) {
    statusText = message;
    statusAt = Util.getMillis();
  }

  /** The status message on screen, or null. */
  public static Component currentStatus() {
    return statusText != null && Util.getMillis() - statusAt < STATUS_MS
        ? statusText
        : null;
  }

  private static void renderStatus(GuiGraphics g, Font font, int centerX, int bottom) {
    Component text = currentStatus();
    if (text == null) {
      return;
    }
    long left = STATUS_MS - (Util.getMillis() - statusAt);
    float fade = Math.min(1f, left / (float) STATUS_FADE_MS);
    int w = Draw.width(font, text.getString()) + 20;
    int h = 16;
    int x = centerX - w / 2;
    int y = bottom - h;
    Draw.round(g, x, y, x + w, y + h, 8, Theme.alpha(Theme.GROUND, (int) (0xE8 * fade)));
    int textAlpha = Math.max(4, (int) (0xFF * fade));
    Draw.label(g, font, text.getString(), x + 10, y + 4, Theme.alpha(Theme.TEXT, textAlpha));
  }

  public static void register() {
    HudElementRegistry.addLast(
        Identifier.fromNamespaceAndPath(RecorderClient.MOD_ID, "replay_bar"),
        (graphics, tickCounter) -> render(graphics));
  }

  private static void render(GuiGraphics g) {
    ReplayServer server = ReplayPlayback.current();
    Minecraft mc = Minecraft.getInstance();
    // The timeline has its own transport; export settings sit where the bar would be.
    if (server == null
        || Compat.hudHidden(mc)
        || mc.screen instanceof TimelineScreen
        || mc.screen instanceof ExportSettingsView) {
      return;
    }
    Theme.refresh();
    long duration = Math.max(1, server.script().durationMs());
    long time = Math.min(server.replayTimeMs(), duration);
    Font font = mc.font;
    int width = Math.min(400, g.guiWidth() - 16);
    int x = (g.guiWidth() - width) / 2;
    int height = HEIGHT - 6;
    int y = g.guiHeight() - HEIGHT;
    // A floating card like OneConfig's panels: soft edge, no hard border.
    Draw.round(g, x, y, x + width, y + height, 10, Theme.alpha(Theme.GROUND, 0xE8));

    // Play state in a round button.
    int bx = x + 4;
    int by = y + 3;
    int bs = height - 6;
    Draw.round(g, bx, by, bx + bs, by + bs, bs / 2, Theme.HOVER);
    int cx = bx + bs / 2;
    int cy = by + bs / 2;
    if (server.clock().isRunning()) {
      Draw.round(g, cx - 3, cy - 3, cx - 1, cy + 3, 1, Theme.TEXT);
      Draw.round(g, cx + 1, cy - 3, cx + 3, cy + 3, 1, Theme.TEXT);
    } else {
      playTriangle(g, cx, cy, Theme.TEXT);
    }

    // Time: current bright, length dim.
    int tx = bx + bs + 6;
    int ty = y + 3;
    String now = RecordingIndicator.formatElapsed(time);
    Draw.label(g, font, now, tx, ty, Theme.TEXT);
    Draw.label(
        g,
        font,
        " / " + RecordingIndicator.formatElapsed(duration),
        tx + Draw.width(font, now),
        ty,
        Theme.DIM);

    // Right: speed and camera chips, then the editor key.
    String hint = RecorderKeys.timelineKeyName() + "  Editor";
    int rx = x + width - 6;
    rx = chip(g, font, hint, rx, ty, Theme.ACCENT_WASH, Theme.TEXT) - 4;
    String mode =
        ReplayEditor.isPathPlaying() ? "path" : ReplayCamera.mode().name().toLowerCase(Locale.ROOT);
    rx = chip(g, font, mode, rx, ty, Theme.RAISED, Theme.DIM) - 4;
    chip(g, font, speed(server.clock().speed()) + "×", rx, ty, Theme.RAISED, Theme.DIM);

    // Progress: a slider track like OneConfig's, with a round thumb.
    int barX = tx;
    int barW = x + width - 10 - barX;
    int barY = y + height - 7;
    Draw.round(g, barX, barY, barX + barW, barY + 3, 2, Theme.TRACK_BG);
    int filled = (int) (barW * time / duration);
    if (filled > 1) {
      Draw.round(g, barX, barY, barX + filled, barY + 3, 2, Theme.ACCENT);
    }
    RecordingMetadata meta = ReplayPlayback.metadata();
    if (meta != null) {
      for (RecordingMetadata.Marker m : meta.markers) {
        int mx = barX + (int) (barW * Math.min(m.timeMs, duration) / duration);
        Draw.round(g, mx - 1, barY - 2, mx + 1, barY + 5, 1, Theme.TRACK_MARKER);
      }
    }
    for (var k : ReplayEditor.path().keyframes()) {
      int kx = barX + (int) (barW * Math.min(k.timeMs(), duration) / duration);
      Draw.diamond(g, kx, barY + 1, 2, Theme.TEXT);
    }
    int px = barX + filled;
    Draw.round(g, px - 4, barY - 3, px + 4, barY + 5, 4, 0xFFFFFFFF);
    renderStatus(g, font, x + width / 2, y - 6);
  }

  /** A play triangle centred on (cx, cy), 7 GUI pixels tall, drawn at window pixels. */
  private static void playTriangle(GuiGraphics g, int cx, int cy, int color) {
    int s = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
    int h = 7 * s;
    int left = (cx - 2) * s;
    int top = cy * s - h / 2;
    g.pose().pushMatrix();
    g.pose().scale(1f / s, 1f / s);
    for (int row = 0; row < h; row++) {
      double mid = Math.abs(row + 0.5 - h / 2.0);
      int len = (int) Math.round((h / 2.0 - mid) * 1.7);
      if (len > 0) {
        g.fill(left, top + row, left + len, top + row + 1, color);
      }
    }
    g.pose().popMatrix();
  }

  /** A small rounded label ending at {@code right}; returns its left edge. */
  private static int chip(GuiGraphics g, Font font, String text, int right, int y, int bg, int fg) {
    int w = Draw.width(font, text) + 8;
    Draw.round(g, right - w, y - 1, right, y + 10, 4, bg);
    Draw.label(g, font, text, right - w + 4, y, fg);
    return right - w;
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
