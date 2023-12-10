package net.minecraft.server.network;

import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.logging.LogUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RateKickingConnection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

public class ServerConnectionListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Supplier<NioEventLoopGroup> SERVER_EVENT_GROUP = Suppliers.memoize(() -> {
        return new NioEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Server IO #%d").setDaemon(true).setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(LOGGER)).build()); // Paper
    });
    public static final Supplier<EpollEventLoopGroup> SERVER_EPOLL_EVENT_GROUP = Suppliers.memoize(() -> {
        return new EpollEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Epoll Server IO #%d").setDaemon(true).setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(LOGGER)).build()); // Paper
    });
    final MinecraftServer server;
    public volatile boolean running;
    private final List<ChannelFuture> channels = Collections.synchronizedList(Lists.newArrayList());
    final List<Connection> connections = Collections.synchronizedList(Lists.newArrayList());
    // Paper start - prevent blocking on adding a new network manager while the server is ticking
    private final java.util.Queue<Connection> pending = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final boolean disableFlushConsolidation = Boolean.getBoolean("Paper.disableFlushConsolidate"); // Paper - Optimize network
    private final void addPending() {
        Connection manager = null;
        while ((manager = pending.poll()) != null) {
            connections.add(manager);
            manager.isPending = false; // Paper - Optimize network
        }
    }
    // Paper end

    public ServerConnectionListener(MinecraftServer server) {
        this.server = server;
        this.running = true;
    }

    // Paper start
    public void startTcpServerListener(@Nullable InetAddress address, int port) throws IOException {
        bind(new java.net.InetSocketAddress(address, port));
    }
    public void bind(java.net.SocketAddress address) throws IOException {
    // Paper end
        List list = this.channels;

        synchronized (this.channels) {
            Class oclass;
            EventLoopGroup eventloopgroup;

            if (Epoll.isAvailable() && this.server.isEpollEnabled()) {
                // Paper start
                if (address instanceof io.netty.channel.unix.DomainSocketAddress) {
                    oclass = io.netty.channel.epoll.EpollServerDomainSocketChannel.class;
                } else {
                oclass = EpollServerSocketChannel.class;
                }
                // Paper end
                eventloopgroup = (EventLoopGroup) ServerConnectionListener.SERVER_EPOLL_EVENT_GROUP.get();
                ServerConnectionListener.LOGGER.info("Using epoll channel type");
            } else {
                oclass = NioServerSocketChannel.class;
                eventloopgroup = (EventLoopGroup) ServerConnectionListener.SERVER_EVENT_GROUP.get();
                ServerConnectionListener.LOGGER.info("Using default channel type");
            }

            // Paper start - indicate Velocity natives in use
            ServerConnectionListener.LOGGER.info("Paper: Using " + com.velocitypowered.natives.util.Natives.compress.getLoadedVariant() + " compression from Velocity.");
            ServerConnectionListener.LOGGER.info("Paper: Using " + com.velocitypowered.natives.util.Natives.cipher.getLoadedVariant() + " cipher from Velocity.");
            // Paper end

            // Paper start - indicate Proxy Protocol usage
            if (io.papermc.paper.configuration.GlobalConfiguration.get().proxies.proxyProtocol) {
                ServerConnectionListener.LOGGER.info("Paper: Using Proxy Protocol");
            }
            // Paper end

            this.channels.add(((ServerBootstrap) ((ServerBootstrap) (new ServerBootstrap()).channel(oclass)).childHandler(new ChannelInitializer<Channel>() {
                protected void initChannel(Channel channel) {
                    Connection.setInitialProtocolAttributes(channel);

                    try {
                        channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                    } catch (ChannelException channelexception) {
                        ;
                    }

                    if (!disableFlushConsolidation) channel.pipeline().addFirst(new io.netty.handler.flush.FlushConsolidationHandler()); // Paper - Optimize network
                    ChannelPipeline channelpipeline = channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30)).addLast("legacy_query", new LegacyQueryHandler(ServerConnectionListener.this.getServer()));

                    Connection.configureSerialization(channelpipeline, PacketFlow.SERVERBOUND, (BandwidthDebugMonitor) null);
                    int j = ServerConnectionListener.this.server.getRateLimitPacketsPerSecond();
                    Connection object = j > 0 ? new RateKickingConnection(j) : new Connection(PacketFlow.SERVERBOUND); // CraftBukkit - decompile error

                    //ServerConnectionListener.this.connections.add(object); // Paper
                    // Paper start - Add support for Proxy Protocol
                    if (io.papermc.paper.configuration.GlobalConfiguration.get().proxies.proxyProtocol) {
                        channel.pipeline().addAfter("timeout", "haproxy-decoder", new io.netty.handler.codec.haproxy.HAProxyMessageDecoder());
                        channel.pipeline().addAfter("haproxy-decoder", "haproxy-handler", new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                if (msg instanceof io.netty.handler.codec.haproxy.HAProxyMessage message) {
                                    if (message.command() == io.netty.handler.codec.haproxy.HAProxyCommand.PROXY) {
                                        String realaddress = message.sourceAddress();
                                        int realport = message.sourcePort();

                                        SocketAddress socketaddr = new java.net.InetSocketAddress(realaddress, realport);

                                        Connection connection = (Connection) channel.pipeline().get("packet_handler");
                                        connection.address = socketaddr;
                                    }
                                } else {
                                    super.channelRead(ctx, msg);
                                }
                            }
                        });
                    }
                    // Paper end
                    // Folia - connection fixes - move down
                    ((Connection) object).configurePacketHandler(channelpipeline);
                    ((Connection) object).setListenerForServerboundHandshake(new ServerHandshakePacketListenerImpl(ServerConnectionListener.this.server, (Connection) object));
                    io.papermc.paper.network.ChannelInitializeListenerHolder.callListeners(channel); // Paper
                    // Folia start - regionised threading
                    io.papermc.paper.threadedregions.RegionizedServer.getInstance().addConnection(object);
                    // Folia end - regionised threading
                }
            }).group(eventloopgroup).localAddress(address)).option(ChannelOption.AUTO_READ, false).bind().syncUninterruptibly()); // CraftBukkit // Paper
        }
    }

    // CraftBukkit start
    public void acceptConnections() {
        synchronized (this.channels) {
            for (ChannelFuture future : this.channels) {
                future.channel().config().setAutoRead(true);
            }
        }
    }
    // CraftBukkit end

    public SocketAddress startMemoryChannel() {
        List list = this.channels;
        ChannelFuture channelfuture;

        synchronized (this.channels) {
            channelfuture = ((ServerBootstrap) ((ServerBootstrap) (new ServerBootstrap()).channel(LocalServerChannel.class)).childHandler(new ChannelInitializer<Channel>() {
                protected void initChannel(Channel channel) {
                    Connection.setInitialProtocolAttributes(channel);
                    Connection networkmanager = new Connection(PacketFlow.SERVERBOUND);

                    networkmanager.setListenerForServerboundHandshake(new MemoryServerHandshakePacketListenerImpl(ServerConnectionListener.this.server, networkmanager));
                    ServerConnectionListener.this.connections.add(networkmanager);
                    ChannelPipeline channelpipeline = channel.pipeline();

                    Connection.configureInMemoryPipeline(channelpipeline, PacketFlow.SERVERBOUND);
                    networkmanager.configurePacketHandler(channelpipeline);
                }
            }).group((EventLoopGroup) ServerConnectionListener.SERVER_EVENT_GROUP.get()).localAddress(LocalAddress.ANY)).bind().syncUninterruptibly();
            this.channels.add(channelfuture);
        }

        return channelfuture.channel().localAddress();
    }

    public void stop() {
        this.running = false;
        Iterator iterator = this.channels.iterator();

        while (iterator.hasNext()) {
            ChannelFuture channelfuture = (ChannelFuture) iterator.next();

            try {
                channelfuture.channel().close().sync();
            } catch (InterruptedException interruptedexception) {
                ServerConnectionListener.LOGGER.error("Interrupted whilst closing channel");
            }
        }

    }

    public void tick() {
        List list = this.connections;

        synchronized (this.connections) {
            // Spigot Start
            this.addPending(); // Paper
            // This prevents players from 'gaming' the server, and strategically relogging to increase their position in the tick order
            if ( org.spigotmc.SpigotConfig.playerShuffle > 0 && 0 % org.spigotmc.SpigotConfig.playerShuffle == 0 ) // Folia - region threading
            {
                Collections.shuffle( this.connections );
            }
            // Spigot End
            Iterator iterator = this.connections.iterator();

            while (iterator.hasNext()) {
                Connection networkmanager = (Connection) iterator.next();

                if (!networkmanager.isConnecting()) {
                    if (networkmanager.isConnected()) {
                        try {
                            networkmanager.tick();
                        } catch (Exception exception) {
                            if (networkmanager.isMemoryConnection()) {
                                throw new ReportedException(CrashReport.forThrowable(exception, "Ticking memory connection"));
                            }

                            ServerConnectionListener.LOGGER.warn("Failed to handle packet for {}", networkmanager.getLoggableAddress(this.server.logIPs()), exception);
                            MutableComponent ichatmutablecomponent = Component.literal("Internal server error");

                            networkmanager.send(new ClientboundDisconnectPacket(ichatmutablecomponent), PacketSendListener.thenRun(() -> {
                                networkmanager.disconnect(ichatmutablecomponent);
                            }));
                            networkmanager.setReadOnly();
                        }
                    } else {
                        // Spigot Start
                        // Fix a race condition where a NetworkManager could be unregistered just before connection.
                        if (networkmanager.preparing) continue;
                        // Spigot End
                        iterator.remove();
                        networkmanager.handleDisconnection();
                    }
                }
            }

        }
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public List<Connection> getConnections() {
        return this.connections;
    }

    private static class LatencySimulator extends ChannelInboundHandlerAdapter {

        private static final Timer TIMER = new HashedWheelTimer();
        private final int delay;
        private final int jitter;
        private final List<ServerConnectionListener.LatencySimulator.DelayedMessage> queuedMessages = Lists.newArrayList();

        public LatencySimulator(int baseDelay, int extraDelay) {
            this.delay = baseDelay;
            this.jitter = extraDelay;
        }

        public void channelRead(ChannelHandlerContext channelhandlercontext, Object object) {
            this.delayDownstream(channelhandlercontext, object);
        }

        private void delayDownstream(ChannelHandlerContext ctx, Object msg) {
            int i = this.delay + (int) (Math.random() * (double) this.jitter);

            this.queuedMessages.add(new ServerConnectionListener.LatencySimulator.DelayedMessage(ctx, msg));
            ServerConnectionListener.LatencySimulator.TIMER.newTimeout(this::onTimeout, (long) i, TimeUnit.MILLISECONDS);
        }

        private void onTimeout(Timeout timeout) {
            ServerConnectionListener.LatencySimulator.DelayedMessage serverconnection_latencysimulator_delayedmessage = (ServerConnectionListener.LatencySimulator.DelayedMessage) this.queuedMessages.remove(0);

            serverconnection_latencysimulator_delayedmessage.ctx.fireChannelRead(serverconnection_latencysimulator_delayedmessage.msg);
        }

        private static class DelayedMessage {

            public final ChannelHandlerContext ctx;
            public final Object msg;

            public DelayedMessage(ChannelHandlerContext context, Object message) {
                this.ctx = context;
                this.msg = message;
            }
        }
    }
}
