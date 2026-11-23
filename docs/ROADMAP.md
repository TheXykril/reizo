# Roadmap

Milestones follow dependency order: recording and playback foundations before any advanced UI.
Each milestone should leave `main` in a working state. GitHub milestones mirror these names.

## M1 — Recording foundation *(in progress)*

Goal: reliable, crash-safe recording of singleplayer and multiplayer sessions.

- [x] Build, CI, docs, agent handoff system
- [x] Stream format v1, sessions, storage, crash recovery (unit tested)
- [x] Byte-exact packet capture at the decoder, local player sampling (gametested)
- [x] Hotkey, HUD indicator, toasts, auto-record on join
- [x] Pause-menu recording controls (Lunar: ESC → hover indicator → Record)
- [x] Modifier-key bindings (Shift+R default like Lunar)
- [x] Multiplayer gametest against a dedicated server (`createServer` in the gametest API)
- [x] Crash gametest: simulated kill mid-recording (copied working directory with a torn tail), verify recovery
- [ ] Performance baseline: FPS, allocation rate, MB/min recorded

## M2 — Playback and free camera (vertical slice steps 5–7)

- Replay world: feed recorded packets into a client-side level with a fake connection
- Local player reconstruction from `LOCAL_PLAYER` frames
- Play/pause, speed, seek (restart-and-fast-forward first, then keyframe snapshots)
- World snapshots at recording start and at intervals (fast seeking, complete mid-session starts)
- Free camera, spectate entity, first/third person
- Replay browser screen (list, rename, delete, Quick View)

## M3 — Projects and cinematic camera

- Project file format (references recordings, non-destructive), autosave and recovery
- Camera keyframes: position, rotation, FOV, roll; linear, Catmull-Rom, Bézier; easing
- Path visualization in the world, look-at targets, camera shake

## M4 — Timeline editor and first export (vertical slice steps 8–9)

- Editor screen: tracks, clips, trim/split, zoom/scroll, keyframe lanes, undo/redo, shortcuts
- Deterministic frame-by-frame renderer (fixed timestep, no wall-clock dependence)
- FFmpeg export (system FFmpeg first; bundled/downloaded later), H.264 software + hardware, progress, cancel
- Auto highlights: while recording, detect kills, deaths, big falls, clutches (a save just before
  fall damage or the void) and PvP combos, and add them as typed markers on the timeline. Jump
  between them in the replay, and export a montage of the chosen ones in one click (a few seconds
  before and after each, with an optional fade). Detection runs on the client from packets the
  recording already has (death messages, health, combat events), so old recordings can be scanned
  too.
- Vertical 9:16 export for Shorts, Reels and TikTok: a crop or a second camera that reframes
  automatically to keep the recorded player in frame, with smoothing so it doesn't jitter, and
  keyframes to override it. Presets for 1080x1920.

## M5 — Effects, overlays, shadow recording

- ✓ (first slice, ADR 0012) Post-processing pipeline (only active in editor/export), effect tracks with keyframed intensity
- Text/image overlays, transitions, letterboxing
- ✓ (first slice) Recorded player's HUD in POV, like Lunar Rewind: hotbar with the selected slot
  and offhand, health, hunger, armor, XP and air from `PLAYER_HUD` frames. A setting and a
  keyframeable "Reizo" toggle, applied in export.
- ✓ (second slice) The inventory and container screens the recorded player opened, drawn with the
  game's own screens from `PLAYER_SCREEN` frames, with the recorded mouse, hovered slot and
  cursor stack. Its own setting and keyframeable toggle. Creative tabs show as the survival
  inventory.
- ✓ Shadow recording (rolling buffer, save last N minutes; ADR 0011)

## M6 — Audio

- ✓ (first slice, ADR 0013) Sound event re-synthesis for game audio, export mixdown
- Microphone capture, track mixing, fades, export mixdown
- Simple Voice Chat integration where permitted; privacy documentation

## M7 — OneClient integration and polish

- OneConfig settings, keybinds and notifications; main menu entry point; style alignment
- OneClient's own HUD elements showing the recorded player in replays (spike first; dropped if
  the spike fails, with no Reizo-drawn replacement). OneClient's HUD elements come from Polyfrost's
  EvergreenHUD (GPL-3.0, OneConfig HUD API). During a replay they measure the person watching:
  CPS and Keystrokes count OneConfig's mouse and key events; Reach, Combo, Armor, Position and the
  like read `mc.player`, which is the replay camera, not the recorded player.
  1. Record the recorded player's attack, use and movement key presses as Reizo frames.
  2. In POV, post them as OneConfig `MouseInputEvent` / `KeyInputEvent` through its event bus so
     CPS and Keystrokes count them, without moving the camera or firing keybinds.
  3. Point the elements that read `mc.player` at the recorded player while in POV (a mixin on
     EvergreenHUD, so it depends on its internals and may need updating with its releases). Its
     Reach and Combo check that the attacker is `mc.player` and not a spectator.
  4. Keyframeable show/hide per element in the editor; they draw in exports like the rest of the
     HUD.
- Distant terrain (LOD) in replays and exports with Distant Horizons and Voxy (optional, needs a
  spike first). During a replay the client is connected to Reizo's local replay server, so both
  mods see an unknown server with no LOD data and show nothing past the render distance.
  1. Spike: find out how each mod names its LOD storage (per server and dimension), and whether it
     has an API to choose the storage or feed LODs (Distant Horizons has a public API; Voxy's is
     less settled). Check which Minecraft versions each supports.
  2. Reuse: during a replay, point the mod at the LOD data of the server the recording came from,
     read-only. This adds nothing to the recording, but it only works on the machine that recorded,
     and it shows today's terrain rather than the terrain at the time of the recording.
  3. Embed, optional: copy the LOD area around the recording into the `.reizo` file, so shared
     replays show distant terrain too. This makes files much larger, so it would be a setting with
     a radius.
  Also check whether server-sent LODs (Distant Horizons can receive them from a server plugin) are
  already captured in recordings and replay correctly. A replay can't answer the mod's requests,
  so they probably don't.

## M9 — Editing studio (semi-full)

Goal (Xykril, 2026-11-23): Reizo should feel like a semi-full editing studio inside the game, made
for Minecraft clips. Cover what a clip needs: cuts, several clips, transitions, titles and basic
audio. Out of scope: a full color-grading suite and multitrack audio mixing like a pro editor.

- Clips: cut a recording into clips, trim and split them, and put clips from one or more
  recordings in order on one timeline; ripple delete and move. Each clip keeps its own camera path,
  settings and effects.
- Transitions between clips: cut, fade through black, crossfade, and a few wipes, with a length.
- Speed per clip: slow motion and fast forward with eased speed ramps (keyframed replay speed).
- Titles and text: keyframed text overlays with a font, size, color, outline and position; simple
  in/out animations (fade, slide, typewriter); title-card presets for intros and outros.
- Image overlays: a logo or watermark with position, size and opacity.
- Audio: game sound and music levels per clip, fades in and out, one music or voice-over track
  from a file with a volume curve, and the recorded microphone (M6). No full mixer.
- Basic look: the existing color grade (exposure, contrast, saturation, temperature) per clip with
  a few presets; letterbox bars and aspect-ratio presets.
- Render presets: YouTube 1080p60, 4K, Shorts 1080x1920 (M4 vertical export), GIF, and a frame
  sequence; a render queue that exports several projects or regions one after another.

## M10 — Creative replay controls

Basics first (Lunar's Rewind has the first three):

- ✓ Time of day and weather keyframes in replays: override the recorded time and weather (clear,
  rain, thunder) for a shot or a timelapse, blended between keyframes, shown in exports.
- Hide entities: click a mob, item or player in the replay (or pick from a list) to hide it from
  that point, keyframeable, also in exports.
- Hide or show groups: all players, all mobs, items, particles, name tags.

Then:

- Privacy mode: one switch to hide or replace other players' names, skins and chat in exports.
- Chat as subtitles: recorded chat shown as styled, keyframeable captions instead of the chat box.
- Green screen and depth export: export without the sky (a solid key color or alpha), or a depth
  pass, so editors can composite over other footage.
- 360° and VR export: equirectangular video with the metadata YouTube needs to show it in 360.
- Ghost compare: play a second recording as a translucent ghost player next to the first, for
  speedruns and parkour.
- Share links: upload a recording or an export and get a link (needs a host; decide privacy and
  cost first, so an ADR before building).
- The rolling buffer that saves the last few minutes already exists (shadow recording, M5).

## M8 — More Minecraft versions

Plan: [ADR 0007](DECISIONS/0007-multi-version-strategy.md) (accepted). Starts only after M1–M4 work
on 1.21.11.

- Stonecutter multi-version build, then Minecraft 26.3 (newest, OneClient mod's target) (#19).
  Done on branch `feat/multi-version` (in review): both versions pass all 20 gametests.
- The rest of OneClient's versions: 1.21.1, 1.21.10, 26.1.2, 26.2 (#21). 1.21.10, 26.1.2 and 26.2
  done (20/20 gametests each). 1.21.1 is a separate port: it predates the GPU device API, the HUD
  element registry, key mapping categories and several packets the code uses (about 100 compile
  errors), so it waits until after the editor UI overhaul.
- ✓ CI matrix: newest of each family on PRs, all versions on main (#22).
- Move timeline, camera-path and export logic into `core` so other adapters reuse it (#23)
- 1.8.9 spike on Ornithe (capture + file, Java-level question for `core`), then the full port (#24, #25)
- Not planned: 1.9–1.20.6 and Forge/NeoForge (new ADR if demand appears)
