/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.compat;

import io.github.thexykril.reizo.core.recording.RecordingSession;
import io.github.thexykril.reizo.core.storage.RecordingArchive;
import io.github.thexykril.reizo.core.storage.RecordingStorage;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.ui.ReplayBrowserScreen;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reizo in OneClient's replay UI. PolyPlus (OneClient's in-game client, GPL-3.0) draws a "Record"
 * card on the pause screen and a "Replays" tab in the lobby from a fixed list of replay mods, each
 * a {@code ReplayProvider}. Reizo adds itself to that list (through {@code
 * PolyPlusReplayProvidersMixin}) as a provider built at run time, so Reizo needs no PolyPlus classes
 * to compile and nothing changes when PolyPlus is not installed.
 */
public final class PolyPlusReplays {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/PolyPlus");
  private static final String PACKAGE = "org.polyfrost.polyplus.compat.replay.";

  /** Reizo's pause menu buttons, which PolyPlus's Record card replaces. */
  private static final List<String> CONTROL_KEYS =
      List.of(
          "reizo.menu.start_recording",
          "reizo.menu.stop_recording",
          "reizo.menu.pause_recording",
          "reizo.menu.resume_recording");

  private static Object provider;
  private static boolean failed;
  private static Constructor<?> entryConstructor;
  private static Constructor<?> stateConstructor;
  private static Method entryPath;

  private PolyPlusReplays() {}

  /** PolyPlus's provider list with Reizo added, or the list unchanged when that fails. */
  public static synchronized List<Object> withReizo(List<Object> providers) {
    if (providers == lastGiven && lastAll != null) {
      return lastAll;
    }
    Object reizo = provider(providers);
    if (reizo == null || providers.contains(reizo)) {
      return providers;
    }
    List<Object> all = new ArrayList<>(providers);
    all.add(reizo);
    lastGiven = providers;
    lastAll = List.copyOf(all);
    return lastAll;
  }

  // PolyPlus's list is made once, so the extended list is too.
  private static List<Object> lastGiven;
  private static List<Object> lastAll;

  private static synchronized Object provider(List<Object> providers) {
    if (provider != null || failed) {
      return provider;
    }
    try {
      ClassLoader loader =
          providers.isEmpty()
              ? PolyPlusReplays.class.getClassLoader()
              : providers.get(0).getClass().getClassLoader();
      Class<?> type = Class.forName(PACKAGE + "ReplayProvider", false, loader);
      Class<?> entry = Class.forName(PACKAGE + "ReplayEntry", false, loader);
      Class<?> state = Class.forName(PACKAGE + "RecordingState", false, loader);
      Class<?> function = Class.forName("kotlin.jvm.functions.Function0", false, loader);
      entryConstructor =
          entry.getConstructor(
              type,
              Path.class,
              String.class,
              String.class,
              Long.class,
              long.class,
              long.class,
              function,
              boolean.class,
              String.class);
      stateConstructor = state.getConstructor(boolean.class, Long.class);
      entryPath = entry.getMethod("getPath");
      provider = Proxy.newProxyInstance(loader, new Class<?>[] {type}, new Handler());
    } catch (ReflectiveOperationException | LinkageError e) {
      failed = true;
      LOGGER.warn("Could not add Reizo to OneClient's replay list", e);
    }
    return provider;
  }

  private static final class Handler implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      try {
        return call(proxy, method, args);
      } catch (RuntimeException | ReflectiveOperationException e) {
        LOGGER.warn("OneClient replay call {} failed", method.getName(), e);
        return fallback(method);
      }
    }

    private Object call(Object proxy, Method method, Object[] args)
        throws ReflectiveOperationException {
      return switch (method.getName()) {
        case "getId" -> "reizo";
        case "getName" -> "Reizo";
        case "getAvailable" -> true;
        case "getInReplay" -> ReplayPlayback.isActive();
        case "list" -> list(proxy);
        case "watch" -> {
          watch((Path) entryPath.invoke(args[0]));
          yield null;
        }
        case "openFolder" -> {
          onClient(() -> Util.getPlatform().openPath(RecorderClient.get().storage().root()));
          yield null;
        }
        case "openNativeBrowser" -> {
          onClient(
              () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.setScreen(ReplayBrowserScreen.create(mc.screen));
              });
          yield null;
        }
        case "getRecording" -> recording();
        case "getCanStart" ->
            !ReplayPlayback.isActive()
                && Minecraft.getInstance().level != null
                && RecorderClient.get().session() == null;
        case "getCanPause" -> RecorderClient.get().session() != null;
        case "getCanDiscard" -> false;
        case "getControlKeyPrefixes" -> CONTROL_KEYS;
        case "getFinishLabel" -> "Finish and save";
        case "start" -> {
          onClient(() -> RecorderClient.get().startRecording());
          yield null;
        }
        case "setPaused" -> {
          boolean paused = (Boolean) args[0];
          onClient(
              () -> {
                RecordingSession s = RecorderClient.get().session();
                if (s != null && (s.state() == RecordingSession.State.PAUSED) != paused) {
                  RecorderClient.get().togglePause();
                }
              });
          yield null;
        }
        case "finish" -> {
          onClient(() -> RecorderClient.get().stopRecording());
          yield null;
        }
        case "discard", "detachPauseOverlay" -> null;
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        case "toString" -> "Reizo replays";
        default -> fallback(method);
      };
    }

    private static Object fallback(Method method) {
      Class<?> r = method.getReturnType();
      if (r == boolean.class) {
        return false;
      }
      if (r == List.class) {
        return List.of();
      }
      if (r == int.class) {
        return 0;
      }
      if (r == long.class) {
        return 0L;
      }
      if (r == float.class) {
        return 0f;
      }
      if (r == double.class) {
        return 0d;
      }
      return null;
    }
  }

  private static Object recording() throws ReflectiveOperationException {
    RecordingSession s = RecorderClient.get().session();
    if (s == null || ReplayPlayback.isActive()) {
      return null;
    }
    return stateConstructor.newInstance(
        s.state() == RecordingSession.State.PAUSED, s.elapsedMillis());
  }

  /** Reizo's recordings, newest first (PolyPlus calls this off the game thread). */
  private static List<Object> list(Object proxy) throws ReflectiveOperationException {
    List<RecordingStorage.Entry> entries;
    try {
      entries = RecorderClient.get().storage().listRecordings();
    } catch (java.io.IOException e) {
      return List.of();
    }
    int protocol = SharedConstants.getProtocolVersion();
    List<Object> out = new ArrayList<>();
    for (RecordingStorage.Entry e : entries) {
      var m = e.metadata();
      boolean playable = m.protocolVersion == protocol;
      long modified;
      try {
        modified = Files.getLastModifiedTime(e.path()).toMillis();
      } catch (java.io.IOException ex) {
        modified = m.startedAtEpochMs;
      }
      String name =
          m.name != null && !m.name.isBlank()
              ? m.name
              : e.path().getFileName().toString().replaceFirst("\\.reizo$", "");
      java.nio.file.Path archive = e.path();
      kotlin.jvm.functions.Function0<byte[]> icon = () -> RecordingArchive.readThumbnail(archive);
      out.add(
          entryConstructor.newInstance(
              proxy,
              e.path(),
              name,
              m.serverName,
              m.durationMs > 0 ? Long.valueOf(m.durationMs) : null,
              modified,
              e.sizeBytes(),
              icon,
              playable,
              playable
                  ? null
                  : "Recorded in " + m.minecraftVersion + ", can't be played on this version"));
    }
    out.sort(
        (a, b) -> {
          try {
            long ma = (long) a.getClass().getMethod("getModified").invoke(a);
            long mb = (long) b.getClass().getMethod("getModified").invoke(b);
            return Long.compare(mb, ma);
          } catch (ReflectiveOperationException ex) {
            return 0;
          }
        });
    return out;
  }

  private static void watch(Path path) {
    onClient(
        () -> {
          Minecraft mc = Minecraft.getInstance();
          try {
            ReplayPlayback.start(mc, path);
          } catch (java.io.IOException | RuntimeException e) {
            LOGGER.warn("Could not play {}", path, e);
          }
        });
  }

  private static void onClient(Runnable r) {
    Minecraft.getInstance().execute(r);
  }
}
