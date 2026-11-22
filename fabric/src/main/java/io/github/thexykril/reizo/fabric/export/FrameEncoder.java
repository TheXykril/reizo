/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.export;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Feeds raw RGBA frames to an FFmpeg process on a writer thread. The queue is small, so a slow
 * encoder slows the renderer down instead of filling memory.
 */
final class FrameEncoder implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Export");
  private static final byte[] END = new byte[0];

  private final Process process;
  private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(6);
  private final Thread writer;
  private final StringBuilder errors = new StringBuilder();
  private volatile IOException failure;
  private int framesWritten;

  FrameEncoder(List<String> command, Path output) throws IOException {
    LOGGER.info("Starting FFmpeg: {}", String.join(" ", command));
    java.nio.file.Files.createDirectories(output.toAbsolutePath().getParent());
    process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
    Thread err = new Thread(() -> drain(process.getErrorStream()), "Reizo-FFmpeg-err");
    err.setDaemon(true);
    err.start();
    writer = new Thread(this::write, "Reizo-FFmpeg-in");
    writer.setDaemon(true);
    writer.start();
  }

  /** Queues one frame; blocks while the queue is full. */
  void submit(byte[] rgba) throws IOException, InterruptedException {
    if (failure != null) {
      throw failure;
    }
    while (!queue.offer(rgba, 100, TimeUnit.MILLISECONDS)) {
      if (failure != null) {
        throw failure;
      }
      if (!process.isAlive()) {
        throw new IOException("FFmpeg exited: " + errors());
      }
    }
  }

  boolean hasRoom() {
    return queue.remainingCapacity() > 0;
  }

  int framesWritten() {
    return framesWritten;
  }

  /** Finishes the file and waits for FFmpeg. */
  void finish() throws IOException, InterruptedException {
    queue.put(END);
    writer.join();
    if (failure != null) {
      throw failure;
    }
    if (!process.waitFor(120, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new IOException("FFmpeg did not finish");
    }
    if (process.exitValue() != 0) {
      throw new IOException("FFmpeg failed (" + process.exitValue() + "): " + errors());
    }
  }

  /** Stops without a usable file (cancel or error). */
  @Override
  public void close() {
    writer.interrupt();
    process.destroy();
  }

  private void write() {
    try (OutputStream in = process.getOutputStream()) {
      while (true) {
        byte[] frame = queue.take();
        if (frame == END) {
          break;
        }
        in.write(frame);
        framesWritten++;
      }
    } catch (IOException e) {
      failure = new IOException("Writing to FFmpeg failed: " + errors(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void drain(InputStream err) {
    try (err) {
      byte[] buf = new byte[4096];
      int n;
      while ((n = err.read(buf)) > 0) {
        synchronized (errors) {
          if (errors.length() < 8192) {
            errors.append(new String(buf, 0, n, StandardCharsets.UTF_8));
          }
        }
      }
    } catch (IOException ignored) {
      // Process ended.
    }
  }

  private String errors() {
    synchronized (errors) {
      return errors.toString().strip();
    }
  }
}
