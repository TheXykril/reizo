/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thexykril.reizo.core.camera.CameraPath;
import io.github.thexykril.reizo.core.effects.PropertyTracks.Kind;
import io.github.thexykril.reizo.core.project.Project;
import io.github.thexykril.reizo.core.timeline.TimelineState;
import java.util.List;
import org.junit.jupiter.api.Test;

class PropertyTracksTest {
  @Test
  void numbersBlendAndHoldAtTheEnds() {
    PropertyTracks p = new PropertyTracks();
    assertTrue(Double.isNaN(p.valueAt("minecraft:gamma", 0)));
    p.put("minecraft:gamma", Kind.NUMBER, 1000, 0.2);
    p.put("minecraft:gamma", Kind.NUMBER, 3000, 1.0);
    assertEquals(0.2, p.valueAt("minecraft:gamma", 0), 1e-9);
    assertEquals(0.6, p.valueAt("minecraft:gamma", 2000), 1e-9);
    assertEquals(0.4, p.valueAt("minecraft:gamma", 1500), 1e-9);
    assertEquals(1.0, p.valueAt("minecraft:gamma", 9000), 1e-9);
  }

  @Test
  void togglesSwitchAtKeyframes() {
    PropertyTracks p = new PropertyTracks();
    p.put("mod:hud", Kind.TOGGLE, 1000, 1);
    p.put("mod:hud", Kind.TOGGLE, 2000, 0);
    assertEquals(1, p.valueAt("mod:hud", 0));
    assertEquals(1, p.valueAt("mod:hud", 1999.9));
    assertEquals(0, p.valueAt("mod:hud", 2000));
    assertEquals(0, p.valueAt("mod:hud", 5000));
  }

  @Test
  void removingTheLastKeyframeDropsTheTrack() {
    PropertyTracks p = new PropertyTracks();
    p.put("a", Kind.NUMBER, 10, 1);
    p.put("b", Kind.NUMBER, 20, 2);
    assertEquals(10, p.nearest("a", 300, 500));
    assertEquals(-1, p.nearest("a", 900, 500));
    assertTrue(p.remove("a", 10));
    assertFalse(p.has("a"));
    assertFalse(p.remove("a", 10));
    assertEquals(List.of("b"), List.copyOf(p.properties()));
  }

  @Test
  void projectAndUndoSnapshotsRoundTrip() {
    PropertyTracks p = new PropertyTracks();
    p.put("oneconfig:polysprint/enabled", Kind.TOGGLE, 0, 1);
    p.put("minecraft:gamma", Kind.NUMBER, 500, 0.5);
    p.put("minecraft:gamma", Kind.NUMBER, 1500, 1);

    Project project = Project.create("id", "name", "rec.reizo", 0);
    project.setSettings(p);
    Project back = Project.fromJson(project.toJson());
    assertEquals(p.keyframes(), back.settings().keyframes());

    TimelineState s = TimelineState.of(new CameraPath(), new EffectTrack(), p, -1, -1);
    PropertyTracks other = new PropertyTracks();
    other.put("x", Kind.NUMBER, 1, 1);
    s.applyTo(other);
    assertEquals(p.keyframes(), other.keyframes());
  }

  @Test
  void oldProjectsHaveNoSettings() {
    Project p = Project.fromJson("{\"recordingFile\":\"a.reizo\",\"settings\":null}");
    assertTrue(p.settings().isEmpty());
    Project q = Project.fromJson("{\"recordingFile\":\"a.reizo\",\"settings\":[{\"id\":null}]}");
    assertTrue(q.settings().isEmpty());
  }
}
