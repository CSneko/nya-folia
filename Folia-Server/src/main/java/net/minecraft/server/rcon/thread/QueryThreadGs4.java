package net.minecraft.server.rcon.thread;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.server.ServerInterface;
import net.minecraft.server.rcon.NetworkDataOutputStream;
import net.minecraft.server.rcon.PktUtils;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;

public class QueryThreadGs4 extends GenericThread {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String GAME_TYPE = "SMP";
    private static final String GAME_ID = "MINECRAFT";
    private static final long CHALLENGE_CHECK_INTERVAL = 30000L;
    private static final long RESPONSE_CACHE_TIME = 5000L;
    private long lastChallengeCheck;
    private final int port;
    private final int serverPort;
    private final int maxPlayers;
    private final String serverName;
    private final String worldName;
    private DatagramSocket socket;
    private final byte[] buffer = new byte[1460];
    private String hostIp;
    private String serverIp;
    private final Map<SocketAddress, QueryThreadGs4.RequestChallenge> validChallenges;
    private final NetworkDataOutputStream rulesResponse;
    private long lastRulesResponse;
    private final ServerInterface serverInterface;

    private QueryThreadGs4(ServerInterface server, int queryPort) {
        super("Query Listener");
        this.serverInterface = server;
        this.port = queryPort;
        this.serverIp = server.getServerIp();
        this.serverPort = server.getServerPort();
        this.serverName = server.getServerName();
        this.maxPlayers = server.getMaxPlayers();
        this.worldName = server.getLevelIdName();
        this.lastRulesResponse = 0L;
        this.hostIp = "0.0.0.0";
        if (!this.serverIp.isEmpty() && !this.hostIp.equals(this.serverIp)) {
            this.hostIp = this.serverIp;
        } else {
            this.serverIp = "0.0.0.0";

            try {
                InetAddress inetAddress = InetAddress.getLocalHost();
                this.hostIp = inetAddress.getHostAddress();
            } catch (UnknownHostException var4) {
                LOGGER.warn("Unable to determine local host IP, please set server-ip in server.properties", (Throwable)var4);
            }
        }

        this.rulesResponse = new NetworkDataOutputStream(1460);
        this.validChallenges = Maps.newHashMap();
    }

    @Nullable
    public static QueryThreadGs4 create(ServerInterface server) {
        int i = server.getProperties().queryPort;
        if (0 < i && 65535 >= i) {
            QueryThreadGs4 queryThreadGs4 = new QueryThreadGs4(server, i);
            return !queryThreadGs4.start() ? null : queryThreadGs4;
        } else {
            LOGGER.warn("Invalid query port {} found in server.properties (queries disabled)", (int)i);
            return null;
        }
    }

    private void sendTo(byte[] buf, DatagramPacket packet) throws IOException {
        this.socket.send(new DatagramPacket(buf, buf.length, packet.getSocketAddress()));
    }

    private boolean processPacket(DatagramPacket packet) throws IOException {
        byte[] bs = packet.getData();
        int i = packet.getLength();
        SocketAddress socketAddress = packet.getSocketAddress();
        LOGGER.debug("Packet len {} [{}]", i, socketAddress);
        if (3 <= i && -2 == bs[0] && -3 == bs[1]) {
            LOGGER.debug("Packet '{}' [{}]", PktUtils.toHexString(bs[2]), socketAddress);
            switch (bs[2]) {
                case 0:
                    if (!this.validChallenge(packet)) {
                        LOGGER.debug("Invalid challenge [{}]", (Object)socketAddress);
                        return false;
                    } else if (15 == i) {
                        this.sendTo(this.buildRuleResponse(packet), packet);
                        LOGGER.debug("Rules [{}]", (Object)socketAddress);
                    } else {
                        NetworkDataOutputStream networkDataOutputStream = new NetworkDataOutputStream(1460);
                        networkDataOutputStream.write(0);
                        networkDataOutputStream.writeBytes(this.getIdentBytes(packet.getSocketAddress()));

                        com.destroystokyo.paper.event.server.GS4QueryEvent.QueryType queryType =
                            com.destroystokyo.paper.event.server.GS4QueryEvent.QueryType.BASIC;
                        com.destroystokyo.paper.event.server.GS4QueryEvent.QueryResponse queryResponse = com.destroystokyo.paper.event.server.GS4QueryEvent.QueryResponse.builder()
                            .motd(this.serverName)
                            .map(this.worldName)
                            .currentPlayers(this.serverInterface.getPlayerCount())
                            .maxPlayers(this.maxPlayers)
                            .port(this.serverPort)
                            .hostname(this.hostIp)
                            .gameVersion(this.serverInterface.getServerVersion())
                            .serverVersion(org.bukkit.Bukkit.getServer().getName() + " on " + org.bukkit.Bukkit.getServer().getBukkitVersion())
                            .build();
                        com.destroystokyo.paper.event.server.GS4QueryEvent queryEvent =
                            new com.destroystokyo.paper.event.server.GS4QueryEvent(queryType, packet.getAddress(), queryResponse);
                        queryEvent.callEvent();
                        queryResponse = queryEvent.getResponse();

                        networkDataOutputStream.writeString(queryResponse.getMotd());
                        networkDataOutputStream.writeString("SMP");
                        networkDataOutputStream.writeString(queryResponse.getMap());
                        networkDataOutputStream.writeString(Integer.toString(queryResponse.getCurrentPlayers()));
                        networkDataOutputStream.writeString(Integer.toString(queryResponse.getMaxPlayers()));
                        networkDataOutputStream.writeShort((short) queryResponse.getPort());
                        networkDataOutputStream.writeString(queryResponse.getHostname());
                        // Paper end
                        this.sendTo(networkDataOutputStream.toByteArray(), packet);
                        LOGGER.debug("Status [{}]", (Object)socketAddress);
                    }
                default:
                    return true;
                case 9:
                    this.sendChallenge(packet);
                    LOGGER.debug("Challenge [{}]", (Object)socketAddress);
                    return true;
            }
        } else {
            LOGGER.debug("Invalid packet [{}]", (Object)socketAddress);
            return false;
        }
    }

    private byte[] buildRuleResponse(DatagramPacket packet) throws IOException {
        long l = Util.getMillis();
        if (l < this.lastRulesResponse + 5000L) {
            byte[] bs = this.rulesResponse.toByteArray();
            byte[] cs = this.getIdentBytes(packet.getSocketAddress());
            bs[1] = cs[0];
            bs[2] = cs[1];
            bs[3] = cs[2];
            bs[4] = cs[3];
            return bs;
        } else {
            this.lastRulesResponse = l;
            this.rulesResponse.reset();
            this.rulesResponse.write(0);
            this.rulesResponse.writeBytes(this.getIdentBytes(packet.getSocketAddress()));
            this.rulesResponse.writeString("splitnum");
            this.rulesResponse.write(128);
            this.rulesResponse.write(0);
            // Paper start
            // Pack plugins
            java.util.List<com.destroystokyo.paper.event.server.GS4QueryEvent.QueryResponse.PluginInformation> plugins = java.util.Collections.emptyList();
            org.bukkit.plugin.Plugin[] bukkitPlugins;
            if (((net.minecraft.server.dedicated.DedicatedServer) this.serverInterface).server.getQueryPlugins() && (bukkitPlugins = org.bukkit.Bukkit.getPluginManager().getPlugins()).length > 0) {
                plugins = java.util.stream.Stream.of(bukkitPlugins)
                    .map(plugin -> com.destroystokyo.paper.event.server.GS4QueryEvent.QueryResponse.PluginInformation.of(plugin.getName(), plugin.getDescription().getVersion()))
                    .collect(java.util.stream.Collectors.toList());
            }

            com.destroystokyo.paper.event.server.GS4QueryEvent.QueryResponse queryResponse = com.destroystokyo.paper.event.server.GS4QueryEvent.QueryResponse.builder()
                .motd(this.serverName)
                .map(this.worldName)
                .currentPlayers(this.serverInterface.getPlayerCount())
                .maxPlayers(this.maxPlayers)
                .port(this.serverPort)
                .hostname(this.hostIp)
                .plugins(plugins)
                .players(this.serverInterface.getPlayerNames())
                .gameVersion(this.serverInterface.getServerVersion())
                .serverVersion(org.bukkit.Bukkit.getServer().getName() + " on " + org.bukkit.Bukkit.getServer().getBukkitVersion())
                .build();
            com.destroystokyo.paper.event.server.GS4QueryEvent.QueryType queryType =
                com.destroystokyo.paper.event.server.GS4QueryEvent.QueryType.FULL;
            com.destroystokyo.paper.event.server.GS4QueryEvent queryEvent =
                new com.destroystokyo.paper.event.server.GS4QueryEvent(queryType, packet.getAddress(), queryResponse);
            queryEvent.callEvent();
            queryResponse = queryEvent.getResponse();
            this.rulesResponse.writeString("hostname");
            this.rulesResponse.writeString(queryResponse.getMotd());
            this.rulesResponse.writeString("gametype");
            this.rulesResponse.writeString("SMP");
            this.rulesResponse.writeString("game_id");
            this.rulesResponse.writeString("MINECRAFT");
            this.rulesResponse.writeString("version");
            this.rulesResponse.writeString(queryResponse.getGameVersion());
            this.rulesResponse.writeString("plugins");
            java.lang.StringBuilder pluginsString = new java.lang.StringBuilder();
            pluginsString.append(queryResponse.getServerVersion());
            if (!queryResponse.getPlugins().isEmpty()) {
                pluginsString.append(": ");
                java.util.Iterator<com.destroystokyo.paper.event.server.GS4QueryEvent.QueryResponse.PluginInformation> iter = queryResponse.getPlugins().iterator();
                while (iter.hasNext()) {
                    com.destroystokyo.paper.event.server.GS4QueryEvent.QueryResponse.PluginInformation info = iter.next();
                    pluginsString.append(info.getName());
                    if (info.getVersion() != null) {
                        pluginsString.append(' ').append(info.getVersion().replace(";", ","));
                    }
                    if (iter.hasNext()) {
                        pluginsString.append(';').append(' ');
                    }
                }
            }
            this.rulesResponse.writeString(pluginsString.toString());
            this.rulesResponse.writeString("map");
            this.rulesResponse.writeString(queryResponse.getMap());
            this.rulesResponse.writeString("numplayers");
            this.rulesResponse.writeString(Integer.toString(queryResponse.getCurrentPlayers()));
            this.rulesResponse.writeString("maxplayers");
            this.rulesResponse.writeString(Integer.toString(queryResponse.getMaxPlayers()));
            this.rulesResponse.writeString("hostport");
            this.rulesResponse.writeString(Integer.toString(queryResponse.getPort()));
            this.rulesResponse.writeString("hostip");
            this.rulesResponse.writeString(queryResponse.getHostname());
            this.rulesResponse.write(0);
            this.rulesResponse.write(1);
            this.rulesResponse.writeString("player_");
            this.rulesResponse.write(0);
            String[] strings = queryResponse.getPlayers().toArray(String[]::new);

            for(String string : strings) {
                this.rulesResponse.writeString(string);
            }

            this.rulesResponse.write(0);
            return this.rulesResponse.toByteArray();
        }
    }

    private byte[] getIdentBytes(SocketAddress address) {
        return this.validChallenges.get(address).getIdentBytes();
    }

    private Boolean validChallenge(DatagramPacket packet) {
        SocketAddress socketAddress = packet.getSocketAddress();
        if (!this.validChallenges.containsKey(socketAddress)) {
            return false;
        } else {
            byte[] bs = packet.getData();
            return this.validChallenges.get(socketAddress).getChallenge() == PktUtils.intFromNetworkByteArray(bs, 7, packet.getLength());
        }
    }

    private void sendChallenge(DatagramPacket packet) throws IOException {
        QueryThreadGs4.RequestChallenge requestChallenge = new QueryThreadGs4.RequestChallenge(packet);
        this.validChallenges.put(packet.getSocketAddress(), requestChallenge);
        this.sendTo(requestChallenge.getChallengeBytes(), packet);
    }

    private void pruneChallenges() {
        if (this.running) {
            long l = Util.getMillis();
            if (l >= this.lastChallengeCheck + 30000L) {
                this.lastChallengeCheck = l;
                this.validChallenges.values().removeIf((query) -> {
                    return query.before(l);
                });
            }
        }
    }

    @Override
    public void run() {
        LOGGER.info("Query running on {}:{}", this.serverIp, this.port);
        this.lastChallengeCheck = Util.getMillis();
        DatagramPacket datagramPacket = new DatagramPacket(this.buffer, this.buffer.length);

        try {
            while(this.running) {
                try {
                    this.socket.receive(datagramPacket);
                    this.pruneChallenges();
                    this.processPacket(datagramPacket);
                } catch (SocketTimeoutException var8) {
                    this.pruneChallenges();
                } catch (PortUnreachableException var9) {
                } catch (IOException var10) {
                    this.recoverSocketError(var10);
                }
            }
        } finally {
            LOGGER.debug("closeSocket: {}:{}", this.serverIp, this.port);
            this.socket.close();
        }

    }

    @Override
    public boolean start() {
        if (this.running) {
            return true;
        } else {
            return !this.initSocket() ? false : super.start();
        }
    }

    private void recoverSocketError(Exception e) {
        if (this.running) {
            LOGGER.warn("Unexpected exception", (Throwable)e);
            if (!this.initSocket()) {
                LOGGER.error("Failed to recover from exception, shutting down!");
                this.running = false;
            }

        }
    }

    private boolean initSocket() {
        try {
            this.socket = new DatagramSocket(this.port, InetAddress.getByName(this.serverIp));
            this.socket.setSoTimeout(500);
            return true;
        } catch (Exception var2) {
            LOGGER.warn("Unable to initialise query system on {}:{}", this.serverIp, this.port, var2);
            return false;
        }
    }

    static class RequestChallenge {
        private final long time = (new Date()).getTime();
        private final int challenge;
        private final byte[] identBytes;
        private final byte[] challengeBytes;
        private final String ident;

        public RequestChallenge(DatagramPacket packet) {
            byte[] bs = packet.getData();
            this.identBytes = new byte[4];
            this.identBytes[0] = bs[3];
            this.identBytes[1] = bs[4];
            this.identBytes[2] = bs[5];
            this.identBytes[3] = bs[6];
            this.ident = new String(this.identBytes, StandardCharsets.UTF_8);
            this.challenge = RandomSource.create().nextInt(16777216);
            this.challengeBytes = String.format(Locale.ROOT, "\t%s%d\u0000", this.ident, this.challenge).getBytes(StandardCharsets.UTF_8);
        }

        public Boolean before(long lastQueryTime) {
            return this.time < lastQueryTime;
        }

        public int getChallenge() {
            return this.challenge;
        }

        public byte[] getChallengeBytes() {
            return this.challengeBytes;
        }

        public byte[] getIdentBytes() {
            return this.identBytes;
        }

        public String getIdent() {
            return this.ident;
        }
    }
}
