/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui.kit;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A number field changed by dragging, like an editor's drag-number box: the label on the left,
 * the value on the right, and for bounded values a faint bar showing where the value sits. Drag
 * left or right to change it (Shift: ten times finer), scroll to step it, double-click to reset
 * it. While dragging, the edits arrive continuously.
 */
public final class DragField extends AbstractWidget {
  private final Component label;
  private final double min;
  private final double max;
  private final double perPixel;
  private final double reset;
  private final DoubleSupplier source;
  private final DoubleConsumer sink;
  private final DoubleFunction<String> format;
  private int accent = Theme.ACCENT;
  private BooleanSupplier marked = () -> false;
  private Runnable commit = () -> {};
  private boolean dragging;
  private double dragValue;

  /**
   * @param perPixel value change per GUI pixel dragged
   * @param reset value a double-click sets
   */
  public DragField(
      int x,
      int y,
      int w,
      Component label,
      double min,
      double max,
      double perPixel,
      double reset,
      DoubleSupplier source,
      DoubleConsumer sink,
      DoubleFunction<String> format) {
    super(x, y, w, 16, label);
    this.label = label;
    this.min = min;
    this.max = max;
    this.perPixel = perPixel;
    this.reset = reset;
    this.source = source;
    this.sink = sink;
    this.format = format;
  }

  public DragField accent(int color) {
    this.accent = color;
    return this;
  }

  /** Runs when a drag ends (for example to save once instead of on every step). */
  public DragField onCommit(Runnable commit) {
    this.commit = commit;
    return this;
  }

  /** Shows a keyframe diamond while {@code marked} holds. */
  public DragField markedWhen(BooleanSupplier marked) {
    this.marked = marked;
    return this;
  }

  private double value() {
    return dragging ? dragValue : source.getAsDouble();
  }

  private double clamp(double v) {
    return Math.max(min, Math.min(max, v));
  }

  private void set(double v) {
    v = clamp(v);
    dragValue = v;
    sink.accept(v);
  }

  @Override
  public void onClick(MouseButtonEvent event, boolean doubleClick) {
    if (doubleClick) {
      set(reset);
      commit.run();
      return;
    }
    dragging = true;
    dragValue = source.getAsDouble();
  }

  @Override
  protected void onDrag(MouseButtonEvent event, double dx, double dy) {
    if (!dragging) {
      return;
    }
    double factor = Minecraft.getInstance().hasShiftDown() ? 0.1 : 1;
    set(dragValue + dx * perPixel * factor);
  }

  @Override
  public void onRelease(MouseButtonEvent event) {
    if (dragging) {
      dragging = false;
      commit.run();
    }
  }

  @Override
  public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
    if (!active || !visible || !isMouseOver(mx, my) || vertical == 0) {
      return false;
    }
    set(source.getAsDouble() + Math.signum(vertical) * perPixel * 10);
    commit.run();
    return true;
  }

  @Override
  public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    Font font = Minecraft.getInstance().font;
    int x0 = getX();
    int y0 = getY();
    int x1 = x0 + getWidth();
    int y1 = y0 + getHeight();
    boolean hot = active && (isHoveredOrFocused() || dragging);
    // Opaque, non-overlapping pieces: the GUI renderer may reorder overlapping translucent fills.
    int ground = hot ? Theme.HOVER : Theme.RAISED;
    double v = value();
    int fx = x0;
    if (max > min && max - min < 1e6) {
      fx = x0 + (int) Math.round((clamp(v) - min) / (max - min) * getWidth());
    }
    if (fx > x0 + 1 && fx < x1 - 1) {
      Draw.round(g, x0, y0, fx, y1, 3, Draw.mix(ground, accent, hot ? 0.38 : 0.26), true, false);
      Draw.round(g, fx, y0, x1, y1, 3, ground, false, true);
    } else {
      Draw.round(g, x0, y0, x1, y1, 3, fx >= x1 - 1 ? Draw.mix(ground, accent, hot ? 0.38 : 0.26) : ground);
    }
    int lx = x0 + 5;
    if (marked.getAsBoolean()) {
      Draw.diamond(g, x0 + 6, y0 + 8, 2, accent);
      lx = x0 + 12;
    }
    String text = format.apply(v);
    int tw = Draw.width(font, text);
    Draw.label(g, font, text, x1 - 5 - tw, y0 + 4, Theme.TEXT);
    Draw.label(
        g,
        font,
        Draw.fit(font, label.getString(), x1 - lx - tw - 10),
        lx,
        y0 + 4,
        active ? Theme.DIM : Theme.FAINT);
  }

  @Override
  protected void updateWidgetNarration(NarrationElementOutput output) {
    output.add(
        NarratedElementType.TITLE,
        Component.empty().append(label).append(": " + format.apply(source.getAsDouble())));
  }
}
