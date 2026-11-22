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
| Shadow (rolling buffer) recording | ✓ 30 s–10 min | ? | ✓ 2 min | ✗ | planned | — | M5 |
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

## Cinematic camera (planned, M3)

Position/rotation/FOV/roll keyframes, path visualization, linear/Catmull-Rom/Bézier
interpolation, easing, look-at tracking, camera shake, depth of field. Lunar modes to match: POV,
Free Camera, Follow, Force First Person, Force Third Person Back/Front. Status: **planned**.

## Timeline editor (planned, M4)

Tracks (Lunar: effects, video, sound), zoom/scroll, trim/split ("cutting tool"), unlink media,
move between tracks, keyframe editing with interpolation and fade per keyframe, multi-select
keyframes, text/image overlays, media library with folders, undo/redo, shortcuts, copy/paste,
autosave, recovery, non-destructive projects. Status: **planned**.

## Visual effects (planned, M5)

Motion blur, depth of field, bloom, color correction, exposure, contrast, saturation, vignette,
camera shake, chromatic aberration, blur, fades, letterboxing, keyframed intensity, chroma key,
resource pack / shader per clip, HUD elements (scoreboard, boss bar) in POV. Status: **planned**.

## Audio (planned, M6)

Game audio (re-synthesized from recorded sound events), microphone, Simple Voice Chat (only where
permitted; see privacy section to be written), per-track volume, mute/solo, fades, sync, preview,
mixdown. Status: **planned**.

## Export (planned, M4/M5)

| Feature | Lunar | Status |
| --- | --- | --- |
| MP4, MKV, MOV, WebM (Lunar also AVI, WebP) | ✓ | planned |
| H.264, HEVC, VP9, AV1 | ✓ | planned |
| NVENC / VAAPI / QSV hardware encode with libx264 fallback | NVENC + libx264 | planned |
| Resolution, orientation, frame rate | ✓ | planned |
| Audio on/off, stereo/mono, 22.05–96 kHz | ✓ | planned |
| Render region (in/out) | ✓ (Jul 2026) | planned |
| Frame sequence, screenshot export, render queue, presets, progress, cancel | ? | planned |

## OneClient integration (planned, M7)

OneConfig settings page, keybinds in OneConfig's keybind page, OneConfig notifications, editor
entry point in the OneClient main menu, styling consistent with PolyCompose. Must remain optional.
Status: **planned**.
