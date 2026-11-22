# 0010: Optional OneConfig page through a compile-only entrypoint

Status: Accepted (2026-11-22). First slice of issue #18.

## Context

Lunar's Rewind is controlled from inside Lunar's own menus. Reizo should feel the same inside
OneClient, whose settings and keybinds live in OneConfig, while staying a normal Fabric mod that
works without OneConfig (AGENTS.md: OneClient integration must stay optional). Reizo is not
endorsed by Polyfrost or OneClient.

Options considered:

1. **Hard dependency on OneConfig.** Simplest code, but breaks plain Fabric installs.
2. **Separate add-on mod.** Clean split, but users must install two jars.
3. **Compile-only API plus a OneConfig entrypoint in the same jar.** Fabric only loads an
   entrypoint class when the mod that owns the key (`oneconfig:config`) asks for it.

## Decision

Option 3.

- `fabric/compat/OneConfigIntegration` implements OneConfig's `ConfigEntrypoint` and is declared
  under `"oneconfig:config"` in `fabric.mod.json`. OneConfig is listed under `suggests`, never
  `depends`. No other Reizo class references OneConfig types.
- Only `org.polyfrost.oneconfig:config` and `config-impl` (Minecraft-independent API, OneConfig
  1.9.1) are `compileOnly`, non-transitive. Nothing from OneConfig is bundled.
- The page edits the same values as Reizo's own settings screen. `config/reizo.json` stays the
  single source of truth: the page reads it when OneConfig builds the page and writes each change
  back to it. OneConfig's own file (`reizo-oneconfig.json`) is never read over Reizo's values.
- Buttons on the page open the replay browser and start/stop recording. Key bindings stay vanilla
  key mappings, which OneConfig's keybind page lists itself. `originalScreen` opens Reizo's
  settings screen.

## Consequences

- Plain Fabric installs are unaffected; the in-game tests run without OneConfig.
- OneConfig's runtime dependencies are not reachable from the cloud CI environment, so the page is
  not covered by automated tests. It needs a manual check in OneClient on a real machine.
- A OneConfig API change can break the page without breaking the build of other code paths; the
  compile-only version is pinned in `gradle/libs.versions.toml`.
- Further OneClient work (main menu entry, notifications, styling) builds on this entrypoint.
