/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.capture;

import io.github.thexykril.reizo.core.format.PlayerHudState;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Records what the local player's HUD shows: hotbar, selected slot, health, food, armor,
 * experience and air. The server does not reliably send these (creative inventories and the
 * selected slot are the client's own), so they are sampled from the client each tick and written
 * as a {@code PLAYER_HUD} frame when anything changed. Client thread only.
 */
public final class HudSampler {
  /** Hotbar slots 0 to 8, then the offhand. */
  public static final int ITEM_COUNT = Inventory.getSelectionSize() + 1;

  private final ItemStack[] lastItems = new ItemStack[ITEM_COUNT];
  private byte[] lastItemBytes;
  private RegistryAccess itemRegistries;
  private PlayerHudState last;

  /** The payload to record this tick, or null when the HUD did not change. */
  public byte[] sample(Minecraft mc, LocalPlayer player, RegistryAccess access) {
    byte[] items = items(player, access);
    var food = player.getFoodData();
    var mode = mc.gameMode == null ? null : mc.gameMode.getPlayerMode();
    PlayerHudState state =
        new PlayerHudState(
            player.getInventory().getSelectedSlot(),
            mode == null ? 0 : mode.getId(),
            player.level().getLevelData().isHardcore() ? PlayerHudState.FLAG_HARDCORE : 0,
            player.getHealth(),
            player.getMaxHealth(),
            player.getAbsorptionAmount(),
            food.getFoodLevel(),
            food.getSaturationLevel(),
            player.getArmorValue(),
            player.experienceLevel,
            player.experienceProgress,
            player.getAirSupply(),
            player.getMaxAirSupply(),
            items);
    if (state.equals(last)) {
      return null;
    }
    last = state;
    return state.encode();
  }

  /** Forgets what was written, so the next sample writes the HUD again. */
  public void reset() {
    last = null;
    java.util.Arrays.fill(lastItems, null);
    lastItemBytes = null;
    itemRegistries = null;
  }

  private byte[] items(LocalPlayer player, RegistryAccess access) {
    Inventory inventory = player.getInventory();
    boolean changed = lastItemBytes == null || access != itemRegistries;
    for (int i = 0; i < ITEM_COUNT; i++) {
      ItemStack now = i < ITEM_COUNT - 1 ? inventory.getItem(i) : player.getOffhandItem();
      if (lastItems[i] == null || !ItemStack.matches(lastItems[i], now)) {
        lastItems[i] = now.copy();
        changed = true;
      }
    }
    if (changed) {
      lastItemBytes = encodeItems(List.of(lastItems), access);
      itemRegistries = access;
    }
    return lastItemBytes;
  }

  /** Encodes stacks with the game's network codec (registry-aware, so ids survive). */
  public static byte[] encodeItems(List<ItemStack> stacks, RegistryAccess access) {
    ByteBuf raw = Unpooled.buffer();
    try {
      RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(raw, access);
      buf.writeVarInt(stacks.size());
      for (ItemStack stack : stacks) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
      }
      byte[] bytes = new byte[raw.readableBytes()];
      raw.readBytes(bytes);
      return bytes;
    } finally {
      raw.release();
    }
  }

  /** Reverses {@link #encodeItems}. */
  public static List<ItemStack> decodeItems(byte[] bytes, RegistryAccess access) {
    ByteBuf raw = Unpooled.wrappedBuffer(bytes);
    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(raw, access);
    int count = buf.readVarInt();
    java.util.ArrayList<ItemStack> out = new java.util.ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      out.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
    }
    return out;
  }
}
