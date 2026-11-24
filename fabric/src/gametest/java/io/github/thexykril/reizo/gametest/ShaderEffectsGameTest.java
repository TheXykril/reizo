/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.gametest;

import io.github.thexykril.reizo.core.effects.Grade;
import io.github.thexykril.reizo.core.recording.RecordingSession;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.compat.ShaderPacks;
import io.github.thexykril.reizo.fabric.playback.PathVisualizer;
import io.github.thexykril.reizo.fabric.playback.ReplayEditor;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.playback.ReplayServer;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Depth of field through a shader pack. Runs only when Iris is loaded with a pack switched on, which
 * CI does not do. To run it by hand, stage Iris and Sodium with REIZO_EXTRA_MODS and a pack plus
 * config/iris.properties with REIZO_EXTRA_RUN_FILES (see docs/TESTING.md).
 */
public final class ShaderEffectsGameTest implements FabricClientGameTest {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/GameTest");

  @Override
  public void runTest(ClientGameTestContext context) {
    if (!FabricLoader.getInstance().isModLoaded("iris")) {
      return;
    }
    context.runOnClient(
        mc -> {
          mc.options.renderDistance().set(2);
          mc.options.framerateLimit().set(10);
        });
    RecordingSession session;
    try (TestSingleplayerContext world = context.worldBuilder().create()) {
      world.getClientWorld().waitForChunksDownload();
      context.waitTicks(20);
      check(ShaderPacks.inUse(), "Iris is loaded but no shader pack is in use");
      context.runOnClient(mc -> RecorderClient.get().toggleRecording());
      session = context.computeOnClient(mc -> RecorderClient.get().session());
      check(session != null, "recording did not start");
      context.waitTicks(40);
      context.runOnClient(mc -> RecorderClient.get().toggleRecording());
    }
    context.waitFor(mc -> session.result().isDone(), 20 * 60);
    Path archive;
    try {
      archive = session.result().get(1, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new AssertionError("recording did not finish", e);
    }
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
    // Follow the recorded player, so there is a subject in front of a distant background.
    context.runOnClient(
        mc -> {
          io.github.thexykril.reizo.fabric.playback.ReplayCamera.set(
              mc, io.github.thexykril.reizo.fabric.playback.ReplayCamera.Mode.FOLLOW);
          if (PathVisualizer.isVisible()) {
            PathVisualizer.toggle();
          }
        });
    context.waitTicks(60);
    context.runOnClient(mc -> mc.getToastManager().clear());
    context.waitTicks(5);
    BufferedImage plain = image(context.takeScreenshot("reizo-shader-none"));
    setGrade(context, Grade.NEUTRAL.withDof(1).withFocus(0.5f));
    BufferedImage near = image(context.takeScreenshot("reizo-shader-dof-near"));
    setGrade(context, Grade.NEUTRAL.withDof(1).withFocus(Grade.AUTO_FOCUS));
    BufferedImage auto = image(context.takeScreenshot("reizo-shader-dof-auto"));
    String numbers =
        String.format(
            java.util.Locale.ROOT,
            "subject %.2f/%.2f/%.2f, edges %.2f/%.2f/%.2f (plain/near/auto)",
            centre(plain),
            centre(near),
            centre(auto),
            edges(plain),
            edges(near),
            edges(auto));
    LOGGER.info("shader depth of field: {}", numbers);
    check(edges(near) < edges(plain) * 0.8, "near focus did not blur with shaders on: " + numbers);
    // The player's outline mixes with the blurred background, so the player keeps part of its
    // detail; a blur focused in front of them takes much more.
    check(
        centre(auto) > centre(near) * 1.25 && centre(auto) > centre(plain) * 0.5,
        "autofocus did not keep the followed player sharp with shaders on: " + numbers);
    check(
        edges(auto) < edges(plain) * 0.9,
        "autofocus blurred nothing behind the player with shaders on: " + numbers);
    context.runOnClient(net.minecraft.client.Minecraft::disconnectWithProgressScreen);
    context.waitFor(
        mc -> !ReplayPlayback.isActive(), 20 * 30);
    context.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
    context.waitTicks(20);
    LOGGER.info("shaderDepthOfField OK");
  }

  private static void setGrade(ClientGameTestContext context, Grade grade) {
    context.runOnClient(
        mc -> {
          ReplayEditor.effects().put(0, grade);
          ReplayEditor.effectsChanged();
        });
    context.waitTicks(5);
  }

  private static BufferedImage image(Path file) {
    try {
      return javax.imageio.ImageIO.read(file.toFile());
    } catch (java.io.IOException e) {
      throw new AssertionError(e);
    }
  }

  /** Mean difference between horizontal neighbours where the followed player stands (0..255). */
  private static double centre(BufferedImage img) {
    int w = img.getWidth();
    int h = img.getHeight();
    return detail(img, w * 46 / 100, h * 48 / 100, w * 54 / 100, h * 70 / 100);
  }

  /** The same along the top of the frame, which shows the distance behind the player. */
  private static double edges(BufferedImage img) {
    return detail(img, 0, 0, img.getWidth(), img.getHeight() / 4);
  }

  private static double detail(BufferedImage img, int x0, int y0, int x1, int y1) {
    double sum = 0;
    int n = 0;
    for (int y = y0; y < y1; y++) {
      for (int x = x0; x < x1 - 1; x++) {
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

  private static void check(boolean ok, String message) {
    if (!ok) {
      throw new AssertionError(message);
    }
  }
}
