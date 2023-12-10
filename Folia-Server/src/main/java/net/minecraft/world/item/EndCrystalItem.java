package net.minecraft.world.item;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

public class EndCrystalItem extends Item {

    public EndCrystalItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos blockposition = context.getClickedPos();
        BlockState iblockdata = world.getBlockState(blockposition);

        if (!iblockdata.is(Blocks.OBSIDIAN) && !iblockdata.is(Blocks.BEDROCK)) {
            return InteractionResult.FAIL;
        } else {
            BlockPos blockposition1 = blockposition.above(); final BlockPos aboveBlockPosition = blockposition1; // Paper - OBFHELPER

            if (!world.isEmptyBlock(blockposition1)) {
                return InteractionResult.FAIL;
            } else {
                double d0 = (double) blockposition1.getX();
                double d1 = (double) blockposition1.getY();
                double d2 = (double) blockposition1.getZ();
                List<Entity> list = world.getEntities((Entity) null, new AABB(d0, d1, d2, d0 + 1.0D, d1 + 2.0D, d2 + 1.0D));

                if (!list.isEmpty()) {
                    return InteractionResult.FAIL;
                } else {
                    if (world instanceof ServerLevel) {
                        EndCrystal entityendercrystal = new EndCrystal(world, d0 + 0.5D, d1, d2 + 0.5D);

                        entityendercrystal.setShowBottom(false);
                        // CraftBukkit start
                        if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPlaceEvent(context, entityendercrystal).isCancelled()) {
                            return InteractionResult.FAIL;
                        }
                        // CraftBukkit end
                        world.addFreshEntity(entityendercrystal);
                        world.gameEvent((Entity) context.getPlayer(), GameEvent.ENTITY_PLACE, blockposition1);
                        EndDragonFight enderdragonbattle = ((ServerLevel) world).getDragonFight();

                        if (enderdragonbattle != null) {
                            enderdragonbattle.tryRespawn(aboveBlockPosition); // Paper - pass placed end crystal position to pre-check proximity to portal
                        }
                    }

                    context.getItemInHand().shrink(1);
                    return InteractionResult.sidedSuccess(world.isClientSide);
                }
            }
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
