package net.minecraft.network;

import com.google.common.base.Suppliers;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.logging.LogUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.flow.FlowControlHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.crypto.Cipher;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.handshake.ClientIntent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.ClientLoginPacketListener;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.status.ClientStatusPacketListener;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.util.Mth;
import net.minecraft.util.SampleLogger;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class Connection extends SimpleChannelInboundHandler<Packet<?>> {

    private static final float AVERAGE_PACKETS_SMOOTHING = 0.75F;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Marker ROOT_MARKER = MarkerFactory.getMarker("NETWORK");
    public static final Marker PACKET_MARKER = (Marker) Util.make(MarkerFactory.getMarker("NETWORK_PACKETS"), (marker) -> {
        marker.add(Connection.ROOT_MARKER);
    });
    public static final Marker PACKET_RECEIVED_MARKER = (Marker) Util.make(MarkerFactory.getMarker("PACKET_RECEIVED"), (marker) -> {
        marker.add(Connection.PACKET_MARKER);
    });
    public static final Marker PACKET_SENT_MARKER = (Marker) Util.make(MarkerFactory.getMarker("PACKET_SENT"), (marker) -> {
        marker.add(Connection.PACKET_MARKER);
    });
    public static final AttributeKey<ConnectionProtocol.CodecData<?>> ATTRIBUTE_SERVERBOUND_PROTOCOL = AttributeKey.valueOf("serverbound_protocol");
    public static final AttributeKey<ConnectionProtocol.CodecData<?>> ATTRIBUTE_CLIENTBOUND_PROTOCOL = AttributeKey.valueOf("clientbound_protocol");
    public static final Supplier<NioEventLoopGroup> NETWORK_WORKER_GROUP = Suppliers.memoize(() -> {
        return new NioEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Client IO #%d").setDaemon(true).setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(LOGGER)).build()); // Paper
    });
    public static final Supplier<EpollEventLoopGroup> NETWORK_EPOLL_WORKER_GROUP = Suppliers.memoize(() -> {
        return new EpollEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Epoll Client IO #%d").setDaemon(true).setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(LOGGER)).build()); // Paper
    });
    public static final Supplier<DefaultEventLoopGroup> LOCAL_WORKER_GROUP = Suppliers.memoize(() -> {
        return new DefaultEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Local Client IO #%d").setDaemon(true).setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(LOGGER)).build()); // Paper
    });
    private final PacketFlow receiving;
    private final Queue<WrappedConsumer> pendingActions = new ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue<>(); // Folia - region threading - connection fixes
    public Channel channel;
    public SocketAddress address;
    // Spigot Start
    public java.util.UUID spoofedUUID;
    public com.mojang.authlib.properties.Property[] spoofedProfile;
    public boolean preparing = true;
    // Spigot End
    @Nullable
    private volatile PacketListener disconnectListener;
    @Nullable
    private volatile PacketListener packetListener;
    @Nullable
    private Component disconnectedReason;
    private boolean encrypted;
    private final java.util.concurrent.atomic.AtomicBoolean disconnectionHandled = new java.util.concurrent.atomic.AtomicBoolean(false); // Folia - region threading - may be called concurrently during configuration stage
    private int receivedPackets;
    private int sentPackets;
    private float averageReceivedPackets;
    private float averageSentPackets;
    private int tickCount;
    private boolean handlingFault;
    @Nullable
    private volatile Component delayedDisconnect;
    @Nullable
    BandwidthDebugMonitor bandwidthDebugMonitor;
    public String hostname = ""; // CraftBukkit - add field
    // Paper start - NetworkClient implementation
    public int protocolVersion;
    public java.net.InetSocketAddress virtualHost;
    private static boolean enableExplicitFlush = Boolean.getBoolean("paper.explicit-flush");
    // Paper end
    // Paper start - Optimize network
    public boolean isPending = true;
    public boolean queueImmunity;
    // Paper end - Optimize network

    // Paper start - add utility methods
    public final net.minecraft.server.level.ServerPlayer getPlayer() {
        if (this.packetListener instanceof net.minecraft.server.network.ServerGamePacketListenerImpl impl) {
            return impl.player;
        } else if (this.packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl impl) {
            org.bukkit.craftbukkit.entity.CraftPlayer player = impl.getCraftPlayer();
            return player == null ? null : player.getHandle();
        }
        return null;
    }
    // Paper end - add utility methods
    // Paper start - packet limiter
    protected final Object PACKET_LIMIT_LOCK = new Object();
    protected final @Nullable io.papermc.paper.util.IntervalledCounter allPacketCounts = io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.allPackets.isEnabled() ? new io.papermc.paper.util.IntervalledCounter(
        (long)(io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.allPackets.interval() * 1.0e9)
    ) : null;
    protected final java.util.Map<Class<? extends net.minecraft.network.protocol.Packet<?>>, io.papermc.paper.util.IntervalledCounter> packetSpecificLimits = new java.util.HashMap<>();

    private boolean stopReadingPackets;
    private void killForPacketSpam() {
        this.sendPacket(new ClientboundDisconnectPacket(io.papermc.paper.adventure.PaperAdventure.asVanilla(io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.kickMessage)), PacketSendListener.thenRun(() -> {
            this.disconnect(io.papermc.paper.adventure.PaperAdventure.asVanilla(io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.kickMessage));
        }), true);
        this.setReadOnly();
        this.stopReadingPackets = true;
    }
    // Paper end - packet limiter

    public Connection(PacketFlow side) {
        this.receiving = side;
    }

    // Folia start - region threading
    private volatile boolean becomeActive;

    public boolean becomeActive() {
        return this.becomeActive;
    }

    private static record DisconnectReq(Component disconnectReason, org.bukkit.event.player.PlayerKickEvent.Cause cause) {}

    private final ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue<DisconnectReq> disconnectReqs =
        new ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue<>();

    /**
     * Safely disconnects the connection while possibly on another thread. Note: This call will not block, even if on the
     * same thread that could disconnect.
     */
    public final void disconnectSafely(Component disconnectReason, org.bukkit.event.player.PlayerKickEvent.Cause cause) {
        this.disconnectReqs.add(new DisconnectReq(disconnectReason, cause));
        // We can't halt packet processing here because a plugin could cancel a kick request.
    }

    public final boolean isPlayerConnected() {
        return this.packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl;
    }
    // Folia end - region threading

    public void channelActive(ChannelHandlerContext channelhandlercontext) throws Exception {
        super.channelActive(channelhandlercontext);
        this.channel = channelhandlercontext.channel();
        this.address = this.channel.remoteAddress();
        // Spigot Start
        this.preparing = false;
        // Spigot End
        if (this.delayedDisconnect != null) {
            this.disconnect(this.delayedDisconnect);
        }
        this.becomeActive = true; // Folia - region threading
    }

    public static void setInitialProtocolAttributes(Channel channel) {
        channel.attr(Connection.ATTRIBUTE_SERVERBOUND_PROTOCOL).set(ConnectionProtocol.HANDSHAKING.codec(PacketFlow.SERVERBOUND));
        channel.attr(Connection.ATTRIBUTE_CLIENTBOUND_PROTOCOL).set(ConnectionProtocol.HANDSHAKING.codec(PacketFlow.CLIENTBOUND));
    }

    public void channelInactive(ChannelHandlerContext channelhandlercontext) {
        this.disconnect(Component.translatable("disconnect.endOfStream"));
    }

    public void exceptionCaught(ChannelHandlerContext channelhandlercontext, Throwable throwable) {
        // Paper start
        if (throwable instanceof io.netty.handler.codec.EncoderException && throwable.getCause() instanceof PacketEncoder.PacketTooLargeException packetTooLargeException) {
            final Packet<?> packet = packetTooLargeException.getPacket();
            final io.netty.util.Attribute<ConnectionProtocol.CodecData<?>> codecDataAttribute = channelhandlercontext.channel().attr(packetTooLargeException.codecKey);
            if (packet.packetTooLarge(this)) {
                ProtocolSwapHandler.swapProtocolIfNeeded(codecDataAttribute, packet);
                return;
            } else if (packet.isSkippable()) {
                Connection.LOGGER.debug("Skipping packet due to errors", throwable.getCause());
                ProtocolSwapHandler.swapProtocolIfNeeded(codecDataAttribute, packet);
                return;
            } else {
                throwable = throwable.getCause();
            }
        }
        // Paper end
        if (throwable instanceof SkipPacketException) {
            Connection.LOGGER.debug("Skipping packet due to errors", throwable.getCause());
        } else {
            boolean flag = !this.handlingFault;

            this.handlingFault = true;
            if (this.channel.isOpen()) {
                net.minecraft.server.level.ServerPlayer player = this.getPlayer(); // Paper
                if (throwable instanceof TimeoutException) {
                    Connection.LOGGER.debug("Timeout", throwable);
                    if (player != null) player.quitReason = org.bukkit.event.player.PlayerQuitEvent.QuitReason.TIMED_OUT; // Paper
                    this.disconnect(Component.translatable("disconnect.timeout"));
                } else {
                    MutableComponent ichatmutablecomponent = Component.translatable("disconnect.genericReason", "Internal Exception: " + throwable);

                    if (player != null) player.quitReason = org.bukkit.event.player.PlayerQuitEvent.QuitReason.ERRONEOUS_STATE; // Paper
                    if (flag) {
                        Connection.LOGGER.debug("Failed to sent packet", throwable);
                        if (this.getSending() == PacketFlow.CLIENTBOUND) {
                            ConnectionProtocol enumprotocol = ((ConnectionProtocol.CodecData) this.channel.attr(Connection.ATTRIBUTE_CLIENTBOUND_PROTOCOL).get()).protocol();
                            Packet<?> packet = enumprotocol == ConnectionProtocol.LOGIN ? new ClientboundLoginDisconnectPacket(ichatmutablecomponent) : new ClientboundDisconnectPacket(ichatmutablecomponent);

                            this.send((Packet) packet, PacketSendListener.thenRun(() -> {
                                this.disconnect(ichatmutablecomponent);
                            }));
                        } else {
                            this.disconnect(ichatmutablecomponent);
                        }

                        this.setReadOnly();
                    } else {
                        Connection.LOGGER.debug("Double fault", throwable);
                        this.disconnect(ichatmutablecomponent);
                    }
                }

            }
        }
        if (net.minecraft.server.MinecraftServer.getServer().isDebugging()) io.papermc.paper.util.TraceUtil.printStackTrace(throwable); // Spigot // Paper
    }

    protected void channelRead0(ChannelHandlerContext channelhandlercontext, Packet<?> packet) {
        if (this.channel.isOpen()) {
            PacketListener packetlistener = this.packetListener;

            if (packetlistener == null) {
                throw new IllegalStateException("Received a packet before the packet listener was initialized");
            } else {
                // Paper start - packet limiter
                if (this.stopReadingPackets) {
                    return;
                }
                if (this.allPacketCounts != null ||
                    io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.overrides.containsKey(packet.getClass())) {
                    long time = System.nanoTime();
                    synchronized (PACKET_LIMIT_LOCK) {
                        if (this.allPacketCounts != null) {
                            this.allPacketCounts.updateAndAdd(1, time);
                            if (this.allPacketCounts.getRate() >= io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.allPackets.maxPacketRate()) {
                                this.killForPacketSpam();
                                return;
                            }
                        }

                        for (Class<?> check = packet.getClass(); check != Object.class; check = check.getSuperclass()) {
                            io.papermc.paper.configuration.GlobalConfiguration.PacketLimiter.PacketLimit packetSpecificLimit =
                                io.papermc.paper.configuration.GlobalConfiguration.get().packetLimiter.overrides.get(check);
                            if (packetSpecificLimit == null || !packetSpecificLimit.isEnabled()) {
                                continue;
                            }
                            io.papermc.paper.util.IntervalledCounter counter = this.packetSpecificLimits.computeIfAbsent((Class)check, (clazz) -> {
                                return new io.papermc.paper.util.IntervalledCounter((long)(packetSpecificLimit.interval() * 1.0e9));
                            });
                            counter.updateAndAdd(1, time);
                            if (counter.getRate() >= packetSpecificLimit.maxPacketRate()) {
                                switch (packetSpecificLimit.action()) {
                                    case DROP:
                                        return;
                                    case KICK:
                                        String deobfedPacketName = io.papermc.paper.util.ObfHelper.INSTANCE.deobfClassName(check.getName());

                                        String playerName;
                                        if (this.packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl impl) {
                                            playerName = impl.getOwner().getName();
                                        } else {
                                            playerName = this.getLoggableAddress(net.minecraft.server.MinecraftServer.getServer().logIPs());
                                        }

                                        Connection.LOGGER.warn("{} kicked for packet spamming: {}", playerName, deobfedPacketName.substring(deobfedPacketName.lastIndexOf(".") + 1));
                                        this.killForPacketSpam();
                                        return;
                                }
                            }
                        }
                    }
                }
                // Paper end - packet limiter
                if (packetlistener.shouldHandleMessage(packet)) {
                    try {
                        Connection.genericsFtw(packet, packetlistener);
                    } catch (RunningOnDifferentThreadException cancelledpackethandleexception) {
                        ;
                    } catch (RejectedExecutionException rejectedexecutionexception) {
                        this.disconnect(Component.translatable("multiplayer.disconnect.server_shutdown"));
                    } catch (ClassCastException classcastexception) {
                        Connection.LOGGER.error("Received {} that couldn't be processed", packet.getClass(), classcastexception);
                        this.disconnect(Component.translatable("multiplayer.disconnect.invalid_packet"));
                    }

                    ++this.receivedPackets;
                }

            }
        }
    }

    private static <T extends PacketListener> void genericsFtw(Packet<T> packet, PacketListener listener) {
        packet.handle((T) listener); // CraftBukkit - decompile error
    }

    public void suspendInboundAfterProtocolChange() {
        this.channel.config().setAutoRead(false);
    }

    public void resumeInboundAfterProtocolChange() {
        this.channel.config().setAutoRead(true);
    }

    public void setListener(PacketListener packetListener) {
        Validate.notNull(packetListener, "packetListener", new Object[0]);
        PacketFlow enumprotocoldirection = packetListener.flow();

        if (enumprotocoldirection != this.receiving) {
            throw new IllegalStateException("Trying to set listener for wrong side: connection is " + this.receiving + ", but listener is " + enumprotocoldirection);
        } else {
            ConnectionProtocol enumprotocol = packetListener.protocol();
            ConnectionProtocol enumprotocol1 = ((ConnectionProtocol.CodecData) this.channel.attr(Connection.getProtocolKey(enumprotocoldirection)).get()).protocol();

            if (enumprotocol1 != enumprotocol) {
                throw new IllegalStateException("Trying to set listener for protocol " + enumprotocol.id() + ", but current " + enumprotocoldirection + " protocol is " + enumprotocol1.id());
            } else {
                this.packetListener = packetListener;
                this.disconnectListener = null;
            }
        }
    }

    public void setListenerForServerboundHandshake(PacketListener packetListener) {
        if (this.packetListener != null) {
            throw new IllegalStateException("Listener already set");
        } else if (this.receiving == PacketFlow.SERVERBOUND && packetListener.flow() == PacketFlow.SERVERBOUND && packetListener.protocol() == ConnectionProtocol.HANDSHAKING) {
            this.packetListener = packetListener;
        } else {
            throw new IllegalStateException("Invalid initial listener");
        }
    }

    public void initiateServerboundStatusConnection(String address, int port, ClientStatusPacketListener listener) {
        this.initiateServerboundConnection(address, port, listener, ClientIntent.STATUS);
    }

    public void initiateServerboundPlayConnection(String address, int port, ClientLoginPacketListener listener) {
        this.initiateServerboundConnection(address, port, listener, ClientIntent.LOGIN);
    }

    private void initiateServerboundConnection(String address, int port, PacketListener listener, ClientIntent intent) {
        this.disconnectListener = listener;
        this.runOnceConnected((networkmanager) -> {
            networkmanager.setClientboundProtocolAfterHandshake(intent);
            this.setListener(listener);
            networkmanager.sendPacket(new ClientIntentionPacket(SharedConstants.getCurrentVersion().getProtocolVersion(), address, port, intent), (PacketSendListener) null, true);
        });
    }

    public void setClientboundProtocolAfterHandshake(ClientIntent intent) {
        this.channel.attr(Connection.ATTRIBUTE_CLIENTBOUND_PROTOCOL).set(intent.protocol().codec(PacketFlow.CLIENTBOUND));
    }

    public void send(Packet<?> packet) {
        this.send(packet, (PacketSendListener) null);
    }

    public void send(Packet<?> packet, @Nullable PacketSendListener callbacks) {
        this.send(packet, callbacks, true);
    }

    public void send(Packet<?> packet, @Nullable PacketSendListener callbacks, boolean flush) {
        // Paper start - Optimize network: Handle oversized packets better
        final boolean connected = this.isConnected();
        if (!connected && !this.preparing) {
            return;
        }

        packet.onPacketDispatch(this.getPlayer());
        if (false && connected && (InnerUtil.canSendImmediate(this, packet) // Folia - region threading - connection fixes
            || (io.papermc.paper.util.MCUtil.isMainThread() && packet.isReady() && this.pendingActions.isEmpty()
            && (packet.getExtraPackets() == null || packet.getExtraPackets().isEmpty())))) {
            this.sendPacket(packet, callbacks, flush);
        } else {
            // Write the packets to the queue, then flush - antixray hooks there already
            final java.util.List<Packet<?>> extraPackets = InnerUtil.buildExtraPackets(packet);
            final boolean hasExtraPackets = extraPackets != null && !extraPackets.isEmpty();
            if (!hasExtraPackets) {
                this.pendingActions.add(new PacketSendAction(packet, callbacks, flush));
            } else {
                final java.util.List<PacketSendAction> actions = new java.util.ArrayList<>(1 + extraPackets.size());
                actions.add(new PacketSendAction(packet, null, false)); // Delay the future listener until the end of the extra packets

                for (int i = 0, len = extraPackets.size(); i < len;) {
                    final Packet<?> extraPacket = extraPackets.get(i);
                    final boolean end = ++i == len;
                    actions.add(new PacketSendAction(extraPacket, end ? callbacks : null, end)); // Append listener to the end
                }

                this.pendingActions.addAll(actions);
            }

            this.flushQueue();
            // Paper end - Optimize network
        }
    }

    public void runOnceConnected(Consumer<Connection> task) {
        if (false && this.isConnected()) { // Folia - region threading - connection fixes
            this.flushQueue();
            task.accept(this);
        } else {
            this.pendingActions.add(new WrappedConsumer(task)); // Paper - Optimize network
            this.flushQueue(); // Folia - region threading - connection fixes
        }

    }

    private void sendPacket(Packet<?> packet, @Nullable PacketSendListener callbacks, boolean flush) {
        ++this.sentPackets;
        if (this.channel.eventLoop().inEventLoop()) {
            this.doSendPacket(packet, callbacks, flush);
        } else {
            this.channel.eventLoop().execute(() -> {
                this.doSendPacket(packet, callbacks, flush);
            });
        }

    }

    private void doSendPacket(Packet<?> packet, @Nullable PacketSendListener callbacks, boolean flush) {
        // Paper start - Optimize network
        final net.minecraft.server.level.ServerPlayer player = this.getPlayer();
        if (!this.isConnected()) {
            packet.onPacketDispatchFinish(player, null);
            return;
        }
        try {
        // Paper end - Optimize network
        ChannelFuture channelfuture = flush ? this.channel.writeAndFlush(packet) : this.channel.write(packet);

        if (callbacks != null) {
            channelfuture.addListener((future) -> {
                if (future.isSuccess()) {
                    callbacks.onSuccess();
                } else {
                    Packet<?> packet1 = callbacks.onFailure();

                    if (packet1 != null) {
                        ChannelFuture channelfuture1 = this.channel.writeAndFlush(packet1);

                        channelfuture1.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                    }
                }

            });
        }

        // Paper start - Optimize network
        if (packet.hasFinishListener()) {
            channelfuture.addListener((ChannelFutureListener) channelFuture -> packet.onPacketDispatchFinish(player, channelFuture));
        }
        channelfuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        } catch (final Exception e) {
            LOGGER.error("NetworkException: {}", player, e);
            this.disconnect(Component.translatable("disconnect.genericReason", "Internal Exception: " + e.getMessage()));
            packet.onPacketDispatchFinish(player, null);
        }
        // Paper end - Optimize network
    }

    public void flushChannel() {
        if (false && this.isConnected()) { // Folia - region threading - connection fixes
            this.flush();
        } else {
            this.pendingActions.add(new WrappedConsumer(Connection::flush)); // Paper - Optimize network
            this.flushQueue(); // Folia - region threading - connection fixes
        }

    }

    private void flush() {
        if (this.channel.eventLoop().inEventLoop()) {
            this.channel.flush();
        } else {
            this.channel.eventLoop().execute(() -> {
                this.channel.flush();
            });
        }

    }

    private static AttributeKey<ConnectionProtocol.CodecData<?>> getProtocolKey(PacketFlow side) {
        AttributeKey attributekey;

        switch (side) {
            case CLIENTBOUND:
                attributekey = Connection.ATTRIBUTE_CLIENTBOUND_PROTOCOL;
                break;
            case SERVERBOUND:
                attributekey = Connection.ATTRIBUTE_SERVERBOUND_PROTOCOL;
                break;
            default:
                throw new IncompatibleClassChangeError();
        }

        return attributekey;
    }

    // Paper start - Optimize network: Rewrite this to be safer if ran off main thread
    private boolean flushQueue() {
        return this.processQueue(); // Folia - region threading - connection fixes
    }

    // Folia start - region threading - connection fixes
    // allow only one thread to be flushing the queue at once to ensure packets are written in the order they are sent
    // into the queue
    private final java.util.concurrent.atomic.AtomicBoolean flushingQueue = new java.util.concurrent.atomic.AtomicBoolean();

    private static boolean canWrite(WrappedConsumer queued) {
        return queued != null && (!(queued instanceof PacketSendAction packet) || packet.packet.isReady());
    }

    private boolean canWritePackets() {
        return canWrite(this.pendingActions.peek());
    }
    // Folia end - region threading - connection fixes

    private boolean processQueue() {
        // Folia start - region threading - connection fixes
        if (!this.isConnected()) {
            return true;
        }

        while (this.canWritePackets()) {
            final boolean set = this.flushingQueue.getAndSet(true);
            try {
                if (set) {
                    // we didn't acquire the lock, break
                    return false;
                }

                ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue<WrappedConsumer> queue =
                    (ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue<WrappedConsumer>)this.pendingActions;
                WrappedConsumer holder;
                for (;;) {
                    // synchronise so that queue clears appear atomic
                    synchronized (queue) {
                        holder = queue.pollIf(Connection::canWrite);
                    }
                    if (holder == null) {
                        break;
                    }

                    holder.accept(this);
                }

            } finally {
                if (!set) {
                    this.flushingQueue.set(false);
                }
            }
        }

        return true;
        // Folia end - region threading - connection fixes
    }
    // Paper end - Optimize network

    private static final int MAX_PER_TICK = io.papermc.paper.configuration.GlobalConfiguration.get().misc.maxJoinsPerTick; // Paper
    private static int joinAttemptsThisTick; // Paper
    private static int currTick; // Paper
    public void tick() {
        this.flushQueue();
        // Folia start - region threading
        // handle disconnect requests, but only after flushQueue()
        DisconnectReq disconnectReq;
        while ((disconnectReq = this.disconnectReqs.poll()) != null) {
            PacketListener packetlistener = this.packetListener;

            if (packetlistener instanceof net.minecraft.server.network.ServerLoginPacketListenerImpl loginPacketListener) {
                loginPacketListener.disconnect(disconnectReq.disconnectReason);
                // this doesn't fail, so abort any further attempts
                return;
            } else if (packetlistener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl commonPacketListener) {
                commonPacketListener.disconnect(disconnectReq.disconnectReason, disconnectReq.cause);
                // may be cancelled by a plugin, if not cancelled then any further calls do nothing
                continue;
            } else {
                // no idea what packet to send
                this.disconnect(disconnectReq.disconnectReason);
                this.setReadOnly();
                return;
            }
        }
        if (!this.isConnected()) {
            // disconnected from above
            this.handleDisconnection();
            return;
        }
        // Folia end - region threading
        // Folia - this is broken
        PacketListener packetlistener = this.packetListener;

        if (packetlistener instanceof TickablePacketListener) {
            TickablePacketListener tickablepacketlistener = (TickablePacketListener) packetlistener;

            // Paper start - limit the number of joins which can be processed each tick
            if (true) { // Folia - region threading
            // Paper start - detailed watchdog information
            net.minecraft.network.protocol.PacketUtils.packetProcessing.push(this.packetListener);
            try { // Paper end - detailed watchdog information
            tickablepacketlistener.tick();
            } finally { // Paper start - detailed watchdog information
                net.minecraft.network.protocol.PacketUtils.packetProcessing.pop();
            } // Paper end - detailed watchdog information
            }
            // Paper end
        }

        if (!this.isConnected()) {// Folia - region threading - it's fine to call if it is already handled, as it no longer logs
            this.handleDisconnection();
        }

        if (this.channel != null) {
            if (enableExplicitFlush) this.channel.eventLoop().execute(() -> this.channel.flush()); // Paper - we don't need to explicit flush here, but allow opt in incase issues are found to a better version
        }

        if (this.tickCount++ % 20 == 0) {
            this.tickSecond();
        }

        if (this.bandwidthDebugMonitor != null) {
            this.bandwidthDebugMonitor.tick();
        }

    }

    protected void tickSecond() {
        this.averageSentPackets = Mth.lerp(0.75F, (float) this.sentPackets, this.averageSentPackets);
        this.averageReceivedPackets = Mth.lerp(0.75F, (float) this.receivedPackets, this.averageReceivedPackets);
        this.sentPackets = 0;
        this.receivedPackets = 0;
    }

    public SocketAddress getRemoteAddress() {
        return this.address;
    }

    public String getLoggableAddress(boolean logIps) {
        return this.address == null ? "local" : (logIps ? this.address.toString() : "IP hidden");
    }

    public void disconnect(Component disconnectReason) {
        // Spigot Start
        this.preparing = false;
        this.clearPacketQueue(); // Paper - Optimize network
        // Spigot End
        if (this.channel == null) {
            this.delayedDisconnect = disconnectReason;
        }

        if (this.isConnected()) {
            this.channel.close(); // We can't wait as this may be called from an event loop.
            this.disconnectedReason = disconnectReason;
        }
        this.becomeActive = true; // Folia - region threading

    }

    public boolean isMemoryConnection() {
        return this.channel instanceof LocalChannel || this.channel instanceof LocalServerChannel;
    }

    public PacketFlow getReceiving() {
        return this.receiving;
    }

    public PacketFlow getSending() {
        return this.receiving.getOpposite();
    }

    public static Connection connectToServer(InetSocketAddress address, boolean useEpoll, @Nullable SampleLogger packetSizeLog) {
        Connection networkmanager = new Connection(PacketFlow.CLIENTBOUND);

        if (packetSizeLog != null) {
            networkmanager.setBandwidthLogger(packetSizeLog);
        }

        ChannelFuture channelfuture = Connection.connect(address, useEpoll, networkmanager);

        channelfuture.syncUninterruptibly();
        return networkmanager;
    }

    public static ChannelFuture connect(InetSocketAddress address, boolean useEpoll, final Connection connection) {
        Class oclass;
        EventLoopGroup eventloopgroup;

        if (Epoll.isAvailable() && useEpoll) {
            oclass = EpollSocketChannel.class;
            eventloopgroup = (EventLoopGroup) Connection.NETWORK_EPOLL_WORKER_GROUP.get();
        } else {
            oclass = NioSocketChannel.class;
            eventloopgroup = (EventLoopGroup) Connection.NETWORK_WORKER_GROUP.get();
        }

        return ((Bootstrap) ((Bootstrap) ((Bootstrap) (new Bootstrap()).group(eventloopgroup)).handler(new ChannelInitializer<Channel>() {
            protected void initChannel(Channel channel) {
                Connection.setInitialProtocolAttributes(channel);

                try {
                    channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                } catch (ChannelException channelexception) {
                    ;
                }

                ChannelPipeline channelpipeline = channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30));

                Connection.configureSerialization(channelpipeline, PacketFlow.CLIENTBOUND, connection.bandwidthDebugMonitor);
                connection.configurePacketHandler(channelpipeline);
            }
        })).channel(oclass)).connect(address.getAddress(), address.getPort());
    }

    public static void configureSerialization(ChannelPipeline pipeline, PacketFlow side, @Nullable BandwidthDebugMonitor packetSizeLogger) {
        PacketFlow enumprotocoldirection1 = side.getOpposite();
        AttributeKey<ConnectionProtocol.CodecData<?>> attributekey = Connection.getProtocolKey(side);
        AttributeKey<ConnectionProtocol.CodecData<?>> attributekey1 = Connection.getProtocolKey(enumprotocoldirection1);

        pipeline.addLast("splitter", new Varint21FrameDecoder(packetSizeLogger)).addLast("decoder", new PacketDecoder(attributekey)).addLast("prepender", new Varint21LengthFieldPrepender()).addLast("encoder", new PacketEncoder(attributekey1)).addLast("unbundler", new PacketBundleUnpacker(attributekey1)).addLast("bundler", new PacketBundlePacker(attributekey));
    }

    public void configurePacketHandler(ChannelPipeline pipeline) {
        pipeline.addLast(new ChannelHandler[]{new FlowControlHandler()}).addLast("packet_handler", this);
    }

    private static void configureInMemoryPacketValidation(ChannelPipeline pipeline, PacketFlow side) {
        PacketFlow enumprotocoldirection1 = side.getOpposite();
        AttributeKey<ConnectionProtocol.CodecData<?>> attributekey = Connection.getProtocolKey(side);
        AttributeKey<ConnectionProtocol.CodecData<?>> attributekey1 = Connection.getProtocolKey(enumprotocoldirection1);

        pipeline.addLast("validator", new PacketFlowValidator(attributekey, attributekey1));
    }

    public static void configureInMemoryPipeline(ChannelPipeline pipeline, PacketFlow side) {
        Connection.configureInMemoryPacketValidation(pipeline, side);
    }

    public static Connection connectToLocalServer(SocketAddress address) {
        final Connection networkmanager = new Connection(PacketFlow.CLIENTBOUND);

        ((Bootstrap) ((Bootstrap) ((Bootstrap) (new Bootstrap()).group((EventLoopGroup) Connection.LOCAL_WORKER_GROUP.get())).handler(new ChannelInitializer<Channel>() {
            protected void initChannel(Channel channel) {
                Connection.setInitialProtocolAttributes(channel);
                ChannelPipeline channelpipeline = channel.pipeline();

                Connection.configureInMemoryPipeline(channelpipeline, PacketFlow.CLIENTBOUND);
                networkmanager.configurePacketHandler(channelpipeline);
            }
        })).channel(LocalChannel.class)).connect(address).syncUninterruptibly();
        return networkmanager;
    }

    // Paper start
//    public void setEncryptionKey(Cipher decryptionCipher, Cipher encryptionCipher) {
//        this.encrypted = true;
//        this.channel.pipeline().addBefore("splitter", "decrypt", new CipherDecoder(decryptionCipher));
//        this.channel.pipeline().addBefore("prepender", "encrypt", new CipherEncoder(encryptionCipher));
//    }

    public void setupEncryption(javax.crypto.SecretKey key) throws net.minecraft.util.CryptException {
        if (!this.encrypted) {
            try {
                com.velocitypowered.natives.encryption.VelocityCipher decryption = com.velocitypowered.natives.util.Natives.cipher.get().forDecryption(key);
                com.velocitypowered.natives.encryption.VelocityCipher encryption = com.velocitypowered.natives.util.Natives.cipher.get().forEncryption(key);

                this.encrypted = true;
                this.channel.pipeline().addBefore("splitter", "decrypt", new CipherDecoder(decryption));
                this.channel.pipeline().addBefore("prepender", "encrypt", new CipherEncoder(encryption));
            } catch (java.security.GeneralSecurityException e) {
                throw new net.minecraft.util.CryptException(e);
            }
        }
    }
    // Paper end

    public boolean isEncrypted() {
        return this.encrypted;
    }

    public boolean isConnected() {
        return this.channel != null && this.channel.isOpen();
    }

    public boolean isConnecting() {
        return this.channel == null;
    }

    @Nullable
    public PacketListener getPacketListener() {
        return this.packetListener;
    }

    @Nullable
    public Component getDisconnectedReason() {
        return this.disconnectedReason;
    }

    public void setReadOnly() {
        if (this.channel != null) {
            this.channel.config().setAutoRead(false);
        }

    }

    public void setupCompression(int compressionThreshold, boolean rejectsBadPackets) {
        if (compressionThreshold >= 0) {
            com.velocitypowered.natives.compression.VelocityCompressor compressor = com.velocitypowered.natives.util.Natives.compress.get().create(io.papermc.paper.configuration.GlobalConfiguration.get().misc.compressionLevel.or(-1)); // Paper
            if (this.channel.pipeline().get("decompress") instanceof CompressionDecoder) {
                ((CompressionDecoder) this.channel.pipeline().get("decompress")).setThreshold(compressionThreshold, rejectsBadPackets);
            } else {
                this.channel.pipeline().addBefore("decoder", "decompress", new CompressionDecoder(compressor, compressionThreshold, rejectsBadPackets)); // Paper
            }

            if (this.channel.pipeline().get("compress") instanceof CompressionEncoder) {
                ((CompressionEncoder) this.channel.pipeline().get("compress")).setThreshold(compressionThreshold);
            } else {
                this.channel.pipeline().addBefore("encoder", "compress", new CompressionEncoder(compressor, compressionThreshold)); // Paper
            }
            this.channel.pipeline().fireUserEventTriggered(io.papermc.paper.network.ConnectionEvent.COMPRESSION_THRESHOLD_SET); // Paper
        } else {
            if (this.channel.pipeline().get("decompress") instanceof CompressionDecoder) {
                this.channel.pipeline().remove("decompress");
            }

            if (this.channel.pipeline().get("compress") instanceof CompressionEncoder) {
                this.channel.pipeline().remove("compress");
            }
            this.channel.pipeline().fireUserEventTriggered(io.papermc.paper.network.ConnectionEvent.COMPRESSION_DISABLED); // Paper
        }

    }

    public void handleDisconnection() {
        if (this.channel != null && !this.channel.isOpen()) {
            if (this.disconnectionHandled.getAndSet(true)) { // Folia - region threading - may be called concurrently during configuration stage
                // Connection.LOGGER.warn("handleDisconnection() called twice"); // Paper - Don't log useless message
            } else {
                // Folia - region threading - may be called concurrently during configuration stage - set above
                PacketListener packetlistener = this.getPacketListener();
                PacketListener packetlistener1 = packetlistener != null ? packetlistener : this.disconnectListener;

                if (packetlistener1 != null) {
                    Component ichatbasecomponent = (Component) Objects.requireNonNullElseGet(this.getDisconnectedReason(), () -> {
                        return Component.translatable("multiplayer.disconnect.generic");
                    });

                    packetlistener1.onDisconnect(ichatbasecomponent);
                }
                this.clearPacketQueue(); // Paper - Optimize network
                // Paper start - Add PlayerConnectionCloseEvent
                final PacketListener packetListener = this.getPacketListener();
                if (packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl commonPacketListener) {
                    /* Player was logged in, either game listener or configuration listener */
                    final com.mojang.authlib.GameProfile profile = commonPacketListener.getOwner();
                    new com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent(profile.getId(),
                        profile.getName(), ((java.net.InetSocketAddress)address).getAddress(), false).callEvent();
                } else if (packetListener instanceof net.minecraft.server.network.ServerLoginPacketListenerImpl loginListener) {
                    /* Player is login stage */
                    switch (loginListener.state) {
                        case VERIFYING:
                        case WAITING_FOR_DUPE_DISCONNECT:
                        case PROTOCOL_SWITCHING:
                        case ACCEPTED:
                            final com.mojang.authlib.GameProfile profile = loginListener.authenticatedProfile; /* Should be non-null at this stage */
                            new com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent(profile.getId(), profile.getName(),
                                ((java.net.InetSocketAddress)address).getAddress(), false).callEvent();
                    }
                }
                // Folia start - region threading
                if (packetlistener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl commonPacketListener) {
                    net.minecraft.server.MinecraftServer.getServer().getPlayerList().removeConnection(
                        commonPacketListener.getOwner().getName(),
                        commonPacketListener.getOwner().getId(), this
                    );
                } else if (packetlistener instanceof net.minecraft.server.network.ServerLoginPacketListenerImpl loginPacketListener) {
                    if (loginPacketListener.state.ordinal() >= net.minecraft.server.network.ServerLoginPacketListenerImpl.State.VERIFYING.ordinal()) {
                        net.minecraft.server.MinecraftServer.getServer().getPlayerList().removeConnection(
                            loginPacketListener.authenticatedProfile.getName(),
                            loginPacketListener.authenticatedProfile.getId(), this
                        );
                    }
                }
                // Folia end - region threading
                // Paper end

            }
        }
    }

    public float getAverageReceivedPackets() {
        return this.averageReceivedPackets;
    }

    public float getAverageSentPackets() {
        return this.averageSentPackets;
    }

    public void setBandwidthLogger(SampleLogger log) {
        this.bandwidthDebugMonitor = new BandwidthDebugMonitor(log);
    }

    // Paper start - Optimize network
    public void clearPacketQueue() {
        final net.minecraft.server.level.ServerPlayer player = getPlayer();
        // Folia start - region threading - connection fixes
        java.util.List<Connection.PacketSendAction> queuedPackets = new java.util.ArrayList<>();
        // synchronise so that flushQueue does not poll values while the queue is being cleared
        synchronized (this.pendingActions) {
            Connection.WrappedConsumer consumer;
            while ((consumer = this.pendingActions.poll()) != null) {
                if (consumer instanceof Connection.PacketSendAction packetHolder) {
                    queuedPackets.add(packetHolder);
                }
            }
        }

        for (Connection.PacketSendAction queuedPacket : queuedPackets) {
            Packet<?> packet = queuedPacket.packet;
            if (packet.hasFinishListener()) {
                packet.onPacketDispatchFinish(player, null);
            }
        }
        // Folia end - region threading - connection fixes
    }

    private static class InnerUtil { // Attempt to hide these methods from ProtocolLib, so it doesn't accidently pick them up.

        @Nullable
        private static java.util.List<Packet<?>> buildExtraPackets(final Packet<?> packet) {
            final java.util.List<Packet<?>> extra = packet.getExtraPackets();
            if (extra == null || extra.isEmpty()) {
                return null;
            }

            final java.util.List<Packet<?>> ret = new java.util.ArrayList<>(1 + extra.size());
            buildExtraPackets0(extra, ret);
            return ret;
        }

        private static void buildExtraPackets0(final java.util.List<Packet<?>> extraPackets, final java.util.List<Packet<?>> into) {
            for (final Packet<?> extra : extraPackets) {
                into.add(extra);
                final java.util.List<Packet<?>> extraExtra = extra.getExtraPackets();
                if (extraExtra != null && !extraExtra.isEmpty()) {
                    buildExtraPackets0(extraExtra, into);
                }
            }
        }

        private static boolean canSendImmediate(final Connection networkManager, final net.minecraft.network.protocol.Packet<?> packet) {
            return networkManager.isPending || networkManager.packetListener.protocol() != ConnectionProtocol.PLAY ||
                packet instanceof net.minecraft.network.protocol.common.ClientboundKeepAlivePacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerChatPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundClearTitlesPacket ||
                packet instanceof net.minecraft.network.protocol.game.ClientboundBossEventPacket;
        }
    }

    private static class WrappedConsumer implements Consumer<Connection> {
        private final Consumer<Connection> delegate;
        private final java.util.concurrent.atomic.AtomicBoolean consumed = new java.util.concurrent.atomic.AtomicBoolean(false);

        private WrappedConsumer(final Consumer<Connection> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void accept(final Connection connection) {
            this.delegate.accept(connection);
        }

        public boolean tryMarkConsumed() {
            return consumed.compareAndSet(false, true);
        }

        public boolean isConsumed() {
            return consumed.get();
        }
    }

    private static final class PacketSendAction extends WrappedConsumer {
        private final Packet<?> packet;

        private PacketSendAction(final Packet<?> packet, @Nullable final PacketSendListener packetSendListener, final boolean flush) {
            super(connection -> connection.sendPacket(packet, packetSendListener, flush));
            this.packet = packet;
        }
    }
    // Paper end - Optimize network
}
