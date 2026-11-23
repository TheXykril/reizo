# Feature matrix

Reference: Lunar Client Rewind, from Lunar's public pages (see [RESEARCH.md](RESEARCH.md)).
Comparison columns for Flashback (FB), Hindsight (HS) and Replay Mod (RM) are from their public
descriptions, not verified by us.

**Status values:** `implemented` (works, with evidence), `partial` (some of it works; gaps listed),
`planned`, `blocked` (cannot proceed; reason given), `unsupported` (deliberately not doing it).
**Evidence:** `UT` unit test, `GT` in-game client gametest, `MV` manual validation (who/when),
`—` none yet. A feature without evidence cannot be `implemented`.

Last verified: 2026-11-22, commit see HANDOFF.md.

## Recording

| Feature | Lunar | FB | HS | RM | Status | Evidence | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Manual start/stop (hotkey) | Shift+R | ✓ | ✓ | ✓ | implemented | GT | Shift+R by default like Lunar (GT: R alone does nothing, Shift+R stops). Modifier set in Reizo settings, key in Controls |
| Start/stop from pause menu | ✓ | ✓ | ? | ✓ | implemented | GT | Top-left buttons: start/stop, pause/resume, Replays. Visual check pending |
| Recording indicator + elapsed time | ✓ | ✓ | ? | ✓ | partial | — | HUD drawn; visual check on real hardware pending |
| Pause / resume recording | ✓ | ✓ | ? | ✓ | implemented | UT | Clock freeze; packets keep flowing (see FORMAT.md). Key unbound by default |
| Markers | ✓ | ✓ | ✓ | ✓ | implemented | UT, GT | Key unbound by default; label "Marker" |
| Auto-record on join | ✓ | ✓ | ✓ | ✓ | implemented | GT | `autoRecordOnJoin` in config/reizo.json |
| Continuous across world/server switch | ✓ | ? | ? | ✗ | planned | — | Currently stops on disconnect |
| Crash backup / recovery | ✓ | ? | ? | partial | implemented | UT | Max ~1 s loss; GT for real crash pending |
| Shadow (rolling buffer) recording | ✓ 30 s–10 min | ? | ✓ 2 min | ✓ 30 s–10 min | implemented | UT, GT | Off by default; ADR 0011. Pause menu "Save last" or a key (unbound). Snapshot hitch not yet measured on real hardware |
| World/entity state reconstruction | ✓ | ✓ | ✓ | ✓ | partial | GT | All clientbound packets recorded byte-exact and re-decodable; basic playback (GT) |
| Local player movement & animation | ✓ | ✓ | ✓ | ✓ | partial | GT | Position/rotation/pose/swing/flags per changed tick; not yet replayed |
| Inventory / equipment | ✓ | ✓ | ? | ✓ | partial | — | Present in recorded packets; untested on playback |
| Particles & sounds | ✓ | ✓ | ✓ | ✓ | partial | — | Server-sent ones are in packets; client-only effects not captured |
| Multiplayer recording | ✓ | ✓ | ✓ | ✓ | partial | — | Same pipeline as singleplayer; no multiplayer test yet |
| Start mid-session with full world | ✓ | ✓ | ? | ✗ | partial | GT | Config + login prelude only; loaded chunks missing until world snapshots (M2) |
| Replay metadata (version, server, player, markers) | ? | ✓ | ✓ | ✓ | implemented | UT, GT | |
| Configurable storage location | ? | ✓ | ? | ✓ | implemented | — | `recordingsDirectory` setting |
| Microphone / system audio capture | ✓ | ✗ | ? | ✗ | planned | — | M6, privacy notes required |

## Playback (M2)

- **Play a recording into the client world:** **partial**, evidence GT (`playBack`: the recorded
  world is entered through the replay server, all play packets delivered, chunks loaded, the replay
  is not recorded). Started from the replay browser (or the dev-only `/reizo-debug play`). ADR 0008.
- **Recorded player shown during playback:** **partial**, evidence GT (`playBack`, `playBackWalk`:
  separate player entity with the recorded UUID; after a recorded walk it ends at the recorded end
  position within 0.5 blocks; also works for mid-session recordings). Movement, rotation, head
  rotation and arm swing only. Sneaking, held items and armor are not shown yet.
- **Spectator camera during playback:** **partial**, evidence GT (camera is in spectator mode with
  its own identity). Plain spectator flight; no speed or roll controls yet.
- **Camera modes free / player view / follow (V key):** **partial**, evidence GT
  (`checkReplayControls`: the key cycles the modes, the camera entity and perspective match). Not
  visually checked by a person yet.
- **Pause and speed (K, `[` and `]`):** **partial**, evidence GT (`checkReplayControls`: pause
  freezes the client world's ticking, speed 2x sets its tick rate to 40). Speeds 1/8x to 8x. The
  free camera also moves faster or slower with the speed, because the client's own tick rate
  changes (to be decoupled later).
- **Recordings started mid-session show the world:** **implemented**, evidence UT
  (`CaptureControllerTest`: snapshot after the prelude, not taken for complete starts) and GT
  (`recordMidSessionWhileWalking`: chunks in the recording, decode with Minecraft's codecs;
  `playBackWalk`: loading screen closes, chunks load; screenshot `reizo-mid-session-replay`). At
  record start `WorldSnapshot` writes the dimension, chunks within render distance, entities with
  data and equipment, tab list with skins, time, weather and world border. Missing: scoreboards,
  boss bars, map data, block entities' extra client-only state, biome zoom seed.
- **Seek (← / → by 5 s, `,` / `.` to markers) and the replay time bar:** **partial**, evidence
  GT (`checkReplayControls`: ← after the end rebuilds the world through a reconfiguration, chunks
  load again, the camera is still a spectator, and → seeks forward. `checkSeeking`: back to the
  start puts the recorded player back near the start, forward to the end puts it at the end within
  0.5 blocks). A backwards seek replays from the start, so it is slow for long recordings until
  snapshots exist. The time bar shows play state, time, speed, camera mode and markers; it is not
  visually checked yet.
- Frame step, timeline navigation with the mouse, follow any entity, recorded player pose and equipment,
  markers in timeline, Quick View: **planned**.

## Replay browser (#10)

- **List, play, rename, delete, open folder:** **implemented**, evidence UT (`listRecordings`,
  `rename`, `delete` in `RecordingStorageTest`) and GT (`browseAndPlay`: opened from the title
  screen button, lists the recordings, Play starts the replay; screenshot `reizo-replay-browser`).
  Opened from the title screen ("Replays", left of Multiplayer) and the pause menu. Recordings from
  another Minecraft version are listed but cannot be played. Double-click plays.
- Thumbnails, search, sorting options, Quick View: **planned**.

## Cinematic camera (M3)

- **Camera keyframes and path playback:** **partial**, evidence UT (`CameraPathTest`: sorting,
  replace, linear and Catmull-Rom through keyframes with continuous velocity, short-way yaw,
  easing) and GT (`checkCameraPath`: N adds a keyframe at the camera; B plays the path; the
  rendered camera is on the path at the replay time; the project is saved; screenshot
  `reizo-camera-path`). Keys: N add, Delete remove nearest, B play/stop, J smoothing (Catmull-Rom
  or linear, with or without ease in/out). Position, yaw and pitch come from the camera. Roll and
  FOV are stored and applied (mixins on `Camera` and `GameRenderer.getFov`).
- **Free camera roll and FOV (hold U / I to roll, - / = to zoom, 0 resets):** **partial**,
  evidence GT (`checkFreeCamera`: holding I rolls 10°, holding - narrows the FOV by 10, N stores
  both in the keyframe, 0 resets). Applied live to the free camera.
- **Camera path shown in the world (G toggles):** **partial**, evidence GT (screenshot
  `reizo-path-display`: curve, keyframe boxes with view arrows, current position; G hides it).
  Drawn with Minecraft's gizmos; hidden with F1, during export and while the path plays. Not
  visually checked by a person yet.
- **Free camera kept across backwards seeks:** **implemented**, evidence GT (`checkTimeline`: the
  camera is within 0.5 blocks of where it was after a ruler seek back).
- **Projects:** **implemented**, evidence UT (`ProjectStoreTest`) and GT (saved project read back).
  One project per recording, created on first replay, autosaved on every change to
  `reizo/projects/<id>.reizoproj`. The recording is referenced, never copied.
- Bézier handles, look-at tracking, camera shake, depth of field: **planned**.

## Timeline editor (M4)

- **Timeline panel during a replay (Y, or "Timeline editor" in the pause menu):** **partial**,
  evidence UT (`TimelineTest`: zoom around the cursor, scrolling limits, ruler spacing, undo/redo,
  group move, copy/paste, box selection) and GT (`checkTimeline`: Y opens it; a mouse drag moves a
  keyframe; Ctrl+Z and Ctrl+Y undo and redo it; I sets and Shift+I clears the export start; a click
  on the ruler seeks; the project is saved; screenshot `reizo-timeline`). Not visually checked by a
  person yet.
  - Tracks: time ruler, markers, camera keyframes, export region (in/out), overview bar.
  - Mouse: click the ruler to seek, drag keyframes (Shift adds to the selection), box-select,
    double-click a keyframe to go to it, wheel zooms around the cursor, Shift+wheel scrolls.
  - Keys: Space, N, Delete, Ctrl+Z/Y, Ctrl+C/V (paste at the playhead), Ctrl+A, I/O, F, ← →.
  - Buttons: play/pause, add, delete, path on/off, smoothing, in, out, undo, redo, export.
  - Every change autosaves to the project; undo covers keyframes, smoothing and the export region.
- Effect, video and sound tracks, trim/split, per-keyframe interpolation and fades, text/image
  overlays, media library: **planned**.

## Visual effects (M5)

- **Implemented** (GT: gray picture with letterbox bars from a keyframe; ADR 0012): exposure,
  contrast, saturation, temperature, vignette and letterbox (1.85, 2, 2.39), keyframed over replay
  time with linear blending, in the Effects panel (timeline → Effects). Shown in the replay and in
  exports. No pass runs when the grade is neutral or outside replays. Covered by the timeline's undo; effect keyframes show as purple squares on the marker track.
- **Planned:** motion blur, depth of field, bloom, camera shake, chromatic aberration, blur,
  fades, chroma key, resource pack / shader per clip, HUD elements (scoreboard, boss bar) in POV.

## Audio (planned, M6)

Game audio (re-synthesized from recorded sound events), microphone, Simple Voice Chat (only where
permitted; see privacy section to be written), per-track volume, mute/solo, fades, sync, preview,
mixdown. Status: **planned**.

## Export (M4/M5)

Frame-exact export through the user's FFmpeg (ADR 0009). "Export video" in the pause menu (or
Export in the timeline) opens the export settings: size, frame rate, quality, format (only the
encoders the installed FFmpeg has), file name and the In/Out range. Evidence GT (`exportVideo`: the settings screen opens from the pause menu and offers H.264 and VP9;
20 fps H.264 export, 93 planned frames, 93 frames in the MP4 by ffprobe; `exportRegionVp9`: VP9 in
WebM of the middle half at 10 fps, codec and frame count checked by ffprobe) and UT (`ExportTest`:
options per encoder, VAAPI upload chain, encoder list parsing).

| Feature | Lunar | Status |
| --- | --- | --- |
| Deterministic frame-by-frame rendering | ✓ | implemented (GT) |
| MP4 / H.264 (libx264) | ✓ | implemented (GT) |
| MKV, WebM; HEVC, VP9, AV1 | ✓ | partial: H.264 and VP9/WebM tested in-game (GT); H.265 and SVT-AV1 options checked with a real FFmpeg by hand |
| NVENC / VAAPI / QSV hardware encode | NVENC + libx264 | partial: offered when FFmpeg has them; command options unit tested; never run on a GPU (needs a check on Hyperion: VAAPI/QSV) |
| Resolution, frame rate, quality | ✓ | implemented: settings screen (GT opens it); the game renders at the output size during an export (GT: 640×360 from a 320×240 window), falling back to window size plus FFmpeg scaling past the GPU's texture limit. While exporting, the window shows the bottom-left part of each frame |
| Render region (in/out) | ✓ (Jul 2026) | implemented (GT): I and O in the timeline; `exportRegionVp9` exports only that range |
| Progress, cancel | ? | implemented: progress in the window title, Esc cancels |
| Audio on/off, stereo/mono, 22.05–96 kHz | ✓ | planned (M6) |
| Frame sequence, screenshot export, render queue, presets | ? | planned |

## OneClient integration (M7)

Optional: works only when OneConfig is installed (for example in OneClient), and Reizo runs the same
without it (ADR 0010). Not affiliated with or endorsed by Polyfrost or OneClient.

- **OneConfig settings page:** **partial**, evidence — (builds against the OneConfig 1.9.1 API;
  not run in OneConfig yet, manual check needed). Page "Reizo" under Utility: Replays button,
  start/stop recording button, auto-record, indicator, save server name, start/stop modifier,
  FFmpeg path, shadow recording on/off, length and a "Save last minutes" button. Values are
  shared with Reizo's own settings screen.
- **Recording indicator as a OneConfig HUD element:** **partial**, evidence — (builds against the
  OneConfig 1.9.1 HUD API; not run yet). "Reizo recording" (Info) shows REC/PAUSED with the time,
  or "Shadow on", and can be placed in OneConfig's HUD editor. Hidden while nothing records.
  Registration failures are caught, so Reizo keeps working with another OneConfig version.
- **Keybinds in OneConfig's keybind page:** **partial**, evidence — (Reizo uses vanilla key
  mappings, which OneConfig lists; not checked).
- OneConfig notifications, editor entry in the OneClient main menu, PolyCompose styling:
  **planned**.
