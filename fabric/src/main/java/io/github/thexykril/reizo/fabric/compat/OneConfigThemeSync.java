/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.compat;

import io.github.thexykril.reizo.core.theme.Palette;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the theme selected in OneConfig (its UI module's {@code OneConfigTheme.getActive()}) so
 * Reizo's editor follows it: colours, light or dark, and the flat, glass or pixel style. Read by
 * reflection, since OneConfig is optional and its theme API may change; anything missing keeps
 * Reizo's own theme.
 */
public final class OneConfigThemeSync {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Theme");
  private static final String THEME = "org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme";

  /** OneConfig theme getter (Compose name mangling included) for each palette colour. */
  private static final Map<String, String> COLOURS =
      Map.ofEntries(
          Map.entry("page", "getPageBackground-0d7_KjU"),
          Map.entry("sidebar", "getSidebarBackground-0d7_KjU"),
          Map.entry("component", "getComponentBackground-0d7_KjU"),
          Map.entry("popup", "getPopupBackground-0d7_KjU"),
          Map.entry("border", "getBorderColor-0d7_KjU"),
          Map.entry("text", "getTextColor-0d7_KjU"),
          Map.entry("textSecondary", "getTextColorSecondary-0d7_KjU"),
          Map.entry("muted", "getMutedText-0d7_KjU"),
          Map.entry("error", "getError-0d7_KjU"),
          Map.entry("warning", "getWarning-0d7_KjU"),
          Map.entry("track", "getControlTrackColor-0d7_KjU"));

  private static boolean unavailable;
  private static Method active;
  private static Object lastTheme;
  private static Palette lastPalette;

  private OneConfigThemeSync() {}

  /** The palette of OneConfig's selected theme, or null without OneConfig. */
  public static Palette palette() {
    if (unavailable) {
      return null;
    }
    try {
      if (active == null) {
        if (!FabricLoader.getInstance().isModLoaded("oneconfig")) {
          unavailable = true;
          return null;
        }
        active = Class.forName(THEME).getMethod("getActive");
      }
      Object theme = active.invoke(null);
      if (theme == null) {
        return null;
      }
      if (theme == lastTheme && lastPalette != null) {
        return lastPalette;
      }
      lastTheme = theme;
      lastPalette = read(theme);
      LOGGER.info("Following OneConfig's theme {}: {}", name(theme), lastPalette);
      return lastPalette;
    } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
      LOGGER.info("OneConfig's theme can't be read; using Reizo's own theme ({})", e.toString());
      unavailable = true;
      return null;
    }
  }

  private static String name(Object theme) {
    try {
      return String.valueOf(theme.getClass().getMethod("getName").invoke(theme));
    } catch (ReflectiveOperationException | RuntimeException e) {
      return theme.getClass().getSimpleName();
    }
  }

  private static Palette read(Object theme) {
    Map<String, Integer> colours = new HashMap<>();
    for (var e : COLOURS.entrySet()) {
      Integer argb = colour(theme, e.getValue());
      if (argb != null) {
        colours.put(e.getKey(), argb);
      }
    }
    Integer accent = accent(theme);
    if (accent != null) {
      colours.put("accent", accent);
    }
    String style = "Flat";
    boolean light = false;
    try {
      style = String.valueOf(theme.getClass().getMethod("getStyle").invoke(theme));
      light = (Boolean) theme.getClass().getMethod("isLight").invoke(theme);
    } catch (ReflectiveOperationException | RuntimeException ignored) {
      // Older OneConfig: flat and dark.
    }
    return Palette.fromOneConfig(colours, style, light, Palette.DEFAULT);
  }

  private static Integer colour(Object target, String getter) {
    try {
      Object v = target.getClass().getMethod(getter).invoke(target);
      return v instanceof Long l ? Palette.composeToArgb(l) : null;
    } catch (ReflectiveOperationException | RuntimeException e) {
      return null;
    }
  }

  /** The accent: the accent brush when it is one colour, else the focus ring colour. */
  private static Integer accent(Object theme) {
    try {
      Object brush = theme.getClass().getMethod("getAccentBrush").invoke(theme);
      if (brush != null) {
        Integer c = colour(brush, "getValue-0d7_KjU");
        if (c != null) {
          return c;
        }
      }
    } catch (ReflectiveOperationException | RuntimeException ignored) {
      // Fall through.
    }
    return colour(theme, "getFocusRingColor-0d7_KjU");
  }
}
