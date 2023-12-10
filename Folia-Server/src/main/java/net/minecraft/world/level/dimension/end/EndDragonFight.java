package net.minecraft.world.level.dimension.end;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.EndFeatures;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockPredicate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public class EndDragonFight {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TICKS_BEFORE_DRAGON_RESPAWN = 1200;
    private static final int TIME_BETWEEN_CRYSTAL_SCANS = 100;
    public static final int TIME_BETWEEN_PLAYER_SCANS = 20;
    private static final int ARENA_SIZE_CHUNKS = 8;
    public static final int ARENA_TICKET_LEVEL = 9;
    public static final int GATEWAY_COUNT = 20;
    private static final int GATEWAY_DISTANCE = 96;
    public static final int DRAGON_SPAWN_Y = 128;
    private final Predicate<Entity> validPlayer;
    private static final Component DEFAULT_BOSS_EVENT_NAME = Component.translatable("entity.minecraft.ender_dragon"); // Paper
    public final ServerBossEvent dragonEvent;
    public final ServerLevel level;
    public final BlockPos origin; // Folia - region threading
    public final ObjectArrayList<Integer> gateways;
    private final BlockPattern exitPortalPattern;
    private int ticksSinceDragonSeen;
    private int crystalsAlive;
    private int ticksSinceCrystalsScanned;
    private int ticksSinceLastPlayerScan;
    private boolean dragonKilled;
    private boolean previouslyKilled;
    private boolean skipArenaLoadedCheck;
    @Nullable
    public UUID dragonUUID;
    private boolean needsStateScanning;
    @Nullable
    public BlockPos portalLocation;
    @Nullable
    public DragonRespawnAnimation respawnStage;
    private int respawnTime;
    @Nullable
    public List<EndCrystal> respawnCrystals;

    public EndDragonFight(ServerLevel world, long gatewaysSeed, EndDragonFight.Data data) {
        this(world, gatewaysSeed, data, BlockPos.ZERO);
    }

    public EndDragonFight(ServerLevel world, long gatewaysSeed, EndDragonFight.Data data, BlockPos origin) {
        this.dragonEvent = (ServerBossEvent) (new ServerBossEvent(DEFAULT_BOSS_EVENT_NAME, BossEvent.BossBarColor.PINK, BossEvent.BossBarOverlay.PROGRESS)).setPlayBossMusic(true).setCreateWorldFog(true); // Paper
        this.gateways = new ObjectArrayList();
        this.ticksSinceLastPlayerScan = 21;
        this.skipArenaLoadedCheck = false;
        this.needsStateScanning = true;
        // Paper start
        this.needsStateScanning = world.paperConfig().entities.spawning.scanForLegacyEnderDragon;
        if (!this.needsStateScanning) this.dragonKilled = true;
        // Paper end
        this.level = world;
        this.origin = origin;
        this.validPlayer = EntitySelector.ENTITY_STILL_ALIVE.and(EntitySelector.withinDistance((double) origin.getX(), (double) (128 + origin.getY()), (double) origin.getZ(), 192.0D));
        this.needsStateScanning = data.needsStateScanning;
        this.dragonUUID = (UUID) data.dragonUUID.orElse(null); // CraftBukkit - decompile error
        this.dragonKilled = data.dragonKilled;
        this.previouslyKilled = data.previouslyKilled;
        if (data.isRespawning) {
            this.respawnStage = DragonRespawnAnimation.START;
        }

        this.portalLocation = (BlockPos) data.exitPortalLocation.orElse(null); // CraftBukkit - decompile error
        this.gateways.addAll((Collection) data.gateways.orElseGet(() -> {
            ObjectArrayList<Integer> objectarraylist = new ObjectArrayList(ContiguousSet.create(Range.closedOpen(0, 20), DiscreteDomain.integers()));

            Util.shuffle(objectarraylist, RandomSource.create(gatewaysSeed));
            return objectarraylist;
        }));
        this.exitPortalPattern = BlockPatternBuilder.start().aisle("       ", "       ", "       ", "   #   ", "       ", "       ", "       ").aisle("       ", "       ", "       ", "   #   ", "       ", "       ", "       ").aisle("       ", "       ", "       ", "   #   ", "       ", "       ", "       ").aisle("  ###  ", " #   # ", "#     #", "#  #  #", "#     #", " #   # ", "  ###  ").aisle("       ", "  ###  ", " ##### ", " ##### ", " ##### ", "  ###  ", "       ").where('#', BlockInWorld.hasState(BlockPredicate.forBlock(Blocks.BEDROCK))).build();
    }

    /** @deprecated */
    @Deprecated
    @VisibleForTesting
    public void skipArenaLoadedCheck() {
        this.skipArenaLoadedCheck = true;
    }

    public EndDragonFight.Data saveData() {
        return new EndDragonFight.Data(this.needsStateScanning, this.dragonKilled, this.previouslyKilled, false, Optional.ofNullable(this.dragonUUID), Optional.ofNullable(this.portalLocation), Optional.of(this.gateways));
    }

    public void tick() {
        this.dragonEvent.setVisible(!this.dragonKilled);
        if (++this.ticksSinceLastPlayerScan >= 20) {
            this.updatePlayers();
            this.ticksSinceLastPlayerScan = 0;
        }

        if (!this.dragonEvent.getPlayers().isEmpty()) {
            this.level.getChunkSource().addRegionTicket(TicketType.DRAGON, new ChunkPos(0, 0), 9, Unit.INSTANCE);
            boolean flag = this.isArenaLoaded(); if (!flag) { return; } // Folia - region threading - don't tick if we don't own the entire region

            if (this.needsStateScanning && flag) {
                this.scanState();
                this.needsStateScanning = false;
            }

            if (this.respawnStage != null) {
                if (this.respawnCrystals == null && flag) {
                    this.respawnStage = null;
                    this.tryRespawn();
                }

                this.respawnStage.tick(this.level, this, this.respawnCrystals, this.respawnTime++, this.portalLocation);
            }

            if (!this.dragonKilled) {
                if ((this.dragonUUID == null || ++this.ticksSinceDragonSeen >= 1200) && flag) {
                    this.findOrCreateDragon();
                    this.ticksSinceDragonSeen = 0;
                }

                if (++this.ticksSinceCrystalsScanned >= 100 && flag) {
                    this.updateCrystalCount();
                    this.ticksSinceCrystalsScanned = 0;
                }
            }
        } else {
            this.level.getChunkSource().removeRegionTicket(TicketType.DRAGON, new ChunkPos(0, 0), 9, Unit.INSTANCE);
        }

    }

    private void scanState() {
        EndDragonFight.LOGGER.info("Scanning for legacy world dragon fight...");
        boolean flag = this.hasActiveExitPortal();

        if (flag) {
            EndDragonFight.LOGGER.info("Found that the dragon has been killed in this world already.");
            this.previouslyKilled = true;
        } else {
            EndDragonFight.LOGGER.info("Found that the dragon has not yet been killed in this world.");
            this.previouslyKilled = false;
            if (this.findExitPortal() == null) {
                this.spawnExitPortal(false);
            }
        }

        List<? extends EnderDragon> list = this.level.getDragons();
        // Folia start - region threading
        // we do not want to deal with any dragons NOT nearby
        list.removeIf((dragon) -> {
            return !io.papermc.paper.util.TickThread.isTickThreadFor(dragon);
        });
        // Folia end - region threading

        if (list.isEmpty()) {
            this.dragonKilled = true;
        } else {
            EnderDragon entityenderdragon = (EnderDragon) list.get(0);

            this.dragonUUID = entityenderdragon.getUUID();
            EndDragonFight.LOGGER.info("Found that there's a dragon still alive ({})", entityenderdragon);
            this.dragonKilled = false;
            if (!flag && this.level.paperConfig().entities.behavior.shouldRemoveDragon) {
                EndDragonFight.LOGGER.info("But we didn't have a portal, let's remove it.");
                entityenderdragon.discard();
                this.dragonUUID = null;
            }
        }

        if (!this.previouslyKilled && this.dragonKilled) {
            this.dragonKilled = false;
        }

    }

    private void findOrCreateDragon() {
        List<? extends EnderDragon> list = this.level.getDragons();

        if (list.isEmpty()) {
            EndDragonFight.LOGGER.debug("Haven't seen the dragon, respawning it");
            this.createNewDragon();
        } else {
            EndDragonFight.LOGGER.debug("Haven't seen our dragon, but found another one to use.");
            this.dragonUUID = ((EnderDragon) list.get(0)).getUUID();
        }

    }

    public void setRespawnStage(DragonRespawnAnimation spawnState) {
        if (this.respawnStage == null) {
            throw new IllegalStateException("Dragon respawn isn't in progress, can't skip ahead in the animation.");
        } else {
            this.respawnTime = 0;
            if (spawnState == DragonRespawnAnimation.END) {
                this.respawnStage = null;
                this.dragonKilled = false;
                EnderDragon entityenderdragon = this.createNewDragon();

                if (entityenderdragon != null) {
                    Iterator iterator = this.dragonEvent.getPlayers().iterator();

                    while (iterator.hasNext()) {
                        ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                        CriteriaTriggers.SUMMONED_ENTITY.trigger(entityplayer, (Entity) entityenderdragon);
                    }
                }
            } else {
                this.respawnStage = spawnState;
            }

        }
    }

    private boolean hasActiveExitPortal() {
        for (int i = -8; i <= 8; ++i) {
            int j = -8;

            label27:
            while (j <= 8) {
                LevelChunk chunk = this.level.getChunk(i, j);
                Iterator iterator = chunk.getBlockEntities().values().iterator();

                BlockEntity tileentity;

                do {
                    if (!iterator.hasNext()) {
                        ++j;
                        continue label27;
                    }

                    tileentity = (BlockEntity) iterator.next();
                } while (!(tileentity instanceof TheEndPortalBlockEntity));

                return true;
            }
        }

        return false;
    }

    @Nullable
    public BlockPattern.BlockPatternMatch findExitPortal() {
        ChunkPos chunkcoordintpair = new ChunkPos(this.origin);

        int i;

        for (int j = -8 + chunkcoordintpair.x; j <= 8 + chunkcoordintpair.x; ++j) {
            for (i = -8 + chunkcoordintpair.z; i <= 8 + chunkcoordintpair.z; ++i) {
                LevelChunk chunk = this.level.getChunk(j, i);
                Iterator iterator = chunk.getBlockEntities().values().iterator();

                while (iterator.hasNext()) {
                    BlockEntity tileentity = (BlockEntity) iterator.next();

                    if (tileentity instanceof TheEndPortalBlockEntity) {
                        BlockPattern.BlockPatternMatch shapedetector_shapedetectorcollection = this.exitPortalPattern.find(this.level, tileentity.getBlockPos());

                        if (shapedetector_shapedetectorcollection != null) {
                            BlockPos blockposition = shapedetector_shapedetectorcollection.getBlock(3, 3, 3).getPos();

                            if (this.portalLocation == null) {
                                this.portalLocation = blockposition;
                            }

                            return shapedetector_shapedetectorcollection;
                        }
                    }
                }
            }
        }

        BlockPos blockposition1 = EndPodiumFeature.getLocation(this.origin);

        i = this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockposition1).getY();

        for (int k = i; k >= this.level.getMinBuildHeight(); --k) {
            BlockPattern.BlockPatternMatch shapedetector_shapedetectorcollection1 = this.exitPortalPattern.find(this.level, new BlockPos(blockposition1.getX(), k, blockposition1.getZ()));

            if (shapedetector_shapedetectorcollection1 != null) {
                if (this.portalLocation == null) {
                    this.portalLocation = shapedetector_shapedetectorcollection1.getBlock(3, 3, 3).getPos();
                }

                return shapedetector_shapedetectorcollection1;
            }
        }

        return null;
    }

    private boolean isArenaLoaded() {
        if (this.skipArenaLoadedCheck) {
            return true;
        } else {
            ChunkPos chunkcoordintpair = new ChunkPos(this.origin);

            for (int i = -8 + chunkcoordintpair.x; i <= 8 + chunkcoordintpair.x; ++i) {
                for (int j = 8 + chunkcoordintpair.z; j <= 8 + chunkcoordintpair.z; ++j) {
                    ChunkAccess ichunkaccess = this.level.getChunkIfLoaded(i, j); // Folia - region threading
                    if (!(ichunkaccess instanceof LevelChunk) || !io.papermc.paper.util.TickThread.isTickThreadFor(this.level, i, j, this.level.regioniser.regionSectionChunkSize)) { // Folia - region threading
                        return false;
                    }

                    FullChunkStatus fullchunkstatus = ((LevelChunk) ichunkaccess).getFullStatus();

                    if (!fullchunkstatus.isOrAfter(FullChunkStatus.BLOCK_TICKING)) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    private void updatePlayers() {
        Set<ServerPlayer> set = Sets.newHashSet();
        Iterator iterator = this.level.getPlayers(this.validPlayer).iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            this.dragonEvent.addPlayer(entityplayer);
            set.add(entityplayer);
        }

        Set<ServerPlayer> set1 = Sets.newHashSet(this.dragonEvent.getPlayers());

        set1.removeAll(set);
        Iterator iterator1 = set1.iterator();

        while (iterator1.hasNext()) {
            ServerPlayer entityplayer1 = (ServerPlayer) iterator1.next();

            this.dragonEvent.removePlayer(entityplayer1);
        }

    }

    private void updateCrystalCount() {
        this.ticksSinceCrystalsScanned = 0;
        this.crystalsAlive = 0;

        SpikeFeature.EndSpike worldgenender_spike;

        for (Iterator iterator = SpikeFeature.getSpikesForLevel(this.level).iterator(); iterator.hasNext(); this.crystalsAlive += this.level.getEntitiesOfClass(EndCrystal.class, worldgenender_spike.getTopBoundingBox()).size()) {
            worldgenender_spike = (SpikeFeature.EndSpike) iterator.next();
        }

        EndDragonFight.LOGGER.debug("Found {} end crystals still alive", this.crystalsAlive);
    }

    public void setDragonKilled(EnderDragon dragon) {
        if (dragon.getUUID().equals(this.dragonUUID)) {
            this.dragonEvent.setProgress(0.0F);
            this.dragonEvent.setVisible(false);
            this.spawnExitPortal(true);
            this.spawnNewGateway();
            // Paper start - DragonEggFormEvent
            BlockPos eggPosition = this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, EndPodiumFeature.getLocation(this.origin));
            org.bukkit.craftbukkit.block.CraftBlockState eggState = org.bukkit.craftbukkit.block.CraftBlockStates.getBlockState(this.level, eggPosition);
            eggState.setData(Blocks.DRAGON_EGG.defaultBlockState());
            io.papermc.paper.event.block.DragonEggFormEvent eggEvent = new io.papermc.paper.event.block.DragonEggFormEvent(org.bukkit.craftbukkit.block.CraftBlock.at(this.level, eggPosition), eggState,
                new org.bukkit.craftbukkit.boss.CraftDragonBattle(this));
            // Paper end - DragonEggFormEvent
            if (this.level.paperConfig().entities.behavior.enderDragonsDeathAlwaysPlacesDragonEgg || !this.previouslyKilled) { // Paper - always place dragon egg
                // Paper start - DragonEggFormEvent
                // this.level.setBlockAndUpdate(this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, EndPodiumFeature.getLocation(this.origin)), Blocks.DRAGON_EGG.defaultBlockState());
            } else {
                eggEvent.setCancelled(true);
            }
            if (eggEvent.callEvent()) {
                eggEvent.getNewState().update(true);
            }
            // Paper end - DragonEggFormEvent

            this.previouslyKilled = true;
            this.dragonKilled = true;
        }

    }

    /** @deprecated */
    @Deprecated
    @VisibleForTesting
    public void removeAllGateways() {
        this.gateways.clear();
    }

    // Paper start
    public boolean spawnNewGatewayIfPossible() {
        if (!this.gateways.isEmpty()) {
            this.spawnNewGateway();
            return true;
        }
        return false;
    }

    public List<EndCrystal> getSpikeCrystals() {
        final List<EndCrystal> endCrystals = new java.util.ArrayList<>();
        for (final SpikeFeature.EndSpike spike : SpikeFeature.getSpikesForLevel(this.level)) {
            endCrystals.addAll(this.level.getEntitiesOfClass(EndCrystal.class, spike.getTopBoundingBox()));
        }
        return endCrystals;
    }
    // Paper end

    private void spawnNewGateway() {
        if (!this.gateways.isEmpty()) {
            int i = (Integer) this.gateways.remove(this.gateways.size() - 1);
            int j = Mth.floor(96.0D * Math.cos(2.0D * (-3.141592653589793D + 0.15707963267948966D * (double) i)));
            int k = Mth.floor(96.0D * Math.sin(2.0D * (-3.141592653589793D + 0.15707963267948966D * (double) i)));

            this.spawnNewGateway(new BlockPos(j, 75, k));
        }
    }

    public void spawnNewGateway(BlockPos pos) {
        this.level.levelEvent(3000, pos, 0);
        this.level.registryAccess().registry(Registries.CONFIGURED_FEATURE).flatMap((iregistry) -> {
            return iregistry.getHolder(EndFeatures.END_GATEWAY_DELAYED);
        }).ifPresent((holder_c) -> {
            ((ConfiguredFeature) holder_c.value()).place(this.level, this.level.getChunkSource().getGenerator(), RandomSource.create(), pos);
        });
    }

    public void spawnExitPortal(boolean previouslyKilled) {
        EndPodiumFeature worldgenendtrophy = new EndPodiumFeature(previouslyKilled);

        if (this.portalLocation == null) {
            for (this.portalLocation = this.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, EndPodiumFeature.getLocation(this.origin)).below(); this.level.getBlockState(this.portalLocation).is(Blocks.BEDROCK) && this.portalLocation.getY() > this.level.getSeaLevel(); this.portalLocation = this.portalLocation.below()) {
                ;
            }
        }

        // Paper start - Prevent "softlocked" exit portal generation
        if (this.portalLocation.getY() <= this.level.getMinBuildHeight()) {
            this.portalLocation = this.portalLocation.atY(this.level.getMinBuildHeight() + 1);
        }
        // Paper end
        if (worldgenendtrophy.place(FeatureConfiguration.NONE, this.level, this.level.getChunkSource().getGenerator(), RandomSource.create(), this.portalLocation)) {
            int i = Mth.positiveCeilDiv(4, 16);

            this.level.getChunkSource().chunkMap.waitForLightBeforeSending(new ChunkPos(this.portalLocation), i);
        }

    }

    @Nullable
    private EnderDragon createNewDragon() {
        this.level.getChunkAt(new BlockPos(this.origin.getX(), 128 + this.origin.getY(), this.origin.getZ()));
        EnderDragon entityenderdragon = (EnderDragon) EntityType.ENDER_DRAGON.create(this.level);

        if (entityenderdragon != null) {
            entityenderdragon.setDragonFight(this);
            entityenderdragon.setFightOrigin(this.origin);
            entityenderdragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
            entityenderdragon.moveTo((double) this.origin.getX(), (double) (128 + this.origin.getY()), (double) this.origin.getZ(), this.level.random.nextFloat() * 360.0F, 0.0F);
            this.level.addFreshEntity(entityenderdragon);
            this.dragonUUID = entityenderdragon.getUUID();
            this.resetSpikeCrystals(); // Paper
        }

        return entityenderdragon;
    }

    public void updateDragon(EnderDragon dragon) {
        if (dragon.getUUID().equals(this.dragonUUID)) {
            this.dragonEvent.setProgress(dragon.getHealth() / dragon.getMaxHealth());
            this.ticksSinceDragonSeen = 0;
            if (dragon.hasCustomName()) {
                this.dragonEvent.setName(dragon.getDisplayName());
                // Paper start - reset to default name
            } else {
                this.dragonEvent.setName(DEFAULT_BOSS_EVENT_NAME);
                // Paper end
            }
        }

    }

    public int getCrystalsAlive() {
        return this.crystalsAlive;
    }

    public void onCrystalDestroyed(EndCrystal enderCrystal, DamageSource source) {
        // Folia start - region threading
        if (!io.papermc.paper.util.TickThread.isTickThreadFor(this.level, this.origin)) {
            return;
        }
        // Folia end - region threading
        if (this.respawnStage != null && this.respawnCrystals.contains(enderCrystal)) {
            EndDragonFight.LOGGER.debug("Aborting respawn sequence");
            this.respawnStage = null;
            this.respawnTime = 0;
            this.resetSpikeCrystals();
            this.spawnExitPortal(true);
        } else {
            this.updateCrystalCount();
            Entity entity = this.level.getEntity(this.dragonUUID);

            if (entity instanceof EnderDragon) {
                ((EnderDragon) entity).onCrystalDestroyed(enderCrystal, enderCrystal.blockPosition(), source);
            }
        }

    }

    public boolean hasPreviouslyKilledDragon() {
        return this.previouslyKilled;
    }

    public boolean tryRespawn() { // CraftBukkit - return boolean
        // Paper start - pass null (indicating no placed end crystal involved) by default
        return this.tryRespawn(null);
    }

    public boolean tryRespawn(@Nullable BlockPos placedEndCrystalPos) { // placedEndCrystalPos is null if the tryRespawn() call was not caused by a placed end crystal
        // Paper end
        if (this.dragonKilled && this.respawnStage == null && io.papermc.paper.util.TickThread.isTickThreadFor(this.level, this.origin)) { // Folia - region threading
            BlockPos blockposition = this.portalLocation;

            if (blockposition == null) {
                EndDragonFight.LOGGER.debug("Tried to respawn, but need to find the portal first.");
                BlockPattern.BlockPatternMatch shapedetector_shapedetectorcollection = this.findExitPortal();

                if (shapedetector_shapedetectorcollection == null) {
                    EndDragonFight.LOGGER.debug("Couldn't find a portal, so we made one.");
                    this.spawnExitPortal(true);
                } else {
                    EndDragonFight.LOGGER.debug("Found the exit portal & saved its location for next time.");
                }

                blockposition = this.portalLocation;
            }

            // Paper start - check placed end crystal to portal proximity before attempting to respawn dragon
            if (placedEndCrystalPos != null) {
                // The end crystal must be 0 or 1 higher than the portal origin
                int dy = placedEndCrystalPos.getY() - blockposition.getY();
                if (dy != 0 && dy != 1) {
                    return false;
                }
                // The end crystal must be within a distance of 1 in one planar direction, and 3 in the other
                int dx = placedEndCrystalPos.getX() - blockposition.getX();
                int dz = placedEndCrystalPos.getZ() - blockposition.getZ();
                if (!((dx >= -1 && dx <= 1 && dz >= -3 && dz <= 3) || (dx >= -3 && dx <= 3 && dz >= -1 && dz <= 1))) {
                    return false;
                }
            }
            // Paper end

            List<EndCrystal> list = Lists.newArrayList();
            BlockPos blockposition1 = blockposition.above(1);
            Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

            while (iterator.hasNext()) {
                Direction enumdirection = (Direction) iterator.next();
                List<EndCrystal> list1 = this.level.getEntitiesOfClass(EndCrystal.class, new AABB(blockposition1.relative(enumdirection, 2)));

                if (list1.isEmpty()) {
                    return false; // CraftBukkit - return value
                }

                list.addAll(list1);
            }

            EndDragonFight.LOGGER.debug("Found all crystals, respawning dragon.");
            return this.respawnDragon(list); // CraftBukkit - return value
        }
        return false; // CraftBukkit - return value
    }

    public boolean respawnDragon(List<EndCrystal> list) { // CraftBukkit - return boolean
        if (this.dragonKilled && this.respawnStage == null) {
            for (BlockPattern.BlockPatternMatch shapedetector_shapedetectorcollection = this.findExitPortal(); shapedetector_shapedetectorcollection != null; shapedetector_shapedetectorcollection = this.findExitPortal()) {
                for (int i = 0; i < this.exitPortalPattern.getWidth(); ++i) {
                    for (int j = 0; j < this.exitPortalPattern.getHeight(); ++j) {
                        for (int k = 0; k < this.exitPortalPattern.getDepth(); ++k) {
                            BlockInWorld shapedetectorblock = shapedetector_shapedetectorcollection.getBlock(i, j, k);

                            if (shapedetectorblock.getState().is(Blocks.BEDROCK) || shapedetectorblock.getState().is(Blocks.END_PORTAL)) {
                                this.level.setBlockAndUpdate(shapedetectorblock.getPos(), Blocks.END_STONE.defaultBlockState());
                            }
                        }
                    }
                }
            }

            this.respawnStage = DragonRespawnAnimation.START;
            this.respawnTime = 0;
            this.spawnExitPortal(false);
            this.respawnCrystals = list;
            return true; // CraftBukkit - return value
        }
        return false; // CraftBukkit - return value
    }

    public void resetSpikeCrystals() {
        Iterator iterator = SpikeFeature.getSpikesForLevel(this.level).iterator();

        while (iterator.hasNext()) {
            SpikeFeature.EndSpike worldgenender_spike = (SpikeFeature.EndSpike) iterator.next();
            List<EndCrystal> list = this.level.getEntitiesOfClass(EndCrystal.class, worldgenender_spike.getTopBoundingBox());
            Iterator iterator1 = list.iterator();

            while (iterator1.hasNext()) {
                EndCrystal entityendercrystal = (EndCrystal) iterator1.next();

                entityendercrystal.setInvulnerable(false);
                entityendercrystal.setBeamTarget((BlockPos) null);
            }
        }

    }

    @Nullable
    public UUID getDragonUUID() {
        return this.dragonUUID;
    }

    public static record Data(boolean needsStateScanning, boolean dragonKilled, boolean previouslyKilled, boolean isRespawning, Optional<UUID> dragonUUID, Optional<BlockPos> exitPortalLocation, Optional<List<Integer>> gateways) {

        public static final Codec<EndDragonFight.Data> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.BOOL.fieldOf("NeedsStateScanning").orElse(true).forGetter(EndDragonFight.Data::needsStateScanning), Codec.BOOL.fieldOf("DragonKilled").orElse(false).forGetter(EndDragonFight.Data::dragonKilled), Codec.BOOL.fieldOf("PreviouslyKilled").orElse(false).forGetter(EndDragonFight.Data::previouslyKilled), Codec.BOOL.optionalFieldOf("IsRespawning", false).forGetter(EndDragonFight.Data::isRespawning), UUIDUtil.CODEC.optionalFieldOf("Dragon").forGetter(EndDragonFight.Data::dragonUUID), BlockPos.CODEC.optionalFieldOf("ExitPortalLocation").forGetter(EndDragonFight.Data::exitPortalLocation), Codec.list(Codec.INT).optionalFieldOf("Gateways").forGetter(EndDragonFight.Data::gateways)).apply(instance, EndDragonFight.Data::new);
        });
        public static final EndDragonFight.Data DEFAULT = new EndDragonFight.Data(true, false, false, false, Optional.empty(), Optional.empty(), Optional.empty());
    }
}
