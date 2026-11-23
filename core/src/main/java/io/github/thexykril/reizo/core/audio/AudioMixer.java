/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.audio;

import java.util.List;
import java.util.function.Function;

/**
 * Renders an export's sound track offline from the sounds that started during it: each clip is
 * resampled for its pitch, attenuated by distance, panned by direction and summed. The result is
 * exact to the sample, independent of how fast the export ran.
 */
public final class AudioMixer {
  public static final int SAMPLE_RATE = 48_000;

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
      double gain = cue.distanceGain();
      if (clip == null || clip.samples().length == 0 || gain <= 0) {
        continue;
      }
      // Equal-power pan.
      double angle = (cue.pan() + 1) * Math.PI / 4;
      float left = (float) (gain * Math.cos(angle) * Math.sqrt(2));
      float right = (float) (gain * Math.sin(angle) * Math.sqrt(2));
      if (!cue.positional()) {
        left = (float) gain;
        right = (float) gain;
      }
      double step = clip.sampleRate() * (double) cue.pitch() / SAMPLE_RATE;
      int start = (int) Math.round(cue.timeMs() / 1000.0 * SAMPLE_RATE);
      float[] src = clip.samples();
      double pos = 0;
      for (int f = Math.max(0, start); f < frames; f++) {
        pos = (f - start) * step;
        int i = (int) pos;
        if (i >= src.length - 1) {
          break;
        }
        double t = pos - i;
        float s = (float) (src[i] + (src[i + 1] - src[i]) * t);
        out[f * 2] += s * left;
        out[f * 2 + 1] += s * right;
      }
    }
    for (int i = 0; i < out.length; i++) {
      out[i] = Math.max(-1, Math.min(1, out[i]));
    }
    return out;
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
