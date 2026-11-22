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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While a replay's camera path plays, its field of view replaces the player's. */
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
}
