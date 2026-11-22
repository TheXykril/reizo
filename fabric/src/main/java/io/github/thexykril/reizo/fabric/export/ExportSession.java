/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.export;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.thexykril.reizo.core.export.ExportPlan;
import io.github.thexykril.reizo.core.export.FfmpegCommand;
import io.github.thexykril.reizo.core.export.RenderSize;
import io.github.thexykril.reizo.fabric.playback.ReplayEditor;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.playback.ReplayServer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders a replay to a video, frame by frame, independent of how fast the computer renders.
 *
 * <p>For each frame the replay server sends every packet up to the frame's time and then a ping;
 * the frame is only shown and captured once the client has answered that ping, so the world is
 * exactly as recorded at that time. Minecraft's own timer is fed a virtual clock that advances by
 * one frame duration per captured frame (see {@code MinecraftMixin}), so ticks and interpolation
 * are deterministic too. Frames are read back from the main render target and piped to FFmpeg.
 *
 * <p>With an output size set, the game renders at that size for the export (see {@code
 * WindowMixin}): the window then shows the bottom-left part of each frame, at full detail.
 *
 * <p>Client (render) thread only.
 */
public final class ExportSession {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Export");

  /** States of an export. */
  public enum State {
    /** Seeking to the start and waiting for the world. */
    PREPARING,
    RUNNING,
    FINISHING,
    DONE,
    FAILED,
    CANCELLED
  }

  private static ExportSession current;
  private static volatile RenderSize renderOverride;

  private final ExportPlan plan;
  private final Path output;
  private final FfmpegCommand.Settings settings;
  private final String ffmpeg;
  private final Consumer<ExportSession> onEnd;
  private State state = State.PREPARING;
  private FrameEncoder encoder;
  private int width;
  private int height;
  private int frame; // next frame to capture
  private int captured;
  private long virtualBaseMs = -1;
  // Whether the frame being rendered now shows the next frame's time (decided once per frame).
  private boolean readyThisFrame;
  private boolean hideGuiBefore;
  private boolean pathBefore;
  private String error;

  private ExportSession(
      ExportPlan plan,
      Path output,
      FfmpegCommand.Settings settings,
      String ffmpeg,
      Consumer<ExportSession> onEnd) {
    this.plan = plan;
    this.output = output;
    this.settings = settings;
    this.ffmpeg = ffmpeg;
    this.onEnd = onEnd;
  }

  public static ExportSession current() {
    return current;
  }

  /** The size the game renders at instead of the window size, or null. */
  public static RenderSize renderOverride() {
    return renderOverride;
  }

  public static boolean isExporting() {
    return current != null && current.state == State.RUNNING;
  }

  /**
   * Starts exporting the running replay. The camera path plays during the export when there is one.
   *
   * @throws IllegalStateException if no replay is in a world or an export is running
   */
  public static ExportSession start(
      Minecraft mc,
      ExportPlan plan,
      Path output,
      FfmpegCommand.Settings settings,
      String ffmpeg,
      Consumer<ExportSession> onEnd) {
    ReplayServer server = ReplayPlayback.current();
    if (server == null || mc.level == null) {
      throw new IllegalStateException("No replay is playing");
    }
    if (current != null) {
      throw new IllegalStateException("An export is already running");
    }
    ExportSession s = new ExportSession(plan, output, settings, ffmpeg, onEnd);
    s.hideGuiBefore = mc.options.hideGui;
    s.pathBefore = ReplayEditor.isPathPlaying();
    current = s;
    server.setPaused(true);
    server.seek(plan.startMs());
    LOGGER.info("Export of {} frames to {} starting", plan.frameCount(), output);
    return s;
  }

  public State state() {
    return state;
  }

  public double progress() {
    return plan.progress(captured);
  }

  public int framesCaptured() {
    return captured;
  }

  public Path output() {
    return output;
  }

  public String error() {
    return error;
  }

  public void cancel() {
    if (state == State.PREPARING || state == State.RUNNING) {
      end(State.CANCELLED, null);
    }
  }

  // ---- hooks from MinecraftMixin ----

  /**
   * The time Minecraft's timer should see this frame: unchanged while not exporting; during an
   * export, the virtual time of the frame being produced.
   */
  public static long timerMillis(long realMillis) {
    ExportSession s = current;
    if (s == null || s.state != State.RUNNING) {
      return realMillis;
    }
    if (s.virtualBaseMs < 0) {
      s.virtualBaseMs = realMillis;
    }
    // The world advances only once the frame's packets are applied (see frameReady). Decided
    // here, before the frame's ticks, so the capture after it matches what was rendered.
    s.readyThisFrame = s.frameReady() && s.encoder.hasRoom();
    int shown = s.readyThisFrame ? s.frame : Math.max(0, s.frame - 1);
    return s.virtualBaseMs + Math.round(shown * 1000.0 / s.plan.fps());
  }

  /** Called after each rendered frame. */
  public static void afterFrame(Minecraft mc) {
    ExportSession s = current;
    if (s != null) {
      s.step(mc);
    }
  }

  private boolean frameReady() {
    ReplayServer server = ReplayPlayback.current();
    return server != null && server.syncedThrough() >= frame + 1;
  }

  private void step(Minecraft mc) {
    ReplayServer server = ReplayPlayback.current();
    if (server == null) {
      end(State.FAILED, "the replay ended");
      return;
    }
    switch (state) {
      case PREPARING -> {
        if (server.isReconfiguring()
            || mc.level == null
            || mc.screen != null
            || Math.abs(server.clock().timeMs() - plan.startMs()) > 5) {
          return;
        }
        RenderTarget target = mc.getMainRenderTarget();
        if (renderOverride == null) {
          RenderSize size =
              RenderSize.of(
                  settings.outWidth(),
                  settings.outHeight(),
                  target.width,
                  target.height,
                  RenderSystem.getDevice().getMaxTextureSize());
          if (!size.matches(target.width, target.height)) {
            renderOverride = size;
            mc.resizeDisplay();
            LOGGER.info("Rendering the export at {}x{}", size.width(), size.height());
            return; // the next frame renders at the new size
          }
        }
        width = target.width;
        height = target.height;
        try {
          encoder =
              new FrameEncoder(
                  FfmpegCommand.build(ffmpeg, width, height, plan.fps(), settings, output), output);
        } catch (IOException e) {
          end(State.FAILED, "could not start FFmpeg: " + e.getMessage());
          return;
        }
        mc.options.hideGui = true;
        ReplayEditor.setPathPlaying(true);
        server.setManual(true, plan.startMs());
        server.advanceTo(Math.round(plan.timeOfFrame(0)), 1);
        state = State.RUNNING;
      }
      case RUNNING -> {
        if (mc.screen != null) {
          // A menu would be captured into the video; Esc cancels the export instead.
          end(State.CANCELLED, null);
          return;
        }
        if (!readyThisFrame) {
          return;
        }
        readyThisFrame = false;
        RenderTarget target = mc.getMainRenderTarget();
        if (target.width != width || target.height != height) {
          end(State.FAILED, "the window was resized during the export");
          return;
        }
        int index = frame;
        Screenshot.takeScreenshot(target, image -> deliver(index, image));
        frame++;
        if (frame % 10 == 0) {
          mc.getWindow()
              .setTitle(
                  String.format(
                      java.util.Locale.ROOT, "Reizo export %.0f%%", 100 * plan.progress(frame)));
        }
        if (frame >= plan.frameCount()) {
          state = State.FINISHING;
        } else {
          server.advanceTo(Math.round(plan.timeOfFrame(frame)), frame + 1);
        }
      }
      case FINISHING -> {
        if (captured >= plan.frameCount()) {
          try {
            encoder.finish();
            end(State.DONE, null);
          } catch (IOException e) {
            end(State.FAILED, e.getMessage());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            end(State.FAILED, "interrupted");
          }
        }
      }
      default -> {}
    }
  }

  private void deliver(int index, NativeImage image) {
    try (image) {
      if (state != State.RUNNING && state != State.FINISHING) {
        return;
      }
      if (index != captured) {
        throw new IllegalStateException("frame " + index + " arrived, expected " + captured);
      }
      int[] abgr = image.getPixelsABGR();
      byte[] rgba = new byte[abgr.length * 4];
      for (int i = 0, o = 0; i < abgr.length; i++) {
        int p = abgr[i];
        rgba[o++] = (byte) p;
        rgba[o++] = (byte) (p >>> 8);
        rgba[o++] = (byte) (p >>> 16);
        rgba[o++] = (byte) 0xFF;
      }
      encoder.submit(rgba);
      captured++;
    } catch (IOException | RuntimeException e) {
      end(State.FAILED, e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      end(State.FAILED, "interrupted");
    }
  }

  private void end(State result, String message) {
    if (state == State.DONE || state == State.FAILED || state == State.CANCELLED) {
      return;
    }
    state = result;
    error = message;
    if (encoder != null && result != State.DONE) {
      encoder.close();
    }
    Minecraft mc = Minecraft.getInstance();
    mc.options.hideGui = hideGuiBefore;
    if (renderOverride != null) {
      renderOverride = null;
      mc.resizeDisplay();
    }
    mc.updateTitle();
    ReplayEditor.setPathPlaying(pathBefore);
    ReplayServer server = ReplayPlayback.current();
    if (server != null) {
      server.setManual(false, 0);
      server.seek(Math.round(plan.timeOfFrame(Math.max(0, frame - 1))));
    }
    current = null;
    if (result == State.DONE) {
      LOGGER.info("Exported {} frames to {}", captured, output);
    } else {
      LOGGER.warn("Export {}: {}", result, message);
    }
    onEnd.accept(this);
  }
}
