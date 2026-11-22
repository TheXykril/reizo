/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thexykril.reizo.core.camera.CameraKeyframe;
import io.github.thexykril.reizo.core.camera.CameraPath;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TimelineTest {
  private static CameraKeyframe k(long t, double x) {
    return new CameraKeyframe(t, x, 64, 0, 0, 0, 0, 70);
  }

  private static CameraPath path(long... times) {
    CameraPath p = new CameraPath();
    for (long t : times) {
      p.put(k(t, t / 1000.0));
    }
    return p;
  }

  private static List<Long> times(CameraPath p) {
    return p.keyframes().stream().map(CameraKeyframe::timeMs).toList();
  }

  @Test
  void viewConvertsBetweenTimeAndX() {
    TimelineView v = new TimelineView(10_000);
    v.setWidth(1000);
    assertEquals(500, v.timeToX(5_000), 1e-9);
    assertEquals(2_500, v.xToTime(250));
    assertEquals(0, v.xToTime(-50));
    assertEquals(10_000, v.xToTime(5000));
  }

  @Test
  void zoomKeepsTheAnchorTimeInPlace() {
    TimelineView v = new TimelineView(10_000);
    v.setWidth(1000);
    v.zoom(4, 750);
    assertEquals(2_500, v.spanMs());
    assertEquals(7_500, v.xToTime(750));
    assertEquals(750, v.timeToX(7_500), 1e-6);
  }

  @Test
  void zoomAndScrollStayInsideTheRecording() {
    TimelineView v = new TimelineView(10_000);
    v.setWidth(1000);
    v.zoom(1000, 500);
    assertEquals(TimelineView.MIN_SPAN_MS, v.spanMs());
    v.zoom(0.0001, 500);
    assertEquals(10_000, v.spanMs());
    assertEquals(0, v.startMs());
    v.zoom(2, 0);
    v.scrollPx(100_000);
    assertEquals(10_000, v.endMs());
    v.scrollPx(-100_000);
    assertEquals(0, v.startMs());
  }

  @Test
  void ensureVisibleScrollsTheLeastAmount() {
    TimelineView v = new TimelineView(60_000);
    v.setWidth(600);
    v.zoom(6, 0); // 0..10 s
    v.ensureVisible(15_000);
    assertEquals(5_000, v.startMs());
    v.ensureVisible(8_000);
    assertEquals(5_000, v.startMs());
    v.ensureVisible(1_000);
    assertEquals(1_000, v.startMs());
  }

  @Test
  void tickStepKeepsTicksApart() {
    TimelineView v = new TimelineView(60_000);
    v.setWidth(600); // 100 ms per pixel
    assertEquals(5_000, v.tickStepMs(40));
    v.zoom(60, 0); // 1 s across 600 px
    assertEquals(100, v.tickStepMs(40));
  }

  @Test
  void historyUndoesAndRedoes() {
    EditHistory<String> h = new EditHistory<>(10);
    assertNull(h.undo("a"));
    h.record("a");
    h.record("b");
    assertEquals("b", h.undo("c"));
    assertEquals("a", h.undo("b"));
    assertFalse(h.canUndo());
    assertEquals("b", h.redo("a"));
    assertEquals("c", h.redo("b"));
    assertNull(h.redo("c"));
  }

  @Test
  void newChangeClearsRedoAndLimitIsKept() {
    EditHistory<Integer> h = new EditHistory<>(3);
    for (int i = 0; i < 5; i++) {
      h.record(i);
    }
    assertEquals(4, h.undo(5));
    h.record(9);
    assertFalse(h.canRedo());
    assertEquals(9, h.undo(10));
    assertEquals(3, h.undo(9));
    assertEquals(2, h.undo(3));
    assertNull(h.undo(2));
  }

  @Test
  void stateRoundTripsThePath() {
    CameraPath p = path(1000, 2000);
    p.setInterpolation(CameraPath.Interpolation.LINEAR);
    TimelineState s = TimelineState.of(p, 500, 3000);
    p.clear();
    p.setEasing(CameraPath.Easing.EASE_IN_OUT);
    s.applyTo(p);
    assertEquals(List.of(1000L, 2000L), times(p));
    assertEquals(CameraPath.Interpolation.LINEAR, p.interpolation());
    assertEquals(CameraPath.Easing.LINEAR, p.easing());
    assertEquals(500, s.inMs());
  }

  @Test
  void stateRoundTripsEffects() {
    var track = new io.github.thexykril.reizo.core.effects.EffectTrack();
    var gray = io.github.thexykril.reizo.core.effects.Grade.NEUTRAL.withSaturation(0);
    track.put(100, gray);
    TimelineState s = TimelineState.of(path(1000), track, -1, -1);
    track.remove(100);
    track.put(900, gray);
    s.applyTo(track);
    assertEquals(1, track.size());
    assertEquals(gray, track.at(100));
  }

  @Test
  void moveShiftsAGroupAndClampsToTheRecording() {
    CameraPath p = path(1000, 2000, 5000);
    Set<Long> moved = KeyframeEdits.move(p, Set.of(1000L, 2000L), 500, 10_000);
    assertEquals(Set.of(1500L, 2500L), moved);
    assertEquals(List.of(1500L, 2500L, 5000L), times(p));
    assertEquals(1.0, p.keyframes().get(0).x(), 1e-9, "keyframe content moves with it");
    KeyframeEdits.move(p, Set.of(1500L, 2500L), -9_000, 10_000);
    assertEquals(List.of(0L, 1000L, 5000L), times(p));
    KeyframeEdits.move(p, Set.of(5000L), 9_000, 10_000);
    assertEquals(10_000L, p.endMs());
  }

  @Test
  void movedKeyframeReplacesOneItLandsOn() {
    CameraPath p = path(1000, 2000);
    KeyframeEdits.move(p, Set.of(1000L), 1000, 10_000);
    assertEquals(List.of(2000L), times(p));
    assertEquals(1.0, p.keyframes().get(0).x(), 1e-9);
  }

  @Test
  void copyPasteKeepsSpacing() {
    CameraPath p = path(1000, 1500, 4000);
    List<CameraKeyframe> c = KeyframeEdits.copy(p, Set.of(1000L, 1500L));
    Set<Long> pasted = KeyframeEdits.paste(p, c, 8000, 8200);
    assertEquals(Set.of(8000L), pasted, "the second would land after the end");
    assertEquals(List.of(1000L, 1500L, 4000L, 8000L), times(p));
    assertTrue(KeyframeEdits.paste(p, List.of(), 0, 10).isEmpty());
  }

  @Test
  void betweenAndRemove() {
    CameraPath p = path(1000, 2000, 3000);
    assertEquals(Set.of(2000L, 3000L), KeyframeEdits.between(p, 3500, 1500));
    assertEquals(2, KeyframeEdits.remove(p, Set.of(1000L, 3000L, 7L)));
    assertEquals(List.of(2000L), times(p));
  }
}
