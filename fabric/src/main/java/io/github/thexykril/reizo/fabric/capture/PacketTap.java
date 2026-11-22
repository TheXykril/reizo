/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.capture;

import io.github.thexykril.reizo.core.format.ProtocolPhase;
import io.github.thexykril.reizo.core.recording.CaptureController;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.PacketFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridge from the decoder mixin to the {@link CaptureController}. Mixins cannot receive constructor
 * arguments, so this is the single static entry point; it holds nothing but the installed
 * controller.
 */
public final class PacketTap {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Capture");

  private static volatile CaptureController controller;
  private static volatile boolean errorLogged;
  private static volatile boolean suppressed;

  private PacketTap() {}

  public static void install(CaptureController c) {
    controller = c;
  }

  /** While true (a replay is playing), nothing is captured. */
  public static void setSuppressed(boolean value) {
    suppressed = value;
  }

  /** Cheap pre-check on the network thread: is this a client decoder for a recorded phase? */
  public static boolean wants(ProtocolInfo<?> info) {
    return controller != null
        && !suppressed
        && info.flow() == PacketFlow.CLIENTBOUND
        && phaseOf(info) != null;
  }

  public static void captured(ProtocolInfo<?> info, byte[] packetBytes) {
    CaptureController c = controller;
    ProtocolPhase phase = phaseOf(info);
    if (c == null || phase == null || suppressed) {
      return;
    }
    try {
      c.onPacket(phase, packetBytes);
    } catch (RuntimeException e) {
      // Recording must never break the connection. Log once to avoid flooding.
      if (!errorLogged) {
        errorLogged = true;
        LOGGER.error("Packet capture failed; further errors are suppressed", e);
      }
    }
  }

  static ProtocolPhase phaseOf(ProtocolInfo<?> info) {
    ConnectionProtocol id = info.id();
    if (id == ConnectionProtocol.CONFIGURATION) {
      return ProtocolPhase.CONFIGURATION;
    }
    if (id == ConnectionProtocol.PLAY) {
      return ProtocolPhase.PLAY;
    }
    return null;
  }
}
