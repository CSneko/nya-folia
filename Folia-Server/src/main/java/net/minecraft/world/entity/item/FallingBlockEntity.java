package net.minecraft.world.entity.item;

import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

// CraftBukkit start;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class FallingBlockEntity extends Entity {

    private static final Logger LOGGER = LogUtils.getLogger();
    public BlockState blockState;
    public int time;
    public boolean dropItem;
    public boolean cancelDrop;
    public boolean hurtEntities;
    public int fallDamageMax;
    public float fallDamagePerDistance;
    @Nullable
    public CompoundTag blockData;
    protected static final EntityDataAccessor<BlockPos> DATA_START_POS = SynchedEntityData.defineId(FallingBlockEntity.class, EntityDataSerializers.BLOCK_POS);
    public boolean autoExpire = true; // Paper - Auto expire setting

    public FallingBlockEntity(EntityType<? extends FallingBlockEntity> type, Level world) {
        super(type, world);
        this.blockState = Blocks.SAND.defaultBlockState();
        this.dropItem = true;
        this.fallDamageMax = 40;
    }

    public FallingBlockEntity(Level world, double x, double y, double z, BlockState block) {
        this(EntityType.FALLING_BLOCK, world);
        this.blockState = block;
        this.blocksBuilding = true;
        this.setPos(x, y, z);
        this.setDeltaMovement(Vec3.ZERO);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.setStartPos(this.blockPosition());
    }

    public static FallingBlockEntity fall(Level world, BlockPos pos, BlockState state) {
        // CraftBukkit start
        return FallingBlockEntity.fall(world, pos, state, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    public static FallingBlockEntity fall(Level world, BlockPos blockposition, BlockState iblockdata, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason spawnReason) {
        // CraftBukkit end
        FallingBlockEntity entityfallingblock = new FallingBlockEntity(world, (double) blockposition.getX() + 0.5D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.5D, iblockdata.hasProperty(BlockStateProperties.WATERLOGGED) ? (BlockState) iblockdata.setValue(BlockStateProperties.WATERLOGGED, false) : iblockdata);
        if (!CraftEventFactory.callEntityChangeBlockEvent(entityfallingblock, blockposition, iblockdata.getFluidState().createLegacyBlock())) return entityfallingblock; // CraftBukkit

        world.setBlock(blockposition, iblockdata.getFluidState().createLegacyBlock(), 3);
        world.addFreshEntity(entityfallingblock, spawnReason); // CraftBukkit
        return entityfallingblock;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    public void setStartPos(BlockPos pos) {
        this.entityData.set(FallingBlockEntity.DATA_START_POS, pos);
    }

    public BlockPos getStartPos() {
        return (BlockPos) this.entityData.get(FallingBlockEntity.DATA_START_POS);
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(FallingBlockEntity.DATA_START_POS, BlockPos.ZERO);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public void tick() {
        // Paper start - fix sand duping
        if (this.isRemoved()) {
            return;
        }
        // Paper end - fix sand duping
        if (this.blockState.isAir()) {
            this.discard();
        } else {
            Block block = this.blockState.getBlock();

            ++this.time;
            if (!this.isNoGravity()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
            }

            this.move(MoverType.SELF, this.getDeltaMovement());

            // Paper start - fix sand duping
            if (this.isRemoved()) {
                return;
            }
            // Paper end - fix sand duping

            // Paper start - Configurable EntityFallingBlock height nerf
            if (this.level().paperConfig().fixes.fallingBlockHeightNerf.test(v -> this.getY() > v)) {
                if (this.dropItem && this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                    this.spawnAtLocation(block);
                }

                this.discard();
                return;
            }
            // Paper end
            if (!this.level().isClientSide) {
                BlockPos blockposition = this.blockPosition();
                boolean flag = this.blockState.getBlock() instanceof ConcretePowderBlock;
                boolean flag1 = flag && this.level().getFluidState(blockposition).is(FluidTags.WATER);
                double d0 = this.getDeltaMovement().lengthSqr();

                if (flag && d0 > 1.0D) {
                    BlockHitResult movingobjectpositionblock = this.level().clip(new ClipContext(new Vec3(this.xo, this.yo, this.zo), this.position(), ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, this));

                    if (movingobjectpositionblock.getType() != HitResult.Type.MISS && this.level().getFluidState(movingobjectpositionblock.getBlockPos()).is(FluidTags.WATER)) {
                        blockposition = movingobjectpositionblock.getBlockPos();
                        flag1 = true;
                    }
                }

                if (!this.onGround() && !flag1) {
                    if (!this.level().isClientSide && ((this.time > 100 && autoExpire) && (blockposition.getY() <= this.level().getMinBuildHeight() || blockposition.getY() > this.level().getMaxBuildHeight()) || (this.time > 600 && autoExpire))) { // Paper - Auto expire setting
                        if (this.dropItem && this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                            this.spawnAtLocation((ItemLike) block);
                        }

                        this.discard();
                    }
                } else {
                    BlockState iblockdata = this.level().getBlockState(blockposition);

                    this.setDeltaMovement(this.getDeltaMovement().multiply(0.7D, -0.5D, 0.7D));
                    if (!iblockdata.is(Blocks.MOVING_PISTON)) {
                        if (!this.cancelDrop) {
                            boolean flag2 = iblockdata.canBeReplaced((BlockPlaceContext) (new DirectionalPlaceContext(this.level(), blockposition, Direction.DOWN, ItemStack.EMPTY, Direction.UP)));
                            boolean flag3 = FallingBlock.isFree(this.level().getBlockState(blockposition.below())) && (!flag || !flag1);
                            boolean flag4 = this.blockState.canSurvive(this.level(), blockposition) && !flag3;

                            if (flag2 && flag4) {
                                if (this.blockState.hasProperty(BlockStateProperties.WATERLOGGED) && this.level().getFluidState(blockposition).getType() == Fluids.WATER) {
                                    this.blockState = (BlockState) this.blockState.setValue(BlockStateProperties.WATERLOGGED, true);
                                }

                                // CraftBukkit start
                                if (!CraftEventFactory.callEntityChangeBlockEvent(this, blockposition, this.blockState)) {
                                    this.discard(); // SPIGOT-6586 called before the event in previous versions
                                    return;
                                }
                                // CraftBukkit end
                                if (this.level().setBlock(blockposition, this.blockState, 3)) {
                                    ((ServerLevel) this.level()).getChunkSource().chunkMap.broadcast(this, new ClientboundBlockUpdatePacket(blockposition, this.level().getBlockState(blockposition)));
                                    this.discard();
                                    if (block instanceof Fallable) {
                                        ((Fallable) block).onLand(this.level(), blockposition, this.blockState, iblockdata, this);
                                    }

                                    if (this.blockData != null && this.blockState.hasBlockEntity()) {
                                        BlockEntity tileentity = this.level().getBlockEntity(blockposition);

                                        if (tileentity != null) {
                                            CompoundTag nbttagcompound = tileentity.saveWithoutMetadata();
                                            Iterator iterator = this.blockData.getAllKeys().iterator();

                                            while (iterator.hasNext()) {
                                                String s = (String) iterator.next();

                                                nbttagcompound.put(s, this.blockData.get(s).copy());
                                            }

                                            try {
                                                tileentity.load(nbttagcompound);
                                            } catch (Exception exception) {
                                                FallingBlockEntity.LOGGER.error("Failed to load block entity from falling block", exception);
                                            }

                                            tileentity.setChanged();
                                        }
                                    }
                                } else if (this.dropItem && this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                                    this.discard();
                                    this.callOnBrokenAfterFall(block, blockposition);
                                    this.spawnAtLocation((ItemLike) block);
                                }
                            } else {
                                this.discard();
                                if (this.dropItem && this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                                    this.callOnBrokenAfterFall(block, blockposition);
                                    this.spawnAtLocation((ItemLike) block);
                                }
                            }
                        } else {
                            this.discard();
                            this.callOnBrokenAfterFall(block, blockposition);
                        }
                    }
                }
            }

            this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));
        }
    }

    public void callOnBrokenAfterFall(Block block, BlockPos pos) {
        if (block instanceof Fallable) {
            ((Fallable) block).onBrokenAfterFall(this.level(), pos, this);
        }

    }

    @Override
    public boolean causeFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        if (!this.hurtEntities) {
            return false;
        } else {
            int i = Mth.ceil(fallDistance - 1.0F);

            if (i < 0) {
                return false;
            } else {
                Predicate<Entity> predicate = EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(EntitySelector.LIVING_ENTITY_STILL_ALIVE);
                Block block = this.blockState.getBlock();
                DamageSource damagesource1;

                if (block instanceof Fallable) {
                    Fallable fallable = (Fallable) block;

                    damagesource1 = fallable.getFallDamageSource(this);
                } else {
                    damagesource1 = this.damageSources().fallingBlock(this);
                }

                DamageSource damagesource2 = damagesource1;
                float f2 = (float) Math.min(Mth.floor((float) i * this.fallDamagePerDistance), this.fallDamageMax);

                this.level().getEntities((Entity) this, this.getBoundingBox(), predicate).forEach((entity) -> {
                    CraftEventFactory.entityDamageRT.set(this); // CraftBukkit // Folia - region threading
                    entity.hurt(damagesource2, f2);
                    CraftEventFactory.entityDamageRT.set(null); // CraftBukkit // Folia - region threading
                });
                boolean flag = this.blockState.is(BlockTags.ANVIL);

                if (flag && f2 > 0.0F && this.random.nextFloat() < 0.05F + (float) i * 0.05F) {
                    BlockState iblockdata = AnvilBlock.damage(this.blockState);

                    if (iblockdata == null) {
                        this.cancelDrop = true;
                    } else {
                        this.blockState = iblockdata;
                    }
                }

                return false;
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        nbt.put("BlockState", NbtUtils.writeBlockState(this.blockState));
        nbt.putInt("Time", this.time);
        nbt.putBoolean("DropItem", this.dropItem);
        nbt.putBoolean("HurtEntities", this.hurtEntities);
        nbt.putFloat("FallHurtAmount", this.fallDamagePerDistance);
        nbt.putInt("FallHurtMax", this.fallDamageMax);
        if (this.blockData != null) {
            nbt.put("TileEntityData", this.blockData);
        }

        nbt.putBoolean("CancelDrop", this.cancelDrop);
        if (!autoExpire) {nbt.putBoolean("Paper.AutoExpire", false);} // Paper - AutoExpire setting
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        this.blockState = NbtUtils.readBlockState(this.level().holderLookup(Registries.BLOCK), nbt.getCompound("BlockState"));
        this.time = nbt.getInt("Time");
        if (nbt.contains("HurtEntities", 99)) {
            this.hurtEntities = nbt.getBoolean("HurtEntities");
            this.fallDamagePerDistance = nbt.getFloat("FallHurtAmount");
            this.fallDamageMax = nbt.getInt("FallHurtMax");
        } else if (this.blockState.is(BlockTags.ANVIL)) {
            this.hurtEntities = true;
        }

        if (nbt.contains("DropItem", 99)) {
            this.dropItem = nbt.getBoolean("DropItem");
        }

        if (nbt.contains("TileEntityData", 10) && !(this.level().paperConfig().entities.spawning.filterBadTileEntityNbtFromFallingBlocks && this.blockState.getBlock() instanceof net.minecraft.world.level.block.GameMasterBlock)) {
            this.blockData = nbt.getCompound("TileEntityData");
        }

        this.cancelDrop = nbt.getBoolean("CancelDrop");
        if (this.blockState.isAir()) {
            this.blockState = Blocks.SAND.defaultBlockState();
        }

        // Paper start - Try and load origin location from the old NBT tags for backwards compatibility
        if (nbt.contains("SourceLoc_x")) {
            int srcX = nbt.getInt("SourceLoc_x");
            int srcY = nbt.getInt("SourceLoc_y");
            int srcZ = nbt.getInt("SourceLoc_z");
            this.setOrigin(new org.bukkit.Location(this.level().getWorld(), srcX, srcY, srcZ));
        }
        // Paper end
        // Paper start
         if (nbt.contains("Paper.AutoExpire")) {
            this.autoExpire = nbt.getBoolean("Paper.AutoExpire");
         }
        // Paper end
    }

    public void setHurtsEntities(float fallHurtAmount, int fallHurtMax) {
        this.hurtEntities = true;
        this.fallDamagePerDistance = fallHurtAmount;
        this.fallDamageMax = fallHurtMax;
    }

    public void disableDrop() {
        this.cancelDrop = true;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public void fillCrashReportCategory(CrashReportCategory section) {
        super.fillCrashReportCategory(section);
        section.setDetail("Immitating BlockState", (Object) this.blockState.toString());
    }

    public BlockState getBlockState() {
        return this.blockState;
    }

    @Override
    protected Component getTypeName() {
        return Component.translatable("entity.minecraft.falling_block_type", this.blockState.getBlock().getName());
    }

    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this, Block.getId(this.getBlockState()));
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.blockState = Block.stateById(packet.getData());
        this.blocksBuilding = true;
        double d0 = packet.getX();
        double d1 = packet.getY();
        double d2 = packet.getZ();

        this.setPos(d0, d1, d2);
        this.setStartPos(this.blockPosition());
    }
}
