package net.minecraft.server.network;

// CraftBukkit start
import com.mojang.authlib.GameProfile;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.status.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.network.protocol.status.ServerStatusPacketListener;
import net.minecraft.network.protocol.status.ServerboundPingRequestPacket;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.craftbukkit.util.CraftIconCache;
import org.bukkit.entity.Player;
// CraftBukkit end

public class ServerStatusPacketListenerImpl implements ServerStatusPacketListener {

    private static final Component DISCONNECT_REASON = Component.translatable("multiplayer.status.request_handled");
    private final ServerStatus status;
    private final Connection connection;
    private boolean hasRequestedStatus;

    public ServerStatusPacketListenerImpl(ServerStatus metadata, Connection connection) {
        this.status = metadata;
        this.connection = connection;
    }

    @Override
    public void onDisconnect(Component reason) {}

    @Override
    public boolean isAcceptingMessages() {
        return this.connection.isConnected();
    }

    @Override
    public void handleStatusRequest(ServerboundStatusRequestPacket packet) {
        if (this.hasRequestedStatus) {
            this.connection.disconnect(ServerStatusPacketListenerImpl.DISCONNECT_REASON);
        } else {
            this.hasRequestedStatus = true;
            // Paper start - Replace everything
            /*
            // CraftBukkit start
            // this.connection.send(new PacketStatusOutServerInfo(this.status));
            MinecraftServer server = MinecraftServer.getServer();
            final Object[] players = server.getPlayerList().players.toArray();
            class ServerListPingEvent extends org.bukkit.event.server.ServerListPingEvent {

                CraftIconCache icon = server.server.getServerIcon();

                ServerListPingEvent() {
                    super(ServerStatusPacketListenerImpl.this.connection.hostname, ((InetSocketAddress) ServerStatusPacketListenerImpl.this.connection.getRemoteAddress()).getAddress(), server.server.motd(), server.getPlayerList().getMaxPlayers()); // Paper - Adventure
                }

                @Override
                public void setServerIcon(org.bukkit.util.CachedServerIcon icon) {
                    if (!(icon instanceof CraftIconCache)) {
                        throw new IllegalArgumentException(icon + " was not created by " + org.bukkit.craftbukkit.CraftServer.class);
                    }
                    this.icon = (CraftIconCache) icon;
                }

                @Override
                public Iterator<Player> iterator() throws UnsupportedOperationException {
                    return new Iterator<Player>() {
                        int i;
                        int ret = Integer.MIN_VALUE;
                        ServerPlayer player;

                        @Override
                        public boolean hasNext() {
                            if (this.player != null) {
                                return true;
                            }
                            final Object[] currentPlayers = players;
                            for (int length = currentPlayers.length, i = this.i; i < length; i++) {
                                final ServerPlayer player = (ServerPlayer) currentPlayers[i];
                                if (player != null) {
                                    this.i = i + 1;
                                    this.player = player;
                                    return true;
                                }
                            }
                            return false;
                        }

                        @Override
                        public Player next() {
                            if (!this.hasNext()) {
                                throw new java.util.NoSuchElementException();
                            }
                            final ServerPlayer player = this.player;
                            this.player = null;
                            this.ret = this.i - 1;
                            return player.getBukkitEntity();
                        }

                        @Override
                        public void remove() {
                            final Object[] currentPlayers = players;
                            final int i = this.ret;
                            if (i < 0 || currentPlayers[i] == null) {
                                throw new IllegalStateException();
                            }
                            currentPlayers[i] = null;
                        }
                    };
                }
            }

            ServerListPingEvent event = new ServerListPingEvent();
            server.server.getPluginManager().callEvent(event);

            java.util.List<GameProfile> profiles = new java.util.ArrayList<GameProfile>(players.length);
            for (Object player : players) {
                if (player != null) {
                    ServerPlayer entityPlayer = ((ServerPlayer) player);
                    if (entityPlayer.allowsListing()) {
                        profiles.add(entityPlayer.getGameProfile());
                    } else {
                        profiles.add(MinecraftServer.ANONYMOUS_PLAYER_PROFILE);
                    }
                }
            }

            // Spigot Start
            if ( !server.hidesOnlinePlayers() && !profiles.isEmpty() )
            {
                java.util.Collections.shuffle( profiles ); // This sucks, its inefficient but we have no simple way of doing it differently
                profiles = profiles.subList( 0, Math.min( profiles.size(), org.spigotmc.SpigotConfig.playerSample ) ); // Cap the sample to n (or less) displayed players, ie: Vanilla behaviour
            }
            // Spigot End
            ServerStatus.Players playerSample = new ServerStatus.Players(event.getMaxPlayers(), profiles.size(), (server.hidesOnlinePlayers()) ? Collections.emptyList() : profiles);

            ServerStatus ping = new ServerStatus(
                    CraftChatMessage.fromString(event.getMotd(), true)[0],
                    Optional.of(playerSample),
                    Optional.of(new ServerStatus.Version(server.getServerModName() + " " + server.getServerVersion(), SharedConstants.getCurrentVersion().getProtocolVersion())),
                    (event.icon.value != null) ? Optional.of(new ServerStatus.Favicon(event.icon.value)) : Optional.empty(),
                    server.enforceSecureProfile()
            );

            this.connection.send(new ClientboundStatusResponsePacket(ping));
            // CraftBukkit end
            */
            com.destroystokyo.paper.network.StandardPaperServerListPingEventImpl.processRequest(MinecraftServer.getServer(), this.connection);
            // Paper end
        }
    }

    @Override
    public void handlePingRequest(ServerboundPingRequestPacket packet) {
        this.connection.send(new ClientboundPongResponsePacket(packet.getTime()));
        this.connection.disconnect(ServerStatusPacketListenerImpl.DISCONNECT_REASON);
    }
}
