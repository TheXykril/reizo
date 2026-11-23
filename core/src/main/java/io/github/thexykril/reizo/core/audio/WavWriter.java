/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.audio;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes 16-bit PCM WAV files. */
public final class WavWriter {
  private WavWriter() {}

  /** Writes interleaved samples in [-1, 1]. */
  public static void write(Path file, float[] samples, int channels, int sampleRate)
      throws IOException {
    int dataBytes = samples.length * 2;
    ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
    header.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    header.putInt(36 + dataBytes);
    header.put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    header.putInt(16).putShort((short) 1).putShort((short) channels).putInt(sampleRate);
    header.putInt(sampleRate * channels * 2).putShort((short) (channels * 2)).putShort((short) 16);
    header.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    header.putInt(dataBytes);
    ByteBuffer data = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN);
    for (float s : samples) {
      data.putShort((short) Math.round(Math.max(-1, Math.min(1, s)) * 32767));
    }
    try (OutputStream out = Files.newOutputStream(file)) {
      out.write(header.array());
      out.write(data.array());
    }
  }
}
