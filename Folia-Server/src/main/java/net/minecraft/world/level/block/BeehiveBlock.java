package net.minecraft.world.level.block;

import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BeehiveBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty HONEY_LEVEL = BlockStateProperties.LEVEL_HONEY;
    public static final int MAX_HONEY_LEVELS = 5;
    private static final int SHEARED_HONEYCOMB_COUNT = 3;

    public BeehiveBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(BeehiveBlock.HONEY_LEVEL, 0)).setValue(BeehiveBlock.FACING, Direction.NORTH));
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos) {
        return (Integer) state.getValue(BeehiveBlock.HONEY_LEVEL);
    }

    @Override
    public void playerDestroy(Level world, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool, boolean includeDrops) { // Paper
        super.playerDestroy(world, player, pos, state, blockEntity, tool, includeDrops); // Paper
        if (!world.isClientSide && blockEntity instanceof BeehiveBlockEntity) {
            BeehiveBlockEntity tileentitybeehive = (BeehiveBlockEntity) blockEntity;

            if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) == 0) {
                tileentitybeehive.emptyAllLivingFromHive(player, state, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
                world.updateNeighbourForOutputSignal(pos, this);
                this.angerNearbyBees(world, pos);
            }

            // CriteriaTriggers.BEE_NEST_DESTROYED.trigger((ServerPlayer) player, state, tool, tileentitybeehive.getOccupantCount()); // Paper - moved until after items are dropped
        }

    }

    private void angerNearbyBees(Level world, BlockPos pos) {
        AABB axisalignedbb = (new AABB(pos)).inflate(8.0D, 6.0D, 8.0D);
        List<Bee> list = world.getEntitiesOfClass(Bee.class, axisalignedbb);

        if (!list.isEmpty()) {
            List<Player> list1 = world.getEntitiesOfClass(Player.class, axisalignedbb);

            if (list1.isEmpty()) {
                return;
            }

            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Bee entitybee = (Bee) iterator.next();

                if (entitybee.getTarget() == null) {
                    Player entityhuman = (Player) Util.getRandom(list1, world.random);

                    entitybee.setTarget(entityhuman, org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER, true); // CraftBukkit
                }
            }
        }

    }

    public static void dropHoneycomb(Level world, BlockPos pos) {
        popResource(world, pos, new ItemStack(Items.HONEYCOMB, 3)); // Paper - conflict on change, item needs to be set below
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack itemstack = player.getItemInHand(hand);
        int i = (Integer) state.getValue(BeehiveBlock.HONEY_LEVEL);
        boolean flag = false;

        if (i >= 5) {
            Item item = itemstack.getItem();

            if (itemstack.is(Items.SHEARS)) {
                // Paper start - Add PlayerShearBlockEvent
                io.papermc.paper.event.block.PlayerShearBlockEvent event = new io.papermc.paper.event.block.PlayerShearBlockEvent((org.bukkit.entity.Player) player.getBukkitEntity(), io.papermc.paper.util.MCUtil.toBukkitBlock(world, pos), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemstack), (hand == InteractionHand.OFF_HAND ? org.bukkit.inventory.EquipmentSlot.OFF_HAND : org.bukkit.inventory.EquipmentSlot.HAND), new java.util.ArrayList<>());
                event.getDrops().add(org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(new ItemStack(Items.HONEYCOMB, 3)));
                if (!event.callEvent()) {
                    return InteractionResult.PASS;
                }
                // Paper end
                world.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
                // Paper start - Add PlayerShearBlockEvent
                for (org.bukkit.inventory.ItemStack itemDrop : event.getDrops()) {
                    popResource(world, pos, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(itemDrop));
                }
                // Paper end
                itemstack.hurtAndBreak(1, player, (entityhuman1) -> {
                    entityhuman1.broadcastBreakEvent(hand);
                });
                flag = true;
                world.gameEvent((Entity) player, GameEvent.SHEAR, pos);
            } else if (itemstack.is(Items.GLASS_BOTTLE)) {
                itemstack.shrink(1);
                world.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (itemstack.isEmpty()) {
                    player.setItemInHand(hand, new ItemStack(Items.HONEY_BOTTLE));
                } else if (!player.getInventory().add(new ItemStack(Items.HONEY_BOTTLE))) {
                    player.drop(new ItemStack(Items.HONEY_BOTTLE), false);
                }

                flag = true;
                world.gameEvent((Entity) player, GameEvent.FLUID_PICKUP, pos);
            }

            if (!world.isClientSide() && flag) {
                player.awardStat(Stats.ITEM_USED.get(item));
            }
        }

        if (flag) {
            if (!CampfireBlock.isSmokeyPos(world, pos)) {
                if (this.hiveContainsBees(world, pos)) {
                    this.angerNearbyBees(world, pos);
                }

                this.releaseBeesAndResetHoneyLevel(world, state, pos, player, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
            } else {
                this.resetHoneyLevel(world, state, pos);
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            return super.use(state, world, pos, player, hand, hit);
        }
    }

    private boolean hiveContainsBees(Level world, BlockPos pos) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof BeehiveBlockEntity) {
            BeehiveBlockEntity tileentitybeehive = (BeehiveBlockEntity) tileentity;

            return !tileentitybeehive.isEmpty();
        } else {
            return false;
        }
    }

    public void releaseBeesAndResetHoneyLevel(Level world, BlockState state, BlockPos pos, @Nullable Player player, BeehiveBlockEntity.BeeReleaseStatus beeState) {
        this.resetHoneyLevel(world, state, pos);
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof BeehiveBlockEntity) {
            BeehiveBlockEntity tileentitybeehive = (BeehiveBlockEntity) tileentity;

            tileentitybeehive.emptyAllLivingFromHive(player, state, beeState);
        }

    }

    public void resetHoneyLevel(Level world, BlockState state, BlockPos pos) {
        world.setBlock(pos, (BlockState) state.setValue(BeehiveBlock.HONEY_LEVEL, 0), 3);
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        if ((Integer) state.getValue(BeehiveBlock.HONEY_LEVEL) >= 5) {
            for (int i = 0; i < random.nextInt(1) + 1; ++i) {
                this.trySpawnDripParticles(world, pos, state);
            }
        }

    }

    private void trySpawnDripParticles(Level world, BlockPos pos, BlockState state) {
        if (state.getFluidState().isEmpty() && world.random.nextFloat() >= 0.3F) {
            VoxelShape voxelshape = state.getCollisionShape(world, pos);
            double d0 = voxelshape.max(Direction.Axis.Y);

            if (d0 >= 1.0D && !state.is(BlockTags.IMPERMEABLE)) {
                double d1 = voxelshape.min(Direction.Axis.Y);

                if (d1 > 0.0D) {
                    this.spawnParticle(world, pos, voxelshape, (double) pos.getY() + d1 - 0.05D);
                } else {
                    BlockPos blockposition1 = pos.below();
                    BlockState iblockdata1 = world.getBlockState(blockposition1);
                    VoxelShape voxelshape1 = iblockdata1.getCollisionShape(world, blockposition1);
                    double d2 = voxelshape1.max(Direction.Axis.Y);

                    if ((d2 < 1.0D || !iblockdata1.isCollisionShapeFullBlock(world, blockposition1)) && iblockdata1.getFluidState().isEmpty()) {
                        this.spawnParticle(world, pos, voxelshape, (double) pos.getY() - 0.05D);
                    }
                }
            }

        }
    }

    private void spawnParticle(Level world, BlockPos pos, VoxelShape shape, double height) {
        this.spawnFluidParticle(world, (double) pos.getX() + shape.min(Direction.Axis.X), (double) pos.getX() + shape.max(Direction.Axis.X), (double) pos.getZ() + shape.min(Direction.Axis.Z), (double) pos.getZ() + shape.max(Direction.Axis.Z), height);
    }

    private void spawnFluidParticle(Level world, double minX, double maxX, double minZ, double maxZ, double height) {
        world.addParticle(ParticleTypes.DRIPPING_HONEY, Mth.lerp(world.random.nextDouble(), minX, maxX), height, Mth.lerp(world.random.nextDouble(), minZ, maxZ), 0.0D, 0.0D, 0.0D);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return (BlockState) this.defaultBlockState().setValue(BeehiveBlock.FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BeehiveBlock.HONEY_LEVEL, BeehiveBlock.FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeehiveBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return world.isClientSide ? null : createTickerHelper(type, BlockEntityType.BEEHIVE, BeehiveBlockEntity::serverTick);
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide && player.isCreative() && world.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            BlockEntity tileentity = world.getBlockEntity(pos);

            if (tileentity instanceof BeehiveBlockEntity) {
                BeehiveBlockEntity tileentitybeehive = (BeehiveBlockEntity) tileentity;
                ItemStack itemstack = new ItemStack(this);
                int i = (Integer) state.getValue(BeehiveBlock.HONEY_LEVEL);
                boolean flag = !tileentitybeehive.isEmpty();

                if (flag || i > 0) {
                    CompoundTag nbttagcompound;

                    if (flag) {
                        nbttagcompound = new CompoundTag();
                        nbttagcompound.put("Bees", tileentitybeehive.writeBees());
                        BlockItem.setBlockEntityData(itemstack, BlockEntityType.BEEHIVE, nbttagcompound);
                    }

                    nbttagcompound = new CompoundTag();
                    nbttagcompound.putInt("honey_level", i);
                    itemstack.addTagElement("BlockStateTag", nbttagcompound);
                    ItemEntity entityitem = new ItemEntity(world, (double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), itemstack);

                    entityitem.setDefaultPickUpDelay();
                    world.addFreshEntity(entityitem);
                }
            }
        }

        super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        Entity entity = (Entity) builder.getOptionalParameter(LootContextParams.THIS_ENTITY);

        if (entity instanceof PrimedTnt || entity instanceof Creeper || entity instanceof WitherSkull || entity instanceof WitherBoss || entity instanceof MinecartTNT) {
            BlockEntity tileentity = (BlockEntity) builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);

            if (tileentity instanceof BeehiveBlockEntity) {
                BeehiveBlockEntity tileentitybeehive = (BeehiveBlockEntity) tileentity;

                tileentitybeehive.emptyAllLivingFromHive((Player) null, state, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
            }
        }

        return super.getDrops(state, builder);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (world.getBlockState(neighborPos).getBlock() instanceof FireBlock) {
            BlockEntity tileentity = world.getBlockEntity(pos);

            if (tileentity instanceof BeehiveBlockEntity) {
                BeehiveBlockEntity tileentitybeehive = (BeehiveBlockEntity) tileentity;

                tileentitybeehive.emptyAllLivingFromHive((Player) null, state, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
            }
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    // CraftBukkit start - fix MC-227255
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BeehiveBlock.FACING, rotation.rotate(state.getValue(BeehiveBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(BeehiveBlock.FACING)));
    }
    // CraftBukkit end
}
