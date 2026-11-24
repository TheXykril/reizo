/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With a shader pack on, Iris draws the hand into the world's depth itself, before Minecraft's own
 * hand step. Depth of field keeps the world's depth just before that. Does nothing without Iris.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class IrisPipelineMixin {
  @Inject(method = "beginHand", at = @At("HEAD"), require = 0)
  private void reizo$keepDepth(CallbackInfo ci) {
    io.github.thexykril.reizo.fabric.playback.EffectRenderer.keepDepthBeforeShaderHand(
        Minecraft.getInstance());
  }
}
