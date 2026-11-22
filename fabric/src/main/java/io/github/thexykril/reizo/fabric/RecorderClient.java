/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric;

import io.github.thexykril.reizo.core.format.FrameType;
import io.github.thexykril.reizo.core.format.LocalPlayerState;
import io.github.thexykril.reizo.core.format.RecordingMetadata;
import io.github.thexykril.reizo.core.recording.CaptureController;
import io.github.thexykril.reizo.core.recording.RecorderSettings;
import io.github.thexykril.reizo.core.recording.RecordingSession;
import io.github.thexykril.reizo.core.storage.RecordingStorage;
import io.github.thexykril.reizo.fabric.capture.LocalPlayerSampler;
import io.github.thexykril.reizo.fabric.capture.PacketTap;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.ui.DebugCommands;
import io.github.thexykril.reizo.fabric.ui.RecorderKeys;
import io.github.thexykril.reizo.fabric.ui.RecordingIndicator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint and owner of all recorder state. There is exactly one instance; mixins reach
 * the capture controller through {@link PacketTap} only.
 */
public final class RecorderClient implements ClientModInitializer {
  public static final String MOD_ID = "reizo";
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo");

  private static RecorderClient instance;

  private ClientConfig config;
  private RecordingStorage storage;
  private CaptureController capture;
  private final LocalPlayerSampler sampler = new LocalPlayerSampler();

  public static RecorderClient get() {
    return instance;
  }

  @Override
  public void onInitializeClient() {
    instance = this;
    FabricLoader loader = FabricLoader.getInstance();
    config = ClientConfig.load(loader.getConfigDir().resolve(MOD_ID + ".json"));
    Path recordings = loader.getGameDir().resolve(config.recordingsDirectory);
    storage = new RecordingStorage(recordings);
    capture = new CaptureController(storage, RecorderSettings.DEFAULTS, System::nanoTime);
    PacketTap.install(capture);

    RecorderKeys.register();
    io.github.thexykril.reizo.fabric.ui.MenuButtons.register();
    RecordingIndicator.register(this);
    io.github.thexykril.reizo.fabric.ui.ReplayHud.register();
    DebugCommands.register(this);

    ClientConfigurationConnectionEvents.START.register(
        (handler, client) -> {
          if (config.autoRecordOnJoin && session() == null && !ReplayPlayback.isActive()) {
            client.execute(this::startRecording);
          }
        });
    ClientConfigurationConnectionEvents.DISCONNECT.register(
        (h, client) -> {
          ReplayPlayback.onConfigurationDisconnect();
          onDisconnect();
        });
    ClientPlayConnectionEvents.DISCONNECT.register(
        (h, client) -> {
          ReplayPlayback.onPlayDisconnect(h);
          onDisconnect();
        });
    ClientPlayConnectionEvents.INIT.register((h, client) -> ReplayPlayback.onPlayInit(h));
    ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    ClientLifecycleEvents.CLIENT_STARTED.register(client -> recoverAbandoned());
    ClientLifecycleEvents.CLIENT_STOPPING.register(client -> stopOnShutdown());

    LOGGER.info("Recorder ready; recordings go to {}", recordings);
  }

  // ---- public API used by keys, HUD and commands ----

  public RecordingSession session() {
    return capture.session();
  }

  public ClientConfig config() {
    return config;
  }

  /** Writes the current settings to the config file. */
  public void saveConfig() {
    config.save(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"));
  }

  public RecordingStorage storage() {
    return storage;
  }

  /** Toggles recording on or off. Client thread only. */
  public void toggleRecording() {
    if (session() != null) {
      stopRecording();
    } else {
      startRecording();
    }
  }

  public void startRecording() {
    Minecraft mc = Minecraft.getInstance();
    if (ReplayPlayback.isActive()) {
      return; // never record a replay
    }
    if (!capture.canRecord()) {
      toast("reizo.toast.not_connected", null);
      return;
    }
    RecordingMetadata meta =
        RecordingMetadata.create(recordingName(mc), System.currentTimeMillis());
    fillMetadata(mc, meta);
    try {
      capture.start(meta);
      sampler.reset();
      LOGGER.info("Recording started: {} (complete from join: {})", meta.id, meta.completeFromJoin);
      toast(
          "reizo.toast.started",
          meta.completeFromJoin ? null : Component.translatable("reizo.toast.started_partial"));
    } catch (IOException | IllegalStateException e) {
      LOGGER.error("Could not start recording", e);
      toast("reizo.toast.start_failed", Component.literal(String.valueOf(e.getMessage())));
    }
  }

  public void stopRecording() {
    CompletableFuture<Path> result = capture.stop();
    if (result == null) {
      return;
    }
    LOGGER.info("Stopping recording");
    onClientThread(() -> toast("reizo.toast.saving", null));
    result.whenComplete(
        (path, error) -> {
          if (error != null) {
            LOGGER.error("Recording failed; it will be recovered on next start", error);
            onClientThread(() -> toast("reizo.toast.save_failed", null));
          } else {
            LOGGER.info("Recording saved to {}", path);
            onClientThread(
                () -> toast("reizo.toast.saved", Component.literal(path.getFileName().toString())));
          }
        });
  }

  public void togglePause() {
    RecordingSession s = session();
    if (s == null) {
      return;
    }
    if (s.state() == RecordingSession.State.PAUSED) {
      s.resume();
    } else {
      s.pause();
    }
  }

  public void addMarker(String label) {
    RecordingSession s = session();
    if (s != null) {
      s.addMarker(label);
      toast("reizo.toast.marker", Component.literal(label));
    }
  }

  // ---- lifecycle ----

  private void onClientTick(Minecraft mc) {
    ReplayPlayback.tick(mc);
    RecorderKeys.handle(this);
    RecordingSession s = session();
    if (s == null || mc.player == null || s.state() != RecordingSession.State.RECORDING) {
      return;
    }
    LocalPlayerState state = sampler.sample(mc.player);
    if (state != null) {
      s.submit(FrameType.LOCAL_PLAYER, state.encode());
    }
  }

  private void onDisconnect() {
    // May run on the network thread, and Minecraft drops queued client tasks while disconnecting,
    // so stop directly; stopRecording() is thread-safe.
    capture.onDisconnect();
    stopRecording();
  }

  private void stopOnShutdown() {
    CompletableFuture<Path> result = capture.stop();
    if (result != null) {
      try {
        // Give the writer a moment to package the file; if it cannot, recovery handles it.
        result.get(10, java.util.concurrent.TimeUnit.SECONDS);
      } catch (Exception e) {
        LOGGER.warn("Recording not finalized at shutdown; it will be recovered next start", e);
      }
    }
  }

  private void recoverAbandoned() {
    Thread t =
        new Thread(
            () -> {
              try {
                List<Path> abandoned = storage.findAbandoned();
                int recovered = 0;
                for (Path dir : abandoned) {
                  RecordingStorage.RecoveryResult r = storage.recover(dir);
                  if (r.recovered()) {
                    recovered++;
                    LOGGER.info("Recovered recording {} ({} frames)", r.archive(), r.frames());
                  } else {
                    LOGGER.warn("Could not recover {}: {}", dir, r.problem());
                  }
                }
                if (recovered > 0) {
                  int count = recovered;
                  Minecraft.getInstance()
                      .execute(
                          () ->
                              toast(
                                  "reizo.toast.recovered",
                                  Component.literal(String.valueOf(count))));
                }
              } catch (IOException e) {
                LOGGER.error("Recording recovery failed", e);
              }
            },
            "Recorder-Recovery");
    t.setDaemon(true);
    t.start();
  }

  // ---- helpers ----

  private String recordingName(Minecraft mc) {
    if (mc.isLocalServer() && mc.getSingleplayerServer() != null) {
      return mc.getSingleplayerServer().getWorldData().getLevelName();
    }
    ServerData server = mc.getCurrentServer();
    return server != null ? server.name : "recording";
  }

  private void fillMetadata(Minecraft mc, RecordingMetadata meta) {
    meta.minecraftVersion = SharedConstants.getCurrentVersion().name();
    meta.protocolVersion = SharedConstants.getProtocolVersion();
    meta.loader = "fabric";
    meta.modVersion =
        FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    meta.localPlayerName = mc.getUser().getName();
    meta.localPlayerUuid = String.valueOf(mc.getUser().getProfileId());
    ServerData server = mc.getCurrentServer();
    if (mc.isLocalServer()) {
      meta.serverType = "singleplayer";
      if (config.storeServerName && mc.getSingleplayerServer() != null) {
        meta.serverName = mc.getSingleplayerServer().getWorldData().getLevelName();
      }
    } else {
      meta.serverType = server != null && server.isRealm() ? "realms" : "multiplayer";
      if (config.storeServerName && server != null) {
        meta.serverName = server.ip;
      }
    }
  }

  /** Runs now if on the client thread, otherwise queues it. Queued tasks may be dropped. */
  private static void onClientThread(Runnable task) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.isSameThread()) {
      task.run();
    } else {
      mc.execute(task);
    }
  }

  private static void toast(String titleKey, Component body) {
    Minecraft mc = Minecraft.getInstance();
    SystemToast.addOrUpdate(
        mc.getToastManager(),
        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
        Component.translatable(titleKey),
        body);
  }
}
