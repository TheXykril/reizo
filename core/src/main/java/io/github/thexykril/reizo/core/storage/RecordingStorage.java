/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.storage;

import io.github.thexykril.reizo.core.format.FrameStreamReader;
import io.github.thexykril.reizo.core.format.RecordingMetadata;
import io.github.thexykril.reizo.core.util.AtomicFiles;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * On-disk layout of recordings.
 *
 * <pre>
 * root/
 *   working/&lt;id&gt;/        a recording in progress (or abandoned by a crash)
 *     stream.bin            frame stream, append-only
 *     meta.json             metadata, replaced atomically
 *     owner.lock            held with an OS file lock while a live session owns the directory
 *   &lt;name&gt;.reizo         finalized recordings (ZIP)
 * </pre>
 *
 * A working directory whose lock can be acquired has no live owner: the game crashed or was killed
 * while recording, and {@link #recover(Path)} can salvage it.
 */
public final class RecordingStorage {
  public static final String WORKING_DIR = "working";
  public static final String STREAM_FILE = "stream.bin";
  public static final String META_FILE = "meta.json";
  public static final String LOCK_FILE = "owner.lock";

  private static final DateTimeFormatter FILE_DATE =
      DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);

  private final Path root;
  private final ZoneId zone;

  public RecordingStorage(Path root) {
    this(root, ZoneId.systemDefault());
  }

  public RecordingStorage(Path root, ZoneId zone) {
    this.root = root;
    this.zone = zone;
  }

  public Path root() {
    return root;
  }

  /** A locked working directory owned by one live recording session. */
  public static final class WorkingDirectory implements AutoCloseable {
    private final Path dir;
    private final FileChannel lockChannel;
    private final FileLock lock;

    private WorkingDirectory(Path dir, FileChannel lockChannel, FileLock lock) {
      this.dir = dir;
      this.lockChannel = lockChannel;
      this.lock = lock;
    }

    public Path dir() {
      return dir;
    }

    public Path streamFile() {
      return dir.resolve(STREAM_FILE);
    }

    public Path metaFile() {
      return dir.resolve(META_FILE);
    }

    public void writeMetadata(RecordingMetadata meta) throws IOException {
      AtomicFiles.writeString(metaFile(), meta.toJson());
    }

    /** Releases the ownership lock. The directory stays on disk. */
    @Override
    public void close() throws IOException {
      try {
        if (lock.isValid()) {
          lock.release();
        }
      } finally {
        lockChannel.close();
      }
    }
  }

  /** Creates and locks a new working directory and writes the initial metadata. */
  public WorkingDirectory createWorking(RecordingMetadata meta) throws IOException {
    Path dir = root.resolve(WORKING_DIR).resolve(meta.id);
    Files.createDirectories(dir.getParent());
    Files.createDirectory(dir);
    WorkingDirectory working = lockDirectory(dir);
    if (working == null) {
      throw new IOException("Could not lock new working directory " + dir);
    }
    try {
      working.writeMetadata(meta);
    } catch (IOException e) {
      working.close();
      throw e;
    }
    return working;
  }

  private static WorkingDirectory lockDirectory(Path dir) throws IOException {
    FileChannel channel =
        FileChannel.open(
            dir.resolve(LOCK_FILE), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    try {
      FileLock lock = channel.tryLock();
      if (lock == null) {
        channel.close();
        return null;
      }
      return new WorkingDirectory(dir, channel, lock);
    } catch (OverlappingFileLockException e) {
      // Held by this JVM: a live session in this process owns it.
      channel.close();
      return null;
    }
  }

  /**
   * Packages a working directory into a finalized archive and deletes the directory. The caller
   * must have closed the stream writer and released the {@link WorkingDirectory}.
   *
   * @return path of the new archive
   */
  public Path finalizeRecording(Path workingDir) throws IOException {
    RecordingMetadata meta = readMetadata(workingDir);
    Path target = uniqueArchivePath(meta);
    Path temp = target.resolveSibling(target.getFileName() + ".tmp");
    try (OutputStream file = Files.newOutputStream(temp);
        ZipOutputStream zip = new ZipOutputStream(file, StandardCharsets.UTF_8)) {
      zip.putNextEntry(new ZipEntry(RecordingArchive.META_ENTRY));
      zip.write(meta.toJson().getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry(RecordingArchive.STREAM_ENTRY));
      Files.copy(workingDir.resolve(STREAM_FILE), zip);
      zip.closeEntry();
    } catch (IOException | RuntimeException e) {
      Files.deleteIfExists(temp);
      throw e;
    }
    AtomicFiles.move(temp, target);
    deleteRecursively(workingDir);
    return target;
  }

  /** Working directories with no live owner, oldest first. */
  public List<Path> findAbandoned() throws IOException {
    Path workingRoot = root.resolve(WORKING_DIR);
    if (!Files.isDirectory(workingRoot)) {
      return List.of();
    }
    List<Path> abandoned = new ArrayList<>();
    try (DirectoryStream<Path> dirs = Files.newDirectoryStream(workingRoot, Files::isDirectory)) {
      for (Path dir : dirs) {
        WorkingDirectory probe = lockDirectory(dir);
        if (probe != null) {
          probe.close();
          abandoned.add(dir);
        }
      }
    }
    abandoned.sort(Comparator.comparing(Path::toString));
    return abandoned;
  }

  /** Outcome of recovering one abandoned recording. */
  public record RecoveryResult(Path workingDir, Path archive, long frames, String problem) {
    public boolean recovered() {
      return archive != null;
    }
  }

  /**
   * Salvages an abandoned working directory: cuts the stream after the last valid block, marks the
   * metadata {@link RecordingMetadata.State#RECOVERED} and finalizes it. A directory without a
   * usable stream or metadata is left in place and reported as not recovered, never deleted.
   */
  public RecoveryResult recover(Path workingDir) throws IOException {
    Path stream = workingDir.resolve(STREAM_FILE);
    if (!Files.isRegularFile(stream) || !Files.isRegularFile(workingDir.resolve(META_FILE))) {
      return new RecoveryResult(workingDir, null, 0, "missing stream or metadata");
    }
    RecordingMetadata meta;
    try {
      meta = readMetadata(workingDir);
    } catch (IllegalArgumentException e) {
      return new RecoveryResult(workingDir, null, 0, "unreadable metadata: " + e.getMessage());
    }
    FrameStreamReader.ScanResult scan;
    try (InputStream in = Files.newInputStream(stream)) {
      scan = FrameStreamReader.scan(in);
    } catch (IOException e) {
      return new RecoveryResult(workingDir, null, 0, "unreadable stream: " + e.getMessage());
    }
    if (scan.truncated()) {
      try (FileChannel channel = FileChannel.open(stream, StandardOpenOption.WRITE)) {
        channel.truncate(scan.validLength());
        channel.force(true);
      }
    }
    if (meta.state == RecordingMetadata.State.RECORDING) {
      meta.state = RecordingMetadata.State.RECOVERED;
    }
    meta.frameCount = scan.frameCount();
    meta.durationMs = Math.max(meta.durationMs, scan.lastTimeMs());
    AtomicFiles.writeString(workingDir.resolve(META_FILE), meta.toJson());
    Path archive = finalizeRecording(workingDir);
    return new RecoveryResult(workingDir, archive, scan.frameCount(), scan.problem());
  }

  /** Finalized recordings, newest file first. Unreadable files are skipped. */
  public List<Path> listArchives() throws IOException {
    if (!Files.isDirectory(root)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(root)) {
      return files
          .filter(p -> p.getFileName().toString().endsWith("." + RecordingArchive.EXTENSION))
          .filter(Files::isRegularFile)
          .sorted(Comparator.comparing(RecordingStorage::lastModified).reversed())
          .toList();
    }
  }

  /** A finalized recording as the replay browser lists it. */
  public record Entry(Path path, RecordingMetadata metadata, long sizeBytes) {}

  /** Readable finalized recordings with their metadata, newest file first. */
  public List<Entry> listRecordings() throws IOException {
    List<Entry> out = new java.util.ArrayList<>();
    for (Path p : listArchives()) {
      try (RecordingArchive archive = RecordingArchive.open(p)) {
        out.add(new Entry(p, archive.metadata(), Files.size(p)));
      } catch (IOException e) {
        // Not a readable recording (partial copy, other file): leave it out of the list.
      }
    }
    return out;
  }

  /**
   * Changes the display name stored in a recording. The file keeps its name, so references to it
   * stay valid; the archive is rewritten to a temporary file and moved over the original.
   */
  public void rename(Path archivePath, String newName) throws IOException {
    String name = newName == null ? "" : newName.strip();
    if (name.isEmpty()) {
      throw new IllegalArgumentException("name must not be empty");
    }
    checkInRoot(archivePath);
    Path temp = archivePath.resolveSibling(archivePath.getFileName() + ".tmp");
    try (java.util.zip.ZipFile in = new java.util.zip.ZipFile(archivePath.toFile());
        OutputStream file = Files.newOutputStream(temp);
        ZipOutputStream out = new ZipOutputStream(file, StandardCharsets.UTF_8)) {
      ZipEntry metaEntry = in.getEntry(RecordingArchive.META_ENTRY);
      if (metaEntry == null) {
        throw new io.github.thexykril.reizo.core.format.FormatException(
            archivePath.getFileName() + " is not a recording");
      }
      RecordingMetadata meta;
      try (java.io.InputStream metaIn = in.getInputStream(metaEntry)) {
        meta =
            RecordingMetadata.fromJson(new String(metaIn.readAllBytes(), StandardCharsets.UTF_8));
      }
      meta.name = name;
      // Same entry order as finalizeRecording: metadata first, so it is cheap to read.
      out.putNextEntry(new ZipEntry(RecordingArchive.META_ENTRY));
      out.write(meta.toJson().getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
      var entries = in.entries();
      while (entries.hasMoreElements()) {
        ZipEntry e = entries.nextElement();
        if (e.getName().equals(RecordingArchive.META_ENTRY)) {
          continue;
        }
        out.putNextEntry(new ZipEntry(e.getName()));
        try (java.io.InputStream data = in.getInputStream(e)) {
          data.transferTo(out);
        }
        out.closeEntry();
      }
    } catch (IOException | RuntimeException e) {
      Files.deleteIfExists(temp);
      throw e;
    }
    AtomicFiles.move(temp, archivePath);
  }

  /** Deletes a finalized recording. Only files directly in this storage's folder are accepted. */
  public void delete(Path archivePath) throws IOException {
    checkInRoot(archivePath);
    Files.delete(archivePath);
  }

  private void checkInRoot(Path archivePath) {
    Path parent = archivePath.toAbsolutePath().normalize().getParent();
    if (parent == null
        || !parent.equals(root.toAbsolutePath().normalize())
        || !archivePath.getFileName().toString().endsWith("." + RecordingArchive.EXTENSION)) {
      throw new IllegalArgumentException(archivePath + " is not a recording in " + root);
    }
  }

  private static long lastModified(Path p) {
    try {
      return Files.getLastModifiedTime(p).toMillis();
    } catch (IOException e) {
      return 0;
    }
  }

  private static RecordingMetadata readMetadata(Path workingDir) throws IOException {
    return RecordingMetadata.fromJson(
        Files.readString(workingDir.resolve(META_FILE), StandardCharsets.UTF_8));
  }

  private Path uniqueArchivePath(RecordingMetadata meta) throws IOException {
    Files.createDirectories(root);
    String date = FILE_DATE.format(Instant.ofEpochMilli(meta.startedAtEpochMs).atZone(zone));
    String base = sanitize(meta.name) + "_" + date;
    Path candidate = root.resolve(base + "." + RecordingArchive.EXTENSION);
    for (int i = 2; Files.exists(candidate); i++) {
      candidate = root.resolve(base + "_" + i + "." + RecordingArchive.EXTENSION);
    }
    return candidate;
  }

  /** Keeps file names portable across Linux, Windows and macOS. */
  static String sanitize(String name) {
    String cleaned = name.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
    if (cleaned.isEmpty() || cleaned.chars().allMatch(c -> c == '.')) {
      cleaned = "recording";
    }
    return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
  }

  private static void deleteRecursively(Path dir) throws IOException {
    try (Stream<Path> walk = Files.walk(dir)) {
      for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(p);
      }
    }
  }
}
