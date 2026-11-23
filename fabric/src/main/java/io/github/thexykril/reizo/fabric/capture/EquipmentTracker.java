/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.capture;

import com.mojang.datafixers.util.Pair;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Records what the local player holds and wears. A server never sends a player their own equipment
 * (only their inventory), so the recording would show the recorded player empty-handed. Each change
 * becomes a vanilla equipment packet for the local player's entity, stored like a captured play
 * packet; playback needs nothing special for it. Client thread only.
 */
public final class EquipmentTracker {
  private static final EquipmentSlot[] SLOTS = {
    EquipmentSlot.MAINHAND,
    EquipmentSlot.OFFHAND,
    EquipmentSlot.FEET,
    EquipmentSlot.LEGS,
    EquipmentSlot.CHEST,
    EquipmentSlot.HEAD
  };

  private final ItemStack[] last = new ItemStack[SLOTS.length];
  private Object level;
  private int entityId = -1;
  private RegistryAccess registries;
  private ProtocolInfo<ClientGamePacketListener> protocol;

  /**
   * The packet to record this tick, or null when nothing changed. Call after the tick's local
   * player sample, so playback has added the recorded player before the packet arrives. A new
   * entity or level (respawn, dimension change) sends everything again.
   */
  public byte[] sample(LocalPlayer player, RegistryAccess access) {
    if (player.getId() != entityId || player.level() != level) {
      reset();
      entityId = player.getId();
      level = player.level();
    }
    List<Pair<EquipmentSlot, ItemStack>> changed = new ArrayList<>();
    for (int i = 0; i < SLOTS.length; i++) {
      ItemStack now = player.getItemBySlot(SLOTS[i]);
      if (last[i] == null || !ItemStack.matches(last[i], now)) {
        last[i] = now.copy();
        changed.add(Pair.of(SLOTS[i], now.copy()));
      }
    }
    if (changed.isEmpty()) {
      return null;
    }
    if (access != registries) {
      registries = access;
      protocol = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(access));
    }
    ByteBuf buf = Unpooled.buffer();
    try {
      protocol.codec().encode(buf, new ClientboundSetEquipmentPacket(entityId, changed));
      byte[] bytes = new byte[buf.readableBytes()];
      buf.readBytes(bytes);
      return bytes;
    } finally {
      buf.release();
    }
  }

  /** Forgets what was sent, so the next sample sends everything. */
  public void reset() {
    java.util.Arrays.fill(last, null);
    entityId = -1;
    level = null;
  }
}
