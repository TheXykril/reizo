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
    TAB
  }

  private final Style style;
  private String icon;
  private BooleanSupplier selected = () -> false;

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
          case ACCENT -> hot ? Theme.ACCENT_HOVER : Theme.ACCENT;
          case TAB -> hot && !sel ? Theme.HOVER : 0;
        };
    if (fill != 0) {
      Draw.round(g, x0, y0, x1, y1, 2, fill);
    }
    if (style == Style.TAB && sel) {
      g.fill(x0 + 2, y1 - 1, x1 - 2, y1, Theme.ACCENT);
    }
    int color;
    if (!active) {
      color = Theme.FAINT;
    } else if (style == Style.ACCENT) {
      color = 0xFFFFFFFF;
    } else if (sel) {
      color = style == Style.TAB ? Theme.TEXT : Theme.ACCENT_HOVER;
    } else {
      color = hot ? Theme.TEXT : (style == Style.TAB ? Theme.DIM : Theme.TEXT);
    }
    if (icon != null && Icons.has(icon)) {
      Icons.draw(g, icon, (x0 + x1) / 2, (y0 + y1) / 2, color);
      return;
    }
    String s = icon != null ? icon : Draw.fit(font, getMessage().getString(), getWidth() - 6);
    Draw.centered(g, font, s, (x0 + x1) / 2 + (icon != null ? 1 : 0), y0 + (getHeight() - 8) / 2, color);
  }
}
