/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.format;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.CRC32;

/**
 * Appends frames to a stream file in CRC-checked blocks.
 *
 * <p>Frames are buffered in memory and written as one block when the buffer reaches {@code
 * targetBlockBytes} or when {@link #flushBlock()} is called. A crash can therefore lose at most the
 * frames of the current unflushed block; every block already written is independently verifiable.
 * The caller decides how often to flush (the recording session flushes on a timer).
 *
 * <p>Not thread-safe: a single writer thread owns an instance.
 */
public final class FrameStreamWriter implements Closeable {
  private final FileChannel channel;
  private final int targetBlockBytes;
  private final ByteArrayOutputStream blockBuffer;
  private final CRC32 crc = new CRC32();

  private int blockFrameCount;
  private int blockFirstTime = -1;
  private int blockLastTime;
  private int lastTimeMs;
  private long framesWritten;
  private boolean closed;

  private FrameStreamWriter(FileChannel channel, int targetBlockBytes) {
    this.channel = channel;
    this.targetBlockBytes = targetBlockBytes;
    this.blockBuffer = new ByteArrayOutputStream(Math.min(targetBlockBytes + 1024, 1 << 20));
  }

  /** Creates a new stream file. Fails if the file already exists. */
  public static FrameStreamWriter create(Path file, int targetBlockBytes) throws IOException {
    if (targetBlockBytes <= 0 || targetBlockBytes > StreamFormat.MAX_BLOCK_PAYLOAD / 2) {
      throw new IllegalArgumentException("Bad block size " + targetBlockBytes);
    }
    FileChannel channel =
        FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    try {
      ByteBuffer header = ByteBuffer.allocate(StreamFormat.HEADER_SIZE);
      header.put(StreamFormat.MAGIC).putInt(StreamFormat.VERSION).flip();
      writeFully(channel, header);
      return new FrameStreamWriter(channel, targetBlockBytes);
    } catch (IOException | RuntimeException e) {
      channel.close();
      Files.deleteIfExists(file);
      throw e;
    }
  }

  /**
   * Opens an existing, already validated stream for appending at {@code validLength}. Anything
   * after that offset (a torn block) is truncated first. Used by crash recovery.
   */
  public static FrameStreamWriter appendTo(Path file, long validLength, int targetBlockBytes)
      throws IOException {
    FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE);
    channel.truncate(validLength);
    channel.position(validLength);
    return new FrameStreamWriter(channel, targetBlockBytes);
  }

  /**
   * Buffers a frame. Frame times must be non-decreasing; the recorder guarantees this by assigning
   * timestamps under the same lock that orders frames.
   */
  public void write(Frame frame) throws IOException {
    ensureOpen();
    if (frame.timeMs() < lastTimeMs) {
      throw new IllegalArgumentException(
          "Frame time went backwards: " + frame.timeMs() + " < " + lastTimeMs);
    }
    if (frame.payload().length > StreamFormat.MAX_FRAME_PAYLOAD) {
      throw new IllegalArgumentException("Frame payload too large: " + frame.payload().length);
    }
    if (blockFirstTime < 0) {
      blockFirstTime = frame.timeMs();
    }
    VarInts.write(blockBuffer, frame.timeMs() - blockFirstTime);
    VarInts.write(blockBuffer, frame.typeId());
    VarInts.write(blockBuffer, frame.payload().length);
    blockBuffer.write(frame.payload());
    blockFrameCount++;
    blockLastTime = frame.timeMs();
    lastTimeMs = frame.timeMs();
    framesWritten++;
    if (blockBuffer.size() >= targetBlockBytes) {
      flushBlock();
    }
  }

  /** Writes the buffered frames as one block. Does nothing if the buffer is empty. */
  public void flushBlock() throws IOException {
    ensureOpen();
    if (blockFrameCount == 0) {
      return;
    }
    byte[] payload = blockBuffer.toByteArray();
    crc.reset();
    crc.update(payload);
    ByteBuffer header = ByteBuffer.allocate(StreamFormat.BLOCK_HEADER_SIZE);
    header
        .putInt(StreamFormat.BLOCK_MAGIC)
        .putInt(payload.length)
        .putInt((int) crc.getValue())
        .putInt(blockFrameCount)
        .putInt(blockFirstTime)
        .putInt(blockLastTime)
        .flip();
    writeFully(channel, new ByteBuffer[] {header, ByteBuffer.wrap(payload)});
    blockBuffer.reset();
    blockFrameCount = 0;
    blockFirstTime = -1;
  }

  /** Flushes the block and forces data to the storage device. */
  public void sync() throws IOException {
    flushBlock();
    channel.force(false);
  }

  public long framesWritten() {
    return framesWritten;
  }

  public int lastTimeMs() {
    return lastTimeMs;
  }

  /** Bytes currently on disk (excludes the in-memory block). */
  public long bytesOnDisk() throws IOException {
    return channel.size();
  }

  private void ensureOpen() throws IOException {
    if (closed) {
      throw new IOException("Writer is closed");
    }
  }

  /** Flushes, syncs and closes. Safe to call twice. */
  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    try {
      sync();
    } finally {
      closed = true;
      channel.close();
    }
  }

  private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
    while (buffer.hasRemaining()) {
      channel.write(buffer);
    }
  }

  private static void writeFully(FileChannel channel, ByteBuffer[] buffers) throws IOException {
    long remaining = 0;
    for (ByteBuffer b : buffers) {
      remaining += b.remaining();
    }
    while (remaining > 0) {
      remaining -= channel.write(buffers);
    }
  }
}
