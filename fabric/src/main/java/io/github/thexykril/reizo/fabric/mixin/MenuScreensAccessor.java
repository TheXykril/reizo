/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The screen constructor for a menu type, to rebuild a recorded container screen in a replay. */
@Mixin(MenuScreens.class)
public interface MenuScreensAccessor {
  @Invoker("getConstructor")
  static <T extends AbstractContainerMenu> MenuScreens.ScreenConstructor<T, ?> reizo$getConstructor(
      MenuType<T> type) {
    throw new AssertionError();
  }
}
