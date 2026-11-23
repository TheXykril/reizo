/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

//? if >=26.1 {
/*import io.github.thexykril.reizo.fabric.playback.ReplayWorldLook;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}
import org.spongepowered.asm.mixin.Mixin;

/**
 * 26.x drives the sky, light and moon from world clocks instead of a day time: the time of day
 * chosen in the replay editor is shown by changing what the client's clocks report. The clocks
 * themselves keep counting the recorded time. Before 26.1 the day time is changed in {@link
 * LevelWeatherMixin} and this mixin is empty.
 */
//? if >=26.3 {
/*@Mixin(net.minecraft.client.ClientClockManager.ClientClockInstance.class)
abstract class ClockTimeMixin {
  @Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
  private void reizo$timeOfDay(CallbackInfoReturnable<Long> cir) {
    cir.setReturnValue(ReplayWorldLook.dayTime(cir.getReturnValueJ()));
  }
}
*///?} else if >=26.1 {
/*@Mixin(net.minecraft.client.ClientClockManager.class)
abstract class ClockTimeMixin {
  @Inject(method = "getTotalTicks", at = @At("RETURN"), cancellable = true)
  private void reizo$timeOfDay(
      net.minecraft.core.Holder<net.minecraft.world.clock.WorldClock> clock,
      CallbackInfoReturnable<Long> cir) {
    cir.setReturnValue(ReplayWorldLook.dayTime(cir.getReturnValueJ()));
  }
}
*///?} else {
@Mixin(net.minecraft.client.Minecraft.class)
abstract class ClockTimeMixin {}
//?}
