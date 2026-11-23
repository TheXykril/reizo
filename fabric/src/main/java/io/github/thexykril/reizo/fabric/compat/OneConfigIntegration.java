/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.compat;

import io.github.thexykril.reizo.fabric.ClientConfig;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.ui.ReplayBrowserScreen;
import io.github.thexykril.reizo.fabric.ui.SettingsScreen;
import net.minecraft.client.Minecraft;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.ConfigEntrypoint;
import org.polyfrost.oneconfig.api.config.v1.annotations.Button;
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.config.v1.annotations.Text;

/**
 * Optional OneConfig page. OneConfig calls this entrypoint only when it is installed (for example
 * in OneClient), so nothing here loads without it. The page edits the same values as Reizo's own
 * settings screen ({@link ClientConfig}); every change is copied there and saved.
 *
 * <p>Key bindings are vanilla key mappings, which OneConfig's keybind page lists on its own.
 */
public final class OneConfigIntegration implements ConfigEntrypoint {
  @Override
  public void registerConfigs(Context context) {
    context.register(new Page());
    OneConfigRecordingHud.register();
    context.originalScreen(
        () ->
            Minecraft.getInstance().setScreen(new SettingsScreen(Minecraft.getInstance().screen)));
  }

  /** Reizo's page in OneConfig. Field names are the property ids. */
  public static final class Page extends Config {
    @Button(
        title = "Replays",
        description = "Browse, play, rename and delete your recordings.",
        text = "Open",
        category = "Replays")
    public static void openReplays() {
      Minecraft.getInstance().setScreen(new ReplayBrowserScreen(null));
    }

    @Button(
        title = "Start or stop recording",
        description = "Same as the start/stop key (Shift+R by default).",
        text = "Toggle",
        category = "Recording")
    public static void toggleRecording() {
      RecorderClient.get().toggleRecording();
    }

    @Switch(
        title = "Record automatically on join",
        description = "Start recording as soon as you join a world or server.",
        category = "Recording")
    public static boolean autoRecordOnJoin;

    @Switch(
        title = "Recording indicator",
        description = "Show the red REC indicator while recording.",
        category = "Recording")
    public static boolean showIndicator = true;

    @Switch(
        title = "Save server or world name",
        description = "Turn off to keep the server address out of recordings you share.",
        category = "Recording")
    public static boolean storeServerName = true;

    @Dropdown(
        title = "Start/stop key modifier",
        description = "Key held together with the start/stop key (set the key in Controls).",
        options = {"None", "Shift", "Ctrl", "Alt"},
        category = "Recording")
    public static int toggleModifier = 1;

    @Button(
        title = "Save last minutes",
        description = "Save what the shadow buffer holds as a recording.",
        text = "Save",
        category = "Shadow")
    public static void saveShadow() {
      Minecraft.getInstance().execute(() -> RecorderClient.get().saveShadow());
    }

    @Switch(
        title = "Shadow recording",
        description =
            "Keep the last minutes of play in memory so you can save them after something happened.",
        category = "Shadow")
    public static boolean shadowEnabled;

    @Dropdown(
        title = "Shadow length",
        description = "How much play a save keeps at least.",
        options = {"30 s", "1 min", "2 min", "5 min", "10 min"},
        category = "Shadow")
    public static int shadowLength = 2;

    @Switch(
        title = "OneConfig notifications",
        description =
            "Show Reizo's messages and export progress as OneConfig notifications instead of"
                + " Minecraft toasts.",
        category = "Recording")
    public static boolean oneConfigNotifications = true;

    @Text(
        title = "FFmpeg path",
        description = "Leave empty to use ffmpeg from the system PATH.",
        placeholder = "ffmpeg",
        category = "Export")
    public static String ffmpegPath = "";

    public Page() {
      super("reizo-oneconfig.json", "Reizo", Category.UTILITY);
      // Start from Reizo's own settings; OneConfig then loads its file, if it has one.
      pull(RecorderClient.get().config());
    }

    @Override
    protected void onInitialized(org.polyfrost.oneconfig.api.config.v1.Tree tree) {
      super.onInitialized(tree);
      for (String id :
          new String[] {
            "autoRecordOnJoin",
            "showIndicator",
            "storeServerName",
            "toggleModifier",
            "ffmpegPath",
            "shadowEnabled",
            "shadowLength",
            "oneConfigNotifications"
          }) {
        addCallback(id, Page::push);
      }
      // config/reizo.json is the source of truth: OneConfig's own file may be older than changes
      // made in Reizo's settings screen, so its values never overwrite Reizo's.
      pull(RecorderClient.get().config());
    }

    private static void pull(ClientConfig c) {
      autoRecordOnJoin = c.autoRecordOnJoin;
      showIndicator = c.showIndicator;
      storeServerName = c.storeServerName;
      toggleModifier = modifierIndex(c.toggleModifier);
      ffmpegPath = c.ffmpegPath == null ? "" : c.ffmpegPath;
      shadowEnabled = c.shadowEnabled;
      oneConfigNotifications = c.oneConfigNotifications;
      int length = RecorderClient.SHADOW_LENGTHS.indexOf(c.shadowSeconds);
      shadowLength = length < 0 ? 2 : length;
    }

    /** Copies the page's values into Reizo's settings and saves them. */
    private static void push() {
      RecorderClient client = RecorderClient.get();
      ClientConfig c = client.config();
      c.autoRecordOnJoin = autoRecordOnJoin;
      c.showIndicator = showIndicator;
      c.storeServerName = storeServerName;
      c.toggleModifier = MODIFIERS[Math.max(0, Math.min(MODIFIERS.length - 1, toggleModifier))];
      c.ffmpegPath = ffmpegPath == null ? "" : ffmpegPath.strip();
      c.shadowEnabled = shadowEnabled;
      c.oneConfigNotifications = oneConfigNotifications;
      c.shadowSeconds =
          RecorderClient.SHADOW_LENGTHS.get(
              Math.max(0, Math.min(RecorderClient.SHADOW_LENGTHS.size() - 1, shadowLength)));
      client.applyShadowSettings();
      client.saveConfig();
    }

    private static final String[] MODIFIERS = {"none", "shift", "control", "alt"};

    private static int modifierIndex(String value) {
      for (int i = 0; i < MODIFIERS.length; i++) {
        if (MODIFIERS[i].equalsIgnoreCase(value)) {
          return i;
        }
      }
      return 0;
    }
  }
}
