# Handoff

Snapshot of the project state for the next session. **Verify against `git log` and the source
before trusting it** (docs/AGENT_PROTOCOL.md).

Last updated: 2026-11-22 by Claude (path display, roll/FOV).

## 1. Current project phase

M1 recording, M2 playback (browser, camera modes, pause/speed, seeking, start snapshots) and the
start of M3 (camera keyframes, projects) are on `main`. M4/M5 export first slice (PR #35) and the
optional OneConfig page (M7 first slice, ADR 0010) are in open PRs. Not started: timeline editor
screen, effects, audio, shadow recording, multi-version.

## 2. Latest completed tasks

- Research: OneClient, OneConfig, Lunar Rewind, Flashback, Hindsight (docs/RESEARCH.md).
- Owner decisions: license GPL-3.0-or-later; name **Reizo**; Flashback-inspired clean-room design;
  nothing from Replay Mod; repository public since 2026-11-22.
- Build: Gradle 9.5.1 wrapper, Loom 1.17.21, Minecraft 1.21.11, Mojang mappings, Spotless, CI.
- `core`: stream format v1, metadata, local player payload, recording session (writer thread,
  pause, markers), storage (locks, packaging, recovery), capture controller (config cache).
- `fabric`: decoder mixin capture, local player sampler, Shift+R toggle + pause/marker keys, HUD
  indicator, toasts, auto-record on join, startup recovery, debug commands (dev only).
- Playback first slice (#7, ADR 0008): `core.playback` (PlaybackClock, ReplayScript) and
  `fabric.playback` (ReplayServer on 127.0.0.1, ReplayPlayback), `/reizo-debug play [file]`,
  capture suppressed during replays.
- Recorded player in playback (#7 part 2, branch `feat/playback-recorded-player`, stacked on
  #28): `PlayTransformer` rewrites login/respawn for a spectator camera, drops packets aimed at the
  camera, and drives a separate recorded-player entity from `LOCAL_PLAYER` samples.
- Replay camera and controls (#7/#9, branch `feat/replay-camera-controls`, stacked on #29):
  `ReplayCamera` (free / POV / follow on V), pause (K) and speed (`[` `]`) via the replay server's
  `ClientboundTickingStatePacket` (recorded tick rate × speed, frozen when paused).
- Replay browser (#10) and pause-menu recording buttons (#2), branch `feat/replay-browser`:
  `ReplayBrowserScreen`, `RenameScreen`, `MenuButtons` (Fabric screen events, no mixins), and
  `RecordingStorage.listRecordings/rename/delete` (unit tested).
- Seeking (#8 first version), branch `feat/replay-seeking`: forward = clock jump; backward =
  `ClientboundStartConfigurationPacket` restart and fast-forward (`ReplayServer.restart`), with
  `ReplayPlayback.tick` waiting for the new level. `ReplayHud` time bar. Known gap: recordings
  started mid-session have no chunks, so the client stays on the loading screen during playback.
- Mid-session start snapshot, branch `feat/mid-session-snapshot`: `WorldSnapshot` (fabric) builds
  play packets from client state; `CaptureController.start(meta, snapshot)` appends them after the
  cached prelude; `RecordingMetadata.startSnapshot`.
- Camera keyframes and projects (M3 start), branch `feat/camera-keyframes`: `core.camera`
  (`CameraKeyframe`, `CameraPath`), `core.project` (`Project`, `ProjectStore`), fabric
  `ReplayEditor` (project per recording, autosave), `CameraMixin` (per-frame position/rotation/roll
  from the path), `GameRendererMixin` (FOV).
- Video export (#14 first slice, ADR 0009), branch `feat/video-export`: `core.export`
  (`ExportPlan`, `FfmpegCommand`), `ReplayServer` manual mode + ping/pong frame sync,
  `MinecraftMixin` virtual timer, `ExportSession`, `FrameEncoder`, `ExportControls`. CI installs
  ffmpeg for the gametest.
- Multi-version plan accepted (ADR 0007, issues #19, #21–#25).
- CI gametests made reliable on slow runners (320x240 window, cheap render options; #27).
- Tests: 37 unit tests; 5 client gametest scenarios (record from join, play back, record
  mid-session while walking, play back the walk).
- Docs: AGENTS.md, CLAUDE.md, docs/*, 8 ADRs, templates.

## 3. Current branch and commit

- `main`: everything up to the OneConfig page (#28–#36 merged 2026-11-22).
- `main` also has the timeline editor (#37).
- `feat/path-visualization`: path display, free camera roll/FOV, camera kept across seeks; PR open.
  Merge workflow for stacked PRs: squash-merge, then merge `origin/main` into the next branch and
  resolve with `git checkout --ours -- .` after checking `git diff --quiet origin/main <merged head>`.

## 4. Active implementation work

- OneConfig page (`fabric/compat/OneConfigIntegration`): builds against the OneConfig 1.9.1 API but
  has never run inside OneConfig. The cloud proxy blocks OneConfig's runtime dependencies
  (repo.hypixel.net, jitpack), so it needs a manual check in OneClient on Hyperion: page shows under
  Utility, buttons work, values match Reizo's settings screen, Reizo's keys appear in OneConfig's
  keybind page, and whether OneClient's main menu hides the title screen "Replays" button.
- Timeline editor (`fabric/ui/TimelineScreen`, `core.timeline`): ruler, markers, camera keyframes,
  export region, overview; drag, box-select, copy/paste, undo/redo (`ReplayEditor` history of
  `TimelineState` snapshots). Test-input gotcha: Fabric's `TestInput` sends key events with
  modifiers 0, so screens must also read `Minecraft.hasControlDown()`/`hasShiftDown()`.
- Seeks are clamped to `ReplayScript.startMs()` (the first play packet). Before that there is no
  world, and a paused replay seeked there stayed on the reconfiguration screen.
- Path display (`PathVisualizer`) uses vanilla gizmos from `WorldRenderEvents.END_EXTRACTION`,
  wrapped in `levelRenderer.collectPerFrameGizmos()`: frames rendered outside `runTick` (the
  gametest screenshot path) have no collection open, and per-tick gizmos are not drained while the
  world is frozen (paused replay).
- Next: export settings
  UI and rendering at output size, shadow recording (#16), audio (#17), effects (#15), recorded
  player pose and equipment, multiplayer and crash gametests (#4, #5), performance baseline (#6).

## 5. Files and modules affected

Everything (initial import): `core/`, `fabric/`, `docs/`, `.github/`, root build files.

## 6. Important decisions

ADRs 0001–0010 in docs/DECISIONS/. Most important for the next agent:
- Capture at `PacketDecoder` (ADR 0004): bytes are exact; phase comes from the decoder.
- Pause freezes the clock but keeps capturing packets (FORMAT.md "Time").
- `core` must stay Minecraft-free.
- Clean-room rule (ADR 0005): never copy or port Flashback/Hindsight code; nothing from Replay Mod.

## 7. Known bugs and blockers

- Mid-session recordings: the start snapshot covers chunks, entities, tab list, time, weather and
  border, but not scoreboards, boss bars or map data yet.
- Fabric's play DISCONNECT event fires on the Netty thread and Minecraft drops queued client tasks
  while disconnecting; `stopRecording()` is therefore thread-safe and called directly. Keep it that
  way (a regression made the first gametest hang).
- Default key is Shift+R like Lunar: the base key R is a vanilla key mapping, the modifier is the
  `toggleModifier` setting (`KeyModifier`), because vanilla key mappings have no modifiers.
- HUD indicator not yet visually checked on real hardware (only rendered under Xvfb).
- Empty private repo `TheXykril/rewind` exists from before the rename; the owner should delete it
  (the session could not rename or delete repositories).
- Cloud environment note: the session's environment needed Fabric/Mojang hosts allow-listed
  (done 2026-11-22). The Mojang session server is not reachable, which only produces harmless
  auth errors in dev logs.

## 8. Test results

2026-11-22, cloud box, path display and roll/FOV (branch `feat/path-visualization`):
- `./gradlew build`: pass (72 unit tests). Gametests: pass (twice for the camera restore), adding
  `checkFreeCamera` and a camera-position check after a backwards seek. Screenshot
  `reizo-path-display` checked: curve, boxes, arrows and the current-position dot are drawn.

2026-11-22, cloud box, timeline editor (branch `feat/timeline-editor`):
- `./gradlew build`: pass (72 unit tests). Gametests: pass, adding `checkTimeline` (Y opens the
  editor; mouse drag moves a keyframe; Ctrl+Z/Ctrl+Y; I and Shift+I; Space pauses and resumes; a
  ruler click seeks; project saved). Screenshot `reizo-timeline` checked: panel, ruler, keyframes,
  playhead and wrapped buttons look right at 320x240.

2026-11-22, cloud box, OneConfig page (branch `feat/oneconfig`):
- `./gradlew build`: pass (59 unit tests). OneConfig could not be loaded at runtime here (its
  dependencies' repositories are blocked by the cloud proxy), so the page itself is untested.

2026-11-22, cloud box, video export (branch `feat/video-export`):
- `./gradlew build`: pass (59 unit tests). Gametests: pass, adding `exportVideo` (93/93 frames,
  a middle frame shows the recorded player walking, HUD hidden).

2026-11-22, cloud box, camera keyframes (branch `feat/camera-keyframes`):
- `./gradlew build`: pass (55 unit tests). Gametests: pass, adding `checkCameraPath`.

2026-11-22, cloud box, start snapshot (branch `feat/mid-session-snapshot`):
- `./gradlew build`: pass (44 unit tests). Gametests: pass; the walking (mid-session) replay now
  loads chunks and leaves the loading screen.

2026-11-22, cloud box, seeking (branch `feat/replay-seeking`):
- `./gradlew build`: pass (42 unit tests). Gametests: pass, adding seek checks in
  `checkReplayControls` (keys) and `checkSeeking` (positions after back/forward seeks).

2026-11-22, cloud box, replay browser (branch `feat/replay-browser`):
- `./gradlew build`: pass (40 unit tests). Gametests: pass, adding `browseAndPlay` (title screen
  button → browser lists recordings → Play starts the replay) and the pause menu "Start recording"
  button starting the walking recording. Screenshots in `fabric/build/run/clientGameTest/screenshots`.

2026-11-22, cloud box, camera and controls (branch `feat/replay-camera-controls`):
- `./gradlew build`: pass (37 unit tests). Gametests: pass, all scenarios plus
  `checkReplayControls` (V cycles POV/follow/free with matching camera entity and perspective; K
  freezes the client tick manager; `]` sets tick rate 40, `[` back to 20).

2026-11-22, cloud box, recorded player (branch `feat/playback-recorded-player`):
- `./gradlew build`: pass (37 unit tests). Gametests: pass, all 5 scenarios. `playBackWalk`:
  recorded player entity ended at the recorded end position (mid-session recording, synthesized tab
  entry); `playBack`: camera is a spectator with its own UUID, recorded player has the recorded
  UUID; leaving the replay ends it within a tick.

2026-11-22, cloud box, playback slice (branch `feat/playback-replay-server`):
- `./gradlew build`: pass (37 unit tests).
- `xvfb-run -a ./gradlew :fabric:runClientGameTest`: pass. `playBack`: 94/94 play packets delivered
  over 3.7 s, 49 chunks loaded, replay player has the recorded UUID, replay not recorded,
  connection closed cleanly on disconnect.
- Not tested: playback of multiplayer recordings, long recordings, manual visual check.

2026-11-22, cloud box (Ubuntu 24.04, JDK 21.0.13, Mesa llvmpipe under Xvfb):
- `./gradlew build`: pass (34 unit tests, 0 failures).
- `xvfb-run -a ./gradlew :fabric:runClientGameTest`: pass, 2/2
  (`recordFromJoin`: config=34 play=242 packets, all decode; `recordMidSessionWhileWalking`:
  45 local player frames, player moved, incomplete flag correct).
- Recording size sample: 2.2 s mid-session walk = 23 KB archive (158 KB uncompressed stream).
- Not tested: multiplayer server, real crash, real GPU/hardware, Wayland, OneClient pack, performance.

2026-11-22, Hyperion (owner laptop: Arch Linux, Hyprland on Wayland, Intel Arc Meteor Lake-P iGPU,
i915 driver, no NVIDIA), branch `feat/recording-foundation` at c72bb9e:
- Environment: JDK 21.0.13 (Arch `java-21-openjdk`) is present; the default `java` on PATH is
  sdkman Temurin 25, and `java-27-openjdk` is also installed. FFmpeg has libx264, libx265,
  SVT-AV1, VAAPI (h264/hevc/av1/vp9), QSV, Vulkan and NVENC/AMF encoders listed. `glxinfo` and
  `vainfo` are not installed; `nvidia-smi` fails (no NVIDIA hardware).
- Gotcha: `~/.gradle/gradle.properties` sets `org.gradle.java.home` to sdkman Java 25, which
  overrides `JAVA_HOME`. Run Gradle with `-Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk`.
- `./gradlew build -x spotlessCheck`: pass (34 unit tests, 0 failures).
- `spotlessJavaCheck` fails locally on every file with `NoSuchMethodError ...
  Log$DeferredDiagnosticHandler.getDiagnostics()` even with the daemon on JDK 21.0.13 (CI passes
  the same check on Temurin 21.0.13). Cause not found yet; likely a local classpath/JDK mix-up.
- `./gradlew :fabric:runClient`: client starts on Wayland, "Recorder ready" logged. Only error is
  the missing `libflite` narrator library (harmless). F9 not tested: nobody at the keyboard.
- `:fabric:runClientGameTest`: first world loaded and "Recording started ... complete from join:
  true" logged, then the run stalled at about 1 FPS in `glfwSwapBuffers` because the laptop
  display was off (DPMS). Rerun with the screen on (2026-11-22, by the Hyperion session): **pass,
  2/2 in 24 s**.

## 9. Uncommitted or unpushed changes

None at the end of this session (everything pushed).

## 10. Next recommended actions

1. Owner: delete the empty `TheXykril/rewind` repo. (PR #20 is merged.)
2. Owner on Hyperion (Arch/Hyprland): run and paste results of
   `java -version; gh auth status; ffmpeg -hide_banner -encoders | grep -E "264|265|av1|vaapi|nvenc|qsv"; glxinfo -B | head -20; vainfo 2>&1 | head; nvidia-smi 2>&1 | head -5`
   then `./gradlew :fabric:runClient` and walk through docs/TESTING.md manual checklist.
3. Next agent: finish #7 (recorded player reconstruction, spectator-style camera player), then #9,
   #8, #10. M1 leftovers: #4 "Multiplayer recording gametest", #2 "Pause-menu recording controls".

## 11. Relevant GitHub issues and pull requests

- Repository: https://github.com/TheXykril/reizo (public)
- PR #20: initial recording foundation (branch `feat/recording-foundation`); tracks issue #1.
- Milestones M1–M8 with issues #1–#19.


## 12. Warnings for the next agent

- The repository is public (owner-approved). Do not publish releases without approval.
- Loom 1.18+ needs Java 25; staying on 1.17.21 keeps Java 21 working. Upgrade deliberately (ADR).
- When writing mixins, read the generated Minecraft sources first
  (`./gradlew :fabric:genSourcesWithVineflower`); names are Mojang mappings.
- Gametests: never block the test thread waiting on the game; use `context.waitFor(...)`.
- The file magic is `REISTRM\0`; the block magic `RTBK` is unrelated to the name and stays.
