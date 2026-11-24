/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.export;

import io.github.thexykril.reizo.core.audio.AudioMixer;
import io.github.thexykril.reizo.core.audio.MusicMix;
import io.github.thexykril.reizo.core.export.FfmpegCommand;
import io.github.thexykril.reizo.fabric.RecorderClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

/**
 * Music files for the editor (ADR 0018): picks one with the system's file dialog or from the music
 * folder, and reads each file's length and waveform once, off the game thread.
 */
public final class MusicFiles {
  /** Waveform buckets per file. */
  public static final int PEAKS = 512;

  /** A file's length and waveform. A file FFmpeg cannot read has length 0. */
  public record Info(long lengthMs, float[] peaks) {}

  private static final Info LOADING = new Info(-1, new float[0]);

  private static final Map<String, Info> INFO = new ConcurrentHashMap<>();

  private MusicFiles() {}

  /** The file's info, or null while it is read (the first call starts reading it). */
  public static Info info(String path) {
    Info i = INFO.computeIfAbsent(path, MusicFiles::startReading);
    return i == LOADING ? null : i;
  }

  private static Info startReading(String path) {
    Thread t =
        new Thread(
            () -> {
              Optional<String> ffmpeg = ffmpeg();
              float[] sound =
                  ffmpeg.isPresent() && Files.isRegularFile(Path.of(path))
                      ? MusicPass.decode(ffmpeg.get(), Path.of(path))
                      : null;
              INFO.put(
                  path,
                  sound == null
                      ? new Info(0, new float[0])
                      : new Info(
                          sound.length / 2 * 1000L / AudioMixer.SAMPLE_RATE,
                          MusicMix.peaks(sound, PEAKS)));
            },
            "Reizo music info");
    t.setDaemon(true);
    t.start();
    return LOADING;
  }

  /** FFmpeg as configured or on the PATH. */
  public static Optional<String> ffmpeg() {
    return FfmpegCommand.locate(RecorderClient.get().config().ffmpegPath, System.getenv("PATH"));
  }

  /** Sound file endings the picker and the music folder list. */
  private static final String[] ENDINGS = {
    ".mp3", ".ogg", ".wav", ".flac", ".m4a", ".opus", ".aac"
  };

  /** The music folder, next to the recordings folder; created on first use. */
  public static Path folder() {
    Path dir = RecorderClient.get().storage().root().resolveSibling("music");
    try {
      Files.createDirectories(dir);
    } catch (java.io.IOException ignored) {
      // listed as empty
    }
    return dir;
  }

  /** Opens the music folder in the system's file manager. */
  public static void openFolder() {
    Util.getPlatform().openPath(folder());
  }

  /** Sound files in the music folder, by name. */
  public static List<Path> library() {
    try (var files = Files.list(folder())) {
      return files.filter(MusicFiles::isSound).sorted().toList();
    } catch (java.io.IOException e) {
      return List.of();
    }
  }

  private static boolean isSound(Path p) {
    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
    for (String e : ENDINGS) {
      if (n.endsWith(e)) {
        return Files.isRegularFile(p);
      }
    }
    return false;
  }

  /**
   * Opens the system's file dialog for a sound file on its own thread, and hands the chosen path to
   * {@code onPick} on the game thread, or {@code onNoDialog} when this system has no dialog.
   * Nothing happens when the dialog is cancelled.
   */
  public static void pick(String title, Consumer<String> onPick, Runnable onNoDialog) {
    Thread t =
        new Thread(
            () -> {
              Optional<String> chosen;
              try {
                chosen = tinyfd(title);
              } catch (Throwable noTinyfd) {
                // Minecraft 26.3 no longer ships LWJGL's tinyfd: ask the system's own tools.
                chosen = systemDialog(title);
              }
              Minecraft mc = Minecraft.getInstance();
              if (chosen == null) {
                mc.execute(onNoDialog);
              } else {
                chosen
                    .filter(c -> !c.isBlank())
                    .ifPresent(c -> mc.execute(() -> onPick.accept(c.strip())));
              }
            },
            "Reizo music picker");
    t.setDaemon(true);
    t.start();
  }

  /** LWJGL's tinyfd through reflection, since not every Minecraft version ships it. */
  private static Optional<String> tinyfd(String title) throws ReflectiveOperationException {
    Class<?> dialogs = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
    Class<?> stackClass = Class.forName("org.lwjgl.system.MemoryStack");
    Class<?> pointers = Class.forName("org.lwjgl.PointerBuffer");
    try (AutoCloseable stack = (AutoCloseable) stackClass.getMethod("stackPush").invoke(null)) {
      Object filters = stackClass.getMethod("mallocPointer", int.class).invoke(stack, ENDINGS.length);
      for (String e : ENDINGS) {
        Object text = stackClass.getMethod("UTF8", CharSequence.class).invoke(stack, "*" + e);
        pointers.getMethod("put", java.nio.ByteBuffer.class).invoke(filters, text);
      }
      pointers.getMethod("flip").invoke(filters);
      Object chosen =
          dialogs
              .getMethod(
                  "tinyfd_openFileDialog",
                  CharSequence.class,
                  CharSequence.class,
                  pointers,
                  CharSequence.class,
                  boolean.class)
              .invoke(null, title, null, filters, "Audio", false);
      return Optional.ofNullable((String) chosen);
    } catch (ReflectiveOperationException e) {
      throw e;
    } catch (Exception e) {
      throw new ReflectiveOperationException(e);
    }
  }

  /**
   * The system's file dialog through its own tools: zenity or kdialog on Linux, AppleScript on
   * macOS, PowerShell on Windows. Empty when cancelled, null when none of them runs.
   */
  private static Optional<String> systemDialog(String title) {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    List<List<String>> tries;
    if (os.contains("win")) {
      tries =
          List.of(
              List.of(
                  "powershell",
                  "-NoProfile",
                  "-Command",
                  "Add-Type -AssemblyName System.Windows.Forms;"
                      + "$d=New-Object System.Windows.Forms.OpenFileDialog;"
                      + "$d.Filter='Audio|*.mp3;*.ogg;*.wav;*.flac;*.m4a;*.opus;*.aac';"
                      + "if($d.ShowDialog() -eq 'OK'){$d.FileName}"));
    } else if (os.contains("mac")) {
      tries =
          List.of(
              List.of(
                  "osascript",
                  "-e",
                  "POSIX path of (choose file with prompt \"" + title.replace("\"", "") + "\")"));
    } else {
      tries =
          List.of(
              List.of(
                  "zenity",
                  "--file-selection",
                  "--title=" + title,
                  "--file-filter=Audio | *.mp3 *.ogg *.wav *.flac *.m4a *.opus *.aac"),
              List.of(
                  "kdialog",
                  "--getopenfilename",
                  System.getProperty("user.home"),
                  "*.mp3 *.ogg *.wav *.flac *.m4a *.opus *.aac"));
    }
    for (List<String> command : tries) {
      try {
        Process p = new ProcessBuilder(command).redirectErrorStream(false).start();
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        p.waitFor();
        return Optional.of(out);
      } catch (java.io.IOException e) {
        // this tool is not installed; try the next
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return Optional.empty();
      }
    }
    return null;
  }
}
