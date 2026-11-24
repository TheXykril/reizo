/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thexykril.reizo.core.project.Project;
import org.junit.jupiter.api.Test;

class EffectsTest {
  @Test
  void gradeClampsAndKnowsNeutral() {
    assertTrue(Grade.NEUTRAL.isNeutral());
    Grade g = new Grade(9, -1, 5, 2, 3, 0.5f);
    assertEquals(new Grade(3, 0, 2, 1, 1, 1), g);
    assertTrue(Grade.NEUTRAL.withFocus(30).isNeutral(), "focus alone changes nothing");
    assertFalse(Grade.NEUTRAL.withBloom(0.5f).isNeutral());
    assertEquals(Grade.AUTO_FOCUS, Grade.NEUTRAL.withFocus(0).focus());
    assertEquals(0.5f, Grade.NEUTRAL.withFocus(0.4f).focus());
    assertEquals(
        Grade.AUTO_FOCUS,
        Grade.NEUTRAL.withFocus(Grade.AUTO_FOCUS).lerp(Grade.NEUTRAL.withFocus(20), 0.5).focus(),
        "automatic focus does not blend");
    assertEquals(1, Grade.NEUTRAL.withDof(3).dof());
    assertFalse(g.isNeutral());
    assertEquals(0, new Grade(0, 1, 1, 0, 0, -2).letterbox());
  }

  @Test
  void letterboxBarsCoverTheRightHeight() {
    Grade scope = Grade.NEUTRAL.withLetterbox(2.39f);
    // 1920x1080: visible 1920/2.39 = 803.3 px, bars (1080-803.3)/2 = 138.3 px each.
    assertEquals(138.3 / 1080, scope.letterboxBar(1920, 1080), 1e-3);
    assertEquals(0, scope.letterboxBar(3000, 1000), 1e-9, "already wider");
    assertEquals(0, Grade.NEUTRAL.letterboxBar(1920, 1080), 1e-9);
  }

  @Test
  void trackBlendsBetweenKeyframesAndHoldsOutside() {
    EffectTrack track = new EffectTrack();
    assertEquals(Grade.NEUTRAL, track.at(500));
    track.put(1000, Grade.NEUTRAL.withSaturation(0).withLetterbox(2.39f));
    track.put(3000, Grade.NEUTRAL.withSaturation(2).withExposure(2));
    assertEquals(0, track.at(0).saturation(), 1e-6);
    assertEquals(1, track.at(2000).saturation(), 1e-6);
    assertEquals(1, track.at(2000).exposure(), 1e-6);
    assertEquals(2.39f, track.at(2999).letterbox(), 1e-6, "letterbox switches, not blends");
    assertEquals(0, track.at(3000).letterbox(), 1e-6);
    assertEquals(2, track.at(9000).saturation(), 1e-6);
    assertEquals(1000, track.nearest(1040, 50));
    assertEquals(-1, track.nearest(1100, 50));
    assertTrue(track.remove(1000));
    assertEquals(1, track.size());
  }

  @Test
  void projectStoresEffectKeyframes() {
    Project p = Project.create("p", "P", "r.reizo", 0);
    EffectTrack track = new EffectTrack();
    track.put(500, new Grade(1, 1.2f, 0.5f, -0.3f, 0.4f, 2.39f, 0.6f, 0.8f, 20));
    p.setEffects(track);
    Project back = Project.fromJson(p.toJson());
    assertEquals(track.keyframes(), back.effects().keyframes());
    // Old projects without effects load with none.
    Project old = Project.fromJson("{\"recordingFile\":\"r.reizo\"}");
    assertTrue(old.effects().isEmpty());
    // Effects saved before bloom and depth of field load without them.
    Project before =
        Project.fromJson(
            "{\"recordingFile\":\"r.reizo\",\"effects\":[{\"t\":0,\"saturation\":0}]}");
    Grade g = before.effects().at(0);
    assertEquals(0, g.bloom());
    assertEquals(0, g.dof());
    assertEquals(Grade.DEFAULT_FOCUS, g.focus());
  }
}
