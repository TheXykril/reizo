# Project

## Vision

A polished Minecraft recording and editing experience comparable to Lunar Client's Rewind, built
as an independent, open-source Fabric mod that works well inside OneClient. Users record gameplay,
open the recording later, fly a free camera through it, edit it on a native timeline with camera
paths, effects, overlays and audio, and export a finished video, without leaving Minecraft.

It is primarily a **recording and editing system**, not an instant-replay feature. A rolling
"shadow" buffer (Lunar calls it Shadow Rewind) is in scope later as a convenience.

## Principles

1. **Replay reconstruction over video capture.** We record the network state the client
   received, so the editor can place a camera anywhere, not just where the player looked.
2. **Reliability first.** Recordings survive crashes; the recorder never crashes or disconnects the
   game; nothing is silently lost (dropped frames are counted).
3. **Minimal gameplay impact.** No disk I/O on game threads; nothing GPU-heavy runs outside the
   editor or export.
4. **Native feel.** The editor is a Minecraft screen with a consistent design language and clear
   hierarchy, not a pile of pill buttons.
5. **Honest status.** Features are only "implemented" with evidence (see FEATURES.md).

## Scope

Recording, playback, cinematic camera, timeline editor, visual effects, audio, rendering/export,
OneClient integration. Full breakdown and status: [FEATURES.md](FEATURES.md). Order of work:
[ROADMAP.md](ROADMAP.md).

## Targets

- Minecraft Java Edition **1.21.11**, Fabric, first ([ADR 0002](DECISIONS/0002-initial-minecraft-version.md)).
- Later: 26.x (OneClient supports 1.21.1, 1.21.10, 1.21.11, 26.1.2, 26.2 and 26.3 as of 2026-11-22).
- Linux (primary dev machine: Arch Linux, Hyprland), Windows and macOS.

## Independence

- Not affiliated with or endorsed by Lunar Client, Polyfrost/OneClient, or Mojang. Never claim
  official support.
- Lunar Rewind is a **functional** reference only: features and workflows from public pages.
- Flashback and Hindsight are proprietary ("All Rights Reserved"): we study their public behavior
  and design ideas and write every line ourselves. See [ADR 0005](DECISIONS/0005-clean-room-policy.md).
- By owner decision, nothing from Replay Mod is used.

## Owner decisions log

| Date | Decision |
| --- | --- |
| 2026-11-22 | License GPL-3.0-or-later |
| 2026-11-22 | Design modeled on Flashback's approach (clean room); nothing from Replay Mod |
| 2026-11-22 | Name "rewind" rejected; project named **Reizo** (owner choice) |
| 2026-11-22 | Repository private until explicitly approved |
