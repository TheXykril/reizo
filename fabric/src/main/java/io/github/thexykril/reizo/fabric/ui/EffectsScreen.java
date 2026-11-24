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
import io.github.thexykril.reizo.fabric.ui.kit.Draw;
import io.github.thexykril.reizo.fabric.ui.kit.DragField;
import io.github.thexykril.reizo.fabric.ui.kit.FlatButton;
import io.github.thexykril.reizo.fabric.ui.kit.KitScreen;
import io.github.thexykril.reizo.fabric.ui.kit.Theme;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Effects for the running replay: exposure, contrast, saturation, temperature, vignette, bloom,
 * depth of field (strength and focus distance in blocks) and letterbox. A change sets an effect
 * keyframe at the current replay time (or edits the keyframe there); between keyframes values
 * blend. The panel sits on the left so the replay stays visible.
 */
public final class EffectsScreen extends KitScreen {
  /** Keyframes this close to the playhead are edited instead of adding another. */
  static final long TOLERANCE_MS = EffectEdits.TOLERANCE_MS;

  private static final int PANEL_WIDTH = 170;

  private final Screen parent;
  private FlatButton removeButton;
  private int colorY;
  private int lensY;
  private int infoY;

  public EffectsScreen(Screen parent) {
    super(Component.translatable("reizo.effects.title"));
    this.parent = parent;
  }

  @Override
  protected void init() {
    if (ReplayPlayback.current() == null || ReplayEditor.project() == null) {
      onClose();
      return;
    }
    sidePanel(PANEL_WIDTH);
    int x = innerX();
    int w = innerW();
    int y = contentY();
    colorY = y;
    y += 12;
    y = field(x, y, w, "exposure", -3, 3, 0, Grade::exposure, Grade::withExposure);
    y = field(x, y, w, "contrast", 0, 2, 1, Grade::contrast, Grade::withContrast);
    y = field(x, y, w, "saturation", 0, 2, 1, Grade::saturation, Grade::withSaturation);
    y = field(x, y, w, "temperature", -1, 1, 0, Grade::temperature, Grade::withTemperature);
    y += GAP;
    lensY = y;
    y += 12;
    y = field(x, y, w, "vignette", 0, 1, 0, Grade::vignette, Grade::withVignette);
    y = field(x, y, w, "bloom", 0, 1, 0, Grade::bloom, Grade::withBloom);
    y = field(x, y, w, "dof", 0, 1, 0, Grade::dof, Grade::withDof);
    y = field(x, y, w, "focus", 0, 64, Grade.AUTO_FOCUS, Grade::focus, Grade::withFocus);
    addRenderableWidget(
        new FlatButton(
                x,
                y,
                w,
                ROW_H - 2,
                Component.translatable("reizo.effects.letterbox_row"),
                FlatButton.Style.ROW,
                b -> EffectEdits.cycleLetterbox())
            .value(EffectsScreen::letterboxName));
    y += ROW_H + GAP;
    infoY = y;
    int half = (w - GAP) / 2;
    removeButton = button(x, footerY(), half, Component.translatable("reizo.effects.remove"), this::removeHere);
    accentButton(x + half + GAP, footerY(), w - half - GAP, Component.translatable("gui.done"), this::onClose);
    refreshButtons();
  }

  private int field(
      int x,
      int y,
      int w,
      String name,
      float min,
      float max,
      float reset,
      Function<Grade, Float> get,
      BiFunction<Grade, Float, Grade> set) {
    addRenderableWidget(
        new DragField(
                x,
                y,
                w,
                Component.translatable("reizo.look." + name),
                min,
                max,
                (max - min) / 200.0,
                reset,
                () -> get.apply(ReplayEditor.gradeNow()),
                v -> EffectEdits.edit(g -> set.apply(g, (float) v)),
                v -> String.format(Locale.ROOT, "%.2f", v).replace("-0.00", "0.00"))
            .accent(Theme.TRACK_LOOK)
            .markedWhen(EffectEdits::atKeyframe));
    return y + 18;
  }

  static String letterboxName() {
    float lb = ReplayEditor.gradeNow().letterbox();
    return lb <= 0
        ? Component.translatable("options.off").getString()
        : String.format(Locale.ROOT, "%.2f:1", lb);
  }

  private long playhead() {
    ReplayServer server = ReplayPlayback.current();
    return server == null ? 0 : Math.min(server.replayTimeMs(), server.script().durationMs());
  }

  /** Sets the keyframe at the edit time to {@code change} applied to the grade shown now. */
  void edit(java.util.function.UnaryOperator<Grade> change) {
    EffectEdits.edit(change);
    refreshButtons();
  }

  private void removeHere() {
    EffectEdits.removeHere();
    refreshButtons();
  }

  private void refreshButtons() {
    if (removeButton != null) {
      removeButton.active = ReplayEditor.effects().nearest(playhead(), TOLERANCE_MS) >= 0;
    }
  }

  @Override
  public void tick() {
    if (ReplayPlayback.current() == null) {
      onClose();
      return;
    }
    refreshButtons();
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  protected void renderCard(GuiGraphics g, int mouseX, int mouseY) {
    sectionLabel(g, Component.translatable("reizo.section.color").getString(), colorY);
    sectionLabel(g, Component.translatable("reizo.section.lens").getString(), lensY);
    int count = ReplayEditor.effects().size();
    long near = ReplayEditor.effects().nearest(playhead(), TOLERANCE_MS);
    Component info =
        Component.translatable(
            near >= 0 ? "reizo.effects.at_keyframe" : "reizo.effects.between",
            ReplayHud.time(playhead()),
            count);
    if (infoY + 10 < footerY()) {
      Draw.wrap(g, font, info, innerX(), infoY, innerW(), Theme.DIM);
    }
  }

  @Override
  public void onClose() {
    minecraft.setScreen(parent);
  }
}
