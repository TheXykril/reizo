/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui.kit;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A Reizo screen in the editor's look: the game dimmed behind a rounded card with a title bar, and
 * helpers that build the kit's rows. Subclasses set the card with {@link #card(int, int)} in {@code
 * init} and lay their widgets out inside {@link #cardX0()}..{@link #cardX1()}, from {@link
 * #contentY()}.
 */
public abstract class KitScreen extends Screen {
  protected static final int TITLE_H = 18;
  protected static final int ROW_H = 18;
  protected static final int GAP = 4;
  protected static final int PAD = 8;

  private int cx0;
  private int cy0;
  private int cx1;
  private int cy1;
  private boolean dim = true;
  private boolean ownScale;

  /** GUI width the screen wants; a smaller GUI lowers the scale while the screen is open. */
  protected int minGuiWidth() {
    return 560;
  }

  /** Call first in {@code init}: fits the GUI scale and updates width and height. */
  protected void fitScale() {
    if (GuiScaleFit.fit(minecraft, minGuiWidth())) {
      width = minecraft.getWindow().getGuiScaledWidth();
      height = minecraft.getWindow().getGuiScaledHeight();
      ownScale = true;
    }
  }

  @Override
  public void removed() {
    super.removed();
    if (ownScale) {
      ownScale = false;
      GuiScaleFit.restore(minecraft);
    }
  }

  protected KitScreen(Component title) {
    super(title);
  }

  /** Centres a card of {@code w} x {@code h}, shrunk to fit the window. */
  protected void card(int w, int h) {
    fitScale();
    w = Math.min(w, width - 16);
    h = Math.min(h, height - 16);
    cx0 = (width - w) / 2;
    cy0 = (height - h) / 2;
    cx1 = cx0 + w;
    cy1 = cy0 + h;
  }

  /** A panel docked on the left, full height, with the game left undimmed beside it. */
  protected void sidePanel(int w) {
    fitScale();
    cx0 = 6;
    cy0 = 6;
    cx1 = Math.min(width - 6, 6 + w);
    cy1 = height - 6;
    dim = false;
  }

  protected int cardX0() {
    return cx0;
  }

  protected int cardX1() {
    return cx1;
  }

  protected int cardY0() {
    return cy0;
  }

  protected int cardY1() {
    return cy1;
  }

  /** First y below the title bar. */
  protected int contentY() {
    return cy0 + TITLE_H + PAD;
  }

  /** Inner left edge and width of the card's content. */
  protected int innerX() {
    return cx0 + PAD;
  }

  protected int innerW() {
    return cx1 - cx0 - PAD * 2;
  }

  /** The bottom row of buttons. */
  protected int footerY() {
    return cy1 - PAD - 16;
  }

  @Override
  public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    if (dim) {
      g.fill(0, 0, width, height, 0xB00B0F13);
    }
    if (cx1 > cx0) {
      Draw.round(g, cx0 - 1, cy0 + 1, cx1 + 3, cy1 + 4, 8, 0x60000000);
      Draw.round(g, cx0 - 1, cy0 - 1, cx1 + 1, cy1 + 1, 7, Theme.LINE);
      Draw.round(g, cx0, cy0, cx1, cy1, 6, 0xFF161D23);
      Draw.round(g, cx0, cy0, cx1, cy0 + TITLE_H, 6, Theme.GROUND | 0xFF000000, true, true);
      Draw.hline(g, cx0, cx1, cy0 + TITLE_H, Theme.LINE);
      Draw.round(g, cx0 + 7, cy0 + 6, cx0 + 13, cy0 + 12, 2, Theme.ACCENT);
      Draw.label(g, font, title.getString(), cx0 + 18, cy0 + 5, Theme.TEXT);
    }
    renderCard(g, mouseX, mouseY);
  }

  /** Draws under the widgets, inside the card (section labels, previews). */
  protected void renderCard(GuiGraphics g, int mouseX, int mouseY) {}

  /** A small section label at {@code y}. */
  protected void sectionLabel(GuiGraphics g, String text, int y) {
    Draw.label(g, font, text.toUpperCase(java.util.Locale.ROOT), innerX() + 1, y, Theme.FAINT);
  }

  // --- Row builders ------------------------------------------------------------------------

  protected FlatButton button(int x, int y, int w, Component label, Runnable action) {
    return addRenderableWidget(
        new FlatButton(x, y, w, ROW_H - 2, label, FlatButton.Style.TONAL, b -> action.run()));
  }

  protected FlatButton accentButton(int x, int y, int w, Component label, Runnable action) {
    return addRenderableWidget(
        new FlatButton(x, y, w, ROW_H - 2, label, FlatButton.Style.ACCENT, b -> action.run()));
  }

  /** An on/off row with a switch. */
  protected FlatButton toggle(
      int x, int y, int w, Component label, BooleanSupplier value, Consumer<Boolean> set) {
    return addRenderableWidget(
        new FlatButton(
                x,
                y,
                w,
                ROW_H - 2,
                label,
                FlatButton.Style.ROW,
                b -> set.accept(!value.getAsBoolean()))
            .switchWhen(value));
  }

  /** A row that steps through {@code values} on click (right-click steps back). */
  protected <T> FlatButton cycle(
      int x,
      int y,
      int w,
      Component label,
      List<T> values,
      java.util.function.Supplier<T> value,
      Function<T, Component> name,
      Consumer<T> set) {
    return addRenderableWidget(
        new FlatButton(
                x,
                y,
                w,
                ROW_H - 2,
                label,
                FlatButton.Style.ROW,
                b -> {
                  int i = values.indexOf(value.get());
                  set.accept(values.get((i + 1) % values.size()));
                })
            .value(() -> name.apply(value.get()).getString()));
  }
}
