# Development

## Requirements

- JDK 21 or newer (the build uses a Java 21 toolchain; Gradle downloads one if missing via the
  Foojay resolver).
- Git. Nothing else: the Gradle wrapper fetches Gradle 9.5.1, Loom fetches Minecraft.
- Network access to `maven.fabricmc.net`, `piston-meta.mojang.com`, `piston-data.mojang.com`,
  `libraries.minecraft.net`, `resources.download.minecraft.net`, Maven Central and the Gradle plugin
  portal.
- For in-game tests without a display: `xvfb-run` and Mesa (software OpenGL works).

## Commands

| Command | What it does |
| --- | --- |
| `./gradlew build` | Compile, Spotless check, unit tests, mod jar |
| `./gradlew :core:test` | Unit tests only (seconds) |
| `./gradlew spotlessApply` | Format code, add license headers |
| `./gradlew :fabric:runClient` | Dev client in `fabric/run/` with `-Dreizo.debug=true` |
| `xvfb-run -a ./gradlew :fabric:runClientGameTest` | In-game end-to-end tests |
| `./gradlew :fabric:genSourcesWithVineflower` | Decompiled Minecraft sources (Mojang names) for reading |
| `scripts/check.sh` | Everything CI runs, plus gametests if a display/Xvfb exists |

## Dev client tips

- Debug commands exist only in dev or with `-Dreizo.debug=true`:
  `/reizo-debug start|stop|pause|marker <label>|status`.
- Recordings: `fabric/run/reizo/recordings/`. Config: `fabric/run/config/reizo.json`.
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
