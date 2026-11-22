/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.storage;

import io.github.thexykril.reizo.core.format.FormatException;
import io.github.thexykril.reizo.core.format.FrameStreamReader;
import io.github.thexykril.reizo.core.format.RecordingMetadata;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A finalized recording file: a ZIP container with {@value #META_ENTRY} and {@value #STREAM_ENTRY}.
 * Later format versions add entries (snapshots, audio, thumbnails); readers ignore entries they do
 * not know.
 */
public final class RecordingArchive implements Closeable {
  /** File extension of finalized recordings, without the dot. */
  public static final String EXTENSION = "reizo";

  public static final String META_ENTRY = "meta.json";
  public static final String STREAM_ENTRY = "stream.bin";

  private final Path path;
  private final ZipFile zip;
  private final RecordingMetadata metadata;

  private RecordingArchive(Path path, ZipFile zip, RecordingMetadata metadata) {
    this.path = path;
    this.zip = zip;
    this.metadata = metadata;
  }

  public static RecordingArchive open(Path path) throws IOException {
    ZipFile zip = new ZipFile(path.toFile());
    try {
      ZipEntry metaEntry = zip.getEntry(META_ENTRY);
      if (metaEntry == null || zip.getEntry(STREAM_ENTRY) == null) {
        throw new FormatException(path.getFileName() + " is not a recording (missing entries)");
      }
      String json;
      try (InputStream in = zip.getInputStream(metaEntry)) {
        json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      RecordingMetadata meta;
      try {
        meta = RecordingMetadata.fromJson(json);
      } catch (IllegalArgumentException e) {
        throw new FormatException(e.getMessage(), e);
      }
      return new RecordingArchive(path, zip, meta);
    } catch (IOException | RuntimeException e) {
      zip.close();
      throw e;
    }
  }

  public Path path() {
    return path;
  }

  /** A copy of the metadata; changing it does not modify the file. */
  public RecordingMetadata metadata() {
    return metadata.copy();
  }

  /** Opens the frame stream for strict sequential reading. The caller closes the reader. */
  public FrameStreamReader openStream() throws IOException {
    return FrameStreamReader.open(zip.getInputStream(zip.getEntry(STREAM_ENTRY)));
  }

  @Override
  public void close() throws IOException {
    zip.close();
  }
}
