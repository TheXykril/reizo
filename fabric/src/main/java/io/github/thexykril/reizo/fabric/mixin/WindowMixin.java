/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import com.mojang.blaze3d.platform.Window;
import io.github.thexykril.reizo.core.export.RenderSize;
import io.github.thexykril.reizo.fabric.export.ExportSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * During an export at a set output size, the framebuffer size the game sees is the output size, so
 * the main render target, the projection and every post-processing target are made at that size.
 * The real window keeps its size; only {@code Minecraft.resizeDisplay} and the renderer read these
 * getters.
 */
@Mixin(Window.class)
abstract class WindowMixin {
  @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
  private void reizo$exportWidth(CallbackInfoReturnable<Integer> cir) {
    RenderSize size = ExportSession.renderOverride();
    if (size != null) {
      cir.setReturnValue(size.width());
    }
  }

  @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
  private void reizo$exportHeight(CallbackInfoReturnable<Integer> cir) {
    RenderSize size = ExportSession.renderOverride();
    if (size != null) {
      cir.setReturnValue(size.height());
    }
  }
}
