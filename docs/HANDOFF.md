# Handoff

Snapshot of the project state for the next session. **Verify against `git log` and the source
before trusting it** (docs/AGENT_PROTOCOL.md).

Last updated: 2026-11-22 by Claude (initial setup session).

## 1. Current project phase

M1 "Recording foundation", first slice done. Vertical slice progress: steps 1–4 (launch dev
client, start, record, stop and save) work and are covered by automated in-game tests. Steps 5–9
(open, play, free camera, project, export) are not started.

## 2. Latest completed tasks

- Research: OneClient, OneConfig, Lunar Rewind, Flashback, Hindsight (docs/RESEARCH.md).
- Owner decisions: license GPL-3.0-or-later; name **Reizo**; Flashback-inspired clean-room design;
  nothing from Replay Mod; repository private.
- Build: Gradle 9.5.1 wrapper, Loom 1.17.21, Minecraft 1.21.11, Mojang mappings, Spotless, CI.
- `core`: stream format v1, metadata, local player payload, recording session (writer thread,
  pause, markers), storage (locks, packaging, recovery), capture controller (config cache).
- `fabric`: decoder mixin capture, local player sampler, F9 toggle + pause/marker keys, HUD
  indicator, toasts, auto-record on join, startup recovery, debug commands (dev only).
- Tests: 34 unit tests; 2 client gametests.
- Docs: AGENTS.md, CLAUDE.md, docs/*, 6 ADRs, templates.

## 3. Current branch and commit

- `main`: initial commit (license + README).
- `feat/recording-foundation`: all work above, open as PR #1 (see section 11).
  Check `git log --oneline` for the exact head commit.

## 4. Active implementation work

None in progress. The PR awaits owner review.

## 5. Files and modules affected

Everything (initial import): `core/`, `fabric/`, `docs/`, `.github/`, root build files.

## 6. Important decisions

ADRs 0001–0006 in docs/DECISIONS/. Most important for the next agent:
- Capture at `PacketDecoder` (ADR 0004): bytes are exact; phase comes from the decoder.
- Pause freezes the clock but keeps capturing packets (FORMAT.md "Time").
- `core` must stay Minecraft-free.
- Clean-room rule (ADR 0005): never copy or port Flashback/Hindsight code; nothing from Replay Mod.

## 7. Known bugs and blockers

- **Mid-session recordings are incomplete:** chunks/entities loaded before start are missing
  (flagged `completeFromJoin=false`, toast warns). Fix = world snapshots (M2 issue).
- Fabric's play DISCONNECT event fires on the Netty thread and Minecraft drops queued client tasks
  while disconnecting; `stopRecording()` is therefore thread-safe and called directly. Keep it that
  way (a regression made the first gametest hang).
- Default key is F9, not Lunar's Shift+R (needs modifier support, issue in M1).
- HUD indicator not yet visually checked on real hardware (only rendered under Xvfb).
- Empty private repo `TheXykril/rewind` exists from before the rename; the owner should delete it
  (the session could not rename or delete repositories).
- Cloud environment note: the session's environment needed Fabric/Mojang hosts allow-listed
  (done 2026-11-22). The Mojang session server is not reachable, which only produces harmless
  auth errors in dev logs.

## 8. Test results

2026-11-22, cloud box (Ubuntu 24.04, JDK 21.0.13, Mesa llvmpipe under Xvfb):
- `./gradlew build`: pass (34 unit tests, 0 failures).
- `xvfb-run -a ./gradlew :fabric:runClientGameTest`: pass, 2/2
  (`recordFromJoin`: config=34 play=248 packets, all decode; `recordMidSessionWhileWalking`:
  45 local player frames, player moved, incomplete flag correct).
- Recording size sample: 2.2 s mid-session walk = 23 KB archive (158 KB uncompressed stream).
- Not tested: multiplayer server, real crash, real GPU/hardware, Wayland, OneClient pack, performance.

## 9. Uncommitted or unpushed changes

None at the end of this session (everything pushed to `feat/recording-foundation`).

## 10. Next recommended actions

1. Owner: review and merge PR #1; delete the empty `TheXykril/rewind` repo.
2. Owner on Hyperion (Arch/Hyprland): run and paste results of
   `java -version; gh auth status; ffmpeg -hide_banner -encoders | grep -E "264|265|av1|vaapi|nvenc|qsv"; glxinfo -B | head -20; vainfo 2>&1 | head; nvidia-smi 2>&1 | head -5`
   then `./gradlew :fabric:runClient` and walk through docs/TESTING.md manual checklist.
3. Next agent: M1 issues "Multiplayer recording gametest" and "Pause-menu recording controls",
   then start M2 "Replay world".

## 11. Relevant GitHub issues and pull requests

- Repository: https://github.com/TheXykril/reizo (private)
- PR #1: initial recording foundation (branch `feat/recording-foundation`).
- Milestones M1–M8 and backlog issues were created; see the Issues tab.

## 12. Warnings for the next agent

- Do not make the repository public or publish releases.
- Loom 1.18+ needs Java 25; staying on 1.17.21 keeps Java 21 working. Upgrade deliberately (ADR).
- When writing mixins, read the generated Minecraft sources first
  (`./gradlew :fabric:genSourcesWithVineflower`); names are Mojang mappings.
- Gametests: never block the test thread waiting on the game; use `context.waitFor(...)`.
- The file magic is `REISTRM\0`; the block magic `RTBK` is unrelated to the name and stays.
