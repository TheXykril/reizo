# Reizo

An independent, open-source Minecraft **recording, replay and video editing** mod for Fabric, with
optional integration for [OneClient](https://modrinth.com/modpack/oneclient-modpack).

Record a session, open it later, fly a free camera through it, cut it on a timeline, add camera
paths and effects, and export a video, all inside Minecraft. Lunar Client's Rewind is the
functional reference.

> **Not affiliated with or endorsed by** Lunar Client, Polyfrost, OneClient or Mojang. No
> proprietary code or assets are used. See [docs/PROJECT.md](docs/PROJECT.md#independence).

## Status

**Pre-alpha. Recording works; playback, editing and export do not exist yet.**

| Area | State |
| --- | --- |
| Recording (packets + local player, crash-safe, recovery) | Implemented, verified by automated in-game tests |
| Playback, free camera | Planned (next milestone) |
| Timeline editor, keyframes, effects, audio, export | Planned |

The verified feature list lives in [docs/FEATURES.md](docs/FEATURES.md). Nothing is listed as
working without an executed test or a documented manual check.

## Requirements

- Minecraft **1.21.10** or **1.21.11** (Java 21+), or **26.1.2**, **26.2** or **26.3** (Java 25+), with Fabric
  Loader ≥ 0.20.1 and Fabric API. Each version has its own jar. Versions other than 1.21.11 are new
  and only tested by the automated in-game tests, not yet by hand.

## Using it (current build)

- **Shift+R** starts and stops recording, like Lunar. The key is rebindable under Controls → Recorder
  and the modifier is in Reizo settings (Replays → Settings).
- Pause/resume and "Add marker" keys are available but unbound by default.
- Recordings are saved to `.minecraft/reizo/recordings/*.reizo`.
- If the game crashes while recording, the recording is recovered on the next launch.

## Building

```sh
./gradlew build                     # compile, format check, unit tests, mod jar
./gradlew :fabric:1.21.11:runClient         # development client
xvfb-run -a ./gradlew :fabric:1.21.11:runClientGameTest   # in-game end-to-end tests (headless)
```

Each Minecraft version builds its own jar, in `fabric/versions/<version>/build/libs/`. See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Documentation

| Document | Contents |
| --- | --- |
| [AGENTS.md](AGENTS.md) | Rules for every contributor and coding agent |
| [docs/PROJECT.md](docs/PROJECT.md) | Vision, scope, constraints |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Modules and how they fit together |
| [docs/FORMAT.md](docs/FORMAT.md) | Recording file format |
| [docs/FEATURES.md](docs/FEATURES.md) | Feature matrix vs. Lunar Rewind |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Milestones |
| [docs/RESEARCH.md](docs/RESEARCH.md) | OneClient, Replay Mod, Flashback, Hindsight, Lunar Rewind findings |
| [docs/HANDOFF.md](docs/HANDOFF.md) | Current state for the next session |

## Third-party assets

- [Inter](https://github.com/rsms/inter) (Inter Medium), the editor's UI font, is bundled under the
  SIL Open Font License 1.1 (`fabric/src/main/resources/licenses/Inter-OFL.txt`). The font is not
  covered by Reizo's GPL.

## License

[GPL-3.0-or-later](LICENSE). See [ADR 0003](docs/DECISIONS/0003-license.md).
