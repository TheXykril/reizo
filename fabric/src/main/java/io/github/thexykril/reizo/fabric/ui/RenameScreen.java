/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.fabric.ui.kit.FlatButton;
import io.github.thexykril.reizo.fabric.ui.kit.KitField;
import io.github.thexykril.reizo.fabric.ui.kit.KitScreen;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Asks for a new recording name. */
final class RenameScreen extends KitScreen {
  private final Screen parent;
  private String value;
  private final Consumer<String> onDone;
  private KitField box;
  private FlatButton done;

  RenameScreen(Screen parent, String initial, Consumer<String> onDone) {
    super(Component.translatable("reizo.browser.rename_title"));
    this.parent = parent;
    this.value = initial == null ? "" : initial;
    this.onDone = onDone;
  }

  @Override
  protected void init() {
    card(240, TITLE_H + PAD * 3 + 20 + 16);
    int x = innerX();
    int w = innerW();
    box = new KitField(font, x, contentY(), w, 20, title);
    box.setMaxLength(64);
    box.setValue(value);
    box.setResponder(
        v -> {
          value = v;
          if (done != null) {
            done.active = !v.isBlank();
          }
        });
    addRenderableWidget(box);
    setInitialFocus(box);
    int half = (w - GAP) / 2;
    button(x, footerY(), half, Component.translatable("gui.cancel"), this::onClose);
    done = accentButton(x + half + GAP, footerY(), w - half - GAP, Component.translatable("gui.done"), this::finish);
    done.active = !value.isBlank();
  }

  private void finish() {
    if (!box.getValue().isBlank()) {
      onDone.accept(box.getValue());
    }
    onClose();
  }

  @Override
  public void onClose() {
    minecraft.setScreen(parent);
  }
}
