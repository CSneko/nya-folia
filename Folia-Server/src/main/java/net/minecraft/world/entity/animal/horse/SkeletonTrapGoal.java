package net.minecraft.world.entity.animal.horse;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class SkeletonTrapGoal extends Goal {

    private final SkeletonHorse horse;
    private java.util.List<org.bukkit.entity.HumanEntity> eligiblePlayers; // Paper

    public SkeletonTrapGoal(SkeletonHorse skeletonHorse) {
        this.horse = skeletonHorse;
    }

    @Override
    public boolean canUse() {
        return !(eligiblePlayers = this.horse.level().findNearbyBukkitPlayers(this.horse.getX(), this.horse.getY(), this.horse.getZ(), 10.0D, net.minecraft.world.entity.EntitySelector.PLAYER_AFFECTS_SPAWNING)).isEmpty(); // Paper - Affects Spawning API & SkeletonHorseTrapEvent
    }

    @Override
    public void tick() {
        ServerLevel worldserver = (ServerLevel) this.horse.level();
        if (!new com.destroystokyo.paper.event.entity.SkeletonHorseTrapEvent((org.bukkit.entity.SkeletonHorse) this.horse.getBukkitEntity(), eligiblePlayers).callEvent()) return; // Paper
        DifficultyInstance difficultydamagescaler = worldserver.getCurrentDifficultyAt(this.horse.blockPosition());

        this.horse.setTrap(false);
        this.horse.setTamed(true);
        this.horse.setAge(0);
        LightningBolt entitylightning = (LightningBolt) EntityType.LIGHTNING_BOLT.create(worldserver);

        if (entitylightning != null) {
            entitylightning.moveTo(this.horse.getX(), this.horse.getY(), this.horse.getZ());
            entitylightning.setVisualOnly(true);
            worldserver.strikeLightning(entitylightning, org.bukkit.event.weather.LightningStrikeEvent.Cause.TRAP); // CraftBukkit
            Skeleton entityskeleton = this.createSkeleton(difficultydamagescaler, this.horse);

            if (entityskeleton != null) {
                entityskeleton.startRiding(this.horse);
                worldserver.addFreshEntityWithPassengers(entityskeleton, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.TRAP); // CraftBukkit

                for (int i = 0; i < 3; ++i) {
                    AbstractHorse entityhorseabstract = this.createHorse(difficultydamagescaler);

                    if (entityhorseabstract != null) {
                        Skeleton entityskeleton1 = this.createSkeleton(difficultydamagescaler, entityhorseabstract);

                        if (entityskeleton1 != null) {
                            entityskeleton1.startRiding(entityhorseabstract);
                            entityhorseabstract.push(this.horse.getRandom().triangle(0.0D, 1.1485D), 0.0D, this.horse.getRandom().triangle(0.0D, 1.1485D));
                            worldserver.addFreshEntityWithPassengers(entityhorseabstract, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.JOCKEY); // CraftBukkit
                        }
                    }
                }

            }
        }
    }

    @Nullable
    private AbstractHorse createHorse(DifficultyInstance localDifficulty) {
        SkeletonHorse entityhorseskeleton = (SkeletonHorse) EntityType.SKELETON_HORSE.create(this.horse.level());

        if (entityhorseskeleton != null) {
            entityhorseskeleton.finalizeSpawn((ServerLevel) this.horse.level(), localDifficulty, MobSpawnType.TRIGGERED, (SpawnGroupData) null, (CompoundTag) null);
            entityhorseskeleton.setPos(this.horse.getX(), this.horse.getY(), this.horse.getZ());
            entityhorseskeleton.invulnerableTime = 60;
            entityhorseskeleton.setPersistenceRequired();
            entityhorseskeleton.setTamed(true);
            entityhorseskeleton.setAge(0);
        }

        return entityhorseskeleton;
    }

    @Nullable
    private Skeleton createSkeleton(DifficultyInstance localDifficulty, AbstractHorse vehicle) {
        Skeleton entityskeleton = (Skeleton) EntityType.SKELETON.create(vehicle.level());

        if (entityskeleton != null) {
            entityskeleton.finalizeSpawn((ServerLevel) vehicle.level(), localDifficulty, MobSpawnType.TRIGGERED, (SpawnGroupData) null, (CompoundTag) null);
            entityskeleton.setPos(vehicle.getX(), vehicle.getY(), vehicle.getZ());
            entityskeleton.invulnerableTime = 60;
            entityskeleton.setPersistenceRequired();
            if (entityskeleton.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                entityskeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            }

            entityskeleton.setItemSlot(EquipmentSlot.MAINHAND, EnchantmentHelper.enchantItem(entityskeleton.getRandom(), this.disenchant(entityskeleton.getMainHandItem()), (int) (5.0F + localDifficulty.getSpecialMultiplier() * (float) entityskeleton.getRandom().nextInt(18)), false));
            entityskeleton.setItemSlot(EquipmentSlot.HEAD, EnchantmentHelper.enchantItem(entityskeleton.getRandom(), this.disenchant(entityskeleton.getItemBySlot(EquipmentSlot.HEAD)), (int) (5.0F + localDifficulty.getSpecialMultiplier() * (float) entityskeleton.getRandom().nextInt(18)), false));
        }

        return entityskeleton;
    }

    private ItemStack disenchant(ItemStack stack) {
        stack.removeTagKey("Enchantments");
        return stack;
    }
}
