/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.playback;

import io.github.thexykril.reizo.fabric.mixin.MinecraftPendingConnectionAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;

/**
 * Joins a replay in memory (ADR 0020), the way the game joins a singleplayer world: no connecting
 * screen and no network address. The client shows "Loading replay" until the recorded world
 * arrives, then the game's own terrain loading screen.
 */
public final class ReplayJoin {
  private ReplayJoin() {}

  /** Whether {@code connection} is the client's end of a replay. Any thread. */
  public static boolean isReplay(Connection connection) {
    return ReplayChannel.isReplayAddress(connection.getRemoteAddress());
  }

  /** Connects the client to {@code server}. Client thread, with no world loaded. */
  static void join(Minecraft mc, ReplayServer server, ServerData data) {
    mc.updateReportEnvironment(ReportEnvironment.thirdParty(data.ip));
    mc.setScreen(new GenericMessageScreen(Component.translatable("reizo.replay.loading")));
    Connection connection = ReplayChannel.connect(server.memoryAddress());
    // As the connecting screen does for a server whose pack setting was never chosen.
    mc.getDownloadedPackSource()
        .configureForServerControl(connection, ServerPackManager.PackPromptStatus.PENDING);
    connection.initiateServerboundPlayConnection(
        data.ip,
        0,
        new ClientHandshakePacketListenerImpl(
            connection,
            mc,
            data,
            new TitleScreen(),
            false,
            null,
            status -> {},
            new LevelLoadTracker(),
            null));
    connection.send(
        new ServerboundHelloPacket(mc.getUser().getName(), mc.getUser().getProfileId()));
    ((MinecraftPendingConnectionAccessor) mc).reizo$setPendingConnection(connection);
  }
}
