/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.compat;

import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Whether Reizo runs inside OneClient. OneClient is a Fabric mod with the id {@code oneclient}
 * (Polyfrost/OneClient), so Fabric Loader knows it like any other mod. Reizo works the same with
 * or without it; this only labels recordings and picks OneClient-friendly defaults. OneConfig
 * features check for OneConfig itself, since it also ships without OneClient.
 */
public final class OneClientDetection {
  public static final String MOD_ID = "oneclient";

  private OneClientDetection() {}

  public static boolean isOneClient() {
    return FabricLoader.getInstance().isModLoaded(MOD_ID);
  }

  /** OneClient's version when it is installed. */
  public static Optional<String> version() {
    return FabricLoader.getInstance()
        .getModContainer(MOD_ID)
        .map(c -> c.getMetadata().getVersion().getFriendlyString());
  }
}
