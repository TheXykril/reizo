/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.recording;

import io.github.thexykril.reizo.core.format.Frame;
import io.github.thexykril.reizo.core.format.ProtocolPhase;
import io.github.thexykril.reizo.core.format.RecordingMetadata;
import io.github.thexykril.reizo.core.storage.RecordingStorage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

/**
 * Connects the game's packet stream to recording sessions. Minecraft-independent: the game adapter
 * only reports decoded clientbound packets and connection lifecycle.
 *
 * <p>The controller always keeps a small cache of the current connection's configuration-phase
 * packets (registries, tags, enabled features) and its first play packet (the login packet that
 * sets up the level). These are sent once per connection, before most users press record, and a
 * recording is unreadable without them. When a recording starts mid-session the cache is written as
 * the recording's prelude.
 *
 * <p>Thread-safe. {@link #onPacket} runs on the network thread; everything else usually runs on the
 * client thread.
 */
public final class CaptureController {
  /** Upper bound for the configuration cache; real servers send well under 2 MB. */
  static final long MAX_CACHE_BYTES = 16L * 1024 * 1024;

  private final RecordingStorage storage;
  private final RecorderSettings settings;
  private final LongSupplier nanoClock;

  // All guarded by "this".
  private final List<Frame> connectionCache = new ArrayList<>();
  private long cacheBytes;
  private boolean cacheOverflowed;
  private ProtocolPhase lastPhase;
  private long playPacketsThisConnection;
  private RecordingSession session;

  public CaptureController(
      RecordingStorage storage, RecorderSettings settings, LongSupplier nanoClock) {
    this.storage = storage;
    this.settings = settings;
    this.nanoClock = nanoClock;
  }

  /** Called for every clientbound configuration or play packet the client decodes. */
  public void onPacket(ProtocolPhase phase, byte[] packetBytes) {
    RecordingSession target;
    synchronized (this) {
      if (phase == ProtocolPhase.CONFIGURATION && lastPhase != ProtocolPhase.CONFIGURATION) {
        // A new configuration phase: a new connection, or a proxy moving us to another server.
        // The previous world's cached state no longer applies.
        clearCache();
        playPacketsThisConnection = 0;
      }
      lastPhase = phase;
      if (phase == ProtocolPhase.CONFIGURATION) {
        cache(Frame.packet(0, phase, packetBytes));
      } else {
        if (playPacketsThisConnection == 0) {
          cache(Frame.packet(0, phase, packetBytes)); // the level login packet
        }
        playPacketsThisConnection++;
      }
      target = session;
    }
    if (target != null) {
      target.submitPacket(phase, packetBytes);
    }
  }

  private void cache(Frame frame) {
    if (cacheOverflowed) {
      return;
    }
    cacheBytes += frame.payload().length;
    if (cacheBytes > MAX_CACHE_BYTES) {
      clearCache();
      cacheOverflowed = true;
      return;
    }
    connectionCache.add(frame);
  }

  private void clearCache() {
    connectionCache.clear();
    cacheBytes = 0;
    cacheOverflowed = false;
  }

  /** Called when the client leaves a server or world. */
  public synchronized void onDisconnect() {
    clearCache();
    lastPhase = null;
    playPacketsThisConnection = 0;
  }

  /** True while connected and past the login phase (something can be recorded). */
  public synchronized boolean canRecord() {
    return lastPhase != null;
  }

  /**
   * Starts a recording. The prelude is the cached connection data, so even a mid-session start
   * produces a stream that begins with the configuration the client received.
   *
   * <p>{@link RecordingMetadata#completeFromJoin} is set to whether nothing world-related was
   * missed: true when no play packet has arrived yet besides the cached login packet.
   *
   * @throws IllegalStateException if a recording is already running
   */
  public synchronized RecordingSession start(RecordingMetadata meta) throws IOException {
    return start(meta, List::of);
  }

  /**
   * Starts a recording like {@link #start(RecordingMetadata)}. When the start is mid-session, the
   * {@code snapshot} is asked for play packets that rebuild what the client already has (chunks,
   * entities, time, ...); they follow the cached prelude at time 0. It is not called for a start
   * that is complete from join, and its packets do not make the recording complete.
   */
  public synchronized RecordingSession start(
      RecordingMetadata meta, java.util.function.Supplier<List<byte[]>> snapshot)
      throws IOException {
    if (session != null) {
      throw new IllegalStateException("A recording is already running");
    }
    meta.completeFromJoin = !cacheOverflowed && playPacketsThisConnection <= 1;
    List<Frame> prelude = new ArrayList<>(cacheOverflowed ? List.of() : connectionCache);
    if (!meta.completeFromJoin && !cacheOverflowed) {
      for (byte[] packet : snapshot.get()) {
        prelude.add(Frame.packet(0, ProtocolPhase.PLAY, packet));
      }
      meta.startSnapshot = true;
    }
    session = RecordingSession.start(storage, meta, settings, prelude, nanoClock);
    return session;
  }

  /** The running session, or null. */
  public synchronized RecordingSession session() {
    return session;
  }

  /** Stops the running session; returns its result, or null if none was running. */
  public CompletableFuture<Path> stop() {
    RecordingSession s;
    synchronized (this) {
      s = session;
      session = null;
    }
    return s == null ? null : s.stop();
  }

  /** Number of cached frames (for diagnostics and tests). */
  public synchronized int cachedFrames() {
    return connectionCache.size();
  }
}
