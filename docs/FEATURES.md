# Feature matrix

Reference: Lunar Client Rewind, from Lunar's public pages (see [RESEARCH.md](RESEARCH.md)).
Comparison columns for Flashback (FB), Hindsight (HS) and Replay Mod (RM) are from their public
descriptions, not verified by us.

**Status values:** `implemented` (works, with evidence), `partial` (some of it works; gaps listed),
`planned`, `blocked` (cannot proceed; reason given), `unsupported` (deliberately not doing it).
**Evidence:** `UT` unit test, `GT` in-game client gametest, `MV` manual validation (who/when),
`—` none yet. A feature without evidence cannot be `implemented`.

Last verified: 2026-11-22, commit see HANDOFF.md. Gametest evidence (GT) holds for Minecraft
1.21.11 and, since 2026-11-23, 26.3 (same 20 checks).

## Recording

| Feature | Lunar | FB | HS | RM | Status | Evidence | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Manual start/stop (hotkey) | Shift+R | ✓ | ✓ | ✓ | implemented | GT | Shift+R by default like Lunar (GT: R alone does nothing, Shift+R stops). Modifier set in Reizo settings, key in Controls |
| Start/stop from pause menu | ✓ | ✓ | ? | ✓ | implemented | GT | Top-left buttons: start/stop, pause/resume, Replays. Visual check pending |
| Recording indicator + elapsed time | ✓ | ✓ | ? | ✓ | partial | — | HUD drawn; visual check on real hardware pending |
| Pause / resume recording | ✓ | ✓ | ? | ✓ | implemented | UT | Clock freeze; packets keep flowing (see FORMAT.md). Key unbound by default |
| Markers | ✓ | ✓ | ✓ | ✓ | implemented | UT, GT | Key unbound by default; label "Marker" |
| Auto-record on join | ✓ | ✓ | ✓ | ✓ | implemented | GT | `autoRecordOnJoin` in config/reizo.json |
| Continuous across world/server switch | ✓ | ? | ? | ✗ | partial | GT | Server switches behind a proxy (the server sends the client back to configuration) stay in one recording and replay as a switch, with seeking and export across it (`recordOnDedicatedServer`, switch made with the server's own reconfiguration, `exportAcrossServerSwitch`). Not tested on a real Velocity/BungeeCord proxy. Leaving and joining another server or world still starts a new recording |
| Crash backup / recovery | ✓ | ? | ? | partial | implemented | UT, GT | Max ~1 s loss. GT `recoverAfterCrash`: a mid-recording copy of the working directory (no lock owner, torn tail) is recovered at startup, marked RECOVERED, keeps its marker and plays back. A real process kill is not tested |
| Shadow (rolling buffer) recording | ✓ 30 s–10 min | ? | ✓ 2 min | ✓ 30 s–10 min | implemented | UT, GT | Off by default; ADR 0011. Pause menu "Save last" or a key (unbound). Snapshot hitch not yet measured on real hardware |
| World/entity state reconstruction | ✓ | ✓ | ✓ | ✓ | partial | GT | All clientbound packets recorded byte-exact and re-decodable; basic playback (GT) |
| Local player movement & animation | ✓ | ✓ | ✓ | ✓ | partial | GT | Position/rotation/pose/swing/flags per changed tick; replayed on the recorded player |
| Inventory / equipment | ✓ | ✓ | ✓ | ✓ | partial | GT | Inventory in recorded packets; own held items and armor recorded as equipment packets and shown on playback |
| Particles & sounds | ✓ | ✓ | ✓ | ✓ | partial | — | Server-sent ones are in packets; client-only effects not captured |
| Multiplayer recording | ✓ | ✓ | ✓ | ✓ | partial | GT | `recordOnDedicatedServer`: auto-record on joining a dedicated server over the network (compressed), server-spawned entity recorded and shown in playback; a server switch in the middle carries on and replays. Not tested: real proxies, real public servers |
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
  rotation, arm swing, pose (crouching, swimming, sleeping, elytra pose) and item use; held items
  and armor (GT `playBackWalk`: sword in hand, helmet worn and crouching at the end). Missing: the
  elytra and burning/invisible flags come only from the server's own packets.
- **Recorded player's HUD in player view (POV):** **partial**, evidence GT (`recordFromJoin`:
  `PLAYER_HUD` frames recorded and their items decode; `checkReplayControls`: the overlay is drawn
  in POV and hidden with its setting off; screenshot `reizo-replay-pov-hud`). Hotbar with the
  selected slot and offhand, hearts (absorption, hardcore), food, armor, XP and air, laid out like
  vanilla. Setting "Recorded player's HUD in POV", keyframeable as "Reizo" in the editor. Not
  shown yet: inventory and container screens, status effects, the attack indicator.
- **Recorded player's inventory and container screens in POV:** **partial**, evidence GT
  (`recordFromJoin`: the open inventory is recorded as `PLAYER_SCREEN` frames whose slots decode;
  `checkReplayControls`: the recorded inventory screen is drawn in POV without opening a real
  screen; screenshot `reizo-replay-pov-hud`). The game's own screen classes, with the recorded
  slots, mouse position, hovered slot, tooltip and cursor stack; the inventory shows the recorded
  player's model. Setting "Recorded player's inventory in POV", keyframeable under "Reizo".
  Screen and container packets no longer reach the replay camera, so a recorded chest no longer
  opens on the viewer and stays open. Not covered: creative tabs (shown as the survival
  inventory), screens without a menu type (horse, sign, book, merchant trades), furnace progress
  arrows.
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
- Frame step, timeline navigation with the mouse, follow any entity,
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
  - Layout (ADR 0015, docked editor): top bar (transport, time, speed, view Free/POV/Follow/Path,
    undo, redo, Export), tool rail (hide entity, effects, settings, panels), Scene outliner,
    Inspector (Camera, Look, World tabs, drag-number fields in folding sections), a menu bar, right-click menus, panels that float or dock by their title bars and the timeline. The outliner shows from 560 GUI pixels
    wide, the Inspector from 440; Tab folds both. The in-game tests run at 320x240, where only the
    top bar, rail and timeline show, so the side panels are covered by screenshots only.
  - Tracks: time ruler, markers, camera keyframes, look (effects), world (time and weather),
    entities (hidden entities), settings, export region (in/out), overview bar. Tracks with keys
    take the free rows first when the window is short. Clicking a key on a non-camera track seeks
    to it.
  - Mouse: click the ruler to seek, drag keyframes (Shift adds to the selection), box-select,
    double-click a keyframe to go to it, wheel zooms around the cursor, Shift+wheel scrolls.
  - Keys: Space, N, Delete, Ctrl+Z/Y, Ctrl+C/V (paste at the playhead), Ctrl+A, I/O, F, H, Tab, ← →.
  - Every change autosaves to the project; undo covers keyframes, smoothing and the export region.
- Effect, video and sound tracks, trim/split, per-keyframe interpolation and fades, text/image
  overlays, media library: **planned**.

## Visual effects (M5)

- **Implemented** (GT: gray picture with letterbox bars from a keyframe; ADR 0012): exposure,
  contrast, saturation, temperature, vignette and letterbox (1.85, 2, 2.39), keyframed over replay
  time with linear blending, in the Effects panel (timeline → Effects). Shown in the replay and in
  exports. No pass runs when the grade is neutral or outside replays. Covered by the timeline's undo; effect keyframes show as purple squares on the marker track.
- **Motion blur in exports:** **implemented**, evidence UT (`ExportTest`: render times over a
  180° shutter), GT (`exportRegionVp9` with 4 renders per frame still has one frame per frame).
  Export settings "Motion blur": off, 4, 8 or 16 renders averaged per frame; costs that many times
  the render time.
- **Settings over time:** **implemented** for Minecraft's settings, evidence GT
  (`checkKeyframedSettings`: the panel opens from the timeline, keyframes drive brightness and
  entity shadows, undo/redo, the player's values come back after leaving the replay), UT
  (`PropertyTracksTest`); ADR 0014. Timeline → Settings lists settings with a search box; a change
  sets a keyframe at the playhead; numbers blend, switches flip; teal squares on the marker track.
  OneConfig mod settings: **partial**, see OneClient integration.
- **Time of day and weather:** **implemented**, evidence GT (`checkTimeAndWeather` on 1.21.11 and
  26.3: keyframes for midnight, rain and thunder make the replay's level report them, screenshot
  `reizo-replay-night-storm` shows a rainy night while paused; removing them goes back to the
  recording). Timeline → Settings → Reizo: "Time of day (replay)" (shown as a clock time), "Rain"
  and "Thunder", blended between keyframes and shown in exports. The recording is unchanged.
- **Hide entities:** **implemented**, evidence GT (`checkHideEntity` on 1.21.11 and 26.3: a hidden
  entity is not drawn, is listed in the settings panel, and undo shows it again). H in a replay
  hides the entity the camera looks at (up to 96 blocks) from the current time on, or shows it
  again; each hidden entity is a keyframed switch "Hide <name>" in Timeline → Settings, saved with
  the project and applied in exports. The look-at picking itself is not covered by a test yet.
- **Depth of field and bloom:** **implemented**, evidence GT (`checkEffects`: depth of field
  focused at 0.5 blocks halves the frame's fine detail; bloom brightens 8% of the frame around the
  bright spots). Effects panel: "Bloom", "Depth of field" (strength) and "Focus (blocks)", keyframed
  like the other effects. Single pass in the effects shader (24-tap disc blur from the world's
  depth; luminance-thresholded glow with a per-pixel turned pattern). Not checked by a person on
  real hardware or with shader packs.
- **Planned:** camera shake, chromatic aberration, blur,
  fades, chroma key, resource pack / shader per clip, HUD elements (scoreboard, boss bar) in POV.

## Audio (M6)

- **Game sound in exports:** **implemented**, evidence GT (`exportVideo`: a sound played during
  the export is in the MP4's AAC track, audible, and starts within one frame of its video time; a
  looping sound that moves past the camera is followed for the second it plays and repeats in the
  track), UT (`AudioTest`: placement, pitch, distance, pan, looping, stopping, moving sounds and
  pitch changes, WAV, mux command). Sounds are rendered offline from the game's sound files,
  panned and attenuated from the camera, and added with FFmpeg (AAC for MP4/MKV, Opus for WebM).
  Sounds are followed while they play: moving sounds (minecarts, elytra) and the camera are
  sampled every frame, looping sounds repeat until the game stops them, and music is included.
  Loops that started while the export got ready (an entity's sound) play from the first frame.
  "Sound" on/off in the export settings (ADR 0013). Left out: one-shot sounds and music that
  started before the export range. Music and minecarts by ear on real hardware: **untested**.
- **Sound and music levels in exports:** **implemented**, evidence GT (`exportVideo` exports at
  sound 50%: the click's gain is at most 0.5). Export settings "Sound" and "Music" (Off, 25 to
  100%), on top of the game's own volume settings; stored in the project. Off leaves those sounds
  out.
- Microphone, Simple Voice Chat (only where permitted), fades: **planned**.

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
- **OneClient mod settings over time:** **partial**, evidence — (builds against the OneConfig
  1.9.1 config API; not run in OneConfig yet). Every switch and bounded number of every OneConfig
  config (OneClient's mods) is listed in timeline → Settings and can be keyframed like Rewind's
  Lunar mod properties. Needs a manual check in OneClient (ADR 0014).
- **Keybinds in OneConfig's keybind page:** **partial**, evidence — (Reizo uses vanilla key
  mappings, which OneConfig lists; not checked).
- **OneConfig notifications:** **partial**, evidence — (builds against the OneConfig 1.9.1
  notifications API; not run in OneConfig yet). With OneConfig installed, Reizo's messages
  (recording started or saved, shadow saved, recovery, export done or failed) show as OneConfig
  notifications typed info, success, warning or error, and an export shows one notification with
  a progress bar. Switch "OneConfig notifications" on Reizo's OneConfig page; any failure falls
  back to Minecraft toasts for the session. GT: without OneConfig, toasts stay vanilla.
- **Entry in OneClient's main menu:** **partial**, evidence GT (`checkMainMenuEntries`: a stand-in
  screen named like OneClient's menu gets a "Replays" button in its top-left corner that opens the
  replay browser; the "Open replays" key opens it from the title screen and that menu). OneClient
  has no API for menu entries, so its menu (`org.polyfrost.oneclient.gui.OneClientMenuScreen`) is
  recognized by class name; not run inside OneClient yet. The key (unbound by default) also opens
  the browser in game.
- PolyCompose styling: **planned**.
