# 0017: Reizo screens on OneConfig's Compose components

Status: Accepted (2026-11-24, by the owner).

## Context

The owner wants Reizo's UI to match OneClient exactly and follow its selected theme (2026-11-23),
and decided to build Reizo for OneClient only for now. The Reizo kit (ADR 0015) imitates
OneConfig's look by drawing it with Minecraft's `GuiGraphics`; screenshots kept falling short of
OneClient's real screens.

OneConfig 1.9.3 (LGPL-3.0), which OneClient ships, has a public Compose UI API:
`api.ui.v1.platform.OneConfigComposeScreen` (a Minecraft `Screen` whose content is a Kotlin
`@Composable`) and about 60 components in `api.ui.v1.components` (Select, Slider, Switch,
TextField, TextButton, LabeledField, themed surfaces), all styled by `OneConfigTheme`. It runs on
PolyCompose (Polyfrost's Compose bundle) and needs Fabric Language Kotlin. Both ship with
OneClient. The components are marked `@ExperimentalOneConfigUiApi`.

A trial (branch `spike/compose-export`) rebuilt the export settings screen on these components.
On 1.21.11 with OneConfig loaded it renders as OneConfig's own screens do, and all 24 in-game tests
pass. The owner called the result "perfect" (2026-11-24).

Options considered:

1. **Keep drawing OneConfig's look with the Reizo kit.** No new dependencies, but it never quite
   matches, and every theme or style change in OneConfig has to be copied by hand.
2. **Compose screens on OneConfig's components.** The screens are OneConfig's own controls, so they
   match OneClient and follow its theme. Costs: Kotlin in the build, hard dependencies on OneConfig,
   PolyCompose and Fabric Language Kotlin, and an experimental API that may change.
3. **Embed another immediate-mode UI (for example imgui-java).** Looks like neither OneClient nor
   Minecraft.

## Decision

Option 2, screen by screen.

- Reizo's full screens and pop-ups are rebuilt as `OneConfigComposeScreen`s in Kotlin
  (`fabric/src/main/kotlin`), using OneConfig's components and `OneConfigTheme` only, with no colours
  of Reizo's own.
- Java code opens them through small factories (`ComposeScreens`), so Compose classes load only when
  a screen opens. While a screen has not moved yet, or if creating the Compose screen fails, the kit
  screen opens instead. Screens are tested through view interfaces (for example
  `ExportSettingsView`), so in-game tests work with either version.
- OneConfig, PolyCompose and Fabric Language Kotlin become required dependencies once the first
  moved screen ships in a release. Until then, OneConfig stays optional.
- Things drawn over the world (the replay bar, the path display, the recording indicator) stay
  `GuiGraphics` drawing, styled after OneConfig's panels and sliders: Compose screens replace the
  game's screen and cannot draw a HUD.
- Order: export settings (done in the trial), the replay browser, settings, then the timeline
  editor, which is the largest.

## Consequences

- Reizo's UI follows OneClient's theme and style without copying it.
- The build compiles Kotlin with the Compose compiler plugin. Compose's jars are unpacked from
  PolyCompose for compiling only. The dev client and in-game tests copy the full OneConfig jar (from
  Modrinth, since the Maven jar does not nest OneConfig's modules), PolyCompose and Fabric Language
  Kotlin into the run's mods folder, because Loom strips nested jars from mod dependencies.
- In-game tests run with OneConfig loaded. Tests clear OneConfig's notifications before clicking
  near the window's bottom-right corner, because its notifications take clicks there.
- An OneConfig update can break screens, because the components are experimental. The OneConfig
  version is pinned and updated on purpose.
- Reizo no longer runs without OneClient's mods once the dependencies are required. This follows the
  owner's OneClient-only decision; a plain-Fabric build would need the kit screens back.
