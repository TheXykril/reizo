/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import io.github.thexykril.reizo.fabric.export.ExportSession;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** During a video export, every sound that starts is also recorded for the export's sound track. */
@Mixin(SoundManager.class)
abstract class SoundManagerMixin {
  @Inject(method = "play", at = @At("HEAD"))
  private void reizo$exportSound(
      SoundInstance sound,
      CallbackInfoReturnable<net.minecraft.client.sounds.SoundEngine.PlayResult> cir) {
    ExportSession.onSound(sound);
  }
}
