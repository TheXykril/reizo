/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class MusicMixTest {
  private static final int RATE = AudioMixer.SAMPLE_RATE;

  /** One second of constant 0.5 on both channels. */
  private static float[] tone() {
    float[] s = new float[RATE * 2];
    java.util.Arrays.fill(s, 0.5f);
    return s;
  }

  @Test
  void placesTrimsAndScales() {
    float[] mix = new float[RATE * 2 * 2]; // two seconds
    MusicItem item = new MusicItem("a", 500, 250, 0, 0.5f, 0, 0);
    assertEquals(1, MusicMix.addTo(mix, List.of(item), p -> tone()));
    int start = RATE / 2;
    assertEquals(0, mix[(start - 1) * 2], "silent before the start");
    assertEquals(0.25f, mix[start * 2], 1e-6, "volume applied");
    // The file is 1 s, trimmed by 0.25 s: it plays 0.75 s.
    int end = start + RATE * 3 / 4;
    assertEquals(0.25f, mix[(end - 1) * 2], 1e-6);
    assertEquals(0, mix[end * 2], "silent after the file runs out");
  }

  @Test
  void fadesAndLimits() {
    float[] mix = new float[RATE * 2];
    java.util.Arrays.fill(mix, 0.8f);
    MusicItem item = new MusicItem("a", 0, 0, 0, 1, 500, 500);
    MusicMix.addTo(mix, List.of(item), p -> tone());
    assertEquals(0.8f, mix[0], 1e-6, "fade-in starts at silence");
    assertEquals(1f, mix[(RATE / 2) * 2], 1e-6, "full volume after the fade, limited to 1");
    assertEquals(0.8f, mix[(RATE - 1) * 2], 1e-3, "fade-out ends at silence");
  }

  @Test
  void skipsUnreadableFilesAndLimitsLength() {
    float[] mix = new float[RATE * 2];
    MusicItem missing = MusicItem.of("missing", 0);
    MusicItem shortOne = new MusicItem("a", 0, 0, 100, 1, 0, 0);
    assertEquals(
        1, MusicMix.addTo(mix, List.of(missing, shortOne), p -> p.equals("a") ? tone() : null));
    assertEquals(0.5f, mix[(RATE / 10 - 1) * 2], 1e-6);
    assertEquals(0, mix[(RATE / 10) * 2], "plays only its length");
  }

  @Test
  void peaksFollowTheLoudestSample() {
    float[] s = new float[8];
    s[2] = -0.9f;
    s[7] = 0.3f;
    float[] p = MusicMix.peaks(s, 2);
    assertEquals(0.9f, p[0], 1e-6);
    assertEquals(0.3f, p[1], 1e-6);
  }
}
