# 0020: Replays join in memory

Status: Accepted (2026-11-24). Xykril chose the in-memory join on 2026-11-24. Amends ADR 0008.

## Context

ADR 0008 plays a recording through a small replay server on a loopback socket, and the client joins
it through the game's connecting screen. Two things showed that this feels like a network join:

- The connecting screen said "Connecting to 127.0.0.1" (OneClient's own connecting screen shows
  the server's name and address). PR #92 replaced the address with the replay's name, but the
  screen itself stayed.
- Flashback, whose design Reizo follows (ADR 0005, design only), shows the game's "Loading terrain"
  screen and opens no network port.

Options considered:

1. **Keep the socket**, only change the texts. Cheap, but still a connecting screen and an open
   loopback port.
2. **Decode in the replay server and pass packet objects** through the game's in-memory pipeline,
   as the integrated server does. The client would no longer decode the recorded bytes with its own
   codecs, which is how recordings stay faithful (ADR 0004). Every packet would be decoded and
   re-encoded on Reizo's side, and registry-bound packets need the client's registries first.
3. **Same bytes, in-memory transport.** Replace the socket with a pair of Netty local channels and
   give the client's end the game's normal network pipeline (`Connection.configureSerialization`
   with `local = false`: varint frames and the packet codecs). Join the way the game joins a
   singleplayer world: no connecting screen, the connection is Minecraft's pending connection until
   the world loads.

## Decision

Option 3. The replay server keeps writing and reading the exact bytes it wrote before (ADR 0008);
only the transport and the join change.

- `fabric.playback.ReplayChannel` binds a `LocalServerChannel` at a unique local address
  (`reizo-replay-<n>`) on its own one-thread `DefaultEventLoopGroup`, accepts one client, and turns
  its inbound buffers into an `InputStream` and an `OutputStream` for `ReplayServer`. It also opens
  the client's end.
- `fabric.playback.ReplayJoin` shows a "Loading replay" screen, opens the connection, starts the
  login with `ClientHandshakePacketListenerImpl` (as the connecting screen and the singleplayer
  join do), and sets it as `Minecraft.pendingConnection`, which the game ticks until a level is
  loaded. Once the recorded world arrives, the game shows its own terrain loading screen.
- The game takes local channels for its integrated server (`Connection.isMemoryConnection`) and
  then skips work only a real server needs, such as applying the tags it sends and compression. A
  replay is a real server to the client, so `ConnectionMixin` answers false for connections to a
  replay address.
- No network port is opened. Nothing else in Reizo's playback changes: seeking, server switches,
  export sync and the HUD samples run as before.
- The socket transport stays behind a switch for a while to compare (`-Dreizo.replay.socket=true`
  or `REIZO_REPLAY_SOCKET=1`). It can be removed once the in-memory join has been used for a
  while.

## Consequences

- Replays no longer show a connecting screen or the server join path that mods such as OneClient
  replace, and no port can collide or be blocked by a firewall.
- One more mixin on a vanilla method (`Connection.isMemoryConnection`) and one accessor
  (`Minecraft.pendingConnection`). Both exist with the same names on every version Reizo builds
  for.
- Chat signing setup (`Minecraft.prepareForMultiplayer`) is no longer run for replays; a replay
  never sends chat.
