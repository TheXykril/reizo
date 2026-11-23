/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.thexykril.reizo.core.camera.CameraKeyframe;
import io.github.thexykril.reizo.core.effects.Grade;
import io.github.thexykril.reizo.core.effects.PropertyTracks;
import io.github.thexykril.reizo.core.format.RecordingMetadata;
import io.github.thexykril.reizo.core.timeline.KeyframeEdits;
import io.github.thexykril.reizo.core.timeline.TimelineView;
import io.github.thexykril.reizo.fabric.compat.Compat;
import io.github.thexykril.reizo.fabric.playback.ReplayCamera;
import io.github.thexykril.reizo.fabric.playback.ReplayEditor;
import io.github.thexykril.reizo.fabric.playback.ReplayEntityHider;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.playback.ReplayServer;
import io.github.thexykril.reizo.fabric.settings.KeyframedSetting;
import io.github.thexykril.reizo.fabric.settings.KeyframedSettings;
import io.github.thexykril.reizo.fabric.ui.kit.Draw;
import io.github.thexykril.reizo.fabric.ui.kit.FlatButton;
import io.github.thexykril.reizo.fabric.ui.kit.FlatSlider;
import io.github.thexykril.reizo.fabric.ui.kit.Icons;
import io.github.thexykril.reizo.fabric.ui.kit.Theme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The replay editor (ADR 0015): docked panels around the playing replay. A top bar with the
 * transport, view mode, undo and Export; a tool rail; a Scene outliner of the replay's entities;
 * an Inspector with tabs for the selected keyframe, the look and the world; and a multi-track
 * timeline (markers, camera, look, world, entities, settings) with the export region. Side panels
 * fold away on narrow windows and with Tab. The replay keeps playing in the middle.
 *
 * <p>Timeline mouse: click the ruler to seek, click or drag camera keyframes (Shift adds to the
 * selection), drag on an empty part of the camera track to box-select, double-click a keyframe to
 * go to it, click a key on the other tracks to go to it, wheel to zoom, Shift+wheel to scroll,
 * click the overview bar to jump there. Keys: Space play/pause, N add keyframe, Delete remove,
 * Ctrl+Z / Ctrl+Y undo and redo, Ctrl+C / Ctrl+V copy and paste at the playhead, Ctrl+A select
 * all, I / O set the export region (Shift clears), F show everything, H hide the entity in view,
 * Tab show or hide the side panels, ← → step one second (Shift: five).
 */
public final class TimelineScreen extends Screen {
  static final int TOP_H = 20;
  private static final int RAIL_W = 20;
  private static final int OUTLINER_W = 118;
  private static final int INSPECTOR_W = 146;
  private static final int HEADER_H = 16;
  private static final int RULER_H = 12;
  private static final int LANE_H = 13;
  private static final int OVERVIEW_H = 10;
  private static final int LABEL_W = 62;
  private static final int ROW_H = 12;
  private static final int HIT_PX = 4;

  /** Panels the side panels can be folded with; kept while the editor reopens. */
  private static boolean panels = true;

  private static Tab tab = Tab.KEYFRAME;

  /** Look tab page: 0 colour, 1 lens. */
  private static int lookPage;

  private enum Drag {
    NONE,
    SEEK,
    KEYFRAMES,
    BOX,
    OVERVIEW
  }

  private enum Tab {
    KEYFRAME("reizo.inspector.keyframe"),
    LOOK("reizo.inspector.look"),
    WORLD("reizo.inspector.world");

    final String key;

    Tab(String key) {
      this.key = key;
    }
  }

  /** Timeline tracks, in display order. */
  private enum Track {
    MARKERS("reizo.timeline.markers", Theme.TRACK_MARKER),
    CAMERA("reizo.timeline.camera", Theme.TRACK_CAMERA),
    LOOK("reizo.track.look", Theme.TRACK_LOOK),
    WORLD("reizo.track.world", Theme.TRACK_WORLD),
    ENTITIES("reizo.track.entities", Theme.TRACK_ENTITY),
    SETTINGS("reizo.track.settings", Theme.TRACK_SETTINGS);

    final String key;
    final int color;

    Track(String key, int color) {
      this.key = key;
      this.color = color;
    }
  }

  private record Lane(Track track, int y) {}

  private static final Set<String> WORLD_IDS =
      Set.of("reizo:time_of_day", "reizo:rain", "reizo:thunder");

  private TimelineView view;
  private final Set<Long> selection = new LinkedHashSet<>();
  private List<CameraKeyframe> clipboard = List.of();

  private Drag drag = Drag.NONE;
  private long dragStartTime;
  private long dragAppliedDelta;
  private boolean dragRecorded;
  private double dragX;

  private FlatButton playButton;
  private FlatButton speedButton;
  private FlatButton letterboxButton;
  private final List<FlatSlider> sliders = new ArrayList<>();
  private String builtInspector = "";

  // Layout, in GUI pixels; set in init().
  private boolean showLeft;
  private boolean showRight;
  private boolean showTitle;
  private int timeTextX = -1;
  private int timelineY;
  private int headerY;
  private int left;
  private int right;
  private int rulerY;
  private int lanesY;
  private int overviewY;
  private final List<Lane> lanes = new ArrayList<>();
  private final List<Entity> sceneRows = new ArrayList<>();
  private int sceneScroll;

  public TimelineScreen() {
    super(Component.translatable("reizo.timeline.title"));
  }

  // --- Layout -----------------------------------------------------------------------------

  @Override
  protected void init() {
    ReplayServer server = ReplayPlayback.current();
    long duration = server == null ? 1 : server.script().durationMs();
    if (view == null || view.durationMs() != Math.max(1, duration)) {
      view = new TimelineView(duration);
    }
    showLeft = panels && width >= 560;
    showRight = panels && width >= 440;
    int timelineH = Math.max(92, Math.min(168, Math.round(height * 0.36f)));
    timelineY = height - timelineH;
    headerY = timelineY + 2;
    rulerY = headerY + HEADER_H;
    lanesY = rulerY + RULER_H + 1;
    overviewY = height - OVERVIEW_H + 2;
    left = LABEL_W + 4;
    right = width - 6;
    view.setWidth(right - left);

    initTopBar();
    initRail();
    initTimelineHeader();
    sliders.clear();
    if (showRight) {
      initInspector();
    }
    builtInspector = inspectorState();
    updateLabels();
  }

  private void initTopBar() {
    int x = 22;
    showTitle = width >= 520;
    if (showTitle) {
      x += Draw.width(font, "Reizo") + 10;
    }
    String[][] transport = {
      {"reizo.editor.prev_marker", "⏮"},
      {"reizo.editor.back", "«"},
      {"reizo.editor.play", "▶"},
      {"reizo.editor.forward", "»"},
      {"reizo.editor.next_marker", "⏭"},
    };
    for (String[] t : transport) {
      FlatButton b = icon(x, 2, 16, t[0], t[1], this::transport);
      if (t[0].equals("reizo.editor.play")) {
        playButton = b;
      }
      x += 17;
    }
    timeTextX = x + 4;
    x = timeTextX + Draw.width(font, "00:00 / 00:00") + 6;
    speedButton =
        addRenderableWidget(
            new FlatButton(
                x, 3, 24, 14, Component.literal("1x"), FlatButton.Style.TONAL, b -> stepSpeed()));
    x += 28;

    // Right group, anchored to the right edge.
    Component export = Component.translatable("reizo.timeline.export");
    int exportW = FlatButton.fitWidth(font, export, 7);
    int rx = width - 4 - exportW;
    addRenderableWidget(
        new FlatButton(
            rx,
            3,
            exportW,
            14,
            export,
            FlatButton.Style.ACCENT,
            b -> Minecraft.getInstance().setScreen(new ExportSettingsScreen(this))));
    rx -= 20;
    icon(rx, 2, 16, "reizo.timeline.redo", "redo", b -> redo());
    rx -= 17;
    icon(rx, 2, 16, "reizo.timeline.undo", "undo", b -> undo());

    // View mode segment, when it fits between the two groups.
    String[] modes = {"free", "pov", "follow", "path"};
    int[] widths = new int[modes.length];
    int total = 0;
    for (int i = 0; i < modes.length; i++) {
      widths[i] = FlatButton.fitWidth(font, viewLabel(modes[i]), 5);
      total += widths[i];
    }
    int vx = Math.max(x + 6, (width - total) / 2);
    if (vx + total <= rx - 10) {
      for (int i = 0; i < modes.length; i++) {
        String mode = modes[i];
        addRenderableWidget(
                new FlatButton(
                    vx, 3, widths[i], 14, viewLabel(mode), FlatButton.Style.TAB, b -> setView(mode)))
            .selectedWhen(() -> mode.equals(currentView()));
        vx += widths[i];
      }
    }
  }

  private static Component viewLabel(String mode) {
    return Component.translatable("reizo.editor.view." + mode);
  }

  private void initRail() {
    String[][] tools = {
      {"reizo.timeline.hide", "eye"},
      {"reizo.timeline.effects", "sparkle"},
      {"reizo.timeline.settings", "sliders"},
      {"reizo.editor.panels", "panels"},
    };
    int y = TOP_H + 4;
    for (String[] t : tools) {
      if (y + 16 > timelineY - 2) {
        break;
      }
      FlatButton b = icon(2, y, 16, t[0], t[1], this::railTool);
      if (t[0].equals("reizo.editor.panels")) {
        b.selectedWhen(() -> panels);
      }
      y += 18;
    }
  }

  private void initTimelineHeader() {
    String[][] tools = {
      {"reizo.timeline.add", "◆"},
      {"reizo.timeline.delete", "✕"},
      {"reizo.timeline.smooth", "wave"},
      {"reizo.timeline.in", "["},
      {"reizo.timeline.out", "]"},
    };
    int x = left;
    for (String[] t : tools) {
      icon(x, headerY, 14, t[0], t[1], this::headerTool);
      x += 15;
    }
    String[][] zoom = {
      {"reizo.editor.fit", "fit"},
      {"reizo.editor.zoom_in", "+"},
      {"reizo.editor.zoom_out", "−"},
    };
    int zx = right - 14;
    for (String[] z : zoom) {
      if (zx < x + 8) {
        break;
      }
      icon(zx, headerY, 14, z[0], z[1], this::headerTool);
      zx -= 15;
    }
  }

  private void initInspector() {
    int x0 = width - INSPECTOR_W + 4;
    int w = INSPECTOR_W - 8;
    int tw = w / Tab.values().length;
    int tx = x0;
    for (Tab t : Tab.values()) {
      addRenderableWidget(
              new FlatButton(
                  tx,
                  TOP_H + 3,
                  tw,
                  14,
                  Component.translatable(t.key),
                  FlatButton.Style.TAB,
                  b -> {
                    tab = t;
                    builtInspector = "";
                  }))
          .selectedWhen(() -> tab == t);
      tx += tw;
    }
    int y = TOP_H + 24;
    switch (tab) {
      case KEYFRAME -> {
        if (selection.size() == 1) {
          int half = (w - 2) / 2;
          y = TOP_H + 24 + 9 * 10 + 4;
          tonal(x0, y, half, "reizo.inspector.goto", b -> goToSelected());
          tonal(x0 + half + 2, y, half, "reizo.inspector.delete", b -> deleteSelection());
          y += 18;
        } else if (selection.isEmpty()) {
          y = TOP_H + 24;
          addRenderableWidget(
              new FlatButton(
                  x0,
                  y,
                  w,
                  16,
                  Component.translatable("reizo.inspector.add_here"),
                  FlatButton.Style.ACCENT,
                  b -> addKeyframe()));
          y += 20;
        } else {
          y = TOP_H + 24 + 14;
        }
        addRenderableWidget(
            new FlatButton(
                x0,
                y,
                w,
                16,
                Component.translatable("reizo.inspector.smoothing"),
                FlatButton.Style.TONAL,
                b -> ReplayEditor.cycleSmoothing()));
      }
      case LOOK -> {
        int half = w / 2;
        for (int p = 0; p < 2; p++) {
          int page = p;
          addRenderableWidget(
                  new FlatButton(
                      x0 + p * half,
                      y,
                      half,
                      14,
                      Component.translatable(p == 0 ? "reizo.look.color" : "reizo.look.lens"),
                      FlatButton.Style.TONAL,
                      b -> {
                        lookPage = page;
                        builtInspector = "";
                      }))
              .selectedWhen(() -> lookPage == page);
        }
        y += 20;
        if (lookPage == 0) {
          y = gradeSlider(x0, y, w, "exposure", -3, 3, Grade::exposure, Grade::withExposure);
          y = gradeSlider(x0, y, w, "contrast", 0, 2, Grade::contrast, Grade::withContrast);
          y = gradeSlider(x0, y, w, "saturation", 0, 2, Grade::saturation, Grade::withSaturation);
          y =
              gradeSlider(
                  x0, y, w, "temperature", -1, 1, Grade::temperature, Grade::withTemperature);
        } else {
          y = gradeSlider(x0, y, w, "vignette", 0, 1, Grade::vignette, Grade::withVignette);
          y = gradeSlider(x0, y, w, "bloom", 0, 1, Grade::bloom, Grade::withBloom);
          y = gradeSlider(x0, y, w, "dof", 0, 1, Grade::dof, Grade::withDof);
          y = gradeSlider(x0, y, w, "focus", 0.5f, 64, Grade::focus, Grade::withFocus);
        }
        letterboxButton =
            addRenderableWidget(
                new FlatButton(
                    x0,
                    y,
                    w,
                    16,
                    Component.translatable("reizo.look.letterbox"),
                    FlatButton.Style.TONAL,
                    b -> EffectEdits.cycleLetterbox()));
        y += 18;
        int half2 = (w - 2) / 2;
        tonal(x0, y, half2, "reizo.look.remove", b -> EffectEdits.removeHere());
        tonal(
            x0 + half2 + 2,
            y,
            half2,
            "reizo.inspector.open_settings",
            b -> Minecraft.getInstance().setScreen(new KeyframedSettingsScreen(this)));
      }
      case WORLD -> {
        int[] colors = {Theme.WARN, Theme.TRACK_WORLD, Theme.TRACK_LOOK};
        int i = 0;
        for (String id : List.of("reizo:time_of_day", "reizo:rain", "reizo:thunder")) {
          KeyframedSetting s = KeyframedSettings.find(id);
          if (s == null) {
            continue;
          }
          FlatSlider slider =
              new FlatSlider(
                  x0,
                  y,
                  w,
                  Component.translatable(s.label()),
                  s.min(),
                  s.max(),
                  () -> SettingEdits.shownValue(s),
                  v -> SettingEdits.edit(s, v, true),
                  s::format);
          slider.accent(colors[i++ % colors.length]).markedWhen(() -> SettingEdits.atKeyframe(s));
          sliders.add(addRenderableWidget(slider));
          y += 24;
        }
        y += 4;
        tonal(x0, y, w, "reizo.inspector.hide_looked", b -> hideLookedAt());
      }
    }
  }

  private int gradeSlider(
      int x,
      int y,
      int w,
      String name,
      float min,
      float max,
      java.util.function.Function<Grade, Float> get,
      java.util.function.BiFunction<Grade, Float, Grade> set) {
    FlatSlider slider =
        new FlatSlider(
            x,
            y,
            w,
            Component.translatable("reizo.look." + name),
            min,
            max,
            () -> get.apply(ReplayEditor.gradeNow()),
            v -> EffectEdits.edit(g -> set.apply(g, (float) v)),
            v -> String.format(Locale.ROOT, "%.2f", v).replace("-0.00", "0.00"));
    slider.accent(Theme.TRACK_LOOK).markedWhen(EffectEdits::atKeyframe);
    sliders.add(addRenderableWidget(slider));
    return y + 22;
  }

  private FlatButton icon(
      int x, int y, int size, String key, String glyph, Button.OnPress onPress) {
    return addRenderableWidget(
            new FlatButton(
                x, y, size, size, Component.translatable(key), FlatButton.Style.GHOST, onPress))
        .icon(glyph);
  }

  private void tonal(int x, int y, int w, String key, Button.OnPress onPress) {
    addRenderableWidget(
        new FlatButton(x, y, w, 16, Component.translatable(key), FlatButton.Style.TONAL, onPress));
  }

  /** What the inspector's widgets depend on; they are rebuilt when it changes. */
  private String inspectorState() {
    if (!showRight) {
      return "none";
    }
    return switch (tab) {
      case KEYFRAME -> "keyframe:" + Math.min(selection.size(), 2);
      case LOOK -> "look:" + lookPage;
      case WORLD -> "world";
    };
  }

  /** The tracks that fit, in display order: markers and camera always, then the busiest. */
  private void layoutLanes() {
    lanes.clear();
    int room = Math.max(2, (overviewY - 2 - lanesY) / LANE_H);
    List<Track> shown = new ArrayList<>(List.of(Track.MARKERS, Track.CAMERA));
    List<Track> rest = new ArrayList<>(List.of(Track.LOOK, Track.WORLD, Track.ENTITIES, Track.SETTINGS));
    rest.sort(Comparator.comparing((Track t) -> keyCount(t) == 0));
    for (Track t : rest) {
      if (shown.size() >= room) {
        break;
      }
      shown.add(t);
    }
    shown.sort(Comparator.naturalOrder());
    int y = lanesY;
    for (Track t : shown) {
      lanes.add(new Lane(t, y));
      y += LANE_H;
    }
  }

  private int laneY(Track track) {
    for (Lane l : lanes) {
      if (l.track == track) {
        return l.y;
      }
    }
    return -1;
  }

  private int lanesBottom() {
    return lanes.isEmpty() ? lanesY : lanes.get(lanes.size() - 1).y + LANE_H - 1;
  }

  private static Track trackOf(String property) {
    if (property.startsWith(ReplayEntityHider.PREFIX)) {
      return Track.ENTITIES;
    }
    return WORLD_IDS.contains(property) ? Track.WORLD : Track.SETTINGS;
  }

  private static int keyCount(Track t) {
    return switch (t) {
      case LOOK -> ReplayEditor.effects().size();
      case WORLD, ENTITIES, SETTINGS -> {
        int n = 0;
        for (PropertyTracks.Keyframe k : ReplayEditor.settings().keyframes()) {
          if (trackOf(k.property()) == t) {
            n++;
          }
        }
        yield n;
      }
      default -> 1;
    };
  }

  // --- State ------------------------------------------------------------------------------

  private void updateLabels() {
    ReplayServer server = ReplayPlayback.current();
    boolean running = server != null && server.clock().isRunning();
    if (playButton != null) {
      playButton.icon(running ? "❚❚" : "▶");
    }
    if (speedButton != null && server != null) {
      speedButton.setMessage(Component.literal(ReplayHud.speed(server.clock().speed()) + "x"));
    }
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
    if (!isDragging()) {
      for (FlatSlider s : sliders) {
        s.refresh();
      }
    }
  }

  // --- Rendering ---------------------------------------------------------------------------

  @Override
  public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    // No blur or dimming: the replay stays visible between the panels.
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    ReplayServer server = ReplayPlayback.current();
    if (server == null) {
      return;
    }
    if (!builtInspector.equals(inspectorState()) && drag == Drag.NONE) {
      rebuildWidgets();
    }
    if (drag == Drag.NONE && server.clock().isRunning()) {
      view.ensureVisible(playhead());
    }
    layoutLanes();

    renderFrames(g);
    if (showLeft) {
      renderOutliner(g, mouseX, mouseY);
    }
    if (showRight) {
      renderInspector(g);
    }
    renderTimeline(g, mouseX);
    renderTopBar(g, server);
    super.render(g, mouseX, mouseY, partialTick);
  }

  /** Panel grounds and the hairlines between them. */
  private void renderFrames(GuiGraphics g) {
    g.fill(0, 0, width, TOP_H, Theme.GROUND);
    Draw.hline(g, 0, width, TOP_H - 1, Theme.LINE);
    g.fill(0, TOP_H, RAIL_W, timelineY, Theme.GROUND);
    Draw.vline(g, RAIL_W - 1, TOP_H, timelineY, Theme.LINE);
    if (showLeft) {
      g.fill(RAIL_W, TOP_H, RAIL_W + OUTLINER_W, timelineY, Theme.SURFACE);
      Draw.vline(g, RAIL_W + OUTLINER_W - 1, TOP_H, timelineY, Theme.LINE);
    }
    if (showRight) {
      g.fill(width - INSPECTOR_W, TOP_H, width, timelineY, Theme.SURFACE);
      Draw.vline(g, width - INSPECTOR_W, TOP_H, timelineY, Theme.LINE);
    }
    g.fill(0, timelineY, width, height, Theme.GROUND);
    Draw.hline(g, 0, width, timelineY, Theme.LINE);
  }

  private void renderTopBar(GuiGraphics g, ReplayServer server) {
    Draw.round(g, 4, 4, 16, 16, 2, Theme.ACCENT);
    Draw.centered(g, font, "R", 10, 6, 0xFFFFFFFF);
    if (showTitle) {
      Draw.label(g, font, "Reizo", 21, 6, Theme.TEXT);
    }
    long head = drag == Drag.SEEK ? clampTime(view.xToTime(lastMouseX - left)) : playhead();
    String time = ReplayHud.time(head) + " / " + ReplayHud.time(view.durationMs());
    Draw.label(g, font, time, timeTextX, 6, Theme.TEXT);
  }

  private double lastMouseX;

  private void renderOutliner(GuiGraphics g, int mouseX, int mouseY) {
    int x0 = RAIL_W;
    int x1 = RAIL_W + OUTLINER_W - 1;
    Draw.label(g, font, Component.translatable("reizo.scene.title").getString(), x0 + 6, TOP_H + 6, Theme.TEXT);
    collectScene();
    String count = Integer.toString(sceneRows.size());
    Draw.label(g, font, count, x1 - 6 - Draw.width(font, count), TOP_H + 6, Theme.FAINT);
    int top = TOP_H + 18;
    int rows = Math.max(0, (timelineY - 4 - top) / ROW_H);
    sceneScroll = Math.max(0, Math.min(sceneScroll, Math.max(0, sceneRows.size() - rows)));
    if (sceneRows.isEmpty()) {
      Draw.label(g, font, Component.translatable("reizo.scene.empty").getString(), x0 + 6, top + 2, Theme.FAINT);
    }
    int recorded = recordedId();
    for (int i = 0; i < rows && sceneScroll + i < sceneRows.size(); i++) {
      Entity e = sceneRows.get(sceneScroll + i);
      int y = top + i * ROW_H;
      boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW_H;
      if (hover) {
        g.fill(x0 + 2, y, x1 - 2, y + ROW_H, Theme.HOVER);
      }
      boolean hidden = ReplayEntityHider.isHidden(e);
      int color =
          e.getId() == recorded
              ? Theme.ACCENT
              : e instanceof Player
                  ? Theme.TRACK_SETTINGS
                  : e instanceof LivingEntity
                      ? Theme.TRACK_ENTITY
                      : e instanceof ItemEntity ? Theme.WARN : Theme.FAINT;
      Draw.dot(g, x0 + 8, y + 5, hidden ? Theme.FAINT : color);
      String name = e.getName().getString();
      Draw.label(
          g, font, Draw.fit(font, name, x1 - x0 - 30), x0 + 14, y + 2, hidden ? Theme.FAINT : Theme.TEXT);
      boolean eyeHover = hover && mouseX >= x1 - 14;
      int eye = eyeHover ? Theme.TEXT : hidden ? Theme.FAINT : Theme.DIM;
      Icons.draw(g, "eye", x1 - 8, y + 5, eye);
      if (hidden) {
        g.fill(x1 - 11, y + 5, x1 - 4, y + 6, eye);
      }
    }
  }

  private int recordedId() {
    ReplayServer server = ReplayPlayback.current();
    return server == null || server.view() == null ? -1 : server.view().recordedEntityId();
  }

  /** The replay's entities: the recorded player first, then players, then the nearest others. */
  private void collectScene() {
    sceneRows.clear();
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null) {
      return;
    }
    Entity camera = mc.getCameraEntity();
    for (Entity e : mc.level.entitiesForRendering()) {
      if (e == mc.player || (e == camera && e != mc.level.getEntity(recordedId()))) {
        continue;
      }
      sceneRows.add(e);
      if (sceneRows.size() >= 256) {
        break;
      }
    }
    int recorded = recordedId();
    Entity from = camera != null ? camera : mc.player;
    sceneRows.sort(
        Comparator.comparing((Entity e) -> e.getId() != recorded)
            .thenComparing(e -> !(e instanceof Player))
            .thenComparingDouble(e -> from == null ? 0 : e.distanceToSqr(from)));
  }

  private void renderInspector(GuiGraphics g) {
    int x0 = width - INSPECTOR_W + 4;
    int x1 = width - 4;
    int y = TOP_H + 24;
    switch (tab) {
      case KEYFRAME -> {
        List<CameraKeyframe> chosen = selectedKeyframes();
        if (chosen.size() == 1) {
          CameraKeyframe k = chosen.get(0);
          String[][] rows = {
            {"reizo.inspector.time", ReplayHud.time(k.timeMs()) + "." + (k.timeMs() % 1000) / 100},
            {"X", num(k.x())},
            {"Y", num(k.y())},
            {"Z", num(k.z())},
            {"reizo.inspector.yaw", num(k.yaw()) + "°"},
            {"reizo.inspector.pitch", num(k.pitch()) + "°"},
            {"reizo.inspector.roll", num(k.roll()) + "°"},
            {"reizo.inspector.fov", num(k.fov()) + "°"},
          };
          Draw.diamond(g, x0 + 3, y + 3, 3, Theme.TRACK_CAMERA);
          Draw.label(g, font, Component.translatable("reizo.inspector.camera_key").getString(), x0 + 10, y, Theme.TEXT);
          y += 12;
          for (String[] r : rows) {
            String label = r[0].length() == 1 ? r[0] : Component.translatable(r[0]).getString();
            Draw.label(g, font, label, x0, y, Theme.DIM);
            Draw.label(g, font, r[1], x1 - Draw.width(font, r[1]), y, Theme.TEXT);
            y += 10;
          }
        } else if (chosen.isEmpty()) {
          Draw.wrap(
              g,
              font,
              Component.translatable("reizo.inspector.no_selection"),
              x0,
              y + 42,
              x1 - x0,
              Theme.DIM);
        } else {
          Draw.label(
              g,
              font,
              Component.translatable("reizo.inspector.many", chosen.size()).getString(),
              x0,
              y,
              Theme.TEXT);
        }
        String mode =
            ReplayEditor.path().interpolation().name().toLowerCase(Locale.ROOT).replace('_', '-')
                + (ReplayEditor.path().easing().name().startsWith("EASE") ? " + ease" : "");
        Draw.label(g, font, Draw.fit(font, mode, x1 - x0), x0, timelineY - 12, Theme.FAINT);
      }
      case LOOK -> {
        if (letterboxButton != null) {
          float lb = ReplayEditor.gradeNow().letterbox();
          letterboxButton.setMessage(
              Component.translatable(
                  "reizo.effects.letterbox",
                  lb <= 0
                      ? Component.translatable("options.off")
                      : Component.literal(String.format(Locale.ROOT, "%.2f:1", lb))));
        }
      }
      case WORLD -> {
        String hidden =
            Component.translatable("reizo.inspector.hidden_count", ReplayEntityHider.hiddenCount())
                .getString();
        int hy = TOP_H + 24 + sliders.size() * 24 + 4 + 20;
        if (hy < timelineY - 10) {
          Draw.label(g, font, hidden, x0, hy, Theme.DIM);
        }
      }
    }
  }

  private static String num(double v) {
    return String.format(Locale.ROOT, "%.1f", v);
  }

  private List<CameraKeyframe> selectedKeyframes() {
    List<CameraKeyframe> out = new ArrayList<>();
    for (CameraKeyframe k : ReplayEditor.path().keyframes()) {
      if (selection.contains(k.timeMs())) {
        out.add(k);
      }
    }
    return out;
  }

  private void renderTimeline(GuiGraphics g, int mouseX) {
    lastMouseX = mouseX;
    long head = drag == Drag.SEEK ? clampTime(view.xToTime(mouseX - left)) : playhead();
    String t = ReplayHud.time(head) + "." + (head % 1000) / 100;
    Draw.label(g, font, t, 6, headerY + 3, Theme.TEXT);

    // Ruler and lanes.
    Draw.round(g, left, rulerY, right, rulerY + RULER_H, 3, Theme.SURFACE);
    for (Lane l : lanes) {
      Draw.round(g, 4, l.y, LABEL_W, l.y + LANE_H - 1, 3, Theme.SURFACE);
      Draw.round(g, 4, l.y + 2, 6, l.y + LANE_H - 3, 1, l.track.color);
      Draw.label(
          g,
          font,
          Draw.fit(font, Component.translatable(l.track.key).getString(), LABEL_W - 12),
          9,
          l.y + 2,
          Theme.DIM);
      Draw.round(g, left, l.y, right, l.y + LANE_H - 1, 3, Theme.RAISED);
    }
    int bottom = lanesBottom();

    g.enableScissor(left, rulerY, right, bottom + 1);
    renderRuler(g);
    renderRegion(g, rulerY, bottom);
    renderMarkers(g);
    renderPropertyKeys(g);
    renderKeyframes(g);
    int cy = laneY(Track.CAMERA);
    if (drag == Drag.BOX && cy >= 0) {
      int a = (int) (left + view.timeToX(dragStartTime));
      g.fill(Math.min(a, mouseX), cy, Math.max(a, mouseX), cy + LANE_H - 1, Theme.ACCENT_WASH);
    }
    int hx = (int) Math.round(left + view.timeToX(head));
    g.fill(hx, rulerY, hx + 1, bottom, Theme.PLAYHEAD);
    g.fill(hx - 2, rulerY, hx + 3, rulerY + 3, Theme.PLAYHEAD);
    g.disableScissor();

    renderOverview(g);
  }

  private void renderRuler(GuiGraphics g) {
    long step = view.tickStepMs(Draw.width(font, "00:00") + 10);
    long first = (view.startMs() / step) * step;
    for (long t = first; t <= view.endMs(); t += step) {
      int x = (int) Math.round(left + view.timeToX(t));
      g.fill(x, rulerY + RULER_H - 4, x + 1, rulerY + RULER_H, Theme.FAINT);
      Draw.label(g, font, label(t, step), x + 2, rulerY + 2, Theme.DIM);
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
    g.fill(a, top, b, bottom, Theme.ACCENT_WASH);
    if (in >= 0) {
      g.fill(a, top, a + 1, bottom, Theme.ACCENT);
    }
    if (out >= 0) {
      g.fill(b - 1, top, b, bottom, Theme.ACCENT);
    }
  }

  private void renderMarkers(GuiGraphics g) {
    RecordingMetadata meta = ReplayPlayback.metadata();
    int y = laneY(Track.MARKERS);
    if (meta == null || y < 0) {
      return;
    }
    for (RecordingMetadata.Marker m : meta.markers) {
      int x = (int) Math.round(left + view.timeToX(m.timeMs));
      g.fill(x, y + 1, x + 1, y + LANE_H - 2, Theme.TRACK_MARKER);
      g.fill(x + 1, y + 1, x + 5, y + 5, Theme.TRACK_MARKER);
    }
  }

  /** Effect and setting keyframes on their tracks, with a bar where a track is active. */
  private void renderPropertyKeys(GuiGraphics g) {
    int ly = laneY(Track.LOOK);
    if (ly >= 0) {
      var keys = ReplayEditor.effects().keyframes();
      if (keys.size() > 1) {
        int a = xOf(keys.get(0).timeMs());
        int b = xOf(keys.get(keys.size() - 1).timeMs());
        g.fill(a, ly + 5, b, ly + 7, Theme.alpha(Theme.TRACK_LOOK, 0x60));
      }
      for (var k : keys) {
        Draw.diamond(g, xOf(k.timeMs()), ly + 6, 3, Theme.TRACK_LOOK);
      }
    }
    for (PropertyTracks.Keyframe k : ReplayEditor.settings().keyframes()) {
      Track t = trackOf(k.property());
      int y = laneY(t);
      if (y >= 0) {
        Draw.diamond(g, xOf(k.timeMs()), y + 6, 3, t.color);
      }
    }
  }

  private void renderKeyframes(GuiGraphics g) {
    int cy = laneY(Track.CAMERA);
    if (cy < 0) {
      return;
    }
    List<CameraKeyframe> keys = ReplayEditor.path().keyframes();
    int mid = cy + 6;
    if (keys.size() > 1) {
      int a = xOf(keys.get(0).timeMs());
      int b = xOf(keys.get(keys.size() - 1).timeMs());
      g.fill(a, mid - 1, b, mid + 1, Theme.alpha(Theme.TRACK_CAMERA, 0x70));
    }
    for (CameraKeyframe k : keys) {
      boolean sel = selection.contains(k.timeMs());
      int x = xOf(k.timeMs());
      if (sel) {
        Draw.diamond(g, x, mid, 5, 0xFFFFFFFF);
      }
      Draw.diamond(g, x, mid, 4, sel ? Theme.ACCENT_HOVER : Theme.TRACK_CAMERA);
    }
  }

  private void renderOverview(GuiGraphics g) {
    int x0 = left;
    int x1 = right;
    if (x1 - x0 < 20) {
      return;
    }
    Draw.round(g, x0, overviewY + 1, x1, overviewY + 5, 1, Theme.RAISED);
    double scale = (double) (x1 - x0) / view.durationMs();
    int a = x0 + (int) (view.startMs() * scale);
    int b = Math.max(a + 4, x0 + (int) (view.endMs() * scale));
    Draw.round(g, a, overviewY, b, overviewY + 6, 2, Theme.FAINT);
    int h = x0 + (int) (playhead() * scale);
    g.fill(h, overviewY, h + 1, overviewY + 6, Theme.PLAYHEAD);
  }

  // --- Mouse ------------------------------------------------------------------------------

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    if (super.mouseClicked(event, doubleClick)) {
      return true;
    }
    double mx = event.x();
    double my = event.y();
    if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
      return false;
    }
    if (showLeft && mx >= RAIL_W && mx < RAIL_W + OUTLINER_W && my >= TOP_H && my < timelineY) {
      return clickScene(mx, my);
    }
    if (mx < left || mx > right) {
      return false;
    }
    layoutLanes();
    long t = clampTime(view.xToTime(mx - left));
    boolean shift = event.hasShiftDown() || Minecraft.getInstance().hasShiftDown();
    if (my >= rulerY && my < rulerY + RULER_H) {
      drag = Drag.SEEK;
      return true;
    }
    for (Lane l : lanes) {
      if (my < l.y || my >= l.y + LANE_H) {
        continue;
      }
      if (l.track == Track.CAMERA) {
        return clickCamera(mx, t, shift, doubleClick);
      }
      long key = keyAt(l.track, mx);
      if (key >= 0) {
        ReplayPlayback.seekTo(key);
      }
      return true;
    }
    if (my >= overviewY - 1 && my < overviewY + 7) {
      drag = Drag.OVERVIEW;
      centerOverview(mx);
      return true;
    }
    return false;
  }

  private boolean clickCamera(double mx, long t, boolean shift, boolean doubleClick) {
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

  private boolean clickScene(double mx, double my) {
    int top = TOP_H + 18;
    if (my < top) {
      return true;
    }
    int index = sceneScroll + (int) ((my - top) / ROW_H);
    if (index < 0 || index >= sceneRows.size()) {
      return true;
    }
    Entity e = sceneRows.get(index);
    if (mx >= RAIL_W + OUTLINER_W - 15) {
      ReplayEntityHider.toggle(e, playhead());
    } else {
      lookAt(e);
    }
    return true;
  }

  /** Turns the free camera towards an entity (switching to the free camera first). */
  private static void lookAt(Entity e) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null) {
      return;
    }
    if (ReplayCamera.mode() != ReplayCamera.Mode.FREE) {
      ReplayCamera.set(mc, ReplayCamera.Mode.FREE);
    }
    ReplayEditor.setPathPlaying(false);
    Player cam = mc.player;
    double dx = e.getX() - cam.getX();
    double dy = e.getY() + e.getBbHeight() * 0.6 - cam.getEyeY();
    double dz = e.getZ() - cam.getZ();
    double horizontal = Math.sqrt(dx * dx + dz * dz);
    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
    float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
    cam.setYRot(yaw);
    cam.setXRot(pitch);
    cam.yRotO = yaw;
    cam.xRotO = pitch;
    cam.setYHeadRot(yaw);
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
      case SEEK -> ReplayPlayback.seekTo(clampTime(view.xToTime(event.x() - left)));
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
    if (showLeft && mx >= RAIL_W && mx < RAIL_W + OUTLINER_W && my >= TOP_H && my < timelineY) {
      sceneScroll = Math.max(0, sceneScroll - (int) Math.signum(vertical) * 3);
      return true;
    }
    if (my < timelineY) {
      return super.mouseScrolled(mx, my, horizontal, vertical);
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
    double t = (mx - left) / (right - left) * view.durationMs();
    view.scrollPx((t - (view.startMs() + view.spanMs() / 2.0)) / view.msPerPixel());
  }

  private long clampTime(long t) {
    return Math.max(0, Math.min(view.durationMs(), t));
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

  /** Time of the key on {@code track} nearest to {@code mx}, or -1. */
  private long keyAt(Track track, double mx) {
    List<Long> times = new ArrayList<>();
    switch (track) {
      case MARKERS -> {
        RecordingMetadata meta = ReplayPlayback.metadata();
        if (meta != null) {
          for (RecordingMetadata.Marker m : meta.markers) {
            times.add((long) m.timeMs);
          }
        }
      }
      case LOOK -> ReplayEditor.effects().keyframes().forEach(k -> times.add(k.timeMs()));
      default -> {
        for (PropertyTracks.Keyframe k : ReplayEditor.settings().keyframes()) {
          if (trackOf(k.property()) == track) {
            times.add(k.timeMs());
          }
        }
      }
    }
    long best = -1;
    double bestD = HIT_PX + 1;
    for (long t : times) {
      double d = Math.abs(left + view.timeToX(t) - mx);
      if (d <= HIT_PX && d < bestD) {
        best = t;
        bestD = d;
      }
    }
    return best;
  }

  // --- Keys -------------------------------------------------------------------------------

  @Override
  public boolean keyPressed(KeyEvent event) {
    if (getFocused() instanceof FlatSlider && isDragging()) {
      return super.keyPressed(event);
    }
    int key = event.key();
    Minecraft mc = Minecraft.getInstance();
    boolean ctrl = Compat.controlDown(event) || mc.hasControlDown();
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
      case InputConstants.KEY_H -> hideLookedAt();
      case InputConstants.KEY_TAB -> togglePanels();
      case InputConstants.KEY_LEFT -> ReplayPlayback.seekBy(shift ? -5000 : -1000);
      case InputConstants.KEY_RIGHT -> ReplayPlayback.seekBy(shift ? 5000 : 1000);
      default -> {
        return super.keyPressed(event);
      }
    }
    return true;
  }

  // --- Actions (package-visible for tests) ------------------------------------------------

  private void transport(Button b) {
    String key = keyOf(b);
    switch (key) {
      case "reizo.editor.prev_marker" -> ReplayPlayback.seekToMarker(-1);
      case "reizo.editor.back" -> ReplayPlayback.seekBy(-5000);
      case "reizo.editor.play" -> togglePlay();
      case "reizo.editor.forward" -> ReplayPlayback.seekBy(5000);
      case "reizo.editor.next_marker" -> ReplayPlayback.seekToMarker(1);
      default -> {}
    }
  }

  private void railTool(Button b) {
    Minecraft mc = Minecraft.getInstance();
    switch (keyOf(b)) {
      case "reizo.timeline.hide" -> hideLookedAt();
      case "reizo.timeline.effects" -> mc.setScreen(new EffectsScreen(this));
      case "reizo.timeline.settings" -> mc.setScreen(new KeyframedSettingsScreen(this));
      case "reizo.editor.panels" -> togglePanels();
      default -> {}
    }
  }

  private void headerTool(Button b) {
    switch (keyOf(b)) {
      case "reizo.timeline.add" -> addKeyframe();
      case "reizo.timeline.delete" -> deleteSelection();
      case "reizo.timeline.smooth" -> ReplayEditor.cycleSmoothing();
      case "reizo.timeline.in" -> ReplayEditor.setRegion(true, playhead());
      case "reizo.timeline.out" -> ReplayEditor.setRegion(false, playhead());
      case "reizo.editor.fit" -> view.showAll();
      case "reizo.editor.zoom_in" -> view.zoom(1.5, xOf(playhead()) - left);
      case "reizo.editor.zoom_out" -> view.zoom(1 / 1.5, xOf(playhead()) - left);
      default -> {}
    }
  }

  private static String keyOf(Button b) {
    return b.getMessage().getContents()
            instanceof net.minecraft.network.chat.contents.TranslatableContents t
        ? t.getKey()
        : "";
  }

  private void togglePanels() {
    panels = !panels;
    rebuildWidgets();
  }

  private void stepSpeed() {
    ReplayPlayback.stepSpeed(Minecraft.getInstance().hasShiftDown() ? -1 : 1);
    updateLabels();
  }

  private static String currentView() {
    if (ReplayEditor.isPathPlaying()) {
      return "path";
    }
    return ReplayCamera.mode().name().toLowerCase(Locale.ROOT);
  }

  private static void setView(String mode) {
    Minecraft mc = Minecraft.getInstance();
    if (mode.equals("path")) {
      ReplayCamera.set(mc, ReplayCamera.Mode.FREE);
      ReplayEditor.setPathPlaying(true);
      return;
    }
    ReplayEditor.setPathPlaying(false);
    ReplayCamera.set(mc, ReplayCamera.Mode.valueOf(mode.toUpperCase(Locale.ROOT)));
  }

  private void hideLookedAt() {
    RecorderKeys.hideLookedAt(Minecraft.getInstance());
  }

  private void goToSelected() {
    List<CameraKeyframe> chosen = selectedKeyframes();
    if (!chosen.isEmpty()) {
      ReplayPlayback.seekTo(chosen.get(0).timeMs());
    }
  }

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
    layoutLanes();
    return laneY(Track.CAMERA) + 6;
  }

  /** GUI y of the middle of the ruler, for tests. */
  public int rulerTrackY() {
    return rulerY + RULER_H / 2;
  }

  /** Selected keyframe times, for tests. */
  public Set<Long> selectedTimes() {
    return Set.copyOf(selection);
  }

  /** Whether the outliner and inspector are shown, for tests. */
  public boolean sidePanelsShown() {
    return showLeft || showRight;
  }
}
