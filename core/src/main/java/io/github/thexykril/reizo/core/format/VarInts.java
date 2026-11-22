/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.format;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/** Unsigned LEB128 variable-length ints (same layout as Minecraft's VarInt for values >= 0). */
public final class VarInts {
  private VarInts() {}

  public static void write(ByteArrayOutputStream out, int value) {
    if (value < 0) {
      throw new IllegalArgumentException("Negative varint " + value);
    }
    while ((value & ~0x7F) != 0) {
      out.write((value & 0x7F) | 0x80);
      value >>>= 7;
    }
    out.write(value);
  }

  /**
   * Reads a varint.
   *
   * @throws FormatException if the value is longer than 5 bytes or the buffer ends early
   */
  public static int read(ByteBuffer in) throws FormatException {
    int value = 0;
    for (int shift = 0; shift < 35; shift += 7) {
      if (!in.hasRemaining()) {
        throw new FormatException("Truncated varint");
      }
      byte b = in.get();
      value |= (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        if (value < 0) {
          throw new FormatException("Varint out of range");
        }
        return value;
      }
    }
    throw new FormatException("Varint too long");
  }
}
