/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.playback;

import io.github.thexykril.reizo.core.effects.PropertyTracks;
import io.github.thexykril.reizo.fabric.settings.KeyframedSetting;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Entities hidden from a replay (and its exports). Each hidden entity is a keyframed switch named
 * {@code reizo:hide/<uuid>} in the project's setting tracks, so hiding can start or stop at any
 * time, is undoable and is saved with the project. Entities are matched by UUID, which the recorded
 * packets keep across seeks. Render (client) thread only.
 */
public final class ReplayEntityHider {
  /** Setting id prefix; the rest is the entity's UUID. */
  public static final String PREFIX = "reizo:hide/";

  /** How far the hide key reaches, in blocks. */
  static final double REACH = 96;

  private static final Set<UUID> hidden = new HashSet<>();

  private ReplayEntityHider() {}

  /** Whether an entity is hidden now. */
  public static boolean isHidden(Entity entity) {
    return !hidden.isEmpty() && ReplayPlayback.isActive() && hidden.contains(entity.getUUID());
  }

  /** How many entities are hidden now. */
  public static int hiddenCount() {
    return hidden.size();
  }

  /** The keyframed switch for an id, or null when the id is not a hide id. */
  public static KeyframedSetting setting(String id) {
    if (!id.startsWith(PREFIX)) {
      return null;
    }
    UUID uuid;
    try {
      uuid = UUID.fromString(id.substring(PREFIX.length()));
    } catch (IllegalArgumentException e) {
      return null;
    }
    return new HideSetting(id, uuid);
  }

  /** The id of the switch that hides this entity. */
  public static String id(Entity entity) {
    return PREFIX + entity.getUUID();
  }

  /**
   * The entity the camera looks at, up to {@link #REACH} blocks away, ignoring the camera entity
   * itself; null when there is none.
   */
  public static Entity lookedAt(Minecraft mc) {
    Entity camera = mc.getCameraEntity();
    if (camera == null || mc.level == null) {
      return null;
    }
    Vec3 from = camera.getEyePosition(1f);
    Vec3 to = from.add(camera.getViewVector(1f).scale(REACH));
    Entity best = null;
    double bestDistance = Double.MAX_VALUE;
    for (Entity e : mc.level.entitiesForRendering()) {
      if (e == camera || e == mc.player) {
        continue;
      }
      AABB box = e.getBoundingBox().inflate(Math.max(0.1, e.getPickRadius()));
      var hit = box.contains(from) ? java.util.Optional.of(from) : box.clip(from, to);
      if (hit.isPresent()) {
        double d = from.distanceToSqr(hit.get());
        if (d < bestDistance) {
          bestDistance = d;
          best = e;
        }
      }
    }
    return best;
  }

  /**
   * Hides the entity from {@code timeMs} on, or shows it again from there if it is hidden now.
   * Returns whether it is hidden afterwards.
   */
  public static boolean toggle(Entity entity, long timeMs) {
    PropertyTracks tracks = ReplayEditor.settings();
    String id = id(entity);
    boolean hide = !hidden.contains(entity.getUUID());
    ReplayEditor.recordUndo();
    if (hide && !tracks.has(id) && timeMs > 0) {
      // Shown until now: a switch holds its first value before its first keyframe.
      tracks.put(id, PropertyTracks.Kind.TOGGLE, 0, 0);
    }
    tracks.put(id, PropertyTracks.Kind.TOGGLE, timeMs, hide ? 1 : 0);
    ReplayEditor.settingsChanged();
    return hide;
  }

  /** Shows every entity again (the replay closed). */
  public static void clear() {
    hidden.clear();
  }

  private record HideSetting(String id, UUID uuid) implements KeyframedSetting {
    @Override
    public String group() {
      return "Reizo";
    }

    @Override
    public String label() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
        for (Entity e : mc.level.entitiesForRendering()) {
          if (e.getUUID().equals(uuid)) {
            return net.minecraft.network.chat.Component.translatable(
                    "reizo.settings.hidden_entity", e.getName())
                .getString();
          }
        }
      }
      return net.minecraft.network.chat.Component.translatable(
              "reizo.settings.hidden_entity", uuid.toString().substring(0, 8))
          .getString();
    }

    @Override
    public PropertyTracks.Kind kind() {
      return PropertyTracks.Kind.TOGGLE;
    }

    @Override
    public double min() {
      return 0;
    }

    @Override
    public double max() {
      return 1;
    }

    @Override
    public double get() {
      return hidden.contains(uuid) ? 1 : 0;
    }

    @Override
    public void set(double value) {
      if (value != 0) {
        hidden.add(uuid);
      } else {
        hidden.remove(uuid);
      }
    }

    @Override
    public void restore(double original) {
      hidden.remove(uuid);
    }
  }
}
