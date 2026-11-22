# Architecture

Status legend: **[built]** exists and is tested, **[planned]** designed but not written.

## Overview

```
┌────────────────────────── fabric (Minecraft 1.21.11 adapter) ───────────────────────────┐
│ PacketDecoderMixin ─► PacketTap ─┐       RecorderClient (entrypoint, lifecycle)          │
│ LocalPlayerSampler (tick) ───────┤       RecorderKeys · RecordingIndicator · toasts       │
│                                  │       [planned] ReplayWorld · CameraController · UI    │
└──────────────────────────────────┼──────────────────────────────────────────────────────┘
                                   ▼
┌──────────────────────────────── core (pure Java) ───────────────────────────────────────┐
│ recording/  CaptureController ─► RecordingSession ─► writer thread                        │
│ format/     Frame · FrameStreamWriter/Reader · RecordingMetadata · LocalPlayerState       │
│ storage/    RecordingStorage (working dirs, locks, finalize, recover) · RecordingArchive  │
│ [planned]   playback/ (seek index, snapshots) · camera/ · timeline/ · project/ · export/  │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

## Modules

### `core` [built]

Minecraft-independent. Depends only on Gson (shipped by Minecraft). Everything that can be
expressed without game classes lives here so it is unit-testable and survives Minecraft version
changes. Rules in [AGENTS.md](../AGENTS.md#architecture-rules).

- `format` — the on-disk stream ([FORMAT.md](FORMAT.md)): frames, CRC blocks, strict and tolerant
  readers, metadata JSON, local player payload.
- `recording` — `RecordingSession` (one recording's lifecycle, thread model below) and
  `CaptureController` (routes packets to the active session, keeps the per-connection
  configuration cache).
- `storage` — directory layout, ownership locks, packaging into archives, crash recovery.

### `fabric` [built, partial]

The game adapter for one Minecraft version. Hooks the game, converts game objects into `core`
types, renders UI. Contains no format or lifecycle logic.

### Planned modules (decided when needed, not before)

Gradle modules are split only when a boundary is proven. Expected packages: `core/playback`
(seek index, keyframe snapshots, frame scheduler), `core/camera` (keyframes, interpolation,
easing — pure math), `core/timeline` (tracks, clips, edit operations with undo), `core/project`
(project files, autosave), `core/export` (render queue, FFmpeg process management),
`fabric/replay` (replay world, entity reconstruction), `fabric/editor` (screens), and an optional
`oneconfig` integration source set loaded only when OneConfig is present.

## Recording data flow

1. Netty decodes a clientbound packet. `PacketDecoderMixin` copies the exact bytes the decoder
   consumed (after decryption and decompression) together with the decoder's own protocol phase.
   Singleplayer uses the same decoder: the integrated server's in-memory channel still serializes.
2. `PacketTap` forwards to `CaptureController.onPacket(phase, bytes)`.
3. The controller updates the configuration cache and, if recording, calls
   `RecordingSession.submitPacket`.
4. The session timestamps and enqueues the frame under one lock (stream order equals time order).
5. The writer thread drains the queue into `FrameStreamWriter`, flushing a CRC block at least once
   a second, fsyncing every 5 s and rewriting `meta.json` every 5 s.
6. Each client tick, `LocalPlayerSampler` records the player's own state when it changed (the
   server never sends it).
7. On stop, the writer drains, closes, marks metadata COMPLETE and packages
   `working/<id>/` into `<name>_<date>.reizo` (ZIP).

Why the decoder and not the packet handler: see [ADR 0004](DECISIONS/0004-capture-at-packet-decoder.md).

## Threading model

| Thread | Does | Never does |
| --- | --- | --- |
| Netty client thread | copy packet bytes, enqueue | disk I/O, waits > 50 ms |
| Client (render) thread | key handling, local player sampling, HUD, start/stop | disk I/O except session start (create dir) |
| `Recorder-Writer-<id>` | all stream and metadata I/O, packaging | touch game objects |
| `Recorder-Recovery` | scan and salvage abandoned recordings at startup | touch game objects |

Back-pressure: the queue holds 65,536 frames. If it stays full for 50 ms the frame is dropped and
counted in metadata (`droppedFrames`) instead of freezing the game.

## Crash safety

- Data loss on crash is bounded by the flush interval (1 s).
- Every block is self-validating (magic, length, CRC32). Recovery keeps the longest valid prefix.
- Ownership uses OS file locks (`owner.lock`), which the OS releases when the process dies, so
  "abandoned" is detected without timestamps or PIDs.
- Unrecoverable directories are left untouched and logged, never deleted.

## Recording completeness

A recording started at join (auto-record) has every packet. A recording started mid-session gets
the cached configuration packets and the level login packet as a prelude, but chunks and entities
that were already loaded are missing until **world snapshots** are implemented (Flashback-style:
synthesize packets from the client's current world at start). `meta.completeFromJoin` records which
case applies. Tracked in the roadmap (M2).

## Extension points (planned)

- Camera interpolators and effects are registries of small interfaces in `core`, so new
  interpolation types and effects need no changes elsewhere.
- Platform adapters: the `fabric` module is per Minecraft version; `core` is shared.
