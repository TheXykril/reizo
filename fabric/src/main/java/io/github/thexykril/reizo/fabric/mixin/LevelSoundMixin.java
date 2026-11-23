/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import io.github.thexykril.reizo.fabric.capture.LocalEffects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sounds the client plays for the local player ahead of the server (placing a block, for one). Every
 * {@code playSound} that names an excepted entity ends here; sounds from the server do not, since
 * they are played from the packet directly.
 */
@Mixin(Level.class)
abstract class LevelSoundMixin {
  @Inject(
      method =
          "playSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FF)V",
      at = @At("HEAD"))
  private void reizo$sound(
      Entity except,
      double x,
      double y,
      double z,
      Holder<SoundEvent> sound,
      SoundSource source,
      float volume,
      float pitch,
      CallbackInfo ci) {
    if ((Object) this instanceof ClientLevel) {
      LocalEffects.sound(except, x, y, z, sound, source, volume, pitch);
    }
  }
}
