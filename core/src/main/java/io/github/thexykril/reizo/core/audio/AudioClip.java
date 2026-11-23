/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.audio;

/** A decoded sound: mono samples in [-1, 1] at a sample rate. */
public record AudioClip(float[] samples, int sampleRate) {
  /** Mixes interleaved samples with {@code channels} channels down to mono. */
  public static AudioClip fromInterleaved(float[] interleaved, int channels, int sampleRate) {
    if (channels <= 1) {
      return new AudioClip(interleaved, sampleRate);
    }
    float[] mono = new float[interleaved.length / channels];
    for (int i = 0; i < mono.length; i++) {
      float sum = 0;
      for (int c = 0; c < channels; c++) {
        sum += interleaved[i * channels + c];
      }
      mono[i] = sum / channels;
    }
    return new AudioClip(mono, sampleRate);
  }

  public double durationSeconds() {
    return samples.length / (double) sampleRate;
  }
}
