# AGENTS.md

Universal instructions for every coding agent (Claude, ChatGPT/Codex, others) and human
contributor. Read this file, then [docs/AGENT_PROTOCOL.md](docs/AGENT_PROTOCOL.md) and
[docs/HANDOFF.md](docs/HANDOFF.md), before changing anything. GitHub is the source of truth;
do not rely on chat history.

## Purpose

An independent, open-source Minecraft recording, replay and video editing mod (Fabric), aiming at
feature parity with Lunar Client's Rewind, with optional OneClient/OneConfig integration. Details:
[docs/PROJECT.md](docs/PROJECT.md).

## Hard constraints

1. **Repository stays private** until the owner explicitly approves making it public. Never change
   visibility or publish releases.
2. **No proprietary or incompatible code.** Never copy, translate or closely port code from
   **Flashback** or **Hindsight** (both "All Rights Reserved") or Lunar Client. Their behavior and
   published design may inform ours; their source text may not. The owner also asked that we use
   **nothing from Replay Mod** (code, file format or libraries) even though it is GPL.
3. **License is GPL-3.0-or-later.** Any third-party code must be GPL-3.0-compatible and recorded
   in `docs/DECISIONS/` before it is added.
4. **No claims of official support** by Polyfrost, OneClient, Lunar Client or Mojang.
5. **Never mark a feature verified** without an executed automated test or a documented manual
   check (docs/FEATURES.md). Visual behavior that only a human can judge stays "untested" until
   someone looks at it.
6. **No placeholder implementations** to make the repo look complete. Unimplemented features are
   absent and listed as planned.
7. **No secrets** in the repository.

## Repository structure

```
core/      Minecraft-independent engine (pure Java 21, Gson only). Format, recording, storage.
fabric/    Fabric client mod for Minecraft 1.21.11 (Mojang mappings). Thin adapter over core.
  src/main/       mod code, mixins, resources
  src/gametest/   in-game end-to-end tests (Fabric client gametest API)
docs/      Project documentation, ADRs, handoff
.github/   CI workflows, issue and PR templates
```

Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). File format:
[docs/FORMAT.md](docs/FORMAT.md).

## Architecture rules

- `core` must never import Minecraft, Fabric, Netty or OneConfig classes. If logic can be written
  without them, it belongs in `core` with unit tests.
- `fabric` code adapts the game to `core`: mixins, events, rendering, UI. Keep it thin.
- Mixins capture data and call one static bridge (e.g. `PacketTap`); no logic in mixins.
- Game threads never do disk I/O during recording; the writer thread owns files.
- Recording must never crash or disconnect the game: catch, log once, continue.
- Changing the on-disk format requires updating docs/FORMAT.md, bumping the version constant, and
  keeping old files readable or documenting why not.
- Architectural changes (new module, new dependency, format change, threading model change) need
  an ADR in `docs/DECISIONS/` and a pull request reviewed by the owner before merge.

## Coding standards

- Java 21. Formatting by google-java-format via Spotless (`./gradlew spotlessApply`). Every source
  file starts with the GPL SPDX header (Spotless adds it).
- `core` compiles with `-Xlint:all -Werror`.
- Prefer immutability, explicit lifecycle (`start`/`stop`/`close`), and dependency injection over
  globals. Inject clocks for anything time-based so it can be tested.
- Validate untrusted input (files on disk are untrusted): bounded allocations, clear exceptions.
- Comments explain *why*; Javadoc on public types.

## Build and test commands

```sh
./gradlew build                          # everything CI runs except gametests
./gradlew :core:test                     # fast unit tests
./gradlew spotlessApply                  # fix formatting
xvfb-run -a ./gradlew :fabric:runClientGameTest   # real Minecraft client tests (needs a display or Xvfb)
./gradlew :fabric:runClient              # manual testing
```

## Git workflow

- `main` is protected by convention: changes land through pull requests.
- Branch names: `feat/<topic>`, `fix/<topic>`, `docs/<topic>`, `chore/<topic>`, `agent/<agent>-<topic>`.
- [Conventional Commits](https://www.conventionalcommits.org/): `feat(core): ...`, `fix(fabric): ...`,
  `docs: ...`, `test: ...`, `build: ...`, `ci: ...`, `refactor: ...`.
- Link substantial work to a GitHub issue (`Closes #N`).
- Never force-push shared branches; never rewrite `main`; never discard another agent's work.
- Semantic versioning; record user-visible changes under "Unreleased" in CHANGELOG.md.

## Documentation and handoff requirements

Before ending a session: update docs/HANDOFF.md (all 12 sections), docs/FEATURES.md status, and
CHANGELOG.md; commit and push. Full checklist: [docs/AGENT_PROTOCOL.md](docs/AGENT_PROTOCOL.md).

## Current constraints

- Single target: Minecraft 1.21.11 Fabric. Multi-version support comes later (ADR 0002).
- Playback does not exist yet, so recordings can only be verified by decoding, not by watching.
- OneConfig integration is planned, not implemented. The mod must keep working without OneConfig.
