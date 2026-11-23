/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.core.format.PlayerScreenState;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.capture.HudSampler;
import io.github.thexykril.reizo.fabric.capture.ScreenSampler;
import io.github.thexykril.reizo.fabric.compat.Compat;
import io.github.thexykril.reizo.fabric.export.ExportSession;
import io.github.thexykril.reizo.fabric.mixin.MenuScreensAccessor;
import io.github.thexykril.reizo.fabric.playback.ReplayCamera;
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback;
import io.github.thexykril.reizo.fabric.playback.ReplayServer;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws the inventory or container screen the recorded player had open while the replay camera is
 * in POV. The screen is the game's own screen class, built with the recorded player's slots and
 * drawn with the recorded mouse position, so it looks exactly like the vanilla one (including the
 * hovered slot, its tooltip and the stack on the cursor). It is never opened: it takes no input and
 * the viewer keeps control of the replay. Shown in exports like the player HUD.
 */
public final class PlayerScreenOverlay {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Replay");

  private static PlayerScreenState built;
  private static Player builtFor;
  private static AbstractContainerScreen<?> screen;
  private static byte[] slotsFrom;
  private static int builtWidth;
  private static int builtHeight;

  private PlayerScreenOverlay() {}

  public static void register() {
    HudElementRegistry.attachElementAfter(
        Identifier.fromNamespaceAndPath(RecorderClient.MOD_ID, "player_hud"),
        Identifier.fromNamespaceAndPath(RecorderClient.MOD_ID, "player_screen"),
        (graphics, tickCounter) -> render(graphics, tickCounter.getGameTimeDeltaPartialTick(false)));
  }

  /** The recorded player, when {@code screen} is the replayed inventory screen; else null. */
  public static LivingEntity entityFor(Object screen) {
    return screen != null && screen == PlayerScreenOverlay.screen ? builtFor : null;
  }

  /** Whether a recorded screen is drawn this frame. */
  public static boolean visible(Minecraft mc) {
    ReplayServer server = ReplayPlayback.current();
    PlayerScreenState state = server == null ? null : server.screen();
    return state != null
        && state.open()
        && state.items() != null
        && ReplayCamera.mode() == ReplayCamera.Mode.POV
        && RecorderClient.get().config().showPlayerScreens
        && (!Compat.hudHidden(mc) || ExportSession.isExporting())
        && !(mc.screen instanceof TimelineScreen)
        && recordedPlayer(mc) != null;
  }

  /** The replayed screen being drawn, or null. For tests. */
  public static AbstractContainerScreen<?> current() {
    return screen;
  }

  private static void render(GuiGraphics g, float partialTick) {
    Minecraft mc = Minecraft.getInstance();
    if (ReplayPlayback.current() == null) {
      screen = null; // let go of the last replay's world
      builtFor = null;
      built = null;
    }
    if (!visible(mc) || mc.getConnection() == null) {
      return;
    }
    PlayerScreenState state = ReplayPlayback.current().screen();
    Player player = recordedPlayer(mc);
    int width = g.guiWidth();
    int height = g.guiHeight();
    RegistryAccess access = mc.getConnection().registryAccess();
    try {
      if (screen == null || !state.sameScreen(built) || player != builtFor) {
        build(mc, state, player, access);
      }
      if (screen == null) {
        return;
      }
      if (width != builtWidth || height != builtHeight) {
        screen.init(width, height);
        builtWidth = width;
        builtHeight = height;
      }
      if (state.items() != slotsFrom) {
        slotsFrom = state.items();
        fill(screen.getMenu(), HudSampler.decodeItems(state.items(), access));
      }
      int mouseX = Math.round(state.mouseX() * width);
      int mouseY = Math.round(state.mouseY() * height);
      draw(screen, g, mouseX, mouseY, partialTick);
    } catch (RuntimeException e) {
      // A screen that needs more than the replay has (a mod's menu, say): show none.
      LOGGER.debug("Could not draw the recorded screen {}", state, e);
      screen = null;
      built = state;
      builtFor = player;
    }
  }

  private static void draw(Screen s, GuiGraphics g, int x, int y, float partialTick) {
    s.renderWithTooltipAndSubtitles(g, x, y, partialTick);
  }

  private static void fill(AbstractContainerMenu menu, List<ItemStack> stacks) {
    if (stacks.isEmpty()) {
      return;
    }
    List<ItemStack> slots = stacks.subList(0, stacks.size() - 1);
    if (slots.size() != menu.slots.size()) {
      return; // recorded with a different menu layout
    }
    menu.initializeContents(menu.getStateId(), slots, stacks.get(stacks.size() - 1));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void build(
      Minecraft mc, PlayerScreenState state, Player player, RegistryAccess access) {
    built = state;
    builtFor = player;
    slotsFrom = null;
    screen = null;
    if (state.kind() == PlayerScreenState.CONTAINER) {
      MenuType type = BuiltInRegistries.MENU.getValue(Identifier.tryParse(state.menuType()));
      MenuScreens.ScreenConstructor constructor =
          type == null ? null : MenuScreensAccessor.reizo$getConstructor(type);
      if (constructor == null) {
        return;
      }
      AbstractContainerMenu menu = type.create(0, player.getInventory());
      screen =
          (AbstractContainerScreen<?>)
              constructor.create(
                  menu, player.getInventory(), ScreenSampler.decodeTitle(state.title(), access));
    } else {
      screen = new InventoryScreen(player);
    }
    builtWidth = 0;
    builtHeight = 0;
  }

  private static Player recordedPlayer(Minecraft mc) {
    ReplayServer server = ReplayPlayback.current();
    if (server == null || mc.level == null || server.view() == null) {
      return null;
    }
    return mc.level.getEntity(server.view().recordedEntityId()) instanceof Player p ? p : null;
  }
}
