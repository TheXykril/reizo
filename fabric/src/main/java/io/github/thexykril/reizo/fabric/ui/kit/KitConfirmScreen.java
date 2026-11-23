/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui.kit;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** A yes/no question in the editor's look; {@code answer} gets the choice. */
public final class KitConfirmScreen extends KitScreen {
  private final BooleanConsumer answer;
  private final Component message;
  private final Component yes;
  private final Component no;
  private final boolean danger;

  public KitConfirmScreen(
      BooleanConsumer answer,
      Component title,
      Component message,
      Component yes,
      Component no,
      boolean danger) {
    super(title);
    this.answer = answer;
    this.message = message;
    this.yes = yes;
    this.no = no;
    this.danger = danger;
  }

  @Override
  protected void init() {
    int lines = Math.max(1, font.split(Draw.ui(message), 240 - PAD * 2).size());
    card(240, TITLE_H + PAD * 3 + lines * 10 + 16);
    int x = innerX();
    int w = innerW();
    int half = (w - GAP) / 2;
    button(x, footerY(), half, no, () -> answer.accept(false));
    FlatButton y =
        addRenderableWidget(
            new FlatButton(
                x + half + GAP,
                footerY(),
                w - half - GAP,
                ROW_H - 2,
                yes,
                FlatButton.Style.ACCENT,
                b -> answer.accept(true)));
    if (danger) {
      y.danger();
    }
  }

  @Override
  protected void renderCard(GuiGraphics g, int mouseX, int mouseY) {
    Draw.wrap(g, font, message, innerX(), contentY(), innerW(), Theme.DIM);
  }

  @Override
  public void onClose() {
    answer.accept(false);
  }
}
