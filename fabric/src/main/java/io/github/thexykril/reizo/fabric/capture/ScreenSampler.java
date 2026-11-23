/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.capture;

import io.github.thexykril.reizo.core.format.PlayerScreenState;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Records the inventory or container screen the local player has open: which screen, its slots,
 * the stack on the cursor and the mouse position. The player's own inventory screen exists only on
 * their client, so the server's packets cannot tell. Written as a {@code PLAYER_SCREEN} frame when
 * anything changes; the slots only when they changed. Client thread only.
 */
public final class ScreenSampler {
  private PlayerScreenState last;
  private List<ItemStack> lastSlots = List.of();
  private RegistryAccess slotRegistries;

  /** The payload to record this tick, or null when nothing changed. */
  public byte[] sample(Minecraft mc, RegistryAccess access) {
    PlayerScreenState now = current(mc, access);
    boolean unchanged =
        now.equals(last)
            || (now.items() == null
                && now.sameScreen(last)
                && now.mouseX() == last.mouseX()
                && now.mouseY() == last.mouseY());
    if (unchanged) {
      return null;
    }
    last = now;
    return now.encode();
  }

  /** Forgets what was written, so the next sample writes the screen in full. */
  public void reset() {
    last = null;
    lastSlots = List.of();
    slotRegistries = null;
  }

  private PlayerScreenState current(Minecraft mc, RegistryAccess access) {
    if (!(mc.screen instanceof AbstractContainerScreen<?> screen) || mc.player == null) {
      return closed();
    }
    int kind;
    String type = "";
    byte[] title = new byte[0];
    AbstractContainerMenu menu = screen.getMenu();
    if (screen instanceof CreativeModeInventoryScreen) {
      // The creative tabs are drawn by the client from its own item list; replays show the
      // survival inventory with the same slots.
      kind = PlayerScreenState.CREATIVE;
      menu = mc.player.inventoryMenu;
    } else if (menu instanceof InventoryMenu) {
      kind = PlayerScreenState.INVENTORY;
    } else {
      net.minecraft.resources.Identifier id;
      try {
        id = BuiltInRegistries.MENU.getKey(menu.getType());
      } catch (UnsupportedOperationException e) {
        return closed(); // a screen without a menu type (horse and other mount screens)
      }
      if (id == null) {
        return closed();
      }
      kind = PlayerScreenState.CONTAINER;
      type = id.toString();
      title = encodeTitle(screen.getTitle(), access);
    }
    List<ItemStack> slots = new ArrayList<>(menu.slots.size() + 1);
    for (Slot slot : menu.slots) {
      slots.add(slot.getItem());
    }
    slots.add(screen.getMenu().getCarried());
    byte[] items = null;
    PlayerScreenState screenOnly =
        new PlayerScreenState(kind, type, title, 0, 0, null);
    if (last == null
        || !screenOnly.sameScreen(last)
        || access != slotRegistries
        || changed(slots)) {
      lastSlots = new ArrayList<>(slots.size());
      for (ItemStack stack : slots) {
        lastSlots.add(stack.copy());
      }
      slotRegistries = access;
      items = HudSampler.encodeItems(lastSlots, access);
    }
    var window = mc.getWindow();
    float mouseX = fraction(mc.mouseHandler.xpos(), window.getScreenWidth());
    float mouseY = fraction(mc.mouseHandler.ypos(), window.getScreenHeight());
    return new PlayerScreenState(kind, type, title, mouseX, mouseY, items);
  }

  private PlayerScreenState closed() {
    lastSlots = List.of();
    return PlayerScreenState.CLOSED;
  }

  private boolean changed(List<ItemStack> slots) {
    if (slots.size() != lastSlots.size()) {
      return true;
    }
    for (int i = 0; i < slots.size(); i++) {
      if (!ItemStack.matches(slots.get(i), lastSlots.get(i))) {
        return true;
      }
    }
    return false;
  }

  /** Mouse position as a fraction of the window, rounded so sub-pixel jitter writes nothing. */
  private static float fraction(double pos, int size) {
    if (size <= 0) {
      return 0;
    }
    return Math.round(Math.max(0, Math.min(1, pos / size)) * 2000f) / 2000f;
  }

  public static byte[] encodeTitle(Component title, RegistryAccess access) {
    ByteBuf raw = Unpooled.buffer();
    try {
      ComponentSerialization.TRUSTED_STREAM_CODEC.encode(
          new RegistryFriendlyByteBuf(raw, access), title);
      byte[] bytes = new byte[raw.readableBytes()];
      raw.readBytes(bytes);
      return bytes;
    } finally {
      raw.release();
    }
  }

  public static Component decodeTitle(byte[] bytes, RegistryAccess access) {
    if (bytes.length == 0) {
      return Component.empty();
    }
    return ComponentSerialization.TRUSTED_STREAM_CODEC.decode(
        new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), access));
  }
}
