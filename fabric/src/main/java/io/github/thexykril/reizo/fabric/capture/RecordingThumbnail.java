/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.capture;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.thexykril.reizo.core.recording.RecordingSession;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes one small picture of the world shortly after a recording starts and stores it as the
 * recording's thumbnail. It is captured right after the world is drawn, before the HUD and menus,
 * and only while no screen is open.
 */
public final class RecordingThumbnail {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Thumbnail");

  /** Thumbnail width in pixels; the height follows the window's aspect ratio. */
  public static final int WIDTH = 480;

  /** Recording time to wait before the picture, so the world around the player has loaded. */
  private static final long DELAY_MS = 1500;

  private static String capturedFor;

  private RecordingThumbnail() {}

  /** Called every frame right after the world is drawn. */
  public static void afterWorld(Minecraft mc) {
    RecorderClient client = RecorderClient.get();
    RecordingSession session = client == null ? null : client.session();
    if (session == null
        || session.id().equals(capturedFor)
        || session.state() != RecordingSession.State.RECORDING
        || session.elapsedMillis() < DELAY_MS
        || mc.screen != null
        || mc.level == null
        || ReplayPlayback.isActive()) {
      return;
    }
    capturedFor = session.id();
    RenderTarget target = mc.getMainRenderTarget();
    Screenshot.takeScreenshot(target, image -> store(session, image));
  }

  private static void store(RecordingSession session, NativeImage image) {
    int w;
    int h;
    int[] abgr;
    try (image) {
      w = image.getWidth();
      h = image.getHeight();
      abgr = image.getPixelsABGR();
    }
    CompletableFuture.runAsync(
        () -> {
          try {
            session.saveThumbnail(encode(abgr, w, h));
          } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not save the recording thumbnail", e);
          }
        });
  }

  /** Box-filters the picture down to {@link #WIDTH} wide and encodes it as PNG. */
  static byte[] encode(int[] abgr, int w, int h) throws IOException {
    int tw = Math.min(WIDTH, w);
    int th = Math.max(1, Math.round(h * (tw / (float) w)));
    BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
    for (int ty = 0; ty < th; ty++) {
      int y0 = ty * h / th;
      int y1 = Math.max(y0 + 1, (ty + 1) * h / th);
      for (int tx = 0; tx < tw; tx++) {
        int x0 = tx * w / tw;
        int x1 = Math.max(x0 + 1, (tx + 1) * w / tw);
        long r = 0;
        long g = 0;
        long b = 0;
        for (int y = y0; y < y1; y++) {
          int row = y * w;
          for (int x = x0; x < x1; x++) {
            int p = abgr[row + x];
            r += p & 0xFF;
            g += (p >>> 8) & 0xFF;
            b += (p >>> 16) & 0xFF;
          }
        }
        int n = (y1 - y0) * (x1 - x0);
        out.setRGB(tx, ty, (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n));
      }
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(out, "png", bytes);
    return bytes.toByteArray();
  }
}
