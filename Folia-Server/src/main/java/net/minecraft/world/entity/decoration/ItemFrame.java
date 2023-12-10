package net.minecraft.world.entity.decoration;

import com.mojang.logging.LogUtils;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent; // Paper
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;

public class ItemFrame extends HangingEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(ItemFrame.class, EntityDataSerializers.ITEM_STACK);
    public static final EntityDataAccessor<Integer> DATA_ROTATION = SynchedEntityData.defineId(ItemFrame.class, EntityDataSerializers.INT);
    public static final int NUM_ROTATIONS = 8;
    public float dropChance;
    public boolean fixed;
    public Integer cachedMapId; // Paper

    public ItemFrame(EntityType<? extends ItemFrame> type, Level world) {
        super(type, world);
        this.dropChance = 1.0F;
    }

    public ItemFrame(Level world, BlockPos pos, Direction facing) {
        this(EntityType.ITEM_FRAME, world, pos, facing);
    }

    public ItemFrame(EntityType<? extends ItemFrame> type, Level world, BlockPos pos, Direction facing) {
        super(type, world, pos);
        this.dropChance = 1.0F;
        this.setDirection(facing);
    }

    @Override
    protected float getEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 0.0F;
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(ItemFrame.DATA_ITEM, ItemStack.EMPTY);
        this.getEntityData().define(ItemFrame.DATA_ROTATION, 0);
    }

    @Override
    public void setDirection(Direction facing) {
        Validate.notNull(facing);
        this.direction = facing;
        if (facing.getAxis().isHorizontal()) {
            this.setXRot(0.0F);
            this.setYRot((float) (this.direction.get2DDataValue() * 90));
        } else {
            this.setXRot((float) (-90 * facing.getAxisDirection().getStep()));
            this.setYRot(0.0F);
        }

        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
        this.recalculateBoundingBox();
    }

    @Override
    protected void recalculateBoundingBox() {
        if (this.direction != null) {
            // CraftBukkit start code moved in to calculateBoundingBox
            this.setBoundingBox(ItemFrame.calculateBoundingBox(this, this.pos, this.direction, this.getWidth(), this.getHeight()));
            // CraftBukkit end
        }
    }

    // CraftBukkit start - break out BB calc into own method
    public static AABB calculateBoundingBox(@Nullable Entity entity, BlockPos blockPosition, Direction direction, int width, int height) {
        {
            double d0 = 0.46875D;
            double d1 = (double) blockPosition.getX() + 0.5D - (double) direction.getStepX() * 0.46875D;
            double d2 = (double) blockPosition.getY() + 0.5D - (double) direction.getStepY() * 0.46875D;
            double d3 = (double) blockPosition.getZ() + 0.5D - (double) direction.getStepZ() * 0.46875D;

            if (entity != null) {
                entity.setPosRaw(d1, d2, d3);
            }
            double d4 = (double) width;
            double d5 = (double) height;
            double d6 = (double) width;
            Direction.Axis enumdirection_enumaxis = direction.getAxis();

            switch (enumdirection_enumaxis) {
                case X:
                    d4 = 1.0D;
                    break;
                case Y:
                    d5 = 1.0D;
                    break;
                case Z:
                    d6 = 1.0D;
            }

            d4 /= 32.0D;
            d5 /= 32.0D;
            d6 /= 32.0D;
            return new AABB(d1 - d4, d2 - d5, d3 - d6, d1 + d4, d2 + d5, d3 + d6);
        }
    }
    // CraftBukkit end

    @Override
    public boolean survives() {
        if (this.fixed) {
            return true;
        } else if (!this.level().noCollision((Entity) this)) {
            return false;
        } else {
            BlockState iblockdata = this.level().getBlockState(this.pos.relative(this.direction.getOpposite()));

            return !iblockdata.isSolid() && (!this.direction.getAxis().isHorizontal() || !DiodeBlock.isDiode(iblockdata)) ? false : this.level().getEntities((Entity) this, this.getBoundingBox(), ItemFrame.HANGING_ENTITY).isEmpty();
        }
    }

    @Override
    public void move(MoverType movementType, Vec3 movement) {
        if (!this.fixed) {
            super.move(movementType, movement);
        }

    }

    @Override
    public void push(double deltaX, double deltaY, double deltaZ) {
        if (!this.fixed) {
            super.push(deltaX, deltaY, deltaZ);
        }

    }

    @Override
    public float getPickRadius() {
        return 0.0F;
    }

    @Override
    public void kill() {
        this.removeFramedMap(this.getItem());
        super.kill();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.fixed) {
            return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.isCreativePlayer() ? false : super.hurt(source, amount);
        } else if (this.isInvulnerableTo(source)) {
            return false;
        } else if (!source.is(DamageTypeTags.IS_EXPLOSION) && !this.getItem().isEmpty()) {
            if (!this.level().isClientSide) {
                // CraftBukkit start - fire EntityDamageEvent
                if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, source, amount, false) || this.isRemoved()) {
                    return true;
                }
                // CraftBukkit end
                // Paper start - call PlayerItemFrameChangeEvent
                if (source.getEntity() instanceof Player player) {
                    var event = new PlayerItemFrameChangeEvent((org.bukkit.entity.Player) player.getBukkitEntity(), (org.bukkit.entity.ItemFrame) this.getBukkitEntity(), this.getItem().asBukkitCopy(), PlayerItemFrameChangeEvent.ItemFrameChangeAction.REMOVE);
                    if (!event.callEvent()) return true; // return true here because you aren't cancelling the damage, just the change
                    this.setItem(ItemStack.fromBukkitCopy(event.getItemStack()), false);
                }
                // Paper end
                this.dropItem(source.getEntity(), false);
                this.gameEvent(GameEvent.BLOCK_CHANGE, source.getEntity());
                this.playSound(this.getRemoveItemSound(), 1.0F, 1.0F);
            }

            return true;
        } else {
            return super.hurt(source, amount);
        }
    }

    public SoundEvent getRemoveItemSound() {
        return SoundEvents.ITEM_FRAME_REMOVE_ITEM;
    }

    @Override
    public int getWidth() {
        return 12;
    }

    @Override
    public int getHeight() {
        return 12;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = 16.0D;

        d1 *= 64.0D * getViewScale();
        return distance < d1 * d1;
    }

    @Override
    public void dropItem(@Nullable Entity entity) {
        this.playSound(this.getBreakSound(), 1.0F, 1.0F);
        this.dropItem(entity, true);
        this.gameEvent(GameEvent.BLOCK_CHANGE, entity);
    }

    public SoundEvent getBreakSound() {
        return SoundEvents.ITEM_FRAME_BREAK;
    }

    @Override
    public void playPlacementSound() {
        this.playSound(this.getPlaceSound(), 1.0F, 1.0F);
    }

    public SoundEvent getPlaceSound() {
        return SoundEvents.ITEM_FRAME_PLACE;
    }

    private void dropItem(@Nullable Entity entity, boolean alwaysDrop) {
        if (!this.fixed) {
            ItemStack itemstack = this.getItem();

            this.setItem(ItemStack.EMPTY);
            if (!this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                if (entity == null) {
                    this.removeFramedMap(itemstack);
                }

            } else {
                if (entity instanceof Player) {
                    Player entityhuman = (Player) entity;

                    if (entityhuman.getAbilities().instabuild) {
                        this.removeFramedMap(itemstack);
                        return;
                    }
                }

                if (alwaysDrop) {
                    this.spawnAtLocation(this.getFrameItemStack());
                }

                if (!itemstack.isEmpty()) {
                    itemstack = itemstack.copy();
                    this.removeFramedMap(itemstack);
                    if (this.random.nextFloat() < this.dropChance) {
                        this.spawnAtLocation(itemstack);
                    }
                }

            }
        }
    }

    // Paper start - Fix MC-123848 (spawn item frame drops above block)
    @Nullable
    @Override
    public net.minecraft.world.entity.item.ItemEntity spawnAtLocation(ItemStack stack) {
        return this.spawnAtLocation(stack, getDirection().equals(Direction.DOWN) ? -0.6F : 0.0F);
    }
    // Paper end

    private void removeFramedMap(ItemStack itemstack) {
        // Paper start - fix MC-252817 (green map markers do not disappear)
        this.getFramedMapIdFromItem(itemstack).ifPresent((i) -> {
            // Paper end
            MapItemSavedData worldmap = MapItem.getSavedData(i, this.level());

            if (worldmap != null) {
                synchronized (worldmap) { // Folia - make map data thread-safe
                worldmap.removedFromFrame(this.pos, this.getId());
                worldmap.setDirty(true);
                } // Folia - make map data thread-safe
            }

        });
        itemstack.setEntityRepresentation((Entity) null);
    }

    public ItemStack getItem() {
        return (ItemStack) this.getEntityData().get(ItemFrame.DATA_ITEM);
    }

    public OptionalInt getFramedMapId() {
        ItemStack itemstack = this.getItem();
        // Paper start - fix MC-252817 (green map markers do not disappear)
        return this.getFramedMapIdFromItem(itemstack);
    }

    public OptionalInt getFramedMapIdFromItem(ItemStack itemstack) {
        // Paper end
        if (itemstack.is(Items.FILLED_MAP)) {
            Integer integer = MapItem.getMapId(itemstack);

            if (integer != null) {
                return OptionalInt.of(integer);
            }
        }

        return OptionalInt.empty();
    }

    public boolean hasFramedMap() {
        return this.getFramedMapId().isPresent();
    }

    public void setItem(ItemStack stack) {
        this.setItem(stack, true);
    }

    public void setItem(ItemStack value, boolean update) {
        // CraftBukkit start
        this.setItem(value, update, true);
    }

    public void setItem(ItemStack itemstack, boolean flag, boolean playSound) {
        // CraftBukkit end
        if (!itemstack.isEmpty()) {
            itemstack = itemstack.copyWithCount(1);
        }

        this.onItemChanged(itemstack);
        this.getEntityData().set(ItemFrame.DATA_ITEM, itemstack);
        if (!itemstack.isEmpty() && flag && playSound) { // CraftBukkit // Paper - only play sound when update flag is set
            this.playSound(this.getAddItemSound(), 1.0F, 1.0F);
        }

        if (flag && this.pos != null) {
            this.level().updateNeighbourForOutputSignal(this.pos, Blocks.AIR);
        }

    }

    public SoundEvent getAddItemSound() {
        return SoundEvents.ITEM_FRAME_ADD_ITEM;
    }

    @Override
    public SlotAccess getSlot(int mappedIndex) {
        return mappedIndex == 0 ? new SlotAccess() {
            @Override
            public ItemStack get() {
                return ItemFrame.this.getItem();
            }

            @Override
            public boolean set(ItemStack stack) {
                ItemFrame.this.setItem(stack);
                return true;
            }
        } : super.getSlot(mappedIndex);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (data.equals(ItemFrame.DATA_ITEM)) {
            this.onItemChanged(this.getItem());
        }

    }

    private void onItemChanged(ItemStack stack) {
        this.cachedMapId = MapItem.getMapId(stack); // Paper
        if (!stack.isEmpty() && stack.getFrame() != this) {
            stack.setEntityRepresentation(this);
        }

        this.recalculateBoundingBox();
    }

    public int getRotation() {
        return (Integer) this.getEntityData().get(ItemFrame.DATA_ROTATION);
    }

    public void setRotation(int value) {
        this.setRotation(value, true);
    }

    private void setRotation(int value, boolean updateComparators) {
        this.getEntityData().set(ItemFrame.DATA_ROTATION, value % 8);
        if (updateComparators && this.pos != null) {
            this.level().updateNeighbourForOutputSignal(this.pos, Blocks.AIR);
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        if (!this.getItem().isEmpty()) {
            nbt.put("Item", this.getItem().save(new CompoundTag()));
            nbt.putByte("ItemRotation", (byte) this.getRotation());
            nbt.putFloat("ItemDropChance", this.dropChance);
        }

        nbt.putByte("Facing", (byte) this.direction.get3DDataValue());
        nbt.putBoolean("Invisible", this.isInvisible());
        nbt.putBoolean("Fixed", this.fixed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        CompoundTag nbttagcompound1 = nbt.getCompound("Item");

        if (nbttagcompound1 != null && !nbttagcompound1.isEmpty()) {
            ItemStack itemstack = ItemStack.of(nbttagcompound1);

            if (itemstack.isEmpty()) {
                ItemFrame.LOGGER.warn("Unable to load item from: {}", nbttagcompound1);
            }

            ItemStack itemstack1 = this.getItem();

            if (!itemstack1.isEmpty() && !ItemStack.matches(itemstack, itemstack1)) {
                this.removeFramedMap(itemstack1);
            }

            this.setItem(itemstack, false);
            this.setRotation(nbt.getByte("ItemRotation"), false);
            if (nbt.contains("ItemDropChance", 99)) {
                this.dropChance = nbt.getFloat("ItemDropChance");
            }
        }

        this.setDirection(Direction.from3DDataValue(nbt.getByte("Facing")));
        this.setInvisible(nbt.getBoolean("Invisible"));
        this.fixed = nbt.getBoolean("Fixed");
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        boolean flag = !this.getItem().isEmpty();
        boolean flag1 = !itemstack.isEmpty();

        if (this.fixed) {
            return InteractionResult.PASS;
        } else if (!this.level().isClientSide) {
            if (!flag) {
                if (flag1 && !this.isRemoved()) {
                    if (itemstack.is(Items.FILLED_MAP)) {
                        MapItemSavedData worldmap = MapItem.getSavedData(itemstack, this.level());

                        if (worldmap != null && worldmap.isTrackedCountOverLimit(256)) {
                            return InteractionResult.FAIL;
                        }
                    }

                    // Paper start - call PlayerItemFrameChangeEvent
                    PlayerItemFrameChangeEvent event = new PlayerItemFrameChangeEvent((org.bukkit.entity.Player) player.getBukkitEntity(), (org.bukkit.entity.ItemFrame) this.getBukkitEntity(), itemstack.asBukkitCopy(), PlayerItemFrameChangeEvent.ItemFrameChangeAction.PLACE);
                    if (!event.callEvent()) {
                        return InteractionResult.FAIL;
                    }
                    this.setItem(ItemStack.fromBukkitCopy(event.getItemStack()));
                    // Paper end
                    this.gameEvent(GameEvent.BLOCK_CHANGE, player);
                    if (!player.getAbilities().instabuild) {
                        itemstack.shrink(1);
                    }
                }
            } else {
                // Paper start - call PlayerItemFrameChangeEvent
                PlayerItemFrameChangeEvent event = new PlayerItemFrameChangeEvent((org.bukkit.entity.Player) player.getBukkitEntity(), (org.bukkit.entity.ItemFrame) this.getBukkitEntity(), this.getItem().asBukkitCopy(), PlayerItemFrameChangeEvent.ItemFrameChangeAction.ROTATE);
                if (!event.callEvent()) {
                    return InteractionResult.FAIL;
                }
                setItem(ItemStack.fromBukkitCopy(event.getItemStack()), false, false);
                // Paper end
                this.playSound(this.getRotateItemSound(), 1.0F, 1.0F);
                this.setRotation(this.getRotation() + 1);
                this.gameEvent(GameEvent.BLOCK_CHANGE, player);
            }

            return InteractionResult.CONSUME;
        } else {
            return !flag && !flag1 ? InteractionResult.PASS : InteractionResult.SUCCESS;
        }
    }

    public SoundEvent getRotateItemSound() {
        return SoundEvents.ITEM_FRAME_ROTATE_ITEM;
    }

    public int getAnalogOutput() {
        return this.getItem().isEmpty() ? 0 : this.getRotation() % 8 + 1;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this, this.direction.get3DDataValue(), this.getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.setDirection(Direction.from3DDataValue(packet.getData()));
    }

    @Override
    public ItemStack getPickResult() {
        ItemStack itemstack = this.getItem();

        return itemstack.isEmpty() ? this.getFrameItemStack() : itemstack.copy();
    }

    protected ItemStack getFrameItemStack() {
        return new ItemStack(Items.ITEM_FRAME);
    }

    @Override
    public float getVisualRotationYInDegrees() {
        Direction enumdirection = this.getDirection();
        int i = enumdirection.getAxis().isVertical() ? 90 * enumdirection.getAxisDirection().getStep() : 0;

        return (float) Mth.wrapDegrees(180 + enumdirection.get2DDataValue() * 90 + this.getRotation() * 45 + i);
    }
}
