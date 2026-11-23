/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * A modifier key required together with a key mapping. Vanilla key mappings have no modifiers, so
 * the base key stays a normal, rebindable key mapping and the modifier is a Reizo setting.
 */
public enum KeyModifier {
  NONE(-1, -1),
  SHIFT(InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT),
  CONTROL(InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL),
  ALT(InputConstants.KEY_LALT, InputConstants.KEY_RALT);

  private final int left;
  private final int right;

  KeyModifier(int left, int right) {
    this.left = left;
    this.right = right;
  }

  /** Whether the modifier is held (always true for {@link #NONE}). */
  public boolean isDown(Window window) {
    return this == NONE || isKeyDown(window, left) || isKeyDown(window, right);
  }

  private static boolean isKeyDown(Window window, int key) {
    //? if >=26.3 {
    /*return InputConstants.isKeyDown(key);
    *///?} else {
    return InputConstants.isKeyDown(window, key);
    //?}
  }

  public Component label() {
    return Component.translatable("reizo.modifier." + name().toLowerCase(Locale.ROOT));
  }

  /** Parses a config value; unknown values mean no modifier. */
  public static KeyModifier parse(String value) {
    if (value != null) {
      for (KeyModifier m : values()) {
        if (m.name().equalsIgnoreCase(value.strip())) {
          return m;
        }
      }
    }
    return NONE;
  }

  public String configValue() {
    return name().toLowerCase(Locale.ROOT);
  }
}
