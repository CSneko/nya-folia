package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

public abstract class ContainerOpenersCounter {

    private static final int CHECK_TICK_DELAY = 5;
    private int openCount;
    public boolean opened; // CraftBukkit

    public ContainerOpenersCounter() {}

    protected abstract void onOpen(Level world, BlockPos pos, BlockState state);

    protected abstract void onClose(Level world, BlockPos pos, BlockState state);

    protected abstract void openerCountChanged(Level world, BlockPos pos, BlockState state, int oldViewerCount, int newViewerCount);

    // CraftBukkit start
    public void onAPIOpen(Level world, BlockPos blockposition, BlockState iblockdata) {
        this.onOpen(world, blockposition, iblockdata);
    }

    public void onAPIClose(Level world, BlockPos blockposition, BlockState iblockdata) {
        this.onClose(world, blockposition, iblockdata);
    }

    public void openerAPICountChanged(Level world, BlockPos blockposition, BlockState iblockdata, int i, int j) {
        this.openerCountChanged(world, blockposition, iblockdata, i, j);
    }
    // CraftBukkit end

    protected abstract boolean isOwnContainer(Player player);

    public void incrementOpeners(Player player, Level world, BlockPos pos, BlockState state) {
        int oldPower = Math.max(0, Math.min(15, this.openCount)); // CraftBukkit - Get power before new viewer is added
        int i = this.openCount++;

        // CraftBukkit start - Call redstone event
        if (world.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.TRAPPED_CHEST)) {
            int newPower = Math.max(0, Math.min(15, this.openCount));

            if (oldPower != newPower) {
                org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(world, pos, oldPower, newPower);
            }
        }
        // CraftBukkit end

        if (i == 0) {
            this.onOpen(world, pos, state);
            world.gameEvent((Entity) player, GameEvent.CONTAINER_OPEN, pos);
            ContainerOpenersCounter.scheduleRecheck(world, pos, state);
        }

        this.openerCountChanged(world, pos, state, i, this.openCount);
    }

    public void decrementOpeners(Player player, Level world, BlockPos pos, BlockState state) {
        int oldPower = Math.max(0, Math.min(15, this.openCount)); // CraftBukkit - Get power before new viewer is added
        if (this.openCount == 0) return; // Paper
        int i = this.openCount--;

        // CraftBukkit start - Call redstone event
        if (world.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.TRAPPED_CHEST)) {
            int newPower = Math.max(0, Math.min(15, this.openCount));

            if (oldPower != newPower) {
                org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(world, pos, oldPower, newPower);
            }
        }
        // CraftBukkit end

        if (this.openCount == 0) {
            this.onClose(world, pos, state);
            world.gameEvent((Entity) player, GameEvent.CONTAINER_CLOSE, pos);
        }

        this.openerCountChanged(world, pos, state, i, this.openCount);
    }

    private int getOpenCount(Level world, BlockPos pos) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        float f = 5.0F;
        AABB axisalignedbb = new AABB((double) ((float) i - 5.0F), (double) ((float) j - 5.0F), (double) ((float) k - 5.0F), (double) ((float) (i + 1) + 5.0F), (double) ((float) (j + 1) + 5.0F), (double) ((float) (k + 1) + 5.0F));

        return world.getEntities(EntityTypeTest.forClass(Player.class), axisalignedbb, this::isOwnContainer).size();
    }

    public void recheckOpeners(Level world, BlockPos pos, BlockState state) {
        int i = this.getOpenCount(world, pos);
        if (this.opened) i++; // CraftBukkit - add dummy count from API
        int j = this.openCount;

        if (j != i) {
            boolean flag = i != 0;
            boolean flag1 = j != 0;

            if (flag && !flag1) {
                this.onOpen(world, pos, state);
                world.gameEvent((Entity) null, GameEvent.CONTAINER_OPEN, pos);
            } else if (!flag) {
                this.onClose(world, pos, state);
                world.gameEvent((Entity) null, GameEvent.CONTAINER_CLOSE, pos);
            }

            this.openCount = i;
        }

        this.openerCountChanged(world, pos, state, j, i);
        if (i > 0) {
            ContainerOpenersCounter.scheduleRecheck(world, pos, state);
        }

    }

    public int getOpenerCount() {
        return this.openCount;
    }

    private static void scheduleRecheck(Level world, BlockPos pos, BlockState state) {
        world.scheduleTick(pos, state.getBlock(), 5);
    }
}
