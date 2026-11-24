# 0018: Imported music on its own track, mixed into the export

Status: Proposed (2026-11-24). Requested by the owner ("media menu for adding music from computer").

## Context

Editors like Rewind let you put your own music under a replay. Reizo's export already renders a
sound track offline (ADR 0013) and muxes it into the video with FFmpeg, so imported music can
join that mix. Open questions were where the files live, how they are decoded, and what the
editor shows.

## Decision

- A project holds a list of **music items** (`Project.music`): the file's absolute path, where it
  starts in the video (ms), where in the file it starts (trim, ms), its length in the video (ms),
  a volume (0..2) and fade-in and fade-out lengths (ms). The file stays where the user keeps it;
  the project refers to it. A missing file is skipped with a warning, the rest still exports.
- **Decoding uses FFmpeg**, which the export already needs: `ffmpeg -i <file> -f f32le -ac 2 -ar
  48000 -` gives 48 kHz stereo floats, so every format FFmpeg reads works (MP3, OGG, FLAC, WAV,
  M4A…). No decoder library is added.
- **Mixing** is pure code in `core.audio.MusicMix`: each item's samples are placed at its start,
  trimmed, scaled by volume and linear fades, added, and limited to [-1, 1]. It runs as a
  **post-pass on the finished video** (`MusicPass`): FFmpeg decodes the video's own sound (or
  silence when it has none), the music is added, and the sound track is replaced while the picture
  is copied. Doing it after the export (and after joining clips) keeps music in video time across
  clip cuts and leaves the frame-exact export path (ADR 0013, 0016) untouched. The export's own
  "music volume" setting keeps applying only to the game's music.
- The editor gets a **Media tab** with "Import music" (the system file dialog through LWJGL's
  tinyfd where Minecraft ships it; 26.3 no longer does, so there it asks zenity, kdialog,
  AppleScript or PowerShell), a **music folder** next to the recordings whose files are added with
  one click (the fallback when no dialog runs), the list of imported items, and a **Music lane** on the
  timeline that shows each item as a block with its waveform. Items move by dragging; volume and
  fades are edited in the panel.
- The music is **not played while previewing** in this first step; it is heard in the export.
  Live preview needs streaming through the game's sound engine and comes later.

## Consequences

- Exports with music need FFmpeg (they already do for any video).
- Projects refer to files outside the game folder; moving the file breaks the link (shown in the
  Media tab).
- Clips from other replays (a separate request) are not covered here and need their own ADR.
