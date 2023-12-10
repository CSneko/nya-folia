package net.minecraft.world.entity.animal;

import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
// CraftBukkit end

public abstract class Animal extends AgeableMob {

    protected static final int PARENT_AGE_AFTER_BREEDING = 6000;
    public int inLove;
    @Nullable
    public UUID loveCause;
    public ItemStack breedItem; // CraftBukkit - Add breedItem variable

    protected Animal(EntityType<? extends Animal> type, Level world) {
        super(type, world);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
    }

    @Override
    protected void customServerAiStep() {
        if (this.getAge() != 0) {
            this.inLove = 0;
        }

        super.customServerAiStep();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.getAge() != 0) {
            this.inLove = 0;
        }

        if (this.inLove > 0) {
            --this.inLove;
            if (this.inLove % 10 == 0) {
                double d0 = this.random.nextGaussian() * 0.02D;
                double d1 = this.random.nextGaussian() * 0.02D;
                double d2 = this.random.nextGaussian() * 0.02D;

                this.level().addParticle(ParticleTypes.HEART, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), d0, d1, d2);
            }
        }

    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            // CraftBukkit start
            boolean result = super.hurt(source, amount);
            if (result) {
            this.inLove = 0;
            }
            return result;
            // CraftBukkit end
        }
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader world) {
        return world.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK) ? 10.0F : world.getPathfindingCostFromLightLevels(pos);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("InLove", this.inLove);
        if (this.loveCause != null) {
            nbt.putUUID("LoveCause", this.loveCause);
        }

    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.inLove = nbt.getInt("InLove");
        this.loveCause = nbt.hasUUID("LoveCause") ? nbt.getUUID("LoveCause") : null;
    }

    public static boolean checkAnimalSpawnRules(EntityType<? extends Animal> type, LevelAccessor world, MobSpawnType spawnReason, BlockPos pos, RandomSource random) {
        return world.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON) && Animal.isBrightEnoughToSpawn(world, pos);
    }

    protected static boolean isBrightEnoughToSpawn(BlockAndTintGetter world, BlockPos pos) {
        return world.getRawBrightness(pos, 0) > 8;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override
    public int getExperienceReward() {
        return 1 + this.level().random.nextInt(3);
    }

    public boolean isFood(ItemStack stack) {
        return stack.is(Items.WHEAT);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (this.isFood(itemstack)) {
            int i = this.getAge();

            if (!this.level().isClientSide && i == 0 && this.canFallInLove()) {
                final ItemStack breedCopy = itemstack.copy(); // Paper
                this.usePlayerItem(player, hand, itemstack);
                this.setInLove(player, breedCopy); // Paper
                return InteractionResult.SUCCESS;
            }

            if (this.isBaby()) {
                this.usePlayerItem(player, hand, itemstack);
                this.ageUp(getSpeedUpSecondsWhenFeeding(-i), true);
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            if (this.level().isClientSide) {
                return InteractionResult.CONSUME;
            }
        }

        return super.mobInteract(player, hand);
    }

    protected void usePlayerItem(Player player, InteractionHand hand, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

    }

    public boolean canFallInLove() {
        return this.inLove <= 0;
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper
    public void setInLove(@Nullable Player player) {
        // Paper start - pass breed stack
        this.setInLove(player, null);
    }
    public void setInLove(@Nullable Player player, @Nullable ItemStack breedItemCopy) {
        if (breedItemCopy != null) this.breedItem = breedItemCopy;
        // Paper end
        // CraftBukkit start
        EntityEnterLoveModeEvent entityEnterLoveModeEvent = CraftEventFactory.callEntityEnterLoveModeEvent(player, this, 600);
        if (entityEnterLoveModeEvent.isCancelled()) {
            this.breedItem = null; // Paper - clear if cancelled
            return;
        }
        this.inLove = entityEnterLoveModeEvent.getTicksInLove();
        // CraftBukkit end
        if (player != null) {
            this.loveCause = player.getUUID();
        }
        // Paper - set breed item in better place

        this.level().broadcastEntityEvent(this, (byte) 18);
    }

    public void setInLoveTime(int loveTicks) {
        this.inLove = loveTicks;
    }

    public int getInLoveTime() {
        return this.inLove;
    }

    @Nullable
    public ServerPlayer getLoveCause() {
        if (this.loveCause == null) {
            return null;
        } else {
            Player entityhuman = this.level().getPlayerByUUID(this.loveCause);

            return entityhuman instanceof ServerPlayer ? (ServerPlayer) entityhuman : null;
        }
    }

    public boolean isInLove() {
        return this.inLove > 0;
    }

    public void resetLove() {
        this.inLove = 0;
    }

    public boolean canMate(Animal other) {
        return other == this ? false : (other.getClass() != this.getClass() ? false : this.isInLove() && other.isInLove());
    }

    public void spawnChildFromBreeding(ServerLevel world, Animal other) {
        AgeableMob entityageable = this.getBreedOffspring(world, other);

        if (entityageable != null) {
            entityageable.setBaby(true);
            entityageable.moveTo(this.getX(), this.getY(), this.getZ(), 0.0F, 0.0F);
            // CraftBukkit start - call EntityBreedEvent
            ServerPlayer breeder = Optional.ofNullable(this.getLoveCause()).or(() -> {
                return Optional.ofNullable(other.getLoveCause());
            }).orElse(null);
            int experience = this.getRandom().nextInt(7) + 1;
            EntityBreedEvent entityBreedEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityBreedEvent(entityageable, this, other, breeder, this.breedItem, experience);
            if (entityBreedEvent.isCancelled()) {
                return;
            }
            experience = entityBreedEvent.getExperience();
            this.finalizeSpawnChildFromBreeding(world, other, entityageable, experience);
            world.addFreshEntityWithPassengers(entityageable, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BREEDING);
            // CraftBukkit end
        }
    }

    public void finalizeSpawnChildFromBreeding(ServerLevel world, Animal other, @Nullable AgeableMob baby) {
        // CraftBukkit start
        this.finalizeSpawnChildFromBreeding(world, other, baby, this.getRandom().nextInt(7) + 1);
    }

    public void finalizeSpawnChildFromBreeding(ServerLevel worldserver, Animal entityanimal, @Nullable AgeableMob entityageable, int experience) {
        // CraftBukkit end
        // Paper start
        ServerPlayer entityplayer = this.getLoveCause();
        if (entityplayer == null) entityplayer = entityanimal.getLoveCause();
        if (entityplayer != null) {
            // Paper end
            entityplayer.awardStat(Stats.ANIMALS_BRED);
            CriteriaTriggers.BRED_ANIMALS.trigger(entityplayer, this, entityanimal, entityageable);
        } // Paper
        this.setAge(6000);
        entityanimal.setAge(6000);
        this.resetLove();
        entityanimal.resetLove();
        worldserver.broadcastEntityEvent(this, (byte) 18);
        if (worldserver.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            // CraftBukkit start - use event experience
            if (experience > 0) {
                worldserver.addFreshEntity(new ExperienceOrb(worldserver, this.getX(), this.getY(), this.getZ(), experience, org.bukkit.entity.ExperienceOrb.SpawnReason.BREED, entityplayer, entityageable)); // Paper
            }
            // CraftBukkit end
        }

    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 18) {
            for (int i = 0; i < 7; ++i) {
                double d0 = this.random.nextGaussian() * 0.02D;
                double d1 = this.random.nextGaussian() * 0.02D;
                double d2 = this.random.nextGaussian() * 0.02D;

                this.level().addParticle(ParticleTypes.HEART, this.getRandomX(1.0D), this.getRandomY() + 0.5D, this.getRandomZ(1.0D), d0, d1, d2);
            }
        } else {
            super.handleEntityEvent(status);
        }

    }
}
