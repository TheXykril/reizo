/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.timeline;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo and redo by snapshots. Before each change the caller records the state as it was; undo hands
 * back the previous state and keeps the current one for redo. A new change clears redo. States must
 * be immutable. Holds at most {@link #limit} undo steps.
 *
 * @param <S> the state type
 */
public final class EditHistory<S> {
  private final int limit;
  private final Deque<S> undo = new ArrayDeque<>();
  private final Deque<S> redo = new ArrayDeque<>();

  public EditHistory(int limit) {
    this.limit = Math.max(1, limit);
  }

  /** Records the state before a change. */
  public void record(S before) {
    undo.push(before);
    while (undo.size() > limit) {
      undo.removeLast();
    }
    redo.clear();
  }

  /** The state to go back to, or null; {@code current} becomes the next redo. */
  public S undo(S current) {
    if (undo.isEmpty()) {
      return null;
    }
    redo.push(current);
    return undo.pop();
  }

  /** The state to go forward to, or null; {@code current} becomes the next undo. */
  public S redo(S current) {
    if (redo.isEmpty()) {
      return null;
    }
    undo.push(current);
    return redo.pop();
  }

  public boolean canUndo() {
    return !undo.isEmpty();
  }

  public boolean canRedo() {
    return !redo.isEmpty();
  }

  public void clear() {
    undo.clear();
    redo.clear();
  }
}
