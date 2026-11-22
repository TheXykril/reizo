# 0001 — Minecraft-independent core plus thin Fabric adapter

Status: Accepted (2026-11-22)

## Context
The project must survive Minecraft updates, support more versions later, and be testable without
launching the game. Most logic (file format, recording lifecycle, timeline, camera math, export
orchestration) does not need game classes.

## Decision
Two Gradle projects: `core` (pure Java 21, Gson only, no Minecraft/Fabric/Netty imports) and
`fabric` (one Minecraft version, Loom). `fabric` jar-in-jars `core`. New logic goes to `core`
whenever possible. Further Gradle modules are added only when a real boundary appears.

## Consequences
+ Fast unit tests for the hard parts; a new Minecraft version means a new adapter, not a rewrite.
+ Clear rule for agents about where code goes.
− Some translation code between game objects and core types (e.g. `LocalPlayerSampler`).
