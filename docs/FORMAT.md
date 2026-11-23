# Recording format

Version: **stream v1**, **metadata schema 1**. Source of truth in code:
`core/.../format/StreamFormat.java`, `RecordingMetadata.java`, `LocalPlayerState.java`.

## Archive (`*.reizo`)

A ZIP file:

| Entry | Contents |
| --- | --- |
| `meta.json` | `RecordingMetadata` (UTF-8 JSON) |
| `stream.bin` | frame stream (below) |

Readers must ignore unknown entries. Planned entries: `snapshots/`, `audio/`, `thumbnail.png`,
`index.bin` (seek index).

## Working directory (while recording)

`<recordings>/working/<uuid>/` with `stream.bin`, `meta.json` (atomically replaced) and
`owner.lock` (OS lock held by the live session).

## Stream (`stream.bin`)

All integers big-endian unless noted; varints are unsigned LEB128 (Minecraft VarInt layout).

```
file    := header block*
header  := "REISTRM\0" (8 bytes) version:int32
block   := 0x5254424B:int32 payloadLength:int32 crc32(payload):int32 frameCount:int32
           firstTimeMs:int32 lastTimeMs:int32 payload
payload := frame{frameCount}
frame   := varint(timeMs - firstTimeMs) varint(typeId) varint(length) bytes[length]
```

Limits: block payload ≤ 16 MiB, frame payload ≤ 8 MiB. Frame times are non-decreasing across the
whole stream. A block is valid only if fully present with matching magic and CRC.

### Frame types

| Id | Name | Payload |
| --- | --- | --- |
| 1 | `PACKET` | `phase:u8` (1 = configuration, 2 = play) then the packet exactly as received: VarInt packet id + body, after decompression/decryption |
| 2 | `LOCAL_PLAYER` | see below |
| 3 | `MARKER` | UTF-8 label |
| 4 | `LIVE_START` | empty. Frames before it are a prelude replayed from cache at time 0 |
| 5 | `PLAYER_HUD` | see below |

Unknown ids must be skipped by readers.

### Packet ids

Packet ids are those of the recording's protocol version (`meta.protocolVersion`, 774 for
1.21.11). Decoding requires that version's codecs; play packets also need the registries from the
recorded configuration phase.

Not every play packet came from the server. The recorder adds vanilla packets the server never
sends to the recording player: the start snapshot (`meta.startSnapshot`) and the local player's
equipment (`ClientboundSetEquipmentPacket` for the local player's entity id, written after that
tick's `LOCAL_PLAYER` frame whenever a held or worn item changes, and in full after a respawn or
dimension change). Readers treat them like any other play packet.

### `LOCAL_PLAYER` layout 1

```
layout:u8(=1) entityId:int32 x:f64 y:f64 z:f64 yRot:f32 xRot:f32 headYRot:f32 bodyYRot:f32
pose:u8 flags:u8 swingTime:u8
flags: 1 onGround, 2 sprinting, 4 sneaking, 8 usingItem, 16 swinging, 32 swing is off-hand
```

`pose` is the `Pose` enum ordinal of the recording's Minecraft version. Written only on ticks where
the state changed.

### `PLAYER_HUD` layout 1

What the recorded player's own HUD showed, sampled from the client because the server does not
send all of it (the selected slot and creative inventories are the client's own). Written after
that tick's `LOCAL_PLAYER` frame, only on ticks where something changed. Big-endian.

```
layout:u8(=1) selectedSlot:u8 gameMode:u8 flags:u8 health:f32 maxHealth:f32 absorption:f32
food:u8 saturation:f32 armor:u8 experienceLevel:int32 experienceProgress:f32
air:int16 maxAir:int16 itemsLength:int32 items:bytes
flags: 1 hardcore
items: VarInt count (10: hotbar slots 0 to 8, then the offhand), then each stack with
       ItemStack.OPTIONAL_STREAM_CODEC against the recording's registries
```

`gameMode` is the `GameType` id (0 survival, 1 creative, 2 adventure, 3 spectator). `air` can be
negative while drowning.

## Time

Recording time in milliseconds, starting at 0, excluding paused periods. Pausing freezes time but
packets keep being recorded, so state changes during a pause apply instantly at the resume point.

## Compatibility rules

- Adding a frame type, metadata field or archive entry: compatible, no version bump.
- Changing an existing layout: bump the relevant version and keep a reader for the old one, or
  write an ADR explaining why old recordings are dropped.
