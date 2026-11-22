/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.export;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The video formats the export screen offers: an FFmpeg encoder and the container it goes in.
 * Software encoders work everywhere FFmpeg has them; the hardware ones need the matching GPU and
 * driver as well, which only a test export shows.
 */
public enum ExportFormat {
  H264("libx264", "mp4", "MP4 · H.264", false),
  H265("libx265", "mp4", "MP4 · H.265", false),
  VP9("libvpx-vp9", "webm", "WebM · VP9", false),
  AV1("libsvtav1", "mkv", "MKV · AV1 (SVT)", false),
  H264_NVENC("h264_nvenc", "mp4", "MP4 · H.264 · NVIDIA", true),
  HEVC_NVENC("hevc_nvenc", "mp4", "MP4 · H.265 · NVIDIA", true),
  H264_VAAPI("h264_vaapi", "mp4", "MP4 · H.264 · VAAPI (Linux)", true),
  HEVC_VAAPI("hevc_vaapi", "mp4", "MP4 · H.265 · VAAPI (Linux)", true),
  H264_QSV("h264_qsv", "mp4", "MP4 · H.264 · Intel QSV", true);

  private final String encoder;
  private final String extension;
  private final String label;
  private final boolean hardware;

  ExportFormat(String encoder, String extension, String label, boolean hardware) {
    this.encoder = encoder;
    this.extension = extension;
    this.label = label;
    this.hardware = hardware;
  }

  public String encoder() {
    return encoder;
  }

  /** File extension without the dot. */
  public String extension() {
    return extension;
  }

  public String label() {
    return label;
  }

  public boolean hardware() {
    return hardware;
  }

  /** The format for an encoder name, or {@link #H264} when it is not one of ours. */
  public static ExportFormat forEncoder(String encoder) {
    for (ExportFormat f : values()) {
      if (f.encoder.equals(encoder)) {
        return f;
      }
    }
    return H264;
  }

  /** The formats whose encoder appears in {@code ffmpeg -encoders} output, in enum order. */
  public static List<ExportFormat> available(String encodersOutput) {
    Set<String> names = parseEncoders(encodersOutput);
    List<ExportFormat> out = new ArrayList<>();
    for (ExportFormat f : values()) {
      if (names.contains(f.encoder)) {
        out.add(f);
      }
    }
    return out;
  }

  /**
   * Video encoder names from {@code ffmpeg -hide_banner -encoders}: lines like {@code " V....D
   * libx264 description"} after the {@code ------} separator.
   */
  static Set<String> parseEncoders(String output) {
    Set<String> names = new HashSet<>();
    if (output == null) {
      return names;
    }
    boolean started = false;
    for (String line : output.split("\\R")) {
      String t = line.strip();
      if (!started) {
        started = t.startsWith("------");
        continue;
      }
      String[] parts = t.split("\\s+", 3);
      if (parts.length >= 2 && parts[0].length() == 6 && parts[0].charAt(0) == 'V') {
        names.add(parts[1].toLowerCase(Locale.ROOT));
      }
    }
    return names;
  }

  /** Replaces or adds the extension of a file name to match this format. */
  public String fileName(String name) {
    int dot = name.lastIndexOf('.');
    String base = dot > 0 ? name.substring(0, dot) : name;
    return base + "." + extension;
  }
}
