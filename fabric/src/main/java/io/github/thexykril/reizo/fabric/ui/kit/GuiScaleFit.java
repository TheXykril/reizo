/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui.kit;

import net.minecraft.client.Minecraft;

/**
 * Lowers the GUI scale while a Reizo screen is open when the player's scale leaves too little room,
 * so Reizo's screens keep their layout at any GUI scale setting. {@link #restore} puts the player's
 * own scale back.
 */
public final class GuiScaleFit {
  private GuiScaleFit() {}

  /** Lowers the scale until the GUI is at least {@code minWidth} wide; true if it changed. */
  public static boolean fit(Minecraft mc, int minWidth) {
    var window = mc.getWindow();
    int scale = window.getGuiScale();
    int fit = scale;
    while (fit > 1 && window.getWidth() / fit < minWidth) {
      fit--;
    }
    if (fit == scale) {
      return false;
    }
    window.setGuiScale(fit);
    return true;
  }

  /** The scale the player's settings ask for. */
  public static void restore(Minecraft mc) {
    var window = mc.getWindow();
    window.setGuiScale(window.calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode()));
  }
}
