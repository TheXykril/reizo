/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.timeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The clips of an edited video, in play order (ADR 0016). Each clip is a replay range {@code [inMs,
 * outMs)}; clips never overlap in replay time, so every moment of the replay appears at most once
 * in the video. Video time runs through the clips one after another. Immutable: edits return a new
 * sequence.
 */
public final class Sequence {
  /** Shortest clip an edit may leave, in ms. */
  public static final long MIN_CLIP_MS = 100;

  /** One clip: a range of the replay. */
  public record Clip(long inMs, long outMs) {
    public Clip {
      if (inMs < 0 || outMs <= inMs) {
        throw new IllegalArgumentException("bad clip " + inMs + ".." + outMs);
      }
    }

    public long lengthMs() {
      return outMs - inMs;
    }

    boolean overlaps(Clip o) {
      return inMs < o.outMs && o.inMs < outMs;
    }
  }

  private static final Sequence EMPTY = new Sequence(List.of());

  private final List<Clip> clips;

  private Sequence(List<Clip> clips) {
    this.clips = List.copyOf(clips);
  }

  public static Sequence empty() {
    return EMPTY;
  }

  /** One clip over a replay range. */
  public static Sequence of(long inMs, long outMs) {
    return new Sequence(List.of(new Clip(inMs, outMs)));
  }

  /**
   * A sequence from stored clips. Invalid clips and clips overlapping an earlier one are dropped,
   * so a damaged project still opens.
   */
  public static Sequence of(List<Clip> clips) {
    List<Clip> kept = new ArrayList<>();
    for (Clip c : clips) {
      if (c != null && kept.stream().noneMatch(c::overlaps)) {
        kept.add(c);
      }
    }
    return new Sequence(kept);
  }

  public boolean isEmpty() {
    return clips.isEmpty();
  }

  public List<Clip> clips() {
    return clips;
  }

  public int size() {
    return clips.size();
  }

  public Clip clip(int index) {
    return clips.get(index);
  }

  /** Length of the edited video. */
  public long durationMs() {
    long d = 0;
    for (Clip c : clips) {
      d += c.lengthMs();
    }
    return d;
  }

  /** Video time where clip {@code index} starts. */
  public long startOf(int index) {
    long t = 0;
    for (int i = 0; i < index; i++) {
      t += clips.get(i).lengthMs();
    }
    return t;
  }

  /** The clip playing at a video time (the last clip at or past the end), or -1 when empty. */
  public int indexAt(long videoMs) {
    long t = 0;
    for (int i = 0; i < clips.size(); i++) {
      long next = t + clips.get(i).lengthMs();
      if (videoMs < next) {
        return i;
      }
      t = next;
    }
    return clips.size() - 1;
  }

  /** The replay time shown at a video time (clamped to the video). */
  public long toReplay(long videoMs) {
    if (clips.isEmpty()) {
      return Math.max(0, videoMs);
    }
    int i = indexAt(Math.max(0, videoMs));
    Clip c = clips.get(i);
    long into = Math.max(0, videoMs) - startOf(i);
    return c.inMs + Math.min(into, c.lengthMs());
  }

  /** The clip that shows a replay time, or -1 when that moment was cut out. */
  public int clipOfReplay(long replayMs) {
    for (int i = 0; i < clips.size(); i++) {
      Clip c = clips.get(i);
      if (replayMs >= c.inMs && replayMs < c.outMs) {
        return i;
      }
    }
    return -1;
  }

  /**
   * The video time that shows a replay time, or -1 when that moment was cut out. The end of a clip
   * maps to the end of that clip in the video.
   */
  public long toVideo(long replayMs) {
    if (clips.isEmpty()) {
      return replayMs;
    }
    int i = clipOfReplay(replayMs);
    if (i < 0) {
      for (int j = 0; j < clips.size(); j++) {
        if (clips.get(j).outMs == replayMs) {
          return startOf(j) + clips.get(j).lengthMs();
        }
      }
      return -1;
    }
    return startOf(i) + replayMs - clips.get(i).inMs;
  }

  /**
   * Splits the clip at a video time in two. Unchanged when the cut is closer than {@link
   * #MIN_CLIP_MS} to the clip's ends.
   */
  public Sequence split(long videoMs) {
    if (clips.isEmpty()) {
      return this;
    }
    int i = indexAt(videoMs);
    Clip c = clips.get(i);
    long at = c.inMs + (videoMs - startOf(i));
    if (at - c.inMs < MIN_CLIP_MS || c.outMs - at < MIN_CLIP_MS) {
      return this;
    }
    List<Clip> next = new ArrayList<>(clips);
    next.set(i, new Clip(c.inMs, at));
    next.add(i + 1, new Clip(at, c.outMs));
    return new Sequence(next);
  }

  /** Removes a clip; the clips after it move up. */
  public Sequence delete(int index) {
    if (index < 0 || index >= clips.size() || clips.size() == 1) {
      return this;
    }
    List<Clip> next = new ArrayList<>(clips);
    next.remove(index);
    return new Sequence(next);
  }

  /** Moves a clip to another position in the play order. */
  public Sequence move(int from, int to) {
    if (from < 0 || from >= clips.size()) {
      return this;
    }
    to = Math.max(0, Math.min(clips.size() - 1, to));
    if (from == to) {
      return this;
    }
    List<Clip> next = new ArrayList<>(clips);
    Clip c = next.remove(from);
    next.add(to, c);
    return new Sequence(next);
  }

  /**
   * Sets a clip's replay start, kept inside {@code [0, outMs - MIN_CLIP_MS]} and clear of the other
   * clips' ranges.
   */
  public Sequence trimStart(int index, long inMs) {
    Clip c = clips.get(index);
    long low = 0;
    for (Clip o : clips) {
      if (o != c && o.outMs <= c.inMs) {
        low = Math.max(low, o.outMs);
      }
    }
    long in = Math.max(low, Math.min(inMs, c.outMs - MIN_CLIP_MS));
    return replace(index, new Clip(in, c.outMs));
  }

  /**
   * Sets a clip's replay end, kept inside {@code [inMs + MIN_CLIP_MS, replayDurationMs]} and clear
   * of the other clips' ranges.
   */
  public Sequence trimEnd(int index, long outMs, long replayDurationMs) {
    Clip c = clips.get(index);
    long high = replayDurationMs;
    for (Clip o : clips) {
      if (o != c && o.inMs >= c.outMs) {
        high = Math.min(high, o.inMs);
      }
    }
    long out = Math.min(high, Math.max(outMs, c.inMs + MIN_CLIP_MS));
    return replace(index, new Clip(c.inMs, out));
  }

  private Sequence replace(int index, Clip clip) {
    List<Clip> next = new ArrayList<>(clips);
    next.set(index, clip);
    return new Sequence(next);
  }

  /** Clips in replay order (for drawing what was cut out). */
  public List<Clip> byReplayTime() {
    List<Clip> sorted = new ArrayList<>(clips);
    sorted.sort(Comparator.comparingLong(Clip::inMs));
    return sorted;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Sequence s && s.clips.equals(clips);
  }

  @Override
  public int hashCode() {
    return clips.hashCode();
  }

  @Override
  public String toString() {
    return "Sequence" + clips;
  }
}
