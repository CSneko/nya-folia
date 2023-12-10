package net.minecraft.world.entity.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public abstract class ThrowableItemProjectile extends ThrowableProjectile implements ItemSupplier {

    private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK = SynchedEntityData.defineId(ThrowableItemProjectile.class, EntityDataSerializers.ITEM_STACK);

    public ThrowableItemProjectile(EntityType<? extends ThrowableItemProjectile> type, Level world) {
        super(type, world);
    }

    public ThrowableItemProjectile(EntityType<? extends ThrowableItemProjectile> type, double x, double y, double z, Level world) {
        super(type, x, y, z, world);
    }

    public ThrowableItemProjectile(EntityType<? extends ThrowableItemProjectile> type, LivingEntity owner, Level world) {
        super(type, owner, world);
    }

    public void setItem(ItemStack item) {
        if (!item.is(this.getDefaultItem()) || item.hasTag()) {
            this.getEntityData().set(ThrowableItemProjectile.DATA_ITEM_STACK, item.copyWithCount(1));
        }

    }

    protected abstract Item getDefaultItem();

    // CraftBukkit start
    public Item getDefaultItemPublic() {
        return this.getDefaultItem();
    }
    // CraftBukkit end

    public ItemStack getItemRaw() {
        return (ItemStack) this.getEntityData().get(ThrowableItemProjectile.DATA_ITEM_STACK);
    }

    @Override
    public ItemStack getItem() {
        ItemStack itemstack = this.getItemRaw();

        return itemstack.isEmpty() ? new ItemStack(this.getDefaultItem()) : itemstack;
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(ThrowableItemProjectile.DATA_ITEM_STACK, ItemStack.EMPTY);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        ItemStack itemstack = this.getItemRaw();

        if (!itemstack.isEmpty()) {
            nbt.put("Item", itemstack.save(new CompoundTag()));
        }

    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        ItemStack itemstack = ItemStack.of(nbt.getCompound("Item"));

        this.setItem(itemstack);
    }
}
