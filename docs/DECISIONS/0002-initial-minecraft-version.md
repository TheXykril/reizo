# 0002 — Minecraft 1.21.11, Fabric, Mojang mappings, Java 21

Status: Accepted (2026-11-22)

## Context
OneClient ships for 1.21.1, 1.21.10, 1.21.11, 26.1.2, 26.2 and 26.3 (Fabric only). 1.21.11 is the
last obfuscated release and the most mature 1.21 target: every mod in the OneClient 1.21.11 pack,
including OneConfig 1.9.1, is available. 26.x needs Java 25 and is newer, with a shifting mod
ecosystem. The owner asked for 1.21.11 to be considered.

## Decision
Start with **1.21.11** on Fabric Loader 0.20.1 and Fabric API 0.141.6, Loom 1.17.21 (newest Loom
that runs on Java 21), Gradle 9.5.1, Java 21. Use **Mojang's official mappings**, because 26.x is
unobfuscated and uses the same names, making the later port mostly about API changes.

## Consequences
+ Stable target, matches OneClient's 1.21.11 pack exactly.
+ Port to 26.x is cheaper than with Yarn.
− Mojang mapping names cannot be redistributed as a mappings file (Loom handles this locally).
− Second version needs a multi-version setup (Stonecutter likely), planned for M8.
