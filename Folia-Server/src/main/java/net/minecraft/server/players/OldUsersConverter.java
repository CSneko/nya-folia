package net.minecraft.server.players;

import com.destroystokyo.paper.exception.ServerInternalException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.yggdrasil.ProfileNotFoundException;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

public class OldUsersConverter {

    static final Logger LOGGER = LogUtils.getLogger();
    public static final File OLD_IPBANLIST = new File("banned-ips.txt");
    public static final File OLD_USERBANLIST = new File("banned-players.txt");
    public static final File OLD_OPLIST = new File("ops.txt");
    public static final File OLD_WHITELIST = new File("white-list.txt");

    public OldUsersConverter() {}

    static List<String> readOldListFormat(File file, Map<String, String[]> valueMap) throws IOException {
        List<String> list = Files.readLines(file, StandardCharsets.UTF_8);
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();

            s = s.trim();
            if (!s.startsWith("#") && s.length() >= 1) {
                String[] astring = s.split("\\|");

                valueMap.put(astring[0].toLowerCase(Locale.ROOT), astring);
            }
        }

        return list;
    }

    private static void lookupPlayers(MinecraftServer server, Collection<String> bannedPlayers, ProfileLookupCallback callback) {
        String[] astring = (String[]) bannedPlayers.stream().filter((s) -> {
            return !StringUtil.isNullOrEmpty(s);
        }).toArray((i) -> {
            return new String[i];
        });

        if (server.usesAuthentication() ||
            (io.papermc.paper.configuration.GlobalConfiguration.get().proxies.isProxyOnlineMode())) { // Spigot: bungee = online mode, for now.  // Paper - Handle via setting
            server.getProfileRepository().findProfilesByNames(astring, callback);
        } else {
            String[] astring1 = astring;
            int i = astring.length;

            for (int j = 0; j < i; ++j) {
                String s = astring1[j];
                UUID uuid = UUIDUtil.createOfflinePlayerUUID(s);
                GameProfile gameprofile = new GameProfile(uuid, s);

                callback.onProfileLookupSucceeded(gameprofile);
            }
        }

    }

    public static boolean convertUserBanlist(final MinecraftServer server) {
        final UserBanList gameprofilebanlist = new UserBanList(PlayerList.USERBANLIST_FILE);

        if (OldUsersConverter.OLD_USERBANLIST.exists() && OldUsersConverter.OLD_USERBANLIST.isFile()) {
            if (gameprofilebanlist.getFile().exists()) {
                try {
                    gameprofilebanlist.load();
                } catch (IOException ioexception) {
                    OldUsersConverter.LOGGER.warn("Could not load existing file {}", gameprofilebanlist.getFile().getName()); // CraftBukkit - don't print stacktrace
                }
            }

            try {
                final Map<String, String[]> map = Maps.newHashMap();

                OldUsersConverter.readOldListFormat(OldUsersConverter.OLD_USERBANLIST, map);
                ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
                    public void onProfileLookupSucceeded(GameProfile gameprofile) {
                        server.getProfileCache().add(gameprofile);
                        String[] astring = (String[]) map.get(gameprofile.getName().toLowerCase(Locale.ROOT));

                        if (astring == null) {
                            OldUsersConverter.LOGGER.warn("Could not convert user banlist entry for {}", gameprofile.getName());
                            throw new OldUsersConverter.ConversionError("Profile not in the conversionlist");
                        } else {
                            Date date = astring.length > 1 ? OldUsersConverter.parseDate(astring[1], (Date) null) : null;
                            String s = astring.length > 2 ? astring[2] : null;
                            Date date1 = astring.length > 3 ? OldUsersConverter.parseDate(astring[3], (Date) null) : null;
                            String s1 = astring.length > 4 ? astring[4] : null;

                            gameprofilebanlist.add(new UserBanListEntry(gameprofile, date, s, date1, s1));
                        }
                    }

                    public void onProfileLookupFailed(String s, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup user banlist entry for {}", s, exception);
                        if (!(exception instanceof ProfileNotFoundException)) {
                            throw new OldUsersConverter.ConversionError("Could not request user " + s + " from backend systems", exception);
                        }
                    }
                };

                OldUsersConverter.lookupPlayers(server, map.keySet(), profilelookupcallback);
                gameprofilebanlist.save();
                OldUsersConverter.renameOldFile(OldUsersConverter.OLD_USERBANLIST);
                return true;
            } catch (IOException ioexception1) {
                OldUsersConverter.LOGGER.warn("Could not read old user banlist to convert it!", ioexception1);
                return false;
            } catch (OldUsersConverter.ConversionError namereferencingfileconverter_fileconversionexception) {
                OldUsersConverter.LOGGER.error("Conversion failed, please try again later", namereferencingfileconverter_fileconversionexception);
                return false;
            }
        } else {
            return true;
        }
    }

    public static boolean convertIpBanlist(MinecraftServer server) {
        IpBanList ipbanlist = new IpBanList(PlayerList.IPBANLIST_FILE);

        if (OldUsersConverter.OLD_IPBANLIST.exists() && OldUsersConverter.OLD_IPBANLIST.isFile()) {
            if (ipbanlist.getFile().exists()) {
                try {
                    ipbanlist.load();
                } catch (IOException ioexception) {
                    OldUsersConverter.LOGGER.warn("Could not load existing file {}", ipbanlist.getFile().getName()); // CraftBukkit - don't print stacktrace
                }
            }

            try {
                Map<String, String[]> map = Maps.newHashMap();

                OldUsersConverter.readOldListFormat(OldUsersConverter.OLD_IPBANLIST, map);
                Iterator iterator = map.keySet().iterator();

                while (iterator.hasNext()) {
                    String s = (String) iterator.next();
                    String[] astring = (String[]) map.get(s);
                    Date date = astring.length > 1 ? OldUsersConverter.parseDate(astring[1], (Date) null) : null;
                    String s1 = astring.length > 2 ? astring[2] : null;
                    Date date1 = astring.length > 3 ? OldUsersConverter.parseDate(astring[3], (Date) null) : null;
                    String s2 = astring.length > 4 ? astring[4] : null;

                    ipbanlist.add(new IpBanListEntry(s, date, s1, date1, s2));
                }

                ipbanlist.save();
                OldUsersConverter.renameOldFile(OldUsersConverter.OLD_IPBANLIST);
                return true;
            } catch (IOException ioexception1) {
                OldUsersConverter.LOGGER.warn("Could not parse old ip banlist to convert it!", ioexception1);
                return false;
            }
        } else {
            return true;
        }
    }

    public static boolean convertOpsList(final MinecraftServer server) {
        final ServerOpList oplist = new ServerOpList(PlayerList.OPLIST_FILE);

        if (OldUsersConverter.OLD_OPLIST.exists() && OldUsersConverter.OLD_OPLIST.isFile()) {
            if (oplist.getFile().exists()) {
                try {
                    oplist.load();
                } catch (IOException ioexception) {
                    OldUsersConverter.LOGGER.warn("Could not load existing file {}", oplist.getFile().getName()); // CraftBukkit - don't print stacktrace
                }
            }

            try {
                List<String> list = Files.readLines(OldUsersConverter.OLD_OPLIST, StandardCharsets.UTF_8);
                ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
                    public void onProfileLookupSucceeded(GameProfile gameprofile) {
                        server.getProfileCache().add(gameprofile);
                        oplist.add(new ServerOpListEntry(gameprofile, server.getOperatorUserPermissionLevel(), false));
                    }

                    public void onProfileLookupFailed(String s, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup oplist entry for {}", s, exception);
                        if (!(exception instanceof ProfileNotFoundException)) {
                            throw new OldUsersConverter.ConversionError("Could not request user " + s + " from backend systems", exception);
                        }
                    }
                };

                OldUsersConverter.lookupPlayers(server, list, profilelookupcallback);
                oplist.save();
                OldUsersConverter.renameOldFile(OldUsersConverter.OLD_OPLIST);
                return true;
            } catch (IOException ioexception1) {
                OldUsersConverter.LOGGER.warn("Could not read old oplist to convert it!", ioexception1);
                return false;
            } catch (OldUsersConverter.ConversionError namereferencingfileconverter_fileconversionexception) {
                OldUsersConverter.LOGGER.error("Conversion failed, please try again later", namereferencingfileconverter_fileconversionexception);
                return false;
            }
        } else {
            return true;
        }
    }

    public static boolean convertWhiteList(final MinecraftServer server) {
        final UserWhiteList whitelist = new UserWhiteList(PlayerList.WHITELIST_FILE);

        if (OldUsersConverter.OLD_WHITELIST.exists() && OldUsersConverter.OLD_WHITELIST.isFile()) {
            if (whitelist.getFile().exists()) {
                try {
                    whitelist.load();
                } catch (IOException ioexception) {
                    OldUsersConverter.LOGGER.warn("Could not load existing file {}", whitelist.getFile().getName()); // CraftBukkit - don't print stacktrace
                }
            }

            try {
                List<String> list = Files.readLines(OldUsersConverter.OLD_WHITELIST, StandardCharsets.UTF_8);
                ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
                    public void onProfileLookupSucceeded(GameProfile gameprofile) {
                        server.getProfileCache().add(gameprofile);
                        whitelist.add(new UserWhiteListEntry(gameprofile));
                    }

                    public void onProfileLookupFailed(String s, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup user whitelist entry for {}", s, exception);
                        if (!(exception instanceof ProfileNotFoundException)) {
                            throw new OldUsersConverter.ConversionError("Could not request user " + s + " from backend systems", exception);
                        }
                    }
                };

                OldUsersConverter.lookupPlayers(server, list, profilelookupcallback);
                whitelist.save();
                OldUsersConverter.renameOldFile(OldUsersConverter.OLD_WHITELIST);
                return true;
            } catch (IOException ioexception1) {
                OldUsersConverter.LOGGER.warn("Could not read old whitelist to convert it!", ioexception1);
                return false;
            } catch (OldUsersConverter.ConversionError namereferencingfileconverter_fileconversionexception) {
                OldUsersConverter.LOGGER.error("Conversion failed, please try again later", namereferencingfileconverter_fileconversionexception);
                return false;
            }
        } else {
            return true;
        }
    }

    @Nullable
    public static UUID convertMobOwnerIfNecessary(final MinecraftServer server, String name) {
        if (!StringUtil.isNullOrEmpty(name) && name.length() <= 16) {
            Optional<UUID> optional = server.getProfileCache().get(name).map(GameProfile::getId);

            if (optional.isPresent()) {
                return (UUID) optional.get();
            } else if (!server.isSingleplayer() && server.usesAuthentication()) {
                final List<GameProfile> list = Lists.newArrayList();
                ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
                    public void onProfileLookupSucceeded(GameProfile gameprofile) {
                        server.getProfileCache().add(gameprofile);
                        list.add(gameprofile);
                    }

                    public void onProfileLookupFailed(String s1, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup user whitelist entry for {}", s1, exception);
                    }
                };

                OldUsersConverter.lookupPlayers(server, Lists.newArrayList(new String[]{name}), profilelookupcallback);
                return !list.isEmpty() ? ((GameProfile) list.get(0)).getId() : null;
            } else {
                return UUIDUtil.createOfflinePlayerUUID(name);
            }
        } else {
            try {
                return UUID.fromString(name);
            } catch (IllegalArgumentException illegalargumentexception) {
                return null;
            }
        }
    }

    public static boolean convertPlayers(final DedicatedServer minecraftServer) {
        final File file = OldUsersConverter.getWorldPlayersDirectory(minecraftServer);
        final File file1 = new File(file.getParentFile(), "playerdata");
        final File file2 = new File(file.getParentFile(), "unknownplayers");

        if (file.exists() && file.isDirectory()) {
            File[] afile = file.listFiles();
            List<String> list = Lists.newArrayList();
            File[] afile1 = afile;
            int i = afile.length;

            for (int j = 0; j < i; ++j) {
                File file3 = afile1[j];
                String s = file3.getName();

                if (s.toLowerCase(Locale.ROOT).endsWith(".dat")) {
                    String s1 = s.substring(0, s.length() - ".dat".length());

                    if (!s1.isEmpty()) {
                        list.add(s1);
                    }
                }
            }

            try {
                final String[] astring = (String[]) list.toArray(new String[list.size()]);
                ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
                    public void onProfileLookupSucceeded(GameProfile gameprofile) {
                        minecraftServer.getProfileCache().add(gameprofile);
                        UUID uuid = gameprofile.getId();

                        this.movePlayerFile(file1, this.getFileNameForProfile(gameprofile.getName()), uuid.toString());
                    }

                    public void onProfileLookupFailed(String s2, Exception exception) {
                        OldUsersConverter.LOGGER.warn("Could not lookup user uuid for {}", s2, exception);
                        if (exception instanceof ProfileNotFoundException) {
                            String s3 = this.getFileNameForProfile(s2);

                            this.movePlayerFile(file2, s3, s3);
                        } else {
                            throw new OldUsersConverter.ConversionError("Could not request user " + s2 + " from backend systems", exception);
                        }
                    }

                    private void movePlayerFile(File playerDataFolder, String fileName, String uuid) {
                        File file5 = new File(file, fileName + ".dat");
                        File file6 = new File(playerDataFolder, uuid + ".dat");

                        // CraftBukkit start - Use old file name to seed lastKnownName
                        CompoundTag root = null;

                        try {
                            root = NbtIo.readCompressed(new java.io.FileInputStream(file5));
                        } catch (Exception exception) {
                            io.papermc.paper.util.TraceUtil.printStackTrace(exception); // Paper
                            ServerInternalException.reportInternalException(exception); // Paper
                        }

                        if (root != null) {
                            if (!root.contains("bukkit")) {
                                root.put("bukkit", new CompoundTag());
                            }
                            CompoundTag data = root.getCompound("bukkit");
                            data.putString("lastKnownName", fileName);

                            try {
                                NbtIo.writeCompressed(root, new java.io.FileOutputStream(file2));
                            } catch (Exception exception) {
                                io.papermc.paper.util.TraceUtil.printStackTrace(exception); // Paper
                                ServerInternalException.reportInternalException(exception); // Paper
                            }
                       }
                        // CraftBukkit end

                        OldUsersConverter.ensureDirectoryExists(playerDataFolder);
                        if (!file5.renameTo(file6)) {
                            throw new OldUsersConverter.ConversionError("Could not convert file for " + fileName);
                        }
                    }

                    private String getFileNameForProfile(String s2) {
                        String s3 = null;
                        String[] astring1 = astring;
                        int k = astring1.length;

                        for (int l = 0; l < k; ++l) {
                            String s4 = astring1[l];

                            if (s4 != null && s4.equalsIgnoreCase(s2)) {
                                s3 = s4;
                                break;
                            }
                        }

                        if (s3 == null) {
                            throw new OldUsersConverter.ConversionError("Could not find the filename for " + s2 + " anymore");
                        } else {
                            return s3;
                        }
                    }
                };

                OldUsersConverter.lookupPlayers(minecraftServer, Lists.newArrayList(astring), profilelookupcallback);
                return true;
            } catch (OldUsersConverter.ConversionError namereferencingfileconverter_fileconversionexception) {
                OldUsersConverter.LOGGER.error("Conversion failed, please try again later", namereferencingfileconverter_fileconversionexception);
                return false;
            }
        } else {
            return true;
        }
    }

    static void ensureDirectoryExists(File directory) {
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new OldUsersConverter.ConversionError("Can't create directory " + directory.getName() + " in world save directory.");
            }
        } else if (!directory.mkdirs()) {
            throw new OldUsersConverter.ConversionError("Can't create directory " + directory.getName() + " in world save directory.");
        }
    }

    public static boolean serverReadyAfterUserconversion(MinecraftServer server) {
        boolean flag = OldUsersConverter.areOldUserlistsRemoved();

        flag = flag && OldUsersConverter.areOldPlayersConverted(server);
        return flag;
    }

    private static boolean areOldUserlistsRemoved() {
        boolean flag = false;

        if (OldUsersConverter.OLD_USERBANLIST.exists() && OldUsersConverter.OLD_USERBANLIST.isFile()) {
            flag = true;
        }

        boolean flag1 = false;

        if (OldUsersConverter.OLD_IPBANLIST.exists() && OldUsersConverter.OLD_IPBANLIST.isFile()) {
            flag1 = true;
        }

        boolean flag2 = false;

        if (OldUsersConverter.OLD_OPLIST.exists() && OldUsersConverter.OLD_OPLIST.isFile()) {
            flag2 = true;
        }

        boolean flag3 = false;

        if (OldUsersConverter.OLD_WHITELIST.exists() && OldUsersConverter.OLD_WHITELIST.isFile()) {
            flag3 = true;
        }

        if (!flag && !flag1 && !flag2 && !flag3) {
            return true;
        } else {
            OldUsersConverter.LOGGER.warn("**** FAILED TO START THE SERVER AFTER ACCOUNT CONVERSION!");
            OldUsersConverter.LOGGER.warn("** please remove the following files and restart the server:");
            if (flag) {
                OldUsersConverter.LOGGER.warn("* {}", OldUsersConverter.OLD_USERBANLIST.getName());
            }

            if (flag1) {
                OldUsersConverter.LOGGER.warn("* {}", OldUsersConverter.OLD_IPBANLIST.getName());
            }

            if (flag2) {
                OldUsersConverter.LOGGER.warn("* {}", OldUsersConverter.OLD_OPLIST.getName());
            }

            if (flag3) {
                OldUsersConverter.LOGGER.warn("* {}", OldUsersConverter.OLD_WHITELIST.getName());
            }

            return false;
        }
    }

    private static boolean areOldPlayersConverted(MinecraftServer server) {
        File file = OldUsersConverter.getWorldPlayersDirectory(server);

        if (file.exists() && file.isDirectory() && (file.list().length > 0 || !file.delete())) {
            OldUsersConverter.LOGGER.warn("**** DETECTED OLD PLAYER DIRECTORY IN THE WORLD SAVE");
            OldUsersConverter.LOGGER.warn("**** THIS USUALLY HAPPENS WHEN THE AUTOMATIC CONVERSION FAILED IN SOME WAY");
            OldUsersConverter.LOGGER.warn("** please restart the server and if the problem persists, remove the directory '{}'", file.getPath());
            return false;
        } else {
            return true;
        }
    }

    private static File getWorldPlayersDirectory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.PLAYER_OLD_DATA_DIR).toFile();
    }

    private static void renameOldFile(File file) {
        File file1 = new File(file.getName() + ".converted");

        file.renameTo(file1);
    }

    static Date parseDate(String dateString, Date fallback) {
        Date date1;

        try {
            date1 = BanListEntry.DATE_FORMAT.get().parse(dateString); // Folia - region threading - SDF is not thread-safe
        } catch (ParseException parseexception) {
            date1 = fallback;
        }

        return date1;
    }

    private static class ConversionError extends RuntimeException {

        ConversionError(String message, Throwable cause) {
            super(message, cause);
        }

        ConversionError(String message) {
            super(message);
        }
    }
}
