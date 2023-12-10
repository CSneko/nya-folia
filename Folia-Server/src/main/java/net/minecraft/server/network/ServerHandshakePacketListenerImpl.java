package net.minecraft.server.network;

import net.minecraft.SharedConstants;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.handshake.ClientIntent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.handshake.ServerHandshakePacketListener;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;

// CraftBukkit start
import java.net.InetAddress;
import java.util.HashMap;
// CraftBukkit end

public class ServerHandshakePacketListenerImpl implements ServerHandshakePacketListener {

    // Spigot start
    private static final com.google.gson.Gson gson = new com.google.gson.Gson();
    static final java.util.regex.Pattern HOST_PATTERN = java.util.regex.Pattern.compile("[0-9a-f\\.:]{0,45}");
    static final java.util.regex.Pattern PROP_PATTERN = java.util.regex.Pattern.compile("\\w{0,16}");
    // Spigot end
    // CraftBukkit start - add fields
    private static final HashMap<InetAddress, Long> throttleTracker = new HashMap<InetAddress, Long>();
    private static int throttleCounter = 0;
    // CraftBukkit end
    private static final Component IGNORE_STATUS_REASON = Component.translatable("disconnect.ignoring_status_request");
    private final MinecraftServer server;
    private final Connection connection;
    private static final boolean BYPASS_HOSTCHECK = Boolean.getBoolean("Paper.bypassHostCheck"); // Paper

    public ServerHandshakePacketListenerImpl(MinecraftServer server, Connection connection) {
        this.server = server;
        this.connection = connection;
    }

    @Override
    public void handleIntention(ClientIntentionPacket packet) {
        this.connection.hostname = packet.hostName() + ":" + packet.port(); // CraftBukkit  - set hostname
        switch (packet.intention()) {
            case LOGIN:
                this.connection.setClientboundProtocolAfterHandshake(ClientIntent.LOGIN);
                // CraftBukkit start - Connection throttle
                try {
                    if (!(this.connection.channel.localAddress() instanceof io.netty.channel.unix.DomainSocketAddress)) { // Paper - the connection throttle is useless when you have a Unix domain socket
                    long currentTime = System.currentTimeMillis();
                    long connectionThrottle = this.server.server.getConnectionThrottle();
                    InetAddress address = ((java.net.InetSocketAddress) this.connection.getRemoteAddress()).getAddress();

                    synchronized (ServerHandshakePacketListenerImpl.throttleTracker) {
                        if (ServerHandshakePacketListenerImpl.throttleTracker.containsKey(address) && !"127.0.0.1".equals(address.getHostAddress()) && currentTime - ServerHandshakePacketListenerImpl.throttleTracker.get(address) < connectionThrottle) {
                            ServerHandshakePacketListenerImpl.throttleTracker.put(address, currentTime);
                            Component chatmessage = io.papermc.paper.adventure.PaperAdventure.asVanilla(io.papermc.paper.configuration.GlobalConfiguration.get().messages.kick.connectionThrottle); // Paper - Configurable connection throttle kick message
                            this.connection.send(new ClientboundLoginDisconnectPacket(chatmessage));
                            this.connection.disconnect(chatmessage);
                            return;
                        }

                        ServerHandshakePacketListenerImpl.throttleTracker.put(address, currentTime);
                        ServerHandshakePacketListenerImpl.throttleCounter++;
                        if (ServerHandshakePacketListenerImpl.throttleCounter > 200) {
                            ServerHandshakePacketListenerImpl.throttleCounter = 0;

                            // Cleanup stale entries
                            java.util.Iterator iter = ServerHandshakePacketListenerImpl.throttleTracker.entrySet().iterator();
                            while (iter.hasNext()) {
                                java.util.Map.Entry<InetAddress, Long> entry = (java.util.Map.Entry) iter.next();
                                if (entry.getValue() > connectionThrottle) {
                                    iter.remove();
                                }
                            }
                        }
                    }
                    } // Paper - add closing bracket for if check above
                } catch (Throwable t) {
                    org.apache.logging.log4j.LogManager.getLogger().debug("Failed to check connection throttle", t);
                }
                // CraftBukkit end
                if (packet.protocolVersion() != SharedConstants.getCurrentVersion().getProtocolVersion()) {
                    Component ichatmutablecomponent; // Paper - Fix hex colors not working in some kick messages

                    if (packet.protocolVersion() < SharedConstants.getCurrentVersion().getProtocolVersion()) { // Paper - Fix incorrect message for outdated clients
                        ichatmutablecomponent = org.bukkit.craftbukkit.util.CraftChatMessage.fromString( java.text.MessageFormat.format( org.spigotmc.SpigotConfig.outdatedClientMessage.replaceAll("'", "''"), SharedConstants.getCurrentVersion().getName() ) )[0]; // Spigot // Paper - Fix hex colors not working in some kick messages
                    } else {
                        ichatmutablecomponent = org.bukkit.craftbukkit.util.CraftChatMessage.fromString( java.text.MessageFormat.format( org.spigotmc.SpigotConfig.outdatedServerMessage.replaceAll("'", "''"), SharedConstants.getCurrentVersion().getName() ) )[0]; // Spigot // Paper - Fix hex colors not working in some kick messages
                    }

                    this.connection.send(new ClientboundLoginDisconnectPacket(ichatmutablecomponent));
                    this.connection.disconnect(ichatmutablecomponent);
                } else {
                    this.connection.setListener(new ServerLoginPacketListenerImpl(this.server, this.connection));
                // Paper start - handshake event
                boolean proxyLogicEnabled = org.spigotmc.SpigotConfig.bungee;
                boolean handledByEvent = false;
                // Try and handle the handshake through the event
                if (com.destroystokyo.paper.event.player.PlayerHandshakeEvent.getHandlerList().getRegisteredListeners().length != 0) { // Hello? Can you hear me?
                    java.net.SocketAddress socketAddress = this.connection.address;
                    String hostnameOfRemote = socketAddress instanceof java.net.InetSocketAddress ? ((java.net.InetSocketAddress) socketAddress).getHostString() : InetAddress.getLoopbackAddress().getHostAddress();
                    com.destroystokyo.paper.event.player.PlayerHandshakeEvent event = new com.destroystokyo.paper.event.player.PlayerHandshakeEvent(packet.hostName(), hostnameOfRemote, !proxyLogicEnabled);
                    if (event.callEvent()) {
                        // If we've failed somehow, let the client know so and go no further.
                        if (event.isFailed()) {
                            Component component = io.papermc.paper.adventure.PaperAdventure.asVanilla(event.failMessage());
                            this.connection.send(new ClientboundLoginDisconnectPacket(component));
                            this.connection.disconnect(component);
                            return;
                        }

                        if (event.getServerHostname() != null) {
                            // change hostname
                            packet = new ClientIntentionPacket(
                                packet.protocolVersion(),
                                event.getServerHostname(),
                                packet.port(),
                                packet.intention()
                            );
                        }
                        if (event.getSocketAddressHostname() != null) this.connection.address = new java.net.InetSocketAddress(event.getSocketAddressHostname(), socketAddress instanceof java.net.InetSocketAddress ? ((java.net.InetSocketAddress) socketAddress).getPort() : 0);
                        this.connection.spoofedUUID = event.getUniqueId();
                        this.connection.spoofedProfile = gson.fromJson(event.getPropertiesJson(), com.mojang.authlib.properties.Property[].class);
                        handledByEvent = true; // Hooray, we did it!
                    }
                }
                    // Spigot Start
                    String[] split = packet.hostName().split("\00");
                    // Don't try and handle default logic if it's been handled by the event.
                    if (!handledByEvent && proxyLogicEnabled) {
                        // Paper end
                    // if (org.spigotmc.SpigotConfig.bungee) { // Paper - comment out, we check above!
                        if ( ( split.length == 3 || split.length == 4 ) && ( ServerHandshakePacketListenerImpl.BYPASS_HOSTCHECK || ServerHandshakePacketListenerImpl.HOST_PATTERN.matcher( split[1] ).matches() ) ) { // Paper
                            // Paper start - Unix domain socket support
                            java.net.SocketAddress socketAddress = this.connection.getRemoteAddress();
                            this.connection.hostname = split[0];
                            this.connection.address = new java.net.InetSocketAddress(split[1], socketAddress instanceof java.net.InetSocketAddress ? ((java.net.InetSocketAddress) socketAddress).getPort() : 0);
                            // Paper end
                            this.connection.spoofedUUID = com.mojang.util.UndashedUuid.fromStringLenient( split[2] );
                        } else
                        {
                            Component chatmessage = Component.literal("If you wish to use IP forwarding, please enable it in your BungeeCord config as well!");
                            this.connection.send(new ClientboundLoginDisconnectPacket(chatmessage));
                            this.connection.disconnect(chatmessage);
                            return;
                        }
                        if ( split.length == 4 )
                        {
                            this.connection.spoofedProfile = ServerHandshakePacketListenerImpl.gson.fromJson(split[3], com.mojang.authlib.properties.Property[].class);
                        }
                    } else if ( ( split.length == 3 || split.length == 4 ) && ( ServerHandshakePacketListenerImpl.HOST_PATTERN.matcher( split[1] ).matches() ) ) {
                        Component chatmessage = Component.literal("Unknown data in login hostname, did you forget to enable BungeeCord in spigot.yml?");
                        this.connection.send(new ClientboundLoginDisconnectPacket(chatmessage));
                        this.connection.disconnect(chatmessage);
                        return;
                    }
                    // Spigot End
                }
                break;
            case STATUS:
                ServerStatus serverping = this.server.getStatus();

                if (this.server.repliesToStatus() && serverping != null) {
                    this.connection.setClientboundProtocolAfterHandshake(ClientIntent.STATUS);
                    this.connection.setListener(new ServerStatusPacketListenerImpl(serverping, this.connection));
                } else {
                    this.connection.disconnect(ServerHandshakePacketListenerImpl.IGNORE_STATUS_REASON);
                }
                break;
            default:
                throw new UnsupportedOperationException("Invalid intention " + packet.intention());
        }

        // Paper start - NetworkClient implementation
        this.connection.protocolVersion = packet.protocolVersion();
        this.connection.virtualHost = com.destroystokyo.paper.network.PaperNetworkClient.prepareVirtualHost(packet.hostName(), packet.port());
        // Paper end
    }

    @Override
    public void onDisconnect(Component reason) {}

    @Override
    public boolean isAcceptingMessages() {
        return this.connection.isConnected();
    }
}
