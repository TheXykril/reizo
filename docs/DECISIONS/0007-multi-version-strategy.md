# 0007: Multi-version strategy (1.8.9 to newest)

Status: Accepted (owner, 2026-11-22). It extends ADR 0001 and 0002 and does not replace them.

## Context

The owner would like Reizo on many Minecraft versions, "from 1.8 to the newest". The facts
(docs/RESEARCH.md, checked 2026-11-22):

- **OneClient** ships Fabric packs only for **1.21.1, 1.21.10, 1.21.11, 26.1.2, 26.2 and 26.3**.
  The OneClient mod itself targets 26.3.
- **OneConfig** supports 1.21.1–26.3 on Fabric and **1.8.9 on Ornithe**. Ornithe is the Fabric
  Loader plus Ornithe's own Gradle plugin and Feather mappings, for old versions. That is the
  Polyfrost-compatible way to reach 1.8.9. Forge 1.8.9 (ForgeGradle 2, a separate ecosystem) and
  Legacy Fabric would both mean a second toolchain that OneConfig does not use.
- 1.8.9 matters because the PvP community, Lunar Rewind's main audience, still plays it.
- Versions between them (1.9 to 1.20.6) are not shipped by OneClient. Each big protocol era
  changes what a replay has to reconstruct:
  - 1.9 combat and entity metadata
  - 1.13 block flattening
  - 1.14 and 1.18 chunk and light formats
  - 1.17 world height
  - 1.19 chat signing
  - 1.20.2 configuration phase
  - 1.20.5 item components
- A replay can only be played back by the same Minecraft version that recorded it. Converting
  recordings between versions is a separate, very large problem.

## Decision

Support versions in **two families**, each with its own adapter on top of the shared `core`:

1. **Modern family (Fabric, Mojang mappings): the OneClient versions.**
   - Versions: 1.21.1, 1.21.10, 1.21.11, 26.1.2, 26.2, 26.3.
   - One `fabric` source tree built for every version with **Stonecutter**, the tool Polyfrost's
     own mods use. Version differences go in `//? if` comment blocks or small per-version shims.
   - 26.x needs Java 25 and Loom 1.18+. 1.21.x stays on Java 21. Stonecutter's per-version
     properties cover both.
2. **Legacy family (Ornithe, 1.8.9 only).**
   - A separate `legacy-1.8.9` adapter module, because the client internals share almost nothing
     with 1.21 (LWJGL 2, fixed-function rendering, no configuration phase, different packet
     classes).
   - Capture stays at the client packet decoder (ADR 0004 still holds conceptually), but the
     mixins, the playback world, the camera, the rendering and the export hooks are all written
     again for 1.8.9.

**Not planned:** 1.9 to 1.20.6, and Forge/NeoForge. Each would add an adapter of its own at close
to the cost of 1.8.9, for versions OneClient doesn't ship. They can be reopened later, with a new
ADR, if there is demand.

### What changes in the code layout

- `core` stays free of Minecraft, and more editor logic moves into it, so the legacy adapter
  re-implements as little as possible:
  - stream format, session, storage
  - timeline and keyframe model, camera paths and interpolation
  - project files
  - export pipeline (FFmpeg process, frame queue)
- Adapters only do capture hooks, the playback world, rendering, input and screens.
- The format already records `protocolVersion` and `minecraftVersion`. Readers must refuse a
  recording made by another version and say clearly why.
- **Java level of `core`:** Minecraft 1.8.9 normally runs on Java 8. Whether Ornithe's 1.8.9 can
  load a Java 17/21 `core` is unverified. Until a spike answers that, `core` must not grow
  dependencies that rule out a Java 8 build. If Java 8 turns out to be required, `core` gets a
  Java 8 target (records and other newer syntax replaced) or is compiled twice. This is the biggest
  open risk.

### Order

1. Finish the vertical slice on 1.21.11 first (M1–M4). Multi-version work must not start before
   recording, playback, the camera and the first export work on one version.
2. Introduce Stonecutter and add **26.3** (newest; the OneClient mod's own target).
3. Add the rest of the OneClient set: 1.21.1, 1.21.10, 26.1.2, 26.2.
4. **1.8.9 spike**: Ornithe dev environment, capture plus a recording file, and the answer to the
   Java-level question. Then decide on the full 1.8.9 port.
5. Full 1.8.9 port (playback, camera, editor, export).

## Consequences

+ Covers every version OneClient ships, plus the most-requested legacy version.
+ The modern family is cheap to extend: a new version mostly costs a few `//? if` blocks and a CI
  matrix entry.
+ Moving more into `core` also makes the code easier to test.
− CI time grows with each version. The CI matrix runs gametests for a few versions on PRs (the
  newest of each family) and all versions on `main`, to keep Actions usage modest.
− 1.8.9 is a second implementation of every rendering-side feature, roughly as much work as the
  first adapter. Every editor feature after that costs extra to keep at parity.
− Ornithe is a smaller ecosystem than Fabric, and its tooling must be verified before committing
  (spike).
− Cross-version playback is not offered.

## Rough cost (one agent, full-time equivalent)

| Step | Estimate |
| --- | --- |
| Stonecutter setup + 26.3 | 1–2 weeks (Loom 1.18, Java 25, API changes) |
| Each further modern version | 1–3 days |
| 1.8.9 spike | 1 week |
| Full 1.8.9 port, to feature parity at the time | 4–8 weeks, then ongoing parity cost |
| Each extra version outside these two families | similar to 1.8.9 |

These are guesses from the scope, not measurements. Re-estimate after the Stonecutter step and the
1.8.9 spike.

## Implementation notes: Stonecutter and 26.3 (2026-11-23)

Step 2 of the order above. Recorded here because it fixes how every later version is added.

- **Build.** Stonecutter 0.10 (Gradle plugin portal) in `settings.gradle.kts`, versions
  `1.21.11` (active, the version the sources are written for) and `26.3`. Each version is a project
  `:fabric:<version>` with its own `gradle.properties`. Loom 1.19.4 needs Gradle 9.8 and a JDK 25 to
  run Gradle; 1.21.11 still compiles with a Java 21 toolchain. 1.21.x uses the remapping Loom plugin
  (`fabric-loom-remap`, Mojang mappings); 26.x is unobfuscated and uses `fabric-loom` without
  mappings.
- **Where differences go**, cheapest first:
  1. Plain renames: regex rules with a reverse, at the end of `fabric/build.gradle.kts` (for
     example `GuiGraphics` to `GuiGraphicsExtractor`, `mc.screen` to `mc.gui.screen()`).
  2. Small accessors: `fabric/compat/Compat.java`, one version block per method.
  3. Everything else: `//? if >=26.1` blocks where the code differs. The biggest is
     `EffectRenderer`, because 26.x replaced Blaze3D's GPU API with "renderpearl" (bind group
     layouts, SPIR-V shaders, reversed depth).
- **Version-neutral code where possible.** Key codes use `InputConstants.KEY_*` (26.x moved from
  GLFW to SDL scancodes), chunk coordinates use `SectionPos`. The effects shader body is shared
  (`shaders/include/grade_body.glsl`) with a header per version.
- **Mixins** are not checked at compile time. Each mixin's targets were checked against the 26.3
  sources; four needed new targets (camera placement and field of view, frame hooks, the export
  clock). The gametests on each version are what proves them.
- **Recordings stay per version**, as decided above: a 1.21.11 recording does not play on 26.3.
- **CI** builds every version and runs the gametests for both 1.21.11 and 26.3. With only two
  modern versions that is still within the "newest of each family" budget; revisit when 1.21.1 and
  the other OneClient versions are added.

## Implementation notes: 1.21.10 and 26.2 (2026-11-23)

- **Versions** `1.21.10` and `26.2` added beside 1.21.11 and 26.3. Both compile from the same
  sources and pass all 20 gametests in a cloud session (headless; 26.2 on lavapipe).
- **Version blocks** can now chain: `//? if >=26.3 { … } else if >=26.1 { … } else { … }`. 26.2 is
  between the two families: it has 26.x's render-state names and reversed depth, but 1.21.11's GLFW
  key codes, `swinging`/`swingTime`, a nullable previous game mode and the `com.mojang.blaze3d` GPU
  API (no renderpearl, GLSL 330 shaders). Blocks that were `>=26.1` and hold one of those are now
  `>=26.3`. Rename rules have a version predicate (`is26`, `is263`, `before12111`).
- **1.21.10 differences:** `ResourceLocation` (a rename that skips `SPDX-License-Identifier`), no
  gizmos (the camera path is drawn as plain lines before the debug renderer), samplers bound
  without sampler objects, `CycleButton.builder(label)` (through `Compat.cycle`), `Camera.getYRot`,
  `Screen.init(mc, w, h)` and the older horse-screen packet name.
- **Export across a server switch** waited forever on 26.2: client ticks follow the export clock,
  and the loading screen only closes after a tick, so the clock now runs in real time while
  nothing is captured.
- **CI:** pull requests run the gametests for 1.21.11 and 26.3 (newest of each family); pushes to
  main run all four.
