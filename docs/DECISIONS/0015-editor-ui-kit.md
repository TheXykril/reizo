# 0015: A docked editor built on a small Reizo UI kit

Status: Proposed (2026-11-23), waiting for the owner's review.

## Context

The owner found the replay editor "basic and nothing like OneClient" and wants it to feel like
Axiom and Flashback combined: docked panels around the world view, a real multi-track timeline,
toolbars and in-world gizmos, with OneClient's look (dark `#11171C` ground, blue `#1878F1` accent,
rounded panels). The owner approved the mockup on 2026-11-23. Axiom and Flashback are not openly
licensed, so only their ideas may be followed (ADR 0005).

The editor so far is a row of vanilla buttons over a two-track timeline, with effects, settings
and export each in a separate full screen. Vanilla widgets draw Minecraft's stone-button sprites,
and their render hooks have different names on each version Reizo builds for
(`renderWidget`/`renderContents` on 1.21.x, `extract*` on 26.x).

Options considered:

1. Keep vanilla widgets and restyle with sprites (a resource pack): cheap, but sprites cannot give
   the flat, rounded, two-tone look, and every screen still lays itself out by hand.
2. Use OneConfig's UI (Polyui) for the editor: matches OneClient exactly, but OneConfig is optional
   (ADR 0010), so the editor would need two implementations.
3. A small kit of Reizo's own: theme colours, a few flat widgets that extend vanilla ones (so
   focus, narration, keyboard and the tests' button lookups keep working) and drawing helpers, with
   one editor screen that docks panels around the world.

## Decision

Option 3.

- `fabric.ui.kit.Theme` holds the palette: OneClient-like dark surfaces, blue accent, one colour per
  track kind. No art or code from OneClient, Axiom or Flashback.
- `fabric.ui.kit.Draw` draws rounded panels, hairlines, diamonds and fitted text with
  `GuiGraphics.fill`, `drawString` and the pose stack only, so it needs no shaders, textures or
  per-version GPU code. Shapes are drawn at the window's real pixel size (the pose is scaled by
  1/GUI scale) with anti-aliased corners, so they do not look blocky.
- Text uses a bundled font, Inter Medium (SIL OFL 1.1, owner asked to drop the Minecraft look on
  2026-11-23), through a `reizo:ui` TTF font with Minecraft's font as the fallback for symbols.
  The licence ships in the jar (`licenses/Inter-OFL.txt`) and the README lists it.
- `fabric.ui.kit.FlatButton` (extends `Button`) and `FlatSlider` (extends `AbstractSliderButton`)
  draw themselves. Their one per-version difference, the render hook's name, is a Stonecutter
  rename plus one version block for 1.21.10.
- The editor (`TimelineScreen`, the class name tests and key bindings use) lays out a top bar
  (transport, time, view mode, undo, Export), a tool rail, a Scene outliner, an Inspector with tabs
  and a multi-track timeline (markers, camera, look, world, entities, settings), with the world
  visible in the middle. Side panels fold away on narrow windows (the 320×240 test window keeps
  only the top bar and the timeline) and with Tab.
- Existing panels (effects, settings, export) stay as they are for now and open from the editor.
  They move into the Inspector one by one in later changes, on the same kit.

## Consequences

- One look for the whole editor, and new studio features (clips, cuts, transitions, titles, audio)
  get tracks and inspector tabs instead of new full screens.
- More drawing code is Reizo's to maintain; it only uses `fill`, `drawString` and scissors, which
  every supported version has.
- A third-party asset under its own licence (OFL) ships in the jar; it must stay unmodified or be
  renamed if modified (OFL reserved-name rule).
- Screens not yet moved to the kit still look vanilla until they are moved.
