/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.export;

import io.github.thexykril.reizo.core.audio.AudioMixer;
import io.github.thexykril.reizo.core.audio.MusicItem;
import io.github.thexykril.reizo.core.audio.MusicMix;
import io.github.thexykril.reizo.core.audio.WavWriter;
import io.github.thexykril.reizo.core.export.FfmpegCommand;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds imported music to a finished export (ADR 0018): decodes the video's sound (silence when it
 * has none) and each music file with FFmpeg, adds the music, and replaces the sound track while
 * copying the picture. Runs off the game thread.
 */
public final class MusicPass {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Music");

  private MusicPass() {}

  /** Returns null on success, else a message. */
  public static String apply(String ffmpeg, Path output, List<MusicItem> items, long durationMs) {
    if (items.isEmpty()) {
      return null;
    }
    int frames = (int) Math.ceil(durationMs / 1000.0 * AudioMixer.SAMPLE_RATE);
    float[] mix = new float[frames * 2];
    float[] own = decode(ffmpeg, output);
    if (own != null) {
      System.arraycopy(own, 0, mix, 0, Math.min(own.length, mix.length));
    }
    Map<String, float[]> files = new HashMap<>();
    for (MusicItem item : items) {
      files.computeIfAbsent(
          item.path(),
          p -> {
            float[] d = Files.isRegularFile(Path.of(p)) ? decode(ffmpeg, Path.of(p)) : null;
            if (d == null) {
              LOGGER.warn("Could not read music {}; left out", p);
            }
            return d;
          });
    }
    int added = MusicMix.addTo(mix, items, files::get);
    if (added == 0) {
      return null;
    }
    Path wav = output.resolveSibling(output.getFileName() + ".music.wav");
    Path temp = output.resolveSibling("music." + output.getFileName());
    try {
      WavWriter.write(wav, mix, 2, AudioMixer.SAMPLE_RATE);
      Process p =
          new ProcessBuilder(FfmpegCommand.replaceAudio(ffmpeg, output, wav, temp))
              .redirectErrorStream(true)
              .start();
      p.getOutputStream().close();
      String log = new String(p.getInputStream().readAllBytes());
      if (p.waitFor() != 0) {
        return "adding the music failed: " + log.strip();
      }
      Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
      LOGGER.info("Added {} music items to {}", added, output);
      return null;
    } catch (IOException e) {
      return "adding the music failed: " + e.getMessage();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "interrupted";
    } finally {
      try {
        Files.deleteIfExists(wav);
        Files.deleteIfExists(temp);
      } catch (IOException ignored) {
        // leftover temporary files
      }
    }
  }

  /** A file's sound as 48 kHz interleaved stereo floats, or null when FFmpeg cannot read it. */
  public static float[] decode(String ffmpeg, Path file) {
    try {
      Process p = new ProcessBuilder(FfmpegCommand.decodeAudio(ffmpeg, file)).start();
      p.getOutputStream().close();
      // Drain the error stream so FFmpeg never blocks on a full pipe.
      Thread drain =
          new Thread(
              () -> {
                try {
                  p.getErrorStream().transferTo(java.io.OutputStream.nullOutputStream());
                } catch (IOException ignored) {
                  // the process ended
                }
              },
              "Reizo music decode");
      drain.setDaemon(true);
      drain.start();
      byte[] raw = p.getInputStream().readAllBytes();
      if (p.waitFor() != 0 || raw.length < 8) {
        return null;
      }
      float[] out = new float[raw.length / 4];
      ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out);
      return out;
    } catch (IOException e) {
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
  }
}
