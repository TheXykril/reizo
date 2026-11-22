# 0004 — Capture packets at the client decoder

Status: Accepted (2026-11-22)

## Context
A replay needs every clientbound packet, in order, with the protocol phase it belongs to.
Options: (a) hook `Connection.channelRead0` and re-encode packet objects; (b) hook the decoder and
copy the bytes it consumed; (c) render-based video capture (no free camera, rejected).
Re-encoding in (a) needs the current `ProtocolInfo`, which is switched from the client thread
while the network thread may still deliver old-phase packets, and some packets are shared between
configuration and play with different ids. Bundles also need special handling there.

## Decision
Mixin into `net.minecraft.network.PacketDecoder#decode`: remember `readerIndex` at HEAD, and just
before the packet is added to the output list copy the consumed bytes. The decoder's own
`protocolInfo` gives the phase. Only `CLIENTBOUND` decoders in CONFIGURATION and PLAY are
captured. Singleplayer uses the same path because the integrated server's in-memory channel still
serializes packets (verified in 1.21.11 sources: `LocalFrameEncoder`/`HiddenByteBuf`).

## Consequences
+ Byte-exact data, no re-encoding cost or codec mismatch; phase attribution is always correct.
+ Bundle delimiters are recorded as ordinary packets, so playback can re-bundle naturally.
+ Verified by gametests that decode every recorded packet with Minecraft's codecs.
− One `byte[]` copy per packet on the network thread (small; measured later in M1 perf work).
− If a future version stops serializing in-memory packets, singleplayer needs a fallback (a).
