# Changelog

All notable changes are documented here. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning: [Semantic Versioning](https://semver.org/). Before 1.0.0, minor versions may break
compatibility.

## [Unreleased]

### Added

- Basic playback: a recording is played back into the client world through a loopback replay
  server (ADR 0008). Dev-only entry point `/reizo-debug play [file]`.
- Playback shows the recorded player as its own entity, moving as recorded, while you watch as a
  spectator camera.
- Replay browser: list, play, rename and delete recordings, and open the recordings folder.
  Opened with the new "Replays" button on the title screen and in the pause menu.
- Pause menu buttons to start, stop, pause and resume recording.
- Start/stop is now Shift+R by default, like Lunar. The modifier can be changed or removed.
- Reizo settings screen (Replays → Settings): auto-record, indicator, saving the server name,
  start/stop modifier, and a shortcut to key bindings.
- Seeking in replays: ← and → jump 5 seconds, `,` and `.` jump to markers. A time bar at the
  bottom shows the position, speed, camera mode and markers.
- Replay controls: V switches the camera between free, the recorded player's view and following
  the player; K pauses and resumes; `[` and `]` change the speed (1/8x to 8x).
- Gradle multi-project build: `core` (Minecraft-independent engine) and `fabric` (Minecraft 1.21.11
  client mod), Spotless formatting, GitHub Actions CI.
- Recording stream format v1: CRC-checked blocks, tolerant scanning for crash recovery
  (`docs/FORMAT.md`).
- Recording sessions with a background writer thread, pause (clock freeze), markers, periodic
  metadata refresh and bounded memory use.
- Crash recovery: abandoned recordings are detected with OS file locks and salvaged on startup.
- Packet capture at the client packet decoder (configuration and play phases, byte-exact) plus
  per-tick local player state.
- Configuration cache so recordings started mid-session still contain registries and the login
  packet.
- Start/stop key, optional pause and marker keys, HUD recording indicator, toasts.
- Auto-record on join setting (`config/reizo.json`).
- Unit tests for format, sessions, storage and recovery; in-game client gametests that record a
  singleplayer world and decode every recorded packet with Minecraft's own codecs.
