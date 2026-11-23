# 0014: Keyframed game and mod settings

Status: Accepted (2026-11-23).

## Context

Lunar's Rewind lets creators keyframe Lunar mod properties and use mods as effects. The OneClient
equivalent is OneConfig: OneClient's mods keep their options in OneConfig config trees. Reizo must
keep working without OneConfig (ADR 0010), and a replay must never leave the player's own settings
changed.

## Decision

- `core.effects.PropertyTracks`: keyframes per setting id (a string), numbers blended linearly,
  toggles switched at each keyframe. Stored in the project as `settings` (additive JSON field) and
  covered by the timeline's undo (`TimelineState.settings`).
- `fabric.settings.KeyframedSetting` is the game-side view of one setting. Sources:
  Minecraft's own video settings (brightness, distortion effects, entity shadows, view bobbing,
  through `OptionInstance.set`, which does not write options.txt), and, when OneConfig is
  installed, `compat.OneConfigSettings`: every switch and every number with min/max of every
  registered config tree except Reizo's own, set with `Property.setAs` so mods see the change.
  Ids: `minecraft:<name>` and `oneconfig:<config file>/<property path>`.
- `KeyframedSettings` drives the tracks once per frame (`GameRenderer.render` HEAD) on the render
  thread. The first time a track touches a setting it remembers the live value; it puts it back
  when the track goes away and on the first frame after the replay closes.
- A track for a setting that is not installed now stays in the project and is skipped.

## Consequences

- One panel (timeline → Settings) covers vanilla and every OneConfig mod without per-mod code.
- OneConfig may save a config file while a value is driven (if a mod saves on change); the value
  is put back afterwards, so the next save writes the player's value again. If the game quits
  during a replay before a frame runs, a driven value can remain. Not observed; needs a check in
  OneClient.
- The OneConfig source builds against OneConfig 1.9.1 but has not run here (the cloud proxy blocks
  OneConfig's runtime dependencies). Failures turn the OneConfig source off and keep Minecraft's.
- Settings with lists, colors, text, keybinds or unbounded numbers are not offered.
