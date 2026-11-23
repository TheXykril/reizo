/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.compat;

import io.github.thexykril.reizo.core.recording.RecordingSession;
import io.github.thexykril.reizo.fabric.RecorderClient;
import io.github.thexykril.reizo.fabric.ui.RecordingIndicator;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;
import org.polyfrost.oneconfig.api.hud.v1.TextHud;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reizo's recording indicator as a OneConfig HUD element, so it can be placed and styled in
 * OneConfig's HUD editor like Lunar's Rewind indicator. Only loaded from {@link
 * OneConfigIntegration}, so only when OneConfig is installed. Hidden while nothing is recorded
 * (except in the HUD editor, so it can be placed).
 */
final class OneConfigRecordingHud extends TextHud {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/OneConfig");

  OneConfigRecordingHud() {
    super("reizo_recording", "Reizo recording", Hud.Category.Companion.getINFO(), "", "");
  }

  /** Registers the element; a OneConfig whose HUD API differs leaves Reizo working without it. */
  static void register() {
    try {
      HudManager.register(new OneConfigRecordingHud());
    } catch (Throwable t) { // LinkageError from a different OneConfig version, too
      LOGGER.warn("OneConfig HUD element not available: {}", t.toString());
    }
  }

  @Override
  protected String getText() {
    RecorderClient client = RecorderClient.get();
    RecordingSession session = client == null ? null : client.session();
    if (session == null) {
      return client != null && client.shadow() != null ? "Shadow on" : "REC 00:00";
    }
    String time = RecordingIndicator.formatElapsed(session.elapsedMillis());
    return session.state() == RecordingSession.State.PAUSED ? "PAUSED " + time : "REC " + time;
  }

  @Override
  public boolean getHidden() {
    RecorderClient client = RecorderClient.get();
    boolean active = client != null && (client.session() != null || client.shadow() != null);
    return !active && !HudManager.INSTANCE.isEditorOpen();
  }
}
