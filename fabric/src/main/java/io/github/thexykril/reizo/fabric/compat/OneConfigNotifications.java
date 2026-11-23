/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.compat;

import java.util.function.DoubleSupplier;
import org.polyfrost.oneconfig.api.notifications.v1.Notification;
import org.polyfrost.oneconfig.api.notifications.v1.Notifications;

/**
 * Reizo's messages as OneConfig notifications, so they look like the rest of OneClient. Loaded only
 * through {@code RecorderClient.toast} when OneConfig is installed; any failure there falls back to
 * Minecraft's toasts.
 */
public final class OneConfigNotifications {
  /** How a message is shown. */
  public enum Kind {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
  }

  private static final long DURATION_MS = 5000;

  private OneConfigNotifications() {}

  public static void send(Kind kind, String title, String body) {
    String text = body == null ? "" : body;
    switch (kind) {
      case SUCCESS -> Notifications.success(title, text, DURATION_MS);
      case WARNING -> Notifications.warning(title, text, DURATION_MS);
      case ERROR -> Notifications.error(title, text, DURATION_MS);
      default -> Notifications.info(title, text, DURATION_MS);
    }
  }

  /** A notification with a progress bar (0 to 1) that stays until {@link #dismiss}. */
  public static Object progress(String title, String body, DoubleSupplier progress) {
    return Notifications.progress(
        title, body == null ? "" : body, () -> (float) progress.getAsDouble());
  }

  public static void dismiss(Object handle) {
    if (handle instanceof Notification n) {
      Notifications.dismiss(n);
    }
  }
}
