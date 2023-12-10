package net.minecraft.world.entity.animal;

import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.player.PlayerBucketEntityEvent;
// CraftBukkit end

public interface Bucketable {

    boolean fromBucket();

    void setFromBucket(boolean fromBucket);

    void saveToBucketTag(ItemStack stack);

    void loadFromBucketTag(CompoundTag nbt);

    ItemStack getBucketItemStack();

    SoundEvent getPickupSound();

    /** @deprecated */
    @Deprecated
    static void saveDefaultDataToBucketTag(Mob entity, ItemStack stack) {
        CompoundTag nbttagcompound = stack.getOrCreateTag();

        if (entity.hasCustomName()) {
            stack.setHoverName(entity.getCustomName());
        }

        if (entity.isNoAi()) {
            nbttagcompound.putBoolean("NoAI", entity.isNoAi());
        }

        if (entity.isSilent()) {
            nbttagcompound.putBoolean("Silent", entity.isSilent());
        }

        if (entity.isNoGravity()) {
            nbttagcompound.putBoolean("NoGravity", entity.isNoGravity());
        }

        if (entity.hasGlowingTag()) {
            nbttagcompound.putBoolean("Glowing", entity.hasGlowingTag());
        }

        if (entity.isInvulnerable()) {
            nbttagcompound.putBoolean("Invulnerable", entity.isInvulnerable());
        }

        nbttagcompound.putFloat("Health", entity.getHealth());
    }

    /** @deprecated */
    @Deprecated
    static void loadDefaultDataFromBucketTag(Mob entity, CompoundTag nbt) {
        if (nbt.contains("NoAI")) {
            entity.setNoAi(nbt.getBoolean("NoAI"));
        }

        if (nbt.contains("Silent")) {
            entity.setSilent(nbt.getBoolean("Silent"));
        }

        if (nbt.contains("NoGravity")) {
            entity.setNoGravity(nbt.getBoolean("NoGravity"));
        }

        if (nbt.contains("Glowing")) {
            entity.setGlowingTag(nbt.getBoolean("Glowing"));
        }

        if (nbt.contains("Invulnerable")) {
            entity.setInvulnerable(nbt.getBoolean("Invulnerable"));
        }

        if (nbt.contains("Health", 99)) {
            entity.setHealth(nbt.getFloat("Health"));
        }

    }

    static <T extends LivingEntity & Bucketable> Optional<InteractionResult> bucketMobPickup(Player player, InteractionHand hand, T entity) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.getItem() == Items.WATER_BUCKET && entity.isAlive()) {
            // CraftBukkit start
            // t0.playSound(((Bucketable) t0).getPickupSound(), 1.0F, 1.0F); // CraftBukkit - moved down
            ItemStack itemstack1 = ((Bucketable) entity).getBucketItemStack();

            ((Bucketable) entity).saveToBucketTag(itemstack1);

            PlayerBucketEntityEvent playerBucketFishEvent = CraftEventFactory.callPlayerFishBucketEvent(entity, player, itemstack, itemstack1, hand);
            itemstack1 = CraftItemStack.asNMSCopy(playerBucketFishEvent.getEntityBucket());
            if (playerBucketFishEvent.isCancelled()) {
                ((ServerPlayer) player).containerMenu.sendAllDataToRemote(); // We need to update inventory to resync client's bucket
                entity.getEntityData().resendPossiblyDesyncedEntity((ServerPlayer) player); // Paper
                return Optional.of(InteractionResult.FAIL);
            }
            entity.playSound(((Bucketable) entity).getPickupSound(), 1.0F, 1.0F);
            // CraftBukkit end
            ItemStack itemstack2 = ItemUtils.createFilledResult(itemstack, player, itemstack1, false);

            player.setItemInHand(hand, itemstack2);
            Level world = entity.level();

            if (!world.isClientSide) {
                CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayer) player, itemstack1);
            }

            entity.discard();
            return Optional.of(InteractionResult.sidedSuccess(world.isClientSide));
        } else {
            return Optional.empty();
        }
    }
}
