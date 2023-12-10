package net.minecraft.world.level.levelgen;

import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class PhantomSpawner implements CustomSpawner {

    //private int nextTick; // Folia - region threading

    public PhantomSpawner() {}

    @Override
    public int tick(ServerLevel world, boolean spawnMonsters, boolean spawnAnimals) {
        if (!spawnMonsters) {
            return 0;
        } else if (!world.getGameRules().getBoolean(GameRules.RULE_DOINSOMNIA)) {
            return 0;
        } else {
            // Paper start
            if (world.paperConfig().entities.behavior.phantomsSpawnAttemptMaxSeconds <= 0) {
                return 0;
            }
            // Paper end
            RandomSource randomsource = world.random;

            io.papermc.paper.threadedregions.RegionizedWorldData worldData = world.getCurrentWorldData(); // Folia - region threading

            --worldData.phantomSpawnerNextTick; // Folia - region threading
            if (worldData.phantomSpawnerNextTick > 0) { // Folia - region threading
                return 0;
            } else {
                // Paper start
                int spawnAttemptMinSeconds = world.paperConfig().entities.behavior.phantomsSpawnAttemptMinSeconds;
                int spawnAttemptMaxSeconds = world.paperConfig().entities.behavior.phantomsSpawnAttemptMaxSeconds;
                worldData.phantomSpawnerNextTick += (spawnAttemptMinSeconds + randomsource.nextInt(spawnAttemptMaxSeconds - spawnAttemptMinSeconds + 1)) * 20; // Folia - region threading
                // Paper end
                if (world.getSkyDarken() < 5 && world.dimensionType().hasSkyLight()) {
                    return 0;
                } else {
                    int i = 0;
                    Iterator iterator = world.getLocalPlayers().iterator(); // Folia - region threading

                    while (iterator.hasNext()) {
                        ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                        if (!entityplayer.isSpectator() && (!world.paperConfig().entities.behavior.phantomsDoNotSpawnOnCreativePlayers || !entityplayer.isCreative())) { // Paper
                            BlockPos blockposition = entityplayer.blockPosition();

                            if (!world.dimensionType().hasSkyLight() || blockposition.getY() >= world.getSeaLevel() && world.canSeeSky(blockposition)) {
                                DifficultyInstance difficultydamagescaler = world.getCurrentDifficultyAt(blockposition);

                                if (difficultydamagescaler.isHarderThan(randomsource.nextFloat() * 3.0F)) {
                                    ServerStatsCounter serverstatisticmanager = entityplayer.getStats();
                                    int j = Mth.clamp(serverstatisticmanager.getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)), 1, Integer.MAX_VALUE);
                                    boolean flag2 = true;

                                    if (randomsource.nextInt(j) >= world.paperConfig().entities.behavior.playerInsomniaStartTicks) { // Paper
                                        BlockPos blockposition1 = blockposition.above(20 + randomsource.nextInt(15)).east(-10 + randomsource.nextInt(21)).south(-10 + randomsource.nextInt(21));
                                        BlockState iblockdata = world.getBlockState(blockposition1);
                                        FluidState fluid = world.getFluidState(blockposition1);

                                        if (NaturalSpawner.isValidEmptySpawnBlock(world, blockposition1, iblockdata, fluid, EntityType.PHANTOM)) {
                                            SpawnGroupData groupdataentity = null;
                                            int k = 1 + randomsource.nextInt(difficultydamagescaler.getDifficulty().getId() + 1);

                                            for (int l = 0; l < k; ++l) {
                                                // Paper start
                                                com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent event = new com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent(io.papermc.paper.util.MCUtil.toLocation(world, blockposition1), entityplayer.getBukkitEntity(), org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL);
                                                if (!event.callEvent()) {
                                                    if (event.shouldAbortSpawn()) {
                                                        break;
                                                    }
                                                    continue;
                                                }
                                                // Paper end
                                                Phantom entityphantom = (Phantom) EntityType.PHANTOM.create(world);

                                                if (entityphantom != null) {
                                                    entityphantom.setSpawningEntity(entityplayer.getUUID()); // Paper
                                                    entityphantom.moveTo(blockposition1, 0.0F, 0.0F);
                                                    groupdataentity = entityphantom.finalizeSpawn(world, difficultydamagescaler, MobSpawnType.NATURAL, groupdataentity, (CompoundTag) null);
                                                    world.addFreshEntityWithPassengers(entityphantom, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL); // CraftBukkit
                                                    ++i;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    return i;
                }
            }
        }
    }
}
