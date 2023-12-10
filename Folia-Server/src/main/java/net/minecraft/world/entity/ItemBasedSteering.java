package net.minecraft.world.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class ItemBasedSteering {

    private static final int MIN_BOOST_TIME = 140;
    private static final int MAX_BOOST_TIME = 700;
    private final SynchedEntityData entityData;
    private final EntityDataAccessor<Integer> boostTimeAccessor;
    private final EntityDataAccessor<Boolean> hasSaddleAccessor;
    public boolean boosting;
    public int boostTime;

    public ItemBasedSteering(SynchedEntityData dataTracker, EntityDataAccessor<Integer> boostTime, EntityDataAccessor<Boolean> saddled) {
        this.entityData = dataTracker;
        this.boostTimeAccessor = boostTime;
        this.hasSaddleAccessor = saddled;
    }

    public void onSynced() {
        this.boosting = true;
        this.boostTime = 0;
    }

    public boolean boost(RandomSource random) {
        if (this.boosting) {
            return false;
        } else {
            this.boosting = true;
            this.boostTime = 0;
            this.entityData.set(this.boostTimeAccessor, random.nextInt(841) + 140);
            return true;
        }
    }

    public void tickBoost() {
        if (this.boosting && this.boostTime++ > this.boostTimeTotal()) {
            this.boosting = false;
        }

    }

    public float boostFactor() {
        return this.boosting ? 1.0F + 1.15F * Mth.sin((float) this.boostTime / (float) this.boostTimeTotal() * 3.1415927F) : 1.0F;
    }

    public int boostTimeTotal() {
        return (Integer) this.entityData.get(this.boostTimeAccessor);
    }

    // CraftBukkit add setBoostTicks(int)
    public void setBoostTicks(int ticks) {
        this.boosting = true;
        this.boostTime = 0;
        this.entityData.set(this.boostTimeAccessor, ticks);
    }
    // CraftBukkit end

    public void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putBoolean("Saddle", this.hasSaddle());
    }

    public void readAdditionalSaveData(CompoundTag nbt) {
        this.setSaddle(nbt.getBoolean("Saddle"));
    }

    public void setSaddle(boolean saddled) {
        this.entityData.set(this.hasSaddleAccessor, saddled);
    }

    public boolean hasSaddle() {
        return (Boolean) this.entityData.get(this.hasSaddleAccessor);
    }
}
