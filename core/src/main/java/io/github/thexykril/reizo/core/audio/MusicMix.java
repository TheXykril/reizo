/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.audio;

import java.util.List;
import java.util.function.Function;

/**
 * Adds imported music (ADR 0018) to an export's mix: each item's decoded samples are placed at its
 * start, trimmed, scaled by its volume and faded in and out linearly.
 */
public final class MusicMix {
  private MusicMix() {}

  /**
   * @param mix interleaved stereo at {@link AudioMixer#SAMPLE_RATE}; added to in place, then
   *     limited to [-1, 1]
   * @param decoded the item's file as interleaved stereo at the same rate, or null when it could
   *     not be read (the item is skipped)
   * @return how many items were added
   */
  public static int addTo(float[] mix, List<MusicItem> items, Function<String, float[]> decoded) {
    int frames = mix.length / 2;
    int added = 0;
    for (MusicItem item : items) {
      float[] src = decoded.apply(item.path());
      if (src == null || src.length < 2) {
        continue;
      }
      int srcFrames = src.length / 2;
      int rate = AudioMixer.SAMPLE_RATE;
      long fileMs = srcFrames * 1000L / rate;
      int length = (int) (item.playedMs(fileMs) * rate / 1000);
      int from = (int) (item.trimMs() * rate / 1000);
      int at = (int) (item.startMs() * rate / 1000);
      int fadeIn = (int) (item.fadeInMs() * rate / 1000);
      int fadeOut = (int) (item.fadeOutMs() * rate / 1000);
      for (int i = 0; i < length; i++) {
        int f = at + i;
        int s = from + i;
        if (f >= frames || s >= srcFrames) {
          break;
        }
        float g = item.volume();
        if (fadeIn > 0 && i < fadeIn) {
          g *= i / (float) fadeIn;
        }
        int left = length - 1 - i;
        if (fadeOut > 0 && left < fadeOut) {
          g *= left / (float) fadeOut;
        }
        mix[f * 2] += src[s * 2] * g;
        mix[f * 2 + 1] += src[s * 2 + 1] * g;
      }
      added++;
    }
    for (int i = 0; i < mix.length; i++) {
      mix[i] = Math.max(-1, Math.min(1, mix[i]));
    }
    return added;
  }

  /**
   * Peaks for drawing a waveform: the largest absolute sample in each of {@code buckets} equal
   * parts of the file, 0..1.
   */
  public static float[] peaks(float[] stereo, int buckets) {
    float[] out = new float[Math.max(1, buckets)];
    int frames = stereo.length / 2;
    if (frames == 0) {
      return out;
    }
    for (int b = 0; b < out.length; b++) {
      int a = (int) ((long) frames * b / out.length);
      int e = (int) Math.max(a + 1, (long) frames * (b + 1) / out.length);
      float m = 0;
      for (int f = a; f < e && f < frames; f++) {
        m = Math.max(m, Math.max(Math.abs(stereo[f * 2]), Math.abs(stereo[f * 2 + 1])));
      }
      out[b] = Math.min(1, m);
    }
    return out;
  }
}
