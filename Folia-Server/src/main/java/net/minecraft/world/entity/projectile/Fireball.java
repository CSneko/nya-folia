package net.minecraft.world.entity.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public abstract class Fireball extends AbstractHurtingProjectile implements ItemSupplier {

    private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK = SynchedEntityData.defineId(Fireball.class, EntityDataSerializers.ITEM_STACK);

    public Fireball(EntityType<? extends Fireball> type, Level world) {
        super(type, world);
    }

    public Fireball(EntityType<? extends Fireball> type, double x, double y, double z, double directionX, double directionY, double directionZ, Level world) {
        super(type, x, y, z, directionX, directionY, directionZ, world);
    }

    public Fireball(EntityType<? extends Fireball> type, LivingEntity owner, double directionX, double directionY, double directionZ, Level world) {
        super(type, owner, directionX, directionY, directionZ, world);
    }

    public void setItem(ItemStack stack) {
        if (true || !stack.is(Items.FIRE_CHARGE) || stack.hasTag()) { // Paper - always allow item changing
            this.getEntityData().set(Fireball.DATA_ITEM_STACK, stack.copyWithCount(1));
        }

    }

    public ItemStack getItemRaw() {
        return (ItemStack) this.getEntityData().get(Fireball.DATA_ITEM_STACK);
    }

    @Override
    public ItemStack getItem() {
        ItemStack itemstack = this.getItemRaw();

        return itemstack.isEmpty() ? new ItemStack(Items.FIRE_CHARGE) : itemstack;
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(Fireball.DATA_ITEM_STACK, ItemStack.EMPTY);
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

        if (!itemstack.isEmpty()) this.setItem(itemstack); // CraftBukkit - SPIGOT-5474 probably came from bugged earlier versions
    }
}
