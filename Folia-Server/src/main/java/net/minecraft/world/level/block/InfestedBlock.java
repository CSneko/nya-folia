package net.minecraft.world.level.block;

import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason; // CraftBukkit

public class InfestedBlock extends Block {

    private final Block hostBlock;
    private static final Map<Block, Block> BLOCK_BY_HOST_BLOCK = Maps.newIdentityHashMap();
    private static final Map<BlockState, BlockState> HOST_TO_INFESTED_STATES = Maps.newIdentityHashMap();
    private static final Map<BlockState, BlockState> INFESTED_TO_HOST_STATES = Maps.newIdentityHashMap();

    public InfestedBlock(Block regularBlock, BlockBehaviour.Properties settings) {
        super(settings.destroyTime(regularBlock.defaultDestroyTime() / 2.0F).explosionResistance(0.75F));
        this.hostBlock = regularBlock;
        InfestedBlock.BLOCK_BY_HOST_BLOCK.put(regularBlock, this);
    }

    public Block getHostBlock() {
        return this.hostBlock;
    }

    public static boolean isCompatibleHostBlock(BlockState block) {
        return InfestedBlock.BLOCK_BY_HOST_BLOCK.containsKey(block.getBlock());
    }

    private void spawnInfestation(ServerLevel world, BlockPos pos) {
        Silverfish entitysilverfish = (Silverfish) EntityType.SILVERFISH.create(world);

        if (entitysilverfish != null) {
            entitysilverfish.moveTo((double) pos.getX() + 0.5D, (double) pos.getY(), (double) pos.getZ() + 0.5D, 0.0F, 0.0F);
            world.addFreshEntity(entitysilverfish, SpawnReason.SILVERFISH_BLOCK); // CraftBukkit - add SpawnReason
            entitysilverfish.spawnAnim();
        }

    }

    @Override
    public void spawnAfterBreak(BlockState state, ServerLevel world, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, world, pos, tool, dropExperience);
        if (world.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS) && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) == 0) {
            this.spawnInfestation(world, pos);
        }

    }

    public static BlockState infestedStateByHost(BlockState regularState) {
        return InfestedBlock.getNewStateWithProperties(InfestedBlock.HOST_TO_INFESTED_STATES, regularState, () -> {
            return ((Block) InfestedBlock.BLOCK_BY_HOST_BLOCK.get(regularState.getBlock())).defaultBlockState();
        });
    }

    public BlockState hostStateByInfested(BlockState infestedState) {
        return InfestedBlock.getNewStateWithProperties(InfestedBlock.INFESTED_TO_HOST_STATES, infestedState, () -> {
            return this.getHostBlock().defaultBlockState();
        });
    }

    private static BlockState getNewStateWithProperties(Map<BlockState, BlockState> stateMap, BlockState fromState, Supplier<BlockState> toStateSupplier) {
        return (BlockState) stateMap.computeIfAbsent(fromState, (iblockdata1) -> {
            BlockState iblockdata2 = (BlockState) toStateSupplier.get();

            Property iblockstate;

            for (Iterator iterator = iblockdata1.getProperties().iterator(); iterator.hasNext(); iblockdata2 = iblockdata2.hasProperty(iblockstate) ? (BlockState) iblockdata2.setValue(iblockstate, iblockdata1.getValue(iblockstate)) : iblockdata2) {
                iblockstate = (Property) iterator.next();
            }

            return iblockdata2;
        });
    }
}
