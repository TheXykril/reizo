/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import io.github.thexykril.reizo.fabric.playback.ReplayJoin;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A replay runs over a Netty local channel (ADR 0020), which the game would take for its
 * integrated server: it would then skip applying the tags the replay sends, among other things. The
 * replay is a real server to the client, so its connection is not a memory connection.
 */
@Mixin(Connection.class)
abstract class ConnectionMixin {
  @Inject(method = "isMemoryConnection", at = @At("HEAD"), cancellable = true)
  private void reizo$replayIsRemote(CallbackInfoReturnable<Boolean> cir) {
    if (ReplayJoin.isReplay((Connection) (Object) this)) {
      cir.setReturnValue(false);
    }
  }
}
