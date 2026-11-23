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
- `fabric.ui.kit.FlatButton` (extends `Button`) and `DragField` (extends `AbstractWidget`) draw
  themselves. Their one per-version difference, the render hook's name, is a Stonecutter rename
  plus one version block for 1.21.10.
- Interaction follows the ImGui-style editors the owner pointed to (chosen 2026-11-23): numbers are
  drag fields (drag to change, Shift for fine steps, scroll to step, double-click to reset) with a
  fill bar and a keyframe diamond; Inspector content sits in collapsible sections and scrolls; a
  menu bar (File, Edit, View, Camera) and right-click context menus (`PopupMenu`) on scene rows,
  the ruler and camera keyframes; the Scene and Inspector panels have title bars, can be dragged
  to float anywhere over the world, and dock back to the left or right edge (double-click the
  title toggles). This is Reizo's own code on vanilla `GuiGraphics`, not imgui-java.
- Widgets draw opaque, non-overlapping pieces (colours mixed in code) instead of stacking
  translucent fills, because the GUI renderer does not keep the order of overlapping translucent
  fills inside one widget reliably.
- The editor (`TimelineScreen`, the class name tests and key bindings use) lays out a top bar
  (transport, time, view mode, undo, Export), a tool rail, a Scene outliner, an Inspector with tabs
  and a multi-track timeline (markers, camera, look, world, entities, settings), with the world
  visible in the middle. Side panels fold away on narrow windows (the 320×240 test window keeps
  only the top bar and the timeline) and with Tab.
- Reizo's other screens (replay browser, settings, export, effects, settings over time, rename,
  the delete question) extend `KitScreen`: the game dimmed behind a rounded card with a title bar
  (or a card docked on the left for panels that keep the replay visible), switch rows, value rows
  that cycle on click, `KitField` text fields (a vanilla `EditBox` drawn in the kit's look) and
  `KitConfirmScreen`. Vanilla `CycleButton`, sliders and selection lists are no longer used.
- Reizo's screens lower the GUI scale while open when the window is narrower than their layout
  needs (`GuiScaleFit`), and put the player's scale back when they close.

## Consequences

- One look for the whole editor, and new studio features (clips, cuts, transitions, titles, audio)
  get tracks and inspector tabs instead of new full screens.
- More drawing code is Reizo's to maintain; it only uses `fill`, `drawString` and scissors, which
  every supported version has.
- A third-party asset under its own licence (OFL) ships in the jar; it must stay unmodified or be
  renamed if modified (OFL reserved-name rule).
- Screens not yet moved to the kit still look vanilla until they are moved.
