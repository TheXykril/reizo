/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.effects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Keyframed grade over replay time. Between keyframes values blend linearly; before the first and
 * after the last keyframe they hold. No keyframes means neutral.
 */
public final class EffectTrack {
  /** One keyframe: the grade at a replay time. */
  public record Keyframe(long timeMs, Grade grade) {}

  private final TreeMap<Long, Grade> keyframes = new TreeMap<>();

  public void put(long timeMs, Grade grade) {
    keyframes.put(timeMs, grade);
  }

  public boolean remove(long timeMs) {
    return keyframes.remove(timeMs) != null;
  }

  public boolean isEmpty() {
    return keyframes.isEmpty();
  }

  public int size() {
    return keyframes.size();
  }

  public List<Keyframe> keyframes() {
    List<Keyframe> out = new ArrayList<>();
    keyframes.forEach((t, g) -> out.add(new Keyframe(t, g)));
    return Collections.unmodifiableList(out);
  }

  /** The keyframe time within {@code toleranceMs} of {@code timeMs}, or -1. */
  public long nearest(long timeMs, long toleranceMs) {
    Long below = keyframes.floorKey(timeMs);
    Long above = keyframes.ceilingKey(timeMs);
    long best = -1;
    long bestDist = Long.MAX_VALUE;
    for (Long k : new Long[] {below, above}) {
      if (k != null && Math.abs(k - timeMs) <= toleranceMs && Math.abs(k - timeMs) < bestDist) {
        best = k;
        bestDist = Math.abs(k - timeMs);
      }
    }
    return best;
  }

  /** The grade at a replay time. */
  public Grade at(double timeMs) {
    if (keyframes.isEmpty()) {
      return Grade.NEUTRAL;
    }
    Map.Entry<Long, Grade> before = keyframes.floorEntry((long) Math.floor(timeMs));
    Map.Entry<Long, Grade> after = keyframes.ceilingEntry((long) Math.ceil(timeMs));
    if (before == null) {
      return after.getValue();
    }
    if (after == null || after.getKey().equals(before.getKey())) {
      return before.getValue();
    }
    double f = (timeMs - before.getKey()) / (double) (after.getKey() - before.getKey());
    return before.getValue().lerp(after.getValue(), f);
  }
}
