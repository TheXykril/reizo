/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.thexykril.reizo.core.camera.CameraKeyframe;
import io.github.thexykril.reizo.core.camera.CameraPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectStoreTest {
  @TempDir Path dir;

  @Test
  void roundTripsCameraPathAndSettings() throws IOException {
    ProjectStore store = new ProjectStore(dir);
    Project p = Project.create("p1", "Clip", "world.reizo", 10);
    CameraPath path = new CameraPath();
    path.setEasing(CameraPath.Easing.EASE_IN_OUT);
    path.put(new CameraKeyframe(0, 1, 2, 3, 90, 10, 5, 60));
    path.put(new CameraKeyframe(500, 4, 5, 6, -90, -10, 0, 80));
    p.setCameraPath(path);
    p.render.fps = 30;
    store.save(p, 20);

    Project back = store.load("p1");
    assertEquals(20, back.modifiedAtEpochMs);
    assertEquals(30, back.render.fps);
    CameraPath again = back.cameraPath();
    assertEquals(CameraPath.Easing.EASE_IN_OUT, again.easing());
    assertEquals(path.keyframes(), again.keyframes());
  }

  @Test
  void findsByRecordingAndSkipsBadFiles() throws IOException {
    ProjectStore store = new ProjectStore(dir);
    store.save(Project.create("a", "A", "one.reizo", 1), 1);
    Project b = Project.create("b", "B", "two.reizo", 1);
    b.recordingId = "rec-2";
    store.save(b, 2);
    Files.writeString(dir.resolve("broken.reizoproj"), "{");
    Files.writeString(dir.resolve("future.reizoproj"), "{\"schema\":99,\"recordingFile\":\"x\"}");
    assertEquals(2, store.list().size());
    assertEquals("b", store.list().get(0).id, "newest first");
    assertEquals("a", store.findForRecording("one.reizo", null).id);
    assertEquals("b", store.findForRecording("renamed.reizo", "rec-2").id);
    assertNull(store.findForRecording("none.reizo", "none"));
  }

  @Test
  void rejectsUnsafeIds() {
    ProjectStore store = new ProjectStore(dir);
    assertThrows(IllegalArgumentException.class, () -> store.pathOf("../x"));
    assertThrows(IllegalArgumentException.class, () -> store.pathOf(""));
  }
}
