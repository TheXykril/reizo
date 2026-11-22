/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.format;

/**
 * Constants of the frame stream file ({@code stream.bin}). Full specification: {@code
 * docs/FORMAT.md}. Any change here must update that document and the format tests.
 *
 * <pre>
 * file   := header block*
 * header := MAGIC(8 bytes) version(int32 BE)
 * block  := BLOCK_MAGIC(int32) payloadLength(int32) crc32(int32) frameCount(int32)
 *           firstTimeMs(int32) lastTimeMs(int32) payload(payloadLength bytes)
 * payload:= frame*
 * frame  := varint(timeMs - firstTimeMs) varint(typeId) varint(length) bytes(length)
 * </pre>
 *
 * The CRC covers the payload only. A block is valid only if its header is complete, the magic
 * matches, the payload is fully present and the CRC matches. Anything after the first invalid block
 * is treated as garbage from an interrupted write and is discarded by recovery.
 */
public final class StreamFormat {
  /** File magic: ASCII {@code "REISTRM"} followed by a zero byte. */
  public static final byte[] MAGIC = {'R', 'E', 'I', 'S', 'T', 'R', 'M', 0};

  /** Stream format version. Bump on any incompatible layout change. */
  public static final int VERSION = 1;

  public static final int HEADER_SIZE = MAGIC.length + Integer.BYTES;

  /** Block magic: ASCII {@code "RTBK"}. */
  public static final int BLOCK_MAGIC = 0x5254424B;

  public static final int BLOCK_HEADER_SIZE = 6 * Integer.BYTES;

  /** Hard limit so a corrupt length can never make a reader allocate gigabytes. */
  public static final int MAX_BLOCK_PAYLOAD = 16 * 1024 * 1024;

  /** Hard limit for a single frame payload. Vanilla packets are far below this. */
  public static final int MAX_FRAME_PAYLOAD = 8 * 1024 * 1024;

  private StreamFormat() {}
}
