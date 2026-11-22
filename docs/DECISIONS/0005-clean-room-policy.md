# 0005 — Clean-room policy for proprietary references

Status: Accepted by the owner (2026-11-22)

## Context
The owner wants the design to follow Flashback, which is "All rights reserved. Do not
redistribute." Hindsight and Lunar Client are also proprietary. The owner also asked that nothing
from Replay Mod be used.

## Decision
- Allowed: public documentation, feature lists, observed behavior, high-level design ideas (e.g.
  "snapshot the world at recording start"), and file names of public features.
- Not allowed: copying, translating or line-by-line porting source code, copying assets, shaders,
  localization text (Flashback's lang files are CC BY 4.0 but we write our own), or file formats
  derived from their code.
- Replay Mod: not used at all (owner preference).
- When an agent reads a proprietary repository to understand an approach, it records in the PR
  what was learned at the design level, and writes the implementation without the source open.

## Consequences
+ No licensing risk to the GPL release.
− Some problems take longer to solve than copying would.
