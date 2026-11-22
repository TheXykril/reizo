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
- [ ] Modifier-key bindings (Shift+R default like Lunar)
- [ ] Multiplayer gametest against a dedicated server (`createServer` in the gametest API)
- [ ] Crash gametest: kill the client mid-recording, verify recovery
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

## M5 — Effects, overlays, shadow recording

- Post-processing pipeline (only active in editor/export), effect tracks with keyframed intensity
- Text/image overlays, transitions, letterboxing, HUD elements in POV
- Shadow recording (rolling buffer, save last N minutes)

## M6 — Audio

- Sound event re-synthesis for game audio, microphone capture, track mixing, fades, export mixdown
- Simple Voice Chat integration where permitted; privacy documentation

## M7 — OneClient integration and polish

- OneConfig settings, keybinds and notifications; main menu entry point; style alignment

## M8 — More Minecraft versions

Plan: [ADR 0007](DECISIONS/0007-multi-version-strategy.md) (accepted). Starts only after M1–M4 work
on 1.21.11.

- Stonecutter multi-version build, then Minecraft 26.3 (newest, OneClient mod's target) (#19)
- The rest of OneClient's versions: 1.21.1, 1.21.10, 26.1.2, 26.2 (#21)
- CI matrix: newest of each family on PRs, all versions on main (#22)
- Move timeline, camera-path and export logic into `core` so other adapters reuse it (#23)
- 1.8.9 spike on Ornithe (capture + file, Java-level question for `core`), then the full port (#24, #25)
- Not planned: 1.9–1.20.6 and Forge/NeoForge (new ADR if demand appears)
