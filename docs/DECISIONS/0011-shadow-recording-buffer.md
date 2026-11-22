# 0011: Shadow recording with snapshot segments in memory

Status: Accepted (2026-11-22). Issue #16.

## Context

Shadow recording ("save the last two minutes") lets a player keep something that already
happened without having pressed record. Lunar's Rewind and console replay buffers work this way.
A Reizo recording must start with the world as the client had it: the cached configuration and
login packets (the prelude) and a snapshot of chunks, entities and state (ADR 0004 and the
mid-session start). A rolling buffer of packets alone cannot do that, because the packets that
built the world fall out of the window.

Options considered:

1. **Write a normal recording to disk all the time and cut it on save.** Simple, but writes
   constantly and needs a way to cut a stream at a point that has a world. Cutting needs the
   same snapshots as option 3.
2. **One snapshot at the start of the window, rebuilt by replaying dropped packets into a model
   of the world.** Exact, but needs a Minecraft-free world model in `core`. Far too large.
3. **Segments in memory, each starting with a snapshot.** Every segment is a world snapshot (the
   same `WorldSnapshot` a mid-session start uses) followed by every frame until the next segment.
   A segment is dropped once the segment after it already covers the window.

## Decision

Option 3, in `core.recording.ShadowBuffer`, owned by `CaptureController`.

- Off by default (`shadowEnabled`). Length 30 s to 10 min (`shadowSeconds`, default 2 min),
  memory budget `shadowMemoryMb` (default 512 MB). When the budget is exceeded the oldest
  segments go first, but the newest segment is always kept.
- Segment length is half the window, between 15 and 120 s. A save covers between the window and
  one and a half windows. Long segments mean fewer snapshots: each one costs a hitch on the game
  thread while chunks are serialized and the memory of a full world copy.
- Play packets reach the buffer from the network thread, local player samples and markers from
  the client thread. A new configuration phase (server switch) or a disconnect clears it.
- Saving copies the buffer and the prelude on the client thread (cheap: lists of references), so
  a disconnect right after the key press cannot clear it. A background thread then writes it
  through a normal `RecordingSession` whose clock replays the frames' capture times. The saved
  recording is a normal archive with `startSnapshot = true` and `completeFromJoin = false`.
- Nothing is buffered while a replay plays (the packet tap is suppressed then).

## Consequences

- No new file format: saves are ordinary recordings and play, edit and export like any other.
- Memory use is real. A snapshot of a large render distance can be tens of MB; the budget bounds
  the total. The gametest logs the buffer size after a join.
- The snapshot hitch happens every segment while shadow recording is on. It needs measuring on
  real hardware (Hyperion) before shadow could be on by default.
- A packet decoded just before a snapshot but applied by the client just after it is in neither
  the snapshot nor the new segment. This is the same small gap a mid-session start has.
