/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import io.github.thexykril.reizo.fabric.playback.ReplayEditor;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a replay's camera path plays, its field of view replaces the player's. During a replay the
 * project's effects are applied to the world picture before the HUD is drawn, and its keyframed
 * settings are set before each frame.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
  @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
  private void reizo$pathFov(
      Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
    float fov = ReplayEditor.fovNow();
    if (!Float.isNaN(fov) && useFovSetting) {
      cir.setReturnValue(fov);
    }
  }

  @Inject(method = "render", at = @At("HEAD"))
  private void reizo$settings(
      net.minecraft.client.DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
    ReplayEditor.applySettings();
  }

  @Inject(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V",
              shift = At.Shift.AFTER))
  private void reizo$effects(
      net.minecraft.client.DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
    io.github.thexykril.reizo.fabric.playback.EffectRenderer.afterWorld(
        net.minecraft.client.Minecraft.getInstance());
  }
}
