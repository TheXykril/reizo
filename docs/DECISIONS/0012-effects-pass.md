# 0012: Effects as one full-screen pass after the world

Status: Accepted (2026-11-22). First slice of issue #15.

## Context

Replays and exports need image effects (exposure, contrast, saturation, vignette, letterbox and
later bloom, depth of field, motion blur) whose strength changes over time. They must cost nothing
outside the editor and must look the same in the preview and in an export.

Options considered:

1. **Vanilla post chains** (`post_effect` JSON). Their uniforms are fixed buffers built when the
   chain loads, so per-frame keyframed values would need a chain rebuild every frame.
2. **Own render pipeline and pass.** A `RenderPipeline` with a uniform buffer that Reizo fills each
   frame, drawn as one full-screen triangle.
3. **A shader mod (Iris).** Out of scope and not always installed.

## Decision

Option 2.

- `core.effects.Grade` holds the values (neutral by default) and `EffectTrack` keyframes them;
  values blend linearly, the letterbox switches at the next keyframe. Projects store them as
  `effects` (additive JSON field).
- `fabric.playback.EffectRenderer` runs after the world and entity outlines, before the HUD,
  from `GameRendererMixin`. It copies the main color texture, then draws it back into the main
  target through `reizo:pipeline/grade` with a `ReizoGrade` uniform buffer (ring buffer, written
  per frame). The vertex shader is vanilla `core/screenquad`.
- It runs only while a replay plays and the grade at the current time is not neutral. Exports
  capture the main target after the frame, so they include the effects at their exact frame time.

## Consequences

- One copy and one pass per frame while effects are on. Effects needing several passes (bloom,
  blur, depth of field) will add passes and targets to the same renderer.
- Iris and Sodium are untested with this pass; Iris replaces the world pipeline, but the main
  target and this pass come after it, so it is expected to work. To check on real hardware.
- Effect edits are not in the timeline's undo history yet.
