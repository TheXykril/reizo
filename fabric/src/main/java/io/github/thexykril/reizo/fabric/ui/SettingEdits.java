/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.core.effects.PropertyTracks;
import io.github.thexykril.reizo.fabric.playback.ReplayEditor;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.playback.ReplayServer;
import io.github.thexykril.reizo.fabric.settings.KeyframedSetting;
import io.github.thexykril.reizo.fabric.settings.KeyframedSettings;

/** Keyframe edits on keyframed settings at the playhead, shared by the editor's panels. */
final class SettingEdits {
  /** A keyframe this close to the playhead is edited instead of adding one. */
  static final long TOLERANCE_MS = 100;

  private static long lastEditMs;

  private SettingEdits() {}

  static long playhead() {
    ReplayServer server = ReplayPlayback.current();
    return server == null ? 0 : Math.min(server.replayTimeMs(), server.script().durationMs());
  }

  /** The value at the playhead: the track's, or the live value without one. */
  static double shownValue(KeyframedSetting s) {
    double v = ReplayEditor.settings().valueAt(s.id(), playhead());
    return Double.isNaN(v) ? s.get() : v;
  }

  /** Whether {@code s} has a keyframe at the playhead. */
  static boolean atKeyframe(KeyframedSetting s) {
    return ReplayEditor.settings().nearest(s.id(), playhead(), TOLERANCE_MS) >= 0;
  }

  /**
   * Sets a keyframe for {@code s} at the playhead (or edits the one there). With {@code coalesce},
   * edits less than 0.7 s apart (a slider drag) share one undo step.
   */
  static void edit(KeyframedSetting s, double value, boolean coalesce) {
    long now = System.currentTimeMillis();
    if (now - lastEditMs > 700) {
      ReplayEditor.recordUndo();
    }
    lastEditMs = coalesce ? now : 0;
    PropertyTracks tracks = ReplayEditor.settings();
    long near = tracks.nearest(s.id(), playhead(), TOLERANCE_MS);
    tracks.put(s.id(), s.kind(), near >= 0 ? near : playhead(), KeyframedSettings.clamp(s, value));
    ReplayEditor.settingsChanged();
  }

  /** Removes the keyframe of {@code s} at the playhead. Returns whether there was one. */
  static boolean removeHere(KeyframedSetting s) {
    long near = ReplayEditor.settings().nearest(s.id(), playhead(), TOLERANCE_MS);
    if (near < 0) {
      return false;
    }
    ReplayEditor.recordUndo();
    lastEditMs = 0;
    ReplayEditor.settings().remove(s.id(), near);
    ReplayEditor.settingsChanged();
    return true;
  }

  /** Ends the current undo group, so the next edit records a new step. */
  static void breakUndoGroup() {
    lastEditMs = 0;
  }
}
