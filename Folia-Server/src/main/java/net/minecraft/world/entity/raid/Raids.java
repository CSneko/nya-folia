package net.minecraft.world.entity.raid;

import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

public class Raids extends SavedData {

    private static final String RAID_FILE_ID = "raids";
    public final Map<Integer, Raid> raidMap = new java.util.concurrent.ConcurrentHashMap<>(); // Folia - make raids thread-safe
    private final ServerLevel level;
    private final java.util.concurrent.atomic.AtomicInteger nextAvailableID = new java.util.concurrent.atomic.AtomicInteger(); // Folia - make raids thread-safe
    private int tick;

    public static SavedData.Factory<Raids> factory(ServerLevel world) {
        return new SavedData.Factory<>(() -> {
            return new Raids(world);
        }, (nbttagcompound) -> {
            return Raids.load(world, nbttagcompound);
        }, DataFixTypes.SAVED_DATA_RAIDS);
    }

    public Raids(ServerLevel world) {
        this.level = world;
        this.nextAvailableID.set(1); // Folia - make raids thread-safe
        this.setDirty();
    }

    public Raid get(int id) {
        return (Raid) this.raidMap.get(id);
    }

    // Folia start - make raids thread-safe
    public void globalTick() {
        ++this.tick;
        if (this.tick % 200 == 0) {
            this.setDirty();
        }
    }
    // Folia end - make raids thread-safe

    public void tick() {
        // Folia - make raids thread-safe - move to globalTick()
        Iterator iterator = this.raidMap.values().iterator();

        while (iterator.hasNext()) {
            Raid raid = (Raid) iterator.next();
            // Folia start - make raids thread-safe
            if (!raid.ownsRaid()) {
                continue;
            }
            // Folia end - make raids thread-safe

            if (this.level.getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)) {
                raid.stop();
            }

            if (raid.isStopped()) {
                iterator.remove();
                this.setDirty();
            } else {
                raid.tick();
            }
        }

        // Folia - make raids thread-safe - move to globalTick()

        DebugPackets.sendRaids(this.level, this.raidMap.values());
    }

    public static boolean canJoinRaid(Raider raider, Raid raid) {
        // Folia start - make raids thread-safe
        if (!raid.ownsRaid()) {
            return false;
        }
        // Folia end - make raids thread-safe
        return raider != null && raid != null && raid.getLevel() != null ? raider.isAlive() && raider.canJoinRaid() && raider.getNoActionTime() <= 2400 && raider.level().dimensionType() == raid.getLevel().dimensionType() : false;
    }

    @Nullable
    public Raid createOrExtendRaid(ServerPlayer player) {
        if (player.isSpectator()) {
            return null;
        } else if (this.level.getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)) {
            return null;
        } else {
            DimensionType dimensionmanager = player.level().dimensionType();

            if (!dimensionmanager.hasRaids() || !io.papermc.paper.util.TickThread.isTickThreadFor(this.level, player.chunkPosition().x, player.chunkPosition().z, 8)) { // Folia - region threading
                return null;
            } else {
                BlockPos blockposition = player.blockPosition();
                List<PoiRecord> list = this.level.getPoiManager().getInRange((holder) -> {
                    return holder.is(PoiTypeTags.VILLAGE);
                }, blockposition, 64, PoiManager.Occupancy.IS_OCCUPIED).toList();
                int i = 0;
                Vec3 vec3d = Vec3.ZERO;

                for (Iterator iterator = list.iterator(); iterator.hasNext(); ++i) {
                    PoiRecord villageplacerecord = (PoiRecord) iterator.next();
                    BlockPos blockposition1 = villageplacerecord.getPos();

                    vec3d = vec3d.add((double) blockposition1.getX(), (double) blockposition1.getY(), (double) blockposition1.getZ());
                }

                BlockPos blockposition2;

                if (i > 0) {
                    vec3d = vec3d.scale(1.0D / (double) i);
                    blockposition2 = BlockPos.containing(vec3d);
                } else {
                    blockposition2 = blockposition;
                }

                Raid raid = this.getOrCreateRaid(player.serverLevel(), blockposition2);
                boolean flag = false;

                if (!raid.isStarted()) {
                    /* CraftBukkit - moved down
                    if (!this.raidMap.containsKey(raid.getId())) {
                        this.raidMap.put(raid.getId(), raid);
                    }
                    */

                    flag = true;
                    // CraftBukkit start - fixed a bug with raid: players could add up Bad Omen level even when the raid had finished
                } else if (raid.isInProgress() && raid.getBadOmenLevel() < raid.getMaxBadOmenLevel()) {
                    flag = true;
                    // CraftBukkit end
                } else {
                    player.removeEffect(MobEffects.BAD_OMEN);
                    this.level.broadcastEntityEvent(player, net.minecraft.world.entity.EntityEvent.BAD_OMEN_TRIGGERED /* (byte) 43 */); // Paper - Fix MC-253884
                }

                if (flag) {
                    // CraftBukkit start
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callRaidTriggerEvent(raid, player)) {
                        player.removeEffect(MobEffects.BAD_OMEN);
                        return null;
                    }

                    if (!this.raidMap.containsKey(raid.getId())) {
                        this.raidMap.put(raid.getId(), raid);
                    }
                    // CraftBukkit end
                    raid.absorbBadOmen(player);
                    this.level.broadcastEntityEvent(player, net.minecraft.world.entity.EntityEvent.BAD_OMEN_TRIGGERED /* (byte) 43 */); // Paper - Fix MC-253884
                    if (!raid.hasFirstWaveSpawned()) {
                        player.awardStat(Stats.RAID_TRIGGER);
                        CriteriaTriggers.BAD_OMEN.trigger(player);
                    }
                }

                this.setDirty();
                return raid;
            }
        }
    }

    private Raid getOrCreateRaid(ServerLevel world, BlockPos pos) {
        Raid raid = world.getRaidAt(pos);

        return raid != null ? raid : new Raid(this.getUniqueId(), world, pos);
    }

    public static Raids load(ServerLevel world, CompoundTag nbt) {
        Raids persistentraid = new Raids(world);

        persistentraid.nextAvailableID.set(nbt.getInt("NextAvailableID")); // Folia - make raids thread-safe
        persistentraid.tick = nbt.getInt("Tick");
        ListTag nbttaglist = nbt.getList("Raids", 10);

        for (int i = 0; i < nbttaglist.size(); ++i) {
            CompoundTag nbttagcompound1 = nbttaglist.getCompound(i);
            Raid raid = new Raid(world, nbttagcompound1);

            persistentraid.raidMap.put(raid.getId(), raid);
        }

        return persistentraid;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        nbt.putInt("NextAvailableID", this.nextAvailableID.get()); // Folia - make raids thread-safe
        nbt.putInt("Tick", this.tick);
        ListTag nbttaglist = new ListTag();
        Iterator iterator = this.raidMap.values().iterator();

        while (iterator.hasNext()) {
            Raid raid = (Raid) iterator.next();
            CompoundTag nbttagcompound1 = new CompoundTag();

            raid.save(nbttagcompound1);
            nbttaglist.add(nbttagcompound1);
        }

        nbt.put("Raids", nbttaglist);
        return nbt;
    }

    public static String getFileId(Holder<DimensionType> dimensionTypeEntry) {
        return dimensionTypeEntry.is(BuiltinDimensionTypes.END) ? "raids_end" : "raids";
    }

    private int getUniqueId() {
        return this.nextAvailableID.incrementAndGet(); // Folia - make raids thread-safe
    }

    @Nullable
    public Raid getNearbyRaid(BlockPos pos, int searchDistance) {
        Raid raid = null;
        double d0 = (double) searchDistance;
        Iterator iterator = this.raidMap.values().iterator();

        while (iterator.hasNext()) {
            Raid raid1 = (Raid) iterator.next();
            // Folia start - make raids thread-safe
            if (!raid1.ownsRaid()) {
                continue;
            }
            // Folia end - make raids thread-safe
            double d1 = raid1.getCenter().distSqr(pos);

            if (raid1.isActive() && d1 < d0) {
                raid = raid1;
                d0 = d1;
            }
        }

        return raid;
    }
}
