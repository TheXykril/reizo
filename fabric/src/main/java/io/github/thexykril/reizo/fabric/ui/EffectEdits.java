/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.core.effects.EffectTrack;
import io.github.thexykril.reizo.core.effects.Grade;
import io.github.thexykril.reizo.fabric.playback.ReplayEditor;
import java.util.function.UnaryOperator;

/** Look (effect) keyframe edits at the playhead, shared by the Effects panel and the Inspector. */
final class EffectEdits {
  /** Keyframes this close to the playhead are edited instead of adding another. */
  static final long TOLERANCE_MS = 100;

  private static long lastEditMs;

  private EffectEdits() {}

  /** The keyframe time edited now: one near the playhead, or the playhead. */
  static long editTime() {
    long near = ReplayEditor.effects().nearest(SettingEdits.playhead(), TOLERANCE_MS);
    return near >= 0 ? near : SettingEdits.playhead();
  }

  static boolean atKeyframe() {
    return ReplayEditor.effects().nearest(SettingEdits.playhead(), TOLERANCE_MS) >= 0;
  }

  /**
   * Sets the keyframe at the edit time to {@code change} applied to the grade there. Changes less
   * than 0.7 s apart (a slider drag) share one undo step.
   */
  static void edit(UnaryOperator<Grade> change) {
    long now = System.currentTimeMillis();
    if (now - lastEditMs > 700) {
      ReplayEditor.recordUndo();
    }
    lastEditMs = now;
    EffectTrack track = ReplayEditor.effects();
    long t = editTime();
    track.put(t, change.apply(track.at(t)));
    ReplayEditor.effectsChanged();
  }

  /** Steps the letterbox to the next ratio (its own undo step). */
  static void cycleLetterbox() {
    float now = ReplayEditor.gradeNow().letterbox();
    float[] all = Grade.LETTERBOXES;
    int i = 0;
    while (i < all.length && Math.abs(all[i] - now) > 0.01f) {
      i++;
    }
    float next = all[(i + 1) % all.length];
    lastEditMs = 0;
    edit(g -> g.withLetterbox(next));
    lastEditMs = 0;
  }

  /** Removes the look keyframe at the playhead. Returns whether there was one. */
  static boolean removeHere() {
    long near = ReplayEditor.effects().nearest(SettingEdits.playhead(), TOLERANCE_MS);
    if (near < 0) {
      return false;
    }
    ReplayEditor.recordUndo();
    lastEditMs = 0;
    ReplayEditor.effects().remove(near);
    ReplayEditor.effectsChanged();
    return true;
  }
}
