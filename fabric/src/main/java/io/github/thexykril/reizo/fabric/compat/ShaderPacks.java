/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.compat;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

/** Whether a shader pack (Iris) draws the world, through Iris's public API when Iris is loaded. */
public final class ShaderPacks {
  private static Object api;
  private static Method inUse;
  private static boolean looked;

  private ShaderPacks() {}

  /** True while Iris has a shader pack on. */
  public static boolean inUse() {
    if (!looked) {
      looked = true;
      if (FabricLoader.getInstance().isModLoaded("iris")) {
        try {
          Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
          api = c.getMethod("getInstance").invoke(null);
          inUse = c.getMethod("isShaderPackInUse");
        } catch (ReflectiveOperationException | LinkageError e) {
          api = null;
        }
      }
    }
    if (api == null) {
      return false;
    }
    try {
      return (boolean) inUse.invoke(api);
    } catch (ReflectiveOperationException e) {
      return false;
    }
  }
}
