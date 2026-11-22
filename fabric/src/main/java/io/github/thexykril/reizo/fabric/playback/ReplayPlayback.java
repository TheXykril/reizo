/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.playback;

import com.mojang.authlib.GameProfile;
import io.github.thexykril.reizo.core.format.Frame;
import io.github.thexykril.reizo.core.format.FrameStreamReader;
import io.github.thexykril.reizo.core.format.RecordingMetadata;
import io.github.thexykril.reizo.core.playback.PlaybackClock;
import io.github.thexykril.reizo.core.playback.ReplayScript;
import io.github.thexykril.reizo.core.storage.RecordingArchive;
import io.github.thexykril.reizo.fabric.capture.PacketTap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts and tracks the replay that is currently playing. At most one replay runs at a time. While
 * one runs, packet capture is suppressed so a replay is never recorded into a new recording.
 */
public final class ReplayPlayback {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Replay");

  private static volatile ReplayServer current;

  private ReplayPlayback() {}

  /** The running replay, or null. */
  public static ReplayServer current() {
    return current;
  }

  public static boolean isActive() {
    return current != null;
  }

  /**
   * Loads a recording and connects the client to it. Leaves the current world first. Client thread
   * only.
   *
   * @throws IOException if the archive cannot be read
   * @throws IllegalArgumentException if the recording cannot be played on this game version
   */
  public static void start(Minecraft mc, Path archivePath) throws IOException {
    if (current != null) {
      throw new IllegalStateException("A replay is already playing");
    }
    RecordingMetadata meta;
    List<Frame> frames;
    try (RecordingArchive archive = RecordingArchive.open(archivePath);
        FrameStreamReader reader = archive.openStream()) {
      meta = archive.metadata();
      frames = reader.readAll();
    }
    int protocol = SharedConstants.getProtocolVersion();
    if (meta.protocolVersion != protocol) {
      throw new IllegalArgumentException(
          "Recorded with Minecraft "
              + meta.minecraftVersion
              + " (protocol "
              + meta.protocolVersion
              + "); this game uses protocol "
              + protocol);
    }
    ReplayScript script = ReplayScript.of(frames);

    if (mc.level != null || mc.getConnection() != null) {
      mc.disconnectWithProgressScreen();
    }

    ReplayServer server =
        new ReplayServer(
            script,
            profile(mc, meta),
            new PlaybackClock(System::nanoTime),
            () -> {
              ClientPacketListener connection = Minecraft.getInstance().getConnection();
              return connection == null ? null : connection.registryAccess();
            },
            ReplayPlayback::onServerClosed);
    current = server;
    PacketTap.setSuppressed(true);
    server.start();

    ServerData data =
        new ServerData("Reizo replay: " + meta.name, "127.0.0.1", ServerData.Type.OTHER);
    LOGGER.info("Playing {} ({} ms)", archivePath.getFileName(), script.durationMs());
    ConnectScreen.startConnecting(
        new TitleScreen(), mc, new ServerAddress("127.0.0.1", server.port()), data, false, null);
  }

  private static GameProfile profile(Minecraft mc, RecordingMetadata meta) {
    if (meta.localPlayerUuid != null && meta.localPlayerName != null) {
      try {
        return new GameProfile(UUID.fromString(meta.localPlayerUuid), meta.localPlayerName);
      } catch (IllegalArgumentException e) {
        LOGGER.warn("Recording has an invalid player UUID; using the current player");
      }
    }
    return new GameProfile(mc.getUser().getProfileId(), mc.getUser().getName());
  }

  private static void onServerClosed() {
    current = null;
    PacketTap.setSuppressed(false);
    LOGGER.info("Replay stopped");
  }
}
