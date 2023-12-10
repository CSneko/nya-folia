package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

// CraftBukkit start
import java.util.Arrays;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class BrushableBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOOT_TABLE_TAG = "LootTable";
    private static final String LOOT_TABLE_SEED_TAG = "LootTableSeed";
    private static final String HIT_DIRECTION_TAG = "hit_direction";
    private static final String ITEM_TAG = "item";
    private static final int BRUSH_COOLDOWN_TICKS = 10;
    private static final int BRUSH_RESET_TICKS = 40;
    private static final int REQUIRED_BRUSHES_TO_BREAK = 10;
    private int brushCount;
    private long brushCountResetsAtTick;
    private long coolDownEndsAtTick;
    public ItemStack item;
    @Nullable
    private Direction hitDirection;
    @Nullable
    public ResourceLocation lootTable;
    public long lootTableSeed;

    public BrushableBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.BRUSHABLE_BLOCK, pos, state);
        this.item = ItemStack.EMPTY;
    }

    public boolean brush(long worldTime, Player player, Direction hitDirection) {
        if (this.hitDirection == null) {
            this.hitDirection = hitDirection;
        }

        this.brushCountResetsAtTick = worldTime + 40L;
        if (worldTime >= this.coolDownEndsAtTick && this.level instanceof ServerLevel) {
            this.coolDownEndsAtTick = worldTime + 10L;
            this.unpackLootTable(player);
            int j = this.getCompletionState();

            if (++this.brushCount >= 10) {
                this.brushingCompleted(player);
                return true;
            } else {
                this.level.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), 40);
                int k = this.getCompletionState();

                if (j != k) {
                    BlockState iblockdata = this.getBlockState();
                    BlockState iblockdata1 = (BlockState) iblockdata.setValue(BlockStateProperties.DUSTED, k);

                    this.level.setBlock(this.getBlockPos(), iblockdata1, 3);
                }

                return false;
            }
        } else {
            return false;
        }
    }

    public void unpackLootTable(Player player) {
        if (this.lootTable != null && this.level != null && !this.level.isClientSide() && this.level.getServer() != null) {
            LootTable loottable = this.level.getServer().getLootData().getLootTable(this.lootTable);

            if (player instanceof ServerPlayer) {
                ServerPlayer entityplayer = (ServerPlayer) player;

                CriteriaTriggers.GENERATE_LOOT.trigger(entityplayer, this.lootTable);
            }

            LootParams lootparams = (new LootParams.Builder((ServerLevel) this.level)).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(this.worldPosition)).withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player).create(LootContextParamSets.CHEST);
            ObjectArrayList<ItemStack> objectarraylist = loottable.getRandomItems(lootparams, this.lootTableSeed);
            ItemStack itemstack;

            switch (objectarraylist.size()) {
                case 0:
                    itemstack = ItemStack.EMPTY;
                    break;
                case 1:
                    itemstack = (ItemStack) objectarraylist.get(0);
                    break;
                default:
                    BrushableBlockEntity.LOGGER.warn("Expected max 1 loot from loot table " + this.lootTable + " got " + objectarraylist.size());
                    itemstack = (ItemStack) objectarraylist.get(0);
            }

            this.item = itemstack;
            this.lootTable = null;
            this.setChanged();
        }
    }

    private void brushingCompleted(Player player) {
        if (this.level != null && this.level.getServer() != null) {
            this.dropContent(player);
            BlockState iblockdata = this.getBlockState();

            this.level.levelEvent(3008, this.getBlockPos(), Block.getId(iblockdata));
            Block block = this.getBlockState().getBlock();
            Block block1;

            if (block instanceof BrushableBlock) {
                BrushableBlock brushableblock = (BrushableBlock) block;

                block1 = brushableblock.getTurnsInto();
            } else {
                block1 = Blocks.AIR;
            }

            this.level.setBlock(this.worldPosition, block1.defaultBlockState(), 3);
        }
    }

    private void dropContent(Player player) {
        if (this.level != null && this.level.getServer() != null) {
            this.unpackLootTable(player);
            if (!this.item.isEmpty()) {
                double d0 = (double) EntityType.ITEM.getWidth();
                double d1 = 1.0D - d0;
                double d2 = d0 / 2.0D;
                Direction enumdirection = (Direction) Objects.requireNonNullElse(this.hitDirection, Direction.UP);
                BlockPos blockposition = this.worldPosition.relative(enumdirection, 1);
                double d3 = (double) blockposition.getX() + 0.5D * d1 + d2;
                double d4 = (double) blockposition.getY() + 0.5D + (double) (EntityType.ITEM.getHeight() / 2.0F);
                double d5 = (double) blockposition.getZ() + 0.5D * d1 + d2;
                ItemEntity entityitem = new ItemEntity(this.level, d3, d4, d5, this.item.split(this.level.random.nextInt(21) + 10));

                entityitem.setDeltaMovement(Vec3.ZERO);
                // CraftBukkit start
                org.bukkit.block.Block bblock = CraftBlock.at(this.level, this.worldPosition);
                CraftEventFactory.handleBlockDropItemEvent(bblock, bblock.getState(), (ServerPlayer) player, Arrays.asList(entityitem));
                // CraftBukkit end
                this.item = ItemStack.EMPTY;
            }

        }
    }

    public void checkReset() {
        if (this.level != null) {
            if (this.brushCount != 0 && this.level.getGameTime() >= this.brushCountResetsAtTick) {
                int i = this.getCompletionState();

                this.brushCount = Math.max(0, this.brushCount - 2);
                int j = this.getCompletionState();

                if (i != j) {
                    this.level.setBlock(this.getBlockPos(), (BlockState) this.getBlockState().setValue(BlockStateProperties.DUSTED, j), 3);
                }

                boolean flag = true;

                this.brushCountResetsAtTick = this.level.getGameTime() + 4L;
            }

            if (this.brushCount == 0) {
                this.hitDirection = null;
                this.brushCountResetsAtTick = 0L;
                this.coolDownEndsAtTick = 0L;
            } else {
                this.level.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), (int) (this.brushCountResetsAtTick - this.level.getGameTime()));
            }

        }
    }

    private boolean tryLoadLootTable(CompoundTag nbt) {
        if (nbt.contains("LootTable", 8)) {
            this.lootTable = new ResourceLocation(nbt.getString("LootTable"));
            this.lootTableSeed = nbt.getLong("LootTableSeed");
            return true;
        } else {
            return false;
        }
    }

    private boolean trySaveLootTable(CompoundTag nbt) {
        if (this.lootTable == null) {
            return false;
        } else {
            nbt.putString("LootTable", this.lootTable.toString());
            if (this.lootTableSeed != 0L) {
                nbt.putLong("LootTableSeed", this.lootTableSeed);
            }

            return true;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag nbttagcompound = super.getUpdateTag();

        if (this.hitDirection != null) {
            nbttagcompound.putInt("hit_direction", this.hitDirection.ordinal());
        }

        nbttagcompound.put("item", this.item.save(new CompoundTag()));
        return nbttagcompound;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt); // CraftBukkit - SPIGOT-7393: Load super Bukkit data
        if (!this.tryLoadLootTable(nbt) && nbt.contains("item")) {
            this.item = ItemStack.of(nbt.getCompound("item"));
        }

        if (nbt.contains("hit_direction")) {
            this.hitDirection = Direction.values()[nbt.getInt("hit_direction")];
        }

    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        if (!this.trySaveLootTable(nbt)) {
            nbt.put("item", this.item.save(new CompoundTag()));
        }

    }

    public void setLootTable(ResourceLocation lootTable, long seed) {
        this.lootTable = lootTable;
        this.lootTableSeed = seed;
    }

    private int getCompletionState() {
        return this.brushCount == 0 ? 0 : (this.brushCount < 3 ? 1 : (this.brushCount < 6 ? 2 : 3));
    }

    @Nullable
    public Direction getHitDirection() {
        return this.hitDirection;
    }

    public ItemStack getItem() {
        return this.item;
    }
}
