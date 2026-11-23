/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import io.github.thexykril.reizo.fabric.capture.LocalEffects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands the local player's own block effects to {@link LocalEffects} for recording. */
@Mixin(ClientLevel.class)
abstract class ClientLevelEffectsMixin {
  @Inject(method = "destroyBlockProgress", at = @At("HEAD"))
  private void reizo$progress(int breakerId, BlockPos pos, int progress, CallbackInfo ci) {
    LocalEffects.blockProgress(breakerId, pos, progress);
  }

  @Inject(method = "levelEvent", at = @At("HEAD"))
  private void reizo$levelEvent(Entity source, int type, BlockPos pos, int data, CallbackInfo ci) {
    LocalEffects.levelEvent(source, type, pos, data);
  }

  //? if >=26.3 {
  /*@Inject(method = "addBreakingBlockEffects", at = @At("HEAD"))
  private void reizo$breaking(BlockPos pos, Direction face, boolean sound, CallbackInfo ci) {
    LocalEffects.breakingEffects(pos, face, sound);
  }
  *///?} else {
  @Inject(method = "addBreakingBlockEffect", at = @At("HEAD"))
  private void reizo$breaking(BlockPos pos, Direction face, CallbackInfo ci) {
    LocalEffects.breakingParticles(((ClientLevel) (Object) this).getBlockState(pos), pos, face);
  }
  //?}
}
