/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.project;

import io.github.thexykril.reizo.core.util.AtomicFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Projects on disk: one JSON file per project, {@code <id>.reizoproj}, written atomically so a
 * crash never leaves a half-written project. Saving often (autosave) is cheap.
 */
public final class ProjectStore {
  public static final String EXTENSION = "reizoproj";

  private final Path root;

  public ProjectStore(Path root) {
    this.root = root;
  }

  public Path root() {
    return root;
  }

  public Path pathOf(String projectId) {
    if (projectId == null || !projectId.matches("[A-Za-z0-9_-]{1,64}")) {
      throw new IllegalArgumentException("bad project id: " + projectId);
    }
    return root.resolve(projectId + "." + EXTENSION);
  }

  public void save(Project project, long now) throws IOException {
    project.modifiedAtEpochMs = now;
    Files.createDirectories(root);
    AtomicFiles.writeString(pathOf(project.id), project.toJson());
  }

  public Project load(String projectId) throws IOException {
    return Project.fromJson(Files.readString(pathOf(projectId), StandardCharsets.UTF_8));
  }

  /** The newest project for a recording (by file name or recording id), or null. */
  public Project findForRecording(String recordingFile, String recordingId) throws IOException {
    for (Project p : list()) {
      if (recordingFile.equals(p.recordingFile)
          || (recordingId != null && recordingId.equals(p.recordingId))) {
        return p;
      }
    }
    return null;
  }

  /** Readable projects, most recently modified first. */
  public List<Project> list() throws IOException {
    if (!Files.isDirectory(root)) {
      return List.of();
    }
    List<Project> out = new ArrayList<>();
    try (Stream<Path> files = Files.list(root)) {
      for (Path p : (Iterable<Path>) files::iterator) {
        if (!p.getFileName().toString().endsWith("." + EXTENSION)) {
          continue;
        }
        try {
          out.add(Project.fromJson(Files.readString(p, StandardCharsets.UTF_8)));
        } catch (IOException | IllegalArgumentException e) {
          // Skip unreadable or newer-schema projects; they stay on disk untouched.
        }
      }
    }
    out.sort(Comparator.comparingLong((Project p) -> p.modifiedAtEpochMs).reversed());
    return out;
  }

  public void delete(String projectId) throws IOException {
    Files.deleteIfExists(pathOf(projectId));
  }
}
