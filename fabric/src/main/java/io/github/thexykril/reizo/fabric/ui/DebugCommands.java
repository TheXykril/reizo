/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.thexykril.reizo.core.recording.RecordingSession;
import io.github.thexykril.reizo.fabric.RecorderClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

/**
 * Client-only debug commands for scripted testing ({@code /reizo-debug ...}). Not the user
 * interface: users record with key bindings and, later, the pause menu and editor. Registered only
 * in a development environment or when {@code -Dreizo.debug=true} is set.
 */
public final class DebugCommands {
  private DebugCommands() {}

  public static void register(RecorderClient client) {
    if (!FabricLoader.getInstance().isDevelopmentEnvironment()
        && !Boolean.getBoolean(RecorderClient.MOD_ID + ".debug")) {
      return;
    }
    ClientCommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess) ->
            dispatcher.register(
                ClientCommandManager.literal(RecorderClient.MOD_ID + "-debug")
                    .then(
                        ClientCommandManager.literal("start")
                            .executes(
                                ctx -> {
                                  client.startRecording();
                                  return 1;
                                }))
                    .then(
                        ClientCommandManager.literal("stop")
                            .executes(
                                ctx -> {
                                  client.stopRecording();
                                  return 1;
                                }))
                    .then(
                        ClientCommandManager.literal("pause")
                            .executes(
                                ctx -> {
                                  client.togglePause();
                                  return 1;
                                }))
                    .then(
                        ClientCommandManager.literal("marker")
                            .then(
                                ClientCommandManager.argument(
                                        "label", StringArgumentType.greedyString())
                                    .executes(
                                        ctx -> {
                                          client.addMarker(
                                              StringArgumentType.getString(ctx, "label"));
                                          return 1;
                                        })))
                    .then(
                        ClientCommandManager.literal("status")
                            .executes(
                                ctx -> {
                                  RecordingSession s = client.session();
                                  ctx.getSource()
                                      .sendFeedback(
                                          Component.literal(
                                              s == null
                                                  ? "Not recording"
                                                  : s.state()
                                                      + " "
                                                      + s.elapsedMillis()
                                                      + " ms, "
                                                      + s.submittedFrames()
                                                      + " frames, "
                                                      + s.droppedFrames()
                                                      + " dropped, "
                                                      + s.bytesOnDisk()
                                                      + " bytes"));
                                  return 1;
                                }))));
  }
}
