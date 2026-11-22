/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import io.github.thexykril.reizo.fabric.capture.PacketTap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.ProtocolInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the exact bytes of every packet the client decodes, after decompression and decryption.
 *
 * <p>Hooking the decoder (rather than the packet handler) has two advantages: the bytes come from
 * the server unchanged, so nothing is re-encoded, and the decoder instance knows its own protocol
 * phase, so packets around a configuration/play switch are never attributed to the wrong phase.
 * Singleplayer goes through the same decoder: the integrated server's in-memory channel still
 * serializes packets.
 *
 * <p>Runs on the network thread. One decoder instance belongs to one channel, which Netty drives
 * from a single thread, so the {@code @Unique} field needs no synchronization.
 */
@Mixin(PacketDecoder.class)
public abstract class PacketDecoderMixin {
  @Shadow @Final private ProtocolInfo<?> protocolInfo;

  @Unique private int recorder$packetStart = -1;

  @Inject(method = "decode", at = @At("HEAD"))
  private void recorder$markStart(
      ChannelHandlerContext ctx, ByteBuf buf, List<Object> out, CallbackInfo ci) {
    recorder$packetStart = PacketTap.wants(protocolInfo) ? buf.readerIndex() : -1;
  }

  @Inject(
      method = "decode",
      at =
          @At(
              value = "INVOKE",
              target = "Ljava/util/List;add(Ljava/lang/Object;)Z",
              shift = At.Shift.BEFORE))
  private void recorder$capture(
      ChannelHandlerContext ctx, ByteBuf buf, List<Object> out, CallbackInfo ci) {
    int start = recorder$packetStart;
    if (start < 0) {
      return;
    }
    recorder$packetStart = -1;
    int length = buf.readerIndex() - start;
    if (length <= 0) {
      return;
    }
    byte[] bytes = new byte[length];
    buf.getBytes(start, bytes);
    PacketTap.captured(protocolInfo, bytes);
  }
}
