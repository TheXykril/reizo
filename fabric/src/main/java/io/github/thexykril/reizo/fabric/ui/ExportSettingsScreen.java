/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.core.export.ExportFormat;
import io.github.thexykril.reizo.core.export.FfmpegCommand;
import io.github.thexykril.reizo.core.project.Project;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.export.ExportControls;
import io.github.thexykril.reizo.fabric.playback.ReplayEditor;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Export settings for the running replay, saved in its project: resolution, frame rate, quality,
 * format (only those the installed FFmpeg can encode), file name, and the range set in the
 * timeline. "Export" starts the frame-by-frame export.
 */
public final class ExportSettingsScreen extends Screen {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Export");

  /** Output sizes; 0x0 keeps the window size. */
  record Resolution(int width, int height) {
    Component label() {
      return width == 0
          ? Component.translatable("reizo.export.resolution.window")
          : Component.literal(width + "×" + height + (height > width ? " (vertical)" : ""));
    }
  }

  static final List<Resolution> RESOLUTIONS =
      List.of(
          new Resolution(0, 0),
          new Resolution(1280, 720),
          new Resolution(1920, 1080),
          new Resolution(2560, 1440),
          new Resolution(3840, 2160),
          new Resolution(1080, 1920));

  static final List<Integer> FRAME_RATES = List.of(24, 30, 60, 120);

  /** Renders averaged per frame for motion blur; 1 is off. */
  static final List<Integer> MOTION_BLUR = List.of(1, 4, 8, 16);

  /** Quality steps as CRF-like values (lower is better). */
  static final List<Integer> QUALITIES = List.of(14, 18, 23, 28);

  /** `ffmpeg -encoders` output per FFmpeg path, read once per game. */
  private static final Map<String, List<ExportFormat>> FORMATS = new HashMap<>();

  private final Screen parent;
  private List<ExportFormat> formats = List.of();
  private String problem;
  private EditBox fileName;

  public ExportSettingsScreen(Screen parent) {
    super(Component.translatable("reizo.export.title"));
    this.parent = parent;
  }

  @Override
  protected void init() {
    Project project = ReplayEditor.project();
    if (project == null || ReplayPlayback.current() == null) {
      onClose();
      return;
    }
    Project.Render r = project.render;
    Optional<String> ffmpeg =
        FfmpegCommand.locate(RecorderClient.get().config().ffmpegPath, System.getenv("PATH"));
    formats = ffmpeg.map(ExportSettingsScreen::formatsOf).orElse(List.of());
    problem =
        ffmpeg.isEmpty()
            ? "reizo.export.no_ffmpeg"
            : formats.isEmpty() ? "reizo.export.no_formats" : null;

    int x = width / 2 - 155;
    int y = 34;
    addRenderableWidget(
        io.github.thexykril.reizo.fabric.compat.Compat.cycle(Resolution::label, closest(r.width, r.height))
            .withValues(RESOLUTIONS)
            .create(
                x,
                y,
                150,
                20,
                Component.translatable("reizo.export.resolution"),
                (b, v) -> {
                  r.width = v.width();
                  r.height = v.height();
                  ReplayEditor.changed();
                }));
    addRenderableWidget(
        io.github.thexykril.reizo.fabric.compat.Compat.cycle(
                (Integer v) -> Component.literal(v + " fps"),
                FRAME_RATES.contains(r.fps) ? r.fps : 60)
            .withValues(FRAME_RATES)
            .create(
                x + 160,
                y,
                150,
                20,
                Component.translatable("reizo.export.fps"),
                (b, v) -> {
                  r.fps = v;
                  ReplayEditor.changed();
                }));
    y += 24;
    addRenderableWidget(
        io.github.thexykril.reizo.fabric.compat.Compat.cycle(ExportSettingsScreen::qualityLabel, closestQuality(r.crf))
            .withValues(QUALITIES)
            .create(
                x,
                y,
                150,
                20,
                Component.translatable("reizo.export.quality"),
                (b, v) -> {
                  r.crf = v;
                  ReplayEditor.changed();
                }));
    if (!formats.isEmpty()) {
      ExportFormat current = ExportFormat.forEncoder(r.codec);
      if (!formats.contains(current)) {
        current = formats.get(0);
        r.codec = current.encoder();
      }
      addRenderableWidget(
          io.github.thexykril.reizo.fabric.compat.Compat.cycle((ExportFormat f) -> Component.literal(f.label()), current)
              .withValues(formats)
              .create(
                  x + 160,
                  y,
                  150,
                  20,
                  Component.translatable("reizo.export.format"),
                  (b, v) -> {
                    r.codec = v.encoder();
                    ReplayEditor.changed();
                  }));
    }
    y += 24;
    // Sound and music levels; "Off" for sound leaves the sound track out entirely.
    addRenderableWidget(
        io.github.thexykril.reizo.fabric.compat.Compat.cycle(ExportSettingsScreen::volumeLabel, r.audio ? volume(r.soundVolume) : 0)
            .withValues(VOLUMES)
            .create(
                x,
                y,
                102,
                20,
                Component.translatable("reizo.export.audio"),
                (b, v) -> {
                  r.audio = v > 0;
                  if (v > 0) {
                    r.soundVolume = v;
                  }
                  ReplayEditor.changed();
                }));
    addRenderableWidget(
        io.github.thexykril.reizo.fabric.compat.Compat.cycle(ExportSettingsScreen::volumeLabel, volume(r.musicVolume))
            .withValues(VOLUMES)
            .create(
                x + 104,
                y,
                102,
                20,
                Component.translatable("reizo.export.music"),
                (b, v) -> {
                  r.musicVolume = v;
                  ReplayEditor.changed();
                }));
    addRenderableWidget(
        io.github.thexykril.reizo.fabric.compat.Compat.cycle(
                (Integer v) ->
                    v <= 1
                        ? Component.translatable("options.off")
                        : Component.translatable("reizo.export.blur_samples", v),
                MOTION_BLUR.contains(r.motionBlur) ? r.motionBlur : 1)
            .withValues(MOTION_BLUR)
            .create(
                x + 208,
                y,
                102,
                20,
                Component.translatable("reizo.export.motion_blur"),
                (b, v) -> {
                  r.motionBlur = v;
                  ReplayEditor.changed();
                }));
    y += 34;
    fileName = new EditBox(font, x, y, 310, 20, Component.translatable("reizo.export.file"));
    fileName.setMaxLength(120);
    fileName.setHint(Component.translatable("reizo.export.file_hint"));
    fileName.setValue(r.output == null ? "" : r.output);
    fileName.setResponder(
        v -> {
          r.output = v.isBlank() ? null : v.strip();
          ReplayEditor.changed();
        });
    addRenderableWidget(fileName);
    y += 34;
    if (ReplayEditor.inMs() >= 0 || ReplayEditor.outMs() >= 0) {
      addRenderableWidget(
          Button.builder(
                  Component.translatable("reizo.export.clear_range"),
                  b -> {
                    ReplayEditor.setRegion(true, -1);
                    ReplayEditor.setRegion(false, -1);
                    rebuildWidgets();
                  })
              .bounds(x + 210, y - 4, 100, 20)
              .build());
    }
    Button export =
        addRenderableWidget(
            Button.builder(Component.translatable("reizo.export.start"), b -> startExport())
                .bounds(width / 2 - 155, height - 28, 150, 20)
                .build());
    export.active = problem == null;
    addRenderableWidget(
        Button.builder(Component.translatable("gui.back"), b -> onClose())
            .bounds(width / 2 + 5, height - 28, 150, 20)
            .build());
  }

  private static final List<Integer> VOLUMES = List.of(0, 25, 50, 75, 100);

  private static Component volumeLabel(Integer v) {
    return v <= 0 ? Component.translatable("options.off") : Component.literal(v + "%");
  }

  /** The closest offered level to a stored one. */
  private static int volume(int stored) {
    int best = 100;
    for (int v : VOLUMES) {
      if (Math.abs(v - stored) < Math.abs(best - stored)) {
        best = v;
      }
    }
    return best;
  }

  private void startExport() {
    minecraft.setScreen(null);
    ExportControls.exportCurrent(minecraft, null);
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    super.render(g, mouseX, mouseY, partialTick);
    g.drawCenteredString(font, title, width / 2, 15, 0xFFFFFFFF);
    int x = width / 2 - 155;
    g.drawString(font, Component.translatable("reizo.export.file"), x, 106, 0xFFA0A0A0);
    var server = ReplayPlayback.current();
    if (server != null) {
      long duration = server.script().durationMs();
      long in = Math.max(0, ReplayEditor.inMs());
      long out = ReplayEditor.outMs() < 0 ? duration : Math.min(ReplayEditor.outMs(), duration);
      boolean whole = ReplayEditor.inMs() < 0 && ReplayEditor.outMs() < 0;
      // Narrower when the "Whole replay" button sits to the right.
      wrapped(
          g,
          Component.translatable(
              whole ? "reizo.export.range_all" : "reizo.export.range",
              ReplayHud.time(in),
              ReplayHud.time(out)),
          x,
          140,
          whole ? 310 : 205,
          0xFFFFFFFF);
    }
    int y = 164;
    if (problem != null) {
      wrapped(g, Component.translatable(problem), x, y, 310, 0xFFFF6060);
    } else {
      y = wrapped(g, Component.translatable("reizo.export.note"), x, y, 310, 0xFFA0A0A0);
      ExportFormat f = ExportFormat.forEncoder(ReplayEditor.project().render.codec);
      if (f.hardware()) {
        wrapped(g, Component.translatable("reizo.export.hardware_note"), x, y + 2, 310, 0xFFE0C060);
      }
    }
  }

  /** Draws wrapped text and returns the y below it. */
  private int wrapped(GuiGraphics g, Component text, int x, int y, int w, int color) {
    g.drawWordWrap(font, text, x, y, w, color);
    return y + font.split(text, w).size() * font.lineHeight;
  }

  @Override
  public void onClose() {
    minecraft.setScreen(parent);
  }

  /** The formats the FFmpeg at {@code path} lists, cached. */
  static List<ExportFormat> formatsOf(String path) {
    synchronized (FORMATS) {
      return FORMATS.computeIfAbsent(path, ExportSettingsScreen::probe);
    }
  }

  private static List<ExportFormat> probe(String path) {
    try {
      Process p =
          new ProcessBuilder(path, "-hide_banner", "-encoders").redirectErrorStream(true).start();
      p.getOutputStream().close();
      String out;
      try (InputStream in = p.getInputStream()) {
        out = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      if (!p.waitFor(5, TimeUnit.SECONDS)) {
        p.destroyForcibly();
      }
      return ExportFormat.available(out);
    } catch (IOException e) {
      LOGGER.warn("Could not list FFmpeg encoders", e);
      return List.of();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return List.of();
    }
  }

  private static Resolution closest(int w, int h) {
    for (Resolution r : RESOLUTIONS) {
      if (r.width() == Math.max(0, w) && r.height() == Math.max(0, h)) {
        return r;
      }
    }
    return RESOLUTIONS.get(0);
  }

  private static int closestQuality(int crf) {
    int best = QUALITIES.get(1);
    for (int q : QUALITIES) {
      if (Math.abs(q - crf) < Math.abs(best - crf)) {
        best = q;
      }
    }
    return best;
  }

  private static Component qualityLabel(int crf) {
    return Component.translatable("reizo.export.quality." + QUALITIES.indexOf(crf));
  }

  /** For tests: the formats offered. */
  public List<ExportFormat> offeredFormats() {
    return formats;
  }
}
