/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.playback;

import com.mojang.authlib.GameProfile;
import io.github.thexykril.reizo.core.playback.PlaybackClock;
import io.github.thexykril.reizo.core.playback.ReplayScript;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.handshake.HandshakeProtocols;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundLoginAcknowledgedPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plays a recording back to the local client over a loopback socket (ADR 0008).
 *
 * <p>The client connects to it like to any server. The replay server answers the login itself, then
 * writes the recorded configuration packets and, once the client has switched to the play protocol,
 * the recorded play packets at their recorded times. Packets are written as the exact bytes that
 * were captured, so the client decodes them with its own codecs, as it did when recording. Packets
 * the client sends are read and discarded, except for the few acknowledgements that mark a protocol
 * switch.
 *
 * <p>No compression and no encryption are negotiated, so a frame is {@code varint(length)} followed
 * by the packet id and body. The socket listens on the loopback address only and accepts a single
 * connection.
 */
public final class ReplayServer implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger("Reizo/Replay");
  private static final int ACCEPT_TIMEOUT_MS = 30_000;
  private static final long KEEP_ALIVE_INTERVAL_MS = 5_000;
  private static final int MAX_SERVERBOUND_FRAME = 2 * 1024 * 1024;
  private static final AtomicInteger THREAD_IDS = new AtomicInteger();

  private final ReplayScript script;
  private final GameProfile profile;
  private final PlaybackClock clock;
  private final Supplier<RegistryAccess> registries;
  private final Runnable onClosed;
  private final ServerSocket listener;
  private final CountDownLatch loginAcknowledged = new CountDownLatch(1);
  private final AtomicInteger configurationsFinished = new AtomicInteger();
  private final AtomicInteger configurationAcks = new AtomicInteger();
  private final java.util.concurrent.atomic.AtomicLong pendingSeek =
      new java.util.concurrent.atomic.AtomicLong(-1);
  private final AtomicInteger seeks = new AtomicInteger();
  private volatile boolean reconfiguring;
  private volatile boolean switching;
  private volatile boolean oldWorldGone;
  private volatile boolean manual;
  private volatile long manualTime;
  private final java.util.concurrent.atomic.AtomicLong manualTarget =
      new java.util.concurrent.atomic.AtomicLong(-1);
  private final AtomicInteger syncRequest = new AtomicInteger();
  private volatile int lastPong;
  private final CountDownLatch helloReceived = new CountDownLatch(1);

  private final java.util.UUID recordedPlayerUuid;
  private final String recordedPlayerName;
  private volatile PlayTransformer transformer;
  private final java.util.concurrent.atomic.AtomicBoolean tickStateChanged =
      new java.util.concurrent.atomic.AtomicBoolean();
  private volatile Socket socket;
  private volatile boolean closed;
  private volatile int playPacketsSent;
  private volatile io.github.thexykril.reizo.core.format.PlayerHudState hud;

  /**
   * Opens the listening socket. Call {@link #start()} afterwards.
   *
   * @param profile the camera's profile (the client's own player in the replay)
   * @param recordedPlayerUuid the recorded player, shown as a separate entity
   * @param recordedPlayerName its name, used if the recording has no tab entry for it
   * @param registries the client's play registries, or null while it has none
   * @param onClosed runs once, on the replay thread, when the connection ends
   */
  public ReplayServer(
      ReplayScript script,
      GameProfile profile,
      java.util.UUID recordedPlayerUuid,
      String recordedPlayerName,
      PlaybackClock clock,
      Supplier<RegistryAccess> registries,
      Runnable onClosed)
      throws IOException {
    this.script = script;
    this.profile = profile;
    this.recordedPlayerUuid = recordedPlayerUuid;
    this.recordedPlayerName = recordedPlayerName;
    this.clock = clock;
    this.registries = registries;
    this.onClosed = onClosed;
    this.listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
  }

  public int port() {
    return listener.getLocalPort();
  }

  public PlaybackClock clock() {
    return clock;
  }

  public ReplayScript script() {
    return script;
  }

  /**
   * Pauses or resumes the replay. While paused no recorded packets are sent and the client's world
   * is frozen (entities stop; the camera can still move). Any thread.
   */
  public void setPaused(boolean paused) {
    if (paused) {
      clock.pause();
    } else {
      clock.resume();
    }
    tickStateChanged.set(true);
  }

  /** Sets the playback speed; the client's tick rate follows, so the world moves at that speed. */
  public void setSpeed(double speed) {
    clock.setSpeed(speed);
    tickStateChanged.set(true);
  }

  /** Play packets written to the client so far. */
  public int playPacketsSent() {
    return playPacketsSent;
  }

  public void start() {
    Thread t = new Thread(this::run, "Reizo-Replay-" + THREAD_IDS.incrementAndGet());
    t.setDaemon(true);
    t.start();
  }

  @Override
  public void close() {
    closed = true;
    closeQuietly(listener);
    Socket s = socket;
    if (s != null) {
      closeQuietly(s);
    }
  }

  private void run() {
    try {
      listener.setSoTimeout(ACCEPT_TIMEOUT_MS);
      try (Socket s = listener.accept()) {
        closeQuietly(listener);
        socket = s;
        s.setTcpNoDelay(true);
        Thread reader = new Thread(() -> readClient(s), Thread.currentThread().getName() + "-in");
        reader.setDaemon(true);
        reader.start();
        OutputStream out = new BufferedOutputStream(s.getOutputStream());
        serve(out);
      }
    } catch (SocketTimeoutException e) {
      LOGGER.warn("Replay client did not connect within {} ms", ACCEPT_TIMEOUT_MS);
    } catch (IOException e) {
      if (!closed) {
        LOGGER.info("Replay connection ended: {}", e.toString());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (RuntimeException e) {
      LOGGER.error("Replay failed", e);
    } finally {
      close();
      onClosed.run();
    }
  }

  private void serve(OutputStream out) throws IOException, InterruptedException {
    awaitOrClosed(helloReceived);
    writeFrame(
        out, encode(LoginProtocols.CLIENTBOUND, loginFinished(profile)));
    out.flush();
    awaitOrClosed(loginAcknowledged);

    RegistryAccess access = configure(out, null);
    PlayTransformer transformer =
        new PlayTransformer(access, profile, recordedPlayerUuid, recordedPlayerName);
    this.transformer = transformer;
    LOGGER.info(
        "Replay running: {} play packets over {} ms",
        script.playPacketCount(),
        script.durationMs());
    clock.resume();
    long lastWrite = System.currentTimeMillis();
    while (!closed) {
      long seek = pendingSeek.getAndSet(-1);
      if (seek >= 0) {
        if (seek < clock.timeMs()) {
          // Backwards: the client's world cannot be undone, so build it again from the start and
          // fast-forward to the target (the entries up to it are all due at once below).
          access = restart(out, access);
          transformer =
              new PlayTransformer(access, profile, recordedPlayerUuid, recordedPlayerName);
          this.transformer = transformer;
          script.rewind();
          playPacketsSent = 0;
          hud = null;
          tickStateChanged.set(true);
        }
        clock.seek(seek);
        seeks.incrementAndGet();
        LOGGER.debug("Replay seeked to {} ms", seek);
      }
      long frameTarget = manualTarget.getAndSet(-1);
      if (frameTarget >= 0) {
        manualTime = frameTarget;
      }
      List<ReplayScript.Entry> due = script.due(manual ? manualTime : clock.timeMs());
      java.util.ArrayDeque<ReplayScript.Entry> queue = new java.util.ArrayDeque<>(due);
      int written = 0;
      while (!queue.isEmpty()) {
        ReplayScript.Entry entry = queue.poll();
        if (entry.kind() == ReplayScript.Kind.RECONFIGURE) {
          // A recorded server switch: the client goes through configuration into the new world.
          if (written > 0) {
            out.flush();
          }
          access = switchServer(out, access, entry.configuration());
          transformer =
              new PlayTransformer(access, profile, recordedPlayerUuid, recordedPlayerName);
          this.transformer = transformer;
          tickStateChanged.set(true);
          if (queue.stream().noneMatch(e -> e.kind() == ReplayScript.Kind.PLAY_PACKET)) {
            // The new world's login right away: an export waits for the world before it moves
            // the replay time on, and the login was recorded a little after the switch.
            queue.addAll(script.throughNextPlayPacket());
          }
          continue;
        }
        if (entry.kind() == ReplayScript.Kind.PLAYER_HUD) {
          try {
            hud = io.github.thexykril.reizo.core.format.PlayerHudState.decode(entry.bytes());
          } catch (io.github.thexykril.reizo.core.format.FormatException e) {
            LOGGER.debug("Skipping an unreadable HUD sample", e);
          }
          continue;
        }
        List<byte[]> packets =
            entry.kind() == ReplayScript.Kind.PLAY_PACKET
                ? transformer.packet(entry.bytes())
                : transformer.localPlayer(entry.bytes());
        for (byte[] packet : packets) {
          writeFrame(out, packet);
          written++;
        }
        if (entry.kind() == ReplayScript.Kind.PLAY_PACKET) {
          playPacketsSent++;
        }
      }
      if (tickStateChanged.getAndSet(false) | transformer.takeRecordedTickStateChanged()) {
        // The client's tick rate and freeze state follow the replay clock, on top of the
        // recorded server's own tick state. The client clamps the rate to at least 1.
        writeFrame(
            out,
            encodePlay(
                access,
                new ClientboundTickingStatePacket(
                    (float) (transformer.recordedTickRate() * (manual ? 1.0 : clock.speed())),
                    transformer.recordedFrozen() || (!manual && !clock.isRunning()))));
        written++;
      }
      int sync = syncRequest.getAndSet(0);
      if (sync != 0) {
        // Answered by the client on its main thread after every packet above: when the pong
        // arrives, the client has applied everything up to the frame time.
        writeFrame(out, encodePlay(access, new ClientboundPingPacket(sync)));
        written++;
      }
      long now = System.currentTimeMillis();
      if (written > 0) {
        out.flush();
        lastWrite = now;
      } else if (now - lastWrite >= KEEP_ALIVE_INTERVAL_MS) {
        // The client drops a connection that stays silent for 30 s (paused or finished replay).
        writeFrame(out, encodePlay(access, new ClientboundKeepAlivePacket(now)));
        out.flush();
        lastWrite = now;
      }
      Thread.sleep(due.isEmpty() ? 5 : 1);
    }
  }

  /**
   * Writes the recorded configuration phase and waits until the client is in the play phase with
   * new registries (different from {@code previous}).
   */
  private RegistryAccess configure(OutputStream out, RegistryAccess previous)
      throws IOException, InterruptedException {
    return configure(out, previous, script.configuration());
  }

  private RegistryAccess configure(
      OutputStream out, RegistryAccess previous, List<byte[]> configuration)
      throws IOException, InterruptedException {
    int finishedBefore = configurationsFinished.get();
    for (byte[] packet : configuration) {
      writeFrame(out, packet);
    }
    out.flush();
    // Play packets must not arrive before the client has switched its decoder to the play
    // protocol, which it confirms with its own finish-configuration packet.
    awaitCount(configurationsFinished, finishedBefore + 1);
    RegistryAccess access = awaitRegistries(previous);
    LOGGER.info("Replay client is in the play phase");
    return access;
  }

  /**
   * Sends the client back to the configuration phase, the way a proxy switches servers, and
   * configures it again. The client drops its world, so the replay starts over cleanly.
   */
  private RegistryAccess restart(OutputStream out, RegistryAccess access)
      throws IOException, InterruptedException {
    reconfiguring = true;
    oldWorldGone = false;
    try {
      int ackedBefore = configurationAcks.get();
      LOGGER.info("Replay seeking back: asking the client to reconfigure");
      writeFrame(out, encodePlay(access, ClientboundStartConfigurationPacket.INSTANCE));
      out.flush();
      awaitCount(configurationAcks, ackedBefore + 1);
      oldWorldGone = true;
      LOGGER.info("Replay restarting for a backwards seek");
      return configure(out, access);
    } catch (IOException | InterruptedException | RuntimeException e) {
      reconfiguring = false;
      throw e;
    }
    // Stays true until the client shows the new world (restartDone), so the client tick does not
    // mistake the missing world for the player leaving the replay.
  }

  /**
   * Plays a recorded server switch: sends the client back to configuration and through the recorded
   * configuration phase of the next server.
   */
  private RegistryAccess switchServer(
      OutputStream out, RegistryAccess access, List<byte[]> configuration)
      throws IOException, InterruptedException {
    switching = true;
    reconfiguring = true;
    oldWorldGone = false;
    try {
      int ackedBefore = configurationAcks.get();
      LOGGER.info("Replay switching servers, as recorded");
      writeFrame(out, encodePlay(access, ClientboundStartConfigurationPacket.INSTANCE));
      out.flush();
      awaitCount(configurationAcks, ackedBefore + 1);
      oldWorldGone = true;
      return configure(out, access, configuration);
    } catch (IOException | InterruptedException | RuntimeException e) {
      reconfiguring = false;
      switching = false;
      throw e;
    }
    // Both stay true until the client shows the new world (restartDone).
  }

  /** True while a recorded server switch is being played (not a backwards seek). */
  public boolean isSwitchingServers() {
    return switching;
  }

  /**
   * Jumps to a replay time, in milliseconds (clamped to the recording). Forwards, the packets in
   * between are sent at once; backwards, the replay is rebuilt from the start. Any thread.
   */
  public void seek(long timeMs) {
    // Never before the first play packet: the world (the login packet) only starts there, and a
    // paused replay would otherwise wait on the reconfiguration screen forever.
    pendingSeek.set(Math.max(script.startMs(), Math.min(timeMs, script.durationMs())));
  }

  /** Number of seeks carried out so far. For tests. */
  public int seeksDone() {
    return seeks.get();
  }

  /**
   * Frame-by-frame mode for export. While on, the replay time is whatever {@link #advanceTo} last
   * set (the wall clock is ignored), and the client's world is never frozen or sped up by the
   * replay controls.
   */
  public void setManual(boolean on, long startMs) {
    manualTime = startMs;
    manual = on;
    tickStateChanged.set(true);
  }

  /** The replay time the client is being shown: the frame time in manual mode, else the clock. */
  public long replayTimeMs() {
    return manual ? manualTime : clock.timeMs();
  }

  public boolean isManual() {
    return manual;
  }

  /**
   * In manual mode: sends every packet up to {@code timeMs}, then a ping with {@code syncId}
   * (non-zero). {@link #syncedThrough()} returns the id once the client has applied them.
   */
  public void advanceTo(long timeMs, int syncId) {
    manualTarget.set(timeMs);
    syncRequest.set(syncId);
  }

  /** The id of the last ping the client answered. */
  public int syncedThrough() {
    return lastPong;
  }

  /** Called on the client thread once the rebuilt world is shown after a backwards seek. */
  public void restartDone() {
    reconfiguring = false;
    switching = false;
    oldWorldGone = false;
  }

  /**
   * During a restart or switch: true once the client has dropped the old world (it answers the
   * switch to configuration only after that), so any world it shows from then on is the new one.
   */
  public boolean oldWorldGone() {
    return oldWorldGone;
  }

  /** True while the client is being sent back through configuration for a backwards seek. */
  public boolean isReconfiguring() {
    return reconfiguring;
  }

  private void awaitCount(AtomicInteger counter, int atLeast)
      throws InterruptedException, IOException {
    long deadline = System.currentTimeMillis() + 30_000;
    while (counter.get() < atLeast) {
      if (closed) {
        throw new IOException("closed");
      }
      if (System.currentTimeMillis() > deadline) {
        throw new IOException("client did not answer the protocol switch");
      }
      Thread.sleep(5);
    }
  }

  /** The client's registries; it creates its play listener before confirming the switch. */
  private RegistryAccess awaitRegistries(RegistryAccess previous)
      throws InterruptedException, IOException {
    long deadline = System.currentTimeMillis() + 10_000;
    while (true) {
      RegistryAccess access = registries.get();
      if (access != null && access != previous) {
        return access;
      }
      if (closed || System.currentTimeMillis() > deadline) {
        throw new IOException("client registries not available");
      }
      Thread.sleep(10);
    }
  }

  /** The recorded player's HUD at the current replay time, or null when none was recorded. */
  public io.github.thexykril.reizo.core.format.PlayerHudState hud() {
    return hud;
  }

  /** The packet transformer once the replay is in the play phase, else null. For tests. */
  public PlayTransformerView view() {
    PlayTransformer t = transformer;
    return t == null
        ? null
        : new PlayTransformerView(t.recordedEntityId(), t.recordedPlayerAdded());
  }

  /** What tests may observe about the recorded player. */
  public record PlayTransformerView(int recordedEntityId, boolean recordedPlayerAdded) {}

  private void awaitOrClosed(CountDownLatch latch) throws InterruptedException, IOException {
    while (!latch.await(100, TimeUnit.MILLISECONDS)) {
      if (closed) {
        throw new IOException("closed");
      }
    }
  }

  // ---- client -> replay server ----

  private enum Phase {
    HANDSHAKE,
    LOGIN,
    CONFIGURATION,
    PLAY
  }

  /** Reads the client's packets. Only protocol switches matter; everything else is dropped. */
  private void readClient(Socket s) {
    Phase phase = Phase.HANDSHAKE;
    try (DataInputStream in = new DataInputStream(s.getInputStream())) {
      while (!closed) {
        byte[] frame = readFrame(in);
        switch (phase) {
          case HANDSHAKE -> {
            if (decode(HandshakeProtocols.SERVERBOUND, frame)
                instanceof ClientIntentionPacket intent) {
              LOGGER.debug("Replay client connected with intent {}", intent.intention());
              phase = Phase.LOGIN;
            }
          }
          case LOGIN -> {
            Packet<?> p = decode(LoginProtocols.SERVERBOUND, frame);
            if (p instanceof ServerboundHelloPacket) {
              helloReceived.countDown();
            } else if (p instanceof ServerboundLoginAcknowledgedPacket) {
              phase = Phase.CONFIGURATION;
              loginAcknowledged.countDown();
            }
          }
          case CONFIGURATION -> {
            if (decode(ConfigurationProtocols.SERVERBOUND, frame)
                instanceof ServerboundFinishConfigurationPacket) {
              phase = Phase.PLAY;
              configurationsFinished.incrementAndGet();
            }
          }
          case PLAY -> {
            // Movement, keep-alive answers and so on are dropped. Only the answer to a restart
            // (backwards seek) matters: after it the client speaks the configuration protocol.
            int id = peekId(frame);
            if (id == CONFIGURATION_ACK_ID) {
              phase = Phase.CONFIGURATION;
              configurationAcks.incrementAndGet();
            } else if (id == PONG_ID && frame.length >= 5) {
              // varint id (one byte for this packet) then the int we sent in the ping.
              int answered =
                  ((frame[1] & 0xFF) << 24)
                      | ((frame[2] & 0xFF) << 16)
                      | ((frame[3] & 0xFF) << 8)
                      | (frame[4] & 0xFF);
              lastPong = answered;
            }
          }
        }
      }
    } catch (EOFException e) {
      LOGGER.debug("Replay client disconnected");
    } catch (IOException | RuntimeException e) {
      if (!closed) {
        LOGGER.info("Replay client connection ended: {}", e.toString());
      }
    } finally {
      close();
    }
  }

  // ---- encoding helpers ----

  /** Packet id of the client's acknowledgement of a switch back to configuration. */
  private static final int CONFIGURATION_ACK_ID =
      serverboundPlayId(ServerboundConfigurationAcknowledgedPacket.INSTANCE.type());

  /** Packet id of the client's answer to a ping (frame sync during export). */
  private static final int PONG_ID = serverboundPlayId(new ServerboundPongPacket(0).type());

  private static int serverboundPlayId(net.minecraft.network.protocol.PacketType<?> wanted) {
    int[] found = {-1};
    GameProtocols.SERVERBOUND_TEMPLATE
        .details()
        .listPackets(
            (type, id) -> {
              if (type.equals(wanted)) {
                found[0] = id;
              }
            });
    if (found[0] < 0 || found[0] > 127) {
      throw new IllegalStateException("serverbound packet " + wanted + " not found");
    }
    return found[0];
  }

  private static int peekId(byte[] bytes) {
    int value = 0;
    for (int i = 0, shift = 0; i < bytes.length && shift < 35; i++, shift += 7) {
      value |= (bytes[i] & 0x7F) << shift;
      if ((bytes[i] & 0x80) == 0) {
        return value;
      }
    }
    return -1;
  }

  private static <T extends net.minecraft.network.PacketListener> Packet<? super T> decode(
      ProtocolInfo<T> protocol, byte[] bytes) {
    ByteBuf buf = Unpooled.wrappedBuffer(bytes);
    return protocol.codec().decode(buf);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static byte[] encode(ProtocolInfo<?> protocol, Packet<?> packet) {
    ByteBuf buf = Unpooled.buffer();
    try {
      ((ProtocolInfo) protocol).codec().encode(buf, packet);
      byte[] out = new byte[buf.readableBytes()];
      buf.readBytes(out);
      return out;
    } finally {
      buf.release();
    }
  }

  private static byte[] encodePlay(
      RegistryAccess access, Packet<? super ClientGamePacketListener> packet) {
    ProtocolInfo<ClientGamePacketListener> play =
        GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(access));
    return encode(play, packet);
  }

  // ---- framing: varint(length) + bytes ----

  private static void writeFrame(OutputStream out, byte[] packet) throws IOException {
    int value = packet.length;
    while ((value & ~0x7F) != 0) {
      out.write((value & 0x7F) | 0x80);
      value >>>= 7;
    }
    out.write(value);
    out.write(packet);
  }

  private static byte[] readFrame(DataInputStream in) throws IOException {
    int length = 0;
    for (int shift = 0; ; shift += 7) {
      int b = in.read();
      if (b < 0) {
        throw new EOFException();
      }
      length |= (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        break;
      }
      if (shift >= 21) {
        throw new IOException("frame length too long");
      }
    }
    if (length <= 0 || length > MAX_SERVERBOUND_FRAME) {
      throw new IOException("bad frame length " + length);
    }
    byte[] frame = new byte[length];
    in.readFully(frame);
    return frame;
  }

  private static void closeQuietly(AutoCloseable c) {
    try {
      c.close();
    } catch (Exception ignored) {
      // Closing during shutdown; nothing useful to do.
    }
  }

  private static ClientboundLoginFinishedPacket loginFinished(GameProfile profile) {
    //? if >=26.1 {
    /*// The session id only feeds telemetry.
    return new ClientboundLoginFinishedPacket(profile, java.util.UUID.randomUUID());
    *///?} else {
    return new ClientboundLoginFinishedPacket(profile);
    //?}
  }
}
