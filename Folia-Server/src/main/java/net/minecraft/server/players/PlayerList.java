package net.minecraft.server.players;

import co.aikar.timings.MinecraftTimings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.io.File;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.FileUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard; // Paper
import net.minecraft.world.scores.Team;
import org.slf4j.Logger;

// CraftBukkit start
import java.util.stream.Collectors;
import net.minecraft.server.dedicated.DedicatedServer;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRespawnEvent.RespawnReason;
import org.bukkit.event.player.PlayerSpawnChangeEvent;
// CraftBukkit end

public abstract class PlayerList {

    public static final File USERBANLIST_FILE = new File("banned-players.json");
    public static final File IPBANLIST_FILE = new File("banned-ips.json");
    public static final File OPLIST_FILE = new File("ops.json");
    public static final File WHITELIST_FILE = new File("whitelist.json");
    public static final Component CHAT_FILTERED_FULL = Component.translatable("chat.filtered_full");
    public static final Component DUPLICATE_LOGIN_DISCONNECT_MESSAGE = Component.translatable("multiplayer.disconnect.duplicate_login");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SEND_PLAYER_INFO_INTERVAL = 600;
    private static final ThreadLocal<SimpleDateFormat> BAN_DATE_FORMAT = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z")); // Folia - region threading - SDF is not thread-safe
    private final MinecraftServer server;
    public final List<ServerPlayer> players = new java.util.concurrent.CopyOnWriteArrayList(); // CraftBukkit - ArrayList -> CopyOnWriteArrayList: Iterator safety
    private final Map<UUID, ServerPlayer> playersByUUID = new java.util.concurrent.ConcurrentHashMap<>(); // Folia - region threading - change to CHM - Note: we do NOT expect concurrency PER KEY!
    private final UserBanList bans;
    private final IpBanList ipBans;
    private final ServerOpList ops;
    private final UserWhiteList whitelist;
    // CraftBukkit start
    // private final Map<UUID, ServerStatisticManager> stats;
    // private final Map<UUID, AdvancementDataPlayer> advancements;
    // CraftBukkit end
    public final PlayerDataStorage playerIo;
    private boolean doWhiteList;
    private final LayeredRegistryAccess<RegistryLayer> registries;
    public int maxPlayers;
    private int viewDistance;
    private int simulationDistance;
    private boolean allowCheatsForAllPlayers;
    private static final boolean ALLOW_LOGOUTIVATOR = false;
    private int sendAllPlayerInfoIn;

    // CraftBukkit start
    private CraftServer cserver;
    private final Map<String,ServerPlayer> playersByName = new java.util.concurrent.ConcurrentHashMap<>(); // Folia - region threading - change to CHM - Note: we do NOT expect concurrency PER KEY!
    public @Nullable String collideRuleTeamName; // Paper - Team name used for collideRule

    // Folia start - region threading
    private final Object stateLock = new Object();
    private final Map<String, Connection> connectionByName = new java.util.HashMap<>();
    private final Map<UUID, Connection> connectionById = new java.util.HashMap<>();

    public boolean pushPendingJoin(String userName, UUID byId, Connection conn) {
        userName = userName.toLowerCase(java.util.Locale.ROOT);
        Connection conflictingName, conflictingId;
        synchronized (this.stateLock) {
            conflictingName = this.connectionByName.get(userName);
            conflictingId = this.connectionById.get(byId);

            if (conflictingName == null && conflictingId == null) {
                // Folia start - max concurrent login
                int loggedInCount = 0;
                for (Connection value : this.connectionById.values()) {
                    if (value.getPacketListener() instanceof ServerGamePacketListenerImpl) {
                        ++loggedInCount;
                    }
                }
                if ((this.connectionById.size() - loggedInCount) >= io.papermc.paper.configuration.GlobalConfiguration.get().misc.maxJoinsPerTick) {
                    return false;
                }
                // Folia end - max concurrent login
                this.connectionByName.put(userName, conn);
                this.connectionById.put(byId, conn);
            }
        }

        Component message = Component.translatable("multiplayer.disconnect.duplicate_login", new Object[0]);

        if (conflictingId != null || conflictingName != null) {
            if (conflictingName != null && conflictingName.isPlayerConnected()) {
                conflictingName.disconnectSafely(message, org.bukkit.event.player.PlayerKickEvent.Cause.DUPLICATE_LOGIN);
            }
            if (conflictingName != conflictingId && conflictingId != null && conflictingId.isPlayerConnected()) {
                conflictingId.disconnectSafely(message, org.bukkit.event.player.PlayerKickEvent.Cause.DUPLICATE_LOGIN);
            }
        }

        return conflictingName == null && conflictingId == null;
    }

    public void removeConnection(String userName, UUID byId, Connection conn) {
        userName = userName.toLowerCase(java.util.Locale.ROOT);
        synchronized (this.stateLock) {
            this.connectionByName.remove(userName, conn);
            this.connectionById.remove(byId, conn);
        }
    }

    private int getTotalConnections() {
        synchronized (this.stateLock) {
            return this.connectionById.size();
        }
    }
    // Folia end - region threading

    public PlayerList(MinecraftServer server, LayeredRegistryAccess<RegistryLayer> registryManager, PlayerDataStorage saveHandler, int maxPlayers) {
        this.cserver = server.server = new CraftServer((DedicatedServer) server, this);
        server.console = new com.destroystokyo.paper.console.TerminalConsoleCommandSender(); // Paper
        // CraftBukkit end

        this.bans = new UserBanList(PlayerList.USERBANLIST_FILE);
        this.ipBans = new IpBanList(PlayerList.IPBANLIST_FILE);
        this.ops = new ServerOpList(PlayerList.OPLIST_FILE);
        this.whitelist = new UserWhiteList(PlayerList.WHITELIST_FILE);
        // CraftBukkit start
        // this.stats = Maps.newHashMap();
        // this.advancements = Maps.newHashMap();
        // CraftBukkit end
        this.server = server;
        this.registries = registryManager;
        this.maxPlayers = maxPlayers;
        this.playerIo = saveHandler;
    }
    abstract public void loadAndSaveFiles(); // Paper - moved from DedicatedPlayerList constructor

    public void loadSpawnForNewPlayer(final Connection connection, final ServerPlayer player, final CommonListenerCookie clientData, org.apache.commons.lang3.mutable.MutableObject<CompoundTag> data, org.apache.commons.lang3.mutable.MutableObject<String> lastKnownName, ca.spottedleaf.concurrentutil.completable.Completable<Location> toComplete) { // Folia - region threading - rewrite login process
        player.isRealPlayer = true; // Paper
        player.loginTime = System.currentTimeMillis(); // Paper
        GameProfile gameprofile = player.getGameProfile();
        GameProfileCache usercache = this.server.getProfileCache();
        String s;

        if (usercache != null) {
            Optional<GameProfile> optional = usercache.get(gameprofile.getId());

            s = (String) optional.map(GameProfile::getName).orElse(gameprofile.getName());
            usercache.add(gameprofile);
        } else {
            s = gameprofile.getName();
        }

        CompoundTag nbttagcompound = this.load(player);
        ResourceKey<Level> resourcekey = null; // Paper
        // CraftBukkit start - Better rename detection
        if (nbttagcompound != null && nbttagcompound.contains("bukkit")) {
            CompoundTag bukkit = nbttagcompound.getCompound("bukkit");
            s = bukkit.contains("lastKnownName", 8) ? bukkit.getString("lastKnownName") : s;
        }
        // CraftBukkit end

        // Paper start - move logic in Entity to here, to use bukkit supplied world UUID & reset to main world spawn if no valid world is found
        boolean invalidPlayerWorld = false;
        bukkitData: if (nbttagcompound != null) {
            // The main way for bukkit worlds to store the world is the world UUID despite mojang adding custom worlds
            final org.bukkit.World bWorld;
            if (nbttagcompound.contains("WorldUUIDMost") && nbttagcompound.contains("WorldUUIDLeast")) {
                bWorld = org.bukkit.Bukkit.getServer().getWorld(new UUID(nbttagcompound.getLong("WorldUUIDMost"), nbttagcompound.getLong("WorldUUIDLeast")));
            } else if (nbttagcompound.contains("world", net.minecraft.nbt.Tag.TAG_STRING)) { // Paper - legacy bukkit world name
                bWorld = org.bukkit.Bukkit.getServer().getWorld(nbttagcompound.getString("world"));
            } else {
                break bukkitData; // if neither of the bukkit data points exist, proceed to the vanilla migration section
            }
            if (bWorld != null) {
                resourcekey = ((CraftWorld) bWorld).getHandle().dimension();
            } else {
                resourcekey = Level.OVERWORLD;
                invalidPlayerWorld = true;
            }
        }
        if (resourcekey == null) { // only run the vanilla logic if we haven't found a world from the bukkit data
        // Below is the vanilla way of getting the dimension, this is for migration from vanilla servers
        // Paper end
        if (nbttagcompound != null) {
            DataResult<ResourceKey<Level>> dataresult = DimensionType.parseLegacy(new Dynamic(NbtOps.INSTANCE, nbttagcompound.get("Dimension"))); // CraftBukkit - decompile error
            Logger logger = PlayerList.LOGGER;

            Objects.requireNonNull(logger);
            // Paper start - reset to main world spawn if no valid world is found
            final Optional<ResourceKey<Level>> result = dataresult.resultOrPartial(logger::error);
            invalidPlayerWorld = result.isEmpty();
            resourcekey = result.orElse(Level.OVERWORLD);
            // Paper end
        } else {
            resourcekey = Level.OVERWORLD; // Paper - revert to vanilla default main world, this isn't an "invalid world" since no player data existed
        }
        } // Paper

        ResourceKey<Level> resourcekey1 = resourcekey;
        ServerLevel worldserver = this.server.getLevel(resourcekey1);
        ServerLevel worldserver1;

        if (worldserver == null) {
            PlayerList.LOGGER.warn("Unknown respawn dimension {}, defaulting to overworld", resourcekey1);
            worldserver1 = this.server.overworld();
            invalidPlayerWorld = true; // Paper - reset to main world if no world with parsed value is found
        } else {
            worldserver1 = worldserver;
        }

        // Paper start
        if (nbttagcompound == null) {
            player.spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT; // set Player SpawnReason to DEFAULT on first login
        // Paper start - reset to main world spawn if first spawn or invalid world
        }
        // Folia start - region threading - rewrite login process
        // must write to these before toComplete is invoked
        data.setValue(nbttagcompound);
        lastKnownName.setValue(s);
        // Folia end - region threading - rewrite login process
        if (nbttagcompound == null || invalidPlayerWorld) {
        // Paper end
            ServerPlayer.fudgeSpawnLocation(worldserver1, player, toComplete); // only move to spawn on first login, otherwise, stay where you are.... // Folia - region threading
        } else { // Folia start - region threading - rewrite login process
            worldserver1.loadChunksForMoveAsync(
                player.getBoundingBox(),
                ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.HIGHER,
                (c) -> {
                    toComplete.complete(io.papermc.paper.util.MCUtil.toLocation(worldserver1, player.position()));
                }
            );
        }
        // Folia end - region threading - rewrite login process
        // Paper end
        // Folia start - region threading - rewrite login process
        return;
    }
    // nbttagcomound -> player data
    // s -> last known name
    public void placeNewPlayer(Connection connection, ServerPlayer player, CommonListenerCookie clientData, CompoundTag nbttagcompound, String s, Location selectedSpawn) {
        ServerLevel worldserver1 = ((CraftWorld)selectedSpawn.getWorld()).getHandle();
        player.setPosRaw(selectedSpawn.getX(), selectedSpawn.getY(), selectedSpawn.getZ());
        player.lastSave = System.nanoTime(); // changed to nanoTime
        // Folia end - region threading - rewrite login process
        player.setServerLevel(worldserver1);
        String s1 = connection.getLoggableAddress(this.server.logIPs());

        // Spigot start - spawn location event
        Player spawnPlayer = player.getBukkitEntity();
        org.spigotmc.event.player.PlayerSpawnLocationEvent ev = new com.destroystokyo.paper.event.player.PlayerInitialSpawnEvent(spawnPlayer, spawnPlayer.getLocation()); // Paper use our duplicate event
        //this.cserver.getPluginManager().callEvent(ev); // Folia - region threading - TODO WTF TO DO WITH THIS EVENT?

        Location loc = ev.getSpawnLocation();
        worldserver1 = ((CraftWorld) loc.getWorld()).getHandle();

        player.spawnIn(worldserver1);
        player.gameMode.setLevel((ServerLevel) player.level());
        // Paper start - set raw so we aren't fully joined to the world (not added to chunk or world)
        player.setPosRaw(loc.getX(), loc.getY(), loc.getZ());
        player.setRot(loc.getYaw(), loc.getPitch());
        // Paper end
        // Spigot end

        // CraftBukkit - Moved message to after join
        // PlayerList.LOGGER.info("{}[{}] logged in with entity id {} at ({}, {}, {})", new Object[]{entityplayer.getName().getString(), s1, entityplayer.getId(), entityplayer.getX(), entityplayer.getY(), entityplayer.getZ()});
        LevelData worlddata = worldserver1.getLevelData();

        player.loadGameTypes(nbttagcompound);
        ServerGamePacketListenerImpl playerconnection = new ServerGamePacketListenerImpl(this.server, connection, player, clientData);
        // Folia start - rewrite login process
        // only after setting the connection listener to game type, add the connection to this regions list
        worldserver1.getCurrentWorldData().connections.add(connection);
        // Folia end - rewrite login process
        GameRules gamerules = worldserver1.getGameRules();
        boolean flag = gamerules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
        boolean flag1 = gamerules.getBoolean(GameRules.RULE_REDUCEDDEBUGINFO);
        boolean flag2 = gamerules.getBoolean(GameRules.RULE_LIMITED_CRAFTING);

        // Spigot - view distance
        playerconnection.send(new ClientboundLoginPacket(player.getId(), worlddata.isHardcore(), this.server.levelKeys(), this.getMaxPlayers(), worldserver1.getWorld().getSendViewDistance(), worldserver1.getWorld().getSimulationDistance(), flag1, !flag, flag2, player.createCommonSpawnInfo(worldserver1))); // Paper - replace old player chunk management
        player.getBukkitEntity().sendSupportedChannels(); // CraftBukkit
        playerconnection.send(new ClientboundChangeDifficultyPacket(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
        playerconnection.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
        playerconnection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
        playerconnection.send(new ClientboundUpdateRecipesPacket(this.server.getRecipeManager().getRecipes()));
        this.sendPlayerPermissionLevel(player);
        player.getStats().markAllDirty();
        player.getRecipeBook().sendInitialRecipeBook(player);
        if (false) this.updateEntireScoreboard(worldserver1.getScoreboard(), player); // Folia - region threading
        this.server.invalidateStatus();
        MutableComponent ichatmutablecomponent;

        if (player.getGameProfile().getName().equalsIgnoreCase(s)) {
            ichatmutablecomponent = Component.translatable("multiplayer.player.joined", player.getDisplayName());
        } else {
            ichatmutablecomponent = Component.translatable("multiplayer.player.joined.renamed", player.getDisplayName(), s);
        }
        // CraftBukkit start
        ichatmutablecomponent.withStyle(ChatFormatting.YELLOW);
        Component joinMessage = ichatmutablecomponent; // Paper - Adventure

        playerconnection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        ServerStatus serverping = this.server.getStatus();

        if (serverping != null) {
            player.sendServerStatus(serverping);
        }

        // entityplayer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(this.players)); // CraftBukkit - replaced with loop below
        this.players.add(player);
        this.playersByName.put(player.getScoreboardName().toLowerCase(java.util.Locale.ROOT), player); // Spigot
        this.playersByUUID.put(player.getUUID(), player);
        // this.broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(entityplayer))); // CraftBukkit - replaced with loop below

        // Paper start - correctly register player BEFORE PlayerJoinEvent, so the entity is valid and doesn't require tick delay hacks
        player.supressTrackerForLogin = true;
        worldserver1.addNewPlayer(player);
        this.server.getCustomBossEvents().onPlayerConnect(player); // see commented out section below worldserver.addPlayerJoin(entityplayer);
        mountSavedVehicle(player, worldserver1, nbttagcompound);
        // Paper end
        // CraftBukkit start
        CraftPlayer bukkitPlayer = player.getBukkitEntity();

        // Ensure that player inventory is populated with its viewer
        player.containerMenu.transferTo(player.containerMenu, bukkitPlayer);

        PlayerJoinEvent playerJoinEvent = new PlayerJoinEvent(bukkitPlayer, io.papermc.paper.adventure.PaperAdventure.asAdventure(ichatmutablecomponent)); // Paper - Adventure
        this.cserver.getPluginManager().callEvent(playerJoinEvent);

        if (!player.connection.isAcceptingMessages()) {
            //return; // Folia - region threading - must still allow the player to connect, as we must add to chunk map before handling disconnect
        }

        final net.kyori.adventure.text.Component jm = playerJoinEvent.joinMessage();

        if (jm != null && !jm.equals(net.kyori.adventure.text.Component.empty())) { // Paper - Adventure
            joinMessage = io.papermc.paper.adventure.PaperAdventure.asVanilla(jm); // Paper - Adventure
            this.server.getPlayerList().broadcastSystemMessage(joinMessage, false); // Paper - Adventure
        }
        // CraftBukkit end

        // CraftBukkit start - sendAll above replaced with this loop
        ClientboundPlayerInfoUpdatePacket packet = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)); // Paper

        final List<ServerPlayer> onlinePlayers = Lists.newArrayListWithExpectedSize(this.players.size() - 1); // Paper - use single player info update packet
        for (ServerPlayer entityplayer1 : this.players) { // Folia - region threadingv

            if (entityplayer1.getBukkitEntity().canSee(bukkitPlayer)) {
                // Paper start
                if (entityplayer1.getBukkitEntity().isListed(bukkitPlayer)) {
                // Paper end
                entityplayer1.connection.send(packet);
                // Paper start
                } else {
                    entityplayer1.connection.send(ClientboundPlayerInfoUpdatePacket.createSinglePlayerInitializing(player, false));
                }
                // Paper end
            }

            if (entityplayer1 == player || !bukkitPlayer.canSee(entityplayer1.getBukkitEntity())) { // Paper - don't include joining player
                continue;
            }

            onlinePlayers.add(entityplayer1); // Paper - use single player info update packet
        }
        // Paper start - use single player info update packet
        if (!onlinePlayers.isEmpty()) {
            player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(onlinePlayers, player));
        }
        // Paper end
        player.sentListPacket = true;
        player.supressTrackerForLogin = false; // Paper
        ((ServerLevel)player.level()).getChunkSource().chunkMap.addEntity(player); // Paper - track entity now
        // CraftBukkit end

        //player.getEntityData().refresh(player); // CraftBukkit - BungeeCord#2321, send complete data to self on spawn Paper - THIS IS NOT NEEDED ANYMORE

        this.sendLevelInfo(player, worldserver1);

        // CraftBukkit start - Only add if the player wasn't moved in the event
        if (player.level() == worldserver1 && !worldserver1.players().contains(player)) {
            worldserver1.addNewPlayer(player);
            this.server.getCustomBossEvents().onPlayerConnect(player);
        }

        worldserver1 = player.serverLevel(); // CraftBukkit - Update in case join event changed it
        // CraftBukkit end
        Iterator iterator = player.getActiveEffects().iterator();

        while (iterator.hasNext()) {
            MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

            playerconnection.send(new ClientboundUpdateMobEffectPacket(player.getId(), mobeffect));
        }

        // Paper start - move vehicle into method so it can be called above - short circuit around that code
        onPlayerJoinFinish(player, worldserver1, s1);
        // Paper start - Send empty chunk, so players aren't stuck in the world loading screen with our chunk system not sending chunks when dead
        if (player.isDeadOrDying()) {
            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> plains = worldserver1.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(
                new net.minecraft.world.level.chunk.EmptyLevelChunk(worldserver1, player.chunkPosition(), plains),
                worldserver1.getLightEngine(), (java.util.BitSet)null, (java.util.BitSet) null, false)
            );
        }
        // Paper end
    }
    private void mountSavedVehicle(ServerPlayer player, ServerLevel worldserver1, CompoundTag nbttagcompound) {
        // Paper end
        if (nbttagcompound != null && nbttagcompound.contains("RootVehicle", 10)) {
            CompoundTag nbttagcompound1 = nbttagcompound.getCompound("RootVehicle");
            // CraftBukkit start
            ServerLevel finalWorldServer = worldserver1;
            Vec3 playerPos = player.position(); // Paper - force sync root vehicle to player position
            Entity entity = EntityType.loadEntityRecursive(nbttagcompound1.getCompound("Entity"), finalWorldServer, (entity1) -> {
                // Paper start - force sync root vehicle to player position
                if (entity1.distanceToSqr(player) > (5.0 * 5.0)) {
                    entity1.setPosRaw(playerPos.x, playerPos.y, playerPos.z, true);
                }
                // Paper end - force sync root vehicle to player position
                return !finalWorldServer.addWithUUID(entity1, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.MOUNT) ? null : entity1; // Paper
                // CraftBukkit end
            });

            if (entity != null) {
                UUID uuid;

                if (nbttagcompound1.hasUUID("Attach")) {
                    uuid = nbttagcompound1.getUUID("Attach");
                } else {
                    uuid = null;
                }

                Iterator iterator1;
                Entity entity1;

                if (entity.getUUID().equals(uuid)) {
                    player.startRiding(entity, true);
                } else {
                    iterator1 = entity.getIndirectPassengers().iterator();

                    while (iterator1.hasNext()) {
                        entity1 = (Entity) iterator1.next();
                        if (entity1.getUUID().equals(uuid)) {
                            player.startRiding(entity1, true);
                            break;
                        }
                    }
                }

                if (!player.isPassenger()) {
                    PlayerList.LOGGER.warn("Couldn't reattach entity to player");
                    entity.discard();
                    iterator1 = entity.getIndirectPassengers().iterator();

                    while (iterator1.hasNext()) {
                        entity1 = (Entity) iterator1.next();
                        entity1.discard();
                    }
                }
            }
        }

        // Paper start
    }
    public void onPlayerJoinFinish(ServerPlayer player, ServerLevel worldserver1, String s1) {
        // Paper end
        player.initInventoryMenu();
        // CraftBukkit - Moved from above, added world
        // Paper start - Add to collideRule team if needed
        final Scoreboard scoreboard = this.getServer().getLevel(Level.OVERWORLD).getScoreboard();
        final PlayerTeam collideRuleTeam = scoreboard.getPlayerTeam(this.collideRuleTeamName);
        if (false && this.collideRuleTeamName != null && collideRuleTeam != null && player.getTeam() == null) { // Folia - region threading
            scoreboard.addPlayerToTeam(player.getScoreboardName(), collideRuleTeam);
        }
        // Paper end
        // CraftBukkit - Moved from above, added world
        PlayerList.LOGGER.info("{}[{}] logged in with entity id {} at ([{}]{}, {}, {})", player.getName().getString(), s1, player.getId(), worldserver1.serverLevelData.getLevelName(), player.getX(), player.getY(), player.getZ());
    }

    public void updateEntireScoreboard(ServerScoreboard scoreboard, ServerPlayer player) {
        Set<Objective> set = Sets.newHashSet();
        Iterator iterator = scoreboard.getPlayerTeams().iterator();

        while (iterator.hasNext()) {
            PlayerTeam scoreboardteam = (PlayerTeam) iterator.next();

            player.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(scoreboardteam, true));
        }

        DisplaySlot[] adisplayslot = DisplaySlot.values();
        int i = adisplayslot.length;

        for (int j = 0; j < i; ++j) {
            DisplaySlot displayslot = adisplayslot[j];
            Objective scoreboardobjective = scoreboard.getDisplayObjective(displayslot);

            if (scoreboardobjective != null && !set.contains(scoreboardobjective)) {
                List<Packet<?>> list = scoreboard.getStartTrackingPackets(scoreboardobjective);
                Iterator iterator1 = list.iterator();

                while (iterator1.hasNext()) {
                    Packet<?> packet = (Packet) iterator1.next();

                    player.connection.send(packet);
                }

                set.add(scoreboardobjective);
            }
        }

    }

    public void addWorldborderListener(ServerLevel world) {
        if (this.playerIo != null) return; // CraftBukkit
        world.getWorldBorder().addListener(new BorderChangeListener() {
            @Override
            public void onBorderSizeSet(WorldBorder border, double size) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderSizePacket(border), border.world); // CraftBukkit
            }

            @Override
            public void onBorderSizeLerping(WorldBorder border, double fromSize, double toSize, long time) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderLerpSizePacket(border), border.world); // CraftBukkit
            }

            @Override
            public void onBorderCenterSet(WorldBorder border, double centerX, double centerZ) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderCenterPacket(border), border.world); // CraftBukkit
            }

            @Override
            public void onBorderSetWarningTime(WorldBorder border, int warningTime) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderWarningDelayPacket(border), border.world); // CraftBukkit
            }

            @Override
            public void onBorderSetWarningBlocks(WorldBorder border, int warningBlockDistance) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderWarningDistancePacket(border), border.world); // CraftBukkit
            }

            @Override
            public void onBorderSetDamagePerBlock(WorldBorder border, double damagePerBlock) {}

            @Override
            public void onBorderSetDamageSafeZOne(WorldBorder border, double safeZoneRadius) {}
        });
    }

    @Nullable
    public CompoundTag load(ServerPlayer player) {
        CompoundTag nbttagcompound = this.server.getWorldData().getLoadedPlayerTag();
        CompoundTag nbttagcompound1;

        if (this.server.isSingleplayerOwner(player.getGameProfile()) && nbttagcompound != null) {
            nbttagcompound1 = nbttagcompound;
            player.load(nbttagcompound);
            PlayerList.LOGGER.debug("loading single player");
        } else {
            nbttagcompound1 = this.playerIo.load(player);
        }

        return nbttagcompound1;
    }

    protected void save(ServerPlayer player) {
        if (!player.getBukkitEntity().isPersistent()) return; // CraftBukkit
        player.lastSave = System.nanoTime(); // Folia - region threading - changed to nanoTime tracking
        this.playerIo.save(player);
        ServerStatsCounter serverstatisticmanager = (ServerStatsCounter) player.getStats(); // CraftBukkit

        if (serverstatisticmanager != null) {
            serverstatisticmanager.save();
        }

        PlayerAdvancements advancementdataplayer = (PlayerAdvancements) player.getAdvancements(); // CraftBukkit

        if (advancementdataplayer != null) {
            advancementdataplayer.save();
        }

    }

    public net.kyori.adventure.text.Component remove(ServerPlayer entityplayer) { // CraftBukkit - return string // Paper - return Component
        // Paper start
        return this.remove(entityplayer, net.kyori.adventure.text.Component.translatable("multiplayer.player.left", net.kyori.adventure.text.format.NamedTextColor.YELLOW, io.papermc.paper.configuration.GlobalConfiguration.get().messages.useDisplayNameInQuitMessage ? entityplayer.getBukkitEntity().displayName() : io.papermc.paper.adventure.PaperAdventure.asAdventure(entityplayer.getDisplayName())));
    }
    public net.kyori.adventure.text.Component remove(ServerPlayer entityplayer, net.kyori.adventure.text.Component leaveMessage) {
        // Paper end
        ServerLevel worldserver = entityplayer.serverLevel();

        entityplayer.awardStat(Stats.LEAVE_GAME);

        // CraftBukkit start - Quitting must be before we do final save of data, in case plugins need to modify it
        // See SPIGOT-5799, SPIGOT-6145
        if (entityplayer.containerMenu != entityplayer.inventoryMenu) {
            entityplayer.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.DISCONNECT); // Paper
        }

        PlayerQuitEvent playerQuitEvent = new PlayerQuitEvent(entityplayer.getBukkitEntity(), leaveMessage, entityplayer.quitReason); // Paper - Adventure & quit reason
        this.cserver.getPluginManager().callEvent(playerQuitEvent);
        entityplayer.getBukkitEntity().disconnect(playerQuitEvent.getQuitMessage());

        if (server.isSameThread()) entityplayer.doTick(); // SPIGOT-924 // Paper - don't tick during emergency shutdowns (Watchdog)
        // CraftBukkit end

        // Paper start - Remove from collideRule team if needed
        if (false && this.collideRuleTeamName != null) { // Folia - region threading
            final Scoreboard scoreBoard = this.server.getLevel(Level.OVERWORLD).getScoreboard();
            final PlayerTeam team = scoreBoard.getPlayersTeam(this.collideRuleTeamName);
            if (entityplayer.getTeam() == team && team != null) {
                scoreBoard.removePlayerFromTeam(entityplayer.getScoreboardName(), team);
            }
        }
        // Paper end

        // Paper - Drop carried item when player has disconnected
        if (!entityplayer.containerMenu.getCarried().isEmpty()) {
            net.minecraft.world.item.ItemStack carried = entityplayer.containerMenu.getCarried();
            entityplayer.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
            entityplayer.drop(carried, false);
        }
        // Paper end

        this.save(entityplayer);
        if (entityplayer.isPassenger()) {
            Entity entity = entityplayer.getRootVehicle();

            if (entity.hasExactlyOnePlayerPassenger()) {
                PlayerList.LOGGER.debug("Removing player mount");
                entityplayer.stopRiding();
                entity.getPassengersAndSelf().forEach((entity1) -> {
                    // Paper start
                    if (entity1 instanceof net.minecraft.world.entity.npc.AbstractVillager villager) {
                        final net.minecraft.world.entity.player.Player human = villager.getTradingPlayer();
                        if (human != null) {
                            villager.setTradingPlayer(null);
                        }
                    }
                    // Paper end
                    entity1.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
                });
            }
        }

        entityplayer.unRide();
        worldserver.removePlayerImmediately(entityplayer, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        // Folia - region threading - move to onDisconnect of common packet listener
        entityplayer.getAdvancements().stopListening();
        this.players.remove(entityplayer);
        this.playersByName.remove(entityplayer.getScoreboardName().toLowerCase(java.util.Locale.ROOT)); // Spigot
        this.server.getCustomBossEvents().onPlayerDisconnect(entityplayer);
        UUID uuid = entityplayer.getUUID();
        ServerPlayer entityplayer1 = (ServerPlayer) this.playersByUUID.get(uuid);

        if (entityplayer1 == entityplayer) {
            this.playersByUUID.remove(uuid);
            // CraftBukkit start
            // this.stats.remove(uuid);
            // this.advancements.remove(uuid);
            // CraftBukkit end
        }

        // CraftBukkit start
        // this.broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(entityplayer.getUUID())));
        ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(entityplayer.getUUID()));
        for (ServerPlayer entityplayer2 : this.players) { // Folia - region threading

            if (entityplayer2.getBukkitEntity().canSee(entityplayer.getBukkitEntity())) {
                entityplayer2.connection.send(packet);
            } else {
                entityplayer2.getBukkitEntity().onEntityRemove(entityplayer);
            }
        }
        // This removes the scoreboard (and player reference) for the specific player in the manager
        this.cserver.getScoreboardManager().removePlayer(entityplayer.getBukkitEntity());
        // CraftBukkit end

        return playerQuitEvent.quitMessage(); // Paper - Adventure
    }

    // CraftBukkit start - Whole method, SocketAddress to LoginListener, added hostname to signature, return EntityPlayer
    public ServerPlayer canPlayerLogin(ServerLoginPacketListenerImpl loginlistener, GameProfile gameprofile) {
        MutableComponent ichatmutablecomponent;

        // Moved from processLogin
        UUID uuid = gameprofile.getId();
        List<ServerPlayer> list = Lists.newArrayList();

        ServerPlayer entityplayer;

        // Folia - region threading - rewrite login process - moved to pushPendingJoin

        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            entityplayer = (ServerPlayer) iterator.next();
            // Folia - moved to pushPendingJoin
        }

        // Instead of kicking then returning, we need to store the kick reason
        // in the event, check with plugins to see if it's ok, and THEN kick
        // depending on the outcome.
        SocketAddress socketaddress = loginlistener.connection.getRemoteAddress();

        ServerPlayer entity = new ServerPlayer(this.server, this.server.getLevel(Level.OVERWORLD), gameprofile, ClientInformation.createDefault());
        Player player = entity.getBukkitEntity();
        PlayerLoginEvent event = new PlayerLoginEvent(player, loginlistener.connection.hostname, ((java.net.InetSocketAddress) socketaddress).getAddress(), ((java.net.InetSocketAddress) loginlistener.connection.channel.remoteAddress()).getAddress());

        // Paper start - Fix MC-158900
        UserBanListEntry gameprofilebanentry;
        if (getBans().isBanned(gameprofile) && (gameprofilebanentry = getBans().get(gameprofile)) != null) {
            // Paper end

            ichatmutablecomponent = Component.translatable("multiplayer.disconnect.banned.reason", gameprofilebanentry.getReason());
            if (gameprofilebanentry.getExpires() != null) {
                ichatmutablecomponent.append((Component) Component.translatable("multiplayer.disconnect.banned.expiration", PlayerList.BAN_DATE_FORMAT.get().format(gameprofilebanentry.getExpires()))); // Folia - region threading - SDF is not thread-safe
            }

            // return chatmessage;
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, io.papermc.paper.adventure.PaperAdventure.asAdventure(ichatmutablecomponent)); // Paper - Adventure
        } else if (!this.isWhiteListed(gameprofile, event)) { // Paper
            //ichatmutablecomponent = Component.translatable("multiplayer.disconnect.not_whitelisted"); // Paper
            //event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(org.spigotmc.SpigotConfig.whitelistMessage)); // Spigot // Paper - Adventure - moved to isWhitelisted
        } else if (this.getIpBans().isBanned(socketaddress) && getIpBans().get(socketaddress) != null && !this.getIpBans().get(socketaddress).hasExpired()) { // Paper - fix NPE with temp ip bans
            IpBanListEntry ipbanentry = this.ipBans.get(socketaddress);

            ichatmutablecomponent = Component.translatable("multiplayer.disconnect.banned_ip.reason", ipbanentry.getReason());
            if (ipbanentry.getExpires() != null) {
                ichatmutablecomponent.append((Component) Component.translatable("multiplayer.disconnect.banned_ip.expiration", PlayerList.BAN_DATE_FORMAT.get().format(ipbanentry.getExpires()))); // Folia - region threading - SDF is not thread-safe
            }

            // return chatmessage;
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, io.papermc.paper.adventure.PaperAdventure.asAdventure(ichatmutablecomponent)); // Paper - Adventure
        } else {
            // return this.players.size() >= this.maxPlayers && !this.canBypassPlayerLimit(gameprofile) ? IChatBaseComponent.translatable("multiplayer.disconnect.server_full") : null;
            if (this.getTotalConnections() >= this.maxPlayers && !this.canBypassPlayerLimit(gameprofile)) { // Folia - region threading - we control connection state here now async, not player list size
                event.disallow(PlayerLoginEvent.Result.KICK_FULL, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(org.spigotmc.SpigotConfig.serverFullMessage)); // Spigot // Paper - Adventure
            }
        }

        this.cserver.getPluginManager().callEvent(event);
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            loginlistener.disconnect(io.papermc.paper.adventure.PaperAdventure.asVanilla(event.kickMessage())); // Paper - Adventure
            return null;
        }
        return entity;
    }

    // CraftBukkit start - added EntityPlayer
    public ServerPlayer getPlayerForLogin(GameProfile gameprofile, ClientInformation clientinformation, ServerPlayer player) {
        player.updateOptions(clientinformation);
        return player;
        // CraftBukkit end
    }

    public boolean disconnectAllPlayersWithProfile(GameProfile gameprofile, ServerPlayer player) { // CraftBukkit - added EntityPlayer
        /* CraftBukkit startMoved up
        UUID uuid = gameprofile.getId();
        Set<EntityPlayer> set = Sets.newIdentityHashSet();
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            EntityPlayer entityplayer = (EntityPlayer) iterator.next();

            if (entityplayer.getUUID().equals(uuid)) {
                set.add(entityplayer);
            }
        }

        EntityPlayer entityplayer1 = (EntityPlayer) this.playersByUUID.get(gameprofile.getId());

        if (entityplayer1 != null) {
            set.add(entityplayer1);
        }

        Iterator iterator1 = set.iterator();

        while (iterator1.hasNext()) {
            EntityPlayer entityplayer2 = (EntityPlayer) iterator1.next();

            entityplayer2.connection.disconnect(PlayerList.DUPLICATE_LOGIN_DISCONNECT_MESSAGE);
        }

        return !set.isEmpty();
        */
        return player == null;
        // CraftBukkit end
    }

    // CraftBukkit start
    public ServerPlayer respawn(ServerPlayer entityplayer, boolean flag, RespawnReason reason) {
        return this.respawn(entityplayer, this.server.getLevel(entityplayer.getRespawnDimension()), flag, null, true, reason);
    }

    public ServerPlayer respawn(ServerPlayer entityplayer, ServerLevel worldserver, boolean flag, Location location, boolean avoidSuffocation, RespawnReason reason) {
        // Paper start
        return respawn(entityplayer, worldserver, flag, location, avoidSuffocation, reason, new org.bukkit.event.player.PlayerRespawnEvent.RespawnFlag[0]);
    }

    public ServerPlayer respawn(ServerPlayer entityplayer, ServerLevel worldserver, boolean flag, Location location, boolean avoidSuffocation, RespawnReason reason, org.bukkit.event.player.PlayerRespawnEvent.RespawnFlag...respawnFlags) {
        // Paper end
        // Folia start - region threading
        if (true) {
            throw new UnsupportedOperationException("Must use teleportAsync while in region threading");
        }
        // Folia end - region threading
        entityplayer.stopRiding(); // CraftBukkit
        this.players.remove(entityplayer);
        this.playersByName.remove(entityplayer.getScoreboardName().toLowerCase(java.util.Locale.ROOT)); // Spigot
        entityplayer.serverLevel().removePlayerImmediately(entityplayer, Entity.RemovalReason.DISCARDED);
        BlockPos blockposition = entityplayer.getRespawnPosition();
        float f = entityplayer.getRespawnAngle();
        boolean flag1 = entityplayer.isRespawnForced();
        /* CraftBukkit start
        WorldServer worldserver = this.server.getLevel(entityplayer.getRespawnDimension());
        Optional optional;

        if (worldserver != null && blockposition != null) {
            optional = EntityHuman.findRespawnPositionAndUseSpawnBlock(worldserver, blockposition, f, flag1, flag);
        } else {
            optional = Optional.empty();
        }

        WorldServer worldserver1 = worldserver != null && optional.isPresent() ? worldserver : this.server.overworld();
        EntityPlayer entityplayer1 = new EntityPlayer(this.server, worldserver1, entityplayer.getGameProfile(), entityplayer.clientInformation());
        // */
        ServerPlayer entityplayer1 = entityplayer;
        org.bukkit.World fromWorld = entityplayer.getBukkitEntity().getWorld();
        entityplayer.wonGame = false;
        // CraftBukkit end

        entityplayer1.connection = entityplayer.connection;
        entityplayer1.restoreFrom(entityplayer, flag);
        entityplayer1.setId(entityplayer.getId());
        entityplayer1.setMainArm(entityplayer.getMainArm());
        Iterator iterator = entityplayer.getTags().iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();

            entityplayer1.addTag(s);
        }

        boolean flag2 = false;

        // Paper start
        boolean isBedSpawn = false;
        boolean isAnchorSpawn = false;
        boolean isRespawn = false;
        boolean isLocAltered = false; // Paper - Fix SPIGOT-5989
        // Paper end

        // CraftBukkit start - fire PlayerRespawnEvent
        if (location == null) {
            // boolean isBedSpawn = false; // Paper - moved up
            ServerLevel worldserver1 = this.server.getLevel(entityplayer.getRespawnDimension());
            if (worldserver1 != null) {
                Optional optional;

                if (blockposition != null) {
                    optional = net.minecraft.world.entity.player.Player.findRespawnPositionAndUseSpawnBlock(worldserver1, blockposition, f, flag1, true); // Paper - Fix SPIGOT-5989
                } else {
                    optional = Optional.empty();
                }

                if (optional.isPresent()) {
                    BlockState iblockdata = worldserver1.getBlockState(blockposition);
                    boolean flag3 = iblockdata.is(Blocks.RESPAWN_ANCHOR);
                    isAnchorSpawn = flag3; // Paper - Fix anchor respawn acting as a bed respawn from the end portal
                    Vec3 vec3d = (Vec3) optional.get();
                    float f1;

                    if (!iblockdata.is(BlockTags.BEDS) && !flag3) {
                        f1 = f;
                    } else {
                        Vec3 vec3d1 = Vec3.atBottomCenterOf(blockposition).subtract(vec3d).normalize();

                        f1 = (float) Mth.wrapDegrees(Mth.atan2(vec3d1.z, vec3d1.x) * 57.2957763671875D - 90.0D);
                    }

                    // entityplayer1.setRespawnPosition(worldserver1.dimension(), blockposition, f, flag1, false); // CraftBukkit - not required, just copies old location into reused entity
                    flag2 = !flag && flag3;
                    isBedSpawn = true;
                    location = CraftLocation.toBukkit(vec3d, worldserver1.getWorld(), f1, 0.0F);
                } else if (blockposition != null) {
                    entityplayer1.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
                    entityplayer1.setRespawnPosition(null, null, 0f, false, false, com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN); // CraftBukkit - SPIGOT-5988: Clear respawn location when obstructed // Paper - PlayerSetSpawnEvent
                }
            }

            if (location == null) {
                worldserver1 = this.server.getLevel(Level.OVERWORLD);
                blockposition = entityplayer1.getSpawnPoint(worldserver1);
                location = CraftLocation.toBukkit(blockposition, worldserver1.getWorld(), worldserver1.levelData.getSpawnAngle(), 0.0F).add(0.5F, 0.1F, 0.5F); // Paper - use world spawn angle
            }

            Player respawnPlayer = entityplayer1.getBukkitEntity();
            PlayerRespawnEvent respawnEvent = new PlayerRespawnEvent(respawnPlayer, location, isBedSpawn && !isAnchorSpawn, isAnchorSpawn, reason, com.google.common.collect.ImmutableSet.<org.bukkit.event.player.PlayerRespawnEvent.RespawnFlag>builder().add(respawnFlags)); // Paper - Fix anchor respawn acting as a bed respawn from the end portal
            this.cserver.getPluginManager().callEvent(respawnEvent);
            // Spigot Start
            if (entityplayer.connection.isDisconnected()) {
                return entityplayer;
            }
            // Spigot End

            // Paper start - Fix SPIGOT-5989
            if (!location.equals(respawnEvent.getRespawnLocation()) ) {
                location = respawnEvent.getRespawnLocation();
                isLocAltered = true;
            }
            // Paper end
            if (!flag) entityplayer.reset(); // SPIGOT-4785
            isRespawn = true; // Paper
        } else {
            location.setWorld(worldserver.getWorld());
        }
        ServerLevel worldserver1 = ((CraftWorld) location.getWorld()).getHandle();
        entityplayer1.spawnIn(worldserver1);
        entityplayer1.unsetRemoved();
        entityplayer1.setShiftKeyDown(false);
        entityplayer1.forceSetPositionRotation(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());

        worldserver1.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.POST_TELEPORT, new net.minecraft.world.level.ChunkPos(location.getBlockX() >> 4, location.getBlockZ() >> 4), 1, entityplayer.getId()); // Paper
        while (avoidSuffocation && !worldserver1.noCollision(entityplayer1, entityplayer1.getBoundingBox(), true) && entityplayer1.getY() < (double) worldserver1.getMaxBuildHeight()) { // Paper - make sure this loads chunks, we default to NOT loading now
            // CraftBukkit end
            entityplayer1.setPos(entityplayer1.getX(), entityplayer1.getY() + 1.0D, entityplayer1.getZ());
        }

        int i = flag ? 1 : 0;
        ServerLevel worldserver2 = entityplayer1.serverLevel();
        LevelData worlddata = worldserver2.getLevelData();

        entityplayer1.connection.send(new ClientboundRespawnPacket(entityplayer1.createCommonSpawnInfo(worldserver2), (byte) i));
        entityplayer1.connection.send(new ClientboundSetChunkCacheRadiusPacket(worldserver1.getWorld().getSendViewDistance())); // Spigot // Paper - replace old player chunk management
        entityplayer1.connection.send(new ClientboundSetSimulationDistancePacket(worldserver1.getWorld().getSimulationDistance())); // Spigot // Paper - replace old player chunk management
        entityplayer1.connection.teleport(CraftLocation.toBukkit(entityplayer1.position(), worldserver2.getWorld(), entityplayer1.getYRot(), entityplayer1.getXRot())); // CraftBukkit
        entityplayer1.connection.send(new ClientboundSetDefaultSpawnPositionPacket(worldserver1.getSharedSpawnPos(), worldserver1.getSharedSpawnAngle()));
        entityplayer1.connection.send(new ClientboundChangeDifficultyPacket(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
        entityplayer1.connection.send(new ClientboundSetExperiencePacket(entityplayer1.experienceProgress, entityplayer1.totalExperience, entityplayer1.experienceLevel));
        this.sendLevelInfo(entityplayer1, worldserver1);
        this.sendPlayerPermissionLevel(entityplayer1);
        if (!entityplayer.connection.isDisconnected()) {
            worldserver1.addRespawnedPlayer(entityplayer1);
            this.players.add(entityplayer1);
            this.playersByName.put(entityplayer1.getScoreboardName().toLowerCase(java.util.Locale.ROOT), entityplayer1); // Spigot
            this.playersByUUID.put(entityplayer1.getUUID(), entityplayer1);
        }
        // entityplayer1.initInventoryMenu();
        entityplayer1.setHealth(entityplayer1.getHealth());
        // Paper start - Fix SPIGOT-5989
        if (flag2 && !isLocAltered) {
            if (!flag1) {
                BlockState data = worldserver1.getBlockState(blockposition);
                worldserver1.setBlock(blockposition, data.setValue(net.minecraft.world.level.block.RespawnAnchorBlock.CHARGE, data.getValue(net.minecraft.world.level.block.RespawnAnchorBlock.CHARGE) - 1), 3);
            }
            entityplayer1.connection.send(new ClientboundSoundPacket(SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.BLOCKS, (double) location.getX(), (double) location.getY(), (double) location.getZ(), 1.0F, 1.0F, worldserver1.getRandom().nextLong()));
        // Paper end
        }
        // Added from changeDimension
        this.sendAllPlayerInfo(entityplayer); // Update health, etc...
        entityplayer.onUpdateAbilities();
        for (MobEffectInstance mobEffect : entityplayer.getActiveEffects()) {
            entityplayer.connection.send(new ClientboundUpdateMobEffectPacket(entityplayer.getId(), mobEffect));
        }

        // Fire advancement trigger
        entityplayer.triggerDimensionChangeTriggers(((CraftWorld) fromWorld).getHandle());

        // Don't fire on respawn
        if (fromWorld != location.getWorld()) {
            PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(entityplayer.getBukkitEntity(), fromWorld);
            this.server.server.getPluginManager().callEvent(event);
        }

        // Save player file again if they were disconnected
        if (entityplayer.connection.isDisconnected()) {
            this.save(entityplayer);
        }

        // Paper start
        if (isRespawn) {
            cserver.getPluginManager().callEvent(new com.destroystokyo.paper.event.player.PlayerPostRespawnEvent(entityplayer.getBukkitEntity(), location, isBedSpawn));
        }
        // Paper end

        // CraftBukkit end
        return entityplayer1;
    }

    public void sendPlayerPermissionLevel(ServerPlayer player) {
        GameProfile gameprofile = player.getGameProfile();
        int i = this.server.getProfilePermissions(gameprofile);

        this.sendPlayerPermissionLevel(player, i);
    }

    public void tick() {
        if (++this.sendAllPlayerInfoIn > 600) {
            // CraftBukkit start
            ServerPlayer[] players = this.players.toArray(new ServerPlayer[0]); // Folia - region threading
            for (final ServerPlayer target : players) { // Folia - region threading

                target.connection.send(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY), java.util.Arrays.stream(players).filter(new Predicate<ServerPlayer>() { // Folia - region threading
                    @Override
                    public boolean test(ServerPlayer input) {
                        return target.getBukkitEntity().canSee(input.getBukkitEntity());
                    }
                }).collect(Collectors.toList())));
            }
            // CraftBukkit end
            this.sendAllPlayerInfoIn = 0;
        }

    }

    public void broadcastAll(Packet<?> packet) {
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            entityplayer.connection.send(packet);
        }

    }

    // CraftBukkit start - add a world/entity limited version
    public void broadcastAll(Packet packet, net.minecraft.world.entity.player.Player entityhuman) {
        for (ServerPlayer entityplayer : this.players) { // Folia - region threading
            if (entityhuman != null && !entityplayer.getBukkitEntity().canSee(entityhuman.getBukkitEntity())) {
                continue;
            }
            entityplayer.connection.send(packet); // Folia - region threading
        }
    }

    public void broadcastAll(Packet packet, Level world) {
        for (net.minecraft.world.entity.player.Player player : world.players()) { // Folia - region threading
            ((ServerPlayer) player).connection.send(packet); // Folia - region threading
        }

    }
    // CraftBukkit end

    public void broadcastAll(Packet<?> packet, ResourceKey<Level> dimension) {
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            if (entityplayer.level().dimension() == dimension) {
                entityplayer.connection.send(packet);
            }
        }

    }

    public void broadcastSystemToTeam(net.minecraft.world.entity.player.Player source, Component message) {
        Team scoreboardteambase = source.getTeam();

        if (scoreboardteambase != null) {
            Collection<String> collection = scoreboardteambase.getPlayers();
            Iterator iterator = collection.iterator();

            while (iterator.hasNext()) {
                String s = (String) iterator.next();
                ServerPlayer entityplayer = this.getPlayerByName(s);

                if (entityplayer != null && entityplayer != source) {
                    entityplayer.sendSystemMessage(message);
                }
            }

        }
    }

    public void broadcastSystemToAllExceptTeam(net.minecraft.world.entity.player.Player source, Component message) {
        Team scoreboardteambase = source.getTeam();

        if (scoreboardteambase == null) {
            this.broadcastSystemMessage(message, false);
        } else {
            for (ServerPlayer entityplayer : this.players) { // Folia - region threading

                if (entityplayer.getTeam() != scoreboardteambase) {
                    entityplayer.sendSystemMessage(message);
                }
            }

        }
    }

    public String[] getPlayerNamesArray() {
        List<ServerPlayer> players = new java.util.ArrayList<>(this.players); // Folia start - region threading
        String[] astring = new String[players.size()];

        for (int i = 0; i < players.size(); ++i) {
            astring[i] = ((ServerPlayer) players.get(i)).getGameProfile().getName();
            // Folia end - region threading
        }

        return astring;
    }

    public UserBanList getBans() {
        return this.bans;
    }

    public IpBanList getIpBans() {
        return this.ipBans;
    }

    public void op(GameProfile profile) {
        this.ops.add(new ServerOpListEntry(profile, this.server.getOperatorUserPermissionLevel(), this.ops.canBypassPlayerLimit(profile)));
        ServerPlayer entityplayer = this.getPlayer(profile.getId());

        if (entityplayer != null) {
            entityplayer.getBukkitEntity().taskScheduler.schedule((nmsEntity) -> { // Folia - region threading
            this.sendPlayerPermissionLevel(entityplayer);
            }, null, 1L); // Folia - region threading
        }

    }

    public void deop(GameProfile profile) {
        this.ops.remove(profile); // CraftBukkit - decompile error
        ServerPlayer entityplayer = this.getPlayer(profile.getId());

        if (entityplayer != null) {
            entityplayer.getBukkitEntity().taskScheduler.schedule((nmsEntity) -> { // Folia - region threading
            this.sendPlayerPermissionLevel(entityplayer);
            }, null, 1L); // Folia - region threading

        }

    }

    private void sendPlayerPermissionLevel(ServerPlayer player, int permissionLevel) {
        // Paper start - add recalculatePermissions parameter
        this.sendPlayerPermissionLevel(player, permissionLevel, true);
    }
    public void sendPlayerPermissionLevel(ServerPlayer player, int permissionLevel, boolean recalculatePermissions) {
        // Paper end
        if (player.connection != null) {
            byte b0;

            if (permissionLevel <= 0) {
                b0 = 24;
            } else if (permissionLevel >= 4) {
                b0 = 28;
            } else {
                b0 = (byte) (24 + permissionLevel);
            }

            player.connection.send(new ClientboundEntityEventPacket(player, b0));
        }

        if (recalculatePermissions) { // Paper
        player.getBukkitEntity().recalculatePermissions(); // CraftBukkit
        this.server.getCommands().sendCommands(player);
        } // Paper
    }

    public boolean isWhiteListed(GameProfile profile) {
        // Paper start
        return isWhiteListed(profile, null);
    }
    public boolean isWhiteListed(GameProfile gameprofile, org.bukkit.event.player.PlayerLoginEvent loginEvent) {
        boolean isOp = this.ops.contains(gameprofile);
        boolean isWhitelisted = !this.doWhiteList || isOp || this.whitelist.contains(gameprofile);
        final com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent event;
        event = new com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent(io.papermc.paper.util.MCUtil.toBukkit(gameprofile), this.doWhiteList, isWhitelisted, isOp, org.spigotmc.SpigotConfig.whitelistMessage);
        event.callEvent();
        if (!event.isWhitelisted()) {
            if (loginEvent != null) {
                loginEvent.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(event.getKickMessage() == null ? org.spigotmc.SpigotConfig.whitelistMessage : event.getKickMessage()));
            }
            return false;
        }
        return true;
        // Paper end
    }

    public boolean isOp(GameProfile profile) {
        return this.ops.contains(profile) || this.server.isSingleplayerOwner(profile) && this.server.getWorldData().getAllowCommands() || this.allowCheatsForAllPlayers;
    }

    @Nullable
    public ServerPlayer getPlayerByName(String name) {
        return this.playersByName.get(name.toLowerCase(java.util.Locale.ROOT)); // Spigot
    }

    public void broadcast(@Nullable net.minecraft.world.entity.player.Player player, double x, double y, double z, double distance, ResourceKey<Level> worldKey, Packet<?> packet) {
        for (ServerPlayer entityplayer : this.players) { // Folia - region threading

            // CraftBukkit start - Test if player receiving packet can see the source of the packet
            if (player != null && !entityplayer.getBukkitEntity().canSee(player.getBukkitEntity())) {
               continue;
            }
            // CraftBukkit end

            if (entityplayer != player && entityplayer.level().dimension() == worldKey) {
                double d4 = x - entityplayer.getX();
                double d5 = y - entityplayer.getY();
                double d6 = z - entityplayer.getZ();

                if (d4 * d4 + d5 * d5 + d6 * d6 < distance * distance) {
                    entityplayer.connection.send(packet);
                }
            }
        }

    }

    public void saveAll() {
        // Paper start - incremental player saving
        this.saveAll(-1);
    }

    public void saveAll(int interval) {
        io.papermc.paper.util.MCUtil.ensureMain("Save Players" , () -> { // Paper - Ensure main
        final ca.spottedleaf.leafprofiler.RegionizedProfiler.Handle profiler = io.papermc.paper.threadedregions.TickRegionScheduler.getProfiler(); // Folia - profiler
        MinecraftTimings.savePlayers.startTiming(); // Paper
        int numSaved = 0;
        long now = System.nanoTime(); // Folia - region threading
        int max = io.papermc.paper.configuration.GlobalConfiguration.get().playerAutoSave.maxPerTick(); // Folia - region threading
        long timeInterval = (long)interval * io.papermc.paper.threadedregions.TickRegionScheduler.TIME_BETWEEN_TICKS; // Folia - region threading
        for (ServerPlayer entityplayer : this.players) { // Folia start - region threading
            if (!io.papermc.paper.util.TickThread.isTickThreadFor(entityplayer)) {
                continue;
            }
            // Folia end - region threading
            if (interval == -1 || now - entityplayer.lastSave >= timeInterval) { // Folia - region threading
                profiler.startTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.PLAYER_SAVE); try { // Folia - profiler
                this.save(entityplayer);
                } finally { profiler.stopTimer(ca.spottedleaf.leafprofiler.LProfilerRegistry.PLAYER_SAVE); } // Folia - profiler
                // Folia start - region threading
                if (interval != -1 && max != -1 && ++numSaved >= max) {
                    break;
                }
                // Folia end - region threading
            }
            // Paper end
        }
        MinecraftTimings.savePlayers.stopTiming(); // Paper
        return null; }); // Paper - ensure main
    }

    public UserWhiteList getWhiteList() {
        return this.whitelist;
    }

    public String[] getWhiteListNames() {
        return this.whitelist.getUserList();
    }

    public ServerOpList getOps() {
        return this.ops;
    }

    public String[] getOpNames() {
        return this.ops.getUserList();
    }

    public void reloadWhiteList() {}

    public void sendLevelInfo(ServerPlayer player, ServerLevel world) {
        WorldBorder worldborder = player.level().getWorldBorder(); // CraftBukkit

        player.connection.send(new ClientboundInitializeBorderPacket(worldborder));
        player.connection.send(new ClientboundSetTimePacket(world.getGameTime(), world.getDayTime(), world.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)));
        player.connection.send(new ClientboundSetDefaultSpawnPositionPacket(world.getSharedSpawnPos(), world.getSharedSpawnAngle()));
        if (world.isRaining()) {
            // CraftBukkit start - handle player weather
            // entityplayer.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.START_RAINING, 0.0F));
            // entityplayer.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.RAIN_LEVEL_CHANGE, worldserver.getRainLevel(1.0F)));
            // entityplayer.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.THUNDER_LEVEL_CHANGE, worldserver.getThunderLevel(1.0F)));
            player.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL, false);
            player.updateWeather(-world.rainLevel, world.rainLevel, -world.thunderLevel, world.thunderLevel);
            // CraftBukkit end
        }

    }

    public void sendAllPlayerInfo(ServerPlayer player) {
        player.inventoryMenu.sendAllDataToRemote();
        // entityplayer.resetSentInfo();
        player.getBukkitEntity().updateScaledHealth(); // CraftBukkit - Update scaled health on respawn and worldchange
        player.getEntityData().refresh(player); // CraftBukkkit - SPIGOT-7218: sync metadata
        player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
        // CraftBukkit start - from GameRules
        int i = player.level().getGameRules().getBoolean(GameRules.RULE_REDUCEDDEBUGINFO) ? 22 : 23;
        player.connection.send(new ClientboundEntityEventPacket(player, (byte) i));
        float immediateRespawn = player.level().getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN) ? 1.0F: 0.0F;
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.IMMEDIATE_RESPAWN, immediateRespawn));
        // CraftBukkit end
    }

    public int getPlayerCount() {
        return this.players.size();
    }

    public int getMaxPlayers() {
        return this.maxPlayers;
    }

    public boolean isUsingWhitelist() {
        return this.doWhiteList;
    }

    public void setUsingWhiteList(boolean whitelistEnabled) {
        new com.destroystokyo.paper.event.server.WhitelistToggleEvent(whitelistEnabled).callEvent();
        this.doWhiteList = whitelistEnabled;
    }

    public List<ServerPlayer> getPlayersWithAddress(String ip) {
        List<ServerPlayer> list = Lists.newArrayList();
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            if (entityplayer.getIpAddress().equals(ip)) {
                list.add(entityplayer);
            }
        }

        return list;
    }

    public int getViewDistance() {
        return this.viewDistance;
    }

    public int getSimulationDistance() {
        return this.simulationDistance;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    @Nullable
    public CompoundTag getSingleplayerData() {
        return null;
    }

    public void setAllowCheatsForAllPlayers(boolean cheatsAllowed) {
        this.allowCheatsForAllPlayers = cheatsAllowed;
    }

    public void removeAll() {
        // Paper start - Extract method to allow for restarting flag
        this.removeAll(false);
    }

    public void removeAll(boolean isRestarting) {
        // Folia start - region threading
        // just send disconnect packet, don't modify state
        for (ServerPlayer player : this.players) {
            final Component shutdownMessage = io.papermc.paper.adventure.PaperAdventure.asVanilla(this.server.server.shutdownMessage()); // Paper - Adventure
            // CraftBukkit end

            player.connection.send(new net.minecraft.network.protocol.common.ClientboundDisconnectPacket(shutdownMessage), net.minecraft.network.PacketSendListener.thenRun(() -> {
                player.connection.connection.disconnect(shutdownMessage);
            }));
        }
        if (true) {
            return;
        }
        // Folia end - region threading
        // Paper end
        // CraftBukkit start - disconnect safely
        for (ServerPlayer player : this.players) {
            if (isRestarting) player.connection.disconnect(org.spigotmc.SpigotConfig.restartMessage, org.bukkit.event.player.PlayerKickEvent.Cause.UNKNOWN); else // Paper - kick event cause (cause is never used here)
            player.connection.disconnect(this.server.server.shutdownMessage(), org.bukkit.event.player.PlayerKickEvent.Cause.UNKNOWN); // CraftBukkit - add custom shutdown message // Paper - Adventure & KickEventCause (cause is never used here)
        }
        // CraftBukkit end

        // Paper start - Remove collideRule team if it exists
        if (false && this.collideRuleTeamName != null) { // Folia - region threading
            final Scoreboard scoreboard = this.getServer().getLevel(Level.OVERWORLD).getScoreboard();
            final PlayerTeam team = scoreboard.getPlayersTeam(this.collideRuleTeamName);
            if (team != null) scoreboard.removePlayerTeam(team);
        }
        // Paper end
    }

    // CraftBukkit start
    public void broadcastMessage(Component[] iChatBaseComponents) {
        for (Component component : iChatBaseComponents) {
            this.broadcastSystemMessage(component, false);
        }
    }
    // CraftBukkit end

    public void broadcastSystemMessage(Component message, boolean overlay) {
        this.broadcastSystemMessage(message, (entityplayer) -> {
            return message;
        }, overlay);
    }

    public void broadcastSystemMessage(Component message, Function<ServerPlayer, Component> playerMessageFactory, boolean overlay) {
        this.server.sendSystemMessage(message);
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();
            Component ichatbasecomponent1 = (Component) playerMessageFactory.apply(entityplayer);

            if (ichatbasecomponent1 != null) {
                entityplayer.sendSystemMessage(ichatbasecomponent1, overlay);
            }
        }

    }

    public void broadcastChatMessage(PlayerChatMessage message, CommandSourceStack source, ChatType.Bound params) {
        Objects.requireNonNull(source);
        this.broadcastChatMessage(message, source::shouldFilterMessageTo, source.getPlayer(), params);
    }

    public void broadcastChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound params) {
        // Paper start
        this.broadcastChatMessage(message, sender, params, null);
    }
    public void broadcastChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound params, @Nullable Function<net.kyori.adventure.audience.Audience, Component> unsignedFunction) {
        // Paper end
        Objects.requireNonNull(sender);
        this.broadcastChatMessage(message, sender::shouldFilterMessageTo, sender, params, unsignedFunction); // Paper
    }

    private void broadcastChatMessage(PlayerChatMessage message, Predicate<ServerPlayer> shouldSendFiltered, @Nullable ServerPlayer sender, ChatType.Bound params) {
        // Paper start
        this.broadcastChatMessage(message, shouldSendFiltered, sender, params, null);
    }
    public void broadcastChatMessage(PlayerChatMessage message, Predicate<ServerPlayer> shouldSendFiltered, @Nullable ServerPlayer sender, ChatType.Bound params, @Nullable Function<net.kyori.adventure.audience.Audience, Component> unsignedFunction) {
        // Paper end
        boolean flag = this.verifyChatTrusted(message);

        this.server.logChatMessage((unsignedFunction == null ? message.decoratedContent() : unsignedFunction.apply(this.server.console)), params, flag ? null : "Not Secure"); // Paper
        OutgoingChatMessage outgoingchatmessage = OutgoingChatMessage.create(message);
        boolean flag1 = false;

        boolean flag2;
        Packet<?> disguised = sender != null && unsignedFunction == null ? new net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket(outgoingchatmessage.content(), params.toNetwork(sender.level().registryAccess())) : null; // Paper - don't send player chat packets from vanished players

        for (Iterator iterator = this.players.iterator(); iterator.hasNext(); flag1 |= flag2 && message.isFullyFiltered()) {
            ServerPlayer entityplayer1 = (ServerPlayer) iterator.next();

            flag2 = shouldSendFiltered.test(entityplayer1);
            // Paper start - don't send player chat packets from vanished players
            if (sender != null && !entityplayer1.getBukkitEntity().canSee(sender.getBukkitEntity())) {
                entityplayer1.connection.send(unsignedFunction != null
                    ? new net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket(unsignedFunction.apply(entityplayer1.getBukkitEntity()), params.toNetwork(sender.level().registryAccess()))
                    : disguised);
                continue;
            }
            // Paper end
            entityplayer1.sendChatMessage(outgoingchatmessage, flag2, params, unsignedFunction == null ? null : unsignedFunction.apply(entityplayer1.getBukkitEntity())); // Paper
        }

        if (flag1 && sender != null) {
            sender.sendSystemMessage(PlayerList.CHAT_FILTERED_FULL);
        }

    }

    public boolean verifyChatTrusted(PlayerChatMessage message) { // Paper - private -> public
        return message.hasSignature() && !message.hasExpiredServer(Instant.now());
    }

    // CraftBukkit start
    public ServerStatsCounter getPlayerStats(ServerPlayer entityhuman) {
        ServerStatsCounter serverstatisticmanager = entityhuman.getStats();
        return serverstatisticmanager == null ? this.getPlayerStats(entityhuman.getUUID(), entityhuman.getGameProfile().getName()) : serverstatisticmanager; // Paper - use username and not display name
    }

    public ServerStatsCounter getPlayerStats(UUID uuid, String displayName) {
        ServerPlayer entityhuman = this.getPlayer(uuid);
        ServerStatsCounter serverstatisticmanager = entityhuman == null ? null : (ServerStatsCounter) entityhuman.getStats();
        // CraftBukkit end

        if (serverstatisticmanager == null) {
            File file = this.server.getWorldPath(LevelResource.PLAYER_STATS_DIR).toFile();
            File file1 = new File(file, uuid + ".json");

            if (!file1.exists()) {
                File file2 = new File(file, displayName + ".json"); // CraftBukkit
                Path path = file2.toPath();

                if (FileUtil.isPathNormalized(path) && FileUtil.isPathPortable(path) && path.startsWith(file.getPath()) && file2.isFile()) {
                    file2.renameTo(file1);
                }
            }

            serverstatisticmanager = new ServerStatsCounter(this.server, file1);
            // this.stats.put(uuid, serverstatisticmanager); // CraftBukkit
        }

        return serverstatisticmanager;
    }

    public PlayerAdvancements getPlayerAdvancements(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerAdvancements advancementdataplayer = (PlayerAdvancements) player.getAdvancements(); // CraftBukkit

        if (advancementdataplayer == null) {
            Path path = this.server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(uuid + ".json");

            advancementdataplayer = new PlayerAdvancements(this.server.getFixerUpper(), this, this.server.getAdvancements(), path, player);
            // this.advancements.put(uuid, advancementdataplayer); // CraftBukkit
        }

        advancementdataplayer.setPlayer(player);
        return advancementdataplayer;
    }

    public void setViewDistance(int viewDistance) {
        this.viewDistance = viewDistance;
        //this.broadcastAll(new ClientboundSetChunkCacheRadiusPacket(viewDistance)); // Paper - move into setViewDistance
        Iterator iterator = this.server.getAllLevels().iterator();

        while (iterator.hasNext()) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            if (worldserver != null) {
                worldserver.getChunkSource().setViewDistance(viewDistance);
            }
        }

    }

    public void setSimulationDistance(int simulationDistance) {
        this.simulationDistance = simulationDistance;
        //this.broadcastAll(new ClientboundSetSimulationDistancePacket(simulationDistance)); // Paper - handled by playerchunkloader
        Iterator iterator = this.server.getAllLevels().iterator();

        while (iterator.hasNext()) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            if (worldserver != null) {
                worldserver.getChunkSource().setSimulationDistance(simulationDistance);
            }
        }

    }

    public List<ServerPlayer> getPlayers() {
        return this.players;
    }

    @Nullable
    public ServerPlayer getPlayer(UUID uuid) {
        return (ServerPlayer) this.playersByUUID.get(uuid);
    }

    public boolean canBypassPlayerLimit(GameProfile profile) {
        return false;
    }

    public void reloadResources() {
        // Paper start - split this method up into separate methods
        this.reloadAdvancementData();
        this.reloadTagData();
        this.reloadRecipeData();
    }
    public void reloadAdvancementData() {
        // Paper end
        // CraftBukkit start
        /*Iterator iterator = this.advancements.values().iterator();

        while (iterator.hasNext()) {
            AdvancementDataPlayer advancementdataplayer = (AdvancementDataPlayer) iterator.next();

            advancementdataplayer.reload(this.server.getAdvancements());
        }*/

        for (ServerPlayer player : this.players) {
            player.getAdvancements().reload(this.server.getAdvancements());
            player.getAdvancements().flushDirty(player); // CraftBukkit - trigger immediate flush of advancements
        }
        // CraftBukkit end

        // Paper start
    }
    public void reloadTagData() {
        // Paper end
        this.broadcastAll(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.registries)));
        // Paper start
    }
    public void reloadRecipeData() {
        // Paper end
        ClientboundUpdateRecipesPacket packetplayoutrecipeupdate = new ClientboundUpdateRecipesPacket(this.server.getRecipeManager().getRecipes());
        Iterator iterator1 = this.players.iterator();

        while (iterator1.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator1.next();

            entityplayer.connection.send(packetplayoutrecipeupdate);
            entityplayer.getRecipeBook().sendInitialRecipeBook(entityplayer);
        }

    }

    public boolean isAllowCheatsForAllPlayers() {
        return this.allowCheatsForAllPlayers;
    }
}
