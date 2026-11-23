/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import io.github.thexykril.reizo.fabric.export.ExportSession;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * During a video export, every sound the game stops (a minecart leaving, music being replaced) ends
 * there in the export's sound track too.
 */
@Mixin(SoundEngine.class)
abstract class SoundEngineMixin {
  @Inject(method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"))
  private void reizo$exportSoundStopped(SoundInstance sound, CallbackInfo ci) {
    ExportSession.onSoundStopped(sound);
  }
}
