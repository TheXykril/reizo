/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import io.github.thexykril.reizo.fabric.export.ExportSession;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * During a video export, Minecraft's timer runs on the export's virtual clock (one frame duration
 * per captured frame) and every rendered frame is offered to the export.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
  //? if >=26.1 {
  /*@ModifyArg(
      method = "runTick",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/DeltaTracker$Timer;advanceGameTime(J)I"),
      index = 0)
  private long reizo$exportClock(long realMillis) {
    return ExportSession.timerMillis(realMillis);
  }

  // 26.x advances the real-time clock separately, in renderFrame; it follows the same clock.
  @ModifyArg(
      method = "renderFrame",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/DeltaTracker$Timer;advanceRealTime(J)V"),
      index = 0)
  private long reizo$exportRealClock(long realMillis) {
    return ExportSession.lastTimerMillis(realMillis);
  }
  *///?} else {
  @ModifyArg(
      method = "runTick",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/DeltaTracker$Timer;advanceTime(JZ)I"),
      index = 0)
  private long reizo$exportClock(long realMillis) {
    return ExportSession.timerMillis(realMillis);
  }
  //?}

  @Inject(method = "runTick", at = @At("TAIL"))
  private void reizo$afterFrame(boolean advanceGameTime, CallbackInfo ci) {
    ExportSession.afterFrame((Minecraft) (Object) this);
  }
}
