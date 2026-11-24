/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.audio;

/**
 * Imported music placed on the video (ADR 0018).
 *
 * @param path the audio file on disk
 * @param startMs where it starts in the video
 * @param trimMs where in the file it starts playing
 * @param lengthMs how long it plays in the video; 0 or less plays to the end of the file
 * @param volume 0 (silent) to 2 (double)
 * @param fadeInMs fade-in length from silence
 * @param fadeOutMs fade-out length to silence at its end
 */
public record MusicItem(
    String path,
    long startMs,
    long trimMs,
    long lengthMs,
    float volume,
    long fadeInMs,
    long fadeOutMs) {
  public MusicItem {
    startMs = Math.max(0, startMs);
    trimMs = Math.max(0, trimMs);
    volume = Float.isNaN(volume) ? 1 : Math.max(0, Math.min(2, volume));
    fadeInMs = Math.max(0, fadeInMs);
    fadeOutMs = Math.max(0, fadeOutMs);
  }

  public static MusicItem of(String path, long startMs) {
    return new MusicItem(path, startMs, 0, 0, 1, 0, 0);
  }

  public MusicItem withStart(long ms) {
    return new MusicItem(path, ms, trimMs, lengthMs, volume, fadeInMs, fadeOutMs);
  }

  public MusicItem withVolume(float v) {
    return new MusicItem(path, startMs, trimMs, lengthMs, v, fadeInMs, fadeOutMs);
  }

  public MusicItem withFades(long in, long out) {
    return new MusicItem(path, startMs, trimMs, lengthMs, volume, in, out);
  }

  public MusicItem withTrim(long trim, long length) {
    return new MusicItem(path, startMs, trim, length, volume, fadeInMs, fadeOutMs);
  }

  /** Length in the video, given the file's length; never negative. */
  public long playedMs(long fileMs) {
    long rest = Math.max(0, fileMs - trimMs);
    return lengthMs > 0 ? Math.min(lengthMs, rest) : rest;
  }
}
