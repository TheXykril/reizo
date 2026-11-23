/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.thexykril.reizo.core.camera.CameraKeyframe;
import io.github.thexykril.reizo.core.camera.CameraPath;
import io.github.thexykril.reizo.core.effects.EffectTrack;
import io.github.thexykril.reizo.core.effects.Grade;
import io.github.thexykril.reizo.core.effects.PropertyTracks;
import java.util.ArrayList;
import java.util.List;

/**
 * An editing project: references one recording (never copies it) and holds the edits made on top of
 * it. Saved as JSON; unknown fields are ignored and missing ones take defaults, so adding a field
 * stays backward compatible. Schema changes that are not additive bump {@link #schema}.
 */
public final class Project {
  public static final int CURRENT_SCHEMA = 1;
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  public int schema = CURRENT_SCHEMA;
  public String id;
  public String name;

  /** File name of the recording in the recordings folder. */
  public String recordingFile;

  /** The recording's metadata id, to find it again if the file was renamed. */
  public String recordingId;

  public long createdAtEpochMs;
  public long modifiedAtEpochMs;

  /** The camera path, empty when the project has none. */
  public List<Keyframe> cameraKeyframes = new ArrayList<>();

  public String interpolation = CameraPath.Interpolation.CATMULL_ROM.name();
  public String easing = CameraPath.Easing.LINEAR.name();

  /** Trim: the part of the recording that is exported, in ms. -1 = from start / to end. */
  public long inMs = -1;

  public long outMs = -1;

  /** Export settings. */
  public Render render = new Render();

  /** Effect keyframes (image grade over time); empty when the project has none. */
  public List<Effect> effects = new ArrayList<>();

  /** Keyframed game and mod settings (see {@link PropertyTracks}); empty when none. */
  public List<Setting> settings = new ArrayList<>();

  /** JSON form of one setting's track. */
  public static final class Setting {
    public String id;
    public String kind = PropertyTracks.Kind.NUMBER.name();
    public List<SettingKey> keys = new ArrayList<>();
  }

  /** JSON form of a setting keyframe. */
  public static final class SettingKey {
    public long t;
    public double v;
  }

  /** JSON form of an effect keyframe. */
  public static final class Effect {
    public long t;
    public float exposure;
    public float contrast = 1;
    public float saturation = 1;
    public float temperature;
    public float vignette;
    public float letterbox;
    public float bloom;
    public float dof;
    public float focus = Grade.DEFAULT_FOCUS;
  }

  /** JSON form of a camera keyframe (records are not Gson-friendly on all versions). */
  public static final class Keyframe {
    public long t;
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    public float roll;
    public float fov = CameraKeyframe.DEFAULT_FOV;
  }

  /** Export settings for the renderer. */
  public static final class Render {
    public int width = 1920;
    public int height = 1080;
    public int fps = 60;

    /** Constant rate factor for software H.264 (lower = better quality, bigger file). */
    public int crf = 18;

    public String codec = "libx264";

    /** Include the game's sounds (rendered offline from the sounds that play). */
    public boolean audio = true;

    /** Level of the game's sounds other than music in the export, percent (0 to 100). */
    public int soundVolume = 100;

    /** Level of the music in the export, percent (0 to 100). */
    public int musicVolume = 100;

    /** Motion blur: renders averaged per frame (180° shutter); 1 is off. */
    public int motionBlur = 1;

    /** Output file name; relative to the exports folder unless absolute. */
    public String output;
  }

  public static Project create(String id, String name, String recordingFile, long now) {
    Project p = new Project();
    p.id = id;
    p.name = name;
    p.recordingFile = recordingFile;
    p.createdAtEpochMs = now;
    p.modifiedAtEpochMs = now;
    return p;
  }

  /** The camera path described by this project. */
  public CameraPath cameraPath() {
    CameraPath path = new CameraPath();
    path.setInterpolation(parse(CameraPath.Interpolation.class, interpolation));
    path.setEasing(parse(CameraPath.Easing.class, easing));
    for (Keyframe k : cameraKeyframes) {
      path.put(new CameraKeyframe(k.t, k.x, k.y, k.z, k.yaw, k.pitch, k.roll, k.fov));
    }
    return path;
  }

  /** Replaces the stored camera path. */
  public void setCameraPath(CameraPath path) {
    interpolation = path.interpolation().name();
    easing = path.easing().name();
    cameraKeyframes = new ArrayList<>();
    for (CameraKeyframe c : path.keyframes()) {
      Keyframe k = new Keyframe();
      k.t = c.timeMs();
      k.x = c.x();
      k.y = c.y();
      k.z = c.z();
      k.yaw = c.yaw();
      k.pitch = c.pitch();
      k.roll = c.roll();
      k.fov = c.fov();
      cameraKeyframes.add(k);
    }
  }

  /** The effect track described by this project. */
  public EffectTrack effects() {
    EffectTrack track = new EffectTrack();
    for (Effect e : effects) {
      track.put(
          e.t,
          new Grade(
              e.exposure,
              e.contrast,
              e.saturation,
              e.temperature,
              e.vignette,
              e.letterbox,
              e.bloom,
              e.dof,
              e.focus));
    }
    return track;
  }

  /** Replaces the stored effect keyframes. */
  public void setEffects(EffectTrack track) {
    effects = new ArrayList<>();
    for (EffectTrack.Keyframe k : track.keyframes()) {
      Effect e = new Effect();
      Grade g = k.grade();
      e.t = k.timeMs();
      e.exposure = g.exposure();
      e.contrast = g.contrast();
      e.saturation = g.saturation();
      e.temperature = g.temperature();
      e.vignette = g.vignette();
      e.letterbox = g.letterbox();
      e.bloom = g.bloom();
      e.dof = g.dof();
      e.focus = g.focus();
      effects.add(e);
    }
  }

  /** The setting tracks described by this project. */
  public PropertyTracks settings() {
    PropertyTracks tracks = new PropertyTracks();
    for (Setting s : settings) {
      if (s == null || s.id == null || s.keys == null) {
        continue;
      }
      PropertyTracks.Kind kind = parse(PropertyTracks.Kind.class, s.kind);
      for (SettingKey k : s.keys) {
        if (k != null && Double.isFinite(k.v)) {
          tracks.put(s.id, kind, k.t, k.v);
        }
      }
    }
    return tracks;
  }

  /** Replaces the stored setting tracks. */
  public void setSettings(PropertyTracks tracks) {
    settings = new ArrayList<>();
    for (String id : tracks.properties()) {
      Setting s = new Setting();
      s.id = id;
      s.kind = tracks.kind(id).name();
      for (PropertyTracks.Keyframe k : tracks.keyframes(id)) {
        SettingKey key = new SettingKey();
        key.t = k.timeMs();
        key.v = k.value();
        s.keys.add(key);
      }
      settings.add(s);
    }
  }

  public String toJson() {
    return GSON.toJson(this);
  }

  /**
   * @throws IllegalArgumentException if the JSON is not a project or is from a newer schema
   */
  public static Project fromJson(String json) {
    Project p;
    try {
      p = GSON.fromJson(json, Project.class);
    } catch (JsonParseException e) {
      throw new IllegalArgumentException("not a project: " + e.getMessage(), e);
    }
    if (p == null || p.recordingFile == null) {
      throw new IllegalArgumentException("not a project");
    }
    if (p.schema > CURRENT_SCHEMA) {
      throw new IllegalArgumentException(
          "project schema " + p.schema + " is newer than this version (" + CURRENT_SCHEMA + ")");
    }
    if (p.cameraKeyframes == null) {
      p.cameraKeyframes = new ArrayList<>();
    }
    if (p.effects == null) {
      p.effects = new ArrayList<>();
    }
    if (p.settings == null) {
      p.settings = new ArrayList<>();
    }
    if (p.render == null) {
      p.render = new Render();
    }
    return p;
  }

  private static <E extends Enum<E>> E parse(Class<E> type, String value) {
    try {
      return Enum.valueOf(type, value);
    } catch (RuntimeException e) {
      return type.getEnumConstants()[0];
    }
  }
}
