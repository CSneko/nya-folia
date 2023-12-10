package net.minecraft.world.item;

import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.DummyGeneratorAccess;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
// CraftBukkit end

public class BucketItem extends Item implements DispensibleContainerItem {

    private static @Nullable ItemStack itemLeftInHandAfterPlayerBucketEmptyEvent = null; // Paper

    public final Fluid content;

    public BucketItem(Fluid fluid, Item.Properties settings) {
        super(settings);
        this.content = fluid;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemstack = user.getItemInHand(hand);
        BlockHitResult movingobjectpositionblock = getPlayerPOVHitResult(world, user, this.content == Fluids.EMPTY ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE);

        if (movingobjectpositionblock.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(itemstack);
        } else if (movingobjectpositionblock.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(itemstack);
        } else {
            BlockPos blockposition = movingobjectpositionblock.getBlockPos();
            Direction enumdirection = movingobjectpositionblock.getDirection();
            BlockPos blockposition1 = blockposition.relative(enumdirection);

            if (world.mayInteract(user, blockposition) && user.mayUseItemAt(blockposition1, enumdirection, itemstack)) {
                BlockState iblockdata;

                if (this.content == Fluids.EMPTY) {
                    iblockdata = world.getBlockState(blockposition);
                    Block block = iblockdata.getBlock();

                    if (block instanceof BucketPickup) {
                        BucketPickup ifluidsource = (BucketPickup) block;
                        // CraftBukkit start
                        ItemStack dummyFluid = ifluidsource.pickupBlock(user, DummyGeneratorAccess.INSTANCE, blockposition, iblockdata);
                        if (dummyFluid.isEmpty()) return InteractionResultHolder.fail(itemstack); // Don't fire event if the bucket won't be filled.
                        PlayerBucketFillEvent event = CraftEventFactory.callPlayerBucketFillEvent((ServerLevel) world, user, blockposition, blockposition, movingobjectpositionblock.getDirection(), itemstack, dummyFluid.getItem(), hand);

                        if (event.isCancelled()) {
                            ((ServerPlayer) user).connection.send(new ClientboundBlockUpdatePacket(world, blockposition)); // SPIGOT-5163 (see PlayerInteractManager)
                            ((ServerPlayer) user).getBukkitEntity().updateInventory(); // SPIGOT-4541
                            return InteractionResultHolder.fail(itemstack);
                        }
                        // CraftBukkit end
                        ItemStack itemstack1 = ifluidsource.pickupBlock(user, world, blockposition, iblockdata);

                        if (!itemstack1.isEmpty()) {
                            user.awardStat(Stats.ITEM_USED.get(this));
                            ifluidsource.getPickupSound().ifPresent((soundeffect) -> {
                                user.playSound(soundeffect, 1.0F, 1.0F);
                            });
                            world.gameEvent((Entity) user, GameEvent.FLUID_PICKUP, blockposition);
                            ItemStack itemstack2 = ItemUtils.createFilledResult(itemstack, user, CraftItemStack.asNMSCopy(event.getItemStack())); // CraftBukkit

                            if (!world.isClientSide) {
                                CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayer) user, itemstack1);
                            }

                            return InteractionResultHolder.sidedSuccess(itemstack2, world.isClientSide());
                        }
                    }

                    return InteractionResultHolder.fail(itemstack);
                } else {
                    iblockdata = world.getBlockState(blockposition);
                    BlockPos blockposition2 = iblockdata.getBlock() instanceof LiquidBlockContainer && this.content == Fluids.WATER ? blockposition : blockposition1;

                    if (this.emptyContents(user, world, blockposition2, movingobjectpositionblock, movingobjectpositionblock.getDirection(), blockposition, itemstack, hand)) { // CraftBukkit
                        this.checkExtraContent(user, world, itemstack, blockposition2);
                        if (user instanceof ServerPlayer) {
                            CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer) user, blockposition2, itemstack);
                        }

                        user.awardStat(Stats.ITEM_USED.get(this));
                        return InteractionResultHolder.sidedSuccess(BucketItem.getEmptySuccessItem(itemstack, user), world.isClientSide());
                    } else {
                        return InteractionResultHolder.fail(itemstack);
                    }
                }
            } else {
                return InteractionResultHolder.fail(itemstack);
            }
        }
    }

    public static ItemStack getEmptySuccessItem(ItemStack stack, Player player) {
        // Paper start
        if (itemLeftInHandAfterPlayerBucketEmptyEvent != null) {
            ItemStack itemInHand = itemLeftInHandAfterPlayerBucketEmptyEvent;
            itemLeftInHandAfterPlayerBucketEmptyEvent = null;
            return itemInHand;
        }
        // Paper end
        return !player.getAbilities().instabuild ? new ItemStack(Items.BUCKET) : stack;
    }

    @Override
    public void checkExtraContent(@Nullable Player player, Level world, ItemStack stack, BlockPos pos) {}

    @Override
    public boolean emptyContents(@Nullable Player player, Level world, BlockPos pos, @Nullable BlockHitResult hitResult) {
        // CraftBukkit start
        return this.emptyContents(player, world, pos, hitResult, null, null, null, InteractionHand.MAIN_HAND);
    }

    public boolean emptyContents(Player entityhuman, Level world, BlockPos blockposition, @Nullable BlockHitResult movingobjectpositionblock, Direction enumdirection, BlockPos clicked, ItemStack itemstack, InteractionHand enumhand) {
        // CraftBukkit end
        Fluid fluidtype = this.content;

        if (!(fluidtype instanceof FlowingFluid)) {
            return false;
        } else {
            FlowingFluid fluidtypeflowing;
            BlockState iblockdata;
            Block block;
            boolean flag;
            LiquidBlockContainer ifluidcontainer;
            boolean flag1;
            label70:
            {
                fluidtypeflowing = (FlowingFluid) fluidtype;
                iblockdata = world.getBlockState(blockposition);
                block = iblockdata.getBlock();
                flag = iblockdata.canBeReplaced(this.content);
                if (!iblockdata.isAir() && !flag) {
                    label67:
                    {
                        if (block instanceof LiquidBlockContainer) {
                            ifluidcontainer = (LiquidBlockContainer) block;
                            if (ifluidcontainer.canPlaceLiquid(entityhuman, world, blockposition, iblockdata, this.content)) {
                                break label67;
                            }
                        }

                        flag1 = false;
                        break label70;
                    }
                }

                flag1 = true;
            }

            boolean flag2 = flag1;

            // CraftBukkit start
            if (flag2 && entityhuman != null) {
                PlayerBucketEmptyEvent event = CraftEventFactory.callPlayerBucketEmptyEvent((ServerLevel) world, entityhuman, blockposition, clicked, enumdirection, itemstack, enumhand);
                if (event.isCancelled()) {
                    ((ServerPlayer) entityhuman).connection.send(new ClientboundBlockUpdatePacket(world, blockposition)); // SPIGOT-4238: needed when looking through entity
                    ((ServerPlayer) entityhuman).getBukkitEntity().updateInventory(); // SPIGOT-4541
                    return false;
                }
                itemLeftInHandAfterPlayerBucketEmptyEvent = event.getItemStack() != null ? event.getItemStack().equals(CraftItemStack.asNewCraftStack(net.minecraft.world.item.Items.BUCKET)) ? null : CraftItemStack.asNMSCopy(event.getItemStack()) : ItemStack.EMPTY; // Paper - fix empty event result itemstack
            }
            // CraftBukkit end
            if (!flag2) {
                return movingobjectpositionblock != null && this.emptyContents(entityhuman, world, movingobjectpositionblock.getBlockPos().relative(movingobjectpositionblock.getDirection()), (BlockHitResult) null, enumdirection, clicked, itemstack, enumhand); // CraftBukkit
            } else if (world.dimensionType().ultraWarm() && this.content.is(FluidTags.WATER)) {
                int i = blockposition.getX();
                int j = blockposition.getY();
                int k = blockposition.getZ();

                world.playSound(entityhuman, blockposition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.6F + (world.random.nextFloat() - world.random.nextFloat()) * 0.8F);

                for (int l = 0; l < 8; ++l) {
                    world.addParticle(ParticleTypes.LARGE_SMOKE, (double) i + Math.random(), (double) j + Math.random(), (double) k + Math.random(), 0.0D, 0.0D, 0.0D);
                }

                return true;
            } else {
                if (block instanceof LiquidBlockContainer) {
                    ifluidcontainer = (LiquidBlockContainer) block;
                    if (this.content == Fluids.WATER) {
                        ifluidcontainer.placeLiquid(world, blockposition, iblockdata, fluidtypeflowing.getSource(false));
                        this.playEmptySound(entityhuman, world, blockposition);
                        return true;
                    }
                }

                if (!world.isClientSide && flag && !iblockdata.liquid()) {
                    world.destroyBlock(blockposition, true);
                }

                if (!world.setBlock(blockposition, this.content.defaultFluidState().createLegacyBlock(), 11) && !iblockdata.getFluidState().isSource()) {
                    return false;
                } else {
                    this.playEmptySound(entityhuman, world, blockposition);
                    return true;
                }
            }
        }
    }

    protected void playEmptySound(@Nullable Player player, LevelAccessor world, BlockPos pos) {
        SoundEvent soundeffect = this.content.is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;

        world.playSound(player, pos, soundeffect, SoundSource.BLOCKS, 1.0F, 1.0F);
        world.gameEvent((Entity) player, GameEvent.FLUID_PLACE, pos);
    }
}
