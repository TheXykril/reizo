# 0006 — Block stream, working directories, lock-based recovery

Status: Accepted (2026-11-22)

## Context
Recordings must survive game crashes and power loss with bounded data loss, without disk I/O on
game threads, and finalized files should be a single shareable file.

## Decision
- While recording: `working/<uuid>/stream.bin` append-only, written in CRC32-checked blocks
  (flushed at least every second, fsynced every 5 s), `meta.json` replaced atomically, and an
  OS-level lock on `owner.lock` held by the live session.
- On stop: package into a ZIP archive `<name>_<date>.reizo` (`meta.json` + `stream.bin`), then
  delete the working directory.
- On startup: any working directory whose lock can be acquired is abandoned; keep the longest
  valid block prefix, mark RECOVERED, package. Never delete what cannot be recovered.
- A single writer thread owns all I/O; producers enqueue into a bounded queue (drop and count if
  full for 50 ms).

## Consequences
+ Max ~1 s loss on crash; recovery needs no PID or timestamp heuristics.
+ ZIP container is extensible (snapshots, audio, thumbnails later).
− Packaging copies the stream once (fast, off-thread). Seeking inside a ZIP entry needs an index
  (planned with snapshots).
