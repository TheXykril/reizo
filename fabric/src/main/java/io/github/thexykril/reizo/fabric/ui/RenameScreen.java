/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Asks for a new recording name. */
final class RenameScreen extends Screen {
  private final Screen parent;
  private final String initial;
  private final Consumer<String> onDone;
  private EditBox box;
  private Button done;

  RenameScreen(Screen parent, String initial, Consumer<String> onDone) {
    super(Component.translatable("reizo.browser.rename_title"));
    this.parent = parent;
    this.initial = initial == null ? "" : initial;
    this.onDone = onDone;
  }

  @Override
  protected void init() {
    box = new EditBox(font, width / 2 - 100, height / 2 - 20, 200, 20, title);
    box.setMaxLength(64);
    box.setValue(box.getValue().isEmpty() ? initial : box.getValue());
    box.setResponder(v -> done.active = !v.isBlank());
    addRenderableWidget(box);
    setInitialFocus(box);
    done =
        addRenderableWidget(
            Button.builder(Component.translatable("gui.done"), b -> finish())
                .bounds(width / 2 - 100, height / 2 + 10, 98, 20)
                .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.cancel"), b -> onClose())
            .bounds(width / 2 + 2, height / 2 + 10, 98, 20)
            .build());
    done.active = !box.getValue().isBlank();
  }

  private void finish() {
    if (!box.getValue().isBlank()) {
      onDone.accept(box.getValue());
    }
    onClose();
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    super.render(g, mouseX, mouseY, partialTick);
    g.drawCenteredString(font, title, width / 2, height / 2 - 40, 0xFFFFFFFF);
  }

  @Override
  public void onClose() {
    minecraft.setScreen(parent);
  }
}
