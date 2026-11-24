/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Minecraft ticks its pending connection until a world is loaded, as it does for a singleplayer
 * world. A replay joins the same way (ADR 0020) instead of through the connecting screen.
 */
@Mixin(Minecraft.class)
public interface MinecraftPendingConnectionAccessor {
  @Accessor("pendingConnection")
  void reizo$setPendingConnection(Connection connection);
}
