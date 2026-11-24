/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.core.format.RecordingMetadata;
import io.github.thexykril.reizo.core.storage.RecordingStorage;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.ui.kit.Draw;
import io.github.thexykril.reizo.fabric.ui.kit.FlatButton;
import io.github.thexykril.reizo.fabric.ui.kit.KitConfirmScreen;
import io.github.thexykril.reizo.fabric.ui.kit.KitScreen;
import io.github.thexykril.reizo.fabric.ui.kit.Theme;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lists saved recordings and plays, renames or deletes them. Opened from the title screen and the
 * pause menu.
 */
public final class ReplayBrowserScreen extends KitScreen implements ReplayBrowserView {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Browser");
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());
  private static final int ITEM_H = 36;

  private final Screen parent;
  private final List<Row> rows = new ArrayList<>();
  private int selected = -1;
  private int scroll;
  private int listY0;
  private int listY1;
  private FlatButton play;
  private FlatButton rename;
  private FlatButton delete;
  private Component error;
  private long lastClick;
  private int lastClickRow = -1;

  public ReplayBrowserScreen(Screen parent) {
    super(Component.translatable("reizo.browser.title"));
    this.parent = parent;
  }

  /**
   * The replay browser: OneConfig's Compose screen when OneConfig is installed (ADR 0017), else this
   * kit screen. {@code -Dreizo.kitScreens=true} forces the kit screens.
   */
  public static Screen create(Screen parent) {
    if (ExportSettingsScreen.composeScreens()) {
      try {
        return ComposeScreens.browser(parent);
      } catch (Throwable t) {
        LOGGER.warn("OneConfig's Compose UI is not usable; using Reizo's own screen", t);
      }
    }
    return new ReplayBrowserScreen(parent);
  }

  /** One recording in the list. */
  record Row(RecordingStorage.Entry entry) {
    boolean playable() {
      return ReplayBrowserScreen.playable(entry);
    }
  }

  /** Whether this Minecraft version can play the recording. */
  static boolean playable(RecordingStorage.Entry entry) {
    return entry.metadata().protocolVersion == SharedConstants.getProtocolVersion();
  }

  /** The second line of a row: when and where it was recorded. */
  static String recordedLine(RecordingMetadata m) {
    String server = m.serverName != null ? m.serverName : String.valueOf(m.serverType);
    return DATE.format(Instant.ofEpochMilli(m.startedAtEpochMs)) + "  ·  " + server;
  }

  /** The third line of a playable row: version, size and markers. */
  static String detailsLine(RecordingStorage.Entry entry) {
    RecordingMetadata m = entry.metadata();
    return m.minecraftVersion
        + "  ·  "
        + size(entry.sizeBytes())
        + (m.markers.isEmpty()
            ? ""
            : "  ·  " + m.markers.size() + (m.markers.size() == 1 ? " marker" : " markers"));
  }

  @Override
  protected void init() {
    card(420, height - 24);
    reload();
    int x = innerX();
    int w = innerW();
    listY0 = contentY();
    listY1 = footerY() - ROW_H - GAP * 2;
    int third = (w - GAP * 2) / 3;
    int y = footerY() - ROW_H - GAP;
    rename = button(x, y, third, Component.translatable("reizo.browser.rename"), this::renameSelected);
    delete =
        button(
            x + third + GAP, y, third, Component.translatable("reizo.browser.delete"), this::deleteSelected);
    play =
        accentButton(
            x + (third + GAP) * 2,
            y,
            w - (third + GAP) * 2,
            Component.translatable("reizo.browser.play"),
            this::playSelected);
    y = footerY();
    button(x, y, third, Component.translatable("reizo.browser.open_folder"), ReplayBrowserScreen::openFolder);
    button(
        x + third + GAP,
        y,
        third,
        Component.translatable("reizo.browser.settings"),
        () -> minecraft.setScreen(SettingsScreen.create(this)));
    button(
        x + (third + GAP) * 2,
        y,
        w - (third + GAP) * 2,
        Component.translatable("gui.done"),
        this::onClose);
    updateButtons();
  }

  private void reload() {
    String keep = selected >= 0 && selected < rows.size() ? rows.get(selected).entry.path().toString() : null;
    List<RecordingStorage.Entry> entries;
    try {
      entries = storage().listRecordings();
    } catch (IOException e) {
      LOGGER.warn("Could not list recordings", e);
      error = Component.translatable("reizo.browser.list_failed");
      entries = List.of();
    }
    rows.clear();
    selected = -1;
    for (RecordingStorage.Entry e : entries) {
      if (e.path().toString().equals(keep)) {
        selected = rows.size();
      }
      rows.add(new Row(e));
    }
  }

  private Row selectedRow() {
    return selected >= 0 && selected < rows.size() ? rows.get(selected) : null;
  }

  private void updateButtons() {
    Row row = selectedRow();
    boolean any = row != null;
    play.active = any && row.playable();
    rename.active = any;
    delete.active = any;
  }

  @Override
  public void tick() {
    updateButtons();
  }

  private int visibleRows() {
    return Math.max(1, (listY1 - listY0) / ITEM_H);
  }

  @Override
  protected void renderCard(GuiGraphics g, int mouseX, int mouseY) {
    int x0 = innerX();
    int x1 = x0 + innerW();
    Draw.round(g, x0, listY0, x1, listY1, 5, Theme.GROUND | 0xFF000000);
    if (rows.isEmpty()) {
      String empty = Component.translatable("reizo.browser.empty").getString();
      Draw.centered(g, font, empty, (x0 + x1) / 2, (listY0 + listY1) / 2 - 4, Theme.FAINT);
    }
    scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows())));
    for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
      int index = scroll + i;
      Row row = rows.get(index);
      int y = listY0 + 3 + i * ITEM_H;
      boolean hot = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ITEM_H - 2;
      boolean sel = index == selected;
      if (sel || hot) {
        Draw.round(g, x0 + 3, y, x1 - 3, y + ITEM_H - 3, 4, sel ? Draw.mix(Theme.RAISED, Theme.ACCENT, 0.25) : Theme.RAISED);
      }
      if (sel) {
        Draw.round(g, x0 + 3, y + 4, x0 + 5, y + ITEM_H - 7, 1, Theme.ACCENT);
      }
      renderRow(g, row, x0 + 10, y + 3, x1 - x0 - 20);
    }
    if (rows.size() > visibleRows()) {
      int track = listY1 - listY0 - 6;
      int thumb = Math.max(12, track * visibleRows() / rows.size());
      int ty = listY0 + 3 + (track - thumb) * scroll / Math.max(1, rows.size() - visibleRows());
      Draw.round(g, x1 - 5, ty, x1 - 2, ty + thumb, 1, Theme.FAINT);
    }
    if (error != null) {
      Draw.label(g, font, Draw.fit(font, error.getString(), innerW()), x0, listY1 + 2, Theme.DANGER);
    }
  }

  private void renderRow(GuiGraphics g, Row row, int x, int y, int w) {
    RecordingMetadata m = row.entry.metadata();
    String length = duration(m.durationMs);
    Draw.label(g, font, Draw.fit(font, m.name, w - Draw.width(font, length) - 8), x, y, Theme.TEXT);
    Draw.label(g, font, length, x + w - Draw.width(font, length), y, Theme.DIM);
    String server = m.serverName != null ? m.serverName : String.valueOf(m.serverType);
    String line2 = DATE.format(Instant.ofEpochMilli(m.startedAtEpochMs)) + "  ·  " + server;
    Draw.label(g, font, Draw.fit(font, line2, w), x, y + 10, Theme.DIM);
    if (!row.playable()) {
      String other = Component.translatable("reizo.browser.other_version", m.minecraftVersion).getString();
      Draw.label(g, font, Draw.fit(font, other, w), x, y + 20, Theme.DANGER);
    } else {
      String line3 =
          m.minecraftVersion
              + "  ·  "
              + size(row.entry.sizeBytes())
              + (m.markers.isEmpty()
            ? ""
            : "  ·  " + m.markers.size() + (m.markers.size() == 1 ? " marker" : " markers"));
      Draw.label(g, font, Draw.fit(font, line3, w), x, y + 20, Theme.FAINT);
    }
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    double mx = event.x();
    double my = event.y();
    if (mx >= innerX() && mx < innerX() + innerW() && my >= listY0 + 3 && my < listY1) {
      int index = scroll + (int) ((my - listY0 - 3) / ITEM_H);
      if (index < rows.size()) {
        long now = Util.getMillis();
        boolean again = index == lastClickRow && (doubleClick || now - lastClick < 250);
        selected = index;
        updateButtons();
        lastClick = now;
        lastClickRow = index;
        if (again) {
          playSelected();
        }
        return true;
      }
    }
    return super.mouseClicked(event, doubleClick);
  }

  @Override
  public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
    scroll = Math.max(0, scroll - (int) Math.signum(vertical));
    return true;
  }

  @Override
  public void onClose() {
    minecraft.setScreen(parent);
  }

  // ---- test hooks ----

  @Override
  public List<RecordingStorage.Entry> entries() {
    return rows.stream().map(Row::entry).toList();
  }

  @Override
  public void select(int index) {
    selected = index;
    updateButtons();
  }

  @Override
  public void pressPlay() {
    play.onPress(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0, 0));
  }

  // ---- actions ----

  private void playSelected() {
    Row row = selectedRow();
    if (row == null || !row.playable()) {
      return;
    }
    try {
      ReplayPlayback.start(minecraft, row.entry.path());
    } catch (IOException | RuntimeException e) {
      LOGGER.warn("Could not play {}", row.entry.path(), e);
      error = Component.translatable("reizo.browser.play_failed", String.valueOf(e.getMessage()));
      minecraft.setScreen(this);
    }
  }

  private void renameSelected() {
    Row row = selectedRow();
    if (row == null) {
      return;
    }
    minecraft.setScreen(
        new RenameScreen(
            this,
            row.entry.metadata().name,
            name -> {
              try {
                storage().rename(row.entry.path(), name);
              } catch (IOException | RuntimeException e) {
                LOGGER.warn("Rename failed", e);
                error = Component.translatable("reizo.browser.rename_failed");
              }
            }));
  }

  private void deleteSelected() {
    Row row = selectedRow();
    if (row == null) {
      return;
    }
    minecraft.setScreen(
        new KitConfirmScreen(
            yes -> {
              if (yes) {
                try {
                  storage().delete(row.entry.path());
                } catch (IOException | RuntimeException e) {
                  LOGGER.warn("Delete failed", e);
                  error = Component.translatable("reizo.browser.delete_failed");
                }
              }
              minecraft.setScreen(this);
            },
            Component.translatable("reizo.browser.delete_question"),
            Component.translatable("reizo.browser.delete_warning", row.entry.metadata().name),
            Component.translatable("reizo.browser.delete"),
            Component.translatable("gui.cancel"),
            true));
  }

  static void openFolder() {
    try {
      Files.createDirectories(storage().root());
      Util.getPlatform().openPath(storage().root());
    } catch (IOException e) {
      LOGGER.warn("Could not open the recordings folder", e);
    }
  }

  static RecordingStorage storage() {
    return RecorderClient.get().storage();
  }

  static String duration(int ms) {
    int s = Math.max(0, ms) / 1000;
    return s >= 3600
        ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
        : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
  }

  static String size(long bytes) {
    if (bytes < 1024 * 1024) {
      return Math.max(1, bytes / 1024) + " KB";
    }
    return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
  }
}
