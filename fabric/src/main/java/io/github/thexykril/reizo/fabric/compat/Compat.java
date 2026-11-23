/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

/**
 * Small differences between Minecraft versions that are not plain renames (those are Stonecutter
 * replacements in fabric/build.gradle.kts). Each method holds one version switch, so the callers
 * stay free of version blocks.
 */
public final class Compat {
  private Compat() {}

  /** Whether the HUD is hidden (F1). */
  public static boolean hudHidden(Minecraft mc) {
    //? if >=26.1 {
    /*return mc.gui.hud.isHidden();
    *///?} else {
    return mc.options.hideGui;
    //?}
  }

  public static void setHudHidden(Minecraft mc, boolean hidden) {
    //? if >=26.1 {
    /*if (mc.gui.hud.isHidden() != hidden) {
      mc.gui.hud.toggle();
    }
    *///?} else {
    mc.options.hideGui = hidden;
    //?}
  }

  /**
   * Makes the main render target follow the window size again, after the size the window reports
   * changed (an export renders at its own size).
   */
  public static void resizeRenderTargets(Minecraft mc) {
    //? if >=26.1 {
    /*mc.gameRenderer.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
    mc.resizeGui();
    *///?} else {
    mc.resizeDisplay();
    //?}
  }

  /** The largest texture side the GPU accepts. */
  public static int maxTextureSize() {
    //? if >=26.1 {
    /*return RenderSystem.getDevice()
        .getDeviceInfo()
        .limits()
        .maxTextureSizeForFormat(com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM);
    *///?} else {
    return RenderSystem.getDevice().getMaxTextureSize();
    //?}
  }
}
