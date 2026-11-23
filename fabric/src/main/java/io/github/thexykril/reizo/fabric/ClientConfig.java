/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.thexykril.reizo.core.util.AtomicFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User settings, stored as JSON in the game's config directory. Unknown or missing fields fall back
 * to defaults, so old files keep working. A OneConfig screen will edit these same values when the
 * OneConfig integration lands (see docs/ROADMAP.md).
 */
public final class ClientConfig {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Config");
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  /** Start recording automatically when joining a world or server. */
  public boolean autoRecordOnJoin = false;

  /** Store the server address or world name in recordings. Off hides it when sharing files. */
  public boolean storeServerName = true;

  /** Show the recording indicator on the HUD. */
  public boolean showIndicator = true;

  /** In POV replays, draw the recorded player's hotbar, health, food, armor and experience. */
  public boolean showPlayerHud = true;

  /** In POV replays, draw the inventory and container screens the recorded player had open. */
  public boolean showPlayerScreens = true;

  /**
   * Modifier that must be held with the start/stop key: "none", "shift", "control" or "alt". The
   * key itself is set in Controls (default R, so Shift+R like Lunar).
   */
  public String toggleModifier = "shift";

  /**
   * Shadow recording: keep the last {@link #shadowSeconds} of play in memory, so it can be saved
   * after something happened without having recorded (like a replay buffer).
   */
  public boolean shadowEnabled = false;

  /** With OneConfig installed: show Reizo's messages as OneConfig notifications. */
  public boolean oneConfigNotifications = true;

  /** How much play a shadow save covers at least, in seconds (30 to 600). */
  public int shadowSeconds = 120;

  /** Memory the shadow buffer may use, in MB; the oldest play is dropped first. */
  public int shadowMemoryMb = 512;

  /** FFmpeg executable for video export; empty uses "ffmpeg" from the system PATH. */
  public String ffmpegPath = "";

  /** Recordings folder, relative to the game directory unless absolute. */
  public String recordingsDirectory = "reizo/recordings";

  public static ClientConfig load(Path file) {
    if (Files.isRegularFile(file)) {
      try {
        ClientConfig config = GSON.fromJson(Files.readString(file), ClientConfig.class);
        if (config != null) {
          return config;
        }
      } catch (IOException | JsonParseException e) {
        LOGGER.warn("Could not read {}, using defaults: {}", file, e.getMessage());
      }
    }
    ClientConfig defaults = new ClientConfig();
    defaults.save(file);
    return defaults;
  }

  public void save(Path file) {
    try {
      Files.createDirectories(file.getParent());
      AtomicFiles.writeString(file, GSON.toJson(this));
    } catch (IOException e) {
      LOGGER.warn("Could not save {}: {}", file, e.getMessage());
    }
  }
}
