package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
// CraftBukkit start
import org.bukkit.event.block.CauldronLevelChangeEvent;
// CraftBukkit end

public class CauldronBlock extends AbstractCauldronBlock {

    private static final float RAIN_FILL_CHANCE = 0.05F;
    private static final float POWDER_SNOW_FILL_CHANCE = 0.1F;

    public CauldronBlock(BlockBehaviour.Properties settings) {
        super(settings, CauldronInteraction.EMPTY);
    }

    @Override
    public boolean isFull(BlockState state) {
        return false;
    }

    protected static boolean shouldHandlePrecipitation(Level world, Biome.Precipitation precipitation) {
        return precipitation == Biome.Precipitation.RAIN ? world.getRandom().nextFloat() < 0.05F : (precipitation == Biome.Precipitation.SNOW ? world.getRandom().nextFloat() < 0.1F : false);
    }

    @Override
    public void handlePrecipitation(BlockState state, Level world, BlockPos pos, Biome.Precipitation precipitation) {
        if (CauldronBlock.shouldHandlePrecipitation(world, precipitation)) {
            if (precipitation == Biome.Precipitation.RAIN) {
                // Paper start - call event for initial fill
                if (!LayeredCauldronBlock.changeLevel(state, world, pos, Blocks.WATER_CAULDRON.defaultBlockState(), null, CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL, false)) { // avoid duplicate game event
                    return;
                }
                // Paper end
                world.gameEvent((Entity) null, GameEvent.BLOCK_CHANGE, pos);
            } else if (precipitation == Biome.Precipitation.SNOW) {
                // Paper start - call event for initial fill
                if (!LayeredCauldronBlock.changeLevel(state, world, pos, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState(), null, CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL, false)) { // avoid duplicate game event
                    return;
                }
                // Paper end
                world.gameEvent((Entity) null, GameEvent.BLOCK_CHANGE, pos);
            }

        }
    }

    @Override
    protected boolean canReceiveStalactiteDrip(Fluid fluid) {
        return true;
    }

    @Override
    protected void receiveStalactiteDrip(BlockState state, Level world, BlockPos pos, Fluid fluid) {
        BlockState iblockdata1;

        if (fluid == Fluids.WATER) {
            iblockdata1 = Blocks.WATER_CAULDRON.defaultBlockState();
            // Paper start - don't send level event or game event if cancelled
            if (!LayeredCauldronBlock.changeLevel(state, world, pos, iblockdata1, null, CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL)) { // CraftBukkit
                return;
            }
            // Paper end
            world.levelEvent(1047, pos, 0);
        } else if (fluid == Fluids.LAVA) {
            iblockdata1 = Blocks.LAVA_CAULDRON.defaultBlockState();
            // Paper start - don't send level event or game event if cancelled
            if (!LayeredCauldronBlock.changeLevel(state, world, pos, iblockdata1, null, CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL)) { // CraftBukkit
                return;
            }
            // Paper end
            world.levelEvent(1046, pos, 0);
        }

    }
}
