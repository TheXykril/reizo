/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.compat;

import io.github.thexykril.reizo.core.effects.PropertyTracks;
import io.github.thexykril.reizo.fabric.settings.KeyframedSetting;
import java.util.ArrayList;
import java.util.List;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.api.config.v1.MetaKeys;
import org.polyfrost.oneconfig.api.config.v1.Node;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.Tree;

/**
 * OneConfig settings the timeline can keyframe: every switch and every bounded number (slider or
 * number field) of every config registered with OneConfig, which is how OneClient's mods keep their
 * options. Reizo's own page is left out. Loaded only through {@code KeyframedSettings} when
 * OneConfig is installed.
 *
 * <p>Values are set with {@link Property#setAs}, the same path OneConfig's own screen uses, so mods
 * that react to a change see it. The driver puts the player's values back afterwards.
 */
public final class OneConfigSettings {
  private static final String OWN_CONFIG = "reizo-oneconfig.json";

  private OneConfigSettings() {}

  public static List<KeyframedSetting> all() {
    List<KeyframedSetting> out = new ArrayList<>();
    for (Tree tree : ConfigManager.active().trees()) {
      String config = tree.getID();
      if (config == null || config.equals(OWN_CONFIG)) {
        continue;
      }
      String group = text(tree.getTitle(), config);
      tree.onAllProps(
          (path, prop) -> {
            KeyframedSetting s = of(config, group, path, prop);
            if (s != null) {
              out.add(s);
            }
          });
    }
    return out;
  }

  private static KeyframedSetting of(String config, String group, String path, Property<?> prop) {
    if (Boolean.TRUE.equals(meta(prop, MetaKeys.HIDDEN.getName()))) {
      return null;
    }
    Class<?> type = box(prop.type);
    PropertyTracks.Kind kind;
    double min;
    double max;
    if (type == Boolean.class) {
      kind = PropertyTracks.Kind.TOGGLE;
      min = 0;
      max = 1;
    } else if (Number.class.isAssignableFrom(type)
        && meta(prop, MetaKeys.MIN.getName()) instanceof Number lo
        && meta(prop, MetaKeys.MAX.getName()) instanceof Number hi
        && hi.doubleValue() > lo.doubleValue()) {
      kind = PropertyTracks.Kind.NUMBER;
      min = lo.doubleValue();
      max = hi.doubleValue();
    } else {
      return null;
    }
    String id = "oneconfig:" + config + "/" + path;
    String label = text(prop.getTitle(), path);
    return new KeyframedSetting() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public String group() {
        return group;
      }

      @Override
      public String label() {
        return label;
      }

      @Override
      public PropertyTracks.Kind kind() {
        return kind;
      }

      @Override
      public double min() {
        return min;
      }

      @Override
      public double max() {
        return max;
      }

      @Override
      public double get() {
        Object v = prop.get();
        if (v instanceof Boolean b) {
          return b ? 1 : 0;
        }
        return v instanceof Number n ? n.doubleValue() : 0;
      }

      @Override
      public void set(double value) {
        prop.setAs(convert(type, value));
      }
    };
  }

  private static Object meta(Node node, String key) {
    try {
      return node.getMetadata(key);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String text(Object title, String fallback) {
    if (title == null) {
      return fallback;
    }
    String s = String.valueOf(Node.strv(title));
    return s.isBlank() ? fallback : s;
  }

  private static Class<?> box(Class<?> c) {
    if (!c.isPrimitive()) {
      return c;
    }
    if (c == boolean.class) {
      return Boolean.class;
    }
    if (c == int.class) {
      return Integer.class;
    }
    if (c == float.class) {
      return Float.class;
    }
    if (c == double.class) {
      return Double.class;
    }
    if (c == long.class) {
      return Long.class;
    }
    if (c == short.class) {
      return Short.class;
    }
    if (c == byte.class) {
      return Byte.class;
    }
    return c;
  }

  private static Object convert(Class<?> type, double v) {
    if (type == Boolean.class) {
      return v != 0;
    }
    if (type == Integer.class) {
      return (int) Math.round(v);
    }
    if (type == Long.class) {
      return Math.round(v);
    }
    if (type == Short.class) {
      return (short) Math.round(v);
    }
    if (type == Byte.class) {
      return (byte) Math.round(v);
    }
    if (type == Float.class) {
      return (float) v;
    }
    return v;
  }
}
