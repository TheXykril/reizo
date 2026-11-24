# Architecture Decision Records

One file per decision: `NNNN-short-title.md` with Status, Context, Decision, Consequences.
Superseded ADRs stay and link to their replacement. Any change listed under "Architecture rules"
in AGENTS.md needs an ADR.

| ADR | Title | Status |
| --- | --- | --- |
| [0001](0001-core-and-adapter-modules.md) | Minecraft-independent core plus thin Fabric adapter | Accepted |
| [0002](0002-initial-minecraft-version.md) | Minecraft 1.21.11, Fabric, Mojang mappings, Java 21 | Accepted |
| [0003](0003-license.md) | GPL-3.0-or-later | Accepted (owner) |
| [0004](0004-capture-at-packet-decoder.md) | Capture packets at the client decoder | Accepted |
| [0005](0005-clean-room-policy.md) | Clean-room policy for proprietary references | Accepted (owner) |
| [0006](0006-recording-storage-and-recovery.md) | Block stream, working directories, lock-based recovery | Accepted |
| [0007](0007-multi-version-strategy.md) | Multi-version strategy: modern Fabric family + 1.8.9 Ornithe | Accepted (owner) |
| [0008](0008-playback-loopback-replay-server.md) | Playback through a loopback replay server | Accepted |
| [0009](0009-frame-exact-export.md) | Frame-exact video export with a virtual clock and FFmpeg | Accepted |
| [0010](0010-optional-oneconfig-page.md) | Optional OneConfig page through a compile-only entrypoint | Accepted |
| [0011](0011-shadow-recording-buffer.md) | Shadow recording with snapshot segments in memory | Accepted |
| [0012](0012-effects-pass.md) | Effects as one full-screen pass after the world | Accepted |
| [0013](0013-export-sound-offline-mix.md) | Export sound mixed offline from the sounds that play | Accepted |
| [0014](0014-keyframed-settings.md) | Keyframed game and mod settings | Accepted |
| [0015](0015-editor-ui-kit.md) | A docked editor built on a small Reizo UI kit | Accepted |
| [0016](0016-clip-track.md) | A clip track that maps video time to replay time | Accepted |
| [0017](0017-compose-screens.md) | Reizo screens on OneConfig's Compose components | Accepted |
| [0018](0018-imported-music-track.md) | Imported music on its own track, mixed into the export | Accepted |
