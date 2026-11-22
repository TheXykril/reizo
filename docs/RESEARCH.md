# Research (2026-11-22)

Sources were read on 2026-11-22. Re-verify anything older than a few months before relying on it.

## OneClient (Polyfrost)

- **Distribution:** a Fabric modpack on Modrinth (`oneclient-modpack`, GPL-3.0-only), updated
  daily (v2.0.572 on 2026-11-22), for Minecraft **1.21.1, 1.21.10, 1.21.11, 26.1.2, 26.2, 26.3**.
  It is a launcher plus pack, not a separate loader: everything is ordinary Fabric.
- **1.21.11 pack contents (144 files):** Fabric Loader 0.20.1, Fabric API 0.141.6, Fabric Language
  Kotlin, **OneConfig 1.9.1**, **PolyCompose 3.0.3** (Compose UI for screens/overlays), Polyfrost's
  Poly* mods, Sodium 0.8.14, Sodium Extra, Iris 1.10.8, ImmediatelyFast, Lithium, ModernFix,
  EntityCulling, **Simple Voice Chat 2.6.24**, Mod Menu, YACL, Cloth Config, and others. No replay
  mod is bundled.
- **OneClient mod** (`Polyfrost/OneClient`, GPL-3.0): replaces the title screen with the OneClient
  menu. README lists Minecraft 26.3 only. Requires OneConfig and Fabric Language Kotlin. Built with
  Stonecutter multi-version.
- **OneConfig** (`Polyfrost/OneConfig`, LGPL-3.0 + "Additional Terms Applicable to OneConfig"):
  supported on 1.21.1–26.3 Fabric and 1.8.9 Ornithe. Modules: `config`, `config-impl`, `hud`,
  `notifications`, `ui`, `events`, `utils`, `internal`. Maven: `https://repo.polyfrost.org/releases`,
  artifacts `org.polyfrost.oneconfig:<mc>-fabric:<ver>` and `org.polyfrost.oneconfig:<module>:<ver>`
  (1.9.1 published). Config API: subclass `org.polyfrost.oneconfig.api.config.v1.Config` with
  annotated fields (`@Switch`, ...). Right Shift opens the OneConfig screen.
- **Extension points for us:** OneConfig config page, keybinds page, notifications, HUD editor;
  OneClient main menu (no public API for adding entries found; would need a mixin or a PR to
  Polyfrost). Conclusion: integrate as an **optional** OneConfig adapter; standalone Fabric
  remains the base.
- **Licensing note:** linking to OneConfig is fine for a GPL mod. Read the Additional Terms before
  shipping the integration.

## Lunar Client Rewind (functional reference)

From lunarclient.com news posts ("How to use Rewind", "Rewind editor for content creators",
"Monthly recap July 2026"). Launch date not stated; likely late 2025.

- Built into Lunar on all versions. Shift+R start/stop; ESC → hover Rewind indicator → "Record
  Rewind". Pause and marker keys must be bound manually.
- Settings: Record Microphone, Record System Audio, auto-record on join, continuous recording
  across switches, crash backup.
- **Shadow Rewind:** rolling buffer, 30 s to 10 min, save with a key or "Save Shadow".
- Main menu "Rewind Editor": list of rewinds and projects; **Quick View** or **Create Project**
  (name, resolution, orientation, frame rate, audio type).
- Editor: preview with Sync/Flight modes; toolbar with cut, unlink, fit timeline, pause, rewind,
  skip. Camera modes: POV, Free Camera, Follow, Force First Person, Force Third Person Back/Front.
- Timeline: effects, video and sound tracks; effect/text/image layers; keyframes via a diamond
  button per property with per-keyframe interpolation and fade; keyframable audio fade, zoom,
  brightness, color, and Lunar mod properties; multi-select keyframes (July 2026).
- Overlays: any Lunar mod as an effect (e.g. Color Saturation), text, images, HUD elements in POV.
  Effects named: camera shake, chroma key, resource packs, shaders.
- Media tab with import and folders (July 2026).
- Export: formats MP4/MKV/MOV/AVI/WebP/WebM; codecs H.264/HEVC/VP9/AV1; encoder NVENC or libx264;
  resolution; audio on/off, stereo/mono, 22.05–96 kHz; "Render Region" in/out (July 2026). FFmpeg
  is downloaded on first export (~100 MB, per a third-party article).
- Transitions are not mentioned.

## Flashback (Moulberry)

- Fabric, Minecraft up to 26.3, source on GitHub, **license "All rights reserved. Do not
  redistribute."** (localization files CC BY 4.0). We may not copy or port its code.
- Public design ideas we follow (implemented independently): record clientbound packets; snapshot
  the world at recording start so mid-session recordings are complete; periodic snapshots for fast
  seeking; editor with keyframe tracks and export inside Minecraft.

## Hindsight (Hardline Interactive)

- Fabric/NeoForge, Minecraft 1.21.1, 1.21.11, 26.2, 26.3 (Fabric only for 26.3). Version 1.1.1
  (Nov 2026). **All Rights Reserved**; closed source.
- Features: build timelapses (server side), session replays (client only), instant clip of last
  two minutes, camera paths, orbit camera, photo mode to 8K, export up to 4K incl. vertical, 360°,
  green screen, MP4/WebM/PNG with a queue, captions, motion blur, Simple Voice Chat playback,
  ReplayMod/Flashback conversion.
- Useful as a feature benchmark only.

## Replay Mod

GPL-3.0-or-later, Fabric/Forge, very wide version range. **Excluded by owner decision**: no code,
format or libraries. Mentioned here only so agents do not re-research it.

## Tooling decisions informed by research

- Fabric Loom 1.17.21 (newest running on Java 21; 1.18+ needs Java 25), Gradle 9.5.1.
- Mojang mappings for 1.21.11; 26.x is unobfuscated with Mojang names, so porting is mostly API
  changes, not renames.
- Fabric client gametest API runs a real client headlessly under Xvfb: used for end-to-end tests.
- FFmpeg: system binary first; Lunar's approach (download on first export) is a later option.
  Encoders on Linux: libx264/libx265/SVT-AV1 software; NVENC (NVIDIA), VAAPI (AMD/Intel), QSV.
