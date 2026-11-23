/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.export;

import io.github.thexykril.reizo.core.audio.AudioClip;
import io.github.thexykril.reizo.core.audio.SoundCue;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects the sounds that start during an export, with the camera at that moment, so the sound
 * track can be rendered offline ({@link io.github.thexykril.reizo.core.audio.AudioMixer}). Sounds
 * are followed while they play: once per video frame, positional ones record where they and the
 * camera are, and sounds that change over time (minecarts, elytra, music fading) their volume and
 * pitch. Looping sounds repeat until the game stops them. Looping sounds that started while the
 * export was preparing (an entity's sound after the jump to the start) play from the first frame.
 */
final class SoundCapture {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Export");

  /** How long a sound that plays once is followed, in ms; longer ones keep their last state. */
  static final double FOLLOW_ONE_SHOT_MS = 30_000;

  private static final class Followed {
    final int index;
    final boolean loop;
    final boolean sample;
    final List<SoundCue.Point> path = new ArrayList<>();

    Followed(int index, boolean loop, boolean sample) {
      this.index = index;
      this.loop = loop;
      this.sample = sample;
    }
  }

  private final List<SoundCue> cues = new ArrayList<>();
  private final float soundVolume;
  private final float musicVolume;

  /** Levels from the export settings, 0 to 1, on top of the game's own volume settings. */
  SoundCapture(float soundVolume, float musicVolume) {
    this.soundVolume = soundVolume;
    this.musicVolume = musicVolume;
  }

  private final Map<SoundInstance, Followed> playing = new IdentityHashMap<>();
  private final Set<SoundInstance> beforeStart = Collections.newSetFromMap(new IdentityHashMap<>());

  /** Records a sound that starts at {@code videoTimeMs}. Client thread. */
  void onPlay(Minecraft mc, SoundInstance sound, double videoTimeMs) {
    if ((sound.getSource() == SoundSource.MUSIC ? musicVolume : soundVolume) <= 0) {
      return; // turned off in the export settings: not decoded or mixed
    }
    if (sound.resolve(mc.getSoundManager()) == null) {
      return;
    }
    Sound resolved = sound.getSound();
    if (resolved == null
        || resolved == net.minecraft.client.sounds.SoundManager.EMPTY_SOUND
        || resolved == net.minecraft.client.sounds.SoundManager.INTENTIONALLY_EMPTY_SOUND) {
      return;
    }
    SoundCue.Point p = point(mc, sound, resolved, videoTimeMs);
    boolean positional =
        !sound.isRelative() && sound.getAttenuation() != SoundInstance.Attenuation.NONE;
    cues.add(
        new SoundCue(
            videoTimeMs,
            resolved.getPath().toString(),
            p.gain(),
            p.pitch(),
            positional,
            p.x(),
            p.y(),
            p.z(),
            p.range(),
            p.listenerX(),
            p.listenerY(),
            p.listenerZ(),
            p.listenerYaw()));
    // A loop with a delay is started again by the game each time, so each repeat is its own cue.
    boolean loop = sound.isLooping() && sound.getDelay() <= 0;
    boolean sample = positional || sound instanceof TickableSoundInstance;
    Followed f = new Followed(cues.size() - 1, loop, sample);
    playing.put(sound, f);
  }

  /** A sound that started while the export was getting ready. Client thread. */
  void onPlayBeforeStart(SoundInstance sound) {
    if (sound.isLooping() && sound.getDelay() <= 0) {
      beforeStart.add(sound);
    }
  }

  /** Adds the looping sounds that started before the first frame and still play. */
  void startFirstFrame(Minecraft mc) {
    for (SoundInstance sound : beforeStart) {
      if (mc.getSoundManager().isActive(sound) && !stopped(sound)) {
        onPlay(mc, sound, 0);
      }
    }
    beforeStart.clear();
  }

  /** The game stopped a sound. */
  void onStop(SoundInstance sound, double videoTimeMs) {
    beforeStart.remove(sound);
    Followed f = playing.remove(sound);
    if (f != null) {
      finish(f, videoTimeMs);
    }
  }

  /** Follows the playing sounds after a frame at {@code videoTimeMs} was captured. */
  void onFrame(Minecraft mc, double videoTimeMs) {
    Iterator<Map.Entry<SoundInstance, Followed>> it = playing.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<SoundInstance, Followed> e = it.next();
      SoundInstance sound = e.getKey();
      Followed f = e.getValue();
      if (stopped(sound)) {
        it.remove();
        finish(f, videoTimeMs);
        continue;
      }
      if (!f.loop
          && !(sound instanceof TickableSoundInstance)
          && videoTimeMs - cues.get(f.index).timeMs() > FOLLOW_ONE_SHOT_MS) {
        it.remove();
        finish(f, Double.POSITIVE_INFINITY);
        continue;
      }
      if (f.sample && sound.getSound() != null) {
        f.path.add(point(mc, sound, sound.getSound(), videoTimeMs));
      }
    }
  }

  /** Ends following every sound still playing; call once the last frame is captured. */
  void finishAll() {
    playing.values().forEach(f -> finish(f, Double.POSITIVE_INFINITY));
    playing.clear();
    beforeStart.clear();
  }

  private void finish(Followed f, double endMs) {
    SoundCue cue = cues.get(f.index);
    if (f.loop || !f.path.isEmpty() || !Double.isInfinite(endMs)) {
      cues.set(f.index, cue.followed(f.loop, endMs, f.path));
    }
  }

  private static boolean stopped(SoundInstance sound) {
    return sound instanceof TickableSoundInstance t && t.isStopped();
  }

  private SoundCue.Point point(
      Minecraft mc, SoundInstance sound, Sound resolved, double videoTimeMs) {
    float volume = sound.getVolume();
    float gain =
        Math.max(0, Math.min(1, volume))
            * Math.max(0, Math.min(1, mc.options.getFinalSoundSourceVolume(sound.getSource())))
            * (sound.getSource() == SoundSource.MUSIC ? musicVolume : soundVolume);
    float pitch = Math.max(0.5f, Math.min(2f, sound.getPitch()));
    float range = Math.max(volume, 1f) * resolved.getAttenuationDistance();
    Camera camera = mc.gameRenderer.getMainCamera();
    Vec3 at = camera.position();
    return new SoundCue.Point(
        videoTimeMs,
        gain,
        pitch,
        sound.getX(),
        sound.getY(),
        sound.getZ(),
        range,
        at.x,
        at.y,
        at.z,
        io.github.thexykril.reizo.fabric.compat.Compat.cameraYaw(camera));
  }

  List<SoundCue> cues() {
    return cues;
  }

  /**
   * Decodes every clip the cues use, as far as the video can play it (music is long); looping clips
   * whole. Unreadable files are left out (logged once).
   */
  Map<String, AudioClip> decodeClips(Minecraft mc, double durationMs) {
    Map<String, Double> needed = new HashMap<>();
    for (SoundCue cue : cues) {
      // At most twice as fast (pitch 2), so a clip never plays more than twice the time left.
      double seconds =
          cue.loop() ? Double.POSITIVE_INFINITY : (durationMs - cue.timeMs()) * 2 / 1000 + 0.1;
      needed.merge(cue.clip(), seconds, Math::max);
    }
    Map<String, AudioClip> clips = new HashMap<>();
    needed.forEach((clip, seconds) -> clips.put(clip, decode(mc, clip, seconds)));
    return clips;
  }

  private static AudioClip decode(Minecraft mc, String path, double maxSeconds) {
    try (InputStream in = mc.getResourceManager().open(Identifier.parse(path));
        JOrbisAudioStream ogg = new JOrbisAudioStream(in)) {
      var format = ogg.getFormat();
      double limit = maxSeconds * format.getSampleRate() * format.getChannels();
      FloatArrayList samples = new FloatArrayList();
      while (samples.size() < limit && ogg.readChunk(samples::add)) {
        // reads until the end, or as far as the video needs
      }
      return AudioClip.fromInterleaved(
          samples.toFloatArray(), format.getChannels(), (int) format.getSampleRate());
    } catch (IOException | RuntimeException e) {
      LOGGER.warn("Could not decode {} for the export's sound: {}", path, e.toString());
      return null;
    }
  }
}
