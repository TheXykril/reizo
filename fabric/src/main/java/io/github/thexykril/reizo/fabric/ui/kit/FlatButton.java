/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui.kit;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * A flat, rounded button in the editor's theme. It is a vanilla {@link Button}, so focus,
 * keyboard, narration and lookups by label keep working; only the drawing differs. An icon button
 * shows a glyph and keeps its label as the tooltip and narration.
 */
public class FlatButton extends Button {
  /** How the button is filled. */
  public enum Style {
    /** No fill until hovered: toolbars. */
    GHOST,
    /** A raised fill: ordinary actions. */
    TONAL,
    /** The accent fill: the main action of a panel. */
    ACCENT,
    /** A tab or segment: accent text and underline when selected. */
    TAB,
    /** A collapsible section header: an arrow and left-aligned text; selected means open. */
    HEADER,
    /** A settings row: label on the left, a value or a switch on the right. */
    ROW,
    /** A segment of a pill switch: the accent fill when selected. */
    SEGMENT
  }

  private final Style style;
  private String icon;
  private BooleanSupplier selected = () -> false;
  private java.util.function.Supplier<String> value;
  private BooleanSupplier on;
  private boolean danger;
  private boolean left;

  public FlatButton(int x, int y, int w, int h, Component label, Style style, OnPress onPress) {
    super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
    this.style = style;
  }

  /** Shows {@code glyph} instead of the label; the label becomes the tooltip. */
  public FlatButton icon(String glyph) {
    if (icon == null) {
      setTooltip(Tooltip.create(getMessage()));
    }
    this.icon = glyph;
    return this;
  }

  public FlatButton selectedWhen(BooleanSupplier selected) {
    this.selected = selected;
    return this;
  }

  /** A ROW's value, drawn on the right. */
  public FlatButton value(java.util.function.Supplier<String> value) {
    this.value = value;
    return this;
  }

  /** Draws a ROW's value as an on/off switch. */
  public FlatButton switchWhen(BooleanSupplier on) {
    this.on = on;
    return this;
  }

  /** Left-aligns the label (list rows). */
  public FlatButton alignLeft() {
    this.left = true;
    return this;
  }

  /** An ACCENT button for a destructive action: red instead of blue. */
  public FlatButton danger() {
    this.danger = true;
    return this;
  }

  public boolean isSelected() {
    return selected.getAsBoolean();
  }

  /** Width that fits the label (or the icon) with padding. */
  public static int fitWidth(Font font, Component label, int padding) {
    return Draw.width(font, label) + padding * 2;
  }

  //? if >=1.21.11 {
  @Override
  protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    draw(g);
  }
  //?} else {
  /*@Override
  protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    draw(g);
  }
  *///?}

  private void draw(GuiGraphics g) {
    Font font = Minecraft.getInstance().font;
    int x0 = getX();
    int y0 = getY();
    int x1 = x0 + getWidth();
    int y1 = y0 + getHeight();
    boolean hot = active && isHoveredOrFocused();
    boolean sel = isSelected();
    int fill =
        switch (style) {
          case GHOST -> sel ? Theme.ACCENT_WASH : hot ? Theme.HOVER : 0;
          case TONAL -> sel ? Theme.ACCENT_WASH : hot ? Theme.HOVER : Theme.RAISED;
          case ACCENT ->
              danger
                  ? (hot ? Draw.mix(Theme.DANGER, 0xFFFFFFFF, 0.15) : Theme.DANGER)
                  : (hot ? Theme.ACCENT_HOVER : Theme.ACCENT);
          case TAB -> sel ? Theme.RAISED : hot ? Theme.HOVER : 0;
          case HEADER -> 0;
          case ROW -> hot ? Theme.HOVER : Theme.RAISED;
          case SEGMENT -> sel ? (hot ? Theme.ACCENT_HOVER : Theme.ACCENT) : hot ? Theme.HOVER : 0;
        };
    if (fill != 0) {
      Draw.round(g, x0, y0, x1, y1, style == Style.ROW ? 4 : 3, fill);
    }
    int color;
    if (!active) {
      color = Theme.FAINT;
    } else if (style == Style.ACCENT || (style == Style.SEGMENT && sel)) {
      color = 0xFFFFFFFF;
    } else if (sel) {
      color = style == Style.TAB ? Theme.TEXT : Theme.ACCENT_HOVER;
    } else {
      color =
          hot ? Theme.TEXT : (style == Style.TAB || style == Style.SEGMENT ? Theme.DIM : Theme.TEXT);
    }
    if (style == Style.HEADER) {
      // A small caps section label; the chevron on the right folds it.
      Icons.draw(g, sel ? "open" : "closed", x1 - 5, (y0 + y1) / 2, 6, hot ? Theme.TEXT : Theme.FAINT);
      String caps = getMessage().getString().toUpperCase(java.util.Locale.ROOT);
      Draw.label(
          g,
          font,
          Draw.fit(font, caps, getWidth() - 14),
          x0 + 1,
          y0 + (getHeight() - 8) / 2,
          hot ? Theme.TEXT : Theme.DIM);
      return;
    }
    if (style == Style.ROW) {
      int ty = y0 + (getHeight() - 8) / 2;
      int right = x1 - 6;
      if (on != null) {
        boolean v = on.getAsBoolean();
        int sw = 16;
        int sh = 8;
        int sy = (y0 + y1 - sh) / 2;
        Draw.round(g, right - sw, sy, right, sy + sh, 4, v ? Theme.ACCENT : Theme.TRACK_BG);
        int kx = v ? right - sh + 1 : right - sw + 1;
        Draw.round(g, kx, sy + 1, kx + sh - 2, sy + sh - 1, 3, active ? 0xFFFFFFFF : Theme.DIM);
        right -= sw + 6;
      } else if (value != null) {
        String v = value.get();
        int vw = Draw.width(font, v);
        Draw.label(g, font, v, right - vw, ty, active ? Theme.ACCENT_HOVER : Theme.FAINT);
        right -= vw + 8;
      }
      Draw.label(
          g, font, Draw.fit(font, getMessage().getString(), right - x0 - 8), x0 + 7, ty, color);
      return;
    }
    if (icon != null && Icons.has(icon)) {
      int size = Math.max(7, Math.min(10, Math.min(getWidth(), getHeight()) - 8));
      Icons.draw(g, icon, (x0 + x1) / 2, (y0 + y1) / 2, size, color);
      return;
    }
    if (left && icon == null) {
      Draw.label(
          g, font, Draw.fit(font, getMessage().getString(), getWidth() - 12), x0 + 6, y0 + (getHeight() - 8) / 2, color);
      return;
    }
    String s = icon != null ? icon : Draw.fit(font, getMessage().getString(), getWidth() - 6);
    Draw.centered(g, font, s, (x0 + x1) / 2 + (icon != null ? 1 : 0), y0 + (getHeight() - 8) / 2, color);
  }
}
