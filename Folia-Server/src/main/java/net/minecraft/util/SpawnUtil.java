package net.minecraft.util;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

public class SpawnUtil {

    public SpawnUtil() {}

    public static <T extends Mob> Optional<T> trySpawnMob(EntityType<T> entityType, MobSpawnType reason, ServerLevel world, BlockPos pos, int tries, int horizontalRange, int verticalRange, SpawnUtil.Strategy requirements) {
        // CraftBukkit start
        return SpawnUtil.trySpawnMob(entityType, reason, world, pos, tries, horizontalRange, verticalRange, requirements, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT, null); // Paper
    }

    public static <T extends Mob> Optional<T> trySpawnMob(EntityType<T> entitytypes, MobSpawnType enummobspawn, ServerLevel worldserver, BlockPos blockposition, int i, int j, int k, SpawnUtil.Strategy spawnutil_a, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason, @javax.annotation.Nullable Runnable onAbort) { // Paper
        // CraftBukkit end
        BlockPos.MutableBlockPos blockposition_mutableblockposition = blockposition.mutable();

        for (int l = 0; l < i; ++l) {
            int i1 = Mth.randomBetweenInclusive(worldserver.random, -j, j);
            int j1 = Mth.randomBetweenInclusive(worldserver.random, -j, j);

            blockposition_mutableblockposition.setWithOffset(blockposition, i1, k, j1);
            if (worldserver.getWorldBorder().isWithinBounds((BlockPos) blockposition_mutableblockposition) && SpawnUtil.moveToPossibleSpawnPosition(worldserver, k, blockposition_mutableblockposition, spawnutil_a)) {
                // Paper start
                String key = EntityType.getKey(entitytypes).getPath();
                org.bukkit.entity.EntityType type = org.bukkit.entity.EntityType.fromName(key);

                com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent event;
                event = new com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent(
                    io.papermc.paper.util.MCUtil.toLocation(worldserver, blockposition),
                    type,
                    reason
                );
                if (!event.callEvent()) {
                    if (event.shouldAbortSpawn()) {
                        if (onAbort != null) {
                            onAbort.run();
                        }
                        return Optional.empty();
                    }
                    break;
                }
                // Paper end
                T t0 = entitytypes.create(worldserver, (CompoundTag) null, null, blockposition_mutableblockposition, enummobspawn, false, false); // CraftBukkit - decompile error

                if (t0 != null) {
                    if (t0.checkSpawnRules(worldserver, enummobspawn) && t0.checkSpawnObstruction(worldserver)) {
                        worldserver.addFreshEntityWithPassengers(t0, reason); // CraftBukkit
                        return Optional.of(t0);
                    }

                    //t0.discard(); // Folia - region threading
                }
            }
        }

        return Optional.empty();
    }

    private static boolean moveToPossibleSpawnPosition(ServerLevel world, int verticalRange, BlockPos.MutableBlockPos pos, SpawnUtil.Strategy requirements) {
        BlockPos.MutableBlockPos blockposition_mutableblockposition1 = (new BlockPos.MutableBlockPos()).set(pos);
        BlockState iblockdata = world.getBlockState(blockposition_mutableblockposition1);

        for (int j = verticalRange; j >= -verticalRange; --j) {
            pos.move(Direction.DOWN);
            blockposition_mutableblockposition1.setWithOffset(pos, Direction.UP);
            BlockState iblockdata1 = world.getBlockState(pos);

            if (requirements.canSpawnOn(world, pos, iblockdata1, blockposition_mutableblockposition1, iblockdata)) {
                pos.move(Direction.UP);
                return true;
            }

            iblockdata = iblockdata1;
        }

        return false;
    }

    public interface Strategy {

        /** @deprecated */
        @Deprecated
        SpawnUtil.Strategy LEGACY_IRON_GOLEM = (worldserver, blockposition, iblockdata, blockposition1, iblockdata1) -> {
            return !iblockdata.is(Blocks.COBWEB) && !iblockdata.is(Blocks.CACTUS) && !iblockdata.is(Blocks.GLASS_PANE) && !(iblockdata.getBlock() instanceof StainedGlassPaneBlock) && !(iblockdata.getBlock() instanceof StainedGlassBlock) && !(iblockdata.getBlock() instanceof LeavesBlock) && !iblockdata.is(Blocks.CONDUIT) && !iblockdata.is(Blocks.ICE) && !iblockdata.is(Blocks.TNT) && !iblockdata.is(Blocks.GLOWSTONE) && !iblockdata.is(Blocks.BEACON) && !iblockdata.is(Blocks.SEA_LANTERN) && !iblockdata.is(Blocks.FROSTED_ICE) && !iblockdata.is(Blocks.TINTED_GLASS) && !iblockdata.is(Blocks.GLASS) ? (iblockdata1.isAir() || iblockdata1.liquid()) && (iblockdata.isSolid() || iblockdata.is(Blocks.POWDER_SNOW)) : false;
        };
        SpawnUtil.Strategy ON_TOP_OF_COLLIDER = (worldserver, blockposition, iblockdata, blockposition1, iblockdata1) -> {
            return iblockdata1.getCollisionShape(worldserver, blockposition1).isEmpty() && Block.isFaceFull(iblockdata.getCollisionShape(worldserver, blockposition), Direction.UP);
        };

        boolean canSpawnOn(ServerLevel world, BlockPos pos, BlockState state, BlockPos abovePos, BlockState aboveState);
    }
}
