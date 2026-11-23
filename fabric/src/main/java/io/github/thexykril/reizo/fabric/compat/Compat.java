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

  /** A cycle button builder starting at {@code initial}. */
  public static <T> net.minecraft.client.gui.components.CycleButton.Builder<T> cycle(
      java.util.function.Function<T, net.minecraft.network.chat.Component> label, T initial) {
    //? if >=1.21.11 {
    return net.minecraft.client.gui.components.CycleButton.builder(label, initial);
    //?} else {
    /*return net.minecraft.client.gui.components.CycleButton.<T>builder(label)
        .withInitialValue(initial);
    *///?}
  }

  /** Whether the HUD is hidden (F1). */
  public static boolean hudHidden(Minecraft mc) {
    //? if >=26.2 {
    /*return mc.gui.hud.isHidden();
    *///?} else {
    return mc.options.hideGui;
    //?}
  }

  public static void setHudHidden(Minecraft mc, boolean hidden) {
    //? if >=26.2 {
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
    //? if >=26.3 {
    /*return RenderSystem.getDevice()
        .getDeviceInfo()
        .limits()
        .maxTextureSizeForFormat(com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM);
    *///?} else if >=26.2 {
    /*return RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSize();
    *///?} else {
    return RenderSystem.getDevice().getMaxTextureSize();
    //?}
  }

  /** The camera's yaw in degrees. */
  public static float cameraYaw(net.minecraft.client.Camera camera) {
    //? if >=1.21.11 {
    return camera.yRot();
    //?} else {
    /*return camera.getYRot();
    *///?}
  }

  /** Whether Control (Command on macOS) is held for a key event. */
  public static boolean controlDown(net.minecraft.client.input.KeyEvent event) {
    //? if >=1.21.11 {
    return event.hasControlDownWithQuirk();
    //?} else {
    /*return event.hasControlDown();
    *///?}
  }
}
