package net.minecraft.world.level.block.entity;

import com.google.common.annotations.VisibleForTesting;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ContainerSingleItem;

// CraftBukkit start
import java.util.List;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class JukeboxBlockEntity extends BlockEntity implements Clearable, ContainerSingleItem {

    private static final int SONG_END_PADDING = 20;
    private final NonNullList<ItemStack> items;
    private int ticksSinceLastEvent;
    public long tickCount;
    public long recordStartedTick;
    public boolean isPlaying;
    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
    private int maxStack = MAX_STACK;
    public boolean opened;

    @Override
    public List<ItemStack> getContents() {
        return this.items;
    }

    @Override
    public void onOpen(CraftHumanEntity who) {
        this.transaction.add(who);
    }

    @Override
    public void onClose(CraftHumanEntity who) {
        this.transaction.remove(who);
    }

    @Override
    public List<HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }

    @Override
    public Location getLocation() {
        if (this.level == null) return null;
        return new org.bukkit.Location(this.level.getWorld(), this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ());
    }
    // CraftBukkit end

    public JukeboxBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.JUKEBOX, pos, state);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        if (nbt.contains("RecordItem", 10)) {
            this.items.set(0, ItemStack.of(nbt.getCompound("RecordItem")));
        }

        this.isPlaying = nbt.getBoolean("IsPlaying");
        this.recordStartedTick = nbt.getLong("RecordStartTick");
        this.tickCount = nbt.getLong("TickCount");
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        if (!this.getFirstItem().isEmpty()) {
            nbt.put("RecordItem", this.getFirstItem().save(new CompoundTag()));
        }

        nbt.putBoolean("IsPlaying", this.isPlaying);
        nbt.putLong("RecordStartTick", this.recordStartedTick);
        nbt.putLong("TickCount", this.tickCount);
    }

    public boolean isRecordPlaying() {
        return !this.getFirstItem().isEmpty() && this.isPlaying;
    }

    private void setHasRecordBlockState(@Nullable Entity entity, boolean hasRecord) {
        if (this.level.getBlockState(this.getBlockPos()) == this.getBlockState()) {
            this.level.setBlock(this.getBlockPos(), (BlockState) this.getBlockState().setValue(JukeboxBlock.HAS_RECORD, hasRecord), 2);
            this.level.gameEvent(GameEvent.BLOCK_CHANGE, this.getBlockPos(), GameEvent.Context.of(entity, this.getBlockState()));
        }

    }

    @VisibleForTesting
    public void startPlaying() {
        this.recordStartedTick = this.tickCount;
        this.isPlaying = true;
        this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        this.level.levelEvent((Player) null, 1010, this.getBlockPos(), Item.getId(this.getFirstItem().getItem()));
        this.setChanged();
    }

    private void stopPlaying() {
        this.isPlaying = false;
        this.level.gameEvent(GameEvent.JUKEBOX_STOP_PLAY, this.getBlockPos(), GameEvent.Context.of(this.getBlockState()));
        this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        this.level.levelEvent(1011, this.getBlockPos(), 0);
        this.setChanged();
    }

    private void tick(Level world, BlockPos pos, BlockState state) {
        ++this.ticksSinceLastEvent;
        if (this.isRecordPlaying()) {
            Item item = this.getFirstItem().getItem();

            if (item instanceof RecordItem) {
                RecordItem itemrecord = (RecordItem) item;

                if (this.shouldRecordStopPlaying(itemrecord)) {
                    this.stopPlaying();
                } else if (this.shouldSendJukeboxPlayingEvent()) {
                    this.ticksSinceLastEvent = 0;
                    world.gameEvent(GameEvent.JUKEBOX_PLAY, pos, GameEvent.Context.of(state));
                    this.spawnMusicParticles(world, pos);
                }
            }
        }

        ++this.tickCount;
    }

    private boolean shouldRecordStopPlaying(RecordItem musicDisc) {
        return this.tickCount >= this.recordStartedTick + (long) musicDisc.getLengthInTicks() + 20L;
    }

    private boolean shouldSendJukeboxPlayingEvent() {
        return this.ticksSinceLastEvent >= 20;
    }

    @Override
    public ItemStack getItem(int slot) {
        return (ItemStack) this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack itemstack = (ItemStack) Objects.requireNonNullElse((ItemStack) this.items.get(slot), ItemStack.EMPTY);

        this.items.set(slot, ItemStack.EMPTY);
        if (!itemstack.isEmpty()) {
            this.setHasRecordBlockState((Entity) null, false);
            this.stopPlaying();
        }

        return itemstack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (stack.is(ItemTags.MUSIC_DISCS) && this.level != null) {
            this.items.set(slot, stack);
            this.setHasRecordBlockState((Entity) null, true);
            this.startPlaying();
        } else if (stack.isEmpty()) {
            this.removeItem(slot, 1);
        }

    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack; // CraftBukkit
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.is(ItemTags.MUSIC_DISCS) && this.getItem(slot).isEmpty();
    }

    @Override
    public boolean canTakeItem(Container hopperInventory, int slot, ItemStack stack) {
        return hopperInventory.hasAnyMatching(ItemStack::isEmpty);
    }

    private void spawnMusicParticles(Level world, BlockPos pos) {
        if (world instanceof ServerLevel) {
            ServerLevel worldserver = (ServerLevel) world;
            Vec3 vec3d = Vec3.atBottomCenterOf(pos).add(0.0D, 1.2000000476837158D, 0.0D);
            float f = (float) world.getRandom().nextInt(4) / 24.0F;

            worldserver.sendParticles(ParticleTypes.NOTE, vec3d.x(), vec3d.y(), vec3d.z(), 0, (double) f, 0.0D, 0.0D, 1.0D);
        }

    }

    public void popOutRecord() {
        if (this.level != null && !this.level.isClientSide) {
            BlockPos blockposition = this.getBlockPos();
            ItemStack itemstack = this.getFirstItem();

            if (!itemstack.isEmpty()) {
                this.removeFirstItem();
                Vec3 vec3d = Vec3.atLowerCornerWithOffset(blockposition, 0.5D, 1.01D, 0.5D).offsetRandom(this.level.random, 0.7F);
                ItemStack itemstack1 = itemstack.copy();
                ItemEntity entityitem = new ItemEntity(this.level, vec3d.x(), vec3d.y(), vec3d.z(), itemstack1);

                entityitem.setDefaultPickUpDelay();
                this.level.addFreshEntity(entityitem);
            }
        }
    }

    public static void playRecordTick(Level world, BlockPos pos, BlockState state, JukeboxBlockEntity blockEntity) {
        blockEntity.tick(world, pos, state);
    }

    @VisibleForTesting
    public void setRecordWithoutPlaying(ItemStack stack) {
        this.items.set(0, stack);
        // CraftBukkit start - add null check for level
        if (this.level != null) {
            this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        }
        // CraftBukkit end
        this.setChanged();
    }
}
