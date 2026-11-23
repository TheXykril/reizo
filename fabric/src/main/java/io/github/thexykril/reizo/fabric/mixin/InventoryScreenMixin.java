/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import io.github.thexykril.reizo.fabric.ui.PlayerScreenOverlay;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A replayed inventory screen shows the recorded player's model, not the replay camera (the
 * vanilla screen always draws the local player).
 */
@Mixin(InventoryScreen.class)
abstract class InventoryScreenMixin {
  // GuiGraphics in the descriptor is renamed for 26.x by the build.
  //? if >=26.1 {
  /*@ModifyArg(
      method = "extractBackground",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"),
      index = 9)
  *///?} else {
  @ModifyArg(
      method = "renderBg",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"),
      index = 9)
  //?}
  private LivingEntity reizo$recordedPlayer(LivingEntity entity) {
    LivingEntity recorded = PlayerScreenOverlay.entityFor(this);
    return recorded != null ? recorded : entity;
  }
}
