package net.minecraft.world.level.block;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.ArrayUtils;

public class BedBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    public static final BooleanProperty OCCUPIED = BlockStateProperties.OCCUPIED;
    protected static final int HEIGHT = 9;
    protected static final VoxelShape BASE = Block.box(0.0D, 3.0D, 0.0D, 16.0D, 9.0D, 16.0D);
    private static final int LEG_WIDTH = 3;
    protected static final VoxelShape LEG_NORTH_WEST = Block.box(0.0D, 0.0D, 0.0D, 3.0D, 3.0D, 3.0D);
    protected static final VoxelShape LEG_SOUTH_WEST = Block.box(0.0D, 0.0D, 13.0D, 3.0D, 3.0D, 16.0D);
    protected static final VoxelShape LEG_NORTH_EAST = Block.box(13.0D, 0.0D, 0.0D, 16.0D, 3.0D, 3.0D);
    protected static final VoxelShape LEG_SOUTH_EAST = Block.box(13.0D, 0.0D, 13.0D, 16.0D, 3.0D, 16.0D);
    protected static final VoxelShape NORTH_SHAPE = Shapes.or(BedBlock.BASE, BedBlock.LEG_NORTH_WEST, BedBlock.LEG_NORTH_EAST);
    protected static final VoxelShape SOUTH_SHAPE = Shapes.or(BedBlock.BASE, BedBlock.LEG_SOUTH_WEST, BedBlock.LEG_SOUTH_EAST);
    protected static final VoxelShape WEST_SHAPE = Shapes.or(BedBlock.BASE, BedBlock.LEG_NORTH_WEST, BedBlock.LEG_SOUTH_WEST);
    protected static final VoxelShape EAST_SHAPE = Shapes.or(BedBlock.BASE, BedBlock.LEG_NORTH_EAST, BedBlock.LEG_SOUTH_EAST);
    private final DyeColor color;

    public BedBlock(DyeColor color, BlockBehaviour.Properties settings) {
        super(settings);
        this.color = color;
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(BedBlock.PART, BedPart.FOOT)).setValue(BedBlock.OCCUPIED, false));
    }

    @Nullable
    public static Direction getBedOrientation(BlockGetter world, BlockPos pos) {
        BlockState iblockdata = world.getBlockState(pos);

        return iblockdata.getBlock() instanceof BedBlock ? (Direction) iblockdata.getValue(BedBlock.FACING) : null;
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (world.isClientSide) {
            return InteractionResult.CONSUME;
        } else {
            if (state.getValue(BedBlock.PART) != BedPart.HEAD) {
                pos = pos.relative((Direction) state.getValue(BedBlock.FACING));
                state = world.getBlockState(pos);
                if (!state.is((Block) this)) {
                    return InteractionResult.CONSUME;
                }
            }

            // CraftBukkit - moved world and biome check into EntityHuman
            if (false && !BedBlock.canSetSpawn(world)) {
                final org.bukkit.block.BlockState explodedBlockState = org.bukkit.craftbukkit.block.CraftBlockStates.getUnplacedBlockState(world, pos, state); // Paper - exploded block state (this won't be called due to the false, but it's good for reference)
                world.removeBlock(pos, false);
                BlockPos blockposition1 = pos.relative(((Direction) state.getValue(BedBlock.FACING)).getOpposite());

                if (world.getBlockState(blockposition1).is((Block) this)) {
                    world.removeBlock(blockposition1, false);
                }

                Vec3 vec3d = pos.getCenter();

                world.explode((Entity) null, world.damageSources().badRespawnPointExplosion(vec3d, explodedBlockState), (ExplosionDamageCalculator) null, vec3d, 5.0F, true, Level.ExplosionInteraction.BLOCK);
                return InteractionResult.SUCCESS;
            } else if ((Boolean) state.getValue(BedBlock.OCCUPIED)) {
                if (!BedBlock.canSetSpawn(world)) return this.explodeBed(state, world, pos); // Paper - check explode first
                if (!this.kickVillagerOutOfBed(world, pos)) {
                    player.displayClientMessage(Component.translatable("block.minecraft.bed.occupied"), true);
                }

                return InteractionResult.SUCCESS;
            } else {
                // CraftBukkit start
                BlockState finaliblockdata = state;
                BlockPos finalblockposition = pos;
                // CraftBukkit end
                player.startSleepInBed(pos).ifLeft((entityhuman_enumbedresult) -> {
                    // Paper start - PlayerBedFailEnterEvent
                    if (entityhuman_enumbedresult != null) {
                        io.papermc.paper.event.player.PlayerBedFailEnterEvent event = new io.papermc.paper.event.player.PlayerBedFailEnterEvent((org.bukkit.entity.Player) player.getBukkitEntity(), io.papermc.paper.event.player.PlayerBedFailEnterEvent.FailReason.VALUES[entityhuman_enumbedresult.ordinal()], org.bukkit.craftbukkit.block.CraftBlock.at(world, finalblockposition), !world.dimensionType().bedWorks(), io.papermc.paper.adventure.PaperAdventure.asAdventure(entityhuman_enumbedresult.getMessage()));
                        if (!event.callEvent()) {
                            return;
                        }
                        // Paper end
                    // CraftBukkit start - handling bed explosion from below here
                    if (event.getWillExplode()) { // Paper
                        this.explodeBed(finaliblockdata, world, finalblockposition);
                    } else
                    // CraftBukkit end
                    if (entityhuman_enumbedresult.getMessage() != null) {
                        final net.kyori.adventure.text.Component message = event.getMessage(); // Paper
                        if (message != null) player.displayClientMessage(io.papermc.paper.adventure.PaperAdventure.asVanilla(message), true); // Paper
                    }
                    } // Paper

                });
                return InteractionResult.SUCCESS;
            }
        }
    }

    // CraftBukkit start
    private InteractionResult explodeBed(BlockState iblockdata, Level world, BlockPos blockposition) {
        {
            {
                final org.bukkit.block.BlockState explodedBlockState = org.bukkit.craftbukkit.block.CraftBlockStates.getUnplacedBlockState(world, blockposition, iblockdata); // Paper - exploded block state
                world.removeBlock(blockposition, false);
                BlockPos blockposition1 = blockposition.relative(((Direction) iblockdata.getValue(BedBlock.FACING)).getOpposite());

                if (world.getBlockState(blockposition1).getBlock() == this) {
                    world.removeBlock(blockposition1, false);
                }

                Vec3 vec3d = blockposition.getCenter();

                world.explode((Entity) null, world.damageSources().badRespawnPointExplosion(vec3d, explodedBlockState), (ExplosionDamageCalculator) null, vec3d, 5.0F, true, Level.ExplosionInteraction.BLOCK);
                return InteractionResult.SUCCESS;
            }
        }
    }
    // CraftBukkit end

    public static boolean canSetSpawn(Level world) {
        return world.dimensionType().bedWorks(); // Paper - actually check if the bed works
    }

    private boolean kickVillagerOutOfBed(Level world, BlockPos pos) {
        List<Villager> list = world.getEntitiesOfClass(Villager.class, new AABB(pos), LivingEntity::isSleeping);

        if (list.isEmpty()) {
            return false;
        } else {
            ((Villager) list.get(0)).stopSleeping();
            return true;
        }
    }

    @Override
    public void fallOn(Level world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        super.fallOn(world, state, pos, entity, fallDistance * 0.5F);
    }

    @Override
    public void updateEntityAfterFallOn(BlockGetter world, Entity entity) {
        if (entity.isSuppressingBounce()) {
            super.updateEntityAfterFallOn(world, entity);
        } else {
            this.bounceUp(entity);
        }

    }

    private void bounceUp(Entity entity) {
        Vec3 vec3d = entity.getDeltaMovement();

        if (vec3d.y < 0.0D) {
            double d0 = entity instanceof LivingEntity ? 1.0D : 0.8D;

            entity.setDeltaMovement(vec3d.x, -vec3d.y * 0.6600000262260437D * d0, vec3d.z);
        }

    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        return direction == BedBlock.getNeighbourDirection((BedPart) state.getValue(BedBlock.PART), (Direction) state.getValue(BedBlock.FACING)) ? (neighborState.is((Block) this) && neighborState.getValue(BedBlock.PART) != state.getValue(BedBlock.PART) ? (BlockState) state.setValue(BedBlock.OCCUPIED, (Boolean) neighborState.getValue(BedBlock.OCCUPIED)) : Blocks.AIR.defaultBlockState()) : super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    private static Direction getNeighbourDirection(BedPart part, Direction direction) {
        return part == BedPart.FOOT ? direction : direction.getOpposite();
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide && player.isCreative()) {
            BedPart blockpropertybedpart = (BedPart) state.getValue(BedBlock.PART);

            if (blockpropertybedpart == BedPart.FOOT) {
                BlockPos blockposition1 = pos.relative(BedBlock.getNeighbourDirection(blockpropertybedpart, (Direction) state.getValue(BedBlock.FACING)));
                BlockState iblockdata1 = world.getBlockState(blockposition1);

                if (iblockdata1.is((Block) this) && iblockdata1.getValue(BedBlock.PART) == BedPart.HEAD) {
                    world.setBlock(blockposition1, Blocks.AIR.defaultBlockState(), 35);
                    world.levelEvent(player, 2001, blockposition1, Block.getId(iblockdata1));
                }
            }
        }

        super.playerWillDestroy(world, pos, state, player);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction enumdirection = ctx.getHorizontalDirection();
        BlockPos blockposition = ctx.getClickedPos();
        BlockPos blockposition1 = blockposition.relative(enumdirection);
        Level world = ctx.getLevel();

        return world.getBlockState(blockposition1).canBeReplaced(ctx) && world.getWorldBorder().isWithinBounds(blockposition1) ? (BlockState) this.defaultBlockState().setValue(BedBlock.FACING, enumdirection) : null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Direction enumdirection = BedBlock.getConnectedDirection(state).getOpposite();

        switch (enumdirection) {
            case NORTH:
                return BedBlock.NORTH_SHAPE;
            case SOUTH:
                return BedBlock.SOUTH_SHAPE;
            case WEST:
                return BedBlock.WEST_SHAPE;
            default:
                return BedBlock.EAST_SHAPE;
        }
    }

    public static Direction getConnectedDirection(BlockState state) {
        Direction enumdirection = (Direction) state.getValue(BedBlock.FACING);

        return state.getValue(BedBlock.PART) == BedPart.HEAD ? enumdirection.getOpposite() : enumdirection;
    }

    public static DoubleBlockCombiner.BlockType getBlockType(BlockState state) {
        BedPart blockpropertybedpart = (BedPart) state.getValue(BedBlock.PART);

        return blockpropertybedpart == BedPart.HEAD ? DoubleBlockCombiner.BlockType.FIRST : DoubleBlockCombiner.BlockType.SECOND;
    }

    private static boolean isBunkBed(BlockGetter world, BlockPos pos) {
        return world.getBlockState(pos.below()).getBlock() instanceof BedBlock;
    }

    public static Optional<Vec3> findStandUpPosition(EntityType<?> type, CollisionGetter world, BlockPos pos, Direction bedDirection, float spawnAngle) {
        Direction enumdirection1 = bedDirection.getClockWise();
        Direction enumdirection2 = enumdirection1.isFacingAngle(spawnAngle) ? enumdirection1.getOpposite() : enumdirection1;

        if (BedBlock.isBunkBed(world, pos)) {
            return BedBlock.findBunkBedStandUpPosition(type, world, pos, bedDirection, enumdirection2);
        } else {
            int[][] aint = BedBlock.bedStandUpOffsets(bedDirection, enumdirection2);
            Optional<Vec3> optional = BedBlock.findStandUpPositionAtOffset(type, world, pos, aint, true);

            return optional.isPresent() ? optional : BedBlock.findStandUpPositionAtOffset(type, world, pos, aint, false);
        }
    }

    private static Optional<Vec3> findBunkBedStandUpPosition(EntityType<?> type, CollisionGetter world, BlockPos pos, Direction bedDirection, Direction respawnDirection) {
        int[][] aint = BedBlock.bedSurroundStandUpOffsets(bedDirection, respawnDirection);
        Optional<Vec3> optional = BedBlock.findStandUpPositionAtOffset(type, world, pos, aint, true);

        if (optional.isPresent()) {
            return optional;
        } else {
            BlockPos blockposition1 = pos.below();
            Optional<Vec3> optional1 = BedBlock.findStandUpPositionAtOffset(type, world, blockposition1, aint, true);

            if (optional1.isPresent()) {
                return optional1;
            } else {
                int[][] aint1 = BedBlock.bedAboveStandUpOffsets(bedDirection);
                Optional<Vec3> optional2 = BedBlock.findStandUpPositionAtOffset(type, world, pos, aint1, true);

                if (optional2.isPresent()) {
                    return optional2;
                } else {
                    Optional<Vec3> optional3 = BedBlock.findStandUpPositionAtOffset(type, world, pos, aint, false);

                    if (optional3.isPresent()) {
                        return optional3;
                    } else {
                        Optional<Vec3> optional4 = BedBlock.findStandUpPositionAtOffset(type, world, blockposition1, aint, false);

                        return optional4.isPresent() ? optional4 : BedBlock.findStandUpPositionAtOffset(type, world, pos, aint1, false);
                    }
                }
            }
        }
    }

    private static Optional<Vec3> findStandUpPositionAtOffset(EntityType<?> type, CollisionGetter world, BlockPos pos, int[][] possibleOffsets, boolean ignoreInvalidPos) {
        BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();
        int[][] aint1 = possibleOffsets;
        int i = possibleOffsets.length;

        for (int j = 0; j < i; ++j) {
            int[] aint2 = aint1[j];

            blockposition_mutableblockposition.set(pos.getX() + aint2[0], pos.getY(), pos.getZ() + aint2[1]);
            Vec3 vec3d = DismountHelper.findSafeDismountLocation(type, world, blockposition_mutableblockposition, ignoreInvalidPos);

            if (vec3d != null) {
                return Optional.of(vec3d);
            }
        }

        return Optional.empty();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BedBlock.FACING, BedBlock.PART, BedBlock.OCCUPIED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BedBlockEntity(pos, state, this.color);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(world, pos, state, placer, itemStack);
        if (!world.isClientSide) {
            BlockPos blockposition1 = pos.relative((Direction) state.getValue(BedBlock.FACING));

            world.setBlock(blockposition1, (BlockState) state.setValue(BedBlock.PART, BedPart.HEAD), 3);
            // CraftBukkit start - SPIGOT-7315: Don't updated if we capture block states
            if (world.getCurrentWorldData().captureBlockStates) { // Folia - region threading
                return;
            }
            // CraftBukkit end
            world.blockUpdated(pos, Blocks.AIR);
            state.updateNeighbourShapes(world, pos, 3);
        }

    }

    public DyeColor getColor() {
        return this.color;
    }

    @Override
    public long getSeed(BlockState state, BlockPos pos) {
        BlockPos blockposition1 = pos.relative((Direction) state.getValue(BedBlock.FACING), state.getValue(BedBlock.PART) == BedPart.HEAD ? 0 : 1);

        return Mth.getSeed(blockposition1.getX(), pos.getY(), blockposition1.getZ());
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return false;
    }

    private static int[][] bedStandUpOffsets(Direction bedDirection, Direction respawnDirection) {
        return (int[][]) ArrayUtils.addAll(BedBlock.bedSurroundStandUpOffsets(bedDirection, respawnDirection), BedBlock.bedAboveStandUpOffsets(bedDirection));
    }

    private static int[][] bedSurroundStandUpOffsets(Direction bedDirection, Direction respawnDirection) {
        return new int[][]{{respawnDirection.getStepX(), respawnDirection.getStepZ()}, {respawnDirection.getStepX() - bedDirection.getStepX(), respawnDirection.getStepZ() - bedDirection.getStepZ()}, {respawnDirection.getStepX() - bedDirection.getStepX() * 2, respawnDirection.getStepZ() - bedDirection.getStepZ() * 2}, {-bedDirection.getStepX() * 2, -bedDirection.getStepZ() * 2}, {-respawnDirection.getStepX() - bedDirection.getStepX() * 2, -respawnDirection.getStepZ() - bedDirection.getStepZ() * 2}, {-respawnDirection.getStepX() - bedDirection.getStepX(), -respawnDirection.getStepZ() - bedDirection.getStepZ()}, {-respawnDirection.getStepX(), -respawnDirection.getStepZ()}, {-respawnDirection.getStepX() + bedDirection.getStepX(), -respawnDirection.getStepZ() + bedDirection.getStepZ()}, {bedDirection.getStepX(), bedDirection.getStepZ()}, {respawnDirection.getStepX() + bedDirection.getStepX(), respawnDirection.getStepZ() + bedDirection.getStepZ()}};
    }

    private static int[][] bedAboveStandUpOffsets(Direction bedDirection) {
        return new int[][]{{0, 0}, {-bedDirection.getStepX(), -bedDirection.getStepZ()}};
    }
}
