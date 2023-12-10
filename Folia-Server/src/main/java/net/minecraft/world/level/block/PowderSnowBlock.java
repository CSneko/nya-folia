package net.minecraft.world.level.block;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PowderSnowBlock extends Block implements BucketPickup {

    private static final float HORIZONTAL_PARTICLE_MOMENTUM_FACTOR = 0.083333336F;
    private static final float IN_BLOCK_HORIZONTAL_SPEED_MULTIPLIER = 0.9F;
    private static final float IN_BLOCK_VERTICAL_SPEED_MULTIPLIER = 1.5F;
    private static final float NUM_BLOCKS_TO_FALL_INTO_BLOCK = 2.5F;
    private static final VoxelShape FALLING_COLLISION_SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.8999999761581421D, 1.0D);
    private static final double MINIMUM_FALL_DISTANCE_FOR_SOUND = 4.0D;
    private static final double MINIMUM_FALL_DISTANCE_FOR_BIG_SOUND = 7.0D;

    public PowderSnowBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState stateFrom, Direction direction) {
        return stateFrom.is((Block) this) ? true : super.skipRendering(state, stateFrom, direction);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(world, pos)).callEvent()) { return; } // Paper
        if (!(entity instanceof LivingEntity) || entity.getFeetBlockState().is((Block) this)) {
            entity.makeStuckInBlock(state, new Vec3(0.8999999761581421D, 1.5D, 0.8999999761581421D));
            if (world.isClientSide) {
                RandomSource randomsource = world.getRandom();
                boolean flag = entity.xOld != entity.getX() || entity.zOld != entity.getZ();

                if (flag && randomsource.nextBoolean()) {
                    world.addParticle(ParticleTypes.SNOWFLAKE, entity.getX(), (double) (pos.getY() + 1), entity.getZ(), (double) (Mth.randomBetween(randomsource, -1.0F, 1.0F) * 0.083333336F), 0.05000000074505806D, (double) (Mth.randomBetween(randomsource, -1.0F, 1.0F) * 0.083333336F));
                }
            }
        }

        entity.setIsInPowderSnow(true);
        if (!world.isClientSide) {
            // CraftBukkit start
            if (entity.isOnFire() && entity.mayInteract(world, pos)) {
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, pos, Blocks.AIR.defaultBlockState(), !(world.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) || entity instanceof Player))) {
                    return;
                }
                // CraftBukkit end
                world.destroyBlock(pos, false);
            }

            entity.setSharedFlagOnFire(false);
        }

    }

    @Override
    public void fallOn(Level world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if ((double) fallDistance >= 4.0D && entity instanceof LivingEntity) {
            LivingEntity entityliving = (LivingEntity) entity;
            LivingEntity.Fallsounds entityliving_a = entityliving.getFallSounds();
            SoundEvent soundeffect = (double) fallDistance < 7.0D ? entityliving_a.small() : entityliving_a.big();

            entity.playSound(soundeffect, 1.0F, 1.0F);
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext) {
            EntityCollisionContext voxelshapecollisionentity = (EntityCollisionContext) context;
            Entity entity = voxelshapecollisionentity.getEntity();

            if (entity != null) {
                if (entity.fallDistance > 2.5F) {
                    return PowderSnowBlock.FALLING_COLLISION_SHAPE;
                }

                boolean flag = entity instanceof FallingBlockEntity;

                if (flag || PowderSnowBlock.canEntityWalkOnPowderSnow(entity) && context.isAbove(Shapes.block(), pos, false) && !context.isDescending()) {
                    return super.getCollisionShape(state, world, pos, context);
                }
            }
        }

        return Shapes.empty();
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    public static boolean canEntityWalkOnPowderSnow(Entity entity) {
        return entity.getType().is(EntityTypeTags.POWDER_SNOW_WALKABLE_MOBS) ? true : (entity instanceof LivingEntity ? ((LivingEntity) entity).getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS) : false);
    }

    @Override
    public ItemStack pickupBlock(@Nullable Player player, LevelAccessor world, BlockPos pos, BlockState state) {
        world.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
        if (!world.isClientSide()) {
            world.levelEvent(2001, pos, Block.getId(state));
        }

        return new ItemStack(Items.POWDER_SNOW_BUCKET);
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL_POWDER_SNOW);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter world, BlockPos pos, PathComputationType type) {
        return true;
    }
}
