/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.capture;

import io.github.thexykril.reizo.core.format.LocalPlayerState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/** Converts the local player into the version-independent {@link LocalPlayerState}. */
public final class LocalPlayerSampler {
  private LocalPlayerState last;

  /** Returns the state to record this tick, or null if nothing changed since the last sample. */
  public LocalPlayerState sample(LocalPlayer player) {
    int flags = 0;
    if (player.onGround()) {
      flags |= LocalPlayerState.FLAG_ON_GROUND;
    }
    if (player.isSprinting()) {
      flags |= LocalPlayerState.FLAG_SPRINTING;
    }
    if (player.isShiftKeyDown()) {
      flags |= LocalPlayerState.FLAG_SNEAKING;
    }
    if (player.isUsingItem()) {
      flags |= LocalPlayerState.FLAG_USING_ITEM;
    }
    //? if >=26.1 {
    /*var swing = player.getCurrentSwing();
    boolean swinging = swing != null;
    boolean offHand = swinging && swing.hand() == InteractionHand.OFF_HAND;
    // 26.x keeps the swing tick count private; its progress (0 to 1) restarts the same way, scaled to fit the byte the format stores.
    int swingTime = Math.round(player.getSwingAnimation(1f) * 250);
    *///?} else {
    boolean swinging = player.swinging;
    boolean offHand = player.swingingArm == InteractionHand.OFF_HAND;
    int swingTime = player.swingTime;
    //?}
    if (swinging) {
      flags |= LocalPlayerState.FLAG_SWINGING;
      if (offHand) {
        flags |= LocalPlayerState.FLAG_SWING_OFFHAND;
      }
    }
    LocalPlayerState state =
        new LocalPlayerState(
            player.getId(),
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getYRot(),
            player.getXRot(),
            player.getYHeadRot(),
            player.yBodyRot,
            player.getPose().ordinal(),
            flags,
            swingTime);
    if (state.equals(last)) {
      return null; // standing still: no need to write a frame every tick
    }
    last = state;
    return state;
  }

  public void reset() {
    last = null;
  }
}
