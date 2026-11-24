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

  static Screen browser(Screen parent) {
    return new ComposeBrowserScreen(parent);
  }

  static Screen settings(Screen parent) {
    return new ComposeSettingsScreen(parent);
  }

  static Screen timeline(Screen parent) {
    return new ComposeTimelineScreen(parent);
  }
}
