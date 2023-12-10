package net.minecraft.server.network;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.thread.BlockableEventLoop;
import org.slf4j.Logger;

// CraftBukkit start
import io.netty.buffer.ByteBuf;
import java.util.concurrent.ExecutionException;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.craftbukkit.util.Waitable;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
// CraftBukkit end

public abstract class ServerCommonPacketListenerImpl implements ServerCommonPacketListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int LATENCY_CHECK_INTERVAL = 15000;
    private static final Component TIMEOUT_DISCONNECTION_MESSAGE = Component.translatable("disconnect.timeout");
    protected final MinecraftServer server;
    public final Connection connection; // Paper
    private long keepAliveTime = Util.getMillis(); // Paper
    private boolean keepAlivePending;
    private long keepAliveChallenge;
    private int latency;
    private volatile boolean suspendFlushingOnServerThread = false;
    private static final long KEEPALIVE_LIMIT = Long.getLong("paper.playerconnection.keepalive", 30) * 1000; // Paper - provide property to set keepalive limit
    protected static final ResourceLocation MINECRAFT_BRAND = new ResourceLocation("brand"); // Paper - Brand support

    public ServerCommonPacketListenerImpl(MinecraftServer minecraftserver, Connection networkmanager, CommonListenerCookie commonlistenercookie, ServerPlayer player) { // CraftBukkit
        this.server = minecraftserver;
        this.connection = networkmanager;
        this.keepAliveTime = Util.getMillis();
        this.latency = commonlistenercookie.latency();
        // CraftBukkit start - add fields and methods
        this.player = player;
        this.cserver = minecraftserver.server;
    }
    protected final ServerPlayer player;
    protected final org.bukkit.craftbukkit.CraftServer cserver;
    public boolean processedDisconnect;

    public CraftPlayer getCraftPlayer() {
        return (this.player == null) ? null : (CraftPlayer) this.player.getBukkitEntity();
        // CraftBukkit end
    }

    @Override
    public void onDisconnect(Component reason) {
        // Paper start
        this.onDisconnect(reason, null);
    }
    public void onDisconnect(Component reason, @Nullable net.kyori.adventure.text.Component quitMessage) {
        // Paper end
        if (this.isSingleplayerOwner()) {
            ServerCommonPacketListenerImpl.LOGGER.info("Stopping singleplayer server as player logged out");
            this.server.halt(false);
        }
        this.player.getBukkitEntity().taskScheduler.retire(); // Folia - region threading

    }

    @Override
    public void handleKeepAlive(ServerboundKeepAlivePacket packet) {
        //PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel()); // CraftBukkit // Paper - This shouldn't be on the main thread
        if (this.keepAlivePending && packet.getId() == this.keepAliveChallenge) {
            int i = (int) (Util.getMillis() - this.keepAliveTime);

            this.latency = (this.latency * 3 + i) / 4;
            this.keepAlivePending = false;
        } else if (!this.isSingleplayerOwner()) {
            // Paper start - This needs to be handled on the main thread for plugins
            // Folia - region threading - do not schedule to main anymore, there is no main
                this.disconnect(ServerCommonPacketListenerImpl.TIMEOUT_DISCONNECTION_MESSAGE, org.bukkit.event.player.PlayerKickEvent.Cause.TIMEOUT); // Paper - kick event cause
            // Folia - region threading - do not schedule to main anymore, there is no main
            // Paper endg
        }

    }

    @Override
    public void handlePong(ServerboundPongPacket packet) {}

    // CraftBukkit start
    private static final ResourceLocation CUSTOM_REGISTER = new ResourceLocation("register");
    private static final ResourceLocation CUSTOM_UNREGISTER = new ResourceLocation("unregister");

    @Override
    public void handleCustomPayload(ServerboundCustomPayloadPacket packet) {
        // Paper start - handle brand payload packet
        if (packet.payload() instanceof net.minecraft.network.protocol.common.custom.BrandPayload brandPayload) {
            this.player.clientBrandName = brandPayload.brand();
        }
        // Paper end - handle brand payload
        if (!(packet.payload() instanceof ServerboundCustomPayloadPacket.UnknownPayload)) {
            return;
        }
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        ResourceLocation identifier = packet.payload().id();
        ByteBuf payload = ((ServerboundCustomPayloadPacket.UnknownPayload)packet.payload()).data();

        if (identifier.equals(ServerCommonPacketListenerImpl.CUSTOM_REGISTER)) {
            try {
                String channels = payload.toString(com.google.common.base.Charsets.UTF_8);
                for (String channel : channels.split("\0")) {
                    this.getCraftPlayer().addChannel(channel);
                }
            } catch (Exception ex) {
                ServerGamePacketListenerImpl.LOGGER.error("Couldn\'t register custom payload", ex);
                this.disconnect("Invalid payload REGISTER!", org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_PAYLOAD); // Paper - kick event cause
            }
        } else if (identifier.equals(ServerCommonPacketListenerImpl.CUSTOM_UNREGISTER)) {
            try {
                String channels = payload.toString(com.google.common.base.Charsets.UTF_8);
                for (String channel : channels.split("\0")) {
                    this.getCraftPlayer().removeChannel(channel);
                }
            } catch (Exception ex) {
                ServerGamePacketListenerImpl.LOGGER.error("Couldn\'t unregister custom payload", ex);
                this.disconnect("Invalid payload UNREGISTER!", org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_PAYLOAD); // Paper - kick event cause
            }
        } else {
            try {
                byte[] data = new byte[payload.readableBytes()];
                payload.readBytes(data);
                // Paper start - Brand support - Retain this incase upstream decides to 'break' the new mechanism in favour of backwards compat...
                if (identifier.equals(MINECRAFT_BRAND)) {
                    try {
                        this.player.clientBrandName = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.copiedBuffer(data)).readUtf(256);
                    } catch (StringIndexOutOfBoundsException ex) {
                        this.player.clientBrandName = "illegal";
                    }
                }
                // Paper end
                this.cserver.getMessenger().dispatchIncomingMessage(this.player.getBukkitEntity(), identifier.toString(), data);
            } catch (Exception ex) {
                ServerGamePacketListenerImpl.LOGGER.error("Couldn\'t dispatch custom payload", ex);
                this.disconnect("Invalid custom payload!", org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_PAYLOAD); // Paper - kick event cause
            }
        }

    }

    public final boolean isDisconnected() {
        return (!this.player.joining && !this.connection.isConnected()) || this.processedDisconnect; // Paper
    }
    // CraftBukkit end

    @Override
    public void handleResourcePackResponse(ServerboundResourcePackPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, (BlockableEventLoop) this.server);
        if (packet.getAction() == ServerboundResourcePackPacket.Action.DECLINED && this.server.isResourcePackRequired()) {
            ServerCommonPacketListenerImpl.LOGGER.info("Disconnecting {} due to resource pack rejection", this.playerProfile().getName());
            this.disconnect(Component.translatable("multiplayer.requiredTexturePrompt.disconnect"), org.bukkit.event.player.PlayerKickEvent.Cause.RESOURCE_PACK_REJECTION); // Paper - kick event cause
        }
        // Paper start
        PlayerResourcePackStatusEvent.Status packStatus = PlayerResourcePackStatusEvent.Status.values()[packet.getAction().ordinal()];
        player.getBukkitEntity().setResourcePackStatus(packStatus);
        this.cserver.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(this.getCraftPlayer(), packStatus)); // CraftBukkit
        // Paper end

    }

    protected void keepConnectionAlive() {
        this.server.getProfiler().push("keepAlive");
        // Paper Start - give clients a longer time to respond to pings as per pre 1.12.2 timings
        // This should effectively place the keepalive handling back to "as it was" before 1.12.2
        long currentTime = Util.getMillis();
        long elapsedTime = currentTime - this.keepAliveTime;

        if (this.keepAlivePending) {
            if (!this.processedDisconnect && elapsedTime >= KEEPALIVE_LIMIT) { // check keepalive limit, don't fire if already disconnected
                ServerGamePacketListenerImpl.LOGGER.warn("{} was kicked due to keepalive timeout!", this.player.getScoreboardName()); // more info
                this.disconnect(ServerCommonPacketListenerImpl.TIMEOUT_DISCONNECTION_MESSAGE, org.bukkit.event.player.PlayerKickEvent.Cause.TIMEOUT); // Paper - kick event cause
            }
        } else {
            if (elapsedTime >= 15000L) { // 15 seconds
                this.keepAlivePending = true;
                this.keepAliveTime = currentTime;
                this.keepAliveChallenge = currentTime;
                this.send(new ClientboundKeepAlivePacket(this.keepAliveChallenge));
            }
        }
        // Paper end

        this.server.getProfiler().pop();
    }

    public void suspendFlushing() {
        this.suspendFlushingOnServerThread = true;
    }

    public void resumeFlushing() {
        this.suspendFlushingOnServerThread = false;
        this.connection.flushChannel();
    }

    public void send(Packet<?> packet) {
        this.send(packet, (PacketSendListener) null);
    }

    public void send(Packet<?> packet, @Nullable PacketSendListener callbacks) {
        // CraftBukkit start
        if (packet == null || this.processedDisconnect) { // Spigot
            return;
        } else if (packet instanceof ClientboundSetDefaultSpawnPositionPacket) {
            ClientboundSetDefaultSpawnPositionPacket packet6 = (ClientboundSetDefaultSpawnPositionPacket) packet;
            this.player.compassTarget = CraftLocation.toBukkit(packet6.pos, this.getCraftPlayer().getWorld());
        }
        // CraftBukkit end
        boolean flag = !this.suspendFlushingOnServerThread || !this.server.isSameThread();

        try {
            this.connection.send(packet, callbacks, flag);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Sending packet");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Packet being sent");

            crashreportsystemdetails.setDetail("Packet class", () -> {
                return packet.getClass().getCanonicalName();
            });
            throw new ReportedException(crashreport);
        }
    }

    // CraftBukkit start
    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper
    public void disconnect(String s) { // Paper
        this.disconnect(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(s), org.bukkit.event.player.PlayerKickEvent.Cause.UNKNOWN); // Paper
    }
    // CraftBukkit end

    // Paper start - kick event cause
    public void disconnect(String s, PlayerKickEvent.Cause cause) {
        this.disconnect(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(s), cause);
    }

    // Paper start
    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper
    public void disconnect(final Component reason) {
        this.disconnect(io.papermc.paper.adventure.PaperAdventure.asAdventure(reason), org.bukkit.event.player.PlayerKickEvent.Cause.UNKNOWN);
    }

    public void disconnect(final Component reason, PlayerKickEvent.Cause cause) {
        this.disconnect(io.papermc.paper.adventure.PaperAdventure.asAdventure(reason), cause);
    }

    public void disconnect(net.kyori.adventure.text.Component reason, org.bukkit.event.player.PlayerKickEvent.Cause cause) { // Paper - kick event cause
        // Paper end
        // CraftBukkit start - fire PlayerKickEvent
        if (this.processedDisconnect) {
            return;
        }
        if (!io.papermc.paper.util.TickThread.isTickThreadFor(this.player)) { // Folia - region threading
            this.connection.disconnectSafely(io.papermc.paper.adventure.PaperAdventure.asVanilla(reason), cause); // Folia - region threading - it HAS to be delayed/async to avoid deadlock if we try to wait for another region
            return;
        }

        net.kyori.adventure.text.Component leaveMessage = net.kyori.adventure.text.Component.translatable("multiplayer.player.left", net.kyori.adventure.text.format.NamedTextColor.YELLOW, io.papermc.paper.configuration.GlobalConfiguration.get().messages.useDisplayNameInQuitMessage ? this.player.getBukkitEntity().displayName() : net.kyori.adventure.text.Component.text(this.player.getScoreboardName())); // Paper - Adventure

        PlayerKickEvent event = new PlayerKickEvent(this.player.getBukkitEntity(), reason, leaveMessage, cause); // Paper - adventure

        if (this.cserver.getServer().isRunning()) {
            this.cserver.getPluginManager().callEvent(event);
        }

        if (event.isCancelled()) {
            // Do not kick the player
            return;
        }
        // Send the possibly modified leave message
        final Component ichatbasecomponent = io.papermc.paper.adventure.PaperAdventure.asVanilla(event.reason()); // Paper - Adventure
        // CraftBukkit end

        this.player.quitReason = org.bukkit.event.player.PlayerQuitEvent.QuitReason.KICKED; // Paper
        this.connection.send(new ClientboundDisconnectPacket(ichatbasecomponent), PacketSendListener.thenRun(() -> {
            this.connection.disconnect(ichatbasecomponent);
        }));
        this.onDisconnect(ichatbasecomponent, event.leaveMessage()); // CraftBukkit - fire quit instantly // Paper - use kick event leave message
        this.connection.setReadOnly();
        MinecraftServer minecraftserver = this.server;
        Connection networkmanager = this.connection;

        Objects.requireNonNull(this.connection);
        // CraftBukkit - Don't wait
        // Folia - region threading
    }

    protected boolean isSingleplayerOwner() {
        return this.server.isSingleplayerOwner(this.playerProfile());
    }

    protected abstract GameProfile playerProfile();

    @VisibleForDebug
    public GameProfile getOwner() {
        return this.playerProfile();
    }

    public int latency() {
        return this.latency;
    }

    protected CommonListenerCookie createCookie(ClientInformation syncedOptions) {
        return new CommonListenerCookie(this.playerProfile(), this.latency, syncedOptions);
    }
}
