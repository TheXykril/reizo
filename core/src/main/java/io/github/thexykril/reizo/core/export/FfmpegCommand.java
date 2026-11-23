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
    String codec = s.codec();
    boolean vaapi = codec.endsWith("_vaapi");
    cmd.add(ffmpeg);
    cmd.addAll(List.of("-hide_banner", "-loglevel", "error", "-y"));
    if (vaapi) {
      // The usual first render node; frames are uploaded to it after scaling.
      cmd.addAll(List.of("-vaapi_device", "/dev/dri/renderD128"));
    }
    cmd.addAll(List.of("-f", "rawvideo", "-pix_fmt", "rgba"));
    cmd.addAll(List.of("-s", inWidth + "x" + inHeight, "-r", Integer.toString(fps)));
    cmd.addAll(List.of("-i", "-"));
    // Most encoders need even dimensions for 4:2:0.
    int w = s.outWidth() > 0 ? s.outWidth() : inWidth;
    int h = s.outHeight() > 0 ? s.outHeight() : inHeight;
    w -= w % 2;
    h -= h % 2;
    List<String> filters = new ArrayList<>();
    if (w != inWidth || h != inHeight) {
      filters.add("scale=" + w + ":" + h + ":flags=lanczos");
    }
    if (vaapi) {
      filters.add("format=nv12");
      filters.add("hwupload");
    }
    if (!filters.isEmpty()) {
      cmd.addAll(List.of("-vf", String.join(",", filters)));
    }
    cmd.addAll(List.of("-c:v", codec));
    String q = Integer.toString(s.crf());
    if (codec.startsWith("libx26")) {
      cmd.addAll(List.of("-preset", s.preset(), "-crf", q));
    } else if (codec.equals("libvpx-vp9")) {
      // Constant quality needs a zero bitrate target; row-mt and deadline keep it usable.
      cmd.addAll(List.of("-crf", q, "-b:v", "0", "-row-mt", "1", "-deadline", "good"));
    } else if (codec.equals("libsvtav1")) {
      cmd.addAll(List.of("-crf", q, "-preset", "8"));
    } else if (codec.endsWith("_nvenc")) {
      cmd.addAll(List.of("-preset", "p5", "-rc", "vbr", "-cq", q, "-b:v", "0"));
    } else if (vaapi) {
      cmd.addAll(List.of("-rc_mode", "CQP", "-qp", q));
    } else if (codec.endsWith("_qsv")) {
      cmd.addAll(List.of("-global_quality", q));
    }
    if (!vaapi) {
      // VAAPI frames are already in GPU memory as nv12; QSV wants nv12 too.
      cmd.addAll(List.of("-pix_fmt", codec.endsWith("_qsv") ? "nv12" : s.pixelFormat()));
    }
    if (output.getFileName().toString().endsWith(".mp4")) {
      cmd.addAll(List.of("-movflags", "+faststart"));
    }
    cmd.add(output.toString());
    return cmd;
  }

  /**
   * The command that adds a sound track to an encoded video without re-encoding the picture: AAC
   * for MP4 and MKV, Opus for WebM.
   */
  public static List<String> muxAudio(String ffmpeg, Path video, Path wav, Path output) {
    String name = output.getFileName().toString();
    boolean webm = name.endsWith(".webm");
    List<String> cmd = new ArrayList<>();
    cmd.add(ffmpeg);
    cmd.addAll(List.of("-hide_banner", "-loglevel", "error", "-y"));
    cmd.addAll(List.of("-i", video.toString(), "-i", wav.toString()));
    cmd.addAll(List.of("-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy"));
    cmd.addAll(
        webm ? List.of("-c:a", "libopus", "-b:a", "160k") : List.of("-c:a", "aac", "-b:a", "192k"));
    cmd.add("-shortest");
    if (name.endsWith(".mp4")) {
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
