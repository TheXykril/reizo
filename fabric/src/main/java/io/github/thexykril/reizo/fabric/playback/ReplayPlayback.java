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
  private static volatile ClientPacketListener playListener;
  private static volatile boolean enteredWorld;

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
            cameraProfile(mc),
            recordedPlayer(mc, meta),
            meta.localPlayerName != null ? meta.localPlayerName : mc.getUser().getName(),
            new PlaybackClock(System::nanoTime),
            () -> {
              ClientPacketListener listener = playListener;
              return listener == null ? null : listener.registryAccess();
            },
            ReplayPlayback::onServerClosed);
    current = server;
    playListener = null;
    enteredWorld = false;
    PacketTap.setSuppressed(true);
    server.start();

    ServerData data =
        new ServerData("Reizo replay: " + meta.name, "127.0.0.1", ServerData.Type.OTHER);
    LOGGER.info("Playing {} ({} ms)", archivePath.getFileName(), script.durationMs());
    ConnectScreen.startConnecting(
        new TitleScreen(), mc, new ServerAddress("127.0.0.1", server.port()), data, false, null);
  }

  /** Called when any connection enters the play phase; the replay needs its registries. */
  public static void onPlayInit(ClientPacketListener listener) {
    if (current != null) {
      playListener = listener;
    }
  }

  /**
   * Called when a play connection ends. Ends the replay when it was the replay's connection: the
   * client may keep the socket half-open for a while, so the server cannot rely on seeing it close.
   */
  public static void onPlayDisconnect(ClientPacketListener listener) {
    ReplayServer server = current;
    if (server != null && listener == playListener) {
      server.close();
    }
  }

  /**
   * Called every client tick. Ends the replay once the client has left the replay world, whichever
   * way it left (the disconnect events above do not always fire promptly for a socket connection).
   */
  public static void tick(Minecraft mc) {
    ReplayServer server = current;
    if (server == null) {
      return;
    }
    boolean inWorld = mc.level != null && mc.getConnection() != null;
    if (inWorld) {
      enteredWorld = true;
    } else if (enteredWorld) {
      server.close();
    }
  }

  /** Called when a configuration-phase connection ends: a replay that never reached play. */
  public static void onConfigurationDisconnect() {
    ReplayServer server = current;
    if (server != null && playListener == null) {
      server.close();
    }
  }

  /**
   * The client's player in a replay is a camera, not the recorded player, so it gets its own UUID
   * (the recorded player is a separate entity with the recorded UUID).
   */
  private static GameProfile cameraProfile(Minecraft mc) {
    UUID id = UUID.nameUUIDFromBytes(("reizo-camera:" + mc.getUser().getName()).getBytes());
    return new GameProfile(id, mc.getUser().getName());
  }

  private static UUID recordedPlayer(Minecraft mc, RecordingMetadata meta) {
    if (meta.localPlayerUuid != null) {
      try {
        return UUID.fromString(meta.localPlayerUuid);
      } catch (IllegalArgumentException e) {
        LOGGER.warn("Recording has an invalid player UUID; using the current player");
      }
    }
    return mc.getUser().getProfileId();
  }

  private static void onServerClosed() {
    current = null;
    playListener = null;
    PacketTap.setSuppressed(false);
    LOGGER.info("Replay stopped");
  }
}
