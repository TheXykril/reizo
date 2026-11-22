/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.format;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FrameStreamTest {
  @TempDir Path dir;

  private static List<Frame> sampleFrames(int count, long seed) {
    Random random = new Random(seed);
    List<Frame> frames = new ArrayList<>();
    int time = 0;
    for (int i = 0; i < count; i++) {
      time += random.nextInt(60);
      byte[] body = new byte[random.nextInt(2000)];
      random.nextBytes(body);
      frames.add(
          i % 7 == 0
              ? Frame.of(time, FrameType.LOCAL_PLAYER, body)
              : Frame.packet(
                  time, i % 3 == 0 ? ProtocolPhase.CONFIGURATION : ProtocolPhase.PLAY, body));
    }
    return frames;
  }

  private Path writeStream(List<Frame> frames, int blockBytes) throws IOException {
    Path file = dir.resolve("stream.bin");
    try (FrameStreamWriter writer = FrameStreamWriter.create(file, blockBytes)) {
      for (Frame f : frames) {
        writer.write(f);
      }
    }
    return file;
  }

  private static List<Frame> readAll(Path file) throws IOException {
    try (FrameStreamReader reader = FrameStreamReader.open(Files.newInputStream(file))) {
      return reader.readAll();
    }
  }

  @Test
  void roundTripPreservesEveryFrameAcrossManyBlocks() throws IOException {
    List<Frame> frames = sampleFrames(3000, 42);
    Path file = writeStream(frames, 4096);
    assertEquals(frames, readAll(file));
  }

  @Test
  void emptyStreamHasHeaderOnly() throws IOException {
    Path file = writeStream(List.of(), 4096);
    assertEquals(StreamFormat.HEADER_SIZE, Files.size(file));
    assertTrue(readAll(file).isEmpty());
  }

  @Test
  void packetFrameExposesPhaseAndBytes() {
    Frame f = Frame.packet(10, ProtocolPhase.CONFIGURATION, new byte[] {7, 8, 9});
    assertEquals(FrameType.PACKET, f.type());
    assertEquals(ProtocolPhase.CONFIGURATION, f.packetPhase());
    assertArrayEquals(new byte[] {7, 8, 9}, f.packetBytes());
  }

  @Test
  void unknownFrameTypesSurviveRoundTrip() throws IOException {
    Frame future = new Frame(5, 999, new byte[] {1, 2});
    Path file = writeStream(List.of(future), 4096);
    List<Frame> read = readAll(file);
    assertEquals(List.of(future), read);
    assertNull(read.get(0).type());
  }

  @Test
  void rejectsTimeGoingBackwards() throws IOException {
    Path file = dir.resolve("s.bin");
    try (FrameStreamWriter writer = FrameStreamWriter.create(file, 4096)) {
      writer.write(Frame.of(100, FrameType.MARKER, new byte[0]));
      assertThrows(
          IllegalArgumentException.class,
          () -> writer.write(Frame.of(99, FrameType.MARKER, new byte[0])));
    }
  }

  @Test
  void refusesToOverwriteExistingFile() throws IOException {
    Path file = writeStream(List.of(), 4096);
    assertThrows(IOException.class, () -> FrameStreamWriter.create(file, 4096));
  }

  @Test
  void rejectsWrongMagicAndNewerVersion() throws IOException {
    byte[] garbage = "definitely not a stream".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    assertThrows(
        FormatException.class, () -> FrameStreamReader.open(new ByteArrayInputStream(garbage)));

    Path file = writeStream(List.of(), 4096);
    byte[] bytes = Files.readAllBytes(file);
    bytes[StreamFormat.MAGIC.length + 3] = (byte) (StreamFormat.VERSION + 1);
    FormatException e =
        assertThrows(
            FormatException.class, () -> FrameStreamReader.open(new ByteArrayInputStream(bytes)));
    assertTrue(e.getMessage().contains("Unsupported stream version"));
  }

  @Test
  void strictReaderDetectsFlippedPayloadByte() throws IOException {
    Path file = writeStream(sampleFrames(50, 1), 1 << 20);
    byte[] bytes = Files.readAllBytes(file);
    int inPayload = StreamFormat.HEADER_SIZE + StreamFormat.BLOCK_HEADER_SIZE + 10;
    bytes[inPayload] ^= 0x55;
    try (FrameStreamReader reader = FrameStreamReader.open(new ByteArrayInputStream(bytes))) {
      FormatException e = assertThrows(FormatException.class, reader::readAll);
      assertTrue(e.getMessage().contains("checksum"));
    }
  }

  @Test
  void scanStopsAtTornTailAndReportsValidPrefix() throws IOException {
    List<Frame> frames = sampleFrames(500, 7);
    Path file = writeStream(frames, 2048);
    long fullSize = Files.size(file);

    // Simulate a crash in the middle of writing the last block.
    try (FileChannel ch = FileChannel.open(file, StandardOpenOption.WRITE)) {
      ch.truncate(fullSize - 100);
    }
    FrameStreamReader.ScanResult scan;
    try (InputStream in = Files.newInputStream(file)) {
      scan = FrameStreamReader.scan(in);
    }
    assertTrue(scan.truncated());
    assertTrue(scan.validLength() < fullSize - 100);
    assertTrue(scan.frameCount() > 0 && scan.frameCount() < frames.size());

    // Everything in the valid prefix must be an exact prefix of what was written.
    try (FileChannel ch = FileChannel.open(file, StandardOpenOption.WRITE)) {
      ch.truncate(scan.validLength());
    }
    List<Frame> salvaged = readAll(file);
    assertEquals(frames.subList(0, salvaged.size()), salvaged);
    assertEquals(scan.frameCount(), salvaged.size());
  }

  @Test
  void scanOfIntactStreamIsNotTruncated() throws IOException {
    List<Frame> frames = sampleFrames(100, 3);
    Path file = writeStream(frames, 2048);
    try (InputStream in = Files.newInputStream(file)) {
      FrameStreamReader.ScanResult scan = FrameStreamReader.scan(in);
      assertFalse(scan.truncated());
      assertEquals(Files.size(file), scan.validLength());
      assertEquals(frames.size(), scan.frameCount());
      assertEquals(frames.get(frames.size() - 1).timeMs(), scan.lastTimeMs());
    }
  }

  @Test
  void appendAfterRecoveryContinuesTheStream() throws IOException {
    List<Frame> first = sampleFrames(40, 9);
    Path file = writeStream(first, 1024);
    long size = Files.size(file);
    int lastTime = first.get(first.size() - 1).timeMs();
    try (FrameStreamWriter writer = FrameStreamWriter.appendTo(file, size, 1024)) {
      writer.write(Frame.of(lastTime + 5, FrameType.MARKER, new byte[] {1}));
    }
    List<Frame> all = readAll(file);
    assertEquals(first.size() + 1, all.size());
    assertEquals(lastTime + 5, all.get(all.size() - 1).timeMs());
  }

  @Test
  void corruptLengthCannotTriggerHugeAllocation() throws IOException {
    Path file = writeStream(sampleFrames(5, 2), 4096);
    byte[] bytes = Files.readAllBytes(file);
    int lengthOffset = StreamFormat.HEADER_SIZE + Integer.BYTES;
    bytes[lengthOffset] = 0x7F; // ~2 GB
    try (FrameStreamReader reader = FrameStreamReader.open(new ByteArrayInputStream(bytes))) {
      assertThrows(FormatException.class, reader::nextBlock);
    }
  }
}
