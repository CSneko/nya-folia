package net.minecraft.world.entity;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class LightningBolt extends Entity {

    private static final int START_LIFE = 2;
    private static final double DAMAGE_RADIUS = 3.0D;
    private static final double DETECTION_RADIUS = 15.0D;
    public int life; // PAIL private -> public
    public long seed;
    public int flashes; // PAIL private -> public
    public boolean visualOnly;
    @Nullable
    private ServerPlayer cause;
    private final Set<Entity> hitEntities = Sets.newHashSet();
    private int blocksSetOnFire;
    public boolean isEffect; // Paper

    public LightningBolt(EntityType<? extends LightningBolt> type, Level world) {
        super(type, world);
        this.noCulling = true;
        this.life = 2;
        this.seed = this.random.nextLong();
        this.flashes = this.random.nextInt(3) + 1;
    }

    public void setVisualOnly(boolean cosmetic) {
        this.visualOnly = cosmetic;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.WEATHER;
    }

    @Nullable
    public ServerPlayer getCause() {
        return this.cause;
    }

    public void setCause(@Nullable ServerPlayer channeler) {
        this.cause = channeler;
    }

    private void powerLightningRod() {
        BlockPos blockposition = this.getStrikePosition();
        BlockState iblockdata = this.level().getBlockState(blockposition);

        if (iblockdata.is(Blocks.LIGHTNING_ROD)) {
            ((LightningRodBlock) iblockdata.getBlock()).onLightningStrike(iblockdata, this.level(), blockposition);
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isEffect && this.life == 2) { // Spigot // Paper
            if (this.level().isClientSide()) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 10000.0F, 0.8F + this.random.nextFloat() * 0.2F, false);
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 2.0F, 0.5F + this.random.nextFloat() * 0.2F, false);
            } else {
                Difficulty enumdifficulty = this.level().getDifficulty();

                if (enumdifficulty == Difficulty.NORMAL || enumdifficulty == Difficulty.HARD) {
                    this.spawnFire(4);
                }

                this.powerLightningRod();
                LightningBolt.clearCopperOnLightningStrike(this.level(), this.getStrikePosition(), this); // Paper - transmit LightningBolt instance to call EntityChangeBlockEvent
                this.gameEvent(GameEvent.LIGHTNING_STRIKE);
            }
        }

        --this.life;
        List list;
        Iterator iterator;

        if (this.life < 0) {
            if (this.flashes == 0) {
                if (this.level() instanceof ServerLevel) {
                    list = this.level().getEntities((Entity) this, new AABB(this.getX() - 15.0D, this.getY() - 15.0D, this.getZ() - 15.0D, this.getX() + 15.0D, this.getY() + 6.0D + 15.0D, this.getZ() + 15.0D), (entity) -> {
                        return entity.isAlive() && !this.hitEntities.contains(entity);
                    });
                    iterator = ((ServerLevel) this.level()).getPlayers((entityplayer) -> {
                        return entityplayer.distanceTo(this) < 256.0F;
                    }).iterator();

                    while (iterator.hasNext()) {
                        ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                        CriteriaTriggers.LIGHTNING_STRIKE.trigger(entityplayer, this, list);
                    }
                }

                this.discard();
            } else if (this.life < -this.random.nextInt(10)) {
                --this.flashes;
                this.life = 1;
                this.seed = this.random.nextLong();
                this.spawnFire(0);
            }
        }

        if (this.life >= 0 && !this.isEffect) { // CraftBukkit - add !this.visualOnly // Paper - undo
            if (!(this.level() instanceof ServerLevel)) {
                this.level().setSkyFlashTime(2);
            } else if (!this.visualOnly) {
                list = this.level().getEntities((Entity) this, new AABB(this.getX() - 3.0D, this.getY() - 3.0D, this.getZ() - 3.0D, this.getX() + 3.0D, this.getY() + 6.0D + 3.0D, this.getZ() + 3.0D), Entity::isAlive);
                iterator = list.iterator();

                while (iterator.hasNext()) {
                    Entity entity = (Entity) iterator.next();

                    entity.thunderHit((ServerLevel) this.level(), this);
                }

                this.hitEntities.addAll(list);
                if (this.cause != null) {
                    CriteriaTriggers.CHANNELED_LIGHTNING.trigger(this.cause, (Collection) list);
                }
            }
        }

    }

    private BlockPos getStrikePosition() {
        Vec3 vec3d = this.position();

        return BlockPos.containing(vec3d.x, vec3d.y - 1.0E-6D, vec3d.z);
    }

    private void spawnFire(int spreadAttempts) {
        if (!this.visualOnly && !this.isEffect && !this.level().isClientSide && this.level().getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) { // Paper
            BlockPos blockposition = this.blockPosition();
            BlockState iblockdata = BaseFireBlock.getState(this.level(), blockposition);

            if (this.level().getBlockState(blockposition).isAir() && iblockdata.canSurvive(this.level(), blockposition)) {
                // CraftBukkit start - add "!visualOnly"
                if (!this.visualOnly && !CraftEventFactory.callBlockIgniteEvent(this.level(), blockposition, this).isCancelled()) {
                    this.level().setBlockAndUpdate(blockposition, iblockdata);
                    ++this.blocksSetOnFire;
                }
                // CraftBukkit end
            }

            for (int j = 0; j < spreadAttempts; ++j) {
                BlockPos blockposition1 = blockposition.offset(this.random.nextInt(3) - 1, this.random.nextInt(3) - 1, this.random.nextInt(3) - 1);

                iblockdata = BaseFireBlock.getState(this.level(), blockposition1);
                if (this.level().getBlockState(blockposition1).isAir() && iblockdata.canSurvive(this.level(), blockposition1)) {
                    // CraftBukkit start - add "!visualOnly"
                    if (!this.visualOnly && !CraftEventFactory.callBlockIgniteEvent(this.level(), blockposition1, this).isCancelled()) {
                        this.level().setBlockAndUpdate(blockposition1, iblockdata);
                        ++this.blocksSetOnFire;
                    }
                    // CraftBukkit end
                }
            }

        }
    }

    private static void clearCopperOnLightningStrike(Level world, BlockPos pos, Entity lightning) { // Paper - transmit LightningBolt instance to call EntityChangeBlockEvent
        BlockState iblockdata = world.getBlockState(pos);
        BlockPos blockposition1;
        BlockState iblockdata1;

        if (iblockdata.is(Blocks.LIGHTNING_ROD)) {
            blockposition1 = pos.relative(((Direction) iblockdata.getValue(LightningRodBlock.FACING)).getOpposite());
            iblockdata1 = world.getBlockState(blockposition1);
        } else {
            blockposition1 = pos;
            iblockdata1 = iblockdata;
        }

        if (iblockdata1.getBlock() instanceof WeatheringCopper) {
            // Paper start - call EntityChangeBlockEvent
            BlockState newBlock = WeatheringCopper.getFirst(world.getBlockState(blockposition1));
            if (CraftEventFactory.callEntityChangeBlockEvent(lightning, blockposition1, newBlock)) {
                world.setBlockAndUpdate(blockposition1, newBlock);
            }
            // Paper end
            BlockPos.MutableBlockPos blockposition_mutableblockposition = pos.mutable();
            int i = world.random.nextInt(3) + 3;

            for (int j = 0; j < i; ++j) {
                int k = world.random.nextInt(8) + 1;

                LightningBolt.randomWalkCleaningCopper(world, blockposition1, blockposition_mutableblockposition, k, lightning); // Paper - transmit LightningBolt instance to call EntityChangeBlockEvent
            }

        }
    }

    private static void randomWalkCleaningCopper(Level world, BlockPos pos, BlockPos.MutableBlockPos mutablePos, int count, Entity lightning) { // Paper - transmit LightningBolt instance to call EntityChangeBlockEvent
        mutablePos.set(pos);

        for (int j = 0; j < count; ++j) {
            Optional<BlockPos> optional = LightningBolt.randomStepCleaningCopper(world, mutablePos, lightning); // Paper - transmit LightningBolt instance to call EntityChangeBlockEvent

            if (optional.isEmpty()) {
                break;
            }

            mutablePos.set((Vec3i) optional.get());
        }

    }

    private static Optional<BlockPos> randomStepCleaningCopper(Level world, BlockPos pos, Entity lightning) { // Paper - transmit LightningBolt instance to call EntityChangeBlockEvent
        Iterator iterator = BlockPos.randomInCube(world.random, 10, pos, 1).iterator();

        BlockPos blockposition1;
        BlockState iblockdata;

        do {
            if (!iterator.hasNext()) {
                return Optional.empty();
            }

            blockposition1 = (BlockPos) iterator.next();
            iblockdata = world.getBlockState(blockposition1);
        } while (!(iblockdata.getBlock() instanceof WeatheringCopper));

        BlockPos blockposition1Final = blockposition1; // CraftBukkit - decompile error
        WeatheringCopper.getPrevious(iblockdata).ifPresent((iblockdata1) -> {
            if (CraftEventFactory.callEntityChangeBlockEvent(lightning, blockposition1Final, iblockdata1)) // Paper - call EntityChangeBlockEvent
            world.setBlockAndUpdate(blockposition1Final, iblockdata1); // CraftBukkit - decompile error
        });
        world.levelEvent(3002, blockposition1, -1);
        return Optional.of(blockposition1);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = 64.0D * getViewScale();

        return distance < d1 * d1;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {}

    public int getBlocksSetOnFire() {
        return this.blocksSetOnFire;
    }

    public Stream<Entity> getHitEntities() {
        return this.hitEntities.stream().filter(Entity::isAlive);
    }
}
