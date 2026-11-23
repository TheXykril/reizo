/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.capture;

import com.mojang.authlib.properties.Property;
import com.mojang.datafixers.util.Pair;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rebuilds, as play packets, what the client already knows when a recording starts mid-session: the
 * dimension, chunks around the player, entities with their data and equipment, the tab list, time,
 * weather and the world border. A server sent all of this before the recording began, so without it
 * a mid-session replay would have an empty world. Client thread only.
 *
 * <p>The packets are built with Minecraft's own constructors and codecs from client state, so they
 * decode exactly like server packets. Server-only details the client never had are absent (for
 * example the biome zoom seed, so biome colour blending can differ slightly at chunk borders).
 */
public final class WorldSnapshot {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Snapshot");

  private final ProtocolInfo<ClientGamePacketListener> protocol;
  private final RegistryAccess registries;
  private final List<byte[]> out = new ArrayList<>();

  private WorldSnapshot(RegistryAccess registries) {
    this.registries = registries;
    this.protocol =
        GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries));
  }

  /** Builds the snapshot packets, or an empty list when the client is not in a world. */
  public static List<byte[]> capture(Minecraft mc) {
    ClientLevel level = mc.level;
    LocalPlayer player = mc.player;
    ClientPacketListener connection = mc.getConnection();
    if (level == null || player == null || connection == null || mc.gameMode == null) {
      return List.of();
    }
    long started = System.nanoTime();
    WorldSnapshot s = new WorldSnapshot(connection.registryAccess());
    try {
      s.build(mc, level, player, connection);
    } catch (RuntimeException e) {
      // A partial snapshot is still better than none; the live packets follow either way.
      LOGGER.warn("World snapshot incomplete", e);
    }
    LOGGER.info(
        "World snapshot: {} packets, {} KB in {} ms",
        s.out.size(),
        s.out.stream().mapToLong(b -> b.length).sum() / 1024,
        (System.nanoTime() - started) / 1_000_000);
    return s.out;
  }

  private void build(
      Minecraft mc, ClientLevel level, LocalPlayer player, ClientPacketListener connection) {
    // The cached login packet names the dimension the player joined; they may have moved since.
    add(
        new ClientboundRespawnPacket(
            new CommonPlayerSpawnInfo(
                level.dimensionTypeRegistration(),
                level.dimension(),
                0L,
                mc.gameMode.getPlayerMode(),
                //? if >=26.1 {
                /*java.util.Optional.ofNullable(mc.gameMode.getPreviousPlayerMode()),
                *///?} else {
                mc.gameMode.getPreviousPlayerMode(),
                //?}
                level.isDebug(),
                false,
                player.getLastDeathLocation(),
                player.getPortalCooldown(),
                level.getSeaLevel()),
            ClientboundRespawnPacket.KEEP_ALL_DATA));

    int radius = Math.max(2, mc.options.getEffectiveRenderDistance());
    int cx = net.minecraft.core.SectionPos.blockToSectionCoord(player.getBlockX());
    int cz = net.minecraft.core.SectionPos.blockToSectionCoord(player.getBlockZ());
    add(new ClientboundSetChunkCacheRadiusPacket(radius));
    add(new ClientboundSetChunkCacheCenterPacket(cx, cz));
    add(new ClientboundPlayerPositionPacket(0, PositionMoveRotation.of(player), Set.of()));

    tabList(connection);

    // Nearest chunks first, so playback shows the area around the player soonest.
    List<LevelChunk> chunks = new ArrayList<>();
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        LevelChunk chunk =
            level.getChunkSource().getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false);
        if (chunk != null && !chunk.isEmpty()) {
          chunks.add(chunk);
        }
      }
    }
    chunks.sort(
        java.util.Comparator.comparingInt(
            c -> Math.max(Math.abs((c.getPos().getMinBlockX() >> 4) - cx), Math.abs((c.getPos().getMinBlockZ() >> 4) - cz))));
    for (LevelChunk chunk : chunks) {
      add(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null));
    }
    // Like a server after the first chunks: lets the client close its loading screen.
    add(new ClientboundGameEventPacket(ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0));

    add(timePacket(level));
    add(new ClientboundInitializeBorderPacket(level.getWorldBorder()));
    if (level.isRaining()) {
      add(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0));
      add(
          new ClientboundGameEventPacket(
              ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level.getRainLevel(1f)));
      add(
          new ClientboundGameEventPacket(
              ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.getThunderLevel(1f)));
    }

    List<Entity> withPassengers = new ArrayList<>();
    for (Entity e : level.entitiesForRendering()) {
      if (e == player) {
        equipment(player); // shown on the recorded player
        continue;
      }
      add(
          new ClientboundAddEntityPacket(
              e.getId(),
              e.getUUID(),
              e.getX(),
              e.getY(),
              e.getZ(),
              e.getXRot(),
              e.getYRot(),
              e.getType(),
              0,
              e.getDeltaMovement(),
              e.getYHeadRot()));
      var data = e.getEntityData().getNonDefaultValues();
      if (data != null && !data.isEmpty()) {
        add(new ClientboundSetEntityDataPacket(e.getId(), data));
      }
      if (e instanceof LivingEntity living) {
        equipment(living);
      }
      if (!e.getPassengers().isEmpty()) {
        withPassengers.add(e);
      }
    }
    for (Entity vehicle : withPassengers) {
      add(new ClientboundSetPassengersPacket(vehicle));
    }
  }

  private void equipment(LivingEntity e) {
    List<Pair<EquipmentSlot, ItemStack>> items = new ArrayList<>();
    for (EquipmentSlot slot : EquipmentSlot.values()) {
      ItemStack stack = e.getItemBySlot(slot);
      if (!stack.isEmpty()) {
        items.add(Pair.of(slot, stack.copy()));
      }
    }
    if (!items.isEmpty()) {
      add(new ClientboundSetEquipmentPacket(e.getId(), items));
    }
  }

  /** Tab list entries, with skins, so player entities are shown with the right profile. */
  private void tabList(ClientPacketListener connection) {
    Collection<PlayerInfo> all = connection.getOnlinePlayers();
    if (all.isEmpty()) {
      return;
    }
    Collection<PlayerInfo> listed = connection.getListedOnlinePlayers();
    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
    try {
      buf.writeEnumSet(
          EnumSet.of(
              ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
              ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
              ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
              ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY),
          ClientboundPlayerInfoUpdatePacket.Action.class);
      buf.writeVarInt(all.size());
      for (PlayerInfo info : all) {
        var profile = info.getProfile();
        buf.writeUUID(profile.id());
        buf.writeUtf(profile.name(), 16);
        Collection<Property> props = profile.properties().values();
        buf.writeVarInt(props.size());
        for (Property p : props) {
          buf.writeUtf(p.name(), 64);
          buf.writeUtf(p.value(), 32767);
          buf.writeNullable(p.signature(), (b, sig) -> b.writeUtf(sig, 1024));
        }
        buf.writeVarInt(info.getGameMode().getId());
        buf.writeBoolean(listed.contains(info));
        buf.writeVarInt(info.getLatency());
      }
      add(ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(buf));
    } finally {
      buf.release();
    }
  }

  private void add(Packet<? super ClientGamePacketListener> packet) {
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

  private static ClientboundSetTimePacket timePacket(ClientLevel level) {
    //? if >=26.1 {
    /*// 26.x replaced the day time with world clocks. Every registered clock is sent as it stands.
    java.util.Map<
            net.minecraft.core.Holder<net.minecraft.world.clock.WorldClock>,
            net.minecraft.world.clock.ClockNetworkState>
        clocks = new java.util.HashMap<>();
    level
        .registryAccess()
        .lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_CLOCK)
        .listElements()
        .forEach(
            clock -> {
              var instance = level.clockManager().getInstance(clock);
              clocks.put(
                  clock,
                  new net.minecraft.world.clock.ClockNetworkState(
                      instance.totalTicks(), instance.partialTick(), instance.rate()));
            });
    return new ClientboundSetTimePacket(level.getGameTime(), clocks);
    *///?} else {
    return new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), true);
    //?}
  }
}
