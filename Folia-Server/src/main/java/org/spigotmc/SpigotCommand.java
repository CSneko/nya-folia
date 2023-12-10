package org.spigotmc;

import java.io.File;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class SpigotCommand extends Command {

    public SpigotCommand(String name) {
        super(name);
        this.description = "Spigot related commands";
        this.usageMessage = "/spigot reload";
        this.setPermission("bukkit.command.spigot");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) return true;

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: " + this.usageMessage);
            return false;
        }

        if (args[0].equals("reload")) {
            Command.broadcastCommandMessage(sender, ChatColor.GREEN + "主人，使用这个命令来重载服务器是不受支持的喵");
            Command.broadcastCommandMessage(sender, ChatColor.GREEN + "如果服务器被这个命令van坏了，请使用/stop来重启喵");

            io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(() -> { // Folia - region threading
            MinecraftServer console = MinecraftServer.getServer();
            org.spigotmc.SpigotConfig.init((File) console.options.valueOf("spigot-settings"));
            for (ServerLevel world : console.getAllLevels()) {
                world.spigotConfig.init();
            }
            console.server.reloadCount++;

            Command.broadcastCommandMessage(sender, ChatColor.GREEN + "喵~重载完成");
            }); // Folia - region threading
        }

        return true;
    }
}
