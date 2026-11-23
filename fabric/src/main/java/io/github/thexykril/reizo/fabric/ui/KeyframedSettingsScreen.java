/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.core.effects.PropertyTracks;
import io.github.thexykril.reizo.fabric.playback.ReplayEditor;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.settings.KeyframedSetting;
import io.github.thexykril.reizo.fabric.settings.KeyframedSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import io.github.thexykril.reizo.fabric.ui.kit.Draw;
import io.github.thexykril.reizo.fabric.ui.kit.DragField;
import io.github.thexykril.reizo.fabric.ui.kit.FlatButton;
import io.github.thexykril.reizo.fabric.ui.kit.KitField;
import io.github.thexykril.reizo.fabric.ui.kit.KitScreen;
import io.github.thexykril.reizo.fabric.ui.kit.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Keyframes for game and mod settings, like Rewind's mod properties: pick a setting (Minecraft's
 * video settings, and every OneConfig mod setting when OneConfig is installed), then change it to
 * set a keyframe at the playhead. Numbers blend between keyframes; switches flip at each one. The
 * replay puts the player's own values back when it closes.
 */
public final class KeyframedSettingsScreen extends KitScreen {
  static final long TOLERANCE_MS = SettingEdits.TOLERANCE_MS;
  private static final int PANEL_WIDTH = 210;
  private static final int ROW_HEIGHT = 17;
  private int listY;

  private final Screen parent;
  private final List<KeyframedSetting> all = new ArrayList<>();
  private final List<KeyframedSetting> shown = new ArrayList<>();
  private final List<FlatButton> rows = new ArrayList<>();
  private KitField search;
  private int scroll;
  private int rowCount;
  private int controlY;
  private String selectedId;
  private DragField slider;
  private FlatButton toggle;
  private FlatButton removeButton;
  private FlatButton clearButton;

  public KeyframedSettingsScreen(Screen parent) {
    super(Component.translatable("reizo.settings_track.title"));
    this.parent = parent;
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
    sidePanel(PANEL_WIDTH);
    int x = innerX();
    int w = innerW();
    search =
        addRenderableWidget(
            new KitField(font, x, contentY(), w, 16, Component.translatable("reizo.settings_track.search"))
                .placeholder(Component.translatable("reizo.settings_track.search")));
    search.setValue(query);
    search.setResponder(q -> filter());
    listY = contentY() + 16 + GAP;
    rowCount = Math.max(3, (footerY() - listY - 64) / ROW_HEIGHT);
    rows.clear();
    for (int i = 0; i < rowCount; i++) {
      int index = i;
      rows.add(
          addRenderableWidget(
              new FlatButton(
                      x,
                      listY + i * ROW_HEIGHT,
                      w,
                      ROW_HEIGHT - 2,
                      Component.empty(),
                      FlatButton.Style.GHOST,
                      b -> select(index))
                  .alignLeft()
                  .selectedWhen(() -> index + scroll < shown.size() && shown.get(index + scroll).id().equals(selectedId))));
    }
    controlY = listY + rowCount * ROW_HEIGHT + 16;
    int third = (w - 2 * GAP) / 3;
    removeButton = button(x, footerY(), third, Component.translatable("reizo.effects.remove"), this::removeHere);
    clearButton =
        button(x + third + GAP, footerY(), third, Component.translatable("reizo.settings_track.clear"), this::clearTrack);
    accentButton(x + 2 * (third + GAP), footerY(), w - 2 * (third + GAP), Component.translatable("gui.done"), this::onClose);
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
    SettingEdits.breakUndoGroup();
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
              new FlatButton(
                      innerX(),
                      controlY,
                      innerW(),
                      ROW_H - 2,
                      Component.translatable("reizo.settings_track.value"),
                      FlatButton.Style.ROW,
                      b -> edit(s, shownValue(s) != 0 ? 0 : 1))
                  .switchWhen(() -> shownValue(s) != 0));
    } else {
      slider =
          addRenderableWidget(
              new DragField(
                      innerX(),
                      controlY,
                      innerW(),
                      Component.translatable("reizo.settings_track.value"),
                      s.min(),
                      s.max(),
                      (s.max() - s.min()) / 200.0,
                      s.min(),
                      () -> shownValue(s),
                      v -> edit(s, v),
                      s::format)
                  .accent(Theme.TRACK_SETTINGS)
                  .markedWhen(() -> ReplayEditor.settings().nearest(s.id(), playhead(), TOLERANCE_MS) >= 0));
    }
    refreshControl();
  }

  private void refreshRows() {
    PropertyTracks tracks = ReplayEditor.settings();
    for (int i = 0; i < rows.size(); i++) {
      FlatButton b = rows.get(i);
      int index = scroll + i;
      b.visible = index < shown.size();
      if (!b.visible) {
        continue;
      }
      KeyframedSetting s = shown.get(index);
      String mark = tracks.has(s.id()) ? "◆ " : "";
      String text = mark + s.group() + " · " + s.label();
      b.setMessage(Component.literal(text));
    }
  }

  private void refreshControl() {
    KeyframedSetting s = selected();
    if (s == null) {
      removeButton.active = false;
      clearButton.active = false;
      return;
    }
    removeButton.active = ReplayEditor.settings().nearest(s.id(), playhead(), TOLERANCE_MS) >= 0;
    clearButton.active = ReplayEditor.settings().has(s.id());
  }

  private static double shownValue(KeyframedSetting s) {
    return SettingEdits.shownValue(s);
  }

  private static long playhead() {
    return SettingEdits.playhead();
  }

  /** Sets a keyframe for {@code s} at the playhead (or edits the one there). */
  void edit(KeyframedSetting s, double value) {
    SettingEdits.edit(s, value, toggle == null);
    refreshControl();
    refreshRows();
  }

  private void removeHere() {
    KeyframedSetting s = selected();
    if (s != null && SettingEdits.removeHere(s)) {
      refreshControl();
      refreshRows();
    }
  }

  private void clearTrack() {
    KeyframedSetting s = selected();
    if (s != null && ReplayEditor.settings().has(s.id())) {
      ReplayEditor.recordUndo();
      SettingEdits.breakUndoGroup();
      ReplayEditor.settings().removeProperty(s.id());
      ReplayEditor.settingsChanged();
      refreshControl();
      refreshRows();
    }
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
    if (mouseX < cardX1() && mouseY >= listY && mouseY < listY + rowCount * ROW_HEIGHT) {
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
  protected void renderCard(GuiGraphics g, int mouseX, int mouseY) {
    if (shown.isEmpty()) {
      Draw.label(
          g, font, Component.translatable("reizo.settings_track.none").getString(), innerX() + 2, listY + 4, Theme.FAINT);
    }
    KeyframedSetting s = selected();
    if (s != null) {
      Draw.label(
          g, font, Draw.fit(font, s.group() + " · " + s.label(), innerW()), innerX(), controlY - 12, Theme.TEXT);
      int count = ReplayEditor.settings().keyframes(s.id()).size();
      boolean at = ReplayEditor.settings().nearest(s.id(), playhead(), TOLERANCE_MS) >= 0;
      Component info =
          Component.translatable(
              at ? "reizo.settings_track.at_keyframe" : "reizo.settings_track.between",
              ReplayHud.time(playhead()),
              count);
      Draw.wrap(g, font, info, innerX(), controlY + 22, innerW(), Theme.DIM);
    }
  }

  @Override
  public void onClose() {
    minecraft.setScreen(parent);
  }
}
