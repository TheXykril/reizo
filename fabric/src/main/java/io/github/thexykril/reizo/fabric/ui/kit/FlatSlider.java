/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui.kit;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * A flat slider in the editor's theme: a label on the left, the value on the right, a thin track
 * with a knob below. Values run from {@code min} to {@code max}; {@link #refresh()} reads the
 * source again.
 */
public final class FlatSlider extends AbstractSliderButton {
  private final Component label;
  private final double min;
  private final double max;
  private final DoubleSupplier source;
  private final DoubleConsumer sink;
  private final DoubleFunction<String> format;
  private int accent = Theme.ACCENT;
  private boolean marked;

  public FlatSlider(
      int x,
      int y,
      int w,
      Component label,
      double min,
      double max,
      DoubleSupplier source,
      DoubleConsumer sink,
      DoubleFunction<String> format) {
    super(x, y, w, 20, label, 0);
    this.label = label;
    this.min = min;
    this.max = max;
    this.source = source;
    this.sink = sink;
    this.format = format;
    refresh();
  }

  public FlatSlider accent(int color) {
    this.accent = color;
    return this;
  }

  /** Shows a keyframe diamond next to the label (a keyframe sits at the playhead). */
  public void setMarked(boolean marked) {
    this.marked = marked;
  }

  public Component label() {
    return label;
  }

  /** Reads the value from the source again (unless the user is dragging it). */
  public void refresh() {
    double v = source.getAsDouble();
    value = max > min ? Math.max(0, Math.min(1, (v - min) / (max - min))) : 0;
    updateMessage();
  }

  private double current() {
    return min + value * (max - min);
  }

  @Override
  protected void updateMessage() {
    setMessage(Component.empty().append(label).append(": " + format.apply(current())));
  }

  @Override
  protected void applyValue() {
    sink.accept(current());
  }

  @Override
  public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    Font font = Minecraft.getInstance().font;
    int x0 = getX();
    int y0 = getY();
    int x1 = x0 + getWidth();
    boolean hot = active && isHoveredOrFocused();
    int lx = x0 + 2;
    if (marked) {
      Draw.diamond(g, x0 + 4, y0 + 4, 3, accent);
      lx = x0 + 10;
    }
    String v = format.apply(current());
    Draw.label(g, font, v, x1 - 2 - font.width(v), y0 + 1, hot ? Theme.TEXT : Theme.DIM);
    Draw.label(
        g,
        font,
        Draw.fit(font, label.getString(), x1 - lx - font.width(v) - 8),
        lx,
        y0 + 1,
        active ? Theme.TEXT : Theme.FAINT);
    int ty = y0 + 14;
    g.fill(x0 + 2, ty, x1 - 2, ty + 2, Theme.TRACK_BG);
    int kx = x0 + 2 + (int) Math.round(value * (getWidth() - 4));
    g.fill(x0 + 2, ty, kx, ty + 2, active ? accent : Theme.FAINT);
    Draw.round(g, kx - 2, ty - 2, kx + 3, ty + 4, 1, hot ? 0xFFFFFFFF : Theme.TEXT);
  }
}
