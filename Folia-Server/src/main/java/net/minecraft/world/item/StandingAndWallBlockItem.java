package net.minecraft.world.item;

import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.event.block.BlockCanBuildEvent;
// CraftBukkit end

public class StandingAndWallBlockItem extends BlockItem {

    public final Block wallBlock;
    private final Direction attachmentDirection;

    public StandingAndWallBlockItem(Block standingBlock, Block wallBlock, Item.Properties settings, Direction verticalAttachmentDirection) {
        super(standingBlock, settings);
        this.wallBlock = wallBlock;
        this.attachmentDirection = verticalAttachmentDirection;
    }

    protected boolean canPlace(LevelReader world, BlockState state, BlockPos pos) {
        return state.canSurvive(world, pos);
    }

    @Nullable
    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState iblockdata = this.wallBlock.getStateForPlacement(context);
        BlockState iblockdata1 = null;
        Level world = context.getLevel();
        BlockPos blockposition = context.getClickedPos();
        Direction[] aenumdirection = context.getNearestLookingDirections();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];

            if (enumdirection != this.attachmentDirection.getOpposite()) {
                BlockState iblockdata2 = enumdirection == this.attachmentDirection ? this.getBlock().getStateForPlacement(context) : iblockdata;

                if (iblockdata2 != null && this.canPlace(world, iblockdata2, blockposition)) {
                    iblockdata1 = iblockdata2;
                    break;
                }
            }
        }

        // CraftBukkit start
        if (iblockdata1 != null) {
            boolean defaultReturn = world.isUnobstructed(iblockdata1, blockposition, CollisionContext.empty());
            org.bukkit.entity.Player player = (context.getPlayer() instanceof ServerPlayer) ? (org.bukkit.entity.Player) context.getPlayer().getBukkitEntity() : null;

            BlockCanBuildEvent event = new BlockCanBuildEvent(CraftBlock.at(world, blockposition), player, CraftBlockData.fromData(iblockdata1), defaultReturn, org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(context.getHand())); // Paper - expose hand
            context.getLevel().getCraftServer().getPluginManager().callEvent(event);

            return (event.isBuildable()) ? iblockdata1 : null;
        } else {
            return null;
        }
        // CraftBukkit end
    }

    @Override
    public void registerBlocks(Map<Block, Item> map, Item item) {
        super.registerBlocks(map, item);
        map.put(this.wallBlock, item);
    }
}
