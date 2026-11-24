/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import io.github.thexykril.reizo.fabric.compat.PolyPlusReplays;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds Reizo to OneClient's replay mods (PolyPlus); skipped when PolyPlus is not installed. */
@Pseudo
@Mixin(targets = "org.polyfrost.polyplus.compat.replay.ReplayProviders", remap = false)
abstract class PolyPlusReplayProvidersMixin {
  @Inject(method = "getAll", at = @At("RETURN"), cancellable = true, require = 0)
  private void reizo$addReizo(CallbackInfoReturnable<List<Object>> cir) {
    cir.setReturnValue(PolyPlusReplays.withReizo(cir.getReturnValue()));
  }
}
