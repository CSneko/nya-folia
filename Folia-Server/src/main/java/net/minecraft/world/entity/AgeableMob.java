package net.minecraft.world.entity;

import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

public abstract class AgeableMob extends PathfinderMob {

    private static final EntityDataAccessor<Boolean> DATA_BABY_ID = SynchedEntityData.defineId(AgeableMob.class, EntityDataSerializers.BOOLEAN);
    public static final int BABY_START_AGE = -24000;
    private static final int FORCED_AGE_PARTICLE_TICKS = 40;
    protected int age;
    protected int forcedAge;
    protected int forcedAgeTimer;
    public boolean ageLocked; // CraftBukkit

    protected AgeableMob(EntityType<? extends AgeableMob> type, Level world) {
        super(type, world);
    }

    // Spigot start
    @Override
    public void inactiveTick()
    {
        super.inactiveTick();
        if ( this.level().isClientSide || this.ageLocked )
        { // CraftBukkit
            this.refreshDimensions();
        } else
        {
            int i = this.getAge();

            if ( i < 0 )
            {
                ++i;
                this.setAge( i );
            } else if ( i > 0 )
            {
                --i;
                this.setAge( i );
            }
        }
    }
    // Spigot end

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData, @Nullable CompoundTag entityNbt) {
        if (entityData == null) {
            entityData = new AgeableMob.AgeableMobGroupData(true);
        }

        AgeableMob.AgeableMobGroupData entityageable_a = (AgeableMob.AgeableMobGroupData) entityData;

        if (entityageable_a.isShouldSpawnBaby() && entityageable_a.getGroupSize() > 0 && world.getRandom().nextFloat() <= entityageable_a.getBabySpawnChance()) {
            this.setAge(-24000);
        }

        entityageable_a.increaseGroupSizeByOne();
        return super.finalizeSpawn(world, difficulty, spawnReason, (SpawnGroupData) entityData, entityNbt);
    }

    @Nullable
    public abstract AgeableMob getBreedOffspring(ServerLevel world, AgeableMob entity);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(AgeableMob.DATA_BABY_ID, false);
    }

    public boolean canBreed() {
        return false;
    }

    public int getAge() {
        return this.level().isClientSide ? ((Boolean) this.entityData.get(AgeableMob.DATA_BABY_ID) ? -1 : 1) : this.age;
    }

    public void ageUp(int age, boolean overGrow) {
        if (this.ageLocked) return; // Paper - GH-1459
        int j = this.getAge();
        int k = j;

        j += age * 20;
        if (j > 0) {
            j = 0;
        }

        int l = j - k;

        this.setAge(j);
        if (overGrow) {
            this.forcedAge += l;
            if (this.forcedAgeTimer == 0) {
                this.forcedAgeTimer = 40;
            }
        }

        if (this.getAge() == 0) {
            this.setAge(this.forcedAge);
        }

    }

    public void ageUp(int age) {
        this.ageUp(age, false);
    }

    public void setAge(int age) {
        int j = this.getAge();

        this.age = age;
        if (j < 0 && age >= 0 || j >= 0 && age < 0) {
            this.entityData.set(AgeableMob.DATA_BABY_ID, age < 0);
            this.ageBoundaryReached();
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("Age", this.getAge());
        nbt.putInt("ForcedAge", this.forcedAge);
        nbt.putBoolean("AgeLocked", this.ageLocked); // CraftBukkit
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setAge(nbt.getInt("Age"));
        this.forcedAge = nbt.getInt("ForcedAge");
        this.ageLocked = nbt.getBoolean("AgeLocked"); // CraftBukkit
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (AgeableMob.DATA_BABY_ID.equals(data)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(data);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide || this.ageLocked) { // CraftBukkit
            if (this.forcedAgeTimer > 0) {
                if (this.forcedAgeTimer % 4 == 0) {
                    this.level().addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), 0.0D, 0.0D, 0.0D);
                }

                --this.forcedAgeTimer;
            }
        } else if (this.isAlive()) {
            int i = this.getAge();

            if (i < 0) {
                ++i;
                this.setAge(i);
            } else if (i > 0) {
                --i;
                this.setAge(i);
            }
        }

    }

    protected void ageBoundaryReached() {
        if (!this.isBaby() && this.isPassenger()) {
            Entity entity = this.getVehicle();

            if (entity instanceof Boat) {
                Boat entityboat = (Boat) entity;

                if (!entityboat.hasEnoughSpaceFor(this)) {
                    this.stopRiding();
                }
            }
        }

    }

    @Override
    public boolean isBaby() {
        return this.getAge() < 0;
    }

    @Override
    public void setBaby(boolean baby) {
        this.setAge(baby ? -24000 : 0);
    }

    public static int getSpeedUpSecondsWhenFeeding(int breedingAge) {
        return (int) ((float) (breedingAge / 20) * 0.1F);
    }

    public static class AgeableMobGroupData implements SpawnGroupData {

        private int groupSize;
        private final boolean shouldSpawnBaby;
        private final float babySpawnChance;

        private AgeableMobGroupData(boolean babyAllowed, float babyChance) {
            this.shouldSpawnBaby = babyAllowed;
            this.babySpawnChance = babyChance;
        }

        public AgeableMobGroupData(boolean babyAllowed) {
            this(babyAllowed, 0.05F);
        }

        public AgeableMobGroupData(float babyChance) {
            this(true, babyChance);
        }

        public int getGroupSize() {
            return this.groupSize;
        }

        public void increaseGroupSizeByOne() {
            ++this.groupSize;
        }

        public boolean isShouldSpawnBaby() {
            return this.shouldSpawnBaby;
        }

        public float getBabySpawnChance() {
            return this.babySpawnChance;
        }
    }
}
