/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import io.github.thexykril.reizo.fabric.playback.ReplayWorldLook;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The weather (and, before 26.1, the time of day) chosen in the replay editor, in place of the
 * recorded ones. Only the client's level in a replay is affected; see {@link ReplayWorldLook}.
 */
@Mixin(Level.class)
abstract class LevelWeatherMixin {
  @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
  private void reizo$rain(float partialTick, CallbackInfoReturnable<Float> cir) {
    if ((Object) this instanceof ClientLevel) {
      cir.setReturnValue(ReplayWorldLook.rain(cir.getReturnValueF()));
    }
  }

  @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
  private void reizo$thunder(float partialTick, CallbackInfoReturnable<Float> cir) {
    if ((Object) this instanceof ClientLevel && ReplayWorldLook.thunderOverridden()) {
      // Minecraft's thunder level is the thunder strength scaled by the rain level.
      cir.setReturnValue(ReplayWorldLook.thunder() * ((Level) (Object) this).getRainLevel(partialTick));
    }
  }

  //? if <26.1 {
  @Inject(method = "getDayTime", at = @At("RETURN"), cancellable = true)
  private void reizo$dayTime(CallbackInfoReturnable<Long> cir) {
    if ((Object) this instanceof ClientLevel) {
      cir.setReturnValue(ReplayWorldLook.dayTime(cir.getReturnValueJ()));
    }
  }
  //?}
}
