package net.minecraft.world.level.block;

import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.block.CraftBlockStates;
import org.bukkit.event.block.CauldronLevelChangeEvent;
// CraftBukkit end

public class LayeredCauldronBlock extends AbstractCauldronBlock {

    public static final int MIN_FILL_LEVEL = 1;
    public static final int MAX_FILL_LEVEL = 3;
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL_CAULDRON;
    private static final int BASE_CONTENT_HEIGHT = 6;
    private static final double HEIGHT_PER_LEVEL = 3.0D;
    public static final Predicate<Biome.Precipitation> RAIN = (biomebase_precipitation) -> {
        return biomebase_precipitation == Biome.Precipitation.RAIN;
    };
    public static final Predicate<Biome.Precipitation> SNOW = (biomebase_precipitation) -> {
        return biomebase_precipitation == Biome.Precipitation.SNOW;
    };
    private final Predicate<Biome.Precipitation> fillPredicate;

    public LayeredCauldronBlock(BlockBehaviour.Properties settings, Predicate<Biome.Precipitation> precipitationPredicate, Map<Item, CauldronInteraction> behaviorMap) {
        super(settings, behaviorMap);
        this.fillPredicate = precipitationPredicate;
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(LayeredCauldronBlock.LEVEL, 1));
    }

    @Override
    public boolean isFull(BlockState state) {
        return (Integer) state.getValue(LayeredCauldronBlock.LEVEL) == 3;
    }

    @Override
    protected boolean canReceiveStalactiteDrip(Fluid fluid) {
        return fluid == Fluids.WATER && this.fillPredicate == LayeredCauldronBlock.RAIN;
    }

    @Override
    protected double getContentHeight(BlockState state) {
        return (6.0D + (double) (Integer) state.getValue(LayeredCauldronBlock.LEVEL) * 3.0D) / 16.0D;
    }

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(world, pos)).callEvent()) { return; } // Paper
        if (!world.isClientSide && entity.isOnFire() && this.isEntityInsideContent(state, pos, entity)) {
            // CraftBukkit start
            if ((entity instanceof net.minecraft.world.entity.player.Player || world.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) && entity.mayInteract(world, pos)) { // Paper - Fixes MC-248588
                if (!this.handleEntityOnFireInsideWithEvent(state, world, pos, entity)) { // Paper - fix powdered snow cauldron extinguishing entities
                    return;
                }
            }
            entity.clearFire();
            // CraftBukkit end
        }

    }

    @Deprecated // Paper - use #handleEntityOnFireInsideWithEvent
    protected void handleEntityOnFireInside(BlockState state, Level world, BlockPos pos) {
        LayeredCauldronBlock.lowerFillLevel(state, world, pos);
    }
    // Paper start
    protected boolean handleEntityOnFireInsideWithEvent(BlockState state, Level world, BlockPos pos, Entity entity) {
        return LayeredCauldronBlock.lowerFillLevel(state, world, pos, entity, CauldronLevelChangeEvent.ChangeReason.EXTINGUISH);
    }
    // Paper end

    public static void lowerFillLevel(BlockState state, Level world, BlockPos pos) {
        // CraftBukkit start
        LayeredCauldronBlock.lowerFillLevel(state, world, pos, null, CauldronLevelChangeEvent.ChangeReason.UNKNOWN);
    }

    public static boolean lowerFillLevel(BlockState iblockdata, Level world, BlockPos blockposition, Entity entity, CauldronLevelChangeEvent.ChangeReason reason) {
        int i = (Integer) iblockdata.getValue(LayeredCauldronBlock.LEVEL) - 1;
        BlockState iblockdata1 = i == 0 ? Blocks.CAULDRON.defaultBlockState() : (BlockState) iblockdata.setValue(LayeredCauldronBlock.LEVEL, i);

        return LayeredCauldronBlock.changeLevel(iblockdata, world, blockposition, iblockdata1, entity, reason);
    }

    // CraftBukkit start
    // Paper start
    public static boolean changeLevel(BlockState iblockdata, Level world, BlockPos blockposition, BlockState newBlock, @javax.annotation.Nullable Entity entity, CauldronLevelChangeEvent.ChangeReason reason) { // Paper - entity is nullable
        return changeLevel(iblockdata, world, blockposition, newBlock, entity, reason, true);
    }

    public static boolean changeLevel(BlockState iblockdata, Level world, BlockPos blockposition, BlockState newBlock, @javax.annotation.Nullable Entity entity, CauldronLevelChangeEvent.ChangeReason reason, boolean sendGameEvent) { // Paper - entity is nullable
    // Paper end
        CraftBlockState newState = CraftBlockStates.getBlockState(world, blockposition);
        newState.setData(newBlock);

        CauldronLevelChangeEvent event = new CauldronLevelChangeEvent(
                world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()),
                (entity == null) ? null : entity.getBukkitEntity(), reason, newState
        );
        world.getCraftServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        newState.update(true);
        if (sendGameEvent) world.gameEvent(GameEvent.BLOCK_CHANGE, blockposition, GameEvent.Context.of(newBlock)); // Paper
        return true;
    }
    // CraftBukkit end

    @Override
    public void handlePrecipitation(BlockState state, Level world, BlockPos pos, Biome.Precipitation precipitation) {
        if (CauldronBlock.shouldHandlePrecipitation(world, precipitation) && (Integer) state.getValue(LayeredCauldronBlock.LEVEL) != 3 && this.fillPredicate.test(precipitation)) {
            BlockState iblockdata1 = (BlockState) state.cycle(LayeredCauldronBlock.LEVEL);

            LayeredCauldronBlock.changeLevel(state, world, pos, iblockdata1, null, CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL); // CraftBukkit
        }
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos) {
        return (Integer) state.getValue(LayeredCauldronBlock.LEVEL);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LayeredCauldronBlock.LEVEL);
    }

    @Override
    protected void receiveStalactiteDrip(BlockState state, Level world, BlockPos pos, Fluid fluid) {
        if (!this.isFull(state)) {
            BlockState iblockdata1 = (BlockState) state.setValue(LayeredCauldronBlock.LEVEL, (Integer) state.getValue(LayeredCauldronBlock.LEVEL) + 1);

            // CraftBukkit start
            if (!LayeredCauldronBlock.changeLevel(state, world, pos, iblockdata1, null, CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL)) {
                return;
            }
            // CraftBukkit end
            world.levelEvent(1047, pos, 0);
        }
    }
}
