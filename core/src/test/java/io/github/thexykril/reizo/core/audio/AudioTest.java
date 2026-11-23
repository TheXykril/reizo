/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thexykril.reizo.core.export.FfmpegCommand;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AudioTest {
  @TempDir Path dir;

  private static SoundCue cue(double t, boolean positional, double x, double z, float yaw) {
    return new SoundCue(t, "c", 1, 1, positional, x, 64, z, 16, 0, 64, 0, yaw);
  }

  private static AudioClip ones(int n, int rate) {
    float[] s = new float[n];
    java.util.Arrays.fill(s, 0.5f);
    return new AudioClip(s, rate);
  }

  @Test
  void downmixesToMono() {
    AudioClip c = AudioClip.fromInterleaved(new float[] {1, 0, 0.5f, 0.5f}, 2, 44_100);
    assertArrayEquals(new float[] {0.5f, 0.5f}, c.samples());
  }

  @Test
  void distanceAndDirection() {
    // Yaw 0 looks to +Z, so +X is on the left and -X on the right.
    assertEquals(-1, cue(0, true, 4, 0, 0).pan(), 1e-9);
    assertEquals(1, cue(0, true, -4, 0, 0).pan(), 1e-9);
    assertEquals(0, cue(0, true, 0, 4, 0).pan(), 1e-9, "straight ahead");
    assertEquals(0.75, cue(0, true, 0, 4, 0).distanceGain(), 1e-9);
    assertEquals(0, cue(0, true, 0, 40, 0).distanceGain(), 1e-9, "out of range");
    assertEquals(1, cue(0, false, 0, 400, 0).distanceGain(), 1e-9);
    assertEquals(0, cue(0, false, 4, 0, 0).pan(), 1e-9);
  }

  @Test
  void mixPlacesClipsAtTheirTimeWithPitchAndPan() {
    Map<String, AudioClip> clips = Map.of("c", ones(4800, 48_000)); // 0.1 s
    // A non-positional cue at 500 ms.
    float[] out = AudioMixer.mix(List.of(cue(500, false, 0, 0, 0)), clips::get, 1000);
    assertEquals(48_000 * 2, out.length);
    assertEquals(0, out[2 * 23_999], 1e-9, "silent before");
    assertEquals(0.5, out[2 * 24_000], 1e-6);
    assertEquals(0.5, out[2 * 24_000 + 1], 1e-6);
    assertEquals(0, out[2 * 29_000], 1e-9, "ended after 0.1 s");
    // Pitch 2 plays it twice as fast.
    SoundCue fast = new SoundCue(0, "c", 1, 2, false, 0, 0, 0, 16, 0, 0, 0, 0);
    float[] f = AudioMixer.mix(List.of(fast), clips::get, 1000);
    assertTrue(f[2 * 2000] != 0 && f[2 * 2500] == 0, "ends after 0.05 s");
    // Fully left: the right channel is silent.
    float[] l = AudioMixer.mix(List.of(cue(0, true, 1, 0, 0)), clips::get, 100);
    assertTrue(l[200] > 0 && Math.abs(l[201]) < 1e-6, l[200] + " " + l[201]);
    // Unknown clip and sounds out of range add nothing; loud sums are limited.
    assertTrue(
        AudioMixer.isSilent(AudioMixer.mix(List.of(cue(0, true, 0, 99, 0)), clips::get, 100)));
    float[] loud =
        AudioMixer.mix(java.util.Collections.nCopies(5, cue(0, false, 0, 0, 0)), clips::get, 100);
    assertEquals(1, loud[0], 1e-6);
    assertFalse(AudioMixer.isSilent(loud));
  }

  @Test
  void writesWav() throws Exception {
    Path wav = dir.resolve("a.wav");
    WavWriter.write(wav, new float[] {0, 1, -1, 0.5f}, 2, 48_000);
    ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(wav)).order(ByteOrder.LITTLE_ENDIAN);
    assertEquals(44 + 8, b.capacity());
    assertEquals(48_000, b.getInt(24));
    assertEquals(32767, b.getShort(46));
    assertEquals(-32767, b.getShort(48));
  }

  @Test
  void muxCommandPicksTheAudioCodecByContainer() {
    String mp4 =
        String.join(
            " ",
            FfmpegCommand.muxAudio("ffmpeg", Path.of("v.mp4"), Path.of("a.wav"), Path.of("o.mp4")));
    assertTrue(mp4.contains("-c:v copy -c:a aac"), mp4);
    assertTrue(mp4.endsWith("-shortest -movflags +faststart o.mp4"), mp4);
    String webm =
        String.join(
            " ",
            FfmpegCommand.muxAudio(
                "ffmpeg", Path.of("v.webm"), Path.of("a.wav"), Path.of("o.webm")));
    assertTrue(webm.contains("-c:a libopus"), webm);
  }
}
