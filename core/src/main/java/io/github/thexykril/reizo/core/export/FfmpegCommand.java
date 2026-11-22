/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.export;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds the FFmpeg command that turns raw RGBA frames on stdin into a video file, and finds the
 * FFmpeg executable. FFmpeg is not bundled: the user's own installation is used (system PATH or a
 * configured path), which keeps licensing simple and allows hardware encoders.
 */
public final class FfmpegCommand {
  private FfmpegCommand() {}

  /** Encoder settings. */
  public record Settings(
      String codec, int crf, String preset, int outWidth, int outHeight, String pixelFormat) {
    public static Settings h264(int crf) {
      return new Settings("libx264", crf, "medium", 0, 0, "yuv420p");
    }
  }

  /**
   * The command line. Input frames are {@code inWidth x inHeight} RGBA at {@code fps}; when the
   * settings give an output size, frames are scaled (Lanczos) to it.
   */
  public static List<String> build(
      String ffmpeg, int inWidth, int inHeight, int fps, Settings s, Path output) {
    if (inWidth <= 0 || inHeight <= 0) {
      throw new IllegalArgumentException("bad frame size " + inWidth + "x" + inHeight);
    }
    List<String> cmd = new ArrayList<>();
    cmd.add(ffmpeg);
    cmd.addAll(List.of("-hide_banner", "-loglevel", "error", "-y"));
    cmd.addAll(List.of("-f", "rawvideo", "-pix_fmt", "rgba"));
    cmd.addAll(List.of("-s", inWidth + "x" + inHeight, "-r", Integer.toString(fps)));
    cmd.addAll(List.of("-i", "-"));
    // Most encoders need even dimensions for 4:2:0.
    int w = s.outWidth() > 0 ? s.outWidth() : inWidth;
    int h = s.outHeight() > 0 ? s.outHeight() : inHeight;
    w -= w % 2;
    h -= h % 2;
    if (w != inWidth || h != inHeight) {
      cmd.addAll(List.of("-vf", "scale=" + w + ":" + h + ":flags=lanczos"));
    }
    cmd.addAll(List.of("-c:v", s.codec()));
    if (s.codec().startsWith("libx26")) {
      cmd.addAll(List.of("-preset", s.preset(), "-crf", Integer.toString(s.crf())));
    } else if (s.codec().endsWith("_nvenc")) {
      cmd.addAll(List.of("-preset", "p5", "-cq", Integer.toString(s.crf())));
    } else if (s.codec().endsWith("_vaapi") || s.codec().endsWith("_qsv")) {
      cmd.addAll(List.of("-global_quality", Integer.toString(s.crf())));
    }
    cmd.addAll(List.of("-pix_fmt", s.pixelFormat()));
    if (output.getFileName().toString().endsWith(".mp4")) {
      cmd.addAll(List.of("-movflags", "+faststart"));
    }
    cmd.add(output.toString());
    return cmd;
  }

  /**
   * Finds FFmpeg: the configured path if it is an executable file, else {@code ffmpeg} on the PATH.
   * Empty when neither exists.
   */
  public static Optional<String> locate(String configured, String pathEnv) {
    if (configured != null && !configured.isBlank()) {
      Path p = Path.of(configured.strip());
      return Files.isRegularFile(p) && Files.isExecutable(p)
          ? Optional.of(p.toString())
          : Optional.empty();
    }
    if (pathEnv == null) {
      return Optional.empty();
    }
    boolean windows = File.separatorChar == '\\';
    for (String dir : pathEnv.split(File.pathSeparator)) {
      if (dir.isBlank()) {
        continue;
      }
      Path candidate = Path.of(dir, windows ? "ffmpeg.exe" : "ffmpeg");
      if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
        return Optional.of(candidate.toString());
      }
    }
    return Optional.empty();
  }
}
