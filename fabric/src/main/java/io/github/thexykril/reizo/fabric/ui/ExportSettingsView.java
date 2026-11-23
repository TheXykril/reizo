/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.core.export.ExportFormat;
import java.util.List;

/** An export settings screen (Reizo's own, or the one on OneConfig's UI), for tests. */
public interface ExportSettingsView {
  /** The formats the installed FFmpeg can encode, as offered on the screen. */
  List<ExportFormat> offeredFormats();
}
