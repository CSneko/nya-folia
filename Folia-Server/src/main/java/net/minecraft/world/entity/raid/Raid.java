package net.minecraft.world.entity.raid;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class Raid {

    private static final int SECTION_RADIUS_FOR_FINDING_NEW_VILLAGE_CENTER = 2;
    private static final int ATTEMPT_RAID_FARTHEST = 0;
    private static final int ATTEMPT_RAID_CLOSE = 1;
    private static final int ATTEMPT_RAID_INSIDE = 2;
    private static final int VILLAGE_SEARCH_RADIUS = 32;
    private static final int RAID_TIMEOUT_TICKS = 48000;
    private static final int NUM_SPAWN_ATTEMPTS = 3;
    private static final String OMINOUS_BANNER_PATTERN_NAME = "block.minecraft.ominous_banner";
    private static final String RAIDERS_REMAINING = "event.minecraft.raid.raiders_remaining";
    public static final int VILLAGE_RADIUS_BUFFER = 16;
    private static final int POST_RAID_TICK_LIMIT = 40;
    private static final int DEFAULT_PRE_RAID_TICKS = 300;
    public static final int MAX_NO_ACTION_TIME = 2400;
    public static final int MAX_CELEBRATION_TICKS = 600;
    private static final int OUTSIDE_RAID_BOUNDS_TIMEOUT = 30;
    public static final int TICKS_PER_DAY = 24000;
    public static final int DEFAULT_MAX_BAD_OMEN_LEVEL = 5;
    private static final int LOW_MOB_THRESHOLD = 2;
    private static final Component RAID_NAME_COMPONENT = Component.translatable("event.minecraft.raid");
    private static final Component RAID_BAR_VICTORY_COMPONENT = Component.translatable("event.minecraft.raid.victory.full");
    private static final Component RAID_BAR_DEFEAT_COMPONENT = Component.translatable("event.minecraft.raid.defeat.full");
    private static final int HERO_OF_THE_VILLAGE_DURATION = 48000;
    public static final int VALID_RAID_RADIUS_SQR = 9216;
    public static final int RAID_REMOVAL_THRESHOLD_SQR = 12544;
    private final Map<Integer, Raider> groupToLeaderMap = Maps.newHashMap();
    private final Map<Integer, Set<Raider>> groupRaiderMap = Maps.newHashMap();
    public final Set<UUID> heroesOfTheVillage = Sets.newHashSet();
    public long ticksActive;
    private BlockPos center;
    private final ServerLevel level;
    private boolean started;
    private final int id;
    public float totalHealth;
    public int badOmenLevel;
    private boolean active;
    private int groupsSpawned;
    private final ServerBossEvent raidEvent;
    private int postRaidTicks;
    private int raidCooldownTicks;
    private final RandomSource random;
    public final int numGroups;
    private Raid.RaidStatus status;
    private int celebrationTicks;
    private Optional<BlockPos> waveSpawnPos;

    // Folia start - make raids thread-safe
    public boolean ownsRaid() {
        BlockPos center = this.getCenter();
        return center != null && io.papermc.paper.util.TickThread.isTickThreadFor(this.level, center.getX() >> 4, center.getZ() >> 4, 8);
    }
    // Folia end - make raids thread-safe

    public Raid(int id, ServerLevel world, BlockPos pos) {
        this.raidEvent = new ServerBossEvent(Raid.RAID_NAME_COMPONENT, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        this.random = RandomSource.create();
        this.waveSpawnPos = Optional.empty();
        this.id = id;
        this.level = world;
        this.active = true;
        this.raidCooldownTicks = 300;
        this.raidEvent.setProgress(0.0F);
        this.center = pos;
        this.numGroups = this.getNumGroups(world.getDifficulty());
        this.status = Raid.RaidStatus.ONGOING;
    }

    public Raid(ServerLevel world, CompoundTag nbt) {
        this.raidEvent = new ServerBossEvent(Raid.RAID_NAME_COMPONENT, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        this.random = RandomSource.create();
        this.waveSpawnPos = Optional.empty();
        this.level = world;
        this.id = nbt.getInt("Id");
        this.started = nbt.getBoolean("Started");
        this.active = nbt.getBoolean("Active");
        this.ticksActive = nbt.getLong("TicksActive");
        this.badOmenLevel = nbt.getInt("BadOmenLevel");
        this.groupsSpawned = nbt.getInt("GroupsSpawned");
        this.raidCooldownTicks = nbt.getInt("PreRaidTicks");
        this.postRaidTicks = nbt.getInt("PostRaidTicks");
        this.totalHealth = nbt.getFloat("TotalHealth");
        this.center = new BlockPos(nbt.getInt("CX"), nbt.getInt("CY"), nbt.getInt("CZ"));
        this.numGroups = nbt.getInt("NumGroups");
        this.status = Raid.RaidStatus.getByName(nbt.getString("Status"));
        this.heroesOfTheVillage.clear();
        if (nbt.contains("HeroesOfTheVillage", 9)) {
            ListTag nbttaglist = nbt.getList("HeroesOfTheVillage", 11);
            Iterator iterator = nbttaglist.iterator();

            while (iterator.hasNext()) {
                Tag nbtbase = (Tag) iterator.next();

                this.heroesOfTheVillage.add(NbtUtils.loadUUID(nbtbase));
            }
        }

    }

    public boolean isOver() {
        return this.isVictory() || this.isLoss();
    }

    public boolean isBetweenWaves() {
        return this.hasFirstWaveSpawned() && this.getTotalRaidersAlive() == 0 && this.raidCooldownTicks > 0;
    }

    public boolean hasFirstWaveSpawned() {
        return this.groupsSpawned > 0;
    }

    public boolean isStopped() {
        return this.status == Raid.RaidStatus.STOPPED;
    }

    public boolean isVictory() {
        return this.status == Raid.RaidStatus.VICTORY;
    }

    public boolean isLoss() {
        return this.status == Raid.RaidStatus.LOSS;
    }

    // CraftBukkit start
    public boolean isInProgress() {
        return this.status == RaidStatus.ONGOING;
    }
    // CraftBukkit end

    public float getTotalHealth() {
        return this.totalHealth;
    }

    public Set<Raider> getAllRaiders() {
        Set<Raider> set = Sets.newHashSet();
        Iterator iterator = this.groupRaiderMap.values().iterator();

        while (iterator.hasNext()) {
            Set<Raider> set1 = (Set) iterator.next();

            set.addAll(set1);
        }

        return set;
    }

    public Level getLevel() {
        return this.level;
    }

    public boolean isStarted() {
        return this.started;
    }

    public int getGroupsSpawned() {
        return this.groupsSpawned;
    }

    private Predicate<ServerPlayer> validPlayer() {
        return (entityplayer) -> {
            BlockPos blockposition = entityplayer.blockPosition();

            return io.papermc.paper.util.TickThread.isTickThreadFor(entityplayer) && entityplayer.isAlive() && this.level.getRaidAt(blockposition) == this; // Folia - make raids thread-safe
        };
    }

    private void updatePlayers() {
        Set<ServerPlayer> set = Sets.newHashSet(this.raidEvent.getPlayers());
        List<ServerPlayer> list = this.level.getPlayers(this.validPlayer());
        Iterator iterator = list.iterator();

        ServerPlayer entityplayer;

        while (iterator.hasNext()) {
            entityplayer = (ServerPlayer) iterator.next();
            if (!set.contains(entityplayer)) {
                this.raidEvent.addPlayer(entityplayer);
            }
        }

        iterator = set.iterator();

        while (iterator.hasNext()) {
            entityplayer = (ServerPlayer) iterator.next();
            if (!list.contains(entityplayer)) {
                this.raidEvent.removePlayer(entityplayer);
            }
        }

    }

    public int getMaxBadOmenLevel() {
        return 5;
    }

    public int getBadOmenLevel() {
        return this.badOmenLevel;
    }

    public void setBadOmenLevel(int badOmenLevel) {
        this.badOmenLevel = badOmenLevel;
    }

    public void absorbBadOmen(Player player) {
        if (player.hasEffect(MobEffects.BAD_OMEN)) {
            this.badOmenLevel += player.getEffect(MobEffects.BAD_OMEN).getAmplifier() + 1;
            this.badOmenLevel = Mth.clamp(this.badOmenLevel, 0, this.getMaxBadOmenLevel());
        }

        player.removeEffect(MobEffects.BAD_OMEN);
    }

    public void stop() {
        this.active = false;
        this.raidEvent.removeAllPlayers();
        this.status = Raid.RaidStatus.STOPPED;
    }

    public void tick() {
        if (!this.isStopped()) {
            if (this.status == Raid.RaidStatus.ONGOING) {
                boolean flag = this.active;

                this.active = this.level.hasChunkAt(this.center);
                if (this.level.getDifficulty() == Difficulty.PEACEFUL) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(this, org.bukkit.event.raid.RaidStopEvent.Reason.PEACE); // CraftBukkit
                    this.stop();
                    return;
                }

                if (flag != this.active) {
                    this.raidEvent.setVisible(this.active);
                }

                if (!this.active) {
                    return;
                }

                if (!this.level.isVillage(this.center)) {
                    this.moveRaidCenterToNearbyVillageSection();
                }

                if (!this.level.isVillage(this.center)) {
                    if (this.groupsSpawned > 0) {
                        this.status = Raid.RaidStatus.LOSS;
                        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidFinishEvent(this, new java.util.ArrayList<>()); // CraftBukkit
                    } else {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(this, org.bukkit.event.raid.RaidStopEvent.Reason.NOT_IN_VILLAGE); // CraftBukkit
                        this.stop();
                    }
                }

                ++this.ticksActive;
                if (this.ticksActive >= 48000L) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(this, org.bukkit.event.raid.RaidStopEvent.Reason.TIMEOUT); // CraftBukkit
                    this.stop();
                    return;
                }

                int i = this.getTotalRaidersAlive();
                boolean flag1;

                if (i == 0 && this.hasMoreWaves()) {
                    if (this.raidCooldownTicks > 0) {
                        flag1 = this.waveSpawnPos.isPresent();
                        boolean flag2 = !flag1 && this.raidCooldownTicks % 5 == 0;

                        if (flag1 && !this.level.isPositionEntityTicking((BlockPos) this.waveSpawnPos.get())) {
                            flag2 = true;
                        }

                        if (flag2) {
                            byte b0 = 0;

                            if (this.raidCooldownTicks < 100) {
                                b0 = 1;
                            } else if (this.raidCooldownTicks < 40) {
                                b0 = 2;
                            }

                            this.waveSpawnPos = this.getValidSpawnPos(b0);
                        }

                        if (this.raidCooldownTicks == 300 || this.raidCooldownTicks % 20 == 0) {
                            this.updatePlayers();
                        }

                        --this.raidCooldownTicks;
                        this.raidEvent.setProgress(Mth.clamp((float) (300 - this.raidCooldownTicks) / 300.0F, 0.0F, 1.0F));
                    } else if (this.raidCooldownTicks == 0 && this.groupsSpawned > 0) {
                        this.raidCooldownTicks = 300;
                        this.raidEvent.setName(Raid.RAID_NAME_COMPONENT);
                        return;
                    }
                }

                if (this.ticksActive % 20L == 0L) {
                    this.updatePlayers();
                    this.updateRaiders();
                    if (i > 0) {
                        if (i <= 2) {
                            this.raidEvent.setName(Raid.RAID_NAME_COMPONENT.copy().append(" - ").append((Component) Component.translatable("event.minecraft.raid.raiders_remaining", i)));
                        } else {
                            this.raidEvent.setName(Raid.RAID_NAME_COMPONENT);
                        }
                    } else {
                        this.raidEvent.setName(Raid.RAID_NAME_COMPONENT);
                    }
                }

                flag1 = false;
                int j = 0;

                while (this.shouldSpawnGroup()) {
                    BlockPos blockposition = this.waveSpawnPos.isPresent() ? (BlockPos) this.waveSpawnPos.get() : this.findRandomSpawnPos(j, 20);

                    if (blockposition != null) {
                        this.started = true;
                        this.spawnGroup(blockposition);
                        if (!flag1) {
                            this.playSound(blockposition);
                            flag1 = true;
                        }
                    } else {
                        ++j;
                    }

                    if (j > 3) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(this, org.bukkit.event.raid.RaidStopEvent.Reason.UNSPAWNABLE);  // CraftBukkit
                        this.stop();
                        break;
                    }
                }

                if (this.isStarted() && !this.hasMoreWaves() && i == 0) {
                    if (this.postRaidTicks < 40) {
                        ++this.postRaidTicks;
                    } else {
                        this.status = Raid.RaidStatus.VICTORY;
                        Iterator iterator = this.heroesOfTheVillage.iterator();

                        List<org.bukkit.entity.Player> winners = new java.util.ArrayList<>(); // CraftBukkit
                        while (iterator.hasNext()) {
                            UUID uuid = (UUID) iterator.next();
                            Entity entity = this.level.getEntity(uuid);

                            if (entity instanceof LivingEntity) {
                                LivingEntity entityliving = (LivingEntity) entity;

                                if (!entity.isSpectator()) {
                                    //entityliving.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 48000, this.badOmenLevel - 1, false, false, true)); // Folia - Fix off region raid heroes - move down
                                    if (entityliving instanceof ServerPlayer) {
                                        ServerPlayer entityplayer = (ServerPlayer) entityliving;

                                        // Folia start - Fix off region raid heroes - moved down
                                        winners.add(entityplayer.getBukkitEntity()); // CraftBukkit
                                    }
                                    // Folia start - Fix off region raid heroes
                                    entityliving.getBukkitEntity().taskScheduler.schedule((LivingEntity lv) -> {
                                        lv.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 48000, this.badOmenLevel - 1, false, false, true));
                                        if (lv instanceof ServerPlayer entityplayer) {
                                            entityplayer.awardStat(Stats.RAID_WIN);
                                            CriteriaTriggers.RAID_WIN.trigger(entityplayer);
                                        }
                                    }, null, 1L);
                                    // Folia end - Fix off region raid heroes
                                }
                            }
                        }
                        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidFinishEvent(this, winners); // CraftBukkit
                    }
                }

                this.setDirty();
            } else if (this.isOver()) {
                ++this.celebrationTicks;
                if (this.celebrationTicks >= 600) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(this, org.bukkit.event.raid.RaidStopEvent.Reason.FINISHED); // CraftBukkit
                    this.stop();
                    return;
                }

                if (this.celebrationTicks % 20 == 0) {
                    this.updatePlayers();
                    this.raidEvent.setVisible(true);
                    if (this.isVictory()) {
                        this.raidEvent.setProgress(0.0F);
                        this.raidEvent.setName(Raid.RAID_BAR_VICTORY_COMPONENT);
                    } else {
                        this.raidEvent.setName(Raid.RAID_BAR_DEFEAT_COMPONENT);
                    }
                }
            }

        }
    }

    private void moveRaidCenterToNearbyVillageSection() {
        Stream<SectionPos> stream = SectionPos.cube(SectionPos.of(this.center), 2);
        ServerLevel worldserver = this.level;

        Objects.requireNonNull(this.level);
        stream.filter(worldserver::isVillage).map(SectionPos::center).min(Comparator.comparingDouble((blockposition) -> {
            return blockposition.distSqr(this.center);
        })).ifPresent(this::setCenter);
    }

    private Optional<BlockPos> getValidSpawnPos(int proximity) {
        for (int j = 0; j < 3; ++j) {
            BlockPos blockposition = this.findRandomSpawnPos(proximity, 1);

            if (blockposition != null) {
                return Optional.of(blockposition);
            }
        }

        return Optional.empty();
    }

    private boolean hasMoreWaves() {
        return this.hasBonusWave() ? !this.hasSpawnedBonusWave() : !this.isFinalWave();
    }

    private boolean isFinalWave() {
        return this.getGroupsSpawned() == this.numGroups;
    }

    private boolean hasBonusWave() {
        return this.badOmenLevel > 1;
    }

    private boolean hasSpawnedBonusWave() {
        return this.getGroupsSpawned() > this.numGroups;
    }

    private boolean shouldSpawnBonusGroup() {
        return this.isFinalWave() && this.getTotalRaidersAlive() == 0 && this.hasBonusWave();
    }

    private void updateRaiders() {
        Iterator<Set<Raider>> iterator = this.groupRaiderMap.values().iterator();
        HashSet hashset = Sets.newHashSet();

        while (iterator.hasNext()) {
            Set<Raider> set = (Set) iterator.next();
            Iterator iterator1 = set.iterator();

            while (iterator1.hasNext()) {
                Raider entityraider = (Raider) iterator1.next();
                BlockPos blockposition = entityraider.blockPosition();

                if (!entityraider.isRemoved() && entityraider.level().dimension() == this.level.dimension() && this.center.distSqr(blockposition) < 12544.0D) {
                    if (entityraider.tickCount > 600) {
                        if (this.level.getEntity(entityraider.getUUID()) == null) {
                            hashset.add(entityraider);
                        }

                        if (!this.level.isVillage(blockposition) && entityraider.getNoActionTime() > 2400) {
                            entityraider.setTicksOutsideRaid(entityraider.getTicksOutsideRaid() + 1);
                        }

                        if (entityraider.getTicksOutsideRaid() >= 30) {
                            hashset.add(entityraider);
                        }
                    }
                } else {
                    hashset.add(entityraider);
                }
            }
        }

        Iterator iterator2 = hashset.iterator();

        while (iterator2.hasNext()) {
            Raider entityraider1 = (Raider) iterator2.next();

            this.removeFromRaid(entityraider1, true);
        }

    }

    private void playSound(BlockPos pos) {
        float f = 13.0F;
        boolean flag = true;
        Collection<ServerPlayer> collection = this.raidEvent.getPlayers();
        long i = this.random.nextLong();
        Iterator iterator = this.level.getLocalPlayers().iterator(); // Folia - region threading

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();
            Vec3 vec3d = entityplayer.position();
            Vec3 vec3d1 = Vec3.atCenterOf(pos);
            double d0 = Math.sqrt((vec3d1.x - vec3d.x) * (vec3d1.x - vec3d.x) + (vec3d1.z - vec3d.z) * (vec3d1.z - vec3d.z));
            double d1 = vec3d.x + 13.0D / d0 * (vec3d1.x - vec3d.x);
            double d2 = vec3d.z + 13.0D / d0 * (vec3d1.z - vec3d.z);

            if (d0 <= 64.0D || collection.contains(entityplayer)) {
                entityplayer.connection.send(new ClientboundSoundPacket(SoundEvents.RAID_HORN, SoundSource.NEUTRAL, d1, entityplayer.getY(), d2, 64.0F, 1.0F, i));
            }
        }

    }

    private void spawnGroup(BlockPos pos) {
        boolean flag = false;
        int i = this.groupsSpawned + 1;

        this.totalHealth = 0.0F;
        DifficultyInstance difficultydamagescaler = this.level.getCurrentDifficultyAt(pos);
        boolean flag1 = this.shouldSpawnBonusGroup();
        Raid.RaiderType[] araid_wave = Raid.RaiderType.VALUES;
        int j = araid_wave.length;
        int k = 0;

        // CraftBukkit start
        Raider leader = null;
        List<Raider> raiders = new java.util.ArrayList<>();
        // CraftBukkit end
        while (k < j) {
            Raid.RaiderType raid_wave = araid_wave[k];
            int l = this.getDefaultNumSpawns(raid_wave, i, flag1) + this.getPotentialBonusSpawns(raid_wave, this.random, i, difficultydamagescaler, flag1);
            int i1 = 0;
            int j1 = 0;

            while (true) {
                if (j1 < l) {
                    Raider entityraider = (Raider) raid_wave.entityType.create(this.level);

                    if (entityraider != null) {
                        if (!flag && entityraider.canBeLeader()) {
                            entityraider.setPatrolLeader(true);
                            this.setLeader(i, entityraider);
                            flag = true;
                            leader = entityraider; // CraftBukkit
                        }

                        this.joinRaid(i, entityraider, pos, false);
                        raiders.add(entityraider); // CraftBukkit
                        if (raid_wave.entityType == EntityType.RAVAGER) {
                            Raider entityraider1 = null;

                            if (i == this.getNumGroups(Difficulty.NORMAL)) {
                                entityraider1 = (Raider) EntityType.PILLAGER.create(this.level);
                            } else if (i >= this.getNumGroups(Difficulty.HARD)) {
                                if (i1 == 0) {
                                    entityraider1 = (Raider) EntityType.EVOKER.create(this.level);
                                } else {
                                    entityraider1 = (Raider) EntityType.VINDICATOR.create(this.level);
                                }
                            }

                            ++i1;
                            if (entityraider1 != null) {
                                this.joinRaid(i, entityraider1, pos, false);
                                entityraider1.moveTo(pos, 0.0F, 0.0F);
                                entityraider1.startRiding(entityraider);
                                raiders.add(entityraider); // CraftBukkit
                            }
                        }

                        ++j1;
                        continue;
                    }
                }

                ++k;
                break;
            }
        }

        this.waveSpawnPos = Optional.empty();
        ++this.groupsSpawned;
        this.updateBossbar();
        this.setDirty();
        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidSpawnWaveEvent(this, leader, raiders); // CraftBukkit
    }

    public void joinRaid(int wave, Raider raider, @Nullable BlockPos pos, boolean existing) {
        boolean flag1 = this.addWaveMob(wave, raider);

        if (flag1) {
            raider.setCurrentRaid(this);
            raider.setWave(wave);
            raider.setCanJoinRaid(true);
            raider.setTicksOutsideRaid(0);
            if (!existing && pos != null) {
                raider.setPos((double) pos.getX() + 0.5D, (double) pos.getY() + 1.0D, (double) pos.getZ() + 0.5D);
                raider.finalizeSpawn(this.level, this.level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, (SpawnGroupData) null, (CompoundTag) null);
                raider.applyRaidBuffs(wave, false);
                raider.setOnGround(true);
                this.level.addFreshEntityWithPassengers(raider, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.RAID); // CraftBukkit
            }
        }

    }

    public void updateBossbar() {
        this.raidEvent.setProgress(Mth.clamp(this.getHealthOfLivingRaiders() / this.totalHealth, 0.0F, 1.0F));
    }

    public float getHealthOfLivingRaiders() {
        float f = 0.0F;
        Iterator iterator = this.groupRaiderMap.values().iterator();

        while (iterator.hasNext()) {
            Set<Raider> set = (Set) iterator.next();

            Raider entityraider;

            for (Iterator iterator1 = set.iterator(); iterator1.hasNext(); f += entityraider.getHealth()) {
                entityraider = (Raider) iterator1.next();
            }
        }

        return f;
    }

    private boolean shouldSpawnGroup() {
        return this.raidCooldownTicks == 0 && (this.groupsSpawned < this.numGroups || this.shouldSpawnBonusGroup()) && this.getTotalRaidersAlive() == 0;
    }

    public int getTotalRaidersAlive() {
        return this.groupRaiderMap.values().stream().mapToInt(Set::size).sum();
    }

    public void removeFromRaid(Raider entity, boolean countHealth) {
        Set<Raider> set = (Set) this.groupRaiderMap.get(entity.getWave());

        if (set != null) {
            boolean flag1 = set.remove(entity);

            if (flag1) {
                if (countHealth) {
                    this.totalHealth -= entity.getHealth();
                }

                entity.setCurrentRaid((Raid) null);
                this.updateBossbar();
                this.setDirty();
            }
        }

    }

    private void setDirty() {
        this.level.getRaids().setDirty();
    }

    public static ItemStack getLeaderBannerInstance() {
        ItemStack itemstack = new ItemStack(Items.WHITE_BANNER);
        CompoundTag nbttagcompound = new CompoundTag();
        ListTag nbttaglist = (new BannerPattern.Builder()).addPattern(BannerPatterns.RHOMBUS_MIDDLE, DyeColor.CYAN).addPattern(BannerPatterns.STRIPE_BOTTOM, DyeColor.LIGHT_GRAY).addPattern(BannerPatterns.STRIPE_CENTER, DyeColor.GRAY).addPattern(BannerPatterns.BORDER, DyeColor.LIGHT_GRAY).addPattern(BannerPatterns.STRIPE_MIDDLE, DyeColor.BLACK).addPattern(BannerPatterns.HALF_HORIZONTAL, DyeColor.LIGHT_GRAY).addPattern(BannerPatterns.CIRCLE_MIDDLE, DyeColor.LIGHT_GRAY).addPattern(BannerPatterns.BORDER, DyeColor.BLACK).toListTag();

        nbttagcompound.put("Patterns", nbttaglist);
        BlockItem.setBlockEntityData(itemstack, BlockEntityType.BANNER, nbttagcompound);
        itemstack.hideTooltipPart(ItemStack.TooltipPart.ADDITIONAL);
        itemstack.setHoverName(Component.translatable("block.minecraft.ominous_banner").withStyle(ChatFormatting.GOLD));
        return itemstack;
    }

    @Nullable
    public Raider getLeader(int wave) {
        return (Raider) this.groupToLeaderMap.get(wave);
    }

    @Nullable
    private BlockPos findRandomSpawnPos(int proximity, int tries) {
        int k = proximity == 0 ? 2 : 2 - proximity;
        BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();

        for (int l = 0; l < tries; ++l) {
            float f = this.level.random.nextFloat() * 6.2831855F;
            int i1 = this.center.getX() + Mth.floor(Mth.cos(f) * 32.0F * (float) k) + this.level.random.nextInt(5);
            int j1 = this.center.getZ() + Mth.floor(Mth.sin(f) * 32.0F * (float) k) + this.level.random.nextInt(5);
            int k1 = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, i1, j1);

            blockposition_mutableblockposition.set(i1, k1, j1);
            if (!this.level.isVillage((BlockPos) blockposition_mutableblockposition) || proximity >= 2) {
                boolean flag = true;

                if (this.level.hasChunksAt(blockposition_mutableblockposition.getX() - 10, blockposition_mutableblockposition.getZ() - 10, blockposition_mutableblockposition.getX() + 10, blockposition_mutableblockposition.getZ() + 10) && this.level.isPositionEntityTicking(blockposition_mutableblockposition) && (NaturalSpawner.isSpawnPositionOk(SpawnPlacements.Type.ON_GROUND, this.level, blockposition_mutableblockposition, EntityType.RAVAGER) || this.level.getBlockState(blockposition_mutableblockposition.below()).is(Blocks.SNOW) && this.level.getBlockState(blockposition_mutableblockposition).isAir())) {
                    return blockposition_mutableblockposition;
                }
            }
        }

        return null;
    }

    private boolean addWaveMob(int wave, Raider entity) {
        return this.addWaveMob(wave, entity, true);
    }

    public boolean addWaveMob(int wave, Raider entity, boolean countHealth) {
        this.groupRaiderMap.computeIfAbsent(wave, (integer) -> {
            return Sets.newHashSet();
        });
        Set<Raider> set = (Set) this.groupRaiderMap.get(wave);
        Raider entityraider1 = null;
        Iterator iterator = set.iterator();

        while (iterator.hasNext()) {
            Raider entityraider2 = (Raider) iterator.next();

            if (entityraider2.getUUID().equals(entity.getUUID())) {
                entityraider1 = entityraider2;
                break;
            }
        }

        if (entityraider1 != null) {
            set.remove(entityraider1);
            set.add(entity);
        }

        set.add(entity);
        if (countHealth) {
            this.totalHealth += entity.getHealth();
        }

        this.updateBossbar();
        this.setDirty();
        return true;
    }

    public void setLeader(int wave, Raider entity) {
        this.groupToLeaderMap.put(wave, entity);
        entity.setItemSlot(EquipmentSlot.HEAD, Raid.getLeaderBannerInstance());
        entity.setDropChance(EquipmentSlot.HEAD, 2.0F);
    }

    public void removeLeader(int wave) {
        this.groupToLeaderMap.remove(wave);
    }

    public BlockPos getCenter() {
        return this.center;
    }

    private void setCenter(BlockPos center) {
        this.center = center;
    }

    public int getId() {
        return this.id;
    }

    private int getDefaultNumSpawns(Raid.RaiderType member, int wave, boolean extra) {
        return extra ? member.spawnsPerWaveBeforeBonus[this.numGroups] : member.spawnsPerWaveBeforeBonus[wave];
    }

    private int getPotentialBonusSpawns(Raid.RaiderType member, RandomSource random, int wave, DifficultyInstance localDifficulty, boolean extra) {
        Difficulty enumdifficulty = localDifficulty.getDifficulty();
        boolean flag1 = enumdifficulty == Difficulty.EASY;
        boolean flag2 = enumdifficulty == Difficulty.NORMAL;
        int j;

        switch (member) {
            case WITCH:
                if (flag1 || wave <= 2 || wave == 4) {
                    return 0;
                }

                j = 1;
                break;
            case PILLAGER:
            case VINDICATOR:
                if (flag1) {
                    j = random.nextInt(2);
                } else if (flag2) {
                    j = 1;
                } else {
                    j = 2;
                }
                break;
            case RAVAGER:
                j = !flag1 && extra ? 1 : 0;
                break;
            default:
                return 0;
        }

        return j > 0 ? random.nextInt(j + 1) : 0;
    }

    public boolean isActive() {
        return this.active;
    }

    public CompoundTag save(CompoundTag nbt) {
        nbt.putInt("Id", this.id);
        nbt.putBoolean("Started", this.started);
        nbt.putBoolean("Active", this.active);
        nbt.putLong("TicksActive", this.ticksActive);
        nbt.putInt("BadOmenLevel", this.badOmenLevel);
        nbt.putInt("GroupsSpawned", this.groupsSpawned);
        nbt.putInt("PreRaidTicks", this.raidCooldownTicks);
        nbt.putInt("PostRaidTicks", this.postRaidTicks);
        nbt.putFloat("TotalHealth", this.totalHealth);
        nbt.putInt("NumGroups", this.numGroups);
        nbt.putString("Status", this.status.getName());
        nbt.putInt("CX", this.center.getX());
        nbt.putInt("CY", this.center.getY());
        nbt.putInt("CZ", this.center.getZ());
        ListTag nbttaglist = new ListTag();
        Iterator iterator = this.heroesOfTheVillage.iterator();

        while (iterator.hasNext()) {
            UUID uuid = (UUID) iterator.next();

            nbttaglist.add(NbtUtils.createUUID(uuid));
        }

        nbt.put("HeroesOfTheVillage", nbttaglist);
        return nbt;
    }

    public int getNumGroups(Difficulty difficulty) {
        switch (difficulty) {
            case EASY:
                return 3;
            case NORMAL:
                return 5;
            case HARD:
                return 7;
            default:
                return 0;
        }
    }

    public float getEnchantOdds() {
        int i = this.getBadOmenLevel();

        return i == 2 ? 0.1F : (i == 3 ? 0.25F : (i == 4 ? 0.5F : (i == 5 ? 0.75F : 0.0F)));
    }

    public void addHeroOfTheVillage(Entity entity) {
        this.heroesOfTheVillage.add(entity.getUUID());
    }

    // CraftBukkit start - a method to get all raiders
    public java.util.Collection<Raider> getRaiders() {
        return this.groupRaiderMap.values().stream().flatMap(Set::stream).collect(java.util.stream.Collectors.toSet());
    }
    // CraftBukkit end

    private static enum RaidStatus {

        ONGOING, VICTORY, LOSS, STOPPED;

        private static final Raid.RaidStatus[] VALUES = values();

        private RaidStatus() {}

        static Raid.RaidStatus getByName(String name) {
            Raid.RaidStatus[] araid_status = Raid.RaidStatus.VALUES;
            int i = araid_status.length;

            for (int j = 0; j < i; ++j) {
                Raid.RaidStatus raid_status = araid_status[j];

                if (name.equalsIgnoreCase(raid_status.name())) {
                    return raid_status;
                }
            }

            return Raid.RaidStatus.ONGOING;
        }

        public String getName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    private static enum RaiderType {

        VINDICATOR(EntityType.VINDICATOR, new int[]{0, 0, 2, 0, 1, 4, 2, 5}), EVOKER(EntityType.EVOKER, new int[]{0, 0, 0, 0, 0, 1, 1, 2}), PILLAGER(EntityType.PILLAGER, new int[]{0, 4, 3, 3, 4, 4, 4, 2}), WITCH(EntityType.WITCH, new int[]{0, 0, 0, 0, 3, 0, 0, 1}), RAVAGER(EntityType.RAVAGER, new int[]{0, 0, 0, 1, 0, 1, 0, 2});

        static final Raid.RaiderType[] VALUES = values();
        final EntityType<? extends Raider> entityType;
        final int[] spawnsPerWaveBeforeBonus;

        private RaiderType(EntityType entitytypes, int[] aint) {
            this.entityType = entitytypes;
            this.spawnsPerWaveBeforeBonus = aint;
        }
    }
}
