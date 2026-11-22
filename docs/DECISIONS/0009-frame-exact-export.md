# 0009: Frame-exact video export with a virtual clock and FFmpeg

Status: Accepted (2026-11-22). First slice of issue #14.

## Context

A video export has to look the same however fast the computer renders. The replay normally runs on
wall-clock time on two threads: the replay server sends packets when the clock reaches them, and
Minecraft ticks the world on its own timer. Capturing whatever is on screen at 60 fps real time
would drop or repeat frames on slow machines and drift from the recording.

Options considered:

1. **Screen recording at real time.** Simple, but not deterministic and limited by real-time
   frame rate.
2. **Separate offscreen renderer.** Would need its own world rendering. Far too large, and it
   duplicates Minecraft.
3. **Step the existing client frame by frame.** Control both clocks so that each output frame is
   one exact step of replay time, and read the frame back from the main render target.

## Decision

Option 3.

- `core.export.ExportPlan` gives frame times from the frame index (never accumulated), so long
  exports do not drift. `core.export.FfmpegCommand` builds the FFmpeg command and finds FFmpeg
  (configured path, else PATH). Both are Minecraft-free and unit tested.
- **Packets:** the replay server gets a manual mode. For each frame it sends every packet up to the
  frame time and then a `ClientboundPingPacket(frame + 1)`. The client answers pings on its main
  thread after the packets before them, so the matching pong means the world is as recorded at that
  time.
- **Ticks:** `MinecraftMixin` feeds `DeltaTracker.Timer.advanceTime` a virtual clock that advances
  by exactly one frame duration, and only once the frame's pong has arrived. Ticks and partial
  ticks (interpolation) are therefore deterministic. The decision is taken once per frame, before
  the ticks, so the captured frame is the one that was rendered.
- **Capture:** after each frame, `Screenshot.takeScreenshot` reads the main render target. The RGBA
  frames go to FFmpeg's stdin through a small bounded queue, so a slow encoder slows rendering
  instead of filling memory. FFmpeg scales to the output size when it differs from the window.
- During export the GUI is hidden, the camera path plays, and opening any screen (Esc) cancels. A
  resized window fails the export.
- FFmpeg is the user's own installation and is not bundled. This avoids shipping codec binaries and
  their licences, and allows hardware encoders (NVENC, VAAPI, QSV) where FFmpeg has them.

## Consequences

+ Frame count and timing are exact (gametest: 93 planned frames, 93 frames in the file).
+ No second renderer: whatever Minecraft and other mods draw is in the video.
− Frames are rendered at window size and scaled by FFmpeg. Rendering directly at the output size
  needs an offscreen render target of that size (later).
− The window must stay open and unresized during an export. Audio is not exported yet (M6).
− Needs FFmpeg installed. The export reports clearly when it is missing.
