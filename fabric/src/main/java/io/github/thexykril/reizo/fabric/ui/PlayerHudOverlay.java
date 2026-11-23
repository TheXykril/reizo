/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.core.format.PlayerHudState;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.capture.HudSampler;
import io.github.thexykril.reizo.fabric.compat.Compat;
import io.github.thexykril.reizo.fabric.export.ExportSession;
import io.github.thexykril.reizo.fabric.playback.ReplayCamera;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.playback.ReplayServer;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the recorded player's HUD (hotbar, health, food, armor, experience and air) from the
 * recording while the replay camera is in POV, laid out like the vanilla HUD. It is attached after
 * the sleep overlay, which Minecraft draws even with the HUD hidden, so it also appears in exports;
 * otherwise it follows F1 like the rest of the HUD.
 */
public final class PlayerHudOverlay {
  private static final Identifier HOTBAR = sprite("hud/hotbar");
  private static final Identifier SELECTION = sprite("hud/hotbar_selection");
  private static final Identifier OFFHAND = sprite("hud/hotbar_offhand_left");
  private static final Identifier XP_BACKGROUND = sprite("hud/experience_bar_background");
  private static final Identifier XP_PROGRESS = sprite("hud/experience_bar_progress");
  private static final Identifier ARMOR_EMPTY = sprite("hud/armor_empty");
  private static final Identifier ARMOR_HALF = sprite("hud/armor_half");
  private static final Identifier ARMOR_FULL = sprite("hud/armor_full");
  private static final Identifier FOOD_EMPTY = sprite("hud/food_empty");
  private static final Identifier FOOD_HALF = sprite("hud/food_half");
  private static final Identifier FOOD_FULL = sprite("hud/food_full");
  private static final Identifier AIR = sprite("hud/air");
  private static final Identifier AIR_BURSTING = sprite("hud/air_bursting");
  private static final int XP_TEXT = 0xFF80FF20;
  private static final int OUTLINE = 0xFF000000;

  private static final int SURVIVAL = 0;
  private static final int CREATIVE = 1;
  private static final int SPECTATOR = 3;

  /** The last decoded hotbar and the bytes it came from, so items decode once per change. */
  private static byte[] decodedFrom;

  private static List<ItemStack> decoded = List.of();

  private PlayerHudOverlay() {}

  public static void register() {
    HudElementRegistry.attachElementAfter(
        VanillaHudElements.SLEEP,
        Identifier.fromNamespaceAndPath(RecorderClient.MOD_ID, "player_hud"),
        (graphics, tickCounter) -> render(graphics));
  }

  /** Whether the overlay is drawn this frame. */
  public static boolean visible(Minecraft mc) {
    ReplayServer server = ReplayPlayback.current();
    return server != null
        && server.hud() != null
        && ReplayCamera.mode() == ReplayCamera.Mode.POV
        && RecorderClient.get().config().showPlayerHud
        && (!Compat.hudHidden(mc) || ExportSession.isExporting())
        && !(mc.screen instanceof TimelineScreen);
  }

  private static void render(GuiGraphics g) {
    Minecraft mc = Minecraft.getInstance();
    if (!visible(mc)) {
      return;
    }
    PlayerHudState hud = ReplayPlayback.current().hud();
    if (hud == null || hud.gameMode() == SPECTATOR) {
      return;
    }
    int center = g.guiWidth() / 2;
    // Above the replay bar while it shows; exports hide the bar and keep the vanilla layout.
    int bottom = g.guiHeight() - (Compat.hudHidden(mc) ? 0 : ReplayHud.HEIGHT);
    hotbar(g, mc, hud, center, bottom);
    if (hud.gameMode() == CREATIVE) {
      return;
    }
    experience(g, mc.font, hud, center, bottom);
    int left = center - 91;
    int right = center + 91;
    int top = bottom - 39;
    int rows = (int) Math.ceil((hud.maxHealth() + hud.absorption()) / 2.0 / 10.0);
    int rowHeight = Math.max(10 - (rows - 2), 3);
    hearts(g, hud, left, top, rowHeight);
    armor(g, hud.armor(), left, top - (rows - 1) * rowHeight - 10);
    food(g, hud.food(), right, top);
    air(g, hud, right, top - 10);
  }

  private static void hotbar(
      GuiGraphics g, Minecraft mc, PlayerHudState hud, int center, int bottom) {
    int y = bottom - 22;
    g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, center - 91, y, 182, 22);
    int slot = Math.max(0, Math.min(8, hud.selectedSlot()));
    g.blitSprite(
        RenderPipelines.GUI_TEXTURED, SELECTION, center - 91 - 1 + slot * 20, y - 1, 24, 23);
    List<ItemStack> items = items(mc, hud.items());
    ItemStack offhand = items.size() > 9 ? items.get(9) : ItemStack.EMPTY;
    if (!offhand.isEmpty()) {
      g.blitSprite(RenderPipelines.GUI_TEXTURED, OFFHAND, center - 91 - 29, y - 1, 29, 24);
      item(g, mc.font, offhand, center - 91 - 26, y + 3);
    }
    for (int i = 0; i < Math.min(9, items.size()); i++) {
      item(g, mc.font, items.get(i), center - 90 + i * 20 + 2, y + 3);
    }
  }

  private static void item(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
    if (!stack.isEmpty()) {
      g.renderItem(stack, x, y);
      g.renderItemDecorations(font, stack, x, y);
    }
  }

  private static void experience(
      GuiGraphics g, Font font, PlayerHudState hud, int center, int bottom) {
    int x = center - 91;
    int y = bottom - 29;
    g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BACKGROUND, x, y, 182, 5);
    int filled = (int) (Math.max(0f, Math.min(1f, hud.experienceProgress())) * 183f);
    if (filled > 0) {
      g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_PROGRESS, 182, 5, 0, 0, x, y, filled, 5);
    }
    if (hud.experienceLevel() > 0) {
      String level = Integer.toString(hud.experienceLevel());
      int tx = center - font.width(level) / 2;
      int ty = bottom - 35;
      g.drawString(font, level, tx + 1, ty, OUTLINE, false);
      g.drawString(font, level, tx - 1, ty, OUTLINE, false);
      g.drawString(font, level, tx, ty + 1, OUTLINE, false);
      g.drawString(font, level, tx, ty - 1, OUTLINE, false);
      g.drawString(font, level, tx, ty, XP_TEXT, false);
    }
  }

  private static void hearts(GuiGraphics g, PlayerHudState hud, int left, int top, int rowHeight) {
    boolean hardcore = (hud.flags() & PlayerHudState.FLAG_HARDCORE) != 0;
    int health = (int) Math.ceil(hud.health());
    int absorption = (int) Math.ceil(hud.absorption());
    int containers = (int) Math.ceil(hud.maxHealth() / 2.0);
    int absorbing = (int) Math.ceil(absorption / 2.0);
    Identifier container = heart("container", hardcore);
    for (int i = containers + absorbing - 1; i >= 0; i--) {
      int x = left + (i % 10) * 8;
      int y = top - (i / 10) * rowHeight;
      g.blitSprite(RenderPipelines.GUI_TEXTURED, container, x, y, 9, 9);
      if (i >= containers) {
        int extra = (i - containers) * 2;
        if (extra < absorption) {
          boolean half = extra + 1 == absorption;
          g.blitSprite(
              RenderPipelines.GUI_TEXTURED,
              heart(half ? "absorbing_half" : "absorbing_full", hardcore),
              x,
              y,
              9,
              9);
        }
      } else if (i * 2 < health) {
        boolean half = i * 2 + 1 == health;
        g.blitSprite(
            RenderPipelines.GUI_TEXTURED, heart(half ? "half" : "full", hardcore), x, y, 9, 9);
      }
    }
  }

  private static void armor(GuiGraphics g, int armor, int left, int y) {
    if (armor <= 0) {
      return;
    }
    for (int i = 0; i < 10; i++) {
      int x = left + i * 8;
      Identifier s =
          i * 2 + 1 < armor ? ARMOR_FULL : i * 2 + 1 == armor ? ARMOR_HALF : ARMOR_EMPTY;
      g.blitSprite(RenderPipelines.GUI_TEXTURED, s, x, y, 9, 9);
    }
  }

  private static void food(GuiGraphics g, int food, int right, int y) {
    for (int i = 0; i < 10; i++) {
      int x = right - i * 8 - 9;
      g.blitSprite(RenderPipelines.GUI_TEXTURED, FOOD_EMPTY, x, y, 9, 9);
      if (i * 2 + 1 < food) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, FOOD_FULL, x, y, 9, 9);
      } else if (i * 2 + 1 == food) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, FOOD_HALF, x, y, 9, 9);
      }
    }
  }

  private static void air(GuiGraphics g, PlayerHudState hud, int right, int y) {
    int max = hud.maxAir();
    int air = Math.min(hud.air(), max);
    if (max <= 0 || air >= max) {
      return;
    }
    // Ten bubbles, each worth a tenth of the supply; one bursts just as it runs out.
    int full = (int) Math.ceil((air - 2) * 10.0 / max);
    int partial = (int) Math.ceil(air * 10.0 / max) - full;
    for (int i = 0; i < full + partial; i++) {
      int x = right - i * 8 - 9;
      g.blitSprite(RenderPipelines.GUI_TEXTURED, i < full ? AIR : AIR_BURSTING, x, y, 9, 9);
    }
  }

  private static List<ItemStack> items(Minecraft mc, byte[] bytes) {
    if (bytes != decodedFrom) {
      decodedFrom = bytes;
      decoded = List.of();
      if (mc.getConnection() != null && bytes.length > 0) {
        try {
          decoded = HudSampler.decodeItems(bytes, mc.getConnection().registryAccess());
        } catch (RuntimeException e) {
          // Items from a registry this game does not have: show the bar without them.
          decoded = List.of();
        }
      }
    }
    return decoded;
  }

  private static Identifier heart(String name, boolean hardcore) {
    String path =
        hardcore
            ? switch (name) {
              case "container" -> "container_hardcore";
              case "full" -> "hardcore_full";
              case "half" -> "hardcore_half";
              default -> name.replace("absorbing_", "absorbing_hardcore_");
            }
            : name;
    return sprite("hud/heart/" + path);
  }

  private static Identifier sprite(String path) {
    return Identifier.withDefaultNamespace(path);
  }
}
