/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.gametest;

import io.github.thexykril.reizo.core.format.Frame;
import io.github.thexykril.reizo.core.format.FrameStreamReader;
import io.github.thexykril.reizo.core.format.FrameType;
import io.github.thexykril.reizo.core.format.LocalPlayerState;
import io.github.thexykril.reizo.core.format.ProtocolPhase;
import io.github.thexykril.reizo.core.format.RecordingMetadata;
import io.github.thexykril.reizo.core.recording.RecordingSession;
import io.github.thexykril.reizo.core.storage.RecordingArchive;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.playback.ReplayServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * End-to-end recording tests in a real Minecraft client with an integrated server.
 *
 * <p>These verify the vertical slice up to "stop and save": packets are captured from the real
 * network pipeline, the file is finalized, and every recorded packet decodes again with Minecraft's
 * own codecs (byte-exact capture). Playback is not covered yet.
 */
public final class RecordingGameTest implements FabricClientGameTest {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/GameTest");

  @Override
  public void runTest(ClientGameTestContext context) {
    useCheapRendering(context);
    Path archive = recordFromJoin(context);
    playBack(context, archive);
    recordMidSessionWhileWalking(context);
  }

  /**
   * CI runners render in software (llvmpipe) on few cores. The client gametest runs the integrated
   * server in lockstep with client ticks, so expensive frames starve world loading until it times
   * out. The test checks recording, not visuals, so render as cheaply as possible.
   */
  private static void useCheapRendering(ClientGameTestContext context) {
    context.runOnClient(
        mc -> {
          Options options = mc.options;
          options.applyGraphicsPreset(GraphicsPreset.FAST);
          options.enableVsync().set(false);
          options.framerateLimit().set(10);
          options.renderDistance().set(2);
          options.simulationDistance().set(5);
          options.mipmapLevels().set(0);
          options.biomeBlendRadius().set(0);
          options.particles().set(ParticleStatus.MINIMAL);
        });
  }

  /** Auto-record on join: the recording must hold the complete connection, including chunks. */
  private Path recordFromJoin(ClientGameTestContext context) {
    context.runOnClient(mc -> RecorderClient.get().config().autoRecordOnJoin = true);
    RecordingSession session;
    RegistryAccess registries;
    try (TestSingleplayerContext world = context.worldBuilder().create()) {
      world.getClientWorld().waitForChunksDownload();
      context.waitTicks(20);
      session = context.computeOnClient(mc -> RecorderClient.get().session());
      check(session != null, "auto-record did not start a session");
      registries = context.computeOnClient(mc -> mc.getConnection().registryAccess());
      context.runOnClient(mc -> RecorderClient.get().addMarker("join test"));
      context.waitTicks(5);
    } finally {
      context.runOnClient(mc -> RecorderClient.get().config().autoRecordOnJoin = false);
    }
    // Leaving the world stops the recording; wait for the file.
    Path archive = await(context, session);
    Stats stats = verifyArchive(archive, registries);
    RecordingMetadata meta = metadata(archive);
    check(meta.completeFromJoin, "join recording should be complete");
    check(stats.configPackets > 0, "no configuration packets recorded");
    check(
        stats.packetNames.getOrDefault("ClientboundLoginPacket", 0) == 1,
        "expected exactly one login packet, got " + stats.packetNames);
    check(
        stats.packetNames.getOrDefault("ClientboundLevelChunkWithLightPacket", 0) > 0,
        "no chunks recorded");
    check(meta.markers.size() == 1, "marker missing");
    check(meta.state == RecordingMetadata.State.COMPLETE, "state " + meta.state);
    LOGGER.info("recordFromJoin OK: {} -> {}", archive.getFileName(), stats);
    return archive;
  }

  /**
   * Plays the join recording back: the client must enter the recorded world through the replay
   * server, receive every play packet, load the recorded chunks, and not record the replay.
   */
  private void playBack(ClientGameTestContext context, Path archive) {
    RecordingMetadata meta = metadata(archive);
    context.runOnClient(
        mc -> {
          try {
            ReplayPlayback.start(mc, archive);
          } catch (java.io.IOException e) {
            throw new AssertionError("could not start replay", e);
          }
        });
    context.waitFor(
        mc -> {
          ReplayServer replay = ReplayPlayback.current();
          return replay != null
              && replay.script().finished()
              && mc.level != null
              && mc.player != null
              && mc.level.getChunkSource().getLoadedChunksCount() > 0;
        },
        20 * 120);
    ReplayServer replay = context.computeOnClient(mc -> ReplayPlayback.current());
    int chunks = context.computeOnClient(mc -> mc.level.getChunkSource().getLoadedChunksCount());
    String cameraUuid = context.computeOnClient(mc -> mc.player.getUUID().toString());
    boolean cameraIsSpectator =
        context.computeOnClient(
            mc -> mc.player.isSpectator() && mc.gameMode.getPlayerMode() == GameType.SPECTATOR);
    // The recorded player is a separate entity with the recorded UUID, driven by the samples.
    context.waitFor(
        mc ->
            mc.level.getEntity(ReplayPlayback.current().view().recordedEntityId())
                instanceof net.minecraft.world.entity.player.Player,
        20 * 10);
    String recordedUuid =
        context.computeOnClient(
            mc ->
                mc.level
                    .getEntity(ReplayPlayback.current().view().recordedEntityId())
                    .getUUID()
                    .toString());
    check(
        replay.playPacketsSent() == replay.script().playPacketCount(),
        "sent " + replay.playPacketsSent() + " of " + replay.script().playPacketCount());
    check(!cameraUuid.equals(meta.localPlayerUuid), "camera has the recorded player's UUID");
    check(cameraIsSpectator, "camera is not a spectator");
    check(recordedUuid.equals(meta.localPlayerUuid), "recorded player entity is " + recordedUuid);
    check(
        context.computeOnClient(mc -> RecorderClient.get().session()) == null,
        "the replay was being recorded");
    context.waitTicks(20);
    check(
        context.computeOnClient(mc -> mc.level != null && ReplayPlayback.isActive()),
        "replay connection dropped");

    context.runOnClient(Minecraft::disconnectWithProgressScreen);
    LOGGER.info("playBack: left the replay");
    context.waitFor(mc -> mc.getConnection() == null, 20 * 30);
    LOGGER.info("playBack: client connection gone");
    context.waitFor(mc -> !ReplayPlayback.isActive(), 20 * 30);
    LOGGER.info("playBack OK: {} play packets, {} chunks loaded", replay.playPacketsSent(), chunks);
  }

  /** Manual start after joining, while the player walks: local player frames must track motion. */
  private void recordMidSessionWhileWalking(ClientGameTestContext context) {
    RecordingSession session;
    RegistryAccess registries;
    try (TestSingleplayerContext world = context.worldBuilder().create()) {
      world.getClientWorld().waitForChunksDownload();
      context.waitTicks(10);
      context.runOnClient(mc -> RecorderClient.get().startRecording());
      session = context.computeOnClient(mc -> RecorderClient.get().session());
      check(session != null, "manual start failed");
      registries = context.computeOnClient(mc -> mc.getConnection().registryAccess());
      context.getInput().holdKeyFor(options -> options.keyUp, 40);
      context.waitTicks(5);
      context.runOnClient(mc -> RecorderClient.get().stopRecording());
    }
    Path archive = await(context, session);
    Stats stats = verifyArchive(archive, registries);
    RecordingMetadata meta = metadata(archive);
    check(!meta.completeFromJoin, "mid-session recording must be flagged incomplete");
    check(
        stats.packetNames.getOrDefault("ClientboundLoginPacket", 0) == 1,
        "prelude must contain the login packet, got " + stats.packetNames);
    check(stats.localPlayerFrames >= 10, "too few local player frames: " + stats.localPlayerFrames);
    double moved = stats.lastPlayer.distanceSq(stats.firstPlayer);
    check(moved > 1.0, "player did not move in recording (dist^2=" + moved + ")");
    check(meta.durationMs > 1000, "duration too short: " + meta.durationMs);
    LOGGER.info("recordMidSessionWhileWalking OK: {} -> {}", archive.getFileName(), stats);
    playBackWalk(context, archive, stats);
  }

  /**
   * Plays the walking recording back: the recorded player must be shown (even though this
   * mid-session recording has no tab entry for it) and end where the recorded walk ended.
   */
  private void playBackWalk(ClientGameTestContext context, Path archive, Stats stats) {
    context.runOnClient(
        mc -> {
          try {
            ReplayPlayback.start(mc, archive);
          } catch (java.io.IOException e) {
            throw new AssertionError("could not start replay", e);
          }
        });
    context.waitFor(
        mc -> {
          ReplayServer replay = ReplayPlayback.current();
          return replay != null
              && replay.script().finished()
              && replay.view() != null
              && mc.level != null
              && mc.level.getEntity(replay.view().recordedEntityId())
                  instanceof net.minecraft.world.entity.player.Player;
        },
        20 * 120);
    context.waitTicks(10); // let position interpolation settle
    Pos shown =
        context.computeOnClient(
            mc -> {
              var e = mc.level.getEntity(ReplayPlayback.current().view().recordedEntityId());
              return new Pos(e.getX(), e.getY(), e.getZ());
            });
    double offSq = shown.distanceSq(stats.lastPlayer);
    check(offSq < 0.25, "recorded player at " + shown + ", walk ended at " + stats.lastPlayer);
    context.runOnClient(Minecraft::disconnectWithProgressScreen);
    context.waitFor(mc -> !ReplayPlayback.isActive(), 20 * 30);
    // Leaving a server ends on the multiplayer screen; tests must end on the title screen.
    context.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
    LOGGER.info("playBackWalk OK: recorded player shown at {}", shown);
  }

  // ---- verification helpers ----

  private static final class Stats {
    int configPackets;
    int playPackets;
    int localPlayerFrames;
    final Map<String, Integer> packetNames = new HashMap<>();
    Pos firstPlayer;
    Pos lastPlayer;

    @Override
    public String toString() {
      return "config="
          + configPackets
          + " play="
          + playPackets
          + " localPlayer="
          + localPlayerFrames
          + " types="
          + packetNames.size();
    }
  }

  private record Pos(double x, double y, double z) {
    double distanceSq(Pos o) {
      double dx = x - o.x;
      double dy = y - o.y;
      double dz = z - o.z;
      return dx * dx + dy * dy + dz * dz;
    }
  }

  /** Decodes every recorded packet with Minecraft's codecs; any leftover byte is a failure. */
  private static Stats verifyArchive(Path archive, RegistryAccess registries) {
    check(Files.isRegularFile(archive), "archive missing: " + archive);
    ProtocolInfo<?> config = ConfigurationProtocols.CLIENTBOUND;
    ProtocolInfo<?> play =
        GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries));
    Stats stats = new Stats();
    try (RecordingArchive a = RecordingArchive.open(archive);
        FrameStreamReader reader = a.openStream()) {
      List<Frame> frames = reader.readAll();
      for (Frame frame : frames) {
        FrameType type = frame.type();
        if (type == FrameType.PACKET) {
          ProtocolPhase phase = frame.packetPhase();
          Packet<?> packet = decode(phase == ProtocolPhase.PLAY ? play : config, frame);
          stats.packetNames.merge(packet.getClass().getSimpleName(), 1, Integer::sum);
          if (phase == ProtocolPhase.PLAY) {
            stats.playPackets++;
          } else {
            stats.configPackets++;
          }
        } else if (type == FrameType.LOCAL_PLAYER) {
          LocalPlayerState s = LocalPlayerState.decode(frame.payload());
          Pos p = new Pos(s.x(), s.y(), s.z());
          if (stats.firstPlayer == null) {
            stats.firstPlayer = p;
          }
          stats.lastPlayer = p;
          stats.localPlayerFrames++;
        }
      }
    } catch (Exception e) {
      throw new AssertionError("Archive verification failed for " + archive, e);
    }
    return stats;
  }

  private static Packet<?> decode(ProtocolInfo<?> protocol, Frame frame) {
    ByteBuf buf = Unpooled.wrappedBuffer(frame.packetBytes());
    try {
      Packet<?> packet = protocol.codec().decode(buf);
      check(
          buf.readableBytes() == 0,
          packet.getClass().getSimpleName() + " left " + buf.readableBytes() + " bytes");
      return packet;
    } finally {
      buf.release();
    }
  }

  /** Waits while letting the client tick; blocking the test thread alone would stall the game. */
  private static Path await(ClientGameTestContext context, RecordingSession session) {
    context.waitFor(mc -> session.result().isDone(), 20 * 60);
    try {
      return session.result().get(1, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new AssertionError("Recording did not finish", e);
    }
  }

  private static RecordingMetadata metadata(Path archive) {
    try (RecordingArchive a = RecordingArchive.open(archive)) {
      return a.metadata();
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }
}
