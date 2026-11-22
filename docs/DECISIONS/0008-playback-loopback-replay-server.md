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
- **Pause and speed.** The replay server owns the client's tick state: recorded
  `ClientboundTickingStatePacket`s are dropped and remembered, and the server sends its own with the
  recorded tick rate times the replay speed, frozen while the replay is paused. Vanilla then slows,
  speeds or freezes the world on the client; players are not frozen, so the camera still moves.
- **Camera modes** (`fabric.playback.ReplayCamera`): free (the spectator camera), the recorded
  player's view, and following behind the recorded player, using the client's camera entity and
  perspective.
- **Seeking** (#8, first version: restart and fast-forward). Forwards, the clock jumps and every
  packet up to the target is sent at once. Backwards, the server sends
  `ClientboundStartConfigurationPacket`, the way a proxy moves a player between servers. The
  client drops its world and acknowledges, and the server replays the configuration phase and then
  the timeline from the start up to the target. The client tick does not end the replay while it
  waits for the rebuilt world. Snapshots for fast seeking in long recordings come later.
- The replay ends when the client leaves the replay world, detected on the client tick. The
  client's socket can stay half-open for up to 30 s, so neither the server nor the disconnect
  events see the close promptly.
- Entry point for now: the dev-only `/reizo-debug play [file]` command. The replay browser (#10) is
  the real entry point.

## Consequences

+ Vanilla code handles login, configuration, registries and level setup, which keeps this
  version-light (ADR 0007).
+ The same bytes that were verified at record time are what the client decodes.
+ Pause and speed use vanilla's tick rate handling, with no mixins.
− The client's own tick rate follows the replay speed, so the free camera also flies faster or
  slower. Decoupling it needs a later change to how the camera player ticks.
− The replay is a "remote server" to the client. Anything that behaves differently for
  multiplayer (for example the pause menu not pausing) needs handling in later slices.
− A backwards seek rebuilds the world from the start, so its cost grows with the target time. The
  client briefly shows the reconfiguring screen. Snapshots (#8) will fix both.
− Not yet shown on the recorded player: sneaking/pose, held items and armor that the server never
  sent for the player itself. Free camera controls beyond spectator flight come in #9.
− A loopback socket is opened while a replay plays (127.0.0.1 only, one connection, closed with the
  replay).
− The gametest run disables Fabric's gametest packet synchronizer, because packets from a raw
  socket are not counted by it.
