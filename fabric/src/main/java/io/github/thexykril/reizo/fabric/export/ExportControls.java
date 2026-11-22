/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.export;

import io.github.thexykril.reizo.core.export.ExportFormat;
import io.github.thexykril.reizo.core.export.ExportPlan;
import io.github.thexykril.reizo.core.export.FfmpegCommand;
import io.github.thexykril.reizo.core.project.Project;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.playback.ReplayEditor;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.playback.ReplayServer;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Starts an export of the running replay with its project's settings, and reports the result. */
public final class ExportControls {
  private static final DateTimeFormatter FILE_DATE =
      DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);

  private ExportControls() {}

  /** Exports folder, next to the recordings folder. */
  public static Path exportsDir() {
    return RecorderClient.get().storage().root().resolveSibling("exports");
  }

  /**
   * Exports the running replay: the project's in/out range (or all of it), its frame rate, size and
   * quality. Returns false (with a toast) when it cannot start.
   */
  public static boolean exportCurrent(Minecraft mc, Consumer<ExportSession> onEnd) {
    ReplayServer server = ReplayPlayback.current();
    Project project = ReplayEditor.project();
    if (server == null || project == null || mc.level == null) {
      return false;
    }
    Optional<String> ffmpeg =
        FfmpegCommand.locate(RecorderClient.get().config().ffmpegPath, System.getenv("PATH"));
    if (ffmpeg.isEmpty()) {
      RecorderClient.toast("reizo.export.no_ffmpeg", null);
      return false;
    }
    long duration = server.script().durationMs();
    long in = project.inMs >= 0 ? Math.min(project.inMs, duration) : 0;
    long out = project.outMs >= 0 ? Math.min(project.outMs, duration) : duration;
    if (out <= in) {
      in = 0;
      out = duration;
    }
    Project.Render r = project.render;
    ExportFormat format = ExportFormat.forEncoder(r.codec);
    String name =
        format.fileName(
            r.output != null && !r.output.isBlank()
                ? r.output.strip()
                : safe(project.name) + "_" + LocalDateTime.now().format(FILE_DATE));
    Path output = exportsDir().resolve(name);
    FfmpegCommand.Settings settings =
        new FfmpegCommand.Settings(format.encoder(), r.crf, "medium", r.width, r.height, "yuv420p");
    ExportSession.start(
        mc,
        new ExportPlan(in, out, r.fps),
        output,
        settings,
        ffmpeg.get(),
        session -> {
          switch (session.state()) {
            case DONE ->
                RecorderClient.toast(
                    "reizo.export.done",
                    Component.literal(session.output().getFileName().toString()));
            case CANCELLED -> RecorderClient.toast("reizo.export.cancelled", null);
            default ->
                RecorderClient.toast(
                    "reizo.export.failed", Component.literal(String.valueOf(session.error())));
          }
          if (onEnd != null) {
            onEnd.accept(session);
          }
        });
    RecorderClient.toast("reizo.export.started", null);
    return true;
  }

  private static String safe(String name) {
    String s = name == null ? "replay" : name.replaceAll("[^A-Za-z0-9 ._-]", "_").strip();
    return s.isEmpty() ? "replay" : s;
  }
}
