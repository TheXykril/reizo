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
  void motionBlurSpreadsSamplesOverTheShutter() {
    ExportPlan plan = new ExportPlan(1000, 2000, 50, 4, 0.5); // 20 ms frames, 10 ms open
    assertEquals(51, plan.frameCount());
    assertEquals(204, plan.sampleCount());
    assertEquals(1000, plan.timeOfSample(0), 1e-9);
    assertEquals(1007.5, plan.timeOfSample(3), 1e-9);
    assertEquals(1020, plan.timeOfSample(4), 1e-9, "next frame starts on its own time");
    assertEquals(plan.timeOfFrame(50), plan.timeOfSample(200), 1e-9);
    ExportPlan none = new ExportPlan(0, 1000, 30);
    assertEquals(none.frameCount(), none.sampleCount());
    assertEquals(none.timeOfFrame(7), none.timeOfSample(7), 1e-9);
    assertThrows(IllegalArgumentException.class, () -> new ExportPlan(0, 10, 30, 0, 0.5));
    assertThrows(IllegalArgumentException.class, () -> new ExportPlan(0, 10, 30, 2, 1.5));
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

  private static String line(String codec, int crf, int outW, int outH, String file) {
    return String.join(
        " ",
        FfmpegCommand.build(
            "ffmpeg",
            320,
            240,
            30,
            new FfmpegCommand.Settings(codec, crf, "medium", outW, outH, "yuv420p"),
            Path.of(file)));
  }

  @Test
  void commandOptionsPerEncoder() {
    String vp9 = line("libvpx-vp9", 31, 0, 0, "a.webm");
    assertTrue(vp9.contains("-c:v libvpx-vp9 -crf 31 -b:v 0"), vp9);
    assertTrue(vp9.contains("-pix_fmt yuv420p"), vp9);
    String av1 = line("libsvtav1", 30, 0, 0, "a.mkv");
    assertTrue(av1.contains("-c:v libsvtav1 -crf 30 -preset 8"), av1);
    String nvenc = line("h264_nvenc", 22, 0, 0, "a.mp4");
    assertTrue(nvenc.contains("-c:v h264_nvenc -preset p5 -rc vbr -cq 22 -b:v 0"), nvenc);
    String qsv = line("h264_qsv", 23, 0, 0, "a.mp4");
    assertTrue(qsv.contains("-global_quality 23 -pix_fmt nv12"), qsv);
  }

  @Test
  void vaapiUploadsFramesAfterScaling() {
    String vaapi = line("h264_vaapi", 24, 640, 480, "a.mp4");
    assertTrue(vaapi.startsWith("ffmpeg -hide_banner -loglevel error -y -vaapi_device "), vaapi);
    assertTrue(
        vaapi.contains("-vf scale=640:480:flags=lanczos,format=nv12,hwupload -c:v h264_vaapi"),
        vaapi);
    assertTrue(vaapi.contains("-rc_mode CQP -qp 24"), vaapi);
    assertFalse(vaapi.contains("-pix_fmt yuv420p"), vaapi);
    String same = line("hevc_vaapi", 24, 0, 0, "a.mp4");
    assertTrue(same.contains("-vf format=nv12,hwupload"), same);
  }

  @Test
  void formatsFromEncoderList() {
    String out =
        String.join(
            "\n",
            "Encoders:",
            " V..... = Video",
            " ------",
            " V....D libx264              libx264 H.264",
            " V....D libvpx-vp9           libvpx VP9",
            " A....D aac                  AAC",
            " V....D h264_vaapi           H.264/AVC (VAAPI)");
    assertEquals(
        List.of(ExportFormat.H264, ExportFormat.VP9, ExportFormat.H264_VAAPI),
        ExportFormat.available(out));
    assertTrue(ExportFormat.available("").isEmpty());
    assertEquals(ExportFormat.VP9, ExportFormat.forEncoder("libvpx-vp9"));
    assertEquals(ExportFormat.H264, ExportFormat.forEncoder("unknown"));
    assertEquals("clip.webm", ExportFormat.VP9.fileName("clip.mp4"));
    assertEquals("clip.mkv", ExportFormat.AV1.fileName("clip"));
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
  void rendersAtTheOutputSizeWhenTheGpuAllows() {
    assertEquals(new RenderSize(853, 480), RenderSize.of(0, 0, 853, 480, 16384));
    assertEquals(new RenderSize(3840, 2160), RenderSize.of(3840, 2160, 853, 480, 16384));
    assertEquals(new RenderSize(1920, 1080), RenderSize.of(1921, 1081, 853, 480, 16384));
    assertEquals(new RenderSize(853, 480), RenderSize.of(3840, 2160, 853, 480, 2048));
    assertTrue(new RenderSize(2, 4).matches(2, 4));
    // Rendering at the output size means FFmpeg does not scale.
    List<String> cmd =
        FfmpegCommand.build(
            "ffmpeg",
            1920,
            1080,
            60,
            new FfmpegCommand.Settings("libx264", 20, "fast", 1920, 1080, "yuv420p"),
            Path.of("a.mp4"));
    assertFalse(cmd.contains("-vf"), String.join(" ", cmd));
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
