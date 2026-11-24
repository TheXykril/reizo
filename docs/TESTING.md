# Testing

Testing is part of every change. A feature is `implemented` in FEATURES.md only with an executed
test or a documented manual check. Automated tests and untested visual behavior are always
reported separately.

## Layers

| Layer | Where | Runs in CI | What it proves |
| --- | --- | --- | --- |
| Unit | `core/src/test` (JUnit 5) | yes | Format, sessions, storage, recovery, controller logic |
| In-game (client gametest) | `fabric/src/gametest` | yes (Xvfb) | Real client + integrated server: capture works, files finalize, every packet decodes with Minecraft's codecs |
| Manual | checklist below | no | Visuals, feel, performance on real hardware |

## Current suites

- `FrameStreamTest` — round trip across blocks, unknown types, corruption (CRC), torn tail scan,
  append after recovery, oversized length guard, version checks.
- `RecordingMetadataTest` — JSON round trip, forward compatibility, invalid input.
- `LocalPlayerStateTest` — payload round trip and validation.
- `RecordingSessionTest` — timestamps (fake clock), pause semantics, prelude, idempotent stop,
  4-thread concurrent submission ordering, periodic metadata, I/O failure leaves recoverable data,
  writer thread exits (resource cleanup).
- `RecordingStorageTest` — live vs abandoned detection, full and partial recovery, unrecoverable
  data never deleted, unique sanitized names.
- `CaptureControllerTest` — mid-session prelude, complete-from-join detection, cache reset on
  reconfiguration, cache size cap, single session.
- `RecordingGameTest` (in-game) — auto-record from join (complete; config, login and chunk packets
  present; marker saved), manual mid-session recording while walking (incomplete flag, login
  prelude, ≥10 local player frames, player moved, duration > 1 s). Also playback, replay controls,
  timeline, effects (grade, depth of field, bloom), keyframed settings, recorded player equipment
  and pose, dedicated server recording (`recordOnDedicatedServer`), crash recovery
  (`recoverAfterCrash`), shadow recording, exports with sound and motion blur, and the recording
  cost benchmark (`measureRecordingCost`).

## Performance baseline

`measureRecordingCost` walks for 200 ticks while recording, then 200 ticks with shadow recording
on, and logs: MB per minute on disk, the client-thread cost per tick (player sample, equipment,
shadow segment snapshots) and the network-thread cost per captured packet (`RecorderStats`). It
fails only on gross regressions (2 ms per tick, 0.5 ms per packet, 30 MB/min, any dropped frame).

| Date | Machine | MB/min | µs per tick (recording) | µs per packet | µs per tick (shadow) | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-11-23 | Cloud container, Xvfb, llvmpipe | 0.08 | 65 | 35 | 78 | Flat test world, walking; ran at about 10 TPS under software rendering |

Still needed for #6: the same test and a spark profile on Hyperion during normal play on a busy
server (FPS with and without recording, allocation rate), recorded in this table.

## Planned suites (by milestone)

Recording compatibility across versions, playback state equality, timeline operations, camera interpolation, audio
sync, export validation (ffprobe the output), performance benchmarks (FPS delta, allocation rate,
MB/min, export fps), OneClient pack compatibility (run gametests with the OneClient mod list).

## Manual validation checklist (record results in HANDOFF.md with date and machine)

1. Dev client starts; "Recorder ready" in log.
2. Join a world; press Shift+R: toast appears; HUD shows blinking red dot and running time.
3. Walk, break blocks, fight a mob for 30 s; add a marker (bind the key first).
4. Press Shift+R: "Saving" then "Recording saved" toasts; file in `reizo/recordings/`.
5. Kill the game process while recording; restart: "Recovered" toast; file present.
6. Check FPS with and without recording (F3), note the difference.

## Commands

```sh
./gradlew :core:test
xvfb-run -a ./gradlew :fabric:runClientGameTest
```

Gametest logs: `fabric/build/run/clientGameTest/logs/latest.log`.

## Runs with other mods (manual, not in CI)

The run folder is rebuilt for every in-game test run, so extra mods and files are staged through
two environment variables:

- `REIZO_EXTRA_MODS=/dir`: every `*.jar` in `/dir` goes into the run's `mods` folder.
- `REIZO_EXTRA_RUN_FILES=/dir`: the contents of `/dir` (for example `config/iris.properties`
  and `shaderpacks/Pack.zip`) are copied into the run folder.

**Shaders** (`ShaderEffectsGameTest`, skipped without Iris): put Iris and Sodium for the
Minecraft version in the mods folder, a shader pack in `shaderpacks/`, and
`config/iris.properties` with `enableShaders=true` and `shaderPack=<file name>`. Iris needs
OpenGL, and 26.x picks Vulkan under Xvfb, so give it a 24-bit GLX screen and let SDL use EGL:

```sh
SDL_VIDEO_FORCE_EGL=1 REIZO_EXTRA_MODS=... REIZO_EXTRA_RUN_FILES=... LIBGL_ALWAYS_SOFTWARE=1 \
  xvfb-run -a -s "-screen 0 1280x1024x24 +extension GLX" ./gradlew :fabric:26.3:runClientGameTest
```

The shader test runs first and checks depth of field through the pack. The main suite runs after
it, but its timing checks (roll keys, for example) fail at the low frame rate shaders give in
software rendering, so read the result of the shader test only.

**PolyPlus** (`checkPolyPlusReplays`, skipped without PolyPlus): add the PolyPlus jar to the mods
folder.
