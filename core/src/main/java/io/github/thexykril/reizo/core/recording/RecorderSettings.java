/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.recording;

/**
 * Tuning for a recording session.
 *
 * @param blockBytes target size of one stream block; larger blocks compress and write more
 *     efficiently, smaller blocks lose less data on a crash
 * @param flushIntervalMs maximum time a frame stays in memory before it is written to disk; this
 *     bounds data lost on a crash
 * @param syncIntervalMs how often written data is forced to the storage device (fsync)
 * @param metadataIntervalMs how often {@code meta.json} is refreshed while recording
 * @param queueCapacity frames buffered between game threads and the writer thread
 * @param submitTimeoutMs how long a game thread may wait for queue space before the frame is
 *     dropped; keeps a stalled disk from freezing the game or network thread
 */
public record RecorderSettings(
    int blockBytes,
    int flushIntervalMs,
    int syncIntervalMs,
    int metadataIntervalMs,
    int queueCapacity,
    int submitTimeoutMs) {

  public static final RecorderSettings DEFAULTS =
      new RecorderSettings(256 * 1024, 1_000, 5_000, 5_000, 65_536, 50);

  public RecorderSettings {
    require(blockBytes >= 1024 && blockBytes <= 4 * 1024 * 1024, "blockBytes");
    require(flushIntervalMs >= 10, "flushIntervalMs");
    require(syncIntervalMs >= flushIntervalMs, "syncIntervalMs");
    require(metadataIntervalMs >= 100, "metadataIntervalMs");
    require(queueCapacity >= 16, "queueCapacity");
    require(submitTimeoutMs >= 0, "submitTimeoutMs");
  }

  private static void require(boolean ok, String name) {
    if (!ok) {
      throw new IllegalArgumentException("Invalid recorder setting: " + name);
    }
  }
}
