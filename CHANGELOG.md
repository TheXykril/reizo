# Changelog

All notable changes are documented here. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning: [Semantic Versioning](https://semver.org/). Before 1.0.0, minor versions may break
compatibility.

## [Unreleased]

### Added

- Gradle multi-project build: `core` (Minecraft-independent engine) and `fabric` (Minecraft 1.21.11
  client mod), Spotless formatting, GitHub Actions CI.
- Recording stream format v1: CRC-checked blocks, tolerant scanning for crash recovery
  (`docs/FORMAT.md`).
- Recording sessions with a background writer thread, pause (clock freeze), markers, periodic
  metadata refresh and bounded memory use.
- Crash recovery: abandoned recordings are detected with OS file locks and salvaged on startup.
- Packet capture at the client packet decoder (configuration and play phases, byte-exact) plus
  per-tick local player state.
- Configuration cache so recordings started mid-session still contain registries and the login
  packet.
- F9 start/stop key, optional pause and marker keys, HUD recording indicator, toasts.
- Auto-record on join setting (`config/reizo.json`).
- Unit tests for format, sessions, storage and recovery; in-game client gametests that record a
  singleplayer world and decode every recorded packet with Minecraft's own codecs.
