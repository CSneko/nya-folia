package net.minecraft.world.entity.ai.village;

import com.mojang.logging.LogUtils;
import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class VillageSiege implements CustomSpawner {

    private static final Logger LOGGER = LogUtils.getLogger();
    // Folia - region threading

    public VillageSiege() {
        // Folia - region threading
    }

    @Override
    public int tick(ServerLevel world, boolean spawnMonsters, boolean spawnAnimals) {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = world.getCurrentWorldData(); // Folia - region threading
        // Folia start - region threading
        // check if the spawn pos is no longer owned by this region
        if (worldData.villageSiegeState.siegeState != State.SIEGE_DONE
            && !io.papermc.paper.util.TickThread.isTickThreadFor(world, worldData.villageSiegeState.spawnX >> 4, worldData.villageSiegeState.spawnZ >> 4, 8)) {
            // can't spawn here, just re-set
            worldData.villageSiegeState = new io.papermc.paper.threadedregions.RegionizedWorldData.VillageSiegeState();
        }
        // Folia end - region threading
        if (!world.isDay() && spawnMonsters) {
            float f = world.getTimeOfDay(0.0F);

            if ((double) f == 0.5D) {
                worldData.villageSiegeState.siegeState = world.random.nextInt(10) == 0 ? VillageSiege.State.SIEGE_TONIGHT : VillageSiege.State.SIEGE_DONE; // Folia - region threading
            }

            if (worldData.villageSiegeState.siegeState == VillageSiege.State.SIEGE_DONE) { // Folia - region threading
                return 0;
            } else {
                if (!worldData.villageSiegeState.hasSetupSiege) { // Folia - region threading
                    if (!this.tryToSetupSiege(world)) {
                        return 0;
                    }

                    worldData.villageSiegeState.hasSetupSiege = true; // Folia - region threading
                }

                if (worldData.villageSiegeState.nextSpawnTime > 0) { // Folia - region threading
                    --worldData.villageSiegeState.nextSpawnTime; // Folia - region threading
                    return 0;
                } else {
                    worldData.villageSiegeState.nextSpawnTime = 2; // Folia - region threading
                    if (worldData.villageSiegeState.zombiesToSpawn > 0) { // Folia - region threading
                        this.trySpawn(world);
                        --worldData.villageSiegeState.zombiesToSpawn; // Folia - region threading
                    } else {
                        worldData.villageSiegeState.siegeState = VillageSiege.State.SIEGE_DONE; // Folia - region threading
                    }

                    return 1;
                }
            }
        } else {
            worldData.villageSiegeState.siegeState = VillageSiege.State.SIEGE_DONE; // Folia - region threading
            worldData.villageSiegeState.hasSetupSiege = false; // Folia - region threading
            return 0;
        }
    }

    private boolean tryToSetupSiege(ServerLevel world) {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = world.getCurrentWorldData(); // Folia - region threading
        Iterator iterator = world.getLocalPlayers().iterator(); // Folia - region threading

        while (iterator.hasNext()) {
            Player entityhuman = (Player) iterator.next();

            if (!entityhuman.isSpectator()) {
                BlockPos blockposition = entityhuman.blockPosition();

                if (world.isVillage(blockposition) && !world.getBiome(blockposition).is(BiomeTags.WITHOUT_ZOMBIE_SIEGES)) {
                    for (int i = 0; i < 10; ++i) {
                        float f = world.random.nextFloat() * 6.2831855F;

                        worldData.villageSiegeState.spawnX = blockposition.getX() + Mth.floor(Mth.cos(f) * 32.0F); // Folia - region threading
                        worldData.villageSiegeState.spawnY = blockposition.getY(); // Folia - region threading
                        worldData.villageSiegeState.spawnZ = blockposition.getZ() + Mth.floor(Mth.sin(f) * 32.0F); // Folia - region threading
                        if (this.findRandomSpawnPos(world, new BlockPos(worldData.villageSiegeState.spawnX, worldData.villageSiegeState.spawnY, worldData.villageSiegeState.spawnZ)) != null) { // Folia - region threading
                            worldData.villageSiegeState.nextSpawnTime = 0; // Folia - region threading
                            worldData.villageSiegeState.zombiesToSpawn = 20; // Folia - region threading
                            break;
                        }
                    }

                    return true;
                }
            }
        }

        return false;
    }

    private void trySpawn(ServerLevel world) {
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = world.getCurrentWorldData(); // Folia - region threading
        Vec3 vec3d = this.findRandomSpawnPos(world, new BlockPos(worldData.villageSiegeState.spawnX, worldData.villageSiegeState.spawnY, worldData.villageSiegeState.spawnZ)); // Folia - region threading

        if (vec3d != null) {
            Zombie entityzombie;

            try {
                entityzombie = new Zombie(world);
                entityzombie.moveTo(vec3d.x, vec3d.y, vec3d.z, world.random.nextFloat() * 360.0F, 0.0F); // Folia - region threading - move up
                entityzombie.finalizeSpawn(world, world.getCurrentDifficultyAt(entityzombie.blockPosition()), MobSpawnType.EVENT, (SpawnGroupData) null, (CompoundTag) null);
            } catch (Exception exception) {
                VillageSiege.LOGGER.warn("Failed to create zombie for village siege at {}", vec3d, exception);
                com.destroystokyo.paper.exception.ServerInternalException.reportInternalException(exception); // Paper
                return;
            }

            // Folia - region threading - move up
            world.addFreshEntityWithPassengers(entityzombie, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION); // CraftBukkit
        }
    }

    @Nullable
    private Vec3 findRandomSpawnPos(ServerLevel world, BlockPos pos) {
        for (int i = 0; i < 10; ++i) {
            int j = pos.getX() + world.random.nextInt(16) - 8;
            int k = pos.getZ() + world.random.nextInt(16) - 8;
            int l = world.getHeight(Heightmap.Types.WORLD_SURFACE, j, k);
            BlockPos blockposition1 = new BlockPos(j, l, k);

            if (world.isVillage(blockposition1) && Monster.checkMonsterSpawnRules(EntityType.ZOMBIE, world, MobSpawnType.EVENT, blockposition1, world.random)) {
                return Vec3.atBottomCenterOf(blockposition1);
            }
        }

        return null;
    }

    public static enum State { // Folia - region threading

        SIEGE_CAN_ACTIVATE, SIEGE_TONIGHT, SIEGE_DONE;

        private State() {}
    }
}
