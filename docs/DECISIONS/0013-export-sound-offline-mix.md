# 0013: Export sound mixed offline from the sounds that play

Status: Accepted (2026-11-23). First slice of issue #17.

## Context

Exports render frame by frame on a virtual clock (ADR 0009), much slower or faster than real
time, so the game's live audio output cannot be recorded. The sound track must still match the
picture to within a frame.

Options considered:

1. **Record OpenAL's output** (loopback device). Tied to real time; useless for a frame-exact
   export, and the gametest machine has no audio device.
2. **Re-synthesize from recorded sound packets.** Misses client-side sounds (footsteps, UI).
3. **Capture every sound the client starts during the export, then mix offline.** Covers server
   and client sounds; the time of each is the video time of the frame being prepared.

## Decision

Option 3.

- `SoundManagerMixin` hooks `SoundManager.play` (before the sound engine, so it works without an
  audio device). While an export runs, `SoundCapture` resolves the sound and stores a `SoundCue`:
  video time, the sound file, gain after the player's volume settings, pitch, position, range, and
  the camera's position and yaw.
- `core.audio.AudioMixer` mixes 48 kHz stereo: linear resampling for pitch, linear distance
  attenuation (like the game), equal-power pan from the camera's yaw. Clips are decoded with the
  game's own `JOrbisAudioStream`.
- The picture is encoded to `<name>.video.<ext>`, then FFmpeg copies it and adds the sound (AAC,
  or Opus for WebM) into the final file. The mux runs off the game thread.

## Consequences

- Sample-exact placement; a gametest checks the sound starts within one frame.
- Left out for now: music, looping and moving sounds (they need following over time), sounds
  that started before the export range, reverb, and sound settings per track.
- WebM exports need an FFmpeg with libopus.
