# Changelog

All notable changes are documented here. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning: [Semantic Versioning](https://semver.org/). Before 1.0.0, minor versions may break
compatibility.

## [Unreleased]

### Fixed

- A backwards seek no longer moves the free camera to the world origin.
- Seeking a paused replay to before its world had loaded left the client on the "reconfiguring"
  screen. Seeks now start no earlier than the recording's first world packet.

### Added

- With OneConfig (for example in OneClient): the recording indicator is also a HUD element
  that can be placed in OneConfig's HUD editor, and the OneConfig page has the shadow recording
  settings.
- Effects for replays and exports (timeline → Effects): exposure, contrast, saturation,
  temperature, vignette and letterbox, keyframed over time.
- Exports are rendered at the chosen size (for example 4K from a smaller window) instead of
  being upscaled from the window.
- Shadow recording (off by default; Settings or OneConfig): keeps the last 30 s to 10 min of
  play in memory without recording, and "Save last …" in the pause menu or the save key writes
  it as a normal recording.
- Export settings screen (pause menu "Export video" or the timeline's Export): size, frame rate,
  quality, format (H.264, H.265, VP9, AV1, and NVIDIA, VAAPI or Intel QSV hardware encoders when
  FFmpeg has them), file name, and the In/Out range.
- Camera path shown in the replay world (G toggles it): the curve, each keyframe with its view
  direction, and where the camera is now.
- Free camera roll (hold U / I) and FOV (hold - / =), reset with 0. New keyframes store both.
- Timeline editor for replays (Y or the pause menu): a panel with a time ruler, markers, camera
  keyframes and the export region. Drag keyframes, box-select, copy and paste, undo and redo,
  zoom and scroll, click to seek, and set the export start and end with I and O.
- Optional OneConfig page (for example in OneClient): open replays, start/stop recording and
  change Reizo's settings from OneConfig. Reizo works the same without OneConfig.
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
- Video export: "Export video" in the pause menu during a replay renders it frame by frame
  (exact timing at any computer speed) and encodes it with your FFmpeg to `reizo/exports/`. The
  camera path plays during the export. Progress shows in the window title; Esc cancels.
- Camera paths in replays: N adds a keyframe at the camera, Delete removes the nearest one, B plays
  the path and J switches smoothing. Keyframes show on the time bar. Each recording gets a project
  that saves the path automatically.
- Recordings started in the middle of a session now include the world as it was at the start
  (chunks, entities, players, time, weather), so they play back with the world visible.
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
