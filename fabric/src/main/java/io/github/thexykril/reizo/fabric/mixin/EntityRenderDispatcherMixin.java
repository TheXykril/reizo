/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.thexykril.reizo.fabric.playback.ReplayEntityHider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entities hidden in the replay editor are not drawn (with their shadow and name tag). */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
  // The entity is taken by type because 26.3 added a parameter after the camera position.
  @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
  private void reizo$hidden(
      CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) Entity entity) {
    if (ReplayEntityHider.isHidden(entity)) {
      cir.setReturnValue(false);
    }
  }
}
