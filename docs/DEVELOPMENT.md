# Development

## Requirements

- JDK 25 to run Gradle (Loom 1.19 requires it). The build also uses a Java 21 toolchain for `core`
  and the 1.21.x builds; Gradle finds an installed JDK 21 or downloads one via the Foojay resolver.
- Git. Nothing else: the Gradle wrapper fetches Gradle 9.8.0, Loom fetches Minecraft.
- Network access to `maven.fabricmc.net`, `piston-meta.mojang.com`, `piston-data.mojang.com`,
  `libraries.minecraft.net`, `resources.download.minecraft.net`, Maven Central and the Gradle plugin
  portal.
- For in-game tests without a display: `xvfb-run` and Mesa (software OpenGL works).

## Commands

| Command | What it does |
| --- | --- |
| `./gradlew build` | Compile, Spotless check, unit tests, one mod jar per Minecraft version |
| `./gradlew :core:test` | Unit tests only (seconds) |
| `./gradlew spotlessApply` | Format code, add license headers |
| `./gradlew :fabric:1.21.11:runClient` | Dev client in `fabric/versions/1.21.11/run/` with `-Dreizo.debug=true` |
| `xvfb-run -a ./gradlew :fabric:1.21.11:runClientGameTest` | In-game end-to-end tests (also `:fabric:26.3:…`) |
| `./gradlew :fabric:1.21.11:genSourcesWithVineflower` | Decompiled Minecraft sources (Mojang names) for reading |
| `scripts/check.sh` | Everything CI runs, plus gametests if a display/Xvfb exists |

## Minecraft versions (Stonecutter)

`fabric/src` is built once per Minecraft version by [Stonecutter](https://stonecutter.kikugie.dev)
(ADR 0007). The versions are listed in `settings.gradle.kts`; each has a project `:fabric:<version>`
and a `fabric/versions/<version>/gradle.properties` (Minecraft, Fabric API and Java versions).

- The sources are written for the **active** version, 1.21.11 (`fabric/stonecutter.gradle.kts`).
  Other versions get generated copies under `fabric/versions/<version>/build/generated/stonecutter`.
- Plain renames between versions are regex rules at the end of `fabric/build.gradle.kts`. Each rule
  has a reverse, so switching the active version and back leaves the sources unchanged.
- Anything else is a version block, kept as small as possible:
  ```java
  //? if >=26.1 {
  /*return InputConstants.isKeyDown(key);
  *///?} else {
  return InputConstants.isKeyDown(window, key);
  //?}
  ```
  Small accessors that differ go in `fabric/compat/Compat.java`, so callers need no block.
- Key codes: use `InputConstants.KEY_*`, never GLFW. 26.x uses SDL scancodes.
- Mixins are not checked at compile time. Check their targets in each version's sources
  (`:fabric:<version>:genSourcesWithVineflower`) and run that version's gametests.

## Dev client tips

- Debug commands exist only in dev or with `-Dreizo.debug=true`:
  `/reizo-debug start|stop|pause|marker <label>|status`.
- Recordings: `fabric/versions/<version>/run/reizo/recordings/`. Config: `…/run/config/reizo.json`.
- Logs: logger names start with `Reizo` (`Reizo/Capture`, `Reizo/Config`).

## Environment inspection (2026-11-22)

| Item | Cloud build box | Owner's machine "Hyperion" |
| --- | --- | --- |
| OS / arch | Ubuntu 24.04, x86_64, 4 cores, 15 GB RAM | Arch Linux, Omarchy, Hyprland — **to verify** |
| Java | OpenJDK 21.0.13 | **to verify** (`java -version`) |
| Gradle | wrapper 9.5.1 | wrapper |
| Git / GitHub | git 2.43; GitHub via app integration | **to verify** (`gh auth status`) |
| FFmpeg | present; encoders libx264, libx265, libsvtav1, libaom-av1, libvpx-vp9 plus nvenc/vaapi/qsv wrappers (hardware not present) | **to verify** (`ffmpeg -hide_banner -encoders`) |
| GPU | none (Mesa llvmpipe under Xvfb) | **to verify** (`glxinfo -B`, `vainfo`, `nvidia-smi`) |
| Display | Xvfb | Wayland (Hyprland): Minecraft runs through XWayland or GLFW Wayland |

Hyperion follow-up commands are listed in docs/HANDOFF.md.

## Wayland / Hyprland notes

LWJGL's GLFW in Minecraft 1.21.11 runs via XWayland by default. If the dev client fails to open a
window, check that XWayland is enabled in Hyprland. Screen capture is not used by this mod (we
render frames ourselves), so Wayland portal restrictions do not apply.
