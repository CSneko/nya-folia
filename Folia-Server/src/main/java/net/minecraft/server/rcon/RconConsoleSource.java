package net.minecraft.server.rcon;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import java.net.SocketAddress;
import org.bukkit.craftbukkit.command.CraftRemoteConsoleCommandSender;
// CraftBukkit end
public class RconConsoleSource implements CommandSource {

    private static final String RCON = "Rcon";
    private static final Component RCON_COMPONENT = Component.literal("Rcon");
    private final StringBuffer buffer = new StringBuffer();
    private final MinecraftServer server;
    // CraftBukkit start
    public final SocketAddress socketAddress;
    private final CraftRemoteConsoleCommandSender remoteConsole = new CraftRemoteConsoleCommandSender(this);

    public RconConsoleSource(MinecraftServer minecraftserver, SocketAddress socketAddress) {
        this.socketAddress = socketAddress;
        // CraftBukkit end
        this.server = minecraftserver;
    }

    public void prepareForCommand() {
        this.buffer.setLength(0);
    }

    public String getCommandResponse() {
        return this.buffer.toString();
    }

    public CommandSourceStack createCommandSourceStack() {
        ServerLevel worldserver = this.server.overworld();

        return new CommandSourceStack(this, Vec3.atLowerCornerOf(worldserver.getSharedSpawnPos()), Vec2.ZERO, worldserver, 4, "Rcon", RconConsoleSource.RCON_COMPONENT, this.server, (Entity) null);
    }

    // CraftBukkit start - Send a String
    public void sendMessage(String message) {
        this.buffer.append(message);
    }

    @Override
    public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
        return this.remoteConsole;
    }
    // CraftBukkit end

    @Override
    public void sendSystemMessage(Component message) {
        this.buffer.append(message.getString());
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public boolean shouldInformAdmins() {
        return this.server.shouldRconBroadcast();
    }
}
