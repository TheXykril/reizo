/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.core.effects.PropertyTracks;
import io.github.thexykril.reizo.fabric.playback.ReplayEditor;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.playback.ReplayServer;
import io.github.thexykril.reizo.fabric.settings.KeyframedSetting;
import io.github.thexykril.reizo.fabric.settings.KeyframedSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Keyframes for game and mod settings, like Rewind's mod properties: pick a setting (Minecraft's
 * video settings, and every OneConfig mod setting when OneConfig is installed), then change it to
 * set a keyframe at the playhead. Numbers blend between keyframes; switches flip at each one. The
 * replay puts the player's own values back when it closes.
 */
public final class KeyframedSettingsScreen extends Screen {
  static final long TOLERANCE_MS = 100;
  private static final int PANEL_WIDTH = 200;
  private static final int ROW_HEIGHT = 18;
  private static final int LIST_Y = 38;

  private final Screen parent;
  private final List<KeyframedSetting> all = new ArrayList<>();
  private final List<KeyframedSetting> shown = new ArrayList<>();
  private final List<Button> rows = new ArrayList<>();
  private EditBox search;
  private int scroll;
  private int rowCount;
  private int controlY;
  private String selectedId;
  private ValueSlider slider;
  private Button toggle;
  private Button removeButton;
  private Button clearButton;
  private long lastEditMs;

  public KeyframedSettingsScreen(Screen parent) {
    super(Component.translatable("reizo.settings_track.title"));
    this.parent = parent;
  }

  /** Slider for a number setting, 0..1 mapped onto the setting's range. */
  private final class ValueSlider extends AbstractSliderButton {
    private final KeyframedSetting setting;

    ValueSlider(int x, int y, KeyframedSetting setting) {
      super(x, y, PANEL_WIDTH - 8, 20, Component.empty(), 0);
      this.setting = setting;
      refresh();
    }

    void refresh() {
      value = (shownValue(setting) - setting.min()) / (setting.max() - setting.min());
      updateMessage();
    }

    private double current() {
      return setting.min() + value * (setting.max() - setting.min());
    }

    @Override
    protected void updateMessage() {
      setMessage(Component.literal(String.format(Locale.ROOT, "%.2f", current())));
    }

    @Override
    protected void applyValue() {
      edit(setting, current());
    }
  }

  @Override
  protected void init() {
    if (ReplayPlayback.current() == null || ReplayEditor.project() == null) {
      onClose();
      return;
    }
    all.clear();
    all.addAll(KeyframedSettings.all());
    String query = search == null ? "" : search.getValue();
    search =
        addRenderableWidget(
            new EditBox(
                font,
                4,
                18,
                PANEL_WIDTH - 8,
                16,
                Component.translatable("reizo.settings_track.search")));
    search.setHint(Component.translatable("reizo.settings_track.search"));
    search.setValue(query);
    search.setResponder(q -> filter());
    rowCount = Math.max(3, (height - LIST_Y - 100) / ROW_HEIGHT);
    rows.clear();
    for (int i = 0; i < rowCount; i++) {
      int index = i;
      rows.add(
          addRenderableWidget(
              Button.builder(Component.empty(), b -> select(index))
                  .bounds(4, LIST_Y + i * ROW_HEIGHT, PANEL_WIDTH - 8, ROW_HEIGHT - 2)
                  .build()));
    }
    controlY = LIST_Y + rowCount * ROW_HEIGHT + 16;
    int third = (PANEL_WIDTH - 12) / 3;
    removeButton =
        addRenderableWidget(
            Button.builder(Component.translatable("reizo.effects.remove"), b -> removeHere())
                .bounds(4, controlY + 24, third, 20)
                .build());
    clearButton =
        addRenderableWidget(
            Button.builder(Component.translatable("reizo.settings_track.clear"), b -> clearTrack())
                .bounds(4 + third + 2, controlY + 24, third, 20)
                .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(4 + 2 * (third + 2), controlY + 24, third, 20)
            .build());
    filter();
  }

  private void filter() {
    String q = search.getValue().trim().toLowerCase(Locale.ROOT);
    shown.clear();
    for (KeyframedSetting s : all) {
      String text = (s.group() + " " + s.label() + " " + s.id()).toLowerCase(Locale.ROOT);
      if (q.isEmpty() || text.contains(q)) {
        shown.add(s);
      }
    }
    scroll = Math.max(0, Math.min(scroll, shown.size() - rowCount));
    if (selectedId == null && !shown.isEmpty()) {
      selectedId = shown.get(0).id();
    }
    rebuildControl();
    refreshRows();
  }

  /** Selects a setting by id (used by the rows and by tests). */
  public void select(String id) {
    selectedId = id;
    lastEditMs = 0;
    rebuildControl();
    refreshRows();
  }

  private void select(int row) {
    int i = scroll + row;
    if (i < shown.size()) {
      select(shown.get(i).id());
    }
  }

  private KeyframedSetting selected() {
    return selectedId == null ? null : KeyframedSettings.find(selectedId);
  }

  private void rebuildControl() {
    if (slider != null) {
      removeWidget(slider);
      slider = null;
    }
    if (toggle != null) {
      removeWidget(toggle);
      toggle = null;
    }
    KeyframedSetting s = selected();
    if (s == null) {
      return;
    }
    if (s.kind() == PropertyTracks.Kind.TOGGLE) {
      toggle =
          addRenderableWidget(
              Button.builder(Component.empty(), b -> edit(s, shownValue(s) != 0 ? 0 : 1))
                  .bounds(4, controlY, PANEL_WIDTH - 8, 20)
                  .build());
    } else {
      slider = addRenderableWidget(new ValueSlider(4, controlY, s));
    }
    refreshControl();
  }

  private void refreshRows() {
    PropertyTracks tracks = ReplayEditor.settings();
    for (int i = 0; i < rows.size(); i++) {
      Button b = rows.get(i);
      int index = scroll + i;
      b.visible = index < shown.size();
      if (!b.visible) {
        continue;
      }
      KeyframedSetting s = shown.get(index);
      String mark = tracks.has(s.id()) ? "◆ " : "";
      String text = mark + s.group() + " · " + s.label();
      String fitted = font.plainSubstrByWidth(text, PANEL_WIDTH - 16);
      if (!fitted.equals(text)) {
        fitted = font.plainSubstrByWidth(text, PANEL_WIDTH - 22) + "…";
      }
      b.setMessage(Component.literal(fitted));
      b.active = !s.id().equals(selectedId);
    }
  }

  private void refreshControl() {
    KeyframedSetting s = selected();
    if (s == null) {
      removeButton.active = false;
      clearButton.active = false;
      return;
    }
    if (slider != null) {
      slider.refresh();
    }
    if (toggle != null) {
      toggle.setMessage(Component.translatable(shownValue(s) != 0 ? "options.on" : "options.off"));
    }
    removeButton.active = ReplayEditor.settings().nearest(s.id(), playhead(), TOLERANCE_MS) >= 0;
    clearButton.active = ReplayEditor.settings().has(s.id());
  }

  /** The value at the playhead: the track's, or the live value without one. */
  private double shownValue(KeyframedSetting s) {
    double v = ReplayEditor.settings().valueAt(s.id(), playhead());
    return Double.isNaN(v) ? s.get() : v;
  }

  private long playhead() {
    ReplayServer server = ReplayPlayback.current();
    return server == null ? 0 : Math.min(server.replayTimeMs(), server.script().durationMs());
  }

  /** Sets a keyframe for {@code s} at the playhead (or edits the one there). */
  void edit(KeyframedSetting s, double value) {
    long now = System.currentTimeMillis();
    if (now - lastEditMs > 700) {
      ReplayEditor.recordUndo();
    }
    lastEditMs = now;
    PropertyTracks tracks = ReplayEditor.settings();
    long near = tracks.nearest(s.id(), playhead(), TOLERANCE_MS);
    tracks.put(s.id(), s.kind(), near >= 0 ? near : playhead(), KeyframedSettings.clamp(s, value));
    ReplayEditor.settingsChanged();
    if (toggle != null) {
      lastEditMs = 0;
    }
    refreshControl();
    refreshRows();
  }

  private void removeHere() {
    KeyframedSetting s = selected();
    if (s == null) {
      return;
    }
    long near = ReplayEditor.settings().nearest(s.id(), playhead(), TOLERANCE_MS);
    if (near >= 0) {
      ReplayEditor.recordUndo();
      lastEditMs = 0;
      ReplayEditor.settings().remove(s.id(), near);
      ReplayEditor.settingsChanged();
      refreshControl();
      refreshRows();
    }
  }

  private void clearTrack() {
    KeyframedSetting s = selected();
    if (s != null && ReplayEditor.settings().has(s.id())) {
      ReplayEditor.recordUndo();
      lastEditMs = 0;
      ReplayEditor.settings().removeProperty(s.id());
      ReplayEditor.settingsChanged();
      refreshControl();
      refreshRows();
    }
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
    if (mouseX < PANEL_WIDTH && mouseY >= LIST_Y && mouseY < LIST_Y + rowCount * ROW_HEIGHT) {
      int max = Math.max(0, shown.size() - rowCount);
      scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(dy)));
      refreshRows();
      return true;
    }
    return super.mouseScrolled(mouseX, mouseY, dx, dy);
  }

  @Override
  public void tick() {
    if (ReplayPlayback.current() == null) {
      onClose();
      return;
    }
    if (getFocused() == null || !isDragging()) {
      refreshControl();
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
    if (shown.isEmpty()) {
      g.drawString(
          font, Component.translatable("reizo.settings_track.none"), 6, LIST_Y + 4, 0xFFA0A0A0);
    }
    KeyframedSetting s = selected();
    if (s != null) {
      g.drawString(
          font,
          font.plainSubstrByWidth(s.group() + " · " + s.label(), PANEL_WIDTH - 8),
          4,
          controlY - 11,
          0xFFFFFFFF);
      int count = ReplayEditor.settings().keyframes(s.id()).size();
      boolean at = ReplayEditor.settings().nearest(s.id(), playhead(), TOLERANCE_MS) >= 0;
      Component info =
          Component.translatable(
              at ? "reizo.settings_track.at_keyframe" : "reizo.settings_track.between",
              ReplayHud.time(playhead()),
              count);
      g.drawWordWrap(font, info, 4, controlY + 50, PANEL_WIDTH - 8, 0xFFA0A0A0);
    }
  }

  @Override
  public void onClose() {
    minecraft.setScreen(parent);
  }
}
