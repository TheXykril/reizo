/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.core.effects.EffectTrack;
import io.github.thexykril.reizo.core.effects.Grade;
import io.github.thexykril.reizo.fabric.playback.ReplayEditor;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.playback.ReplayServer;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Effects for the running replay: exposure, contrast, saturation, temperature, vignette, bloom,
 * depth of field (strength and focus distance in blocks) and letterbox. A change sets an effect
 * keyframe at the current replay time (or edits the keyframe there); between keyframes values
 * blend. The panel sits on the left so the replay stays visible.
 */
public final class EffectsScreen extends Screen {
  /** Keyframes this close to the playhead are edited instead of adding another. */
  static final long TOLERANCE_MS = 100;

  private static final int PANEL_WIDTH = 150;

  private final Screen parent;
  private Button removeButton;
  private Button letterboxButton;

  public EffectsScreen(Screen parent) {
    super(Component.translatable("reizo.effects.title"));
    this.parent = parent;
  }

  /** One slider for one grade value. */
  private final class ValueSlider extends AbstractSliderButton {
    private final String key;
    private final float min;
    private final float max;
    private final Function<Grade, Float> get;
    private final BiFunction<Grade, Float, Grade> set;

    ValueSlider(
        int x,
        int y,
        String key,
        float min,
        float max,
        Function<Grade, Float> get,
        BiFunction<Grade, Float, Grade> set) {
      super(x, y, PANEL_WIDTH - 8, rowHeight, Component.empty(), 0);
      this.key = key;
      this.min = min;
      this.max = max;
      this.get = get;
      this.set = set;
      refresh();
    }

    void refresh() {
      value = (get.apply(ReplayEditor.gradeNow()) - min) / (max - min);
      updateMessage();
    }

    private float current() {
      return (float) (min + value * (max - min));
    }

    @Override
    protected void updateMessage() {
      setMessage(
          Component.translatable(
              key, String.format(Locale.ROOT, "%.2f", current()).replace("-0.00", "0.00")));
    }

    @Override
    protected void applyValue() {
      edit(g -> set.apply(g, current()));
    }
  }

  private final java.util.List<ValueSlider> sliders = new java.util.ArrayList<>();
  private long lastEditMs;
  private int rowHeight = 20;
  private int infoY;

  @Override
  protected void init() {
    if (ReplayPlayback.current() == null || ReplayEditor.project() == null) {
      onClose();
      return;
    }
    sliders.clear();
    // Eight sliders, the letterbox button and the bottom row; rows shrink on short windows.
    int step = Math.max(16, Math.min(22, (height - 22 - 30) / 9));
    rowHeight = step - 2;
    int x = 4;
    int y = 22;
    sliders.add(
        slider(x, y, "reizo.effects.exposure", -3, 3, Grade::exposure, Grade::withExposure));
    y += step;
    sliders.add(slider(x, y, "reizo.effects.contrast", 0, 2, Grade::contrast, Grade::withContrast));
    y += step;
    sliders.add(
        slider(x, y, "reizo.effects.saturation", 0, 2, Grade::saturation, Grade::withSaturation));
    y += step;
    sliders.add(
        slider(
            x, y, "reizo.effects.temperature", -1, 1, Grade::temperature, Grade::withTemperature));
    y += step;
    sliders.add(slider(x, y, "reizo.effects.vignette", 0, 1, Grade::vignette, Grade::withVignette));
    y += step;
    sliders.add(slider(x, y, "reizo.effects.bloom", 0, 1, Grade::bloom, Grade::withBloom));
    y += step;
    sliders.add(slider(x, y, "reizo.effects.dof", 0, 1, Grade::dof, Grade::withDof));
    y += step;
    sliders.add(slider(x, y, "reizo.effects.focus", 0.5f, 64, Grade::focus, Grade::withFocus));
    y += step;
    letterboxButton =
        addRenderableWidget(
            Button.builder(Component.empty(), b -> cycleLetterbox())
                .bounds(x, y, PANEL_WIDTH - 8, rowHeight)
                .build());
    y += step + 4;
    removeButton =
        addRenderableWidget(
            Button.builder(Component.translatable("reizo.effects.remove"), b -> removeHere())
                .bounds(x, y, (PANEL_WIDTH - 10) / 2, 20)
                .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(x + (PANEL_WIDTH - 10) / 2 + 2, y, (PANEL_WIDTH - 10) / 2, 20)
            .build());
    infoY = y + 26;
    refresh();
  }

  private ValueSlider slider(
      int x,
      int y,
      String key,
      float min,
      float max,
      Function<Grade, Float> get,
      BiFunction<Grade, Float, Grade> set) {
    return addRenderableWidget(new ValueSlider(x, y, key, min, max, get, set));
  }

  private long playhead() {
    ReplayServer server = ReplayPlayback.current();
    return server == null ? 0 : Math.min(server.replayTimeMs(), server.script().durationMs());
  }

  /** The keyframe time edited now: one near the playhead, or the playhead. */
  private long editTime() {
    long near = ReplayEditor.effects().nearest(playhead(), TOLERANCE_MS);
    return near >= 0 ? near : playhead();
  }

  /** Sets the keyframe at the edit time to {@code change} applied to the grade shown now. */
  void edit(java.util.function.UnaryOperator<Grade> change) {
    // One undo step per slider drag: changes less than a moment apart belong together.
    long now = System.currentTimeMillis();
    if (now - lastEditMs > 700) {
      ReplayEditor.recordUndo();
    }
    lastEditMs = now;
    EffectTrack track = ReplayEditor.effects();
    long t = editTime();
    track.put(t, change.apply(track.at(t)));
    ReplayEditor.effectsChanged();
    refreshButtons();
  }

  private void cycleLetterbox() {
    float now = ReplayEditor.gradeNow().letterbox();
    float[] all = Grade.LETTERBOXES;
    int i = 0;
    while (i < all.length && Math.abs(all[i] - now) > 0.01f) {
      i++;
    }
    float next = all[(i + 1) % all.length];
    lastEditMs = 0;
    edit(g -> g.withLetterbox(next));
  }

  private void removeHere() {
    long near = ReplayEditor.effects().nearest(playhead(), TOLERANCE_MS);
    if (near >= 0) {
      ReplayEditor.recordUndo();
      lastEditMs = 0;
      ReplayEditor.effects().remove(near);
      ReplayEditor.effectsChanged();
      refresh();
    }
  }

  private void refresh() {
    for (ValueSlider s : sliders) {
      s.refresh();
    }
    refreshButtons();
  }

  private void refreshButtons() {
    float lb = ReplayEditor.gradeNow().letterbox();
    letterboxButton.setMessage(
        Component.translatable(
            "reizo.effects.letterbox",
            lb <= 0
                ? Component.translatable("options.off")
                : Component.literal(String.format(Locale.ROOT, "%.2f:1", lb))));
    removeButton.active = ReplayEditor.effects().nearest(playhead(), TOLERANCE_MS) >= 0;
  }

  @Override
  public void tick() {
    if (ReplayPlayback.current() == null) {
      onClose();
      return;
    }
    // Follow the replay while it plays, unless a slider is being dragged.
    if (getFocused() == null || !isDragging()) {
      refresh();
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    g.fill(0, 0, PANEL_WIDTH, height, 0xA0000000);
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    super.render(g, mouseX, mouseY, partialTick);
    g.drawString(font, title, 4, 6, 0xFFFFFFFF);
    int count = ReplayEditor.effects().size();
    long near = ReplayEditor.effects().nearest(playhead(), TOLERANCE_MS);
    Component info =
        Component.translatable(
            near >= 0 ? "reizo.effects.at_keyframe" : "reizo.effects.between",
            ReplayHud.time(playhead()),
            count);
    if (infoY + font.lineHeight <= height) {
      g.drawWordWrap(font, info, 4, infoY, PANEL_WIDTH - 8, 0xFFA0A0A0);
    }
  }

  @Override
  public void onClose() {
    minecraft.setScreen(parent);
  }
}
