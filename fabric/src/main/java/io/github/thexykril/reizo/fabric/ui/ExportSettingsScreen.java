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
import io.github.thexykril.reizo.fabric.ui.kit.Draw;
import io.github.thexykril.reizo.fabric.ui.kit.FlatButton;
import io.github.thexykril.reizo.fabric.ui.kit.KitField;
import io.github.thexykril.reizo.fabric.ui.kit.KitScreen;
import io.github.thexykril.reizo.fabric.ui.kit.Theme;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Export settings for the running replay, saved in its project: resolution, frame rate, quality,
 * format (only those the installed FFmpeg can encode), file name, and the range set in the
 * timeline. "Export" starts the frame-by-frame export.
 */
public final class ExportSettingsScreen extends KitScreen implements ExportSettingsView {
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
  private KitField fileName;
  private int videoY;
  private int audioY;
  private int fileY;
  private int infoY;

  /**
   * The export settings screen: OneConfig's Compose version when OneConfig is installed (trial,
   * ADR 0017), else this kit screen.
   */
  public static Screen create(Screen parent) {
    if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("oneconfig")
        && !Boolean.getBoolean("reizo.kitScreens")) {
      try {
        return ComposeScreens.export(parent);
      } catch (Throwable t) {
        LOGGER.warn("OneConfig's Compose UI is not usable; using Reizo's own screen", t);
      }
    }
    return new ExportSettingsScreen(parent);
  }

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

    card(360, height - 24);
    int x = innerX();
    int w = innerW();
    int half = (w - GAP) / 2;
    int y = contentY();
    videoY = y;
    y += 12;
    cycle(x, y, half, Component.translatable("reizo.export.resolution"), RESOLUTIONS,
        () -> closest(r.width, r.height), Resolution::label,
        v -> {
          r.width = v.width();
          r.height = v.height();
          ReplayEditor.changed();
        });
    cycle(x + half + GAP, y, w - half - GAP, Component.translatable("reizo.export.fps"), FRAME_RATES,
        () -> FRAME_RATES.contains(r.fps) ? r.fps : 60, (Integer v) -> Component.literal(v + " fps"),
        v -> {
          r.fps = v;
          ReplayEditor.changed();
        });
    y += ROW_H;
    cycle(x, y, half, Component.translatable("reizo.export.quality"), QUALITIES,
        () -> closestQuality(r.crf), ExportSettingsScreen::qualityLabel,
        v -> {
          r.crf = v;
          ReplayEditor.changed();
        });
    if (!formats.isEmpty()) {
      if (!formats.contains(ExportFormat.forEncoder(r.codec))) {
        r.codec = formats.get(0).encoder();
      }
      cycle(x + half + GAP, y, w - half - GAP, Component.translatable("reizo.export.format"), formats,
          () -> ExportFormat.forEncoder(r.codec), (ExportFormat f) -> Component.literal(f.label()),
          v -> {
            r.codec = v.encoder();
            ReplayEditor.changed();
          });
    }
    y += ROW_H;
    cycle(x, y, w, Component.translatable("reizo.export.motion_blur"), MOTION_BLUR,
        () -> MOTION_BLUR.contains(r.motionBlur) ? r.motionBlur : 1,
        (Integer v) ->
            v <= 1
                ? Component.translatable("options.off")
                : Component.translatable("reizo.export.blur_samples", v),
        v -> {
          r.motionBlur = v;
          ReplayEditor.changed();
        });
    y += ROW_H + GAP;
    audioY = y;
    y += 12;
    // Sound and music levels; "Off" for sound leaves the sound track out entirely.
    cycle(x, y, half, Component.translatable("reizo.export.audio"), VOLUMES,
        () -> r.audio ? volume(r.soundVolume) : 0, ExportSettingsScreen::volumeLabel,
        v -> {
          r.audio = v > 0;
          if (v > 0) {
            r.soundVolume = v;
          }
          ReplayEditor.changed();
        });
    cycle(x + half + GAP, y, w - half - GAP, Component.translatable("reizo.export.music"), VOLUMES,
        () -> volume(r.musicVolume), ExportSettingsScreen::volumeLabel,
        v -> {
          r.musicVolume = v;
          ReplayEditor.changed();
        });
    y += ROW_H + GAP;
    fileY = y;
    y += 12;
    fileName = new KitField(font, x, y, w, 18, Component.translatable("reizo.export.file"));
    fileName.placeholder(Component.translatable("reizo.export.file_hint"));
    fileName.setMaxLength(120);
    fileName.setValue(r.output == null ? "" : r.output);
    fileName.setResponder(
        v -> {
          r.output = v.isBlank() ? null : v.strip();
          ReplayEditor.changed();
        });
    addRenderableWidget(fileName);
    y += 18 + GAP * 2;
    infoY = y;
    if (ReplayEditor.inMs() >= 0 || ReplayEditor.outMs() >= 0) {
      button(x + w - 100, y - 2, 100, Component.translatable("reizo.export.clear_range"),
          () -> {
            ReplayEditor.setRegion(true, -1);
            ReplayEditor.setRegion(false, -1);
            rebuildWidgets();
          });
    }
    button(x, footerY(), half, Component.translatable("gui.back"), this::onClose);
    FlatButton export =
        accentButton(x + half + GAP, footerY(), w - half - GAP, Component.translatable("reizo.export.start"), this::startExport);
    export.active = problem == null;
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
  protected void renderCard(GuiGraphics g, int mouseX, int mouseY) {
    sectionLabel(g, Component.translatable("reizo.export.section.video").getString(), videoY);
    sectionLabel(g, Component.translatable("reizo.export.section.audio").getString(), audioY);
    sectionLabel(g, Component.translatable("reizo.export.file").getString(), fileY);
    int x = innerX();
    int w = innerW();
    int y = infoY;
    g.enableScissor(x, infoY - 2, x + w, footerY() - 4);
    renderInfo(g, x, y, w);
    g.disableScissor();
  }

  private void renderInfo(GuiGraphics g, int x, int y, int w) {
    var server = ReplayPlayback.current();
    if (server != null) {
      long duration = server.script().durationMs();
      long in = Math.max(0, ReplayEditor.inMs());
      long out = ReplayEditor.outMs() < 0 ? duration : Math.min(ReplayEditor.outMs(), duration);
      boolean whole = ReplayEditor.inMs() < 0 && ReplayEditor.outMs() < 0;
      y =
          wrapped(
                  g,
                  Component.translatable(
                      whole ? "reizo.export.range_all" : "reizo.export.range",
                      ReplayHud.time(in),
                      ReplayHud.time(out)),
                  x,
                  y,
                  whole ? w : w - 106,
                  Theme.TEXT)
              + 6;
    }
    if (problem != null) {
      wrapped(g, Component.translatable(problem), x, y, w, Theme.DANGER);
    } else {
      y = wrapped(g, Component.translatable("reizo.export.note"), x, y, w, Theme.DIM);
      ExportFormat f = ExportFormat.forEncoder(ReplayEditor.project().render.codec);
      if (f.hardware()) {
        wrapped(g, Component.translatable("reizo.export.hardware_note"), x, y + 2, w, Theme.WARN);
      }
    }
  }

  /** Draws wrapped text and returns the y below it. */
  private int wrapped(GuiGraphics g, Component text, int x, int y, int w, int color) {
    return y + Draw.wrap(g, font, text, x, y, w, color);
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

  static Resolution closest(int w, int h) {
    for (Resolution r : RESOLUTIONS) {
      if (r.width() == Math.max(0, w) && r.height() == Math.max(0, h)) {
        return r;
      }
    }
    return RESOLUTIONS.get(0);
  }

  static int closestQuality(int crf) {
    int best = QUALITIES.get(1);
    for (int q : QUALITIES) {
      if (Math.abs(q - crf) < Math.abs(best - crf)) {
        best = q;
      }
    }
    return best;
  }

  static Component qualityLabel(int crf) {
    return Component.translatable("reizo.export.quality." + QUALITIES.indexOf(crf));
  }

  /** For tests: the formats offered. */
  @Override
  public List<ExportFormat> offeredFormats() {
    return formats;
  }
}
