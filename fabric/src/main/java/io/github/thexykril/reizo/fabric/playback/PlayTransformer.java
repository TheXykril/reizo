/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.playback;

import io.github.thexykril.reizo.core.format.FormatException;
import io.github.thexykril.reizo.core.format.LocalPlayerState;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns the recorded play stream into what the replay client should see (ADR 0008, "recorded
 * player").
 *
 * <p>When recording, the client's own player was the recorded player. In a replay the client's
 * player becomes a spectator camera with its own entity id, and the recorded player is shown as a
 * separate player entity that follows the recorded {@code LOCAL_PLAYER} samples:
 *
 * <ul>
 *   <li>The login packet is rewritten: a camera entity id and the spectator game mode.
 *   <li>Respawn packets keep their dimension but get the spectator game mode.
 *   <li>Packets that would move, rotate, hurt or kill the camera, or change its game mode or
 *       abilities, are dropped. The first position packet is kept so the camera starts where the
 *       recorded player started.
 *   <li>Each local player sample becomes position, head rotation, swing and pose packets for the
 *       recorded player entity, which is added on the first sample.
 * </ul>
 *
 * Everything else passes through as the captured bytes. Not thread-safe; used by the replay thread.
 */
final class PlayTransformer {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Replay");

  /** Entity id for the camera; far from the ids a server hands out. */
  static final int CAMERA_ENTITY_ID = Integer.MAX_VALUE - 7;

  private static final int ANIMATE_SWING_MAIN_HAND = 0;
  private static final int ANIMATE_SWING_OFF_HAND = 3;

  private final ProtocolInfo<ClientGamePacketListener> protocol;
  private final RegistryAccess registryAccess;
  private final UUID recordedPlayerUuid;
  private final String recordedPlayerName;
  private final int playerInfoId;
  private boolean recordedPlayerInfoSeen;
  private final com.mojang.authlib.GameProfile camera;
  private final int playerPositionId;
  private final int playerRotationId;
  private final int setHealthId;
  private final int combatKillId;
  private final int abilitiesId;
  private final int gameEventId;
  private final int respawnId;
  private final int tickingStateId;
  private final int startConfigurationId;

  /**
   * Screen and container packets meant for the recorded player. The camera must not open the
   * recorded player's chests or books (they would take over the viewer's input and never close,
   * since closing was the recorded client's own action); recorded screens are drawn from {@code
   * PLAYER_SCREEN} frames instead.
   */
  private final java.util.Set<Integer> screenPacketIds = new java.util.HashSet<>();
  private volatile float recordedTickRate = 20f;
  private volatile boolean recordedFrozen;
  private final java.util.concurrent.atomic.AtomicBoolean recordedTickStateChanged =
      new java.util.concurrent.atomic.AtomicBoolean();

  private boolean loginSeen;
  private boolean cameraPlaced;
  private int recordedEntityId = -1;
  private boolean recordedPlayerAdded;
  private int lastSwingTime = Integer.MAX_VALUE;
  private int lastPose = -1;
  private int lastLivingFlags = -1;

  PlayTransformer(
      RegistryAccess access,
      com.mojang.authlib.GameProfile camera,
      UUID recordedPlayerUuid,
      String recordedPlayerName) {
    this.recordedPlayerName = recordedPlayerName;
    this.camera = camera;
    this.registryAccess = access;
    this.protocol =
        GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(access));
    this.recordedPlayerUuid = recordedPlayerUuid;
    PositionMoveRotation zero = new PositionMoveRotation(Vec3.ZERO, Vec3.ZERO, 0, 0);
    this.playerPositionId = idOf(new ClientboundPlayerPositionPacket(0, zero, Set.of()));
    this.playerRotationId = idOf(new ClientboundPlayerRotationPacket(0, false, 0, false));
    this.setHealthId = idOf(new ClientboundSetHealthPacket(20, 20, 5));
    this.combatKillId = idOf(new ClientboundPlayerCombatKillPacket(0, Component.empty()));
    this.abilitiesId = idOf(new ClientboundPlayerAbilitiesPacket(new Abilities()));
    this.playerInfoId = peekId(playerInfo(new UUID(0, 0), "x", GameType.SURVIVAL, false));
    this.tickingStateId = idOf(new ClientboundTickingStatePacket(20f, false));
    this.startConfigurationId =
        idOf(net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket.INSTANCE);
    this.gameEventId =
        idOf(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, 0));
    java.util.Set<net.minecraft.network.protocol.PacketType<?>> screenTypes =
        java.util.Set.of(
            net.minecraft.network.protocol.game.GamePacketTypes.CLIENTBOUND_OPEN_SCREEN,
            net.minecraft.network.protocol.game.GamePacketTypes.CLIENTBOUND_MOUNT_SCREEN_OPEN,
            net.minecraft.network.protocol.game.GamePacketTypes.CLIENTBOUND_OPEN_SIGN_EDITOR,
            net.minecraft.network.protocol.game.GamePacketTypes.CLIENTBOUND_OPEN_BOOK,
            net.minecraft.network.protocol.game.GamePacketTypes.CLIENTBOUND_MERCHANT_OFFERS,
            net.minecraft.network.protocol.game.GamePacketTypes.CLIENTBOUND_CONTAINER_CLOSE,
            net.minecraft.network.protocol.game.GamePacketTypes.CLIENTBOUND_CONTAINER_SET_CONTENT,
            net.minecraft.network.protocol.game.GamePacketTypes.CLIENTBOUND_CONTAINER_SET_SLOT,
            net.minecraft.network.protocol.game.GamePacketTypes.CLIENTBOUND_CONTAINER_SET_DATA,
            net.minecraft.network.protocol.game.GamePacketTypes.CLIENTBOUND_SET_CURSOR_ITEM,
            net.minecraft.network.protocol.game.GamePacketTypes.CLIENTBOUND_SET_PLAYER_INVENTORY,
            net.minecraft.network.protocol.game.GamePacketTypes.CLIENTBOUND_PLACE_GHOST_RECIPE);
    GameProtocols.CLIENTBOUND_TEMPLATE
        .details()
        .listPackets(
            (type, networkId) -> {
              if (screenTypes.contains(type)) {
                screenPacketIds.add(networkId);
              }
            });
    Holder<net.minecraft.world.level.dimension.DimensionType> overworld =
        access.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD);
    this.respawnId =
        idOf(
            new ClientboundRespawnPacket(
                new CommonPlayerSpawnInfo(
                    overworld,
                    net.minecraft.world.level.Level.OVERWORLD,
                    0,
                    GameType.SPECTATOR,
                    //? if >=26.3 {
                    /*java.util.Optional.empty(),
                    *///?} else {
                    null,
                    //?}
                    false,
                    false,
                    java.util.Optional.empty(),
                    0,
                    63),
                (byte) 0));
  }

  /** Entity id of the recorded player, or -1 before the login packet. */
  int recordedEntityId() {
    return recordedEntityId;
  }

  /** The tick rate the recorded server last announced (20 unless it was changed). */
  float recordedTickRate() {
    return recordedTickRate;
  }

  /** Whether the recorded server had frozen its ticking (the tick freeze command). */
  boolean recordedFrozen() {
    return recordedFrozen;
  }

  /** True once after a recorded tick state packet was dropped. */
  boolean takeRecordedTickStateChanged() {
    return recordedTickStateChanged.getAndSet(false);
  }

  boolean recordedPlayerAdded() {
    return recordedPlayerAdded;
  }

  /** Transforms one recorded play packet into zero or more packets for the client. */
  List<byte[]> packet(byte[] bytes) {
    if (!loginSeen) {
      loginSeen = true;
      // Like a server's own entry for the joining player: the camera needs one to be a spectator.
      // The client's loading screen waits for the chunks-start event, which a server sends only
      // after the first chunks; a replay paused at its first packet would wait forever, so it
      // comes right away (a spectator does not wait for its own chunk).
      return List.of(
          rewriteLogin(bytes),
          cameraPlayerInfo(),
          encode(
              new ClientboundGameEventPacket(
                  ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0)));
    }
    int id = peekId(bytes);
    if (id == playerPositionId) {
      if (cameraPlaced) {
        return List.of();
      }
      cameraPlaced = true;
      return List.of(bytes);
    }
    if (id == startConfigurationId) {
      // The replay server plays a recorded server switch itself (ReplayScript.Kind.RECONFIGURE).
      return List.of();
    }
    if (id == playerRotationId
        || id == setHealthId
        || id == combatKillId
        || id == abilitiesId
        || screenPacketIds.contains(id)) {
      return List.of();
    }
    if (id == playerInfoId && !recordedPlayerInfoSeen) {
      ClientboundPlayerInfoUpdatePacket p = (ClientboundPlayerInfoUpdatePacket) decode(bytes);
      if (p.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)
          && p.entries().stream().anyMatch(e -> recordedPlayerUuid.equals(e.profileId()))) {
        recordedPlayerInfoSeen = true;
      }
      return List.of(bytes);
    }
    if (id == tickingStateId) {
      // The replay server sends the tick state itself, so the replay speed and pause apply on top
      // of the recorded rate (see ReplayServer).
      ClientboundTickingStatePacket p = (ClientboundTickingStatePacket) decode(bytes);
      recordedTickRate = p.tickRate();
      recordedFrozen = p.isFrozen();
      recordedTickStateChanged.set(true);
      return List.of();
    }
    if (id == gameEventId) {
      ClientboundGameEventPacket p = (ClientboundGameEventPacket) decode(bytes);
      return p.getEvent() == ClientboundGameEventPacket.CHANGE_GAME_MODE
          ? List.of()
          : List.of(bytes);
    }
    if (id == respawnId) {
      ClientboundRespawnPacket p = (ClientboundRespawnPacket) decode(bytes);
      recordedPlayerAdded = false; // the new level does not have the recorded player yet
      lastPose = -1;
      lastLivingFlags = -1;
      return List.of(
          encode(
              new ClientboundRespawnPacket(spectator(p.commonPlayerSpawnInfo()), p.dataToKeep())));
    }
    return List.of(bytes);
  }

  /** Packets that show one recorded local player sample on the recorded player entity. */
  List<byte[]> localPlayer(byte[] payload) {
    LocalPlayerState s;
    try {
      s = LocalPlayerState.decode(payload);
    } catch (FormatException e) {
      LOGGER.debug("Skipping unreadable local player sample: {}", e.getMessage());
      return List.of();
    }
    if (recordedEntityId < 0) {
      return List.of();
    }
    List<byte[]> out = new ArrayList<>(4);
    Vec3 pos = new Vec3(s.x(), s.y(), s.z());
    if (!recordedPlayerAdded) {
      recordedPlayerAdded = true;
      if (!recordedPlayerInfoSeen) {
        // Started mid-session: the server sent the player's tab entry before the recording began.
        // The client only adds a player entity it has an entry for.
        recordedPlayerInfoSeen = true;
        out.add(playerInfo(recordedPlayerUuid, recordedPlayerName, GameType.SURVIVAL, true));
      }
      out.add(
          encode(
              new ClientboundAddEntityPacket(
                  recordedEntityId,
                  recordedPlayerUuid,
                  s.x(),
                  s.y(),
                  s.z(),
                  s.xRot(),
                  s.yRot(),
                  EntityType.PLAYER,
                  0,
                  Vec3.ZERO,
                  s.headYRot())));
    }
    out.add(
        encode(
            new ClientboundEntityPositionSyncPacket(
                recordedEntityId,
                //? if >=26.3 {
                /*net.minecraft.world.entity.PositionPath.of(pos),
                s.yRot(),
                s.xRot(),
                *///?} else {
                new PositionMoveRotation(pos, Vec3.ZERO, s.yRot(), s.xRot()),
                //?}
                s.has(LocalPlayerState.FLAG_ON_GROUND))));
    out.add(rotateHead(recordedEntityId, s.headYRot()));
    boolean swinging = s.has(LocalPlayerState.FLAG_SWINGING);
    if (swinging && s.swingTime() < lastSwingTime) {
      int action =
          s.has(LocalPlayerState.FLAG_SWING_OFFHAND)
              ? ANIMATE_SWING_OFF_HAND
              : ANIMATE_SWING_MAIN_HAND;
      out.add(animate(recordedEntityId, action));
    }
    lastSwingTime = swinging ? s.swingTime() : Integer.MAX_VALUE;
    byte[] data = poseData(s);
    if (data != null) {
      out.add(data);
    }
    return out;
  }

  private byte[] rewriteLogin(byte[] bytes) {
    Packet<?> decoded = decode(bytes);
    if (!(decoded instanceof ClientboundLoginPacket p)) {
      throw new IllegalStateException(
          "first play packet is " + decoded.type() + ", not the login packet");
    }
    recordedEntityId = p.playerId();
    return encode(
        new ClientboundLoginPacket(
            CAMERA_ENTITY_ID,
            p.hardcore(),
            p.levels(),
            p.maxPlayers(),
            p.chunkRadius(),
            p.simulationDistance(),
            p.reducedDebugInfo(),
            p.showDeathScreen(),
            p.doLimitedCrafting(),
            spectator(p.commonPlayerSpawnInfo()),
            //? if >=26.2 {
            /*p.onlineMode(),
            *///?}
            p.enforcesSecureChat()));
  }

  private static CommonPlayerSpawnInfo spectator(CommonPlayerSpawnInfo info) {
    return new CommonPlayerSpawnInfo(
        info.dimensionType(),
        info.dimension(),
        info.seed(),
        GameType.SPECTATOR,
        //? if >=26.3 {
        /*java.util.Optional.of(info.gameType()),
        *///?} else {
        info.gameType(),
        //?}
        info.isDebug(),
        info.isFlat(),
        info.lastDeathLocation(),
        info.portalCooldown(),
        info.seaLevel());
  }

  // ---- packets that have no plain constructor (they take an Entity or a ServerPlayer) ----

  /** Unlisted tab entry for the camera, in spectator mode. */
  private byte[] cameraPlayerInfo() {
    return playerInfo(camera.id(), camera.name(), GameType.SPECTATOR, false);
  }

  /** A tab entry without skin properties (for a player the recording has no entry for). */
  private byte[] playerInfo(UUID id, String name, GameType mode, boolean listed) {
    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
    try {
      buf.writeEnumSet(
          java.util.EnumSet.of(
              ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
              ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
              ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED),
          ClientboundPlayerInfoUpdatePacket.Action.class);
      buf.writeVarInt(1);
      buf.writeUUID(id);
      buf.writeUtf(name, 16); // ADD_PLAYER: name
      buf.writeVarInt(0); // ADD_PLAYER: no profile properties
      buf.writeVarInt(mode.getId()); // UPDATE_GAME_MODE
      buf.writeBoolean(listed); // UPDATE_LISTED
      return encode(ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(buf));
    } finally {
      buf.release();
    }
  }

  /**
   * Entity data for the sample's pose (crouching, swimming, sleeping...) and item use, when they
   * changed. The shared flags byte is left alone: it also holds bits only the server knows
   * (burning, invisible, glowing), which the recorded server packets set.
   */
  private byte[] poseData(LocalPlayerState s) {
    Pose[] poses = Pose.values();
    int pose = s.pose() < poses.length ? s.pose() : Pose.STANDING.ordinal();
    int living = s.has(LocalPlayerState.FLAG_USING_ITEM) ? 1 : 0;
    if (pose == lastPose && living == lastLivingFlags) {
      return null;
    }
    lastPose = pose;
    lastLivingFlags = living;
    return encode(
        new ClientboundSetEntityDataPacket(
            recordedEntityId,
            List.of(
                SynchedEntityData.DataValue.create(
                    io.github.thexykril.reizo.fabric.mixin.EntityDataAccessors.reizo$pose(),
                    poses[pose]),
                SynchedEntityData.DataValue.create(
                    io.github.thexykril.reizo.fabric.mixin.LivingEntityDataAccessors
                        .reizo$livingFlags(),
                    (byte) living))));
  }

  private byte[] rotateHead(int entityId, float headYRot) {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    try {
      buf.writeVarInt(entityId);
      buf.writeByte((byte) net.minecraft.util.Mth.floor(headYRot * 256.0F / 360.0F));
      return encode(ClientboundRotateHeadPacket.STREAM_CODEC.decode(buf));
    } finally {
      buf.release();
    }
  }

  private byte[] animate(int entityId, int action) {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    try {
      buf.writeVarInt(entityId);
      buf.writeByte(action);
      return encode(ClientboundAnimatePacket.STREAM_CODEC.decode(buf));
    } finally {
      buf.release();
    }
  }

  // ---- codec helpers ----

  private int idOf(Packet<? super ClientGamePacketListener> packet) {
    return peekId(encode(packet));
  }

  private Packet<? super ClientGamePacketListener> decode(byte[] bytes) {
    return protocol.codec().decode(Unpooled.wrappedBuffer(bytes));
  }

  private byte[] encode(Packet<? super ClientGamePacketListener> packet) {
    ByteBuf buf = Unpooled.buffer();
    try {
      protocol.codec().encode(buf, packet);
      byte[] out = new byte[buf.readableBytes()];
      buf.readBytes(out);
      return out;
    } finally {
      buf.release();
    }
  }

  private static int peekId(byte[] bytes) {
    int value = 0;
    for (int i = 0, shift = 0; i < bytes.length && shift < 35; i++, shift += 7) {
      value |= (bytes[i] & 0x7F) << shift;
      if ((bytes[i] & 0x80) == 0) {
        return value;
      }
    }
    return -1;
  }
}
