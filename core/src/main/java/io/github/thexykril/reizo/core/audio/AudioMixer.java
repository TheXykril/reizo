/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.audio;

import java.util.List;
import java.util.function.Function;

/**
 * Renders an export's sound track offline from the sounds that started during it: each clip is
 * resampled for its pitch, attenuated by distance, panned by direction and summed. Sounds with a
 * path (moving or changing ones) are re-evaluated every {@link #BLOCK} samples with the gains
 * ramped in between; looping sounds repeat until they stop. The result is exact to the sample,
 * independent of how fast the export ran.
 */
public final class AudioMixer {
  public static final int SAMPLE_RATE = 48_000;

  /** Samples between two evaluations of a followed sound (5 ms). */
  static final int BLOCK = 240;

  private AudioMixer() {}

  /**
   * @param durationMs length of the video
   * @param clips decoded clip for a cue's {@link SoundCue#clip()}, or null when unavailable
   * @return interleaved stereo samples at {@link #SAMPLE_RATE}, limited to [-1, 1]
   */
  public static float[] mix(
      List<SoundCue> cues, Function<String, AudioClip> clips, double durationMs) {
    int frames = (int) Math.ceil(durationMs / 1000.0 * SAMPLE_RATE);
    float[] out = new float[frames * 2];
    for (SoundCue cue : cues) {
      AudioClip clip = clips.apply(cue.clip());
      if (clip == null || clip.samples().length < 2) {
        continue;
      }
      mixCue(cue, clip, out, frames);
    }
    for (int i = 0; i < out.length; i++) {
      out[i] = Math.max(-1, Math.min(1, out[i]));
    }
    return out;
  }

  private static void mixCue(SoundCue cue, AudioClip clip, float[] out, int frames) {
    float[] src = clip.samples();
    int last = src.length - 1;
    int start = (int) Math.round(cue.timeMs() / 1000.0 * SAMPLE_RATE);
    int end =
        Double.isInfinite(cue.endMs())
            ? frames
            : (int) Math.min(frames, Math.round(cue.endMs() / 1000.0 * SAMPLE_RATE));
    double pos = 0;
    double[] g0 = gains(cue, start);
    for (int b = Math.max(0, start); b < end; b += BLOCK) {
      int blockEnd = Math.min(end, b + BLOCK);
      double[] g1 = gains(cue, blockEnd);
      // Pitch for the block from its start; gains ramp linearly to avoid clicks.
      double step = clip.sampleRate() * g0[2] / SAMPLE_RATE;
      if (b == Math.max(0, start)) {
        pos = (b - start) * step;
      }
      for (int f = b; f < blockEnd; f++) {
        if (pos >= last) {
          if (!cue.loop()) {
            return;
          }
          pos %= last;
        }
        int i = (int) pos;
        double t = pos - i;
        float s = (float) (src[i] + (src[i + 1] - src[i]) * t);
        double k = (f - b) / (double) (blockEnd - b);
        out[f * 2] += (float) (s * (g0[0] + (g1[0] - g0[0]) * k));
        out[f * 2 + 1] += (float) (s * (g0[1] + (g1[1] - g0[1]) * k));
        pos += step;
      }
      g0 = g1;
    }
  }

  /** Left gain, right gain and pitch of a cue at a sample. */
  private static double[] gains(SoundCue cue, int sample) {
    SoundCue.Point p = cue.at(sample * 1000.0 / SAMPLE_RATE);
    double gain = p.distanceGain(cue.positional());
    double pitch = Math.max(0.5, Math.min(2, p.pitch()));
    if (!cue.positional()) {
      return new double[] {gain, gain, pitch};
    }
    // Equal-power pan.
    double angle = (p.pan(true) + 1) * Math.PI / 4;
    return new double[] {
      gain * Math.cos(angle) * Math.sqrt(2), gain * Math.sin(angle) * Math.sqrt(2), pitch
    };
  }

  /** True when every sample is silent. */
  public static boolean isSilent(float[] samples) {
    for (float s : samples) {
      if (s != 0) {
        return false;
      }
    }
    return true;
  }
}
