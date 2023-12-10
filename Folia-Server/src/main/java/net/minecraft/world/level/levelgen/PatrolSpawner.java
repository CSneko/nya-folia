package net.minecraft.world.level.levelgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

public class PatrolSpawner implements CustomSpawner {

    //private int nextTick; // Folia - region threading

    public PatrolSpawner() {}

    @Override
    public int tick(ServerLevel world, boolean spawnMonsters, boolean spawnAnimals) {
        if (world.paperConfig().entities.behavior.pillagerPatrols.disable || world.paperConfig().entities.behavior.pillagerPatrols.spawnChance == 0) return 0; // Paper
        if (!spawnMonsters) {
            return 0;
        } else if (!world.getGameRules().getBoolean(GameRules.RULE_DO_PATROL_SPAWNING)) {
            return 0;
        } else {
            RandomSource randomsource = world.random;
            io.papermc.paper.threadedregions.RegionizedWorldData worldData = world.getCurrentWorldData(); // Folia - region threading

            // Paper start - Patrol settings
            // Random player selection moved up for per player spawning and configuration
            int j = world.getLocalPlayers().size(); // Folia - region threading
            if (j < 1) {
                return 0;
            }

            net.minecraft.server.level.ServerPlayer entityhuman = world.getLocalPlayers().get(randomsource.nextInt(j)); // Folia - region threading
            if (entityhuman.isSpectator()) {
                return 0;
            }

            int patrolSpawnDelay;
            if (world.paperConfig().entities.behavior.pillagerPatrols.spawnDelay.perPlayer) {
                --entityhuman.patrolSpawnDelay;
                patrolSpawnDelay = entityhuman.patrolSpawnDelay;
            } else {
                worldData.patrolSpawnerNextTick--; // Folia - region threading
                patrolSpawnDelay = worldData.patrolSpawnerNextTick; // Folia - region threading
            }

            if (patrolSpawnDelay > 0) {
                return 0;
            } else {
                long days;
                if (world.paperConfig().entities.behavior.pillagerPatrols.start.perPlayer) {
                    days = entityhuman.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME)) / 24000L; // PLAY_ONE_MINUTE is actually counting in ticks, a misnomer by Mojang
                } else {
                    days = world.getDayTime() / 24000L;
                }
                if (world.paperConfig().entities.behavior.pillagerPatrols.spawnDelay.perPlayer) {
                    entityhuman.patrolSpawnDelay += world.paperConfig().entities.behavior.pillagerPatrols.spawnDelay.ticks + randomsource.nextInt(1200);
                } else {
                    worldData.patrolSpawnerNextTick += world.paperConfig().entities.behavior.pillagerPatrols.spawnDelay.ticks + randomsource.nextInt(1200); // Folia - region threading
                }

                if (days >= world.paperConfig().entities.behavior.pillagerPatrols.start.day && world.isDay()) {
                    if (randomsource.nextDouble() >= world.paperConfig().entities.behavior.pillagerPatrols.spawnChance) {
                        // Paper end
                        return 0;
                    } else {

                        if (j < 1) {
                            return 0;
                        } else {

                            if (entityhuman.isSpectator()) {
                                return 0;
                            } else if (world.isCloseToVillage(entityhuman.blockPosition(), 2)) {
                                return 0;
                            } else {
                                int k = (24 + randomsource.nextInt(24)) * (randomsource.nextBoolean() ? -1 : 1);
                                int l = (24 + randomsource.nextInt(24)) * (randomsource.nextBoolean() ? -1 : 1);
                                BlockPos.MutableBlockPos blockposition_mutableblockposition = entityhuman.blockPosition().mutable().move(k, 0, l);
                                boolean flag2 = true;

                                if (!world.hasChunksAt(blockposition_mutableblockposition.getX() - 10, blockposition_mutableblockposition.getZ() - 10, blockposition_mutableblockposition.getX() + 10, blockposition_mutableblockposition.getZ() + 10)) {
                                    return 0;
                                } else {
                                    Holder<Biome> holder = world.getBiome(blockposition_mutableblockposition);

                                    if (holder.is(BiomeTags.WITHOUT_PATROL_SPAWNS)) {
                                        return 0;
                                    } else {
                                        int i1 = 0;
                                        int j1 = (int) Math.ceil((double) world.getCurrentDifficultyAt(blockposition_mutableblockposition).getEffectiveDifficulty()) + 1;

                                        for (int k1 = 0; k1 < j1; ++k1) {
                                            ++i1;
                                            blockposition_mutableblockposition.setY(world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockposition_mutableblockposition).getY());
                                            if (k1 == 0) {
                                                if (!this.spawnPatrolMember(world, blockposition_mutableblockposition, randomsource, true)) {
                                                    break;
                                                }
                                            } else {
                                                this.spawnPatrolMember(world, blockposition_mutableblockposition, randomsource, false);
                                            }

                                            blockposition_mutableblockposition.setX(blockposition_mutableblockposition.getX() + randomsource.nextInt(5) - randomsource.nextInt(5));
                                            blockposition_mutableblockposition.setZ(blockposition_mutableblockposition.getZ() + randomsource.nextInt(5) - randomsource.nextInt(5));
                                        }

                                        return i1;
                                    }
                                }
                            }
                        }
                    }
                } else {
                    return 0;
                }
            }
        }
    }

    private boolean spawnPatrolMember(ServerLevel world, BlockPos pos, RandomSource random, boolean captain) {
        BlockState iblockdata = world.getBlockState(pos);

        if (!NaturalSpawner.isValidEmptySpawnBlock(world, pos, iblockdata, iblockdata.getFluidState(), EntityType.PILLAGER)) {
            return false;
        } else if (!PatrollingMonster.checkPatrollingMonsterSpawnRules(EntityType.PILLAGER, world, MobSpawnType.PATROL, pos, random)) {
            return false;
        } else {
            PatrollingMonster entitymonsterpatrolling = (PatrollingMonster) EntityType.PILLAGER.create(world);

            if (entitymonsterpatrolling != null) {
                if (captain) {
                    entitymonsterpatrolling.setPatrolLeader(true);
                    entitymonsterpatrolling.findPatrolTarget();
                }

                entitymonsterpatrolling.setPos((double) pos.getX(), (double) pos.getY(), (double) pos.getZ());
                entitymonsterpatrolling.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), MobSpawnType.PATROL, (SpawnGroupData) null, (CompoundTag) null);
                world.addFreshEntityWithPassengers(entitymonsterpatrolling, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.PATROL); // CraftBukkit
                return true;
            } else {
                return false;
            }
        }
    }
}
