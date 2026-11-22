# 0003 — GPL-3.0-or-later

Status: Accepted by the owner (2026-11-22)

## Context
The project is intended to become open source. Options considered: GPL-3.0-or-later,
LGPL-3.0-or-later, MIT, Apache-2.0. OneClient and Polyfrost mods are GPL; OneConfig is LGPL-3.0
with additional terms. Flashback and Hindsight are proprietary and unusable regardless of our
license.

## Decision
GPL-3.0-or-later for all code. Every source file carries `SPDX-License-Identifier:
GPL-3.0-or-later` (enforced by Spotless).

## Consequences
+ Forks and redistributions must stay open; compatible with the GPL Polyfrost ecosystem.
+ Linking to OneConfig (LGPL) is permitted; its Additional Terms must be reviewed before shipping
  the integration.
− Permissively licensed projects cannot absorb our code without becoming GPL.
