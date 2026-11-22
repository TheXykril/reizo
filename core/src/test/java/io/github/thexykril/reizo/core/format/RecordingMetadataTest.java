/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RecordingMetadataTest {
  @Test
  void jsonRoundTrip() {
    RecordingMetadata meta = RecordingMetadata.create("My clip", 1_700_000_000_000L);
    meta.minecraftVersion = "1.21.11";
    meta.protocolVersion = 774;
    meta.completeFromJoin = true;
    meta.markers.add(new RecordingMetadata.Marker(1234, "kill"));
    RecordingMetadata copy = RecordingMetadata.fromJson(meta.toJson());
    assertEquals(meta.id, copy.id);
    assertEquals("My clip", copy.name);
    assertEquals(774, copy.protocolVersion);
    assertTrue(copy.completeFromJoin);
    assertEquals(1, copy.markers.size());
    assertEquals("kill", copy.markers.get(0).label);
    assertEquals(RecordingMetadata.State.RECORDING, copy.state);
  }

  @Test
  void ignoresUnknownFieldsAndFillsMissingLists() {
    RecordingMetadata meta =
        RecordingMetadata.fromJson("{\"id\":\"x\",\"schema\":1,\"futureField\":42}");
    assertEquals("x", meta.id);
    assertNotNull(meta.markers);
  }

  @Test
  void rejectsNewerSchemaAndMissingId() {
    assertThrows(
        IllegalArgumentException.class,
        () -> RecordingMetadata.fromJson("{\"id\":\"x\",\"schema\":999}"));
    assertThrows(IllegalArgumentException.class, () -> RecordingMetadata.fromJson("{}"));
    assertThrows(IllegalArgumentException.class, () -> RecordingMetadata.fromJson("{not json"));
  }
}
