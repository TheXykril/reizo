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
import io.github.thexykril.reizo.core.timeline.Sequence;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    partsProgress = 0;
    partCount = 1;
    FfmpegCommand.Settings settings =
        new FfmpegCommand.Settings(format.encoder(), r.crf, "medium", r.width, r.height, "yuv420p");
    Sequence clips = ReplayEditor.sequence();
    if (clips.size() > 1) {
      // One frame-exact export per clip, joined without re-encoding (ADR 0016).
      List<Path> parts = new ArrayList<>();
      for (int i = 0; i < clips.size(); i++) {
        parts.add(output.resolveSibling(output.getFileName() + ".part" + i + "." + format.extension()));
      }
      exportPart(mc, clips, 0, parts, output, settings, ffmpeg.get(), r, onEnd);
      startedToast(output);
      return true;
    }
    if (clips.size() == 1) {
      in = clips.clip(0).inMs();
      out = Math.min(clips.clip(0).outMs(), duration);
    }
    ExportSession.start(
        mc,
        new ExportPlan(in, out, r.fps, Math.max(1, Math.min(16, r.motionBlur)), 0.5),
        output,
        settings,
        ffmpeg.get(),
        r.audio,
        Math.max(0, Math.min(100, r.soundVolume)) / 100f,
        Math.max(0, Math.min(100, r.musicVolume)) / 100f,
        session -> {
          dismissProgress();
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
    startedToast(output);
    return true;
  }

  /** Overall progress of a clip export: parts done plus the running part's share. */
  private static double partsProgress;

  private static int partCount = 1;

  private static double progress() {
    ExportSession s = ExportSession.current();
    double part = s == null ? 1 : s.progress();
    return Math.min(1, (partsProgress + part) / partCount);
  }

  private static void exportPart(
      Minecraft mc,
      Sequence clips,
      int index,
      List<Path> parts,
      Path output,
      FfmpegCommand.Settings settings,
      String ffmpeg,
      Project.Render r,
      Consumer<ExportSession> onEnd) {
    partsProgress = index;
    partCount = clips.size();
    Sequence.Clip clip = clips.clip(index);
    long duration = ReplayPlayback.current() == null ? clip.outMs() : ReplayPlayback.current().script().durationMs();
    ExportSession.start(
        mc,
        new ExportPlan(
            clip.inMs(),
            Math.min(clip.outMs(), duration),
            r.fps,
            Math.max(1, Math.min(16, r.motionBlur)),
            0.5),
        parts.get(index),
        settings,
        ffmpeg,
        r.audio,
        Math.max(0, Math.min(100, r.soundVolume)) / 100f,
        Math.max(0, Math.min(100, r.musicVolume)) / 100f,
        session -> {
          if (session.state() == ExportSession.State.DONE && index + 1 < clips.size()) {
            exportPart(mc, clips, index + 1, parts, output, settings, ffmpeg, r, onEnd);
            return;
          }
          if (session.state() != ExportSession.State.DONE) {
            dismissProgress();
            deleteAll(parts);
            if (session.state() == ExportSession.State.CANCELLED) {
              RecorderClient.toast("reizo.export.cancelled", null);
            } else {
              RecorderClient.toast(
                  "reizo.export.failed", Component.literal(String.valueOf(session.error())));
            }
            if (onEnd != null) {
              onEnd.accept(session);
            }
            return;
          }
          partsProgress = partCount;
          Thread joiner =
              new Thread(
                  () -> {
                    String error = join(ffmpeg, parts, output);
                    deleteAll(parts);
                    mc.execute(
                        () -> {
                          dismissProgress();
                          if (error == null) {
                            RecorderClient.toast(
                                "reizo.export.done",
                                Component.literal(output.getFileName().toString()));
                          } else {
                            RecorderClient.toast("reizo.export.failed", Component.literal(error));
                          }
                          if (onEnd != null) {
                            onEnd.accept(session);
                          }
                        });
                  },
                  "Reizo export join");
          joiner.setDaemon(true);
          joiner.start();
        });
  }

  /** Joins the parts with FFmpeg's concat demuxer, copying the streams. Null on success. */
  static String join(String ffmpeg, List<Path> parts, Path output) {
    Path list = output.resolveSibling(output.getFileName() + ".parts.txt");
    try {
      StringBuilder sb = new StringBuilder();
      for (Path p : parts) {
        sb.append("file '")
            .append(p.toAbsolutePath().toString().replace("'", "'\\''"))
            .append("'\n");
      }
      Files.writeString(list, sb.toString());
      Process p =
          new ProcessBuilder(
                  ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-f", "concat", "-safe", "0",
                  "-i", list.toString(), "-c", "copy", output.toString())
              .redirectErrorStream(true)
              .start();
      p.getOutputStream().close();
      String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int code = p.waitFor();
      return code == 0 ? null : "FFmpeg could not join the clips: " + out.strip();
    } catch (IOException e) {
      return "could not join the clips: " + e.getMessage();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "interrupted";
    } finally {
      try {
        Files.deleteIfExists(list);
      } catch (IOException ignored) {
        // a leftover list file
      }
    }
  }

  private static void deleteAll(List<Path> parts) {
    for (Path p : parts) {
      try {
        Files.deleteIfExists(p);
      } catch (IOException ignored) {
        // a leftover part
      }
    }
  }

  private static void startedToast(Path output) {
    if (RecorderClient.useOneConfigNotifications()) {
      // In OneClient: one notification with a progress bar instead of a start toast.
      try {
        progressNotification =
            io.github.thexykril.reizo.fabric.compat.OneConfigNotifications.progress(
                Component.translatable("reizo.export.started").getString(),
                output.getFileName().toString(),
                ExportControls::progress);
        return;
      } catch (Throwable t) {
        RecorderClient.oneConfigNotificationsFailed(t);
      }
    }
    RecorderClient.toast("reizo.export.started", null);
  }

  /** The OneConfig progress notification of the running export, or null. */
  private static Object progressNotification;

  private static void dismissProgress() {
    Object n = progressNotification;
    progressNotification = null;
    if (n != null) {
      try {
        io.github.thexykril.reizo.fabric.compat.OneConfigNotifications.dismiss(n);
      } catch (Throwable t) {
        RecorderClient.oneConfigNotificationsFailed(t);
      }
    }
  }

  private static String safe(String name) {
    String s = name == null ? "replay" : name.replaceAll("[^A-Za-z0-9 ._-]", "_").strip();
    return s.isEmpty() ? "replay" : s;
  }
}
