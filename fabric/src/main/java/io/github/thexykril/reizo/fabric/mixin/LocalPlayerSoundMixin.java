/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import io.github.thexykril.reizo.fabric.capture.LocalEffects;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sounds the local player makes for themselves only, such as footsteps: the server plays them to
 * everyone else.
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerSoundMixin {
  @Inject(method = "playSound", at = @At("HEAD"))
  private void reizo$sound(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
    LocalPlayer self = (LocalPlayer) (Object) this;
    LocalEffects.playerSound(self, sound, self.getSoundSource(), volume, pitch);
  }
}
