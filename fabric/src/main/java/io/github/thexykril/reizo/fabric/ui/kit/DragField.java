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

  /** Axis colours for X, Y and Z fields. */
  private static int axisColor(String label) {
    return switch (label) {
      case "X" -> 0xFFE5484D;
      case "Y" -> 0xFF46C46A;
      case "Z" -> 0xFF4C8DFF;
      default -> 0;
    };
  }

  @Override
  public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    Font font = Minecraft.getInstance().font;
    int x0 = getX();
    int y0 = getY();
    int x1 = x0 + getWidth();
    int y1 = y0 + getHeight();
    boolean hot = active && (isHoveredOrFocused() || dragging);
    double v = value();
    String text = format.apply(v);
    int tw = Draw.width(font, text);
    String name = label.getString();
    int axis = axisColor(name);
    boolean bounded = max > min && max - min < 1e6;
    int ty = y0 + (getHeight() - 8) / 2;
    if (axis != 0 || !bounded) {
      // A number box: the axis letter in its colour, or the label, then the value.
      Draw.round(g, x0, y0, x1, y1, 3, hot ? Theme.HOVER : Theme.RAISED);
      int lx = x0 + 5;
      if (marked.getAsBoolean()) {
        Draw.diamond(g, x0 + 6, y0 + 8, 2, accent);
        lx = x0 + 12;
      }
      if (axis != 0) {
        Draw.label(g, font, name, lx, ty, axis);
        int vx = lx + Draw.width(font, name) + 4;
        Draw.label(g, font, Draw.fit(font, text, x1 - vx - 3), vx, ty, Theme.TEXT);
      } else {
        Draw.label(g, font, text, x1 - 5 - tw, ty, Theme.TEXT);
        Draw.label(
            g,
            font,
            Draw.fit(font, name, x1 - lx - tw - 10),
            lx,
            ty,
            active ? Theme.DIM : Theme.FAINT);
      }
      return;
    }
    // A slider row: label, a thin track with a knob, the value.
    if (hot) {
      Draw.round(g, x0 - 2, y0, x1 + 2, y1, 3, Theme.alpha(Theme.HOVER, 0x90));
    }
    int lx = x0;
    if (marked.getAsBoolean()) {
      Draw.diamond(g, x0 + 2, y0 + 8, 2, accent);
      lx = x0 + 7;
    }
    int labelW = Math.max(24, getWidth() * 2 / 5);
    Draw.label(
        g,
        font,
        Draw.fit(font, name, x0 + labelW - lx - 3),
        lx,
        ty,
        active ? (hot ? Theme.TEXT : Theme.DIM) : Theme.FAINT);
    int valueW = Math.max(tw, 18);
    Draw.label(g, font, text, x1 - tw, ty, Theme.TEXT);
    int t0 = x0 + labelW + 3;
    int t1 = x1 - valueW - 6;
    if (t1 - t0 < 8) {
      return;
    }
    int my = (y0 + y1) / 2;
    double f = (clamp(v) - min) / (max - min);
    int kx = t0 + (int) Math.round(f * (t1 - t0));
    Draw.round(g, t0, my - 1, t1, my + 1, 1, Theme.LINE);
    // Fill from zero when the range spans it (a bipolar slider), else from the start.
    double origin = min < 0 && max > 0 ? 0 : min;
    double rf = (origin - min) / (max - min);
    int rx = t0 + (int) Math.round(rf * (t1 - t0));
    if (kx != rx) {
      Draw.round(g, Math.min(kx, rx), my - 1, Math.max(kx, rx), my + 1, 1, accent);
    }
    Draw.round(g, kx - 3, my - 3, kx + 3, my + 3, 3, active ? 0xFFFFFFFF : Theme.DIM);
  }

  @Override
  protected void updateWidgetNarration(NarrationElementOutput output) {
    output.add(
        NarratedElementType.TITLE,
        Component.empty().append(label).append(": " + format.apply(source.getAsDouble())));
  }
}
