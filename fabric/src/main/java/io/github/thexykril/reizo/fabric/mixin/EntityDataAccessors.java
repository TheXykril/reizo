/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The entity's synced pose, for showing the recorded player's pose in replays. */
@Mixin(Entity.class)
public interface EntityDataAccessors {
  @Accessor("DATA_POSE")
  static EntityDataAccessor<Pose> reizo$pose() {
    throw new AssertionError();
  }
}
