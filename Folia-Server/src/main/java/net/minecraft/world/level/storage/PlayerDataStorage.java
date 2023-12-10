package net.minecraft.world.level.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.File;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

// CraftBukkit start
import java.io.FileInputStream;
import java.io.InputStream;
import org.bukkit.craftbukkit.entity.CraftPlayer;
// CraftBukkit end

public class PlayerDataStorage {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final File playerDir;
    protected final DataFixer fixerUpper;

    public PlayerDataStorage(LevelStorageSource.LevelStorageAccess session, DataFixer dataFixer) {
        this.fixerUpper = dataFixer;
        this.playerDir = session.getLevelPath(LevelResource.PLAYER_DATA_DIR).toFile();
        this.playerDir.mkdirs();
    }

    public void save(Player player) {
        if (org.spigotmc.SpigotConfig.disablePlayerDataSaving) return; // Spigot
        try {
            CompoundTag nbttagcompound = player.saveWithoutId(new CompoundTag());
            File file = File.createTempFile(player.getStringUUID() + "-", ".dat", this.playerDir);

            NbtIo.writeCompressed(nbttagcompound, file);
            File file1 = new File(this.playerDir, player.getStringUUID() + ".dat");
            File file2 = new File(this.playerDir, player.getStringUUID() + ".dat_old");

            Util.safeReplaceFile(file1, file, file2);
        } catch (Exception exception) {
            PlayerDataStorage.LOGGER.warn("Failed to save player data for {}", player.getScoreboardName(), exception); // Paper
        }

    }

    @Nullable
    public CompoundTag load(Player player) {
        CompoundTag nbttagcompound = null;

        try {
            File file = new File(this.playerDir, player.getStringUUID() + ".dat");
            // Spigot Start
            boolean usingWrongFile = false;
            if ( org.bukkit.Bukkit.getOnlineMode() && !file.exists() ) // Paper - Check online mode first
            {
                file = new File( this.playerDir, java.util.UUID.nameUUIDFromBytes( ( "OfflinePlayer:" + player.getScoreboardName() ).getBytes( "UTF-8" ) ).toString() + ".dat");
                if ( file.exists() )
                {
                    usingWrongFile = true;
                    org.bukkit.Bukkit.getServer().getLogger().warning( "Using offline mode UUID file for player " + player.getScoreboardName() + " as it is the only copy we can find." );
                }
            }
            // Spigot End

            if (file.exists() && file.isFile()) {
                nbttagcompound = NbtIo.readCompressed(file);
            }
            // Spigot Start
            if ( usingWrongFile )
            {
                file.renameTo( new File( file.getPath() + ".offline-read" ) );
            }
            // Spigot End
        } catch (Exception exception) {
            PlayerDataStorage.LOGGER.warn("Failed to load player data for {}", player.getName().getString());
        }

        if (nbttagcompound != null) {
            // CraftBukkit start
            if (player instanceof ServerPlayer) {
                CraftPlayer player1 = (CraftPlayer) player.getBukkitEntity();
                // Only update first played if it is older than the one we have
                long modified = new File(this.playerDir, player.getUUID().toString() + ".dat").lastModified();
                if (modified < player1.getFirstPlayed()) {
                    player1.setFirstPlayed(modified);
                }
            }
            // CraftBukkit end
            int i = NbtUtils.getDataVersion(nbttagcompound, -1);

            player.load(ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.PLAYER, nbttagcompound, i, net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion())); // Paper - replace player converter
        }

        return nbttagcompound;
    }

    // CraftBukkit start
    public CompoundTag getPlayerData(String s) {
        try {
            File file1 = new File(this.playerDir, s + ".dat");

            if (file1.exists()) {
                return NbtIo.readCompressed((InputStream) (new FileInputStream(file1)));
            }
        } catch (Exception exception) {
            PlayerDataStorage.LOGGER.warn("Failed to load player data for " + s);
        }

        return null;
    }
    // CraftBukkit end

    public String[] getSeenPlayers() {
        String[] astring = this.playerDir.list();

        if (astring == null) {
            astring = new String[0];
        }

        for (int i = 0; i < astring.length; ++i) {
            if (astring[i].endsWith(".dat")) {
                astring[i] = astring[i].substring(0, astring[i].length() - 4);
            }
        }

        return astring;
    }

    // CraftBukkit start
    public File getPlayerDir() {
        return this.playerDir;
    }
    // CraftBukkit end
}
