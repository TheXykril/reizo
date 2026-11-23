# Changelog

All notable changes are documented here. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning: [Semantic Versioning](https://semver.org/). Before 1.0.0, minor versions may break
compatibility.

## [Unreleased]

### Fixed

- A chest or other container the recorded player opened no longer opens on the viewer's screen
  during a replay (where it stayed open and took over the controls).
- The settings screen fits at GUI scale 3 on a 720p window again.
- Exporting a multiplayer recording from its very first moment waited forever on the loading
  screen.
- Silent sounds no longer count as the start of an export's audio (seen on 26.1.2 and once on
  1.21.10, where a test then measured the wrong sound).
- Exporting across a recorded server switch could wait forever on the loading screen (seen on
  26.2).
- A backwards seek no longer moves the free camera to the world origin.
- Seeking a paused replay to before its world had loaded left the client on the "reconfiguring"
  screen. Seeks now start no earlier than the recording's first world packet.

### Added

- A new replay editor look (ADR 0015, proposed): docked panels in a dark theme with a blue accent,
  in the spirit of OneClient. A top bar with play controls, the time, speed, the view (Free, POV,
  Follow, Path), undo and Export; a tool rail; a Scene list of the replay's players and entities
  (click to look at one, click its eye to hide it); an Inspector with Keyframe, Look and World tabs
  (time of day, rain and thunder sliders); and a timeline with separate Markers, Camera, Look,
  World, Entities and Settings tracks. Tab folds the side panels; small windows keep only the top
  bar and the timeline. Text uses the Inter font and shapes have smooth corners, so the editor no
  longer looks like a Minecraft menu. The Inspector's Look tab holds the effects. Numbers are drag fields (drag, Shift for fine steps,
  scroll, double-click to reset), Inspector sections fold, and there is a menu bar (File, Edit,
  View, Camera) and right-click menus on scene rows, the ruler and camera keyframes. The Scene and
  Inspector panels can be dragged out by their title bars to float, and docked left or right.
  The replay browser, settings, export, effects, settings-over-time and rename screens, the delete
  question and the replay bar use the same look (switches, value rows, rounded text fields). Reizo's
  screens lower the GUI scale while open when the window is too small for their layout.
  With OneConfig installed (OneClient), Reizo's UI follows the selected OneConfig theme: its
  colours, light or dark, and its flat, glass or pixel style.
- Hide entities in a replay: press H while looking at a mob, item or player to hide it from that
  moment on (press again to show it). Hidden entities are keyframed switches in Timeline →
  Settings, undoable, saved with the project and hidden in exports too.
- Time of day, rain and thunder can be keyframed in a replay (Timeline → Settings), for night shots,
  storms or timelapses. They show in exports; the recording itself is unchanged.
- Minecraft 1.21.10, 26.1.2 and 26.2 support (separate jars), so every OneClient version from
  1.21.10 up is covered. Only tested by the automated in-game tests so far.
- Recordings made in OneClient note it (and its version) in their metadata.
- The inventory and chest screens the recorded player opened now show in player view (POV) replays,
  with the mouse, hovered item and the item on the cursor. They can be turned off in the settings
  or keyframed in the editor.
- The recorded player's HUD in player view (POV): hotbar with the selected slot and offhand,
  health, hunger, armor, experience and air, as they were while recording. It can be turned off in
  the settings or keyframed in the editor, and shows in exports.
- Minecraft 26.3 support (a separate jar, built from the same sources with Stonecutter). Not
  yet tested by hand. Recordings play only on the version that made them.

- Separate "Sound" and "Music" levels in the export settings.
- "Replays" in the top-left corner of OneClient's main menu, and an "Open replays" key (unbound by
  default) that opens the replay browser from any main menu or in game.
- Server switches behind a proxy (Velocity, BungeeCord) no longer split anything: the recording
  carries on, and the replay switches worlds where the recording did, also when seeking or
  exporting across it.
- Exports now include music, looping sounds and moving sounds (minecarts, elytra, bees): sounds
  follow their source and the camera while they play, and loops repeat until they stop.
- With OneConfig (for example in OneClient): Reizo's messages show as OneConfig notifications, and
  exports show their progress in one. Can be turned off on Reizo's OneConfig page.
- Depth of field (strength and focus distance) and bloom in the Effects panel, keyframed like
  the other effects.
- The recorded player in replays now holds its items, wears its armor and shows its pose
  (crouching, swimming, sleeping) and item use.
- Settings over time (timeline → Settings), like Rewind's mod properties: keyframe Minecraft's
  brightness, distortion effects, entity shadows and view bobbing, and with OneConfig (for example
  in OneClient) every switch and slider of every OneConfig mod. Your own values come back when the
  replay closes.
- Motion blur for exports (export settings): 4, 8 or 16 renders averaged per frame over a 180°
  shutter.
- Exported videos have the game's sound, rendered in sync from the sounds that play during the
  export ("Sound" in the export settings).
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
