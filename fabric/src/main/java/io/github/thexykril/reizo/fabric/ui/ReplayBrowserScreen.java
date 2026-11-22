/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.core.format.RecordingMetadata;
import io.github.thexykril.reizo.core.storage.RecordingStorage;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
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
public final class ReplayBrowserScreen extends Screen {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Browser");
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());
  private static final int GRAY = 0xFFA0A0A0;
  private static final int WHITE = 0xFFFFFFFF;
  private static final int RED = 0xFFFF6060;

  private final Screen parent;
  private RecordingList list;
  private Button play;
  private Button rename;
  private Button delete;
  private Component error;

  public ReplayBrowserScreen(Screen parent) {
    super(Component.translatable("reizo.browser.title"));
    this.parent = parent;
  }

  @Override
  protected void init() {
    list = addRenderableWidget(new RecordingList(minecraft, width, height - 32 - 64, 32, 36));
    reload();

    int y = height - 52;
    play =
        addRenderableWidget(
            Button.builder(Component.translatable("reizo.browser.play"), b -> playSelected())
                .bounds(width / 2 - 154, y, 100, 20)
                .build());
    rename =
        addRenderableWidget(
            Button.builder(Component.translatable("reizo.browser.rename"), b -> renameSelected())
                .bounds(width / 2 - 50, y, 100, 20)
                .build());
    delete =
        addRenderableWidget(
            Button.builder(Component.translatable("reizo.browser.delete"), b -> deleteSelected())
                .bounds(width / 2 + 54, y, 100, 20)
                .build());
    addRenderableWidget(
        Button.builder(Component.translatable("reizo.browser.open_folder"), b -> openFolder())
            .bounds(width / 2 - 154, y + 24, 100, 20)
            .build());
    addRenderableWidget(
        Button.builder(
                Component.translatable("reizo.browser.settings"),
                b -> minecraft.setScreen(new SettingsScreen(this)))
            .bounds(width / 2 - 50, y + 24, 100, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(width / 2 + 54, y + 24, 100, 20)
            .build());
    updateButtons();
  }

  private void reload() {
    List<RecordingStorage.Entry> entries;
    try {
      entries = storage().listRecordings();
    } catch (IOException e) {
      LOGGER.warn("Could not list recordings", e);
      error = Component.translatable("reizo.browser.list_failed");
      entries = List.of();
    }
    list.set(entries);
  }

  private void updateButtons() {
    RecordingList.Row row = list.getSelected();
    boolean any = row != null;
    play.active = any && row.playable();
    rename.active = any;
    delete.active = any;
  }

  @Override
  public void tick() {
    updateButtons();
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    super.render(g, mouseX, mouseY, partialTick);
    g.drawCenteredString(font, title, width / 2, 12, WHITE);
    if (list.children().isEmpty()) {
      g.drawCenteredString(
          font, Component.translatable("reizo.browser.empty"), width / 2, height / 2 - 20, GRAY);
    }
    if (error != null) {
      g.drawCenteredString(font, error, width / 2, height - 64, RED);
    }
  }

  @Override
  public void onClose() {
    minecraft.setScreen(parent);
  }

  // ---- test hooks ----

  /** The listed recordings, in list order. */
  public List<RecordingStorage.Entry> entries() {
    return list.children().stream().map(r -> r.entry).toList();
  }

  /** Selects a row like a click would. */
  public void select(int index) {
    list.setSelected(list.children().get(index));
    updateButtons();
  }

  /** Presses Play. */
  public void pressPlay() {
    play.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
  }

  // ---- actions ----

  private void playSelected() {
    RecordingList.Row row = list.getSelected();
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
    RecordingList.Row row = list.getSelected();
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
    RecordingList.Row row = list.getSelected();
    if (row == null) {
      return;
    }
    minecraft.setScreen(
        new ConfirmScreen(
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
            Component.translatable("gui.cancel")));
  }

  private void openFolder() {
    try {
      Files.createDirectories(storage().root());
      Util.getPlatform().openPath(storage().root());
    } catch (IOException e) {
      LOGGER.warn("Could not open the recordings folder", e);
    }
  }

  private static RecordingStorage storage() {
    return RecorderClient.get().storage();
  }

  // ---- list ----

  final class RecordingList extends ObjectSelectionList<RecordingList.Row> {
    RecordingList(Minecraft mc, int width, int height, int y, int itemHeight) {
      super(mc, width, height, y, itemHeight);
    }

    void set(List<RecordingStorage.Entry> entries) {
      Row selected = getSelected();
      String selectedPath = selected == null ? null : selected.entry.path().toString();
      clearEntries();
      for (RecordingStorage.Entry e : entries) {
        Row row = new Row(e);
        addEntry(row);
        if (e.path().toString().equals(selectedPath)) {
          setSelected(row);
        }
      }
    }

    @Override
    public int getRowWidth() {
      return Math.min(320, width - 20);
    }

    final class Row extends ObjectSelectionList.Entry<Row> {
      final RecordingStorage.Entry entry;
      private long lastClick;

      Row(RecordingStorage.Entry entry) {
        this.entry = entry;
      }

      boolean playable() {
        return entry.metadata().protocolVersion == SharedConstants.getProtocolVersion();
      }

      @Override
      public Component getNarration() {
        return Component.literal(entry.metadata().name);
      }

      @Override
      public void renderContent(
          GuiGraphics g, int mouseX, int mouseY, boolean hovered, float partialTick) {
        RecordingMetadata m = entry.metadata();
        int x = getContentX();
        int y = getContentY();
        g.drawString(font, m.name, x + 2, y + 1, WHITE);
        String server = m.serverName != null ? m.serverName : String.valueOf(m.serverType);
        String line2 =
            DATE.format(Instant.ofEpochMilli(m.startedAtEpochMs))
                + "  ·  "
                + duration(m.durationMs)
                + "  ·  "
                + server;
        g.drawString(font, line2, x + 2, y + 12, GRAY);
        Component line3;
        if (!playable()) {
          line3 =
              Component.translatable("reizo.browser.other_version", m.minecraftVersion)
                  .withStyle(ChatFormatting.RED);
        } else {
          line3 =
              Component.literal(
                  m.minecraftVersion
                      + "  ·  "
                      + size(entry.sizeBytes())
                      + (m.markers.isEmpty() ? "" : "  ·  " + m.markers.size() + " markers"));
        }
        g.drawString(font, line3, x + 2, y + 23, GRAY);
      }

      @Override
      public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        setSelected(this);
        updateButtons();
        long now = Util.getMillis();
        if (doubleClick || now - lastClick < 250) {
          playSelected();
        }
        lastClick = now;
        return true;
      }
    }
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
