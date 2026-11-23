/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui.kit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * A text field in the editor's theme. Editing, selection, clipboard and narration are vanilla
 * {@link EditBox}; only the drawing differs (rounded field, the editor font, an accent caret).
 */
public class KitField extends EditBox {
  private Component placeholder = Component.empty();

  public KitField(Font font, int x, int y, int w, int h, Component label) {
    super(font, x, y, w, h, label);
  }

  /** Text shown dimmed while the field is empty. */
  public KitField placeholder(Component placeholder) {
    this.placeholder = placeholder;
    return this;
  }

  @Override
  public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    if (!isVisible()) {
      return;
    }
    Font font = Minecraft.getInstance().font;
    int x0 = getX();
    int y0 = getY();
    int x1 = x0 + getWidth();
    int y1 = y0 + getHeight();
    boolean focused = isFocused();
    Draw.round(g, x0, y0, x1, y1, 4, focused ? Theme.ACCENT : Theme.LINE);
    Draw.round(
        g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, 3, isHovered() && !focused ? Theme.HOVER : Theme.RAISED);
    int tx = x0 + 6;
    int ty = y0 + (getHeight() - 8) / 2;
    int inner = getWidth() - 12;
    String value = getValue();
    if (value.isEmpty() && !focused) {
      Draw.label(g, font, Draw.fit(font, placeholder.getString(), inner), tx, ty, Theme.FAINT);
      return;
    }
    // Scroll so the caret stays visible.
    int cursor = Math.min(getCursorPosition(), value.length());
    int start = 0;
    while (start < cursor && Draw.width(font, value.substring(start, cursor)) > inner - 2) {
      start++;
    }
    int end = value.length();
    while (end > start && Draw.width(font, value.substring(start, end)) > inner) {
      end--;
    }
    String shown = value.substring(start, Math.max(start, end));
    String selected = getHighlighted();
    if (focused && !selected.isEmpty()) {
      int a = value.indexOf(selected, Math.max(0, Math.min(cursor, value.length()) - selected.length()));
      if (a < 0) {
        a = value.indexOf(selected);
      }
      int sa = Math.max(a, start);
      int sb = Math.min(a + selected.length(), end);
      if (sb > sa) {
        int xa = tx + Draw.width(font, value.substring(start, sa));
        int xb = tx + Draw.width(font, value.substring(start, sb));
        g.fill(xa, ty - 1, xb, ty + 9, Theme.ACCENT_WASH);
      }
    }
    Draw.label(g, font, shown, tx, ty, active ? Theme.TEXT : Theme.DIM);
    if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
      int cx = tx + Draw.width(font, value.substring(start, Math.max(start, cursor)));
      g.fill(cx, ty - 1, cx + 1, ty + 9, Theme.ACCENT_HOVER);
    }
  }
}
