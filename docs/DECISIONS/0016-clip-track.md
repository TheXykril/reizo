# 0016: A clip track that maps video time to replay time

Status: Proposed (2026-11-23), waiting for the owner's review.

## Context

The owner compared Reizo with Lunar Rewind and picked clips first (2026-11-23): split, trim and
arrange parts of a replay on a video track, as in Rewind's editor. So far a project has one range
(In/Out) of one recording, and everything (camera, effects, settings keyframes) is stored in replay
time.

Options considered:

1. **Keep the timeline in replay time and mark cut parts.** Easy, but clips can't be reordered.
2. **Clips that copy their keyframes.** Each clip owns its own keyframes in clip time. Moving a
   clip moves its edits, but a camera move that spans a cut is split in two, and every keyframe
   format changes.
3. **An edit list over replay time.** Clips are ranges of the replay, in the order they play.
   Keyframes stay in replay time, tied to moments in the world, and the editor draws them where
   that moment sits in the edited video.

## Decision

Option 3.

- `core.timeline.Sequence` holds the clips: each is a replay range `[inMs, outMs)`, in play order.
  Clips never overlap in replay time, so each moment of the replay is shown at most once and every
  keyframe has one place in the video. It maps both ways: `toReplay(videoMs)` and
  `toVideo(replayMs)` (empty for a moment that was cut out). Edits: split at a video time, trim a
  clip's start or end, delete a clip (the rest closes up), and move a clip to another position.
- A project with no clips plays the In/Out range as before (old projects open unchanged). The first
  edit creates one clip from that range. Saved in the project as `clips` (an additive field, schema
  stays 1). Clip edits are undoable with the rest of the timeline.
- The editor's timeline shows video time when clips exist: a Video lane with the clips, the cut
  parts left out, and keyframes drawn at their video position. Seeking and adding keyframes map
  through the sequence.
- Playback in the editor follows the clips: reaching a clip's end seeks to the next clip's start
  (a backwards jump rebuilds the replay, as any backwards seek does, ADR 0008).
- Export renders each clip as its own frame-exact export (ADR 0009) with its sound (ADR 0013), then
  joins the parts with FFmpeg's concat demuxer without re-encoding.

## Consequences

- Cuts, trims and reordering keep camera paths, effects and settings tied to what happens in the
  world. A camera move that spans a cut simply continues in the next clip at that moment.
- A clip can't show the same moment twice (no duplicates or overlaps); copies would need clip-owned
  keyframes later.
- Reordered clips make the editor and the export rebuild the replay at backwards jumps, which takes
  a moment.
- Transitions, speed per clip and more video tracks are later steps on the same model.
