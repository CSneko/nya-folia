package net.minecraft.server.network;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.net.SocketAddress;
import java.util.Locale;
import net.minecraft.server.ServerInfo;
import org.slf4j.Logger;

public class LegacyQueryHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final ServerInfo server;
    private ByteBuf buf; // Paper

    public LegacyQueryHandler(ServerInfo server) {
        this.server = server;
    }

    public void channelRead(ChannelHandlerContext channelhandlercontext, Object object) {
        ByteBuf bytebuf = (ByteBuf) object;

        // Paper start - Make legacy ping handler more reliable
        if (this.buf != null) {
            try {
                readLegacy1_6(channelhandlercontext, bytebuf);
            } finally {
                bytebuf.release();
            }
            return;
        }
        // Paper end
        bytebuf.markReaderIndex();
        boolean flag = true;

        try {
            try {
                if (bytebuf.readUnsignedByte() != 254) {
                    return;
                }

                SocketAddress socketaddress = channelhandlercontext.channel().remoteAddress();
                int i = bytebuf.readableBytes();
                String s = null; // Paper
                // org.bukkit.event.server.ServerListPingEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callServerListPingEvent(socketaddress, this.server.getMotd(), this.server.getPlayerCount(), this.server.getMaxPlayers()); // CraftBukkit // Paper
                com.destroystokyo.paper.event.server.PaperServerListPingEvent event; // Paper

                if (i == 0) {
                    LegacyQueryHandler.LOGGER.debug("Ping: (<1.3.x) from {}", net.minecraft.server.MinecraftServer.getServer().logIPs() ? socketaddress: "<ip address withheld>"); // Paper

                    // Paper start - Call PaperServerListPingEvent and use results
                    event = com.destroystokyo.paper.network.PaperLegacyStatusClient.processRequest(net.minecraft.server.MinecraftServer.getServer(), (java.net.InetSocketAddress) socketaddress, 39, null);
                    if (event == null) {
                        channelhandlercontext.close();
                        bytebuf.release();
                        flag = false;
                        return;
                    }
                    s = String.format(Locale.ROOT, "%s\u00a7%d\u00a7%d", com.destroystokyo.paper.network.PaperLegacyStatusClient.getUnformattedMotd(event), event.getNumPlayers(), event.getMaxPlayers());
                    // Paper end
                    LegacyQueryHandler.sendFlushAndClose(channelhandlercontext, LegacyQueryHandler.createLegacyDisconnectPacket(channelhandlercontext.alloc(), s));
                } else {
                    if (bytebuf.readUnsignedByte() != 1) {
                        return;
                    }

                    if (bytebuf.isReadable()) {
                        // Paper start - Replace with improved version below
                        if (bytebuf.readUnsignedByte() != 250) {
                            s = this.readLegacy1_6(channelhandlercontext, bytebuf);
                            if (s == null) {
                                return;
                            }
                        }
                        // if (!LegacyQueryHandler.readCustomPayloadPacket(bytebuf)) {
                        //     return;
                        // }
                        //
                        // LegacyQueryHandler.LOGGER.debug("Ping: (1.6) from {}", socketaddress);
                        // Paper end
                    } else {
                        LegacyQueryHandler.LOGGER.debug("Ping: (1.4-1.5.x) from {}", net.minecraft.server.MinecraftServer.getServer().logIPs() ? socketaddress: "<ip address withheld>"); // Paper
                    }

                    if (s == null) {
                        // Paper start - Call PaperServerListPingEvent and use results
                        event = com.destroystokyo.paper.network.PaperLegacyStatusClient.processRequest(net.minecraft.server.MinecraftServer.getServer(), (java.net.InetSocketAddress) socketaddress, 127, null); // Paper
                        if (event == null) {
                            channelhandlercontext.close();
                            bytebuf.release();
                            flag = false;
                            return;
                        }
                        s = String.format(Locale.ROOT, "\u00a71\u0000%d\u0000%s\u0000%s\u0000%d\u0000%d", new Object[] { event.getProtocolVersion(), this.server.getServerVersion(), event.getMotd(), event.getNumPlayers(), event.getMaxPlayers()}); // CraftBukkit
                        // Paper end
                    }
                    LegacyQueryHandler.sendFlushAndClose(channelhandlercontext, LegacyQueryHandler.createLegacyDisconnectPacket(channelhandlercontext.alloc(), s));
                }

                bytebuf.release();
                flag = false;
            } catch (RuntimeException runtimeexception) {
                ;
            }

        } finally {
            if (flag) {
                bytebuf.resetReaderIndex();
                channelhandlercontext.channel().pipeline().remove(this);
                channelhandlercontext.fireChannelRead(object);
            }

        }
    }

    private static boolean readCustomPayloadPacket(ByteBuf buf) {
        short short0 = buf.readUnsignedByte();

        if (short0 != 250) {
            return false;
        } else {
            String s = LegacyProtocolUtils.readLegacyString(buf);

            if (!"MC|PingHost".equals(s)) {
                return false;
            } else {
                int i = buf.readUnsignedShort();

                if (buf.readableBytes() != i) {
                    return false;
                } else {
                    short short1 = buf.readUnsignedByte();

                    if (short1 < 73) {
                        return false;
                    } else {
                        String s1 = LegacyProtocolUtils.readLegacyString(buf);
                        int j = buf.readInt();

                        return j <= 65535;
                    }
                }
            }
        }
    }

    // Paper start
    private static String readLegacyString(ByteBuf buf) {
        int size = buf.readShort() * Character.BYTES;
        if (!buf.isReadable(size)) {
            return null;
        }

        String result = buf.toString(buf.readerIndex(), size, java.nio.charset.StandardCharsets.UTF_16BE);
        buf.skipBytes(size); // toString doesn't increase readerIndex automatically
        return result;
    }

    private String readLegacy1_6(ChannelHandlerContext ctx, ByteBuf part) {
        ByteBuf buf = this.buf;

        if (buf == null) {
            this.buf = buf = ctx.alloc().buffer();
            buf.markReaderIndex();
        } else {
            buf.resetReaderIndex();
        }

        buf.writeBytes(part);

        if (!buf.isReadable(Short.BYTES + Short.BYTES + Byte.BYTES + Short.BYTES + Integer.BYTES)) {
            return null;
        }

        String s = readLegacyString(buf);
        if (s == null) {
            return null;
        }

        if (!s.equals("MC|PingHost")) {
            removeHandler(ctx);
            return null;
        }

        if (!buf.isReadable(Short.BYTES) || !buf.isReadable(buf.readShort())) {
            return null;
        }

        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        int protocolVersion = buf.readByte();
        String host = readLegacyString(buf);
        if (host == null) {
            removeHandler(ctx);
            return null;
        }
        int port = buf.readInt();

        if (buf.isReadable()) {
            removeHandler(ctx);
            return null;
        }

        buf.release();
        this.buf = null;

        LOGGER.debug("Ping: (1.6) from {}", net.minecraft.server.MinecraftServer.getServer().logIPs() ? ctx.channel().remoteAddress(): "<ip address withheld>"); // Paper

        java.net.InetSocketAddress virtualHost = com.destroystokyo.paper.network.PaperNetworkClient.prepareVirtualHost(host, port);
        com.destroystokyo.paper.event.server.PaperServerListPingEvent event = com.destroystokyo.paper.network.PaperLegacyStatusClient.processRequest(
                server, (java.net.InetSocketAddress) ctx.channel().remoteAddress(), protocolVersion, virtualHost);
        if (event == null) {
            ctx.close();
            return null;
        }

        String response = String.format("\u00a71\u0000%d\u0000%s\u0000%s\u0000%d\u0000%d", event.getProtocolVersion(), event.getVersion(),
            com.destroystokyo.paper.network.PaperLegacyStatusClient.getMotd(event), event.getNumPlayers(), event.getMaxPlayers());
        return response;
    }

    private void removeHandler(ChannelHandlerContext ctx) {
        ByteBuf buf = this.buf;
        this.buf = null;

        buf.resetReaderIndex();
        ctx.pipeline().remove(this);
        ctx.fireChannelRead(buf);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (this.buf != null) {
            this.buf.release();
            this.buf = null;
        }
    }
    // Paper end

    // CraftBukkit start
    private static String createVersion0Response(ServerInfo serverinfo, org.bukkit.event.server.ServerListPingEvent event) {
        return String.format(Locale.ROOT, "%s\u00a7%d\u00a7%d", event.getMotd(), event.getNumPlayers(), event.getMaxPlayers());
        // CraftBukkit end
    }

    // CraftBukkit start
    private static String createVersion1Response(ServerInfo serverinfo, org.bukkit.event.server.ServerListPingEvent event) {
        return String.format(Locale.ROOT, "\u00a71\u0000%d\u0000%s\u0000%s\u0000%d\u0000%d", 127, serverinfo.getServerVersion(), event.getMotd(), event.getNumPlayers(), event.getMaxPlayers());
        // CraftBukkit end
    }

    private static void sendFlushAndClose(ChannelHandlerContext context, ByteBuf buf) {
        context.pipeline().firstContext().writeAndFlush(buf).addListener(ChannelFutureListener.CLOSE);
    }

    private static ByteBuf createLegacyDisconnectPacket(ByteBufAllocator allocator, String string) {
        ByteBuf bytebuf = allocator.buffer();

        bytebuf.writeByte(255);
        LegacyProtocolUtils.writeLegacyString(bytebuf, string);
        return bytebuf;
    }
}
