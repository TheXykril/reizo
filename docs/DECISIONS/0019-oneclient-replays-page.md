# 0019: Show Reizo recordings in OneClient's Replays page and Record card

Status: Proposed (2026-11-24). Requested by the owner ("look how replays are integrated into
oneclient ui for something like flashback, our needs the same thing").

## Context

OneClient ships PolyPlus (Polyfrost, GPL-3.0). PolyPlus draws a "Record" card on the pause screen
and a Replays tab and page in the lobby (search, details, Watch, Show in folder, thumbnails). Both
read a fixed list of replay providers, `org.polyfrost.polyplus.compat.replay.ReplayProviders.all`
(Flashback and Replay Mod). There is no way for another mod to register itself.

This is not an official integration: PolyPlus does not know about Reizo, and nothing here is
endorsed by Polyfrost or OneClient.

## Decision

- A **`@Pseudo` mixin** on `ReplayProviders.getAll` (`require = 0`) adds one Reizo provider to the
  returned list. Without PolyPlus, or when the method changes, the mixin does nothing.
- The provider is a **`java.lang.reflect.Proxy`** of PolyPlus's `ReplayProvider` interface
  (`compat.PolyPlusReplays`), so Reizo does not compile against PolyPlus. Methods it does not
  know (from a newer PolyPlus) return a neutral value (false, 0, an empty list or null). It lists Reizo's recordings (name,
  server, length, size, playable on this version or a warning), plays them, opens the folder and
  Reizo's own browser, and drives recording (start, pause, finish) for the Record card. It hides
  Reizo's own pause-menu buttons there through `controlKeyPrefixes`, as PolyPlus does for
  Flashback.
- **Recordings get a thumbnail**: a 480-pixel-wide PNG of the world (no HUD, no menu) taken 1.5 s
  after a recording starts, stored in the working folder and packed as `thumbnail.png` in the
  `.reizo` archive. Readers that do not know the entry ignore it (the format already allows extra
  entries). PolyPlus shows it; Reizo's own browser can use it later.
- Long term, ask Polyfrost for a public way to register a provider and switch to it.

## Consequences

- Reizo depends on the shape of a class in another mod. A PolyPlus update can rename or change it;
  then the mixin silently stops applying and Reizo's recordings disappear from that page (nothing
  crashes). The gametests cannot load PolyPlus, so this is checked by hand in OneClient.
- Recordings made before this change have no thumbnail and show without a picture.
