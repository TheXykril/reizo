/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import net.minecraft.client.quickplay.QuickPlayLog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Launchers read Minecraft's Quick Play log ({@code --quickPlayPath}) to show the last played
 * world or server. A replay connects to Reizo's local replay server like a multiplayer server, so
 * without this it would replace the real last played entry. While a replay runs, the log keeps
 * what it had.
 */
@Mixin(QuickPlayLog.class)
abstract class QuickPlayLogMixin {
  @Inject(method = "setWorldData", at = @At("HEAD"), cancellable = true)
  private void reizo$skipReplayWorld(CallbackInfo ci) {
    if (ReplayPlayback.current() != null) {
      ci.cancel();
    }
  }

  @Inject(method = "log", at = @At("HEAD"), cancellable = true)
  private void reizo$skipReplayLog(CallbackInfo ci) {
    if (ReplayPlayback.current() != null) {
      ci.cancel();
    }
  }
}
