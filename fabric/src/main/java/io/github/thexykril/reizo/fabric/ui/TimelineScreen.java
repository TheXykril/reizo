/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.core.camera.CameraKeyframe;
import io.github.thexykril.reizo.core.format.RecordingMetadata;
import io.github.thexykril.reizo.core.timeline.KeyframeEdits;
import io.github.thexykril.reizo.core.timeline.TimelineView;
import io.github.thexykril.reizo.fabric.playback.ReplayEditor;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.playback.ReplayServer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * The replay timeline editor: a panel at the bottom of the screen over the playing replay, with a
 * time ruler, the recording's markers, the camera keyframes and the export region. The replay keeps
 * playing behind it.
 *
 * <p>Mouse: click the ruler to seek, click or drag keyframes (Shift adds to the selection), drag on
 * an empty part of the camera track to box-select, double-click a keyframe to go to it, wheel to
 * zoom, Shift+wheel to scroll, click the overview bar to jump there. Keys: Space play/pause, N add
 * keyframe, Delete remove, Ctrl+Z / Ctrl+Y undo and redo, Ctrl+C / Ctrl+V copy and paste at the
 * playhead, Ctrl+A select all, I / O set the export region (Shift clears), F show everything, ← →
 * step one second (Shift: five).
 */
public final class TimelineScreen extends Screen {
  private static final int PANEL_BG = 0xD0101014;
  private static final int TRACK_BG = 0xFF1E1E24;
  private static final int RULER_BG = 0xFF26262E;
  private static final int TICK = 0xFF6A6A78;
  private static final int TEXT = 0xFFFFFFFF;
  private static final int DIM = 0xFFA0A0B0;
  private static final int PLAYHEAD = 0xFFE5484D;
  private static final int MARKER = 0xFFF5A524;
  private static final int KEYFRAME = 0xFF4CC9F0;
  private static final int SELECTED = 0xFFFFFFFF;
  private static final int PATH_LINE = 0x804CC9F0;
  private static final int REGION = 0x3080FF80;
  private static final int REGION_EDGE = 0xFF80FF80;
  private static final int BOX = 0x40FFFFFF;

  static final int PANEL_HEIGHT = 92;
  private static final int LABEL_WIDTH = 46;
  private static final int HIT_PX = 4;

  private enum Drag {
    NONE,
    SEEK,
    KEYFRAMES,
    BOX,
    OVERVIEW
  }

  private TimelineView view;
  private final Set<Long> selection = new LinkedHashSet<>();
  private List<CameraKeyframe> clipboard = List.of();

  private Drag drag = Drag.NONE;
  private long dragStartTime;
  private long dragAppliedDelta;
  private boolean dragRecorded;
  private double dragX;
  private Button playButton;
  private Button pathButton;

  // Layout, in GUI pixels; set in init().
  private int panelY;
  private int left;
  private int right;
  private int rulerY;
  private int markerY;
  private int cameraY;
  private int overviewY;

  public TimelineScreen() {
    super(Component.translatable("reizo.timeline.title"));
  }

  @Override
  protected void init() {
    ReplayServer server = ReplayPlayback.current();
    long duration = server == null ? 1 : server.script().durationMs();
    if (view == null || view.durationMs() != Math.max(1, duration)) {
      view = new TimelineView(duration);
    }
    List<Button> row = new ArrayList<>();
    playButton = button(row, "", b -> togglePlay());
    button(row, "reizo.timeline.add", b -> addKeyframe());
    button(row, "reizo.timeline.delete", b -> deleteSelection());
    pathButton = button(row, "", b -> ReplayEditor.togglePath());
    button(row, "reizo.timeline.smooth", b -> ReplayEditor.cycleSmoothing());
    button(row, "reizo.timeline.in", b -> ReplayEditor.setRegion(true, playhead()));
    button(row, "reizo.timeline.out", b -> ReplayEditor.setRegion(false, playhead()));
    button(row, "reizo.timeline.undo", b -> undo());
    button(row, "reizo.timeline.redo", b -> redo());
    button(
        row,
        "reizo.timeline.effects",
        b -> Minecraft.getInstance().setScreen(new EffectsScreen(this)));
    button(
        row,
        "reizo.timeline.settings",
        b -> Minecraft.getInstance().setScreen(new KeyframedSettingsScreen(this)));
    button(
        row,
        "reizo.timeline.export",
        b -> Minecraft.getInstance().setScreen(new ExportSettingsScreen(this)));
    updateLabels();
    // Buttons as wide as their labels, wrapped onto more rows on narrow windows.
    int[] widths = new int[row.size()];
    int rows = 1;
    int lineWidth = 4;
    for (int i = 0; i < row.size(); i++) {
      widths[i] = Math.max(20, font.width(row.get(i).getMessage()) + 10);
      if (lineWidth + widths[i] > width - 4) {
        rows++;
        lineWidth = 4;
      }
      lineWidth += widths[i] + 2;
    }
    panelY = height - PANEL_HEIGHT - (rows - 1) * 18;
    int x = 4;
    int y = panelY + 4;
    for (int i = 0; i < row.size(); i++) {
      if (x + widths[i] > width - 4) {
        x = 4;
        y += 18;
      }
      row.get(i).setRectangle(widths[i], 16, x, y);
      x += widths[i] + 2;
    }
    left = 4 + LABEL_WIDTH;
    right = width - 6;
    rulerY = y + 20;
    markerY = rulerY + 13;
    cameraY = markerY + 14;
    overviewY = cameraY + 22;
    view.setWidth(right - left);
  }

  private Button button(List<Button> row, String key, Button.OnPress onPress) {
    Button b =
        addRenderableWidget(
            Button.builder(key.isEmpty() ? Component.empty() : Component.translatable(key), onPress)
                .build());
    row.add(b);
    return b;
  }

  private void updateLabels() {
    ReplayServer server = ReplayPlayback.current();
    boolean running = server != null && server.clock().isRunning();
    playButton.setMessage(Component.literal(running ? "❚❚" : "▶"));
    pathButton.setMessage(
        Component.translatable(
            ReplayEditor.isPathPlaying() ? "reizo.timeline.path_on" : "reizo.timeline.path_off"));
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void tick() {
    if (ReplayPlayback.current() == null) {
      onClose();
      return;
    }
    // Keep the selection valid after undo, redo or keyframe keys.
    Set<Long> existing = new LinkedHashSet<>();
    for (CameraKeyframe k : ReplayEditor.path().keyframes()) {
      existing.add(k.timeMs());
    }
    if (drag != Drag.KEYFRAMES) {
      selection.retainAll(existing);
    }
    updateLabels();
  }

  // --- Rendering ---------------------------------------------------------------------------

  @Override
  public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    // No blur or dimming: the replay stays visible above the panel.
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    ReplayServer server = ReplayPlayback.current();
    if (server == null) {
      return;
    }
    if (drag == Drag.NONE && server.clock().isRunning()) {
      view.ensureVisible(playhead());
    }
    g.fill(0, panelY, width, height, PANEL_BG);
    super.render(g, mouseX, mouseY, partialTick);

    g.drawString(font, Component.translatable("reizo.timeline.markers"), 4, markerY + 2, DIM);
    g.drawString(font, Component.translatable("reizo.timeline.camera"), 4, cameraY + 5, DIM);
    g.fill(left, rulerY, right, rulerY + 12, RULER_BG);
    g.fill(left, markerY, right, markerY + 12, TRACK_BG);
    g.fill(left, cameraY, right, cameraY + 18, TRACK_BG);

    g.enableScissor(left, rulerY, right, overviewY);
    renderRuler(g);
    renderRegion(g, rulerY, overviewY - 2);
    renderMarkers(g);
    // Effect keyframes: small purple squares along the bottom of the marker track.
    for (var k : ReplayEditor.effects().keyframes()) {
      int ex = (int) Math.round(left + view.timeToX(k.timeMs()));
      g.fill(ex - 2, markerY + 8, ex + 2, markerY + 12, 0xFFB070FF);
    }
    // Setting keyframes: small teal squares along the top of the marker track.
    for (var k : ReplayEditor.settings().keyframes()) {
      int sx = (int) Math.round(left + view.timeToX(k.timeMs()));
      g.fill(sx - 2, markerY, sx + 2, markerY + 4, 0xFF40D0C0);
    }
    renderKeyframes(g);
    if (drag == Drag.BOX) {
      int a = (int) (left + view.timeToX(dragStartTime));
      int b = mouseX;
      g.fill(Math.min(a, b), cameraY, Math.max(a, b), cameraY + 18, BOX);
    }
    long head = drag == Drag.SEEK ? view.xToTime(mouseX - left) : playhead();
    int hx = (int) Math.round(left + view.timeToX(head));
    g.fill(hx, rulerY, hx + 1, cameraY + 18, PLAYHEAD);
    g.disableScissor();

    renderOverview(g);
    String time =
        ReplayHud.time(head)
            + " / "
            + ReplayHud.time(view.durationMs())
            + "  ·  "
            + ReplayHud.speed(server.clock().speed())
            + "x";
    g.drawString(font, time, 4, overviewY - 1, TEXT, false);
  }

  private void renderRuler(GuiGraphics g) {
    long step = view.tickStepMs(font.width("00:00") + 8);
    long first = (view.startMs() / step) * step;
    for (long t = first; t <= view.endMs(); t += step) {
      int x = (int) Math.round(left + view.timeToX(t));
      g.fill(x, rulerY + 8, x + 1, rulerY + 12, TICK);
      g.drawString(font, label(t, step), x + 2, rulerY + 2, DIM, false);
    }
  }

  private static String label(long t, long step) {
    String base = ReplayHud.time(t);
    if (step < 1000) {
      return base + "." + (t % 1000) / 100;
    }
    return base;
  }

  private void renderRegion(GuiGraphics g, int top, int bottom) {
    long in = ReplayEditor.inMs();
    long out = ReplayEditor.outMs();
    if (in < 0 && out < 0) {
      return;
    }
    int a = (int) Math.round(left + view.timeToX(Math.max(0, in)));
    int b = (int) Math.round(left + view.timeToX(out < 0 ? view.durationMs() : out));
    g.fill(a, top, b, bottom, REGION);
    if (in >= 0) {
      g.fill(a, top, a + 1, bottom, REGION_EDGE);
    }
    if (out >= 0) {
      g.fill(b - 1, top, b, bottom, REGION_EDGE);
    }
  }

  private void renderMarkers(GuiGraphics g) {
    RecordingMetadata meta = ReplayPlayback.metadata();
    if (meta == null) {
      return;
    }
    for (RecordingMetadata.Marker m : meta.markers) {
      int x = (int) Math.round(left + view.timeToX(m.timeMs));
      g.fill(x - 1, markerY + 1, x + 2, markerY + 11, MARKER);
    }
  }

  private void renderKeyframes(GuiGraphics g) {
    List<CameraKeyframe> keys = ReplayEditor.path().keyframes();
    int mid = cameraY + 9;
    if (keys.size() > 1) {
      int a = (int) Math.round(left + view.timeToX(keys.get(0).timeMs()));
      int b = (int) Math.round(left + view.timeToX(keys.get(keys.size() - 1).timeMs()));
      g.fill(a, mid, b, mid + 1, PATH_LINE);
    }
    for (CameraKeyframe k : keys) {
      int x = (int) Math.round(left + view.timeToX(k.timeMs()));
      boolean sel = selection.contains(k.timeMs());
      // A small diamond.
      int c = sel ? SELECTED : KEYFRAME;
      g.fill(x - 1, mid - 4, x + 2, mid + 5, c);
      g.fill(x - 3, mid - 2, x + 4, mid + 3, c);
      g.fill(x - 2, mid - 3, x + 3, mid + 4, c);
    }
  }

  private void renderOverview(GuiGraphics g) {
    int x0 = left + 80;
    int x1 = right;
    if (x1 - x0 < 20) {
      return;
    }
    g.fill(x0, overviewY + 1, x1, overviewY + 5, TRACK_BG);
    double scale = (double) (x1 - x0) / view.durationMs();
    int a = x0 + (int) (view.startMs() * scale);
    int b = Math.max(a + 2, x0 + (int) (view.endMs() * scale));
    g.fill(a, overviewY, b, overviewY + 6, TICK);
    int h = x0 + (int) (playhead() * scale);
    g.fill(h, overviewY, h + 1, overviewY + 6, PLAYHEAD);
  }

  // --- Mouse ------------------------------------------------------------------------------

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    if (super.mouseClicked(event, doubleClick)) {
      return true;
    }
    double mx = event.x();
    double my = event.y();
    if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || mx < left || mx > right) {
      return false;
    }
    long t = view.xToTime(mx - left);
    boolean shift = event.hasShiftDown() || Minecraft.getInstance().hasShiftDown();
    if (my >= rulerY && my < rulerY + 12) {
      drag = Drag.SEEK;
      return true;
    }
    if (my >= markerY && my < markerY + 12) {
      RecordingMetadata.Marker m = markerAt(mx);
      if (m != null) {
        ReplayPlayback.seekTo(m.timeMs);
      }
      return true;
    }
    if (my >= cameraY && my < cameraY + 18) {
      CameraKeyframe k = keyframeAt(mx);
      if (k == null) {
        if (!shift) {
          selection.clear();
        }
        drag = Drag.BOX;
        dragStartTime = t;
        return true;
      }
      if (doubleClick) {
        ReplayPlayback.seekTo(k.timeMs());
        return true;
      }
      if (shift) {
        if (!selection.remove(k.timeMs())) {
          selection.add(k.timeMs());
        }
      } else if (!selection.contains(k.timeMs())) {
        selection.clear();
        selection.add(k.timeMs());
      }
      if (selection.contains(k.timeMs())) {
        drag = Drag.KEYFRAMES;
        dragX = mx;
        dragAppliedDelta = 0;
        dragRecorded = false;
      }
      return true;
    }
    if (my >= overviewY - 1 && my < overviewY + 7 && mx >= left + 80) {
      drag = Drag.OVERVIEW;
      centerOverview(mx);
      return true;
    }
    return false;
  }

  @Override
  public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
    switch (drag) {
      case KEYFRAMES -> {
        long delta = Math.round((event.x() - dragX) * view.msPerPixel());
        long step = delta - dragAppliedDelta;
        if (step != 0) {
          if (!dragRecorded) {
            ReplayEditor.recordUndo();
            dragRecorded = true;
          }
          long before = selection.stream().mapToLong(Long::longValue).min().orElse(0);
          Set<Long> moved =
              KeyframeEdits.move(ReplayEditor.path(), selection, step, view.durationMs());
          if (!moved.isEmpty()) {
            long actual = moved.stream().mapToLong(Long::longValue).min().orElse(0) - before;
            dragAppliedDelta += actual;
            selection.clear();
            selection.addAll(moved);
          }
        }
        return true;
      }
      case OVERVIEW -> {
        centerOverview(event.x());
        return true;
      }
      case SEEK, BOX -> {
        return true;
      }
      default -> {
        return super.mouseDragged(event, dx, dy);
      }
    }
  }

  @Override
  public boolean mouseReleased(MouseButtonEvent event) {
    Drag was = drag;
    drag = Drag.NONE;
    switch (was) {
      case SEEK -> ReplayPlayback.seekTo(view.xToTime(event.x() - left));
      case KEYFRAMES -> {
        if (dragRecorded) {
          ReplayEditor.changed();
        }
      }
      case BOX ->
          selection.addAll(
              KeyframeEdits.between(
                  ReplayEditor.path(), dragStartTime, view.xToTime(event.x() - left)));
      default -> {
        return super.mouseReleased(event);
      }
    }
    return true;
  }

  @Override
  public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
    if (my < panelY) {
      return false;
    }
    boolean shift = Minecraft.getInstance().hasShiftDown();
    if (horizontal != 0 || shift) {
      double amount = horizontal != 0 ? horizontal : vertical;
      view.scrollPx(-amount * 20);
    } else if (vertical != 0) {
      view.zoom(Math.pow(1.25, vertical), Math.max(0, Math.min(mx - left, right - left)));
    }
    return true;
  }

  private void centerOverview(double mx) {
    int x0 = left + 80;
    double t = (mx - x0) / (right - x0) * view.durationMs();
    view.scrollPx((t - (view.startMs() + view.spanMs() / 2.0)) / view.msPerPixel());
  }

  private CameraKeyframe keyframeAt(double mx) {
    CameraKeyframe best = null;
    double bestD = HIT_PX + 1;
    for (CameraKeyframe k : ReplayEditor.path().keyframes()) {
      double d = Math.abs(left + view.timeToX(k.timeMs()) - mx);
      if (d <= HIT_PX && d < bestD) {
        best = k;
        bestD = d;
      }
    }
    return best;
  }

  private RecordingMetadata.Marker markerAt(double mx) {
    RecordingMetadata meta = ReplayPlayback.metadata();
    if (meta == null) {
      return null;
    }
    RecordingMetadata.Marker best = null;
    double bestD = HIT_PX + 1;
    for (RecordingMetadata.Marker m : meta.markers) {
      double d = Math.abs(left + view.timeToX(m.timeMs) - mx);
      if (d <= HIT_PX && d < bestD) {
        best = m;
        bestD = d;
      }
    }
    return best;
  }

  // --- Keys -------------------------------------------------------------------------------

  @Override
  public boolean keyPressed(KeyEvent event) {
    int key = event.key();
    Minecraft mc = Minecraft.getInstance();
    boolean ctrl = io.github.thexykril.reizo.fabric.compat.Compat.controlDown(event) || mc.hasControlDown();
    boolean shift = event.hasShiftDown() || mc.hasShiftDown();
    if (ctrl) {
      switch (key) {
        case InputConstants.KEY_Z -> {
          if (shift) {
            redo();
          } else {
            undo();
          }
          return true;
        }
        case InputConstants.KEY_Y -> {
          redo();
          return true;
        }
        case InputConstants.KEY_C -> {
          clipboard = KeyframeEdits.copy(ReplayEditor.path(), selection);
          return true;
        }
        case InputConstants.KEY_V -> {
          paste();
          return true;
        }
        case InputConstants.KEY_A -> {
          selectAll();
          return true;
        }
        default -> {}
      }
    }
    switch (key) {
      case InputConstants.KEY_SPACE -> togglePlay();
      case InputConstants.KEY_N -> addKeyframe();
      case InputConstants.KEY_DELETE, InputConstants.KEY_BACKSPACE -> deleteSelection();
      case InputConstants.KEY_I -> ReplayEditor.setRegion(true, shift ? -1 : playhead());
      case InputConstants.KEY_O -> ReplayEditor.setRegion(false, shift ? -1 : playhead());
      case InputConstants.KEY_F -> view.showAll();
      case InputConstants.KEY_LEFT -> ReplayPlayback.seekBy(shift ? -5000 : -1000);
      case InputConstants.KEY_RIGHT -> ReplayPlayback.seekBy(shift ? 5000 : 1000);
      default -> {
        return super.keyPressed(event);
      }
    }
    return true;
  }

  // --- Actions (package-visible for tests) ------------------------------------------------

  long playhead() {
    ReplayServer server = ReplayPlayback.current();
    return server == null ? 0 : Math.min(server.replayTimeMs(), server.script().durationMs());
  }

  void togglePlay() {
    ReplayPlayback.togglePause();
    updateLabels();
  }

  void addKeyframe() {
    CameraKeyframe k = ReplayEditor.addKeyframe(Minecraft.getInstance());
    if (k != null) {
      selection.clear();
      selection.add(k.timeMs());
    }
  }

  void deleteSelection() {
    if (selection.isEmpty()) {
      ReplayEditor.removeNearestKeyframe();
      return;
    }
    ReplayEditor.recordUndo();
    KeyframeEdits.remove(ReplayEditor.path(), selection);
    selection.clear();
    ReplayEditor.changed();
  }

  void selectAll() {
    selection.clear();
    for (CameraKeyframe k : ReplayEditor.path().keyframes()) {
      selection.add(k.timeMs());
    }
  }

  void paste() {
    if (clipboard.isEmpty()) {
      return;
    }
    ReplayEditor.recordUndo();
    Set<Long> pasted =
        KeyframeEdits.paste(ReplayEditor.path(), clipboard, playhead(), view.durationMs());
    selection.clear();
    selection.addAll(pasted);
    ReplayEditor.changed();
  }

  void undo() {
    ReplayEditor.undo();
  }

  void redo() {
    ReplayEditor.redo();
  }

  Set<Long> selection() {
    return selection;
  }

  TimelineView view() {
    return view;
  }

  /** GUI x of a time on the timeline, for tests. */
  public int xOf(long timeMs) {
    return (int) Math.round(left + view.timeToX(timeMs));
  }

  /** GUI y of the middle of the camera track, for tests. */
  public int cameraTrackY() {
    return cameraY + 9;
  }

  /** GUI y of the middle of the ruler, for tests. */
  public int rulerTrackY() {
    return rulerY + 6;
  }

  /** Selected keyframe times, for tests. */
  public Set<Long> selectedTimes() {
    return Set.copyOf(selection);
  }
}
