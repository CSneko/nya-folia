package net.minecraft.world.level.block.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.LockCode;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

public abstract class BaseContainerBlockEntity extends BlockEntity implements Container, MenuProvider, Nameable {

    public LockCode lockKey;
    @Nullable
    public Component name;

    protected BaseContainerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.lockKey = LockCode.NO_LOCK;
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        this.lockKey = LockCode.fromTag(nbt);
        if (nbt.contains("CustomName", 8)) {
            this.name = io.papermc.paper.util.MCUtil.getBaseComponentFromNbt("CustomName", nbt); // Paper - Catch ParseException
        }

    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        this.lockKey.addToTag(nbt);
        if (this.name != null) {
            nbt.putString("CustomName", Component.Serializer.toJson(this.name));
        }

    }

    public void setCustomName(Component customName) {
        this.name = customName;
    }

    @Override
    public Component getName() {
        return this.name != null ? this.name : this.getDefaultName();
    }

    @Override
    public Component getDisplayName() {
        return this.getName();
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return this.name;
    }

    protected abstract Component getDefaultName();

    public boolean canOpen(Player player) {
        return BaseContainerBlockEntity.canUnlock(player, this.lockKey, this.getDisplayName(), this); // Paper
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper
    public static boolean canUnlock(Player player, LockCode lock, Component containerName) {
        // Paper start
        return canUnlock(player, lock, containerName, null);
    }
    public static boolean canUnlock(Player player, LockCode lock, Component containerName, @Nullable BlockEntity blockEntity) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && blockEntity != null && blockEntity.getLevel() != null && blockEntity.getLevel().getBlockEntity(blockEntity.getBlockPos()) == blockEntity) {
            final org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(blockEntity.getLevel(), blockEntity.getBlockPos());
            net.kyori.adventure.text.Component lockedMessage = net.kyori.adventure.text.Component.translatable("container.isLocked", io.papermc.paper.adventure.PaperAdventure.asAdventure(containerName));
            net.kyori.adventure.sound.Sound lockedSound = net.kyori.adventure.sound.Sound.sound(org.bukkit.Sound.BLOCK_CHEST_LOCKED, net.kyori.adventure.sound.Sound.Source.BLOCK, 1.0F, 1.0F);
            final io.papermc.paper.event.block.BlockLockCheckEvent event = new io.papermc.paper.event.block.BlockLockCheckEvent(block, (io.papermc.paper.block.LockableTileState) block.getState(), serverPlayer.getBukkitEntity(), lockedMessage, lockedSound);
            event.callEvent();
            if (event.getResult() == org.bukkit.event.Event.Result.ALLOW) {
                return true;
            } else if (event.getResult() == org.bukkit.event.Event.Result.DENY || (!player.isSpectator() && !lock.unlocksWith(event.isUsingCustomKeyItemStack() ? org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getKeyItem()) : player.getMainHandItem()))) {
                if (event.getLockedMessage() != null) {
                    event.getPlayer().sendActionBar(event.getLockedMessage());
                }
                if (event.getLockedSound() != null) {
                    event.getPlayer().playSound(event.getLockedSound());
                }
                return false;
            } else {
                return true;
            }
        } else { // logic below is replaced by logic above
        // Paper end
        if (!player.isSpectator() && !lock.unlocksWith(player.getMainHandItem())) {
            player.displayClientMessage(Component.translatable("container.isLocked", containerName), true); // Paper - diff on change
            player.playNotifySound(SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F);
            return false;
        } else {
            return true;
        }
        } // Paper
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return this.canOpen(player) ? this.createMenu(syncId, playerInventory) : null;
    }

    protected abstract AbstractContainerMenu createMenu(int syncId, Inventory playerInventory);

    // CraftBukkit start
    @Override
    public org.bukkit.Location getLocation() {
        if (this.level == null) return null;
        return new org.bukkit.Location(this.level.getWorld(), this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ());
    }
    // CraftBukkit end
}
