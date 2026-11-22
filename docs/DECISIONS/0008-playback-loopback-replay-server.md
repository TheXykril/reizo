# 0008: Playback through a loopback replay server

Status: Accepted (2026-11-22). First slice of issue #7.

## Context

A recording holds the exact bytes of every clientbound configuration and play packet (ADR 0004).
To play it back, the client has to receive those packets again, in order and at their times, with
its normal packet handlers, so that the world, entities and effects look the way they did.

Options considered:

1. **Fake connection inside the client:** build a `Connection` without a socket and push decoded
   packets into the client's packet listeners. This bypasses the login and configuration flow,
   which the client relies on to set up registries, tags and the level. That flow is version
   specific and fragile to re-create by hand.
2. **Replay integrated server:** run a special integrated server and let it send the packets. This
   is the general direction Flashback's design takes (design only, ADR 0005). It brings a whole
   server lifecycle (level storage, player list, ticking) into the playback path that the first
   slice does not need.
3. **Loopback replay server:** a small socket server on 127.0.0.1 that speaks just enough of the
   protocol. The client connects through the normal `ConnectScreen`, so login, configuration and
   the switch to play all run through vanilla code. The server writes the recorded bytes as they
   were captured.

## Decision

Option 3.

- `core.playback.ReplayScript` splits a recording into configuration packets and timed play
  packets. `core.playback.PlaybackClock` gives replay time with pause and speed. Both are
  Minecraft-free and unit tested.
- `fabric.playback.ReplayServer` listens on the loopback address on a random port and accepts one
  connection. It:
  1. reads the handshake;
  2. answers `Hello` with `LoginFinished`, using the recorded player's profile;
  3. after `LoginAcknowledged`, writes the configuration packets;
  4. waits for the client's `FinishConfiguration`, so no play bytes reach the configuration decoder;
  5. writes play packets as the clock reaches their times, plus a keep-alive every 5 s while idle
     (the client drops a silent connection after 30 s).

  No compression or encryption is negotiated. Serverbound packets are read and dropped.
- `fabric.playback.ReplayPlayback` loads the archive and refuses another protocol version. It
  leaves the current world, starts the server and connects. While a replay is active, packet capture
  is suppressed, so a replay is never recorded.
- **Recorded player and camera** (`fabric.playback.PlayTransformer`). During recording the
  client's own player was the recorded player. In a replay the client's player is a spectator
  camera instead:
  - The login packet is rewritten with a camera entity id and spectator mode.
  - The camera gets its own UUID and an unlisted tab entry.
  - Respawns keep their dimension but stay spectator.
  - Packets that would move, rotate, hurt or kill the camera, or change its mode or abilities, are
    dropped. The first position packet is kept, so the camera starts where the player was.
  - The recorded player is added as a separate player entity with the recorded entity id and
    UUID. The `LOCAL_PLAYER` samples drive its position, rotation, head rotation and arm swing.
  - A mid-session recording has no tab entry for the player, so a skinless one is added.
- The replay ends when the client leaves the replay world, detected on the client tick. The
  client's socket can stay half-open for up to 30 s, so neither the server nor the disconnect
  events see the close promptly.
- Entry point for now: the dev-only `/reizo-debug play [file]` command. The replay browser (#10) is
  the real entry point.

## Consequences

+ Vanilla code handles login, configuration, registries and level setup, which keeps this
  version-light (ADR 0007).
+ The same bytes that were verified at record time are what the client decodes.
+ Pause and speed can later be added on the server side (clock) together with the client tick rate.
− The replay is a "remote server" to the client. Anything that behaves differently for
  multiplayer (for example the pause menu not pausing) needs handling in later slices.
− Seeking backwards needs a restart or snapshots (#8). The script only moves forward.
− Not yet shown on the recorded player: sneaking/pose, held items and armor that the server never
  sent for the player itself. Free camera controls beyond spectator flight come in #9.
− A loopback socket is opened while a replay plays (127.0.0.1 only, one connection, closed with the
  replay).
− The gametest run disables Fabric's gametest packet synchronizer, because packets from a raw
  socket are not counted by it.
