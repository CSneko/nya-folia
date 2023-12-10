package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class ConduitBlockEntity extends BlockEntity {

    private static final int BLOCK_REFRESH_RATE = 2;
    private static final int EFFECT_DURATION = 13;
    private static final float ROTATION_SPEED = -0.0375F;
    private static final int MIN_ACTIVE_SIZE = 16;
    private static final int MIN_KILL_SIZE = 42;
    private static final int KILL_RANGE = 8;
    private static final Block[] VALID_BLOCKS = new Block[]{Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN, Blocks.DARK_PRISMARINE};
    public int tickCount;
    private float activeRotation;
    private boolean isActive;
    private boolean isHunting;
    private final List<BlockPos> effectBlocks = Lists.newArrayList();
    @Nullable
    private LivingEntity destroyTarget;
    @Nullable
    private UUID destroyTargetUUID;
    private long nextAmbientSoundActivation;

    public ConduitBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.CONDUIT, pos, state);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        if (nbt.hasUUID("Target")) {
            this.destroyTargetUUID = nbt.getUUID("Target");
        } else {
            this.destroyTargetUUID = null;
        }

    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        if (this.destroyTarget != null) {
            nbt.putUUID("Target", this.destroyTarget.getUUID());
        }

    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    public static void clientTick(Level world, BlockPos pos, BlockState state, ConduitBlockEntity blockEntity) {
        ++blockEntity.tickCount;
        long i = world.getRedstoneGameTime(); // Folia - region threading
        List<BlockPos> list = blockEntity.effectBlocks;

        if (i % 40L == 0L) {
            blockEntity.isActive = ConduitBlockEntity.updateShape(world, pos, list);
            ConduitBlockEntity.updateHunting(blockEntity, list);
        }

        ConduitBlockEntity.updateClientTarget(world, pos, blockEntity);
        ConduitBlockEntity.animationTick(world, pos, list, blockEntity.destroyTarget, blockEntity.tickCount);
        if (blockEntity.isActive()) {
            ++blockEntity.activeRotation;
        }

    }

    public static void serverTick(Level world, BlockPos pos, BlockState state, ConduitBlockEntity blockEntity) {
        ++blockEntity.tickCount;
        long i = world.getRedstoneGameTime(); // Folia - region threading
        List<BlockPos> list = blockEntity.effectBlocks;

        if (i % 40L == 0L) {
            boolean flag = ConduitBlockEntity.updateShape(world, pos, list);

            if (flag != blockEntity.isActive) {
                SoundEvent soundeffect = flag ? SoundEvents.CONDUIT_ACTIVATE : SoundEvents.CONDUIT_DEACTIVATE;

                world.playSound((Player) null, pos, soundeffect, SoundSource.BLOCKS, 1.0F, 1.0F);
            }

            blockEntity.isActive = flag;
            ConduitBlockEntity.updateHunting(blockEntity, list);
            if (flag) {
                ConduitBlockEntity.applyEffects(world, pos, list);
                ConduitBlockEntity.updateDestroyTarget(world, pos, state, list, blockEntity);
            }
        }

        if (blockEntity.isActive()) {
            if (i % 80L == 0L) {
                world.playSound((Player) null, pos, SoundEvents.CONDUIT_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F);
            }

            if (i > blockEntity.nextAmbientSoundActivation) {
                blockEntity.nextAmbientSoundActivation = i + 60L + (long) world.getRandom().nextInt(40);
                world.playSound((Player) null, pos, SoundEvents.CONDUIT_AMBIENT_SHORT, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }

    }

    private static void updateHunting(ConduitBlockEntity blockEntity, List<BlockPos> activatingBlocks) {
        blockEntity.setHunting(activatingBlocks.size() >= 42);
    }

    private static boolean updateShape(Level world, BlockPos pos, List<BlockPos> activatingBlocks) {
        activatingBlocks.clear();

        int i;
        int j;
        int k;

        for (i = -1; i <= 1; ++i) {
            for (j = -1; j <= 1; ++j) {
                for (k = -1; k <= 1; ++k) {
                    BlockPos blockposition1 = pos.offset(i, j, k);

                    if (!world.isWaterAt(blockposition1)) {
                        return false;
                    }
                }
            }
        }

        for (i = -2; i <= 2; ++i) {
            for (j = -2; j <= 2; ++j) {
                for (k = -2; k <= 2; ++k) {
                    int l = Math.abs(i);
                    int i1 = Math.abs(j);
                    int j1 = Math.abs(k);

                    if ((l > 1 || i1 > 1 || j1 > 1) && (i == 0 && (i1 == 2 || j1 == 2) || j == 0 && (l == 2 || j1 == 2) || k == 0 && (l == 2 || i1 == 2))) {
                        BlockPos blockposition2 = pos.offset(i, j, k);
                        BlockState iblockdata = world.getBlockState(blockposition2);
                        Block[] ablock = ConduitBlockEntity.VALID_BLOCKS;
                        int k1 = ablock.length;

                        for (int l1 = 0; l1 < k1; ++l1) {
                            Block block = ablock[l1];

                            if (iblockdata.is(block)) {
                                activatingBlocks.add(blockposition2);
                            }
                        }
                    }
                }
            }
        }

        return activatingBlocks.size() >= 16;
    }

    private static void applyEffects(Level world, BlockPos pos, List<BlockPos> activatingBlocks) {
        int i = activatingBlocks.size();
        int j = i / 7 * 16;
        int k = pos.getX();
        int l = pos.getY();
        int i1 = pos.getZ();
        AABB axisalignedbb = (new AABB((double) k, (double) l, (double) i1, (double) (k + 1), (double) (l + 1), (double) (i1 + 1))).inflate((double) j).expandTowards(0.0D, (double) world.getHeight(), 0.0D);
        List<Player> list1 = world.getEntitiesOfClass(Player.class, axisalignedbb);

        if (!list1.isEmpty()) {
            Iterator iterator = list1.iterator();

            while (iterator.hasNext()) {
                Player entityhuman = (Player) iterator.next();

                if (pos.closerThan(entityhuman.blockPosition(), (double) j) && entityhuman.isInWaterOrRain()) {
                    entityhuman.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 260, 0, true, true), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.CONDUIT); // CraftBukkit
                }
            }

        }
    }

    private static void updateDestroyTarget(Level world, BlockPos pos, BlockState state, List<BlockPos> activatingBlocks, ConduitBlockEntity blockEntity) {
        LivingEntity entityliving = blockEntity.destroyTarget;
        int i = activatingBlocks.size();

        if (i < 42) {
            blockEntity.destroyTarget = null;
        } else if (blockEntity.destroyTarget == null && blockEntity.destroyTargetUUID != null) {
            blockEntity.destroyTarget = ConduitBlockEntity.findDestroyTarget(world, pos, blockEntity.destroyTargetUUID);
            blockEntity.destroyTargetUUID = null;
        } else if (blockEntity.destroyTarget == null) {
            List<LivingEntity> list1 = world.getEntitiesOfClass(LivingEntity.class, ConduitBlockEntity.getDestroyRangeAABB(pos), (entityliving1) -> {
                return entityliving1 instanceof Enemy && entityliving1.isInWaterOrRain();
            });

            if (!list1.isEmpty()) {
                blockEntity.destroyTarget = (LivingEntity) list1.get(world.random.nextInt(list1.size()));
            }
        } else if (!blockEntity.destroyTarget.isAlive() || !pos.closerThan(blockEntity.destroyTarget.blockPosition(), 8.0D)) {
            blockEntity.destroyTarget = null;
        }

        if (blockEntity.destroyTarget != null) {
            // CraftBukkit start
            CraftEventFactory.blockDamageRT.set(CraftBlock.at(world, pos)); // Folia - region threading
            if (blockEntity.destroyTarget.hurt(world.damageSources().magic(), 4.0F)) {
                world.playSound((Player) null, blockEntity.destroyTarget.getX(), blockEntity.destroyTarget.getY(), blockEntity.destroyTarget.getZ(), SoundEvents.CONDUIT_ATTACK_TARGET, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            CraftEventFactory.blockDamageRT.set(null); // Folia - region threading
            // CraftBukkit end
        }

        if (entityliving != blockEntity.destroyTarget) {
            world.sendBlockUpdated(pos, state, state, 2);
        }

    }

    private static void updateClientTarget(Level world, BlockPos pos, ConduitBlockEntity blockEntity) {
        if (blockEntity.destroyTargetUUID == null) {
            blockEntity.destroyTarget = null;
        } else if (blockEntity.destroyTarget == null || !blockEntity.destroyTarget.getUUID().equals(blockEntity.destroyTargetUUID)) {
            blockEntity.destroyTarget = ConduitBlockEntity.findDestroyTarget(world, pos, blockEntity.destroyTargetUUID);
            if (blockEntity.destroyTarget == null) {
                blockEntity.destroyTargetUUID = null;
            }
        }

    }

    private static AABB getDestroyRangeAABB(BlockPos pos) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();

        return (new AABB((double) i, (double) j, (double) k, (double) (i + 1), (double) (j + 1), (double) (k + 1))).inflate(8.0D);
    }

    @Nullable
    private static LivingEntity findDestroyTarget(Level world, BlockPos pos, UUID uuid) {
        List<LivingEntity> list = world.getEntitiesOfClass(LivingEntity.class, ConduitBlockEntity.getDestroyRangeAABB(pos), (entityliving) -> {
            return entityliving.getUUID().equals(uuid);
        });

        return list.size() == 1 ? (LivingEntity) list.get(0) : null;
    }

    private static void animationTick(Level world, BlockPos pos, List<BlockPos> activatingBlocks, @Nullable Entity entity, int ticks) {
        RandomSource randomsource = world.random;
        double d0 = (double) (Mth.sin((float) (ticks + 35) * 0.1F) / 2.0F + 0.5F);

        d0 = (d0 * d0 + d0) * 0.30000001192092896D;
        Vec3 vec3d = new Vec3((double) pos.getX() + 0.5D, (double) pos.getY() + 1.5D + d0, (double) pos.getZ() + 0.5D);
        Iterator iterator = activatingBlocks.iterator();

        float f;

        while (iterator.hasNext()) {
            BlockPos blockposition1 = (BlockPos) iterator.next();

            if (randomsource.nextInt(50) == 0) {
                BlockPos blockposition2 = blockposition1.subtract(pos);

                f = -0.5F + randomsource.nextFloat() + (float) blockposition2.getX();
                float f1 = -2.0F + randomsource.nextFloat() + (float) blockposition2.getY();
                float f2 = -0.5F + randomsource.nextFloat() + (float) blockposition2.getZ();

                world.addParticle(ParticleTypes.NAUTILUS, vec3d.x, vec3d.y, vec3d.z, (double) f, (double) f1, (double) f2);
            }
        }

        if (entity != null) {
            Vec3 vec3d1 = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());
            float f3 = (-0.5F + randomsource.nextFloat()) * (3.0F + entity.getBbWidth());
            float f4 = -1.0F + randomsource.nextFloat() * entity.getBbHeight();

            f = (-0.5F + randomsource.nextFloat()) * (3.0F + entity.getBbWidth());
            Vec3 vec3d2 = new Vec3((double) f3, (double) f4, (double) f);

            world.addParticle(ParticleTypes.NAUTILUS, vec3d1.x, vec3d1.y, vec3d1.z, vec3d2.x, vec3d2.y, vec3d2.z);
        }

    }

    public boolean isActive() {
        return this.isActive;
    }

    public boolean isHunting() {
        return this.isHunting;
    }

    private void setHunting(boolean eyeOpen) {
        this.isHunting = eyeOpen;
    }

    public float getActiveRotation(float tickDelta) {
        return (this.activeRotation + tickDelta) * -0.0375F;
    }
}
