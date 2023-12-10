package org.bukkit.craftbukkit.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.bukkit.Material;
import org.bukkit.block.Block;

public final class CapturedBlockState extends CraftBlockState {

    private final boolean treeBlock;

    public CapturedBlockState(Block block, int flag, boolean treeBlock) {
        super(block, flag);

        this.treeBlock = treeBlock;
    }

    protected CapturedBlockState(CapturedBlockState state) {
        super(state);

        this.treeBlock = state.treeBlock;
    }

    @Override
    public boolean update(boolean force, boolean applyPhysics) {
        boolean result = super.update(force, applyPhysics);

        // Paper start
        this.checkTreeBlockHack();
        return result;
    }
    public void checkTreeBlockHack() {
        // Paper end
        // SPIGOT-5537: Horrible hack to manually add bees given World.captureTreeGeneration does not support tiles
        if (this.treeBlock && this.getType() == Material.BEE_NEST) {
            WorldGenLevel generatoraccessseed = this.world.getHandle();
            BlockPos blockposition1 = this.getPosition();
            RandomSource random = generatoraccessseed.getRandom();

            // Begin copied block from WorldGenFeatureTreeBeehive
            BlockEntity tileentity = generatoraccessseed.getBlockEntity(blockposition1);

            if (tileentity instanceof BeehiveBlockEntity) {
                BeehiveBlockEntity tileentitybeehive = (BeehiveBlockEntity) tileentity;
                int j = 2 + random.nextInt(2);

                for (int k = 0; k < j; ++k) {
                    Bee entitybee = new Bee(EntityType.BEE, generatoraccessseed.getMinecraftWorld());
                    entitybee.setPosRaw(blockposition1.getX(), blockposition1.getY(), blockposition1.getZ()); // Folia - region threading - set position so that thread checks do not fail

                    tileentitybeehive.addOccupantWithPresetTicks(entitybee, false, random.nextInt(599));
                }
            }
            // End copied block
        }

        // Paper
    }

    @Override
    public CapturedBlockState copy() {
        return new CapturedBlockState(this);
    }

    public static CapturedBlockState getBlockState(Level world, BlockPos pos, int flag) {
        return new CapturedBlockState(world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()), flag, false);
    }

    public static CapturedBlockState getTreeBlockState(Level world, BlockPos pos, int flag) {
        return new CapturedBlockState(world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()), flag, true);
    }
}
