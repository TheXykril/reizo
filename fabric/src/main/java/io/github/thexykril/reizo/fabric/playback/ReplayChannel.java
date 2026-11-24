/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.playback;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;

/**
 * The in-memory link between the replay server and the client (ADR 0020).
 *
 * <p>Both ends are Netty local channels, so nothing listens on a network port. The bytes are the
 * same as over the old loopback socket: the client's end gets the game's normal (network)
 * serialization pipeline, so it decodes the recorded packets with its own codecs, exactly as it did
 * while recording. Only the transport changes.
 *
 * <p>The game treats local channels as its integrated server ({@code Connection.isMemoryConnection})
 * and then skips work a real server needs, such as applying the tags it sends. A replay is a real
 * server to the client, so {@code ConnectionMixin} answers false for connections to this channel.
 */
final class ReplayChannel {
  private static final String ADDRESS_PREFIX = "reizo-replay-";
  private static final AtomicInteger IDS = new AtomicInteger();
  private static volatile EventLoopGroup group;

  private ReplayChannel() {}

  /** One daemon thread serves every replay link; links are short-lived and light. */
  private static EventLoopGroup group() {
    EventLoopGroup g = group;
    if (g == null) {
      synchronized (ReplayChannel.class) {
        g = group;
        if (g == null) {
          g =
              new DefaultEventLoopGroup(
                  1,
                  r -> {
                    Thread t = new Thread(r, "Reizo-Replay-IO");
                    t.setDaemon(true);
                    return t;
                  });
          group = g;
        }
      }
    }
    return g;
  }

  /** Whether {@code address} is the client end of a replay link. */
  static boolean isReplayAddress(SocketAddress address) {
    return address instanceof LocalAddress local && local.id().startsWith(ADDRESS_PREFIX);
  }

  /** The replay server's end: accepts one client and hands out its byte streams. */
  static final class Server implements AutoCloseable {
    private final LocalAddress address = new LocalAddress(ADDRESS_PREFIX + IDS.incrementAndGet());
    private final CompletableFuture<Channel> accepted = new CompletableFuture<>();
    private final Input input = new Input();
    private final Channel listener;

    Server() {
      listener =
          new ServerBootstrap()
              .group(group())
              .channel(LocalServerChannel.class)
              .childHandler(
                  new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) {
                      if (!accepted.complete(channel)) {
                        channel.close();
                        return;
                      }
                      channel.pipeline().addLast(new Reader(input));
                    }
                  })
              .bind(address)
              .syncUninterruptibly()
              .channel();
    }

    SocketAddress address() {
      return address;
    }

    /** Waits for the client. Returns null on timeout. */
    Channel accept(long timeoutMs) throws InterruptedException {
      try {
        Channel c = accepted.get(timeoutMs, TimeUnit.MILLISECONDS);
        listener.close();
        return c;
      } catch (java.util.concurrent.TimeoutException e) {
        return null;
      } catch (java.util.concurrent.ExecutionException e) {
        return null;
      }
    }

    /** What the client sends, as one stream of frames. */
    InputStream input() {
      return input;
    }

    /** Writes to the client; {@link OutputStream#flush()} sends what was written. */
    OutputStream output(Channel channel) {
      return new Output(channel);
    }

    @Override
    public void close() {
      listener.close();
      Channel c = accepted.getNow(null);
      accepted.cancel(false);
      if (c != null) {
        c.close();
      }
      input.end();
    }
  }

  /**
   * Opens the client's end: a {@link Connection} with the network pipeline (varint frames, the
   * game's packet codecs) over a local channel.
   */
  static Connection connect(SocketAddress address) {
    Connection connection = new Connection(PacketFlow.CLIENTBOUND);
    new Bootstrap()
        .group(group())
        .channel(LocalChannel.class)
        .handler(
            new ChannelInitializer<Channel>() {
              @Override
              protected void initChannel(Channel channel) {
                Connection.configureSerialization(
                    channel.pipeline(), PacketFlow.CLIENTBOUND, false, null);
                connection.configurePacketHandler(channel.pipeline());
              }
            })
        .connect(address)
        .syncUninterruptibly();
    return connection;
  }

  /** Copies inbound buffers into {@link Input}. */
  private static final class Reader extends ChannelInboundHandlerAdapter {
    private final Input input;

    Reader(Input input) {
      this.input = input;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
      if (msg instanceof ByteBuf buf) {
        try {
          if (buf.isReadable()) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            input.offer(bytes);
          }
        } finally {
          buf.release();
        }
      } else {
        io.netty.util.ReferenceCountUtil.release(msg);
      }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
      input.end();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      ctx.close();
    }
  }

  /** A blocking stream over the chunks the client sent. Ends when the channel closes. */
  private static final class Input extends InputStream {
    private static final byte[] END = new byte[0];
    private final LinkedBlockingQueue<byte[]> chunks = new LinkedBlockingQueue<>();
    private byte[] current = new byte[0];
    private int pos;
    private boolean ended;

    void offer(byte[] bytes) {
      chunks.add(bytes);
    }

    void end() {
      chunks.add(END);
    }

    private boolean fill() throws IOException {
      while (pos >= current.length) {
        if (ended) {
          return false;
        }
        byte[] next;
        try {
          next = chunks.take();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new java.io.InterruptedIOException();
        }
        if (next == END) {
          ended = true;
          return false;
        }
        current = next;
        pos = 0;
      }
      return true;
    }

    @Override
    public int read() throws IOException {
      return fill() ? current[pos++] & 0xFF : -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (len == 0) {
        return 0;
      }
      if (!fill()) {
        return -1;
      }
      int n = Math.min(len, current.length - pos);
      System.arraycopy(current, pos, b, off, n);
      pos += n;
      return n;
    }

    @Override
    public void close() {
      end();
    }
  }

  /** Collects writes and sends them as one buffer on flush. Replay thread only. */
  private static final class Output extends OutputStream {
    private final Channel channel;
    private ByteBuf pending = Unpooled.buffer();

    Output(Channel channel) {
      this.channel = channel;
    }

    @Override
    public void write(int b) {
      pending.writeByte(b);
    }

    @Override
    public void write(byte[] b, int off, int len) {
      pending.writeBytes(b, off, len);
    }

    @Override
    public void flush() throws IOException {
      if (!channel.isActive()) {
        throw new IOException("replay client disconnected");
      }
      if (pending.isReadable()) {
        ByteBuf out = pending;
        pending = Unpooled.buffer();
        channel.writeAndFlush(out).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
      }
    }

    @Override
    public void close() {
      pending.release();
    }
  }
}
