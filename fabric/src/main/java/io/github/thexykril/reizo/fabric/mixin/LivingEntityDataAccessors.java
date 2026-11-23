/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The living entity flags (using an item), for showing the recorded player in replays. */
@Mixin(LivingEntity.class)
public interface LivingEntityDataAccessors {
  @Accessor("DATA_LIVING_ENTITY_FLAGS")
  static EntityDataAccessor<Byte> reizo$livingFlags() {
    throw new AssertionError();
  }
}
