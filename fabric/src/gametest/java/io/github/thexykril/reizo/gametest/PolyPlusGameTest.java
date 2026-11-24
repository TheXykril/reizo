/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.gametest;

import io.github.thexykril.reizo.core.format.FrameStreamWriter;
import io.github.thexykril.reizo.core.format.RecordingMetadata;
import io.github.thexykril.reizo.core.storage.RecordingStorage;
import io.github.thexykril.reizo.fabric.RecorderClient;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OneClient's Replays page (ADR 0019): Reizo's recordings, with thumbnails, in PolyPlus's replay
 * list. Runs only when PolyPlus is staged with REIZO_EXTRA_MODS, which CI does not do.
 *
 * <p>PolyPlus replaces the title screen with its lobby, and the gametest framework expects every
 * test to end on the title screen, so such a run always ends with that framework error after this
 * test's "OK" line. Read the log line, not the exit code.
 */
public final class PolyPlusGameTest implements FabricClientGameTest {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/GameTest");

  @Override
  public void runTest(ClientGameTestContext context) {
    if (!FabricLoader.getInstance().isModLoaded("polyplus")) {
      return;
    }
    // PolyPlus also replaces the world creation screen, so the test cannot open a world here: it
    // stores a small recording with a thumbnail directly (capturing one is checked by the main
    // suite).
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    context.runOnClient(
        mc -> {
          try {
            RecordingStorage storage = RecorderClient.get().storage();
            RecordingMetadata meta =
                RecordingMetadata.create("PolyPlus check", System.currentTimeMillis());
            meta.minecraftVersion = SharedConstants.getCurrentVersion().name();
            meta.protocolVersion = SharedConstants.getProtocolVersion();
            RecordingStorage.WorkingDirectory working = storage.createWorking(meta);
            FrameStreamWriter.create(working.streamFile(), 1024).close();
            java.nio.file.Files.write(working.thumbnailFile(), png);
            working.close();
            storage.finalizeRecording(working.dir());
          } catch (java.io.IOException e) {
            throw new AssertionError(e);
          }
        });
    String result =
        context.computeOnClient(
            mc -> {
              try {
                Class<?> providers =
                    Class.forName("org.polyfrost.polyplus.compat.replay.ReplayProviders");
                Object instance = providers.getField("INSTANCE").get(null);
                List<?> all = (List<?>) providers.getMethod("getAll").invoke(instance);
                Object reizo = null;
                for (Object p : all) {
                  if ("reizo".equals(p.getClass().getMethod("getId").invoke(p))) {
                    reizo = p;
                  }
                }
                check(reizo != null, "Reizo is not in PolyPlus's providers: " + all);
                check(
                    (Boolean) reizo.getClass().getMethod("getAvailable").invoke(reizo),
                    "Reizo provider is not available");
                List<?> entries = (List<?>) reizo.getClass().getMethod("list").invoke(reizo);
                check(!entries.isEmpty(), "Reizo provider lists no recordings");
                Object first = entries.get(0);
                byte[] icon = (byte[]) first.getClass().getMethod("readIcon").invoke(first);
                check(
                    java.util.Arrays.equals(icon, png),
                    "PolyPlus did not get the recording's thumbnail");
                check(
                    (Boolean) first.getClass().getMethod("getPlayable").invoke(first),
                    "a recording from this version is not playable");
                return all.size()
                    + " providers, "
                    + entries.size()
                    + " recordings, first \""
                    + first.getClass().getMethod("getName").invoke(first)
                    + "\" with a "
                    + icon.length
                    + " byte thumbnail";
              } catch (ReflectiveOperationException e) {
                throw new AssertionError("PolyPlus replay API changed", e);
              }
            });
    LOGGER.info("checkPolyPlusReplays OK: {}", result);
  }

  private static void check(boolean ok, String message) {
    if (!ok) {
      throw new AssertionError(message);
    }
  }
}
