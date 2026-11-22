/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.util;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Crash-safe file replacement: write a sibling temp file, fsync it, then rename over the target.
 */
public final class AtomicFiles {
  private AtomicFiles() {}

  public static void writeString(Path target, String content) throws IOException {
    write(target, content.getBytes(StandardCharsets.UTF_8));
  }

  public static void write(Path target, byte[] content) throws IOException {
    Path temp = target.resolveSibling(target.getFileName() + ".tmp");
    try (FileChannel channel =
        FileChannel.open(
            temp,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE)) {
      java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(content);
      while (buf.hasRemaining()) {
        channel.write(buf);
      }
      channel.force(true);
    }
    move(temp, target);
  }

  /** Renames atomically where the filesystem supports it, otherwise falls back to a replace. */
  public static void move(Path source, Path target) throws IOException {
    try {
      Files.move(
          source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
