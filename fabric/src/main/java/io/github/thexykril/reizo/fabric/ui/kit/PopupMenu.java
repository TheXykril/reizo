/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A drop-down or right-click menu: a rounded list of actions with optional shortcut hints and
 * separators. The screen that owns it draws it last and gives it clicks first.
 */
public final class PopupMenu {
  private static final int ROW = 13;
  private static final int PAD = 3;

  private record Item(Component label, String shortcut, Runnable action, BooleanSupplier enabled) {}

  private final List<Item> items = new ArrayList<>();
  private int x;
  private int y;
  private int w;

  public PopupMenu add(Component label, String shortcut, Runnable action) {
    return add(label, shortcut, action, () -> true);
  }

  public PopupMenu add(
      Component label, String shortcut, Runnable action, BooleanSupplier enabled) {
    items.add(new Item(label, shortcut, action, enabled));
    return this;
  }

  public PopupMenu separator() {
    items.add(null);
    return this;
  }

  /** Places the menu at (x, y), kept inside a screen of the given size. */
  public PopupMenu at(Font font, int x, int y, int screenW, int screenH) {
    int width = 90;
    for (Item i : items) {
      if (i != null) {
        int sw = i.shortcut.isEmpty() ? 0 : Draw.width(font, i.shortcut) + 14;
        width = Math.max(width, Draw.width(font, i.label) + sw + 18);
      }
    }
    this.w = width;
    this.x = Math.max(2, Math.min(x, screenW - width - 2));
    this.y = Math.max(2, Math.min(y, screenH - height() - 2));
    return this;
  }

  private int height() {
    int h = PAD * 2;
    for (Item i : items) {
      h += i == null ? 5 : ROW;
    }
    return h;
  }

  public boolean contains(double mx, double my) {
    return mx >= x && mx < x + w && my >= y && my < y + height();
  }

  public void draw(GuiGraphics g, Font font, int mouseX, int mouseY) {
    int h = height();
    Draw.round(g, x - 1, y - 1, x + w + 1, y + h + 1, 5, Theme.LINE);
    Draw.round(g, x, y, x + w, y + h, 4, Theme.alpha(Draw.mix(Theme.SURFACE, Theme.RAISED, 0.3), 0xFA));
    int ry = y + PAD;
    for (Item i : items) {
      if (i == null) {
        Draw.hline(g, x + 6, x + w - 6, ry + 2, Theme.LINE);
        ry += 5;
        continue;
      }
      boolean on = i.enabled.getAsBoolean();
      boolean hot = on && mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + ROW;
      if (hot) {
        Draw.round(g, x + 3, ry, x + w - 3, ry + ROW, 3, Theme.ACCENT);
      }
      Draw.label(g, font, i.label.getString(), x + 9, ry + 3, on ? Theme.TEXT : Theme.FAINT);
      if (!i.shortcut.isEmpty()) {
        Draw.label(
            g,
            font,
            i.shortcut,
            x + w - 9 - Draw.width(font, i.shortcut),
            ry + 3,
            hot ? Theme.TEXT : Theme.FAINT);
      }
      ry += ROW;
    }
  }

  /** Runs the item under the mouse. Returns whether the click was on the menu. */
  public boolean click(double mx, double my) {
    if (!contains(mx, my)) {
      return false;
    }
    int ry = y + PAD;
    for (Item i : items) {
      int rh = i == null ? 5 : ROW;
      if (i != null && my >= ry && my < ry + rh && i.enabled.getAsBoolean()) {
        i.action.run();
        return true;
      }
      ry += rh;
    }
    return true;
  }
}
