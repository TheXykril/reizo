/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.gametest;

import io.github.thexykril.reizo.core.camera.CameraKeyframe;
import io.github.thexykril.reizo.core.effects.Grade;
import io.github.thexykril.reizo.core.effects.PropertyTracks;
import io.github.thexykril.reizo.core.export.ExportFormat;
import io.github.thexykril.reizo.core.format.Frame;
import io.github.thexykril.reizo.core.format.FrameStreamReader;
import io.github.thexykril.reizo.core.format.FrameType;
import io.github.thexykril.reizo.core.format.LocalPlayerState;
import io.github.thexykril.reizo.core.format.ProtocolPhase;
import io.github.thexykril.reizo.core.format.RecordingMetadata;
import io.github.thexykril.reizo.core.recording.RecordingSession;
import io.github.thexykril.reizo.core.storage.RecordingArchive;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.export.ExportControls;
import io.github.thexykril.reizo.fabric.export.ExportSession;
import io.github.thexykril.reizo.fabric.playback.EffectRenderer;
import io.github.thexykril.reizo.fabric.playback.PathVisualizer;
import io.github.thexykril.reizo.fabric.playback.ReplayCamera;
import io.github.thexykril.reizo.fabric.playback.ReplayEditor;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.playback.ReplayServer;
import io.github.thexykril.reizo.fabric.ui.ExportSettingsScreen;
import io.github.thexykril.reizo.fabric.ui.ReplayBrowserScreen;
import io.github.thexykril.reizo.fabric.ui.TimelineScreen;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
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
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * End-to-end recording tests in a real Minecraft client with an integrated server.
 *
 * <p>These verify the vertical slice up to "stop and save": packets are captured from the real
 * network pipeline, the file is finalized, and every recorded packet decodes again with Minecraft's
 * own codecs (byte-exact capture). The recordings are then played back through the replay server,
 * including the camera, pause and speed keys.
 */
public final class RecordingGameTest implements FabricClientGameTest {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/GameTest");

  @Override
  public void runTest(ClientGameTestContext context) {
    useCheapRendering(context);
    Path archive = recordFromJoin(context);
    playBack(context, archive);
    recordOnDedicatedServer(context);
    recordMidSessionWhileWalking(context);
    saveShadowAfterWalking(context);
    browseAndPlay(context);
  }

  /**
   * Shadow recording: with it on, walking is kept in memory without recording, and the pause menu's
   * "Save last" button writes it as a recording that starts with a world snapshot.
   */
  private void saveShadowAfterWalking(ClientGameTestContext context) {
    context.runOnClient(
        mc -> {
          RecorderClient.get().config().shadowEnabled = true;
          RecorderClient.get().config().shadowSeconds = 30;
          RecorderClient.get().applyShadowSettings();
        });
    java.util.concurrent.CompletableFuture<Path> saved;
    RegistryAccess registries;
    try (TestSingleplayerContext world = context.worldBuilder().create()) {
      world.getClientWorld().waitForChunksDownload();
      context.waitFor(mc -> !RecorderClient.get().shadow().isEmpty(), 20 * 5);
      long snapshotBytes = context.computeOnClient(mc -> RecorderClient.get().shadow().bytes());
      context.getInput().holdKeyFor(options -> options.keyUp, 40);
      context.waitTicks(5);
      check(
          context.computeOnClient(mc -> RecorderClient.get().session()) == null,
          "shadow must not start a recording");
      registries = context.computeOnClient(mc -> mc.getConnection().registryAccess());
      context.runOnClient(
          mc -> mc.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true)));
      context.waitTicks(2);
      saved =
          context.computeOnClient(
              mc -> {
                var button =
                    net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(mc.screen).stream()
                        .filter(
                            b ->
                                b.getMessage().getContents()
                                        instanceof
                                        net.minecraft.network.chat.contents.TranslatableContents t
                                    && t.getKey().equals("reizo.menu.save_shadow"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("no shadow save button"));
                check(button.active, "shadow save button is inactive");
                // The button saves and closes the menu; call the same method to get its result.
                mc.setScreen(null);
                return RecorderClient.get().saveShadow();
              });
      LOGGER.info("Shadow buffer after join: {} KB", snapshotBytes / 1024);
    }
    context.waitFor(mc -> saved.isDone(), 20 * 30);
    Path archive = saved.join();
    check(archive != null, "shadow save wrote nothing");
    Stats stats = verifyArchive(archive, registries);
    RecordingMetadata meta = metadata(archive);
    check(meta.startSnapshot && !meta.completeFromJoin, "shadow save must start with a snapshot");
    check(
        stats.packetNames.getOrDefault("ClientboundLevelChunkWithLightPacket", 0) > 0,
        "shadow snapshot has no chunks: " + stats.packetNames);
    check(stats.localPlayerFrames >= 10, "too few local player frames: " + stats.localPlayerFrames);
    check(
        stats.lastPlayer.distanceSq(stats.firstPlayer) > 1.0, "player did not move in shadow save");
    check(meta.durationMs >= 2000, "shadow save too short: " + meta.durationMs);
    context.runOnClient(
        mc -> {
          RecorderClient.get().config().shadowEnabled = false;
          RecorderClient.get().applyShadowSettings();
          check(RecorderClient.get().shadow() == null, "shadow not turned off");
        });
    LOGGER.info(
        "saveShadowAfterWalking OK: {} -> {} ({} ms)",
        archive.getFileName(),
        stats,
        meta.durationMs);
  }

  /**
   * Opens the replay browser from the title screen button, checks it lists the recordings made
   * above, and plays the newest one with the Play button.
   */
  private void browseAndPlay(ClientGameTestContext context) {
    context.waitFor(
        mc -> mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen, 20 * 10);
    context.runOnClient(
        mc -> {
          pressButton(mc, "reizo.menu.replays");
        });
    context.waitFor(mc -> mc.screen instanceof ReplayBrowserScreen, 20 * 5);
    context.waitTicks(2);
    context.takeScreenshot("reizo-replay-browser");
    int listed = context.computeOnClient(mc -> ((ReplayBrowserScreen) mc.screen).entries().size());
    check(listed >= 2, "browser lists " + listed + " recordings, expected at least 2");
    context.runOnClient(
        mc -> {
          ReplayBrowserScreen browser = (ReplayBrowserScreen) mc.screen;
          browser.select(0);
          browser.pressPlay();
        });
    context.waitFor(
        mc ->
            ReplayPlayback.isActive()
                && ReplayPlayback.current().script().finished()
                && mc.level != null
                && mc.player != null,
        20 * 60);
    context.waitTicks(10);
    context.takeScreenshot("reizo-replay-playing");
    exportVideo(context);
    context.runOnClient(Minecraft::disconnectWithProgressScreen);
    context.waitFor(mc -> !ReplayPlayback.isActive(), 20 * 30);
    context.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
    LOGGER.info("browseAndPlay OK: {} recordings listed", listed);
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

  /**
   * Multiplayer (#4): auto-record on joining a real dedicated server over the network (with its
   * packet compression), see an entity the server spawns during the recording, and play it back.
   */
  private void recordOnDedicatedServer(ClientGameTestContext context) {
    context.runOnClient(mc -> RecorderClient.get().config().autoRecordOnJoin = true);
    RecordingSession session;
    RegistryAccess registries;
    try (TestDedicatedServerContext server = context.worldBuilder().createServer();
        TestServerConnection connection = server.connect()) {
      connection.getClientWorld().waitForChunksDownload();
      context.waitTicks(10);
      session = context.computeOnClient(mc -> RecorderClient.get().session());
      check(session != null, "auto-record did not start on a dedicated server");
      registries = context.computeOnClient(mc -> mc.getConnection().registryAccess());
      server.runOnServer(
          s -> {
            var player = s.getPlayerList().getPlayers().get(0);
            var pig =
                net.minecraft.world.entity.EntityType.PIG.create(
                    player.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            pig.setPos(player.getX() + 2, player.getY(), player.getZ());
            pig.setNoAi(true);
            pig.setCustomName(net.minecraft.network.chat.Component.literal("reizo-pig"));
            player.level().addFreshEntity(pig);
          });
      context.waitFor(mc -> findPig(mc) != null, 20 * 10);
      context.waitTicks(20);
    } finally {
      context.runOnClient(mc -> RecorderClient.get().config().autoRecordOnJoin = false);
    }
    Path archive = await(context, session);
    Stats stats = verifyArchive(archive, registries);
    RecordingMetadata meta = metadata(archive);
    check("multiplayer".equals(meta.serverType), "server type " + meta.serverType);
    check(meta.completeFromJoin, "server join recording should be complete");
    check(
        stats.packetNames.getOrDefault("ClientboundLevelChunkWithLightPacket", 0) > 0,
        "no chunks recorded from the server");
    check(
        stats.packetNames.getOrDefault("ClientboundAddEntityPacket", 0) > 0,
        "no entities recorded from the server: " + stats.packetNames.keySet());
    check(stats.localPlayerFrames > 0, "no local player frames on the server");

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
              && mc.screen == null
              && findPig(mc) != null;
        },
        20 * 60);
    context.runOnClient(Minecraft::disconnectWithProgressScreen);
    context.waitFor(mc -> !ReplayPlayback.isActive(), 20 * 30);
    context.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
    LOGGER.info("recordOnDedicatedServer OK: {} -> {}", archive.getFileName(), stats);
  }

  private static net.minecraft.world.entity.Entity findPig(Minecraft mc) {
    if (mc.level == null) {
      return null;
    }
    for (var e : mc.level.entitiesForRendering()) {
      if (e.getType() == net.minecraft.world.entity.EntityType.PIG
          && e.getCustomName() != null
          && "reizo-pig".equals(e.getCustomName().getString())) {
        return e;
      }
    }
    return null;
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
    checkReplayControls(context);

    context.runOnClient(Minecraft::disconnectWithProgressScreen);
    LOGGER.info("playBack: left the replay");
    context.waitFor(mc -> mc.getConnection() == null, 20 * 30);
    LOGGER.info("playBack: client connection gone");
    context.waitFor(mc -> !ReplayPlayback.isActive(), 20 * 30);
    context.waitTicks(2);
    check(
        context.computeOnClient(
            mc ->
                mc.options.gamma().get() == gammaBefore
                    && mc.options.entityShadows().get() == shadowsBefore),
        "leaving the replay did not put the keyframed settings back");
    LOGGER.info("playBack OK: {} play packets, {} chunks loaded", replay.playPacketsSent(), chunks);
  }

  /**
   * Drives the replay keys with their default bindings: the camera key cycles free, player view and
   * follow; the pause key freezes the client's world; the speed keys change its tick rate.
   */
  private void checkReplayControls(ClientGameTestContext context) {
    int recordedId =
        context.computeOnClient(mc -> ReplayPlayback.current().view().recordedEntityId());

    context.getInput().pressKey(GLFW.GLFW_KEY_V);
    context.waitTicks(2);
    check(ReplayCamera.mode() == ReplayCamera.Mode.POV, "camera mode " + ReplayCamera.mode());
    check(
        context.computeOnClient(
            mc ->
                mc.getCameraEntity().getId() == recordedId
                    && mc.options.getCameraType() == CameraType.FIRST_PERSON),
        "player view does not look through the recorded player");

    context.getInput().pressKey(GLFW.GLFW_KEY_V);
    context.waitTicks(2);
    check(ReplayCamera.mode() == ReplayCamera.Mode.FOLLOW, "camera mode " + ReplayCamera.mode());
    check(
        context.computeOnClient(
            mc ->
                mc.getCameraEntity().getId() == recordedId
                    && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK),
        "follow camera is not behind the recorded player");

    context.getInput().pressKey(GLFW.GLFW_KEY_V);
    context.waitTicks(2);
    check(ReplayCamera.mode() == ReplayCamera.Mode.FREE, "camera mode " + ReplayCamera.mode());
    check(
        context.computeOnClient(
            mc ->
                mc.getCameraEntity() == mc.player
                    && mc.options.getCameraType() == CameraType.FIRST_PERSON),
        "free camera is not the spectator camera");

    context.getInput().pressKey(GLFW.GLFW_KEY_K);
    context.waitFor(mc -> mc.level.tickRateManager().isFrozen(), 20 * 5);
    check(!ReplayPlayback.current().clock().isRunning(), "replay clock still running");

    context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_BRACKET);
    context.waitFor(mc -> mc.level.tickRateManager().tickrate() == 40f, 20 * 5);
    context.getInput().pressKey(GLFW.GLFW_KEY_K);
    context.waitFor(mc -> !mc.level.tickRateManager().isFrozen(), 20 * 5);
    check(ReplayPlayback.current().clock().speed() == 2.0, "speed did not step to 2x");

    context.getInput().pressKey(GLFW.GLFW_KEY_LEFT_BRACKET);
    context.waitFor(mc -> mc.level.tickRateManager().tickrate() == 20f, 20 * 5);

    // The replay has finished; the back key restarts it through a reconfiguration.
    Object levelBefore = context.computeOnClient(mc -> mc.level);
    context.getInput().pressKey(GLFW.GLFW_KEY_LEFT);
    context.waitFor(
        mc -> {
          ReplayServer replay = ReplayPlayback.current();
          return replay != null
              && replay.seeksDone() == 1
              && !replay.isReconfiguring()
              && mc.level != null
              && mc.level != levelBefore
              && mc.screen == null
              && mc.level.getChunkSource().getLoadedChunksCount() > 0;
        },
        20 * 30);
    check(
        context.computeOnClient(mc -> mc.player.isSpectator()),
        "camera is not a spectator after a backwards seek");
    context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
    context.waitFor(mc -> ReplayPlayback.current().seeksDone() == 2, 20 * 5);
    context.waitFor(mc -> ReplayPlayback.current().script().finished(), 20 * 10);
    checkCameraPath(context);
    checkTimeline(context);
    checkFreeCamera(context);
    checkEffects(context);
    checkKeyframedSettings(context);
    LOGGER.info("checkReplayControls OK");
  }

  /**
   * Effects: the timeline's Effects button opens the panel; a keyframe with no saturation, full
   * vignette and a 2.39 letterbox turns the picture gray with black bars, and is saved in the
   * project.
   */
  private void checkEffects(ClientGameTestContext context) {
    context.runOnClient(
        mc -> mc.setScreen(new io.github.thexykril.reizo.fabric.ui.TimelineScreen()));
    context.waitTicks(2);
    context.runOnClient(mc -> pressButton(mc, "reizo.timeline.effects"));
    context.waitTicks(2);
    check(
        context.computeOnClient(
            mc -> mc.screen instanceof io.github.thexykril.reizo.fabric.ui.EffectsScreen),
        "Effects button did not open the effects panel");
    check(
        context.computeOnClient(mc -> EffectRenderer.lastApplied() == null),
        "effects pass ran without effects");
    context.runOnClient(
        mc -> {
          ReplayEditor.recordUndo();
          ReplayEditor.effects()
              .put(0, Grade.NEUTRAL.withSaturation(0).withVignette(1).withLetterbox(2.39f));
          ReplayEditor.effectsChanged();
        });
    context.waitTicks(3);
    Path shot = context.takeScreenshot("reizo-effects");
    check(
        context.computeOnClient(mc -> EffectRenderer.lastApplied() != null),
        "effects pass did not run");
    check(
        context.computeOnClient(mc -> ReplayEditor.project().effects.size() == 1),
        "effect keyframe not stored in the project");
    try {
      java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(shot.toFile());
      int x = img.getWidth() * 3 / 4;
      int mid = img.getRGB(x, img.getHeight() / 2);
      int r = (mid >> 16) & 0xFF;
      int g = (mid >> 8) & 0xFF;
      int b = mid & 0xFF;
      check(
          Math.abs(r - g) <= 3 && Math.abs(g - b) <= 3 && r + g + b > 30,
          "picture is not gray: " + r + "," + g + "," + b);
      int top = img.getRGB(x, 1) & 0xFFFFFF;
      check(top == 0, "no letterbox bar at the top: " + Integer.toHexString(top));
    } catch (java.io.IOException e) {
      throw new AssertionError(e);
    }
    context.runOnClient(
        mc -> {
          // Undo covers effect keyframes too.
          check(ReplayEditor.undo(), "nothing to undo");
          check(ReplayEditor.effects().isEmpty(), "undo kept the effect keyframe");
          check(ReplayEditor.project().effects.isEmpty(), "undo not saved to the project");
          mc.setScreen(null);
        });
    context.waitTicks(2);
    check(
        context.computeOnClient(mc -> EffectRenderer.lastApplied() == null),
        "effects pass still running after removing the keyframe");
    LOGGER.info("checkEffects OK");
  }

  private double gammaBefore;
  private boolean shadowsBefore;

  /**
   * Keyframed settings: the timeline's Settings button opens the panel, which lists Minecraft's
   * settings; keyframes drive brightness and entity shadows during the replay, undo covers them,
   * and the player's own values come back when the replay closes (checked in playBack after
   * leaving).
   */
  private void checkKeyframedSettings(ClientGameTestContext context) {
    context.runOnClient(
        mc -> mc.setScreen(new io.github.thexykril.reizo.fabric.ui.TimelineScreen()));
    context.waitTicks(2);
    context.runOnClient(mc -> pressButton(mc, "reizo.timeline.settings"));
    context.waitTicks(2);
    check(
        context.computeOnClient(
            mc -> mc.screen instanceof io.github.thexykril.reizo.fabric.ui.KeyframedSettingsScreen),
        "Settings button did not open the settings panel");
    check(
        context.computeOnClient(
            mc ->
                io.github.thexykril.reizo.fabric.settings.KeyframedSettings.find("minecraft:gamma")
                    != null),
        "brightness is not listed");
    gammaBefore = context.computeOnClient(mc -> mc.options.gamma().get());
    shadowsBefore = context.computeOnClient(mc -> mc.options.entityShadows().get());
    double target = gammaBefore > 0.5 ? 0.1 : 0.9;
    context.runOnClient(
        mc -> {
          var tracks = ReplayEditor.settings();
          ReplayEditor.recordUndo();
          // One keyframe holds its value everywhere (blending is unit tested in core).
          tracks.put("minecraft:gamma", PropertyTracks.Kind.NUMBER, 0, target);
          tracks.put(
              "minecraft:entity_shadows", PropertyTracks.Kind.TOGGLE, 0, shadowsBefore ? 0 : 1);
          ReplayEditor.settingsChanged();
        });
    context.waitTicks(3);
    context.takeScreenshot("reizo-settings");
    context.runOnClient(
        mc -> {
          check(
              Math.abs(mc.options.gamma().get() - target) < 1e-6,
              "brightness not driven: " + mc.options.gamma().get() + ", expected " + target);
          check(mc.options.entityShadows().get() != shadowsBefore, "entity shadows not switched");
          check(ReplayEditor.project().settings.size() == 2, "settings not stored in the project");
          check(ReplayEditor.undo(), "nothing to undo");
          check(ReplayEditor.settings().isEmpty(), "undo kept the setting keyframes");
        });
    context.waitTicks(2);
    check(
        context.computeOnClient(
            mc ->
                mc.options.gamma().get() == gammaBefore
                    && mc.options.entityShadows().get() == shadowsBefore),
        "removing the tracks did not put the settings back");
    // Leave a track so leaving the replay has something to put back.
    context.runOnClient(
        mc -> {
          check(ReplayEditor.redo(), "nothing to redo");
          mc.setScreen(null);
        });
    context.waitTicks(2);
    check(
        context.computeOnClient(mc -> mc.options.entityShadows().get() != shadowsBefore),
        "redo did not bring the settings back");
    LOGGER.info("checkKeyframedSettings OK");
  }

  /** Manual start after joining, while the player walks: local player frames must track motion. */
  private void recordMidSessionWhileWalking(ClientGameTestContext context) {
    RecordingSession session;
    RegistryAccess registries;
    try (TestSingleplayerContext world = context.worldBuilder().create()) {
      world.getClientWorld().waitForChunksDownload();
      context.waitTicks(10);
      // Start from the pause menu's button (#2), the way a player would without the hotkey.
      context.runOnClient(
          mc -> mc.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true)));
      context.waitTicks(2);
      context.runOnClient(mc -> pressButton(mc, "reizo.menu.start_recording"));
      session = context.computeOnClient(mc -> RecorderClient.get().session());
      check(session != null, "pause menu start failed");
      check(context.computeOnClient(mc -> mc.screen == null), "pause menu still open");
      registries = context.computeOnClient(mc -> mc.getConnection().registryAccess());
      // Equipment changes during the recording must show on the recorded player in playback.
      world
          .getServer()
          .runOnServer(
              server -> {
                var p = server.getPlayerList().getPlayers().get(0);
                p.setItemSlot(
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    new net.minecraft.world.item.ItemStack(
                        net.minecraft.world.item.Items.DIAMOND_SWORD));
                p.setItemSlot(
                    net.minecraft.world.entity.EquipmentSlot.HEAD,
                    new net.minecraft.world.item.ItemStack(
                        net.minecraft.world.item.Items.IRON_HELMET));
              });
      context.getInput().holdKeyFor(options -> options.keyUp, 40);
      context.waitTicks(5);
      // R alone must not stop it; Shift+R (the default, like Lunar) does.
      context.getInput().pressKey(GLFW.GLFW_KEY_R);
      context.waitTicks(2);
      check(
          context.computeOnClient(mc -> RecorderClient.get().session()) != null,
          "R without Shift stopped the recording");
      // Shift is also sneak: hold it a moment so the recording ends crouching.
      context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
      context.waitTicks(5);
      check(
          context.computeOnClient(
              mc -> mc.player.getPose() == net.minecraft.world.entity.Pose.CROUCHING),
          "player is not crouching before the stop");
      context.getInput().pressKey(GLFW.GLFW_KEY_R);
      context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
      context.waitTicks(2);
      check(
          context.computeOnClient(mc -> RecorderClient.get().session()) == null,
          "Shift+R did not stop the recording");
    }
    Path archive = await(context, session);
    Stats stats = verifyArchive(archive, registries);
    RecordingMetadata meta = metadata(archive);
    check(!meta.completeFromJoin, "mid-session recording must be flagged incomplete");
    check(meta.startSnapshot, "mid-session recording has no start snapshot");
    check(
        stats.packetNames.getOrDefault("ClientboundLevelChunkWithLightPacket", 0) > 0,
        "start snapshot has no chunks: " + stats.packetNames);
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
    // The start snapshot holds the chunks, so the world shows and the loading screen closes.
    context.waitFor(
        mc -> mc.screen == null && mc.level.getChunkSource().getLoadedChunksCount() > 0, 20 * 30);
    context.waitTicks(10); // let position interpolation settle
    context.takeScreenshot("reizo-mid-session-replay");
    Pos shown =
        context.computeOnClient(
            mc -> {
              var e = mc.level.getEntity(ReplayPlayback.current().view().recordedEntityId());
              return new Pos(e.getX(), e.getY(), e.getZ());
            });
    double offSq = shown.distanceSq(stats.lastPlayer);
    check(offSq < 0.25, "recorded player at " + shown + ", walk ended at " + stats.lastPlayer);
    context.runOnClient(
        mc -> {
          var e =
              (net.minecraft.world.entity.player.Player)
                  mc.level.getEntity(ReplayPlayback.current().view().recordedEntityId());
          check(
              e.getMainHandItem().is(net.minecraft.world.item.Items.DIAMOND_SWORD),
              "recorded player does not hold the sword: " + e.getMainHandItem());
          check(
              e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                  .is(net.minecraft.world.item.Items.IRON_HELMET),
              "recorded player does not wear the helmet");
          check(
              e.getPose() == net.minecraft.world.entity.Pose.CROUCHING,
              "recorded player is not crouching at the end: " + e.getPose());
        });
    checkSeeking(context, stats);
    context.runOnClient(Minecraft::disconnectWithProgressScreen);
    context.waitFor(mc -> !ReplayPlayback.isActive(), 20 * 30);
    // Leaving a server ends on the multiplayer screen; tests must end on the title screen.
    context.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
    LOGGER.info("playBackWalk OK: recorded player shown at {}", shown);
  }

  /**
   * Seeks the finished walk replay back to the start (the world is rebuilt through a
   * reconfiguration), checks the recorded player is back near the start, then seeks forward to the
   * end and checks it is at the end again.
   */
  private void checkSeeking(ClientGameTestContext context, Stats stats) {
    Object levelBefore = context.computeOnClient(mc -> mc.level);
    int seeksBefore = ReplayPlayback.current().seeksDone();
    long durationMs = ReplayPlayback.current().script().durationMs();
    context.runOnClient(mc -> ReplayPlayback.seekBy(-durationMs));
    context.waitFor(
        mc -> {
          ReplayServer replay = ReplayPlayback.current();
          return replay != null
              && replay.seeksDone() > seeksBefore
              && !replay.isReconfiguring()
              && mc.level != null
              && mc.level != levelBefore
              && replay.view() != null
              && mc.level.getEntity(replay.view().recordedEntityId())
                  instanceof net.minecraft.world.entity.player.Player;
        },
        20 * 30);
    context.waitTicks(5);
    check(ReplayPlayback.isActive(), "replay ended during a backwards seek");
    Pos early = recordedPlayerPos(context);
    check(
        early.distanceSq(stats.lastPlayer) > 1.0,
        "after seeking back the recorded player is still at the end: " + early);

    context.runOnClient(mc -> ReplayPlayback.current().seek(durationMs));
    context.waitFor(mc -> ReplayPlayback.current().script().finished(), 20 * 10);
    context.waitTicks(10);
    Pos end = recordedPlayerPos(context);
    check(
        end.distanceSq(stats.lastPlayer) < 0.25,
        "after seeking forward the recorded player is at " + end + ", not " + stats.lastPlayer);
    LOGGER.info("checkSeeking OK: back to {}, forward to {}", early, end);
  }

  private static Pos recordedPlayerPos(ClientGameTestContext context) {
    return context.computeOnClient(
        mc -> {
          var e = mc.level.getEntity(ReplayPlayback.current().view().recordedEntityId());
          return new Pos(e.getX(), e.getY(), e.getZ());
        });
  }

  /**
   * Camera path: N adds a keyframe at the camera, B plays the path, and the rendered camera then
   * sits on the path at the replay time. The project is saved with the keyframes.
   */
  private void checkCameraPath(ClientGameTestContext context) {
    context.getInput().pressKey(GLFW.GLFW_KEY_N);
    context.waitTicks(2);
    check(
        ReplayEditor.path().size() == 1, "N did not add a keyframe: " + ReplayEditor.path().size());
    double eyeY = context.computeOnClient(mc -> mc.player.getEyeY());
    check(
        Math.abs(ReplayEditor.path().keyframes().get(0).y() - eyeY) < 0.01,
        "keyframe is not at the camera");

    long duration = ReplayPlayback.current().script().durationMs();
    ReplayEditor.path().clear();
    context.runOnClient(
        mc -> {
          double x = mc.player.getX();
          double y = mc.player.getEyeY() + 5;
          double z = mc.player.getZ();
          ReplayEditor.putKeyframe(new CameraKeyframe(0, x, y, z, 0, 30, 0, 50));
          ReplayEditor.putKeyframe(new CameraKeyframe(duration, x + 10, y, z, 90, 30, 0, 50));
        });
    context.getInput().pressKey(GLFW.GLFW_KEY_B);
    context.waitTicks(2);
    check(ReplayEditor.isPathPlaying(), "B did not start the camera path");
    // Pause halfway through the path (a backwards seek, so the world is rebuilt first).
    context.runOnClient(
        mc -> {
          ReplayPlayback.current().setPaused(true);
          ReplayPlayback.current().seek(duration / 2);
        });
    context.waitFor(
        mc -> {
          ReplayServer replay = ReplayPlayback.current();
          return !replay.isReconfiguring()
              && Math.abs(replay.clock().timeMs() - duration / 2) < 5
              && mc.level != null
              && mc.screen == null;
        },
        20 * 30);
    context.waitTicks(5);
    double[] cam =
        context.computeOnClient(
            mc -> {
              var p = mc.gameRenderer.getMainCamera().position();
              return new double[] {p.x, p.y, p.z, mc.gameRenderer.getMainCamera().yRot()};
            });
    CameraKeyframe expected = ReplayEditor.path().sample(ReplayPlayback.current().clock().timeMs());
    check(
        Math.abs(cam[0] - expected.x()) < 0.01
            && Math.abs(cam[1] - expected.y()) < 0.01
            && Math.abs(cam[2] - expected.z()) < 0.01,
        "camera at " + java.util.Arrays.toString(cam) + ", path says " + expected);
    check(Math.abs(cam[3] - expected.yaw()) < 0.01, "camera yaw " + cam[3] + " vs " + expected);
    var saved =
        context.computeOnClient(
            mc -> {
              try {
                return ReplayEditor.store().load(ReplayEditor.project().id);
              } catch (java.io.IOException e) {
                throw new AssertionError(e);
              }
            });
    check(saved.cameraKeyframes.size() == 2, "project not saved with the keyframes");
    context.takeScreenshot("reizo-camera-path");
    context.getInput().pressKey(GLFW.GLFW_KEY_B);
    context.runOnClient(mc -> ReplayPlayback.current().setPaused(false));
    context.waitTicks(2);
    check(!ReplayEditor.isPathPlaying(), "B did not stop the camera path");
    LOGGER.info("checkCameraPath OK: camera on the path at {}", expected);
  }

  /**
   * Opens the timeline editor with its key and edits with real mouse and key input: drag a
   * keyframe, undo and redo, set the export region, seek from the ruler.
   */
  private void checkTimeline(ClientGameTestContext context) {
    long duration = ReplayPlayback.current().script().durationMs();
    long middle = duration / 2;
    context.runOnClient(
        mc ->
            ReplayEditor.putKeyframe(
                new CameraKeyframe(middle, mc.player.getX(), mc.player.getEyeY(), 0, 0, 0, 0, 70)));
    context.getInput().pressKey(GLFW.GLFW_KEY_Y);
    context.waitTicks(2);
    check(
        context.computeOnClient(mc -> mc.screen instanceof TimelineScreen),
        "Y did not open the timeline");
    double scale = context.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
    int[] at =
        context.computeOnClient(
            mc -> {
              TimelineScreen s = (TimelineScreen) mc.screen;
              return new int[] {
                s.xOf(middle), s.cameraTrackY(), s.xOf(duration / 4), s.rulerTrackY()
              };
            });
    // Drag the middle keyframe 30 GUI pixels to the left.
    context.getInput().setCursorPos((at[0] + 0.5) * scale, (at[1] + 0.5) * scale);
    context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    context.waitTicks(1);
    for (int i = 0; i < 3; i++) {
      context.getInput().moveCursor(-10 * scale, 0);
      context.waitTicks(1);
    }
    context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    context.waitTicks(2);
    List<Long> afterDrag = keyframeTimes();
    check(afterDrag.size() == 3, "drag changed the keyframe count: " + afterDrag);
    long moved = afterDrag.get(1);
    check(moved < middle - 100, "keyframe was not dragged left: " + middle + " -> " + moved);
    Set<Long> selected =
        context.computeOnClient(mc -> ((TimelineScreen) mc.screen).selectedTimes());
    check(selected.equals(Set.of(moved)), "dragged keyframe not selected: " + selected);

    context.getInput().holdControl();
    context.getInput().pressKey(GLFW.GLFW_KEY_Z);
    context.getInput().releaseControl();
    context.waitTicks(1);
    check(keyframeTimes().get(1) == middle, "Ctrl+Z did not undo the drag: " + keyframeTimes());
    context.getInput().holdControl();
    context.getInput().pressKey(GLFW.GLFW_KEY_Y);
    context.getInput().releaseControl();
    context.waitTicks(1);
    check(keyframeTimes().get(1) == moved, "Ctrl+Y did not redo the drag: " + keyframeTimes());

    context.getInput().pressKey(GLFW.GLFW_KEY_I);
    context.waitTicks(1);
    long in = ReplayEditor.inMs();
    check(in >= 0, "I did not set the export start");
    context.takeScreenshot("reizo-timeline");
    context.getInput().holdShift();
    context.getInput().pressKey(GLFW.GLFW_KEY_I);
    context.getInput().releaseShift();
    context.waitTicks(1);
    check(ReplayEditor.inMs() == -1, "Shift+I did not clear the export start");

    // Space pauses; then a click on the ruler at a quarter of the recording seeks there.
    context.getInput().pressKey(GLFW.GLFW_KEY_SPACE);
    context.waitTicks(1);
    check(!ReplayPlayback.current().clock().isRunning(), "Space did not pause the replay");
    int seeks = ReplayPlayback.current().seeksDone();
    var cameraBefore = context.computeOnClient(mc -> mc.player.position());
    context.getInput().setCursorPos((at[2] + 0.5) * scale, (at[3] + 0.5) * scale);
    context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    context.waitFor(
        mc -> {
          ReplayServer r = ReplayPlayback.current();
          return r.seeksDone() > seeks && !r.isReconfiguring() && mc.level != null;
        },
        20 * 30);
    context.waitTicks(2);
    var cameraAfter = context.computeOnClient(mc -> mc.player.position());
    check(
        cameraAfter.distanceTo(cameraBefore) < 0.5,
        "backwards seek moved the free camera: " + cameraBefore + " -> " + cameraAfter);
    long now = ReplayPlayback.current().clock().timeMs();
    // Seeks never go before the first play packet (the world starts there).
    long expected = Math.max(ReplayPlayback.current().script().startMs(), duration / 4);
    check(
        Math.abs(now - expected) < Math.max(250, duration / 20),
        "ruler click seeked to " + now + ", expected about " + expected);
    var saved =
        context.computeOnClient(
            mc -> {
              try {
                return ReplayEditor.store().load(ReplayEditor.project().id);
              } catch (java.io.IOException e) {
                throw new AssertionError(e);
              }
            });
    check(
        saved.cameraKeyframes.size() == 3 && saved.cameraKeyframes.get(1).t == moved,
        "project not saved after the timeline edit");
    context.runOnClient(mc -> mc.setScreen(null));
    context.waitTicks(1);
    LOGGER.info(
        "checkTimeline OK: keyframe dragged {} -> {}, ruler seek to {}", middle, moved, now);
  }

  /**
   * Roll and FOV keys on the free camera, stored in a new keyframe, and the camera path drawn in
   * the world (screenshot only: gizmos have no state to assert on).
   */
  private void checkFreeCamera(ClientGameTestContext context) {
    check(ReplayCamera.mode() == ReplayCamera.Mode.FREE, "camera not free: " + ReplayCamera.mode());
    context.getInput().holdKeyFor(GLFW.GLFW_KEY_I, 5);
    context.waitTicks(1);
    float roll = ReplayEditor.freeRoll();
    check(roll >= 8 && roll <= 12, "holding I did not roll right: " + roll);
    check(
        ReplayEditor.rollNow() == roll,
        "roll not applied to the camera: " + ReplayEditor.rollNow());
    float setting = context.computeOnClient(mc -> mc.options.fov().get().floatValue());
    context.getInput().holdKeyFor(GLFW.GLFW_KEY_MINUS, 10);
    context.waitTicks(1);
    float fov = ReplayEditor.fovNow();
    check(fov < setting - 7 && fov > setting - 13, "holding - did not zoom in: " + fov);
    CameraKeyframe k =
        context.computeOnClient(
            mc -> {
              ReplayEditor.path().clear();
              return ReplayEditor.addKeyframe(mc);
            });
    check(
        k != null && k.roll() == roll && k.fov() == fov,
        "keyframe did not store roll and FOV: " + k);
    context.getInput().pressKey(GLFW.GLFW_KEY_0);
    context.waitTicks(1);
    check(
        ReplayEditor.freeRoll() == 0 && Float.isNaN(ReplayEditor.fovNow()),
        "0 did not reset the camera");

    // A path in front of the camera, curving through three keyframes, for the screenshot.
    long duration = ReplayPlayback.current().script().durationMs();
    context.runOnClient(
        mc -> {
          ReplayEditor.path().clear();
          var eye = mc.player.getEyePosition();
          var look = mc.player.getLookAngle();
          var flat = look.multiply(1, 0, 1).normalize();
          var side = new net.minecraft.world.phys.Vec3(-flat.z, 0, flat.x);
          for (int i = 0; i < 3; i++) {
            var p =
                eye.add(look.scale(5 + i)).add(side.scale((i - 1) * 2)).add(0, (i - 1) * 0.5, 0);
            ReplayEditor.putKeyframe(
                new CameraKeyframe(
                    duration * i / 2, p.x, p.y, p.z, mc.player.getYRot() + 30 * (i - 1), 0, 0, 70));
          }
        });
    check(PathVisualizer.isVisible(), "path display off by default");
    context.waitTicks(3);
    context.takeScreenshot("reizo-path-display");
    context.getInput().pressKey(GLFW.GLFW_KEY_G);
    context.waitTicks(1);
    check(!PathVisualizer.isVisible(), "G did not hide the path");
    context.getInput().pressKey(GLFW.GLFW_KEY_G);
    LOGGER.info("checkFreeCamera OK: roll {} and FOV {} stored in {}", roll, fov, k);
  }

  private static List<Long> keyframeTimes() {
    return ReplayEditor.path().keyframes().stream().map(CameraKeyframe::timeMs).toList();
  }

  /**
   * Exports the running replay with FFmpeg at 20 fps, frame by frame, and checks the video has
   * exactly one frame per planned frame. Skipped (with a log line) when FFmpeg is not installed.
   */
  private void exportVideo(ClientGameTestContext context) {
    if (io.github.thexykril.reizo.core.export.FfmpegCommand.locate(null, System.getenv("PATH"))
        .isEmpty()) {
      LOGGER.warn("exportVideo SKIPPED: ffmpeg not on PATH");
      return;
    }
    // The pause menu's "Export video" opens the settings, which list what this FFmpeg can encode.
    context.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true)));
    context.waitTicks(2);
    context.runOnClient(mc -> pressButton(mc, "reizo.menu.export"));
    context.waitTicks(2);
    var offered =
        context.computeOnClient(
            mc ->
                mc.screen instanceof ExportSettingsScreen e
                    ? e.offeredFormats()
                    : List.<ExportFormat>of());
    check(
        offered.contains(ExportFormat.H264) && offered.contains(ExportFormat.VP9),
        "export settings did not offer H.264 and VP9: " + offered);
    context.takeScreenshot("reizo-export-settings");
    context.runOnClient(mc -> mc.setScreen(null));
    context.waitTicks(1);

    java.util.concurrent.atomic.AtomicReference<ExportSession> result =
        new java.util.concurrent.atomic.AtomicReference<>();
    long duration = ReplayPlayback.current().script().durationMs();
    context.runOnClient(
        mc -> {
          var render = ReplayEditor.project().render;
          render.fps = 20;
          render.width = 0; // keep the window size; no scaling on slow CI machines
          render.height = 0;
          render.crf = 30;
          render.codec = "libx264";
          render.output = "gametest-export.mp4";
          check(ExportControls.exportCurrent(mc, result::set), "export did not start");
        });
    // A sound in the middle of the export ends up in its sound track.
    context.waitFor(
        mc -> ExportSession.current() != null && ExportSession.current().framesCaptured() > 20,
        20 * 120);
    context.runOnClient(
        mc ->
            mc.getSoundManager()
                .play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f)));
    context.waitFor(mc -> result.get() != null, 20 * 240);
    ExportSession s = result.get();
    check(s.state() == ExportSession.State.DONE, "export ended " + s.state() + ": " + s.error());
    check(s.soundsCaptured() >= 1, "no sound captured during the export");
    String streams =
        run(
            "ffprobe",
            "-v",
            "error",
            "-show_entries",
            "stream=codec_name",
            "-of",
            "csv=p=0",
            s.output().toString());
    check(streams.strip().equals("h264\naac"), "export streams: " + streams.strip());
    String volume =
        run(
            "ffmpeg",
            "-hide_banner",
            "-i",
            s.output().toString(),
            "-map",
            "0:a",
            "-af",
            "volumedetect",
            "-f",
            "null",
            "-");
    var max = java.util.regex.Pattern.compile("max_volume: (-?[0-9.]+) dB").matcher(volume);
    check(
        max.find() && Double.parseDouble(max.group(1)) > -40, "export sound is silent: " + volume);
    // In sync: the sound starts within one frame (50 ms at 20 fps) of the frame it played on.
    String silence =
        run(
            "ffmpeg",
            "-hide_banner",
            "-i",
            s.output().toString(),
            "-map",
            "0:a",
            "-af",
            "silencedetect=n=-60dB:d=0.01",
            "-f",
            "null",
            "-");
    var end = java.util.regex.Pattern.compile("silence_end: ([0-9.]+)").matcher(silence);
    check(end.find(), "no sound onset found: " + silence);
    double onsetMs = Double.parseDouble(end.group(1)) * 1000;
    check(
        Math.abs(onsetMs - s.firstSoundMs()) <= 50,
        "sound at " + onsetMs + " ms, expected " + s.firstSoundMs() + " ms");
    check(
        !java.nio.file.Files.exists(s.output().resolveSibling("gametest-export.video.mp4")),
        "temporary video left behind");
    int expected =
        new io.github.thexykril.reizo.core.export.ExportPlan(0, duration, 20).frameCount();
    check(s.framesCaptured() == expected, "captured " + s.framesCaptured() + " of " + expected);
    String frames =
        run(
            "ffprobe",
            "-v",
            "error",
            "-count_frames",
            "-select_streams",
            "v:0",
            "-show_entries",
            "stream=nb_read_frames",
            "-of",
            "csv=p=0",
            s.output().toString());
    check(
        frames.strip().equals(Integer.toString(expected)),
        "video has " + frames.strip() + " frames, expected " + expected);
    LOGGER.info("exportVideo OK: {} frames in {}", expected, s.output());
    exportRegionVp9(context, duration);
  }

  /** A second export: VP9 in WebM, only the middle half (the timeline's In/Out), at 10 fps. */
  private void exportRegionVp9(ClientGameTestContext context, long duration) {
    long in = duration / 4;
    long out = duration * 3 / 4;
    java.util.concurrent.atomic.AtomicReference<ExportSession> result =
        new java.util.concurrent.atomic.AtomicReference<>();
    context.runOnClient(
        mc -> {
          ReplayEditor.setRegion(true, in);
          ReplayEditor.setRegion(false, out);
          var render = ReplayEditor.project().render;
          render.fps = 10;
          render.codec = "libvpx-vp9";
          render.crf = 40;
          render.output = "gametest-region.mp4"; // the extension follows the format
          // Larger than the 320x240 window: the game renders at this size for the export.
          render.width = 640;
          render.height = 360;
          // Motion blur: four renders averaged per frame; still one frame each in the file.
          render.motionBlur = 4;
          check(ExportControls.exportCurrent(mc, result::set), "VP9 export did not start");
        });
    context.waitFor(mc -> result.get() != null, 20 * 240);
    ExportSession s = result.get();
    check(
        s.state() == ExportSession.State.DONE, "VP9 export ended " + s.state() + ": " + s.error());
    check(
        s.output().getFileName().toString().equals("gametest-region.webm"),
        "VP9 export file: " + s.output());
    int expected = new io.github.thexykril.reizo.core.export.ExportPlan(in, out, 10).frameCount();
    String probe =
        run(
            "ffprobe",
            "-v",
            "error",
            "-count_frames",
            "-select_streams",
            "v:0",
            "-show_entries",
            "stream=codec_name,width,height,nb_read_frames",
            "-of",
            "csv=p=0",
            s.output().toString());
    check(
        probe.strip().startsWith("vp9,640,360," + expected),
        "VP9 export probe: " + probe.strip() + ", expected vp9,640,360," + expected);
    // Rendered, not blank: the mean brightness of the middle frame (scaled to one gray pixel).
    byte[] mean =
        runBytes(
            "ffmpeg",
            "-v",
            "error",
            "-i",
            s.output().toString(),
            "-vf",
            "select=eq(n\\," + expected / 2 + "),scale=1:1",
            "-frames:v",
            "1",
            "-f",
            "rawvideo",
            "-pix_fmt",
            "gray",
            "-");
    check(
        mean.length == 1 && (mean[0] & 0xFF) > 20,
        "export frame is blank: " + java.util.Arrays.toString(mean));
    var size =
        context.computeOnClient(
            mc ->
                mc.getMainRenderTarget().width
                    + "x"
                    + mc.getMainRenderTarget().height
                    + " window "
                    + mc.getWindow().getWidth()
                    + "x"
                    + mc.getWindow().getHeight());
    check(size.startsWith("320x240"), "render size not restored after export: " + size);
    context.runOnClient(
        mc -> {
          ReplayEditor.setRegion(true, -1);
          ReplayEditor.setRegion(false, -1);
          ReplayEditor.project().render.motionBlur = 1;
        });
    LOGGER.info("exportRegionVp9 OK: {} frames of {}..{} ms in {}", expected, in, out, s.output());
  }

  private static byte[] runBytes(String... command) {
    try {
      Process p =
          new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
      byte[] out = p.getInputStream().readAllBytes();
      p.waitFor(60, TimeUnit.SECONDS);
      return out;
    } catch (java.io.IOException e) {
      throw new AssertionError("could not run " + command[0], e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  private static String run(String... command) {
    try {
      Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
      String out =
          new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      p.waitFor(60, TimeUnit.SECONDS);
      return out;
    } catch (java.io.IOException e) {
      throw new AssertionError("could not run " + command[0], e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  /** Presses the button on the current screen whose label is the given translation key. */
  private static void pressButton(Minecraft mc, String key) {
    var button =
        net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(mc.screen).stream()
            .filter(
                b -> b.getMessage().equals(net.minecraft.network.chat.Component.translatable(key)))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no '" + key + "' button on " + mc.screen));
    ((net.minecraft.client.gui.components.Button) button)
        .onPress(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
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
