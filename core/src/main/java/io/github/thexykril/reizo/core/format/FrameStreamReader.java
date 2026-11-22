/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.format;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Reads blocks from a frame stream.
 *
 * <p>Two modes:
 *
 * <ul>
 *   <li><b>Strict</b> ({@link #nextBlock()}): any corruption throws {@link FormatException}. Used
 *       for finalized recordings, which must be intact.
 *   <li><b>Tolerant</b> ({@link #scan(InputStream)}): stops at the first invalid block and reports
 *       how many bytes were valid. Used by crash recovery to cut off a torn tail.
 * </ul>
 */
public final class FrameStreamReader implements Closeable {
  private final InputStream in;
  private final int version;
  private long position;

  private FrameStreamReader(InputStream in, int version) {
    this.in = in;
    this.version = version;
    this.position = StreamFormat.HEADER_SIZE;
  }

  /** Reads and checks the stream header. */
  public static FrameStreamReader open(InputStream in) throws IOException {
    byte[] header = in.readNBytes(StreamFormat.HEADER_SIZE);
    if (header.length < StreamFormat.HEADER_SIZE) {
      throw new FormatException("Stream header truncated");
    }
    if (!Arrays.equals(
        header, 0, StreamFormat.MAGIC.length, StreamFormat.MAGIC, 0, StreamFormat.MAGIC.length)) {
      throw new FormatException("Not a recording stream (bad magic)");
    }
    int version = ByteBuffer.wrap(header, StreamFormat.MAGIC.length, Integer.BYTES).getInt();
    if (version < 1 || version > StreamFormat.VERSION) {
      throw new FormatException(
          "Unsupported stream version "
              + version
              + " (this build reads up to "
              + StreamFormat.VERSION
              + ")");
    }
    return new FrameStreamReader(in, version);
  }

  public int version() {
    return version;
  }

  /** Byte offset just after the last fully read block. */
  public long position() {
    return position;
  }

  /**
   * Reads the next block.
   *
   * @return the block's frames, or {@code null} at a clean end of stream
   * @throws FormatException if the block is truncated or corrupt
   */
  public List<Frame> nextBlock() throws IOException {
    byte[] headerBytes = in.readNBytes(StreamFormat.BLOCK_HEADER_SIZE);
    if (headerBytes.length == 0) {
      return null;
    }
    if (headerBytes.length < StreamFormat.BLOCK_HEADER_SIZE) {
      throw new FormatException("Block header truncated at offset " + position);
    }
    ByteBuffer header = ByteBuffer.wrap(headerBytes);
    int magic = header.getInt();
    int length = header.getInt();
    int crcValue = header.getInt();
    int frameCount = header.getInt();
    int firstTime = header.getInt();
    int lastTime = header.getInt();
    if (magic != StreamFormat.BLOCK_MAGIC) {
      throw new FormatException("Bad block magic at offset " + position);
    }
    if (length < 0 || length > StreamFormat.MAX_BLOCK_PAYLOAD || frameCount < 0) {
      throw new FormatException("Bad block header at offset " + position);
    }
    byte[] payload = in.readNBytes(length);
    if (payload.length < length) {
      throw new FormatException("Block payload truncated at offset " + position);
    }
    CRC32 crc = new CRC32();
    crc.update(payload);
    if ((int) crc.getValue() != crcValue) {
      throw new FormatException("Block checksum mismatch at offset " + position);
    }
    List<Frame> frames = decodeFrames(payload, frameCount, firstTime);
    if (!frames.isEmpty() && frames.get(frames.size() - 1).timeMs() != lastTime) {
      throw new FormatException("Block last time mismatch at offset " + position);
    }
    position += StreamFormat.BLOCK_HEADER_SIZE + length;
    return frames;
  }

  private List<Frame> decodeFrames(byte[] payload, int frameCount, int firstTime)
      throws FormatException {
    ByteBuffer buf = ByteBuffer.wrap(payload);
    List<Frame> frames = new ArrayList<>(frameCount);
    try {
      for (int i = 0; i < frameCount; i++) {
        int time = firstTime + VarInts.read(buf);
        int type = VarInts.read(buf);
        int len = VarInts.read(buf);
        if (len > buf.remaining() || len > StreamFormat.MAX_FRAME_PAYLOAD) {
          throw new FormatException("Frame length out of bounds");
        }
        byte[] body = new byte[len];
        buf.get(body);
        frames.add(new Frame(time, type, body));
      }
    } catch (IllegalArgumentException e) {
      throw new FormatException("Invalid frame in block at offset " + position, e);
    }
    if (buf.hasRemaining()) {
      throw new FormatException("Trailing bytes in block at offset " + position);
    }
    return frames;
  }

  /** Reads every remaining frame in strict mode. Intended for tests and small recordings. */
  public List<Frame> readAll() throws IOException {
    List<Frame> all = new ArrayList<>();
    List<Frame> block;
    while ((block = nextBlock()) != null) {
      all.addAll(block);
    }
    return all;
  }

  @Override
  public void close() throws IOException {
    in.close();
  }

  /** Result of a tolerant scan. */
  public record ScanResult(
      long validLength, long frameCount, int lastTimeMs, boolean truncated, String problem) {}

  /**
   * Scans a stream, stopping at the first invalid block instead of throwing. The header itself must
   * be valid.
   */
  public static ScanResult scan(InputStream in) throws IOException {
    try (FrameStreamReader reader = open(in)) {
      long frames = 0;
      int lastTime = 0;
      while (true) {
        long before = reader.position();
        List<Frame> block;
        try {
          block = reader.nextBlock();
        } catch (FormatException | EOFException e) {
          return new ScanResult(before, frames, lastTime, true, e.getMessage());
        }
        if (block == null) {
          return new ScanResult(before, frames, lastTime, false, null);
        }
        frames += block.size();
        if (!block.isEmpty()) {
          lastTime = block.get(block.size() - 1).timeMs();
        }
      }
    }
  }
}
