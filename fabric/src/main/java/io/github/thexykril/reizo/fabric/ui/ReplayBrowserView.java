/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui;

import io.github.thexykril.reizo.core.storage.RecordingStorage;
import java.util.List;

/** The replay browser, whichever screen draws it (Compose or the kit). */
public interface ReplayBrowserView {
  /** The listed recordings, in list order. */
  List<RecordingStorage.Entry> entries();

  /** Selects a row like a click would. */
  void select(int index);

  /** Presses Play. */
  void pressPlay();
}
