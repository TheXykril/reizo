/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import io.github.thexykril.reizo.core.camera.CameraKeyframe;
import io.github.thexykril.reizo.fabric.playback.ReplayEditor;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a replay's camera path plays, the camera is placed from the path every frame (at the exact
 * replay time, so motion is smooth at any frame rate) and rolled by the path's roll.
 */
@Mixin(Camera.class)
abstract class CameraMixin {
  @Shadow
  protected abstract void setPosition(double x, double y, double z);

  @Shadow
  protected abstract void setRotation(float yRot, float xRot);

  //? if >=26.1 {
  /*@Inject(method = "alignWithEntity", at = @At("TAIL"))
  private void reizo$applyCameraPath(float partialTicks, CallbackInfo ci) {
  *///?} else {
  @Inject(method = "setup", at = @At("TAIL"))
  private void reizo$applyCameraPath(
      //? if >=1.21.11 {
      Level level,
      //?} else {
      /*net.minecraft.world.level.BlockGetter level,
      *///?}
      Entity entity,
      boolean detached,
      boolean mirrored,
      float partialTick,
      CallbackInfo ci) {
    //?}
    CameraKeyframe k = ReplayEditor.cameraNow();
    if (k != null) {
      setRotation(k.yaw(), k.pitch());
      setPosition(k.x(), k.y(), k.z());
    }
  }

  //? if >=26.1 {
  /*// 26.x computes the field of view in the camera (1.21.x does it in GameRendererMixin).
  @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
  private void reizo$pathFov(
      float partialTicks,
      org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
    float fov = ReplayEditor.fovNow();
    if (!Float.isNaN(fov)) {
      cir.setReturnValue(fov);
    }
  }
  *///?}

  @ModifyArg(
      method = "setRotation",
      at =
          @At(
              value = "INVOKE",
              target = "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;"),
      index = 2)
  private float reizo$roll(float roll) {
    float r = ReplayEditor.rollNow();
    return Float.isNaN(r) ? roll : (float) Math.toRadians(-r);
  }
}
