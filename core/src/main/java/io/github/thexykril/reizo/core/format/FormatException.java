/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.format;

import java.io.IOException;

/** Thrown when recording data is corrupt or uses an unsupported format version. */
public class FormatException extends IOException {
  private static final long serialVersionUID = 1L;

  public FormatException(String message) {
    super(message);
  }

  public FormatException(String message, Throwable cause) {
    super(message, cause);
  }
}
