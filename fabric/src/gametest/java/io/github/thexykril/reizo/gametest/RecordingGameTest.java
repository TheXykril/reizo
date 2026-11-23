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
import io.github.thexykril.reizo.core.timeline.Sequence;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.core.timeline.Sequence;
import io.github.thexykril.reizo.fabric.export.ExportControls;
import io.github.thexykril.reizo.fabric.export.ExportSession;
import io.github.thexykril.reizo.fabric.playback.EffectRenderer;
import io.github.thexykril.reizo.fabric.playback.PathVisualizer;
import io.github.thexykril.reizo.fabric.playback.ReplayCamera;
import io.github.thexykril.reizo.fabric.playback.ReplayEditor;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.playback.ReplayServer;
import io.github.thexykril.reizo.fabric.ui.ExportSettingsView;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.client.CameraType;
//? if >=1.21.11 {
import net.minecraft.client.GraphicsPreset;
//?}
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
import com.mojang.blaze3d.platform.InputConstants;
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
    recoverAfterCrash(context);
    measureRecordingCost(context);
  }

  /**
   * Performance baseline (#6), headless: what recording costs the game while walking for 10 s, then
   * what shadow recording costs. Logs the numbers (docs/TESTING.md has the ones from real machines)
   * and fails only on gross regressions, since CI machines are slow and shared.
   */
  private void measureRecordingCost(ClientGameTestContext context) {
    try (TestSingleplayerContext world = context.worldBuilder().create()) {
      world.getClientWorld().waitForChunksDownload();
      context.waitTicks(20);
      io.github.thexykril.reizo.fabric.capture.RecorderStats.reset();
      context.runOnClient(mc -> RecorderClient.get().toggleRecording());
      RecordingSession session = context.computeOnClient(mc -> RecorderClient.get().session());
      check(session != null, "recording did not start");
      long start = System.nanoTime();
      context.getInput().holdKeyFor(options -> options.keyUp, 200);
      double seconds = (System.nanoTime() - start) / 1e9;
      var rec = io.github.thexykril.reizo.fabric.capture.RecorderStats.snapshot();
      context.runOnClient(mc -> RecorderClient.get().toggleRecording());
      Path archive = await(context, session);
      long bytes;
      try {
        bytes = Files.size(archive);
      } catch (java.io.IOException e) {
        throw new AssertionError(e);
      }
      double mbPerMin = bytes / 1048576.0 / seconds * 60;
      check(
          session.droppedFrames() == 0, "recording dropped " + session.droppedFrames() + " frames");

      io.github.thexykril.reizo.fabric.capture.RecorderStats.reset();
      context.runOnClient(
          mc -> {
            RecorderClient.get().config().shadowEnabled = true;
            RecorderClient.get().config().shadowSeconds = 30;
            RecorderClient.get().applyShadowSettings();
          });
      context.getInput().holdKeyFor(options -> options.keyDown, 200);
      var shadow = io.github.thexykril.reizo.fabric.capture.RecorderStats.snapshot();
      long shadowBytes = context.computeOnClient(mc -> RecorderClient.get().shadow().bytes());
      context.runOnClient(
          mc -> {
            RecorderClient.get().config().shadowEnabled = false;
            RecorderClient.get().applyShadowSettings();
          });
      LOGGER.info(
          "measureRecordingCost OK: recording {} MB/min ({} bytes in {} s), {} us per tick over {}"
              + " ticks, {} us per packet over {} packets; shadow {} us per tick (segment"
              + " snapshots included), {} KB in memory",
          String.format("%.2f", mbPerMin),
          bytes,
          String.format("%.1f", seconds),
          String.format("%.1f", rec.microsPerTick()),
          rec.ticks(),
          String.format("%.2f", rec.microsPerPacket()),
          rec.packets(),
          String.format("%.1f", shadow.microsPerTick()),
          shadowBytes / 1024);
      check(rec.microsPerTick() < 2000, "recording costs " + rec.microsPerTick() + " us per tick");
      check(rec.microsPerPacket() < 500, "capture costs " + rec.microsPerPacket() + " us/packet");
      check(mbPerMin < 30, "recording writes " + mbPerMin + " MB/min");
    }
    context.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
  }

  /**
   * Crash recovery (#5). A killed game leaves its working directory with whatever had reached the
   * disk and no live lock owner. The test makes that state by copying a live recording's working
   * directory mid-recording, without its lock, and appending half a block of garbage like a write
   * cut short. Startup recovery must salvage it into a playable recording marked RECOVERED.
   */
  private void recoverAfterCrash(ClientGameTestContext context) {
    RecordingSession session;
    RegistryAccess registries;
    Path crashed;
    try (TestSingleplayerContext world = context.worldBuilder().create()) {
      world.getClientWorld().waitForChunksDownload();
      context.waitTicks(10);
      context.runOnClient(mc -> RecorderClient.get().toggleRecording());
      session = context.computeOnClient(mc -> RecorderClient.get().session());
      check(session != null, "recording did not start");
      registries = context.computeOnClient(mc -> mc.getConnection().registryAccess());
      context.runOnClient(mc -> RecorderClient.get().addMarker("before the crash"));
      context.getInput().holdKeyFor(options -> options.keyUp, 20);
      // Past the flush (1 s) and metadata (5 s) intervals, so both are on disk.
      context.waitTicks(130);
      Path root = context.computeOnClient(mc -> RecorderClient.get().storage().root());
      Path live = root.resolve("working").resolve(session.id());
      check(Files.isDirectory(live), "no working directory at " + live);
      crashed = root.resolve("working").resolve(session.id() + "-crashed");
      try {
        Files.createDirectories(crashed);
        Files.copy(live.resolve("meta.json"), crashed.resolve("meta.json"));
        byte[] stream = Files.readAllBytes(live.resolve("stream.bin"));
        check(stream.length > 0, "nothing flushed to disk after 6 s");
        byte[] torn = new byte[stream.length + 300];
        System.arraycopy(stream, 0, torn, 0, stream.length);
        for (int i = stream.length; i < torn.length; i++) {
          torn[i] = (byte) (i * 31);
        }
        Files.write(crashed.resolve("stream.bin"), torn);
      } catch (java.io.IOException e) {
        throw new AssertionError(e);
      }
      context.runOnClient(mc -> RecorderClient.get().toggleRecording());
    }
    await(context, session);
    int recovered = context.computeOnClient(mc -> RecorderClient.get().recoverAbandonedNow());
    check(recovered == 1, "recovered " + recovered + " recordings, expected 1");
    check(!Files.exists(crashed), "working directory left after recovery");
    Path archive;
    try (var files = Files.list(crashed.getParent().getParent())) {
      archive =
          files
              .filter(f -> f.toString().endsWith(".reizo"))
              .filter(f -> metadata(f).state == RecordingMetadata.State.RECOVERED)
              .findFirst()
              .orElseThrow(() -> new AssertionError("no recovered recording"));
    } catch (java.io.IOException e) {
      throw new AssertionError(e);
    }
    Stats stats = verifyArchive(archive, registries);
    RecordingMetadata meta = metadata(archive);
    check(
        stats.packetNames.getOrDefault("ClientboundLevelChunkWithLightPacket", 0) > 0,
        "recovered recording has no chunks");
    check(stats.localPlayerFrames > 0, "recovered recording has no player frames");
    check(meta.markers.size() == 1, "marker lost in recovery");
    check(meta.durationMs > 1000, "recovered duration " + meta.durationMs);
    // The recovered file plays like any other.
    context.runOnClient(
        mc -> {
          try {
            ReplayPlayback.start(mc, archive);
          } catch (java.io.IOException e) {
            throw new AssertionError("could not start the recovered replay", e);
          }
        });
    context.waitFor(
        mc -> {
          ReplayServer replay = ReplayPlayback.current();
          return replay != null
              && replay.script().finished()
              && mc.level != null
              && mc.screen == null
              && mc.level.getChunkSource().getLoadedChunksCount() > 0;
        },
        20 * 60);
    context.runOnClient(Minecraft::disconnectWithProgressScreen);
    context.waitFor(mc -> !ReplayPlayback.isActive(), 20 * 30);
    context.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
    LOGGER.info("recoverAfterCrash OK: {} -> {}", archive.getFileName(), stats);
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
    checkMainMenuEntries(context);
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
   * The "Open replays" key on the title screen, and the "Replays" button and key on a main menu
   * that replaces the title screen (OneClient's, matched by class name).
   */
  private void checkMainMenuEntries(ClientGameTestContext context) {
    check(
        io.github.thexykril.reizo.fabric.ui.MenuButtons.isReplacementMainMenuClass(
            "org.polyfrost.oneclient.gui.OneClientMenuScreen"),
        "OneClient's menu screen is not recognized");
    context.runOnClient(
        mc -> {
          var key = io.github.thexykril.reizo.fabric.ui.RecorderKeys.openReplays();
          key.setKey(
              com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(
                  com.mojang.blaze3d.platform.InputConstants.KEY_F7));
          net.minecraft.client.KeyMapping.resetMapping();
        });
    try {
      context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F7);
      context.waitFor(mc -> mc.screen instanceof ReplayBrowserScreen, 20 * 5);
      context.runOnClient(mc -> mc.setScreen(new OneclientMenuStub()));
      context.waitTicks(2);
      context.takeScreenshot("reizo-replacement-main-menu");
      context.runOnClient(mc -> pressButton(mc, "reizo.menu.replays"));
      context.waitFor(mc -> mc.screen instanceof ReplayBrowserScreen, 20 * 5);
      context.runOnClient(mc -> mc.setScreen(new OneclientMenuStub()));
      context.waitTicks(2);
      context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F7);
      context.waitFor(mc -> mc.screen instanceof ReplayBrowserScreen, 20 * 5);
    } finally {
      context.runOnClient(
          mc -> {
            io.github.thexykril.reizo.fabric.ui.RecorderKeys.openReplays()
                .setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
            net.minecraft.client.KeyMapping.resetMapping();
            mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
          });
    }
    context.waitFor(
        mc -> mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen, 20 * 10);
    LOGGER.info("checkMainMenuEntries OK");
  }

  /** Stands in for a main menu that replaces the title screen; matched by its class name. */
  private static final class OneclientMenuStub extends net.minecraft.client.gui.screens.Screen {
    OneclientMenuStub() {
      super(net.minecraft.network.chat.Component.literal("Main menu"));
    }
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
          //? if >=1.21.11 {
          options.applyGraphicsPreset(GraphicsPreset.FAST);
          //?} else {
          /*options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FAST);
          *///?}
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
                EntityType.PIG.create(
                    player.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            pig.setPos(player.getX() + 2, player.getY(), player.getZ());
            pig.setNoAi(true);
            pig.setCustomName(net.minecraft.network.chat.Component.literal("reizo-pig"));
            player.level().addFreshEntity(pig);
          });
      context.waitFor(mc -> findPig(mc) != null, 20 * 10);
      context.waitTicks(20);
      // A server switch behind a proxy: the server sends the client back to configuration and
      // then into a world again. The recording carries on through it.
      server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).connection.switchToConfig());
      context.waitFor(mc -> mc.getConnection() == null || mc.level == null, 20 * 10);
      for (int i = 0; i < 100 && !returnToWorld(server); i++) {
        context.waitTicks(1);
      }
      context.waitFor(mc -> mc.level != null && mc.player != null && mc.screen == null, 20 * 30);
      connection.getClientWorld().waitForChunksDownload();
      context.waitTicks(10);
      check(
          context.computeOnClient(mc -> RecorderClient.get().session()) == session,
          "the recording did not carry on through the server switch");
      server.runOnServer(
          s -> {
            var player = s.getPlayerList().getPlayers().get(0);
            var pig =
                EntityType.PIG.create(
                    player.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            pig.setPos(player.getX() - 2, player.getY(), player.getZ());
            pig.setNoAi(true);
            pig.setCustomName(net.minecraft.network.chat.Component.literal("reizo-pig-2"));
            player.level().addFreshEntity(pig);
          });
      context.waitFor(mc -> findPig(mc, "reizo-pig-2") != null, 20 * 10);
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
              && findPig(mc, "reizo-pig-2") != null;
        },
        20 * 60);
    check(
        context.computeOnClient(mc -> ReplayPlayback.current().script().reconfigureCount()) == 1,
        "the server switch is not in the replay");
    // Back to before the switch: the first server's world, without the second pig.
    long start = context.computeOnClient(mc -> ReplayPlayback.current().script().startMs());
    context.runOnClient(mc -> ReplayPlayback.current().seek(start + 300));
    context.waitFor(
        mc ->
            !ReplayPlayback.current().isReconfiguring()
                && mc.screen == null
                && findPig(mc) != null
                && findPig(mc, "reizo-pig-2") == null,
        20 * 30);
    exportAcrossServerSwitch(context);
    context.runOnClient(Minecraft::disconnectWithProgressScreen);
    context.waitFor(mc -> !ReplayPlayback.isActive(), 20 * 30);
    context.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
    LOGGER.info("recordOnDedicatedServer OK: {} -> {}", archive.getFileName(), stats);
  }

  /** Exports the whole replay, server switch included: every planned frame is in the video. */
  private void exportAcrossServerSwitch(ClientGameTestContext context) {
    if (io.github.thexykril.reizo.core.export.FfmpegCommand.locate(null, System.getenv("PATH"))
        .isEmpty()) {
      LOGGER.warn("exportAcrossServerSwitch SKIPPED: ffmpeg not on PATH");
      return;
    }
    java.util.concurrent.atomic.AtomicReference<ExportSession> result =
        new java.util.concurrent.atomic.AtomicReference<>();
    long[] range = new long[2];
    context.runOnClient(
        mc -> {
          var script = ReplayPlayback.current().script();
          range[0] = script.startMs();
          range[1] = script.durationMs();
          ReplayEditor.setRegion(true, range[0]);
          ReplayEditor.setRegion(false, range[1]);
          var render = ReplayEditor.project().render;
          render.fps = 10;
          render.width = 0;
          render.height = 0;
          render.crf = 35;
          render.codec = "libx264";
          render.motionBlur = 1;
          render.output = "gametest-switch.mp4";
          check(ExportControls.exportCurrent(mc, result::set), "export did not start");
        });
    context.waitFor(mc -> result.get() != null, 20 * 240);
    ExportSession s = result.get();
    check(s.state() == ExportSession.State.DONE, "export ended " + s.state() + ": " + s.error());
    int expected =
        new io.github.thexykril.reizo.core.export.ExportPlan(range[0], range[1], 10).frameCount();
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
        frames.strip().equals(String.valueOf(expected)),
        "export across the switch has " + frames.strip() + " frames, expected " + expected);
    LOGGER.info("exportAcrossServerSwitch OK: {} frames", expected);
  }

  /** Sends the player waiting in configuration back into the world; false while there is none. */
  private static boolean returnToWorld(TestDedicatedServerContext server) {
    java.util.concurrent.atomic.AtomicBoolean done =
        new java.util.concurrent.atomic.AtomicBoolean();
    server.runOnServer(
        s -> {
          for (var c : s.getConnection().getConnections()) {
            if (c.getPacketListener()
                instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl l) {
              l.returnToWorld();
              done.set(true);
            }
          }
        });
    return done.get();
  }

  private static net.minecraft.world.entity.Entity findPig(Minecraft mc) {
    return findPig(mc, "reizo-pig");
  }

  private static net.minecraft.world.entity.Entity findPig(Minecraft mc, String name) {
    if (mc.level == null) {
      return null;
    }
    for (var e : mc.level.entitiesForRendering()) {
      if (e.getType() == EntityType.PIG
          && e.getCustomName() != null
          && name.equals(e.getCustomName().getString())) {
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
      // Left open until the world closes, so the replay ends on the recorded inventory screen.
      context.runOnClient(
          mc ->
              mc.setScreen(
                  new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
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
    check(meta.client == null, "recorded as made in " + meta.client + " without OneClient");
    check(stats.hudFrames > 0, "no HUD frames recorded");
    check(stats.screenFrames > 0, "the open inventory was not recorded");
    check(meta.state == RecordingMetadata.State.COMPLETE, "state " + meta.state);
    // Messages use OneConfig's notifications exactly when OneConfig is loaded, else Minecraft toasts.
    boolean oneConfig = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("oneconfig");
    check(
        RecorderClient.useOneConfigNotifications() == oneConfig,
        "OneConfig notifications " + (oneConfig ? "missing with" : "without") + " OneConfig");
    check(
        RecorderClient.toastKind("reizo.toast.save_failed").name().equals("ERROR")
            && RecorderClient.toastKind("reizo.toast.saved").name().equals("SUCCESS")
            && RecorderClient.toastKind("reizo.export.cancelled").name().equals("WARNING")
            && RecorderClient.toastKind("reizo.toast.started").name().equals("INFO"),
        "wrong notification kinds");
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

    context.getInput().pressKey(InputConstants.KEY_V);
    context.waitTicks(2);
    check(ReplayCamera.mode() == ReplayCamera.Mode.POV, "camera mode " + ReplayCamera.mode());
    check(
        context.computeOnClient(
            mc ->
                mc.getCameraEntity().getId() == recordedId
                    && mc.options.getCameraType() == CameraType.FIRST_PERSON),
        "player view does not look through the recorded player");
    // The recorded player's hotbar and status bars are drawn from the recording in player view.
    context.waitFor(mc -> io.github.thexykril.reizo.fabric.ui.PlayerHudOverlay.visible(mc), 20 * 5);
    // The inventory screen open when the recording ended is drawn from the recording too.
    context.waitFor(
        mc -> io.github.thexykril.reizo.fabric.ui.PlayerScreenOverlay.visible(mc), 20 * 5);
    context.waitTicks(2);
    check(
        context.computeOnClient(
            mc ->
                io.github.thexykril.reizo.fabric.ui.PlayerScreenOverlay.current()
                        instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
                    && mc.screen == null),
        "recorded inventory not drawn, or opened as a real screen");
    context.takeScreenshot("reizo-replay-pov-hud");
    context.runOnClient(mc -> RecorderClient.get().config().showPlayerHud = false);
    check(
        !context.computeOnClient(io.github.thexykril.reizo.fabric.ui.PlayerHudOverlay::visible),
        "player HUD shown with its setting off");
    context.runOnClient(mc -> RecorderClient.get().config().showPlayerHud = true);

    context.getInput().pressKey(InputConstants.KEY_V);
    context.waitTicks(2);
    check(ReplayCamera.mode() == ReplayCamera.Mode.FOLLOW, "camera mode " + ReplayCamera.mode());
    check(
        context.computeOnClient(
            mc ->
                mc.getCameraEntity().getId() == recordedId
                    && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK),
        "follow camera is not behind the recorded player");

    context.getInput().pressKey(InputConstants.KEY_V);
    context.waitTicks(2);
    check(ReplayCamera.mode() == ReplayCamera.Mode.FREE, "camera mode " + ReplayCamera.mode());
    check(
        context.computeOnClient(
            mc ->
                mc.getCameraEntity() == mc.player
                    && mc.options.getCameraType() == CameraType.FIRST_PERSON),
        "free camera is not the spectator camera");

    context.getInput().pressKey(InputConstants.KEY_K);
    context.waitFor(mc -> mc.level.tickRateManager().isFrozen(), 20 * 5);
    check(!ReplayPlayback.current().clock().isRunning(), "replay clock still running");

    context.getInput().pressKey(InputConstants.KEY_RBRACKET);
    context.waitFor(mc -> mc.level.tickRateManager().tickrate() == 40f, 20 * 5);
    context.getInput().pressKey(InputConstants.KEY_K);
    context.waitFor(mc -> !mc.level.tickRateManager().isFrozen(), 20 * 5);
    check(ReplayPlayback.current().clock().speed() == 2.0, "speed did not step to 2x");

    context.getInput().pressKey(InputConstants.KEY_LBRACKET);
    context.waitFor(mc -> mc.level.tickRateManager().tickrate() == 20f, 20 * 5);

    // The replay has finished; the back key restarts it through a reconfiguration.
    Object levelBefore = context.computeOnClient(mc -> mc.level);
    context.getInput().pressKey(InputConstants.KEY_LEFT);
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
    context.getInput().pressKey(InputConstants.KEY_RIGHT);
    context.waitFor(mc -> ReplayPlayback.current().seeksDone() == 2, 20 * 5);
    context.waitFor(mc -> ReplayPlayback.current().script().finished(), 20 * 10);
    checkCameraPath(context);
    checkTimeline(context);
    checkClips(context);
    checkFreeCamera(context);
    checkEffects(context);
    checkKeyframedSettings(context);
    checkTimeAndWeather(context);
    checkHideEntity(context);
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
    // Depth of field focused right in front of the camera blurs the world; bloom brightens the
    // bright sky. Both compared with the same frame without effects.
    java.awt.image.BufferedImage plain = image(context.takeScreenshot("reizo-effects-none"));
    context.runOnClient(
        mc -> {
          ReplayEditor.effects().put(0, Grade.NEUTRAL.withDof(1).withFocus(0.5f));
          ReplayEditor.effectsChanged();
        });
    context.waitTicks(3);
    java.awt.image.BufferedImage blurred = image(context.takeScreenshot("reizo-effects-dof"));
    context.runOnClient(
        mc -> {
          ReplayEditor.effects().put(0, Grade.NEUTRAL.withBloom(1));
          ReplayEditor.effectsChanged();
        });
    context.waitTicks(3);
    java.awt.image.BufferedImage bloomed = image(context.takeScreenshot("reizo-effects-bloom"));
    context.runOnClient(
        mc -> {
          ReplayEditor.effects().remove(0);
          ReplayEditor.effectsChanged();
        });
    double sharp = detail(plain);
    double soft = detail(blurred);
    check(soft < sharp * 0.7, "depth of field did not blur: detail " + sharp + " -> " + soft);
    double glow = glowing(plain, bloomed);
    check(glow > 0.02, "bloom lit too few pixels: " + glow);
    LOGGER.info(
        "checkEffects OK: detail {} -> {} with depth of field, {}% of pixels lit by bloom",
        String.format("%.2f", sharp),
        String.format("%.2f", soft),
        String.format("%.1f", glow * 100));
  }

  private static java.awt.image.BufferedImage image(Path file) {
    try {
      return javax.imageio.ImageIO.read(file.toFile());
    } catch (java.io.IOException e) {
      throw new AssertionError(e);
    }
  }

  /** Mean difference between horizontal neighbours in the upper middle of the frame (0..255). */
  private static double detail(java.awt.image.BufferedImage img) {
    double sum = 0;
    int n = 0;
    for (int y = img.getHeight() / 10; y < img.getHeight() * 7 / 10; y++) {
      for (int x = img.getWidth() / 4; x < img.getWidth() - 1; x++) {
        int a = img.getRGB(x, y);
        int b = img.getRGB(x + 1, y);
        for (int shift = 0; shift <= 16; shift += 8) {
          sum += Math.abs(((a >> shift) & 0xFF) - ((b >> shift) & 0xFF));
        }
        n += 3;
      }
    }
    return sum / n;
  }

  /** Share of pixels in the same area that got clearly brighter (by more than 8 of 255). */
  private static double glowing(
      java.awt.image.BufferedImage before, java.awt.image.BufferedImage after) {
    int lit = 0;
    int n = 0;
    for (int y = before.getHeight() / 10; y < before.getHeight() * 7 / 10; y++) {
      for (int x = before.getWidth() / 4; x < before.getWidth(); x++) {
        int p = before.getRGB(x, y);
        int q = after.getRGB(x, y);
        int gain = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
          gain += ((q >> shift) & 0xFF) - ((p >> shift) & 0xFF);
        }
        if (gain > 24) {
          lit++;
        }
        n++;
      }
    }
    return lit / (double) n;
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

  /** Time of day and weather keyframes override the recorded ones, and removing them goes back. */
  private void checkTimeAndWeather(ClientGameTestContext context) {
    String time = "reizo:time_of_day";
    String rain = "reizo:rain";
    String thunder = "reizo:thunder";
    double recordedTime =
        context.computeOnClient(
            mc -> io.github.thexykril.reizo.fabric.settings.KeyframedSettings.find(time).get());
    check(
        context.computeOnClient(mc -> mc.level.getRainLevel(1f) == 0f),
        "the test world should be clear");
    context.runOnClient(
        mc -> {
          var tracks = ReplayEditor.settings();
          ReplayEditor.recordUndo();
          tracks.put(time, PropertyTracks.Kind.NUMBER, 0, 18_000);
          tracks.put(rain, PropertyTracks.Kind.NUMBER, 0, 1);
          tracks.put(thunder, PropertyTracks.Kind.NUMBER, 0, 1);
          ReplayEditor.settingsChanged();
        });
    context.waitTicks(5);
    context.takeScreenshot("reizo-replay-night-storm");
    context.runOnClient(
        mc -> {
          double shown =
              io.github.thexykril.reizo.fabric.settings.KeyframedSettings.find(time).get();
          check(Math.abs(shown - 18_000) < 1, "time of day not overridden: " + shown);
          check(mc.level.getRainLevel(1f) == 1f, "rain not overridden: " + mc.level.getRainLevel(1f));
          check(
              mc.level.getThunderLevel(1f) == 1f,
              "thunder not overridden: " + mc.level.getThunderLevel(1f));
          check(mc.level.isRaining() && mc.level.isThundering(), "the level does not report a storm");
          check(ReplayEditor.undo(), "nothing to undo");
        });
    context.waitTicks(2);
    context.runOnClient(
        mc -> {
          double shown =
              io.github.thexykril.reizo.fabric.settings.KeyframedSettings.find(time).get();
          // The recorded clock keeps running underneath, so allow for the ticks since.
          check(
              Math.abs(shown - recordedTime) < 200,
              "time of day not back to the recording: " + shown + ", was " + recordedTime);
          check(mc.level.getRainLevel(1f) == 0f, "rain not back to the recording");
          check(!io.github.thexykril.reizo.fabric.playback.ReplayWorldLook.thunderOverridden(),
              "thunder override left behind");
        });
    LOGGER.info("checkTimeAndWeather OK: recorded time {}", Math.round(recordedTime));
  }

  /** An entity hidden in the editor is not drawn, is listed in the settings, and undo shows it. */
  private void checkHideEntity(ClientGameTestContext context) {
    String name =
        context.computeOnClient(
            mc -> {
              net.minecraft.world.entity.Entity target = null;
              for (var e : mc.level.entitiesForRendering()) {
                if (e != mc.getCameraEntity() && e != mc.player) {
                  target = e;
                  break;
                }
              }
              check(target != null, "no entity in the replay to hide");
              var server = ReplayPlayback.current();
              long now = Math.min(server.replayTimeMs(), server.script().durationMs());
              check(
                  io.github.thexykril.reizo.fabric.playback.ReplayEntityHider.toggle(target, now),
                  "toggle did not hide " + target);
              return target.getUUID().toString();
            });
    context.waitTicks(2);
    context.runOnClient(
        mc -> {
          var target = entityByUuid(mc, name);
          check(
              io.github.thexykril.reizo.fabric.playback.ReplayEntityHider.isHidden(target),
              "entity not hidden");
          check(
              !mc.getEntityRenderDispatcher()
                  .shouldRender(
                      target,
                      new net.minecraft.client.renderer.culling.Frustum(
                          new org.joml.Matrix4f(), new org.joml.Matrix4f()),
                      0,
                      0,
                      0
                      //? if >=26.3 {
                      /*, 1f
                      *///?}
                      ),
              "a hidden entity would still be drawn");
          check(
              io.github.thexykril.reizo.fabric.settings.KeyframedSettings.all().stream()
                  .anyMatch(s -> s.id().endsWith(name)),
              "the hidden entity is not listed in the settings");
          check(ReplayEditor.undo(), "nothing to undo");
        });
    context.waitTicks(2);
    check(
        context.computeOnClient(
            mc ->
                !io.github.thexykril.reizo.fabric.playback.ReplayEntityHider.isHidden(
                    entityByUuid(mc, name))),
        "undo did not show the entity again");
    LOGGER.info("checkHideEntity OK: {}", name);
  }

  private static net.minecraft.world.entity.Entity entityByUuid(
      net.minecraft.client.Minecraft mc, String uuid) {
    for (var e : mc.level.entitiesForRendering()) {
      if (e.getUUID().toString().equals(uuid)) {
        return e;
      }
    }
    throw new AssertionError("entity " + uuid + " left the replay");
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
      context.getInput().pressKey(InputConstants.KEY_R);
      context.waitTicks(2);
      check(
          context.computeOnClient(mc -> RecorderClient.get().session()) != null,
          "R without Shift stopped the recording");
      // Shift is also sneak: hold it a moment so the recording ends crouching.
      context.getInput().holdKey(InputConstants.KEY_LSHIFT);
      context.waitTicks(5);
      check(
          context.computeOnClient(
              mc -> mc.player.getPose() == net.minecraft.world.entity.Pose.CROUCHING),
          "player is not crouching before the stop");
      context.getInput().pressKey(InputConstants.KEY_R);
      context.getInput().releaseKey(InputConstants.KEY_LSHIFT);
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
    context.getInput().pressKey(InputConstants.KEY_N);
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
    context.getInput().pressKey(InputConstants.KEY_B);
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
              return new double[] {p.x, p.y, p.z, io.github.thexykril.reizo.fabric.compat.Compat.cameraYaw(mc.gameRenderer.getMainCamera())};
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
    context.getInput().pressKey(InputConstants.KEY_B);
    context.runOnClient(mc -> ReplayPlayback.current().setPaused(false));
    context.waitTicks(2);
    check(!ReplayEditor.isPathPlaying(), "B did not stop the camera path");
    LOGGER.info("checkCameraPath OK: camera on the path at {}", expected);
  }

  /**
   * Opens the timeline editor with its key and edits with real mouse and key input: drag a
   * keyframe, undo and redo, set the export region, seek from the ruler.
   */
  /** Dismisses every OneConfig notification on screen; does nothing without OneConfig. */
  private static void clearOneConfigNotifications() {
    if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("oneconfig")) {
      return;
    }
    try {
      Class<?> manager =
          Class.forName("org.polyfrost.oneconfig.api.notifications.v1.NotificationsManager");
      manager.getMethod("clearAll").invoke(manager.getField("INSTANCE").get(null));
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("could not clear OneConfig notifications", e);
    }
  }

  private void checkTimeline(ClientGameTestContext context) {
    long duration = ReplayPlayback.current().script().durationMs();
    long middle = duration / 2;
    context.runOnClient(
        mc ->
            ReplayEditor.putKeyframe(
                new CameraKeyframe(middle, mc.player.getX(), mc.player.getEyeY(), 0, 0, 0, 0, 70)));
    context.getInput().pressKey(InputConstants.KEY_Y);
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
    // OneConfig's notifications (its first-launch toast, Reizo's own) sit over the window's
    // bottom-right corner and take clicks there; clear them so the drag reaches the timeline.
    context.runOnClient(mc -> clearOneConfigNotifications());
    // Drag the middle keyframe 30 GUI pixels to the left.
    context.getInput().setCursorPos((at[0] + 0.5) * scale, (at[1] + 0.5) * scale);
    context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
    context.waitTicks(1);
    for (int i = 0; i < 3; i++) {
      context.getInput().moveCursor(-10 * scale, 0);
      context.waitTicks(1);
    }
    context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
    context.waitTicks(2);
    List<Long> afterDrag = keyframeTimes();
    check(afterDrag.size() == 3, "drag changed the keyframe count: " + afterDrag);
    long moved = afterDrag.get(1);
    check(moved < middle - 100, "keyframe was not dragged left: " + middle + " -> " + moved);
    Set<Long> selected =
        context.computeOnClient(mc -> ((TimelineScreen) mc.screen).selectedTimes());
    check(selected.equals(Set.of(moved)), "dragged keyframe not selected: " + selected);

    context.getInput().holdControl();
    context.getInput().pressKey(InputConstants.KEY_Z);
    context.getInput().releaseControl();
    context.waitTicks(1);
    check(keyframeTimes().get(1) == middle, "Ctrl+Z did not undo the drag: " + keyframeTimes());
    context.getInput().holdControl();
    context.getInput().pressKey(InputConstants.KEY_Y);
    context.getInput().releaseControl();
    context.waitTicks(1);
    check(keyframeTimes().get(1) == moved, "Ctrl+Y did not redo the drag: " + keyframeTimes());

    context.getInput().pressKey(InputConstants.KEY_I);
    context.waitTicks(1);
    long in = ReplayEditor.inMs();
    check(in >= 0, "I did not set the export start");
    context.takeScreenshot("reizo-timeline");
    context.getInput().holdShift();
    context.getInput().pressKey(InputConstants.KEY_I);
    context.getInput().releaseShift();
    context.waitTicks(1);
    check(ReplayEditor.inMs() == -1, "Shift+I did not clear the export start");

    // Space pauses; then a click on the ruler at a quarter of the recording seeks there.
    context.getInput().pressKey(InputConstants.KEY_SPACE);
    context.waitTicks(1);
    check(!ReplayPlayback.current().clock().isRunning(), "Space did not pause the replay");
    int seeks = ReplayPlayback.current().seeksDone();
    var cameraBefore = context.computeOnClient(mc -> mc.player.position());
    context.getInput().setCursorPos((at[2] + 0.5) * scale, (at[3] + 0.5) * scale);
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    context.waitFor(
        mc -> {
          ReplayServer r = ReplayPlayback.current();
          return r.seeksDone() > seeks && !r.isReconfiguring() && mc.level != null;
        },
        20 * 30);
    context.waitTicks(2);
    check(
        context.computeOnClient(mc -> mc.screen instanceof TimelineScreen),
        "the editor did not come back after a backwards seek");
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
   * Clips on the Video lane with real input: S splits at the playhead, a click selects a clip and
   * Delete removes it, Ctrl+Z brings it back, dragging an end trims, dragging a clip reorders.
   */
  private void checkClips(ClientGameTestContext context) {
    long duration = ReplayPlayback.current().script().durationMs();
    long middle = duration / 2;
    context.runOnClient(mc -> ReplayPlayback.current().setPaused(true));
    context.getInput().pressKey(InputConstants.KEY_Y);
    context.waitTicks(2);
    check(
        context.computeOnClient(mc -> mc.screen instanceof TimelineScreen),
        "Y did not open the timeline for clips");
    check(ReplayEditor.sequence().isEmpty(), "clips before any edit: " + ReplayEditor.sequence());
    int seeks = ReplayPlayback.current().seeksDone();
    context.runOnClient(mc -> ReplayEditor.seekVideo(middle));
    waitSeek(context, seeks);
    context.getInput().pressKey(InputConstants.KEY_S);
    context.waitTicks(1);
    Sequence split = ReplayEditor.sequence();
    check(split.size() == 2, "S did not split the replay in two: " + split);
    long cut = split.clip(0).outMs();
    check(
        Math.abs(cut - Math.max(middle, ReplayPlayback.current().script().startMs())) < 250,
        "split at " + cut + ", expected about " + middle);

    double scale = context.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
    int y = context.computeOnClient(mc -> ((TimelineScreen) mc.screen).videoTrackY());
    // Click the first clip and delete it; undo brings it back.
    int first = context.computeOnClient(mc -> ((TimelineScreen) mc.screen).clipX(0, 0));
    context.getInput().setCursorPos((first + 0.5) * scale, (y + 0.5) * scale);
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    // The click seeks back into the first clip, which rebuilds the world; the editor comes back.
    context.waitFor(
        mc ->
            !ReplayPlayback.current().isReconfiguring()
                && mc.level != null
                && mc.screen instanceof TimelineScreen,
        20 * 30);
    String picked =
        context.computeOnClient(
            mc ->
                mc.screen instanceof TimelineScreen s
                    ? s.selectedClip() + " x=" + first + " y=" + y
                    : String.valueOf(mc.screen));
    check(picked.startsWith("0 "), "click did not select the first clip: " + picked);
    context.getInput().pressKey(InputConstants.KEY_DELETE);
    context.waitTicks(1);
    check(
        ReplayEditor.sequence().size() == 1 && ReplayEditor.sequence().clip(0).inMs() == cut,
        "Delete did not remove the first clip: " + ReplayEditor.sequence());
    context.getInput().holdControl();
    context.getInput().pressKey(InputConstants.KEY_Z);
    context.getInput().releaseControl();
    context.waitTicks(1);
    check(ReplayEditor.sequence().equals(split), "Ctrl+Z did not restore the clip");

    // Drag the end of the second clip 20 GUI pixels to the left: a shorter clip.
    int end = context.computeOnClient(mc -> ((TimelineScreen) mc.screen).clipX(1, 1));
    dragMouse(context, scale, end - 1, y, -20);
    Sequence trimmed = ReplayEditor.sequence();
    check(
        trimmed.size() == 2 && trimmed.clip(1).outMs() < split.clip(1).outMs() - 100,
        "dragging the clip end did not trim it: " + split + " -> " + trimmed);

    // Drag the second clip onto the first: they swap places.
    int[] xs =
        context.computeOnClient(
            mc -> {
              TimelineScreen s = (TimelineScreen) mc.screen;
              return new int[] {s.clipX(1, 0), s.clipX(0, -1) + 4};
            });
    dragMouse(context, scale, xs[0], y, xs[1] - xs[0]);
    Sequence moved = ReplayEditor.sequence();
    check(
        moved.size() == 2 && moved.clip(0).equals(trimmed.clip(1)),
        "dragging a clip did not reorder: " + trimmed + " -> " + moved);
    var saved =
        context.computeOnClient(
            mc -> {
              try {
                return ReplayEditor.store().load(ReplayEditor.project().id);
              } catch (java.io.IOException e) {
                throw new AssertionError(e);
              }
            });
    check(saved.sequence().equals(moved), "clips not saved: " + saved.sequence());
    context.takeScreenshot("reizo-clips");
    context.runOnClient(
        mc -> {
          ReplayEditor.clearClips();
          mc.setScreen(null);
        });
    context.waitTicks(1);
    LOGGER.info("checkClips OK: split at {}, trimmed and reordered to {}", cut, moved);
  }

  private static void waitSeek(ClientGameTestContext context, int seeksBefore) {
    context.waitFor(
        mc -> {
          ReplayServer r = ReplayPlayback.current();
          return r.seeksDone() > seeksBefore && !r.isReconfiguring() && mc.level != null;
        },
        20 * 30);
    context.waitTicks(2);
  }

  /** Holds the left button at (x, y) in GUI pixels and moves it {@code dx} in steps. */
  private static void dragMouse(ClientGameTestContext context, double scale, int x, int y, int dx) {
    context.getInput().setCursorPos((x + 0.5) * scale, (y + 0.5) * scale);
    context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
    context.waitTicks(1);
    for (int i = 1; i <= 4; i++) {
      context.getInput().moveCursor(dx * scale / 4, 0);
      context.waitTicks(1);
    }
    context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
    context.waitTicks(2);
  }

  /**
   * Roll and FOV keys on the free camera, stored in a new keyframe, and the camera path drawn in
   * the world (screenshot only: gizmos have no state to assert on).
   */
  private void checkFreeCamera(ClientGameTestContext context) {
    check(ReplayCamera.mode() == ReplayCamera.Mode.FREE, "camera not free: " + ReplayCamera.mode());
    context.getInput().holdKeyFor(InputConstants.KEY_I, 5);
    context.waitTicks(1);
    float roll = ReplayEditor.freeRoll();
    check(roll >= 8 && roll <= 12, "holding I did not roll right: " + roll);
    check(
        ReplayEditor.rollNow() == roll,
        "roll not applied to the camera: " + ReplayEditor.rollNow());
    float setting = context.computeOnClient(mc -> mc.options.fov().get().floatValue());
    context.getInput().holdKeyFor(InputConstants.KEY_MINUS, 10);
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
    context.getInput().pressKey(InputConstants.KEY_0);
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
    context.getInput().pressKey(InputConstants.KEY_G);
    context.waitTicks(1);
    check(!PathVisualizer.isVisible(), "G did not hide the path");
    context.getInput().pressKey(InputConstants.KEY_G);
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
                mc.screen instanceof ExportSettingsView e
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
          render.soundVolume = 50; // the export's own level, on top of the game's
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
    // A looping sound that moves past the camera and is stopped after a second is followed.
    context.waitFor(
        mc -> ExportSession.current() != null && ExportSession.current().framesCaptured() > 40,
        20 * 120);
    PassingLoop passing = context.computeOnClient(PassingLoop::new);
    context.runOnClient(mc -> mc.getSoundManager().play(passing));
    // Without an audio device (CI) the sound engine never ticks the sound, so the test does; then
    // the game stops it, as an entity's sound is stopped when it leaves.
    for (int i = 0; i < 20; i++) {
      context.runOnClient(mc -> passing.tick());
      context.waitTicks(1);
    }
    context.runOnClient(mc -> mc.getSoundManager().stop(passing));
    context.waitFor(mc -> result.get() != null, 20 * 240);
    ExportSession s = result.get();
    check(s.state() == ExportSession.State.DONE, "export ended " + s.state() + ": " + s.error());
    check(s.soundsCaptured() >= 2, "sounds captured during the export: " + s.soundsCaptured());
    var loop =
        s.soundCues().stream()
            .filter(io.github.thexykril.reizo.core.audio.SoundCue::loop)
            .findFirst()
            .orElse(null);
    check(loop != null, "the looping sound was not followed: " + s.soundCues());
    // Find the click by name: a recorded sound can be captured before it.
    var clickCue =
        s.soundCues().stream()
            .filter(c -> !c.positional() && c.clip().contains("click"))
            .findFirst()
            .orElse(null);
    check(clickCue != null, "the button click was not captured: " + s.soundCues());
    float click = clickCue.gain();
    check(click > 0 && click <= 0.5f + 1e-4f, "sound at 50% exported with gain " + click);
    check(
        loop.endMs() - loop.timeMs() > 700 && loop.endMs() - loop.timeMs() < 1500,
        "looping sound from " + loop.timeMs() + " to " + loop.endMs() + " ms");
    check(
        loop.path().size() >= 10
            && Math.abs(loop.path().get(loop.path().size() - 1).x() - loop.x()) > 2,
        "looping sound path: " + loop.path().size() + " points");
    LOGGER.info(
        "exportVideo: loop {} to {} ms, {} path points",
        loop.timeMs(),
        loop.endMs(),
        loop.path().size());
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
        "sound at " + onsetMs + " ms, expected " + s.firstSoundMs() + " ms; cues " + s.soundCues());
    // The click (0.28 s, half of it silent) starts again about every 0.28 s while the loop plays.
    double from = loop.timeMs() / 1000 - 0.03;
    double to = loop.endMs() / 1000;
    var onsets = java.util.regex.Pattern.compile("silence_end: ([0-9.]+)").matcher(silence);
    int repeats = 0;
    while (onsets.find()) {
      double t = Double.parseDouble(onsets.group(1));
      if (t >= from && t < to) {
        repeats++;
      }
    }
    check(repeats >= 3, "the loop started " + repeats + " times in its second: " + silence);
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
    exportClips(context, duration);
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

  /**
   * Exports two clips in reverse replay order: one file, as many frames as both clips, in clip
   * order, with no part files left behind.
   */
  private void exportClips(ClientGameTestContext context, long duration) {
    Sequence clips =
        Sequence.of(
            List.of(
                new Sequence.Clip(duration / 2, duration * 3 / 4),
                new Sequence.Clip(duration / 8, duration / 4)));
    java.util.concurrent.atomic.AtomicReference<ExportSession> result =
        new java.util.concurrent.atomic.AtomicReference<>();
    context.runOnClient(
        mc -> {
          ReplayEditor.editClips(s -> clips);
          var render = ReplayEditor.project().render;
          render.fps = 10;
          render.codec = "libx264";
          render.crf = 35;
          render.output = "gametest-clips.mp4";
          check(ExportControls.exportCurrent(mc, result::set), "clip export did not start");
        });
    context.waitFor(mc -> result.get() != null, 20 * 240);
    context.waitTicks(2);
    ExportSession s = result.get();
    check(
        s.state() == ExportSession.State.DONE, "clip export ended " + s.state() + ": " + s.error());
    Path output = ExportControls.exportsDir().resolve("gametest-clips.mp4");
    int expected = 0;
    for (Sequence.Clip c : clips.clips()) {
      expected += new io.github.thexykril.reizo.core.export.ExportPlan(c.inMs(), c.outMs(), 10)
          .frameCount();
    }
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
            output.toString());
    check(
        frames.strip().equals(String.valueOf(expected)),
        "clip export has " + frames.strip() + " frames, expected " + expected);
    try (var files = java.nio.file.Files.list(output.getParent())) {
      List<String> left =
          files
              .map(p -> p.getFileName().toString())
              .filter(n -> n.startsWith("gametest-clips.mp4."))
              .toList();
      check(left.isEmpty(), "clip export left part files: " + left);
    } catch (java.io.IOException e) {
      throw new AssertionError(e);
    }
    context.runOnClient(mc -> ReplayEditor.clearClips());
    LOGGER.info("exportClips OK: {} frames from {} in {}", expected, clips, output);
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
        .onPress(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_RETURN, 0, 0));
  }

  // ---- verification helpers ----

  private static final class Stats {
    int configPackets;
    int playPackets;
    int localPlayerFrames;
    int hudFrames;
    int screenFrames;
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
          + " hud="
          + hudFrames
          + " screen="
          + screenFrames
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
        } else if (type == FrameType.PLAYER_HUD) {
          io.github.thexykril.reizo.core.format.PlayerHudState hud =
              io.github.thexykril.reizo.core.format.PlayerHudState.decode(frame.payload());
          check(
              io.github.thexykril.reizo.fabric.capture.HudSampler.decodeItems(hud.items(), registries)
                      .size()
                  == io.github.thexykril.reizo.fabric.capture.HudSampler.ITEM_COUNT,
              "HUD frame without the hotbar and offhand");
          stats.hudFrames++;
        } else if (type == FrameType.PLAYER_SCREEN) {
          io.github.thexykril.reizo.core.format.PlayerScreenState screen =
              io.github.thexykril.reizo.core.format.PlayerScreenState.decode(frame.payload());
          if (screen.items() != null && screen.open()) {
            io.github.thexykril.reizo.fabric.capture.HudSampler.decodeItems(
                screen.items(), registries);
          }
          stats.screenFrames++;
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

  /** A looping click that moves along +X past the camera, 0.4 blocks per tick. */
  private static final class PassingLoop
      extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
    PassingLoop(net.minecraft.client.Minecraft mc) {
      super(
          net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
          net.minecraft.sounds.SoundSource.MASTER,
          net.minecraft.util.RandomSource.create(1));
      var at = mc.gameRenderer.getMainCamera().position();
      this.x = at.x - 4;
      this.y = at.y;
      this.z = at.z;
      this.looping = true;
      this.delay = 0;
      this.relative = false;
    }

    @Override
    public void tick() {
      x += 0.4;
    }
  }
}
