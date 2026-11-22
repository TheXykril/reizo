/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportTest {
  @TempDir Path dir;

  @Test
  void planComputesFramesFromIndex() {
    ExportPlan plan = new ExportPlan(1000, 2000, 60);
    assertEquals(61, plan.frameCount());
    assertEquals(1000, plan.timeOfFrame(0), 1e-9);
    assertEquals(2000, plan.timeOfFrame(60), 1e-9);
    assertEquals(1000 + 1000.0 / 60 * 7, plan.timeOfFrame(7), 1e-9);
    // No drift after many frames.
    ExportPlan hour = new ExportPlan(0, 3_600_000, 60);
    assertEquals(3_600_000, hour.timeOfFrame(216_000), 1e-6);
    assertEquals(0.5, new ExportPlan(0, 999, 2).progress(1), 1e-9);
    assertThrows(IllegalArgumentException.class, () -> new ExportPlan(0, 0, 60));
    assertThrows(IllegalArgumentException.class, () -> new ExportPlan(0, 10, 0));
  }

  @Test
  void commandForX264WithScaling() {
    List<String> cmd =
        FfmpegCommand.build(
            "ffmpeg",
            853,
            480,
            30,
            new FfmpegCommand.Settings("libx264", 20, "fast", 1921, 1080, "yuv420p"),
            Path.of("out.mp4"));
    String line = String.join(" ", cmd);
    assertTrue(line.contains("-f rawvideo -pix_fmt rgba -s 853x480 -r 30 -i -"), line);
    assertTrue(line.contains("-vf scale=1920:1080:flags=lanczos"), line);
    assertTrue(line.contains("-c:v libx264 -preset fast -crf 20"), line);
    assertTrue(line.endsWith("-movflags +faststart out.mp4"), line);
  }

  @Test
  void commandWithoutScalingWhenSizesMatch() {
    List<String> cmd =
        FfmpegCommand.build(
            "ffmpeg", 320, 240, 60, FfmpegCommand.Settings.h264(18), Path.of("a.mkv"));
    assertFalse(cmd.contains("-vf"));
    assertFalse(cmd.contains("-movflags"));
  }

  @Test
  void locatesFfmpegOnPathOrConfigured() throws IOException {
    if (File.separatorChar == '\\') {
      return; // POSIX permissions below
    }
    Path bin = Files.createDirectory(dir.resolve("bin"));
    Path ff = Files.writeString(bin.resolve("ffmpeg"), "#!/bin/sh\n");
    Files.setPosixFilePermissions(ff, PosixFilePermissions.fromString("rwxr-xr-x"));
    assertEquals(ff.toString(), FfmpegCommand.locate(null, "/nonexistent:" + bin).orElseThrow());
    assertEquals(ff.toString(), FfmpegCommand.locate(ff.toString(), null).orElseThrow());
    assertTrue(FfmpegCommand.locate(dir.resolve("nope").toString(), bin.toString()).isEmpty());
    assertTrue(FfmpegCommand.locate("", "/nonexistent").isEmpty());
  }
}
