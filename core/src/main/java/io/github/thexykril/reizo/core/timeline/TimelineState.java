/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.timeline;

import io.github.thexykril.reizo.core.camera.CameraKeyframe;
import io.github.thexykril.reizo.core.camera.CameraPath;
import java.util.List;

/**
 * An immutable snapshot of everything the timeline edits, for undo and redo: the camera path and
 * the export region.
 *
 * @param inMs start of the export region, -1 for the start of the recording
 * @param outMs end of the export region, -1 for the end of the recording
 */
public record TimelineState(
    List<CameraKeyframe> keyframes,
    CameraPath.Interpolation interpolation,
    CameraPath.Easing easing,
    long inMs,
    long outMs) {
  public TimelineState {
    keyframes = List.copyOf(keyframes);
  }

  public static TimelineState of(CameraPath path, long inMs, long outMs) {
    return new TimelineState(path.keyframes(), path.interpolation(), path.easing(), inMs, outMs);
  }

  /** Writes the snapshot's camera path into {@code path}. */
  public void applyTo(CameraPath path) {
    path.clear();
    path.setInterpolation(interpolation);
    path.setEasing(easing);
    for (CameraKeyframe k : keyframes) {
      path.put(k);
    }
  }
}
