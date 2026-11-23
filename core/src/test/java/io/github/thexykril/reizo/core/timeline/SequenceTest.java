/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.thexykril.reizo.core.timeline.Sequence.Clip;
import java.util.List;
import org.junit.jupiter.api.Test;

class SequenceTest {
  @Test
  void splitKeepsTheVideoAndMapsBothWays() {
    Sequence s = Sequence.of(0, 10_000).split(4_000);
    assertEquals(List.of(new Clip(0, 4_000), new Clip(4_000, 10_000)), s.clips());
    assertEquals(10_000, s.durationMs());
    assertEquals(6_000, s.toReplay(6_000));
    assertEquals(6_000, s.toVideo(6_000));
  }

  @Test
  void deleteClosesTheGapAndCutMomentsHaveNoVideoTime() {
    Sequence s = Sequence.of(0, 10_000).split(3_000).split(5_000).delete(1);
    assertEquals(List.of(new Clip(0, 3_000), new Clip(5_000, 10_000)), s.clips());
    assertEquals(8_000, s.durationMs());
    assertEquals(5_500, s.toReplay(3_500));
    assertEquals(3_500, s.toVideo(5_500));
    assertEquals(-1, s.toVideo(4_000));
    assertEquals(-1, s.clipOfReplay(4_000));
  }

  @Test
  void moveChangesThePlayOrder() {
    Sequence s = Sequence.of(0, 10_000).split(4_000).move(1, 0);
    assertEquals(List.of(new Clip(4_000, 10_000), new Clip(0, 4_000)), s.clips());
    assertEquals(4_000, s.toReplay(0));
    assertEquals(1_000, s.toReplay(7_000));
    assertEquals(6_000, s.toVideo(0));
    assertEquals(1, s.indexAt(6_000));
    assertEquals(6_000, s.startOf(1));
  }

  @Test
  void trimsStayClearOfOtherClipsAndKeepAMinimumLength() {
    Sequence s = Sequence.of(0, 10_000).split(4_000).delete(0);
    assertEquals(new Clip(1_000, 10_000), s.trimStart(0, 1_000).clip(0));
    Sequence two = Sequence.of(0, 10_000).split(4_000);
    assertEquals(new Clip(0, 4_000), two.trimEnd(0, 6_000, 10_000).clip(0));
    assertEquals(new Clip(4_000, 10_000), two.trimStart(1, 2_000).clip(1));
    assertEquals(new Clip(0, Sequence.MIN_CLIP_MS), two.trimEnd(0, 0, 10_000).clip(0));
    assertEquals(new Clip(4_000, 9_000), two.trimEnd(1, 9_000, 10_000).clip(1));
  }

  @Test
  void splitsTooCloseToAnEdgeAndDeletingTheLastClipDoNothing() {
    Sequence s = Sequence.of(0, 10_000);
    assertSame(s, s.split(50));
    assertSame(s, s.delete(0));
  }

  @Test
  void damagedStoredClipsAreDropped() {
    Sequence s =
        Sequence.of(List.of(new Clip(0, 5_000), new Clip(4_000, 6_000), new Clip(7_000, 8_000)));
    assertEquals(List.of(new Clip(0, 5_000), new Clip(7_000, 8_000)), s.clips());
  }

  @Test
  void anEmptySequenceIsTheIdentity() {
    Sequence s = Sequence.empty();
    assertEquals(1_234, s.toReplay(1_234));
    assertEquals(1_234, s.toVideo(1_234));
  }

  @Test
  void projectsSaveAndLoadClips() {
    var p = io.github.thexykril.reizo.core.project.Project.create("id", "n", "r.reizo", 0);
    assertEquals(Sequence.empty(), p.sequence());
    Sequence s = Sequence.of(0, 10_000).split(4_000).move(1, 0);
    p.setSequence(s);
    var back = io.github.thexykril.reizo.core.project.Project.fromJson(p.toJson());
    assertEquals(s, back.sequence());
    var old = io.github.thexykril.reizo.core.project.Project.fromJson("{\"recordingFile\":\"a\"}");
    assertEquals(Sequence.empty(), old.sequence());
  }
}
