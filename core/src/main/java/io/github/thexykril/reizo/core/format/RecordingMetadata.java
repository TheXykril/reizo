/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.format;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Descriptive data stored as {@code meta.json} next to the frame stream. Mutable on purpose: the
 * recorder updates it while recording and rewrites it atomically.
 *
 * <p>Fields are public for Gson; unknown JSON fields are ignored, so adding a field is backward
 * compatible. Bump {@link #CURRENT_SCHEMA} only for incompatible changes.
 */
public final class RecordingMetadata {
  public static final int CURRENT_SCHEMA = 1;

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

  /** Lifecycle of the recording as last written to disk. */
  public enum State {
    /** Being written. If found on startup without a live owner, the client crashed. */
    RECORDING,
    /** Stopped cleanly and packaged. */
    COMPLETE,
    /** Salvaged after a crash; the tail after the last valid block was lost. */
    RECOVERED
  }

  public int schema = CURRENT_SCHEMA;
  public int streamVersion = StreamFormat.VERSION;
  public String id;
  public String name;
  public State state = State.RECORDING;

  /** Wall-clock start, epoch milliseconds. */
  public long startedAtEpochMs;

  /** Recording time (excludes pauses), updated periodically and on stop. */
  public int durationMs;

  public long frameCount;

  public String minecraftVersion;
  public int protocolVersion;
  public String modVersion;
  public String loader;

  /** "singleplayer", "multiplayer" or "realms". */
  public String serverType;

  /** Server address for multiplayer, world name for singleplayer. May be null for privacy. */
  public String serverName;

  public String localPlayerUuid;
  public String localPlayerName;

  /**
   * True if recording started at the beginning of a connection, so the stream holds the complete
   * world state. False if it started mid-session; then chunks and entities loaded before the start
   * are missing until world snapshots are implemented (see docs/FEATURES.md).
   */
  public boolean completeFromJoin;

  public List<Marker> markers = new ArrayList<>();

  /** Free-form diagnostics, e.g. counts of packets that failed to be captured. */
  public long droppedFrames;

  /** A named point in the recording. */
  public static final class Marker {
    public int timeMs;
    public String label;

    public Marker() {}

    public Marker(int timeMs, String label) {
      this.timeMs = timeMs;
      this.label = label;
    }
  }

  public static RecordingMetadata create(String name, long startedAtEpochMs) {
    RecordingMetadata meta = new RecordingMetadata();
    meta.id = UUID.randomUUID().toString();
    meta.name = Objects.requireNonNull(name, "name");
    meta.startedAtEpochMs = startedAtEpochMs;
    return meta;
  }

  public RecordingMetadata copy() {
    return fromJson(toJson());
  }

  public String toJson() {
    return GSON.toJson(this);
  }

  public static RecordingMetadata fromJson(String json) {
    RecordingMetadata meta;
    try {
      meta = GSON.fromJson(json, RecordingMetadata.class);
    } catch (JsonParseException e) {
      throw new IllegalArgumentException("Invalid recording metadata: " + e.getMessage(), e);
    }
    if (meta == null || meta.id == null) {
      throw new IllegalArgumentException("Recording metadata has no id");
    }
    if (meta.schema > CURRENT_SCHEMA) {
      throw new IllegalArgumentException(
          "Recording metadata schema "
              + meta.schema
              + " is newer than supported "
              + CURRENT_SCHEMA);
    }
    if (meta.markers == null) {
      meta.markers = new ArrayList<>();
    }
    return meta;
  }
}
