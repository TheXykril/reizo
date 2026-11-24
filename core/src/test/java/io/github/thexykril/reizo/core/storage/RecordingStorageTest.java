/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thexykril.reizo.core.format.Frame;
import io.github.thexykril.reizo.core.format.FrameStreamReader;
import io.github.thexykril.reizo.core.format.FrameStreamWriter;
import io.github.thexykril.reizo.core.format.ProtocolPhase;
import io.github.thexykril.reizo.core.format.RecordingMetadata;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordingStorageTest {
  @TempDir Path dir;

  private RecordingStorage storage() {
    return new RecordingStorage(dir, ZoneOffset.UTC);
  }

  /** Simulates a client that crashed mid-recording: frames on disk, lock released, no stop. */
  private Path crashedRecording(RecordingStorage storage, int frames, boolean tearTail)
      throws IOException {
    RecordingMetadata meta = RecordingMetadata.create("crash test", 0L);
    RecordingStorage.WorkingDirectory working = storage.createWorking(meta);
    FrameStreamWriter writer = FrameStreamWriter.create(working.streamFile(), 1024);
    for (int i = 0; i < frames; i++) {
      writer.write(Frame.packet(i * 10, ProtocolPhase.PLAY, new byte[200]));
    }
    writer.close();
    if (tearTail) {
      try (FileChannel ch = FileChannel.open(working.streamFile(), StandardOpenOption.WRITE)) {
        ch.truncate(ch.size() - 50);
      }
    }
    working.close(); // the OS releases locks when a process dies
    return working.dir();
  }

  @Test
  void thumbnailIsPackedAndSurvivesRename() throws IOException {
    RecordingStorage storage = storage();
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
    RecordingMetadata meta = RecordingMetadata.create("thumb", 0L);
    RecordingStorage.WorkingDirectory working = storage.createWorking(meta);
    FrameStreamWriter.create(working.streamFile(), 1024).close();
    Files.write(working.thumbnailFile(), png);
    working.close();
    Path archive = storage.finalizeRecording(working.dir());

    assertArrayEquals(png, RecordingArchive.readThumbnail(archive));
    storage.rename(archive, "renamed");
    assertArrayEquals(png, RecordingArchive.readThumbnail(archive));
  }

  @Test
  void recordingWithoutThumbnailHasNone() throws IOException {
    RecordingStorage storage = storage();
    Path archive = storage.recover(crashedRecording(storage, 3, false)).archive();
    assertNull(RecordingArchive.readThumbnail(archive));
    assertNull(RecordingArchive.readThumbnail(dir.resolve("missing.reizo")));
  }

  @Test
  void liveSessionIsNotReportedAsAbandoned() throws IOException {
    RecordingStorage storage = storage();
    try (RecordingStorage.WorkingDirectory live =
        storage.createWorking(RecordingMetadata.create("live", 0L))) {
      assertTrue(Files.isDirectory(live.dir()));
      assertTrue(storage.findAbandoned().isEmpty());
    }
    assertEquals(1, storage.findAbandoned().size());
  }

  @Test
  void recoversIntactStreamCompletely() throws IOException {
    RecordingStorage storage = storage();
    Path working = crashedRecording(storage, 100, false);
    RecordingStorage.RecoveryResult result = storage.recover(working);

    assertTrue(result.recovered());
    assertEquals(100, result.frames());
    assertNull(result.problem());
    assertFalse(Files.exists(working));
    try (RecordingArchive archive = RecordingArchive.open(result.archive());
        FrameStreamReader reader = archive.openStream()) {
      RecordingMetadata meta = archive.metadata();
      assertEquals(RecordingMetadata.State.RECOVERED, meta.state);
      assertEquals(990, meta.durationMs);
      assertEquals(100, reader.readAll().size());
    }
  }

  @Test
  void recoversValidPrefixOfTornStream() throws IOException {
    RecordingStorage storage = storage();
    Path working = crashedRecording(storage, 100, true);
    RecordingStorage.RecoveryResult result = storage.recover(working);

    assertTrue(result.recovered());
    assertTrue(result.frames() > 0 && result.frames() < 100);
    assertTrue(result.problem() != null);
    try (RecordingArchive archive = RecordingArchive.open(result.archive());
        FrameStreamReader reader = archive.openStream()) {
      List<Frame> frames = reader.readAll(); // strict read must succeed after recovery
      assertEquals(result.frames(), frames.size());
      assertEquals(archive.metadata().durationMs, frames.get(frames.size() - 1).timeMs());
    }
  }

  @Test
  void leavesUnrecoverableDirectoryInPlace() throws IOException {
    RecordingStorage storage = storage();
    RecordingStorage.WorkingDirectory working =
        storage.createWorking(RecordingMetadata.create("broken", 0L));
    working.close();
    Files.writeString(working.streamFile(), "garbage");
    RecordingStorage.RecoveryResult result = storage.recover(working.dir());
    assertFalse(result.recovered());
    assertTrue(Files.isDirectory(working.dir()), "never delete data we could not salvage");
  }

  @Test
  void archiveNamesAreUniqueAndSanitized() throws IOException {
    RecordingStorage storage = storage();
    Path a = storage.recover(crashedRecording(storage, 1, false)).archive();
    Path b = storage.recover(crashedRecording(storage, 1, false)).archive();
    assertNotEquals(a, b);
    assertTrue(a.getFileName().toString().startsWith("crash test_1970-01-01_00-00-00"));
    assertEquals(2, storage.listArchives().size());

    assertEquals("a_b_c", RecordingStorage.sanitize("a/b\\c"));
    assertEquals("recording", RecordingStorage.sanitize(".."));
    assertEquals("recording", RecordingStorage.sanitize("   "));
  }

  private Path finishedRecording(RecordingStorage storage, String name) throws IOException {
    RecordingMetadata meta = RecordingMetadata.create(name, 0L);
    RecordingStorage.WorkingDirectory working = storage.createWorking(meta);
    FrameStreamWriter writer = FrameStreamWriter.create(working.streamFile(), 1024);
    writer.write(Frame.packet(0, ProtocolPhase.PLAY, new byte[] {1, 2, 3}));
    writer.close();
    working.close();
    return storage.finalizeRecording(working.dir());
  }

  @Test
  void listRecordingsSkipsUnreadableFiles() throws IOException {
    RecordingStorage storage = storage();
    Path good = finishedRecording(storage, "good");
    Files.writeString(dir.resolve("broken.reizo"), "not a zip");
    List<RecordingStorage.Entry> list = storage.listRecordings();
    assertEquals(1, list.size());
    assertEquals(good, list.get(0).path());
    assertEquals("good", list.get(0).metadata().name);
    assertTrue(list.get(0).sizeBytes() > 0);
  }

  @Test
  void renameKeepsFileAndFrames() throws IOException {
    RecordingStorage storage = storage();
    Path p = finishedRecording(storage, "before");
    storage.rename(p, "  after  ");
    try (RecordingArchive archive = RecordingArchive.open(p);
        FrameStreamReader reader = archive.openStream()) {
      assertEquals("after", archive.metadata().name);
      List<Frame> frames = reader.readAll();
      assertEquals(1, frames.size());
      assertEquals(3, frames.get(0).packetBytes().length);
    }
    assertFalse(Files.exists(p.resolveSibling(p.getFileName() + ".tmp")));
    assertThrows(IllegalArgumentException.class, () -> storage.rename(p, " "));
  }

  @Test
  void deleteOnlyAcceptsRecordingsInRoot(@TempDir Path other) throws IOException {
    RecordingStorage storage = storage();
    Path p = finishedRecording(storage, "gone");
    Path outside = Files.writeString(other.resolve("x.reizo"), "x");
    assertThrows(IllegalArgumentException.class, () -> storage.delete(outside));
    assertThrows(IllegalArgumentException.class, () -> storage.delete(dir.resolve("../x.reizo")));
    storage.delete(p);
    assertFalse(Files.exists(p));
    assertTrue(Files.exists(outside));
  }
}
