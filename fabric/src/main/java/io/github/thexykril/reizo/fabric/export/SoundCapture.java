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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * track can be rendered offline ({@link io.github.thexykril.reizo.core.audio.AudioMixer}). Music
 * and sounds that loop or follow an entity (minecarts, elytra) are left out: they would need to be
 * followed over time.
 */
final class SoundCapture {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Export");

  private final List<SoundCue> cues = new ArrayList<>();

  /** Records a sound that starts at {@code videoTimeMs}. Client thread. */
  void onPlay(Minecraft mc, SoundInstance sound, double videoTimeMs) {
    if (sound.getSource() == SoundSource.MUSIC
        || sound.isLooping()
        || sound instanceof TickableSoundInstance
        || sound.resolve(mc.getSoundManager()) == null) {
      return;
    }
    Sound resolved = sound.getSound();
    if (resolved == null
        || resolved == net.minecraft.client.sounds.SoundManager.EMPTY_SOUND
        || resolved == net.minecraft.client.sounds.SoundManager.INTENTIONALLY_EMPTY_SOUND) {
      return;
    }
    float volume = sound.getVolume();
    float gain =
        Math.max(0, Math.min(1, volume))
            * Math.max(0, Math.min(1, mc.options.getFinalSoundSourceVolume(sound.getSource())));
    float pitch = Math.max(0.5f, Math.min(2f, sound.getPitch()));
    boolean positional =
        !sound.isRelative() && sound.getAttenuation() != SoundInstance.Attenuation.NONE;
    float range = Math.max(volume, 1f) * resolved.getAttenuationDistance();
    Camera camera = mc.gameRenderer.getMainCamera();
    Vec3 at = camera.position();
    cues.add(
        new SoundCue(
            videoTimeMs,
            resolved.getPath().toString(),
            gain,
            pitch,
            positional,
            sound.getX(),
            sound.getY(),
            sound.getZ(),
            range,
            at.x,
            at.y,
            at.z,
            camera.yRot()));
  }

  List<SoundCue> cues() {
    return cues;
  }

  /** Decodes every clip the cues use. Unreadable files are left out (logged once). */
  Map<String, AudioClip> decodeClips(Minecraft mc) {
    Map<String, AudioClip> clips = new HashMap<>();
    for (SoundCue cue : cues) {
      if (clips.containsKey(cue.clip())) {
        continue;
      }
      clips.put(cue.clip(), decode(mc, cue.clip()));
    }
    return clips;
  }

  private static AudioClip decode(Minecraft mc, String path) {
    try (InputStream in = mc.getResourceManager().open(Identifier.parse(path));
        JOrbisAudioStream ogg = new JOrbisAudioStream(in)) {
      FloatArrayList samples = new FloatArrayList();
      while (ogg.readChunk(samples::add)) {
        // reads until the end
      }
      var format = ogg.getFormat();
      return AudioClip.fromInterleaved(
          samples.toFloatArray(), format.getChannels(), (int) format.getSampleRate());
    } catch (IOException | RuntimeException e) {
      LOGGER.warn("Could not decode {} for the export's sound: {}", path, e.toString());
      return null;
    }
  }
}
