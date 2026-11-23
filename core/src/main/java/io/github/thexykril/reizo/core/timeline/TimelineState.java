/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.timeline;

import io.github.thexykril.reizo.core.camera.CameraKeyframe;
import io.github.thexykril.reizo.core.camera.CameraPath;
import io.github.thexykril.reizo.core.effects.EffectTrack;
import io.github.thexykril.reizo.core.effects.PropertyTracks;
import java.util.List;

/**
 * An immutable snapshot of everything the timeline edits, for undo and redo: the camera path, the
 * effect keyframes, the setting keyframes and the export region.
 *
 * @param inMs start of the export region, -1 for the start of the recording
 * @param outMs end of the export region, -1 for the end of the recording
 */
public record TimelineState(
    List<CameraKeyframe> keyframes,
    CameraPath.Interpolation interpolation,
    CameraPath.Easing easing,
    long inMs,
    long outMs,
    List<EffectTrack.Keyframe> effects,
    List<PropertyTracks.Keyframe> settings) {
  public TimelineState {
    keyframes = List.copyOf(keyframes);
    effects = List.copyOf(effects);
    settings = List.copyOf(settings);
  }

  public static TimelineState of(CameraPath path, long inMs, long outMs) {
    return of(path, new EffectTrack(), inMs, outMs);
  }

  public static TimelineState of(CameraPath path, EffectTrack effects, long inMs, long outMs) {
    return of(path, effects, new PropertyTracks(), inMs, outMs);
  }

  public static TimelineState of(
      CameraPath path, EffectTrack effects, PropertyTracks settings, long inMs, long outMs) {
    return new TimelineState(
        path.keyframes(),
        path.interpolation(),
        path.easing(),
        inMs,
        outMs,
        effects.keyframes(),
        settings.keyframes());
  }

  /** Writes the snapshot's setting keyframes into {@code tracks}. */
  public void applyTo(PropertyTracks tracks) {
    tracks.setAll(settings);
  }

  /** Writes the snapshot's effect keyframes into {@code track}. */
  public void applyTo(EffectTrack track) {
    for (EffectTrack.Keyframe k : track.keyframes()) {
      track.remove(k.timeMs());
    }
    for (EffectTrack.Keyframe k : effects) {
      track.put(k.timeMs(), k.grade());
    }
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
