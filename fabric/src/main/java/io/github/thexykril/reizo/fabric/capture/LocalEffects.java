/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.capture;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Records what the local player's own client shows of their actions but a server never sends back
 * to them: block breaking progress (the cracks), the break particles and sound, the particles and
 * hit sound while mining, and sounds the client plays ahead of the server (placing blocks, footsteps). A
 * server sends all of these to the other players only, so without this a replay shows the recorded
 * player breaking and placing blocks silently, with no cracks or particles.
 *
 * <p>Each one becomes the vanilla packet another player would have received, stored like a
 * captured play packet (as {@link EquipmentTracker} does), so playback needs nothing special. The
 * mixins call the static methods here; {@link #drain} hands the packets to the recorder once per
 * tick. Client thread only.
 */
public final class LocalEffects {
  /** More than any tick produces; protects memory if nothing drains (no recording running). */
  private static final int MAX_PENDING = 512;

  private static final List<Packet<? super ClientGamePacketListener>> pending = new ArrayList<>();
  private static volatile boolean enabled;
  private static boolean mining;
  private static BlockPos lastProgressPos;
  private static int lastProgress = Integer.MIN_VALUE;

  private static RegistryAccess registries;
  private static ProtocolInfo<ClientGamePacketListener> protocol;

  private LocalEffects() {}

  /** Whether anything records (a recording or the shadow buffer). Effects are dropped otherwise. */
  public static void setEnabled(boolean value) {
    enabled = value;
    if (!value) {
      pending.clear();
    }
  }

  /** A crack stage set by the local player's mining ({@code ClientLevel.destroyBlockProgress}). */
  public static void blockProgress(int breakerId, BlockPos pos, int progress) {
    Entity player = localPlayer();
    if (player == null || breakerId != player.getId()) {
      return; // another player's cracks: the server sent them, so they are recorded already
    }
    // Mining sets the same stage every tick; a stage change is enough.
    if (progress == lastProgress && pos.equals(lastProgressPos)) {
      return;
    }
    lastProgress = progress;
    lastProgressPos = pos.immutable();
    add(new ClientboundBlockDestructionPacket(breakerId, lastProgressPos, progress));
  }

  /** A level event the local player caused, such as breaking a block (particles and sound). */
  public static void levelEvent(Entity source, int type, BlockPos pos, int data) {
    if (source == null || source != localPlayer()) {
      return; // server events reach the client with no source and are recorded already
    }
    add(new ClientboundLevelEventPacket(type, pos.immutable(), data, false));
  }

  /** A sound the client plays ahead of the server, such as placing a block. */
  public static void sound(
      Entity except,
      double x,
      double y,
      double z,
      Holder<SoundEvent> sound,
      SoundSource source,
      float volume,
      float pitch) {
    if (except == null || except != localPlayer()) {
      return;
    }
    add(new ClientboundSoundPacket(sound, source, x, y, z, volume, pitch, seed()));
  }

  /** A sound the local player makes for themselves only, such as their own footsteps. */
  public static void playerSound(Entity player, SoundEvent sound, SoundSource source, float volume, float pitch) {
    if (player != localPlayer()) {
      return;
    }
    add(
        new ClientboundSoundPacket(
            net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
            source,
            player.getX(),
            player.getY(),
            player.getZ(),
            volume,
            pitch,
            seed()));
  }

  /** Set while the client mines a block ({@code Minecraft.continueAttack}). */
  public static void setMining(boolean value) {
    mining = value;
  }

  /**
   * A sound started while mining: the hit sound of the block being mined, which the client plays
   * for itself only (before 26.3).
   */
  public static void miningSound(double x, double y, double z) {
    Minecraft mc = Minecraft.getInstance();
    if (!mining || mc.level == null || localPlayer() == null) {
      return;
    }
    BlockPos pos = BlockPos.containing(x, y, z);
    BlockState state = mc.level.getBlockState(pos);
    if (state.isAir()) {
      return;
    }
    var type = state.getSoundType();
    add(
        new ClientboundSoundPacket(
            net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(
                type.getHitSound()),
            SoundSource.BLOCKS,
            x,
            y,
            z,
            (type.getVolume() + 1f) / 8f,
            type.getPitch() * 0.5f,
            seed()));
  }

  /**
   * 26.3's mining effects (particles, and the hit sound when {@code sound}), which a server sends
   * other players as a level event; recorded as that event.
   */
  public static void breakingEffects(BlockPos pos, Direction face, boolean sound) {
    if (!mining || localPlayer() == null) {
      return; // also called for other players' level events, which are recorded already
    }
    add(new ClientboundLevelEventPacket(sound ? 2020 : 2019, pos.immutable(), face.ordinal(), false));
  }

  /**
   * The small particles mining knocks off a block face. There is no packet for them, so they are
   * recorded as one block particle on that face, which looks the same from a distance.
   */
  public static void breakingParticles(BlockState state, BlockPos pos, Direction face) {
    if (!mining || localPlayer() == null || state.isAir()) {
      return;
    }
    var normal = face.getUnitVec3i();
    double out = 0.6; // half a block plus the small gap vanilla leaves
    add(
        new ClientboundLevelParticlesPacket(
            new BlockParticleOption(ParticleTypes.BLOCK, state),
            false,
            false,
            pos.getX() + 0.5 + normal.getX() * out,
            pos.getY() + 0.5 + normal.getY() * out,
            pos.getZ() + 0.5 + normal.getZ() * out,
            normal.getX() == 0 ? 0.25f : 0f,
            normal.getY() == 0 ? 0.25f : 0f,
            normal.getZ() == 0 ? 0.25f : 0f,
            0f,
            1));
  }

  /**
   * The packets collected since the last call, encoded; empty when there are none. A new level
   * forgets the last crack stage, so breaking the same block again is recorded.
   */
  public static List<byte[]> drain(RegistryAccess access) {
    if (pending.isEmpty()) {
      return List.of();
    }
    if (access != registries) {
      registries = access;
      protocol = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(access));
    }
    List<byte[]> out = new ArrayList<>(pending.size());
    for (Packet<? super ClientGamePacketListener> packet : pending) {
      ByteBuf buf = Unpooled.buffer();
      try {
        protocol.codec().encode(buf, packet);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        out.add(bytes);
      } finally {
        buf.release();
      }
    }
    pending.clear();
    return out;
  }

  /** Forgets the last crack stage (a new recording or level). */
  public static void reset() {
    lastProgress = Integer.MIN_VALUE;
    lastProgressPos = null;
  }

  private static void add(Packet<? super ClientGamePacketListener> packet) {
    if (enabled && pending.size() < MAX_PENDING) {
      pending.add(packet);
    }
  }

  private static Entity localPlayer() {
    Minecraft mc = Minecraft.getInstance();
    return mc.isSameThread() ? mc.player : null;
  }

  private static long seed() {
    return java.util.concurrent.ThreadLocalRandom.current().nextLong();
  }
}
