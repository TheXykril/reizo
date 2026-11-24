/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import net.minecraft.client.gui.screens.Screen;

/**
 * Creates the screens built on OneConfig's Compose UI. Kept apart so that their classes (and
 * OneConfig's) are only loaded when OneConfig is installed.
 */
final class ComposeScreens {
  private ComposeScreens() {}

  static Screen export(Screen parent) {
    return new ComposeExportScreen(parent);
  }
}
