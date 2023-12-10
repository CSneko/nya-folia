package net.minecraft.world.level.border;

import com.google.common.collect.Lists;
import com.mojang.serialization.DynamicLike;
import java.util.Iterator;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WorldBorder {

    public static final double MAX_SIZE = 5.9999968E7D;
    public static final double MAX_CENTER_COORDINATE = 2.9999984E7D;
    private final List<BorderChangeListener> listeners = Lists.newArrayList();
    private double damagePerBlock = 0.2D;
    private double damageSafeZone = 5.0D;
    private int warningTime = 15;
    private int warningBlocks = 5;
    private double centerX;
    private double centerZ;
    int absoluteMaxSize = 29999984;
    private WorldBorder.BorderExtent extent = new WorldBorder.StaticBorderExtent(5.9999968E7D);
    public static final WorldBorder.Settings DEFAULT_SETTINGS = new WorldBorder.Settings(0.0D, 0.0D, 0.2D, 5.0D, 5, 15, 5.9999968E7D, 0L, 0.0D);
    public net.minecraft.server.level.ServerLevel world; // CraftBukkit

    public WorldBorder() {}

    // Folia - region threading - TODO make this shit thread-safe

    public boolean isWithinBounds(BlockPos pos) {
        return (double) (pos.getX() + 1) > this.getMinX() && (double) pos.getX() < this.getMaxX() && (double) (pos.getZ() + 1) > this.getMinZ() && (double) pos.getZ() < this.getMaxZ();
    }

    // Paper start
    private static final ThreadLocal<BlockPos.MutableBlockPos> mutPos = ThreadLocal.withInitial(() -> new BlockPos.MutableBlockPos()); // Folia - region threading
    public boolean isBlockInBounds(int chunkX, int chunkZ) {
        return this.isWithinBounds(mutPos.get().set(chunkX, 64, chunkZ)); // Folia - region threading
    }
    public boolean isChunkInBounds(int chunkX, int chunkZ) {
        return this.isWithinBounds(mutPos.get().set(((chunkX << 4) + 15), 64, (chunkZ << 4) + 15)); // Folia - region threading
    }
    // Paper end

    public boolean isWithinBounds(ChunkPos pos) {
        return (double) pos.getMaxBlockX() > this.getMinX() && (double) pos.getMinBlockX() < this.getMaxX() && (double) pos.getMaxBlockZ() > this.getMinZ() && (double) pos.getMinBlockZ() < this.getMaxZ();
    }

    public boolean isWithinBounds(double x, double z) {
        return x > this.getMinX() && x < this.getMaxX() && z > this.getMinZ() && z < this.getMaxZ();
    }

    public boolean isWithinBounds(double x, double z, double margin) {
        return x > this.getMinX() - margin && x < this.getMaxX() + margin && z > this.getMinZ() - margin && z < this.getMaxZ() + margin;
    }

    public boolean isWithinBounds(AABB box) {
        return box.maxX > this.getMinX() && box.minX < this.getMaxX() && box.maxZ > this.getMinZ() && box.minZ < this.getMaxZ();
    }

    public BlockPos clampToBounds(double x, double y, double z) {
        return BlockPos.containing(Mth.clamp(x, this.getMinX(), this.getMaxX()), y, Mth.clamp(z, this.getMinZ(), this.getMaxZ()));
    }

    public double getDistanceToBorder(Entity entity) {
        return this.getDistanceToBorder(entity.getX(), entity.getZ());
    }

    public VoxelShape getCollisionShape() {
        return this.extent.getCollisionShape();
    }

    public double getDistanceToBorder(double x, double z) {
        double d2 = z - this.getMinZ();
        double d3 = this.getMaxZ() - z;
        double d4 = x - this.getMinX();
        double d5 = this.getMaxX() - x;
        double d6 = Math.min(d4, d5);

        d6 = Math.min(d6, d2);
        return Math.min(d6, d3);
    }

    public boolean isInsideCloseToBorder(Entity entity, AABB box) {
        double d0 = Math.max(Mth.absMax(box.getXsize(), box.getZsize()), 1.0D);

        return this.getDistanceToBorder(entity) < d0 * 2.0D && this.isWithinBounds(entity.getX(), entity.getZ(), d0);
    }

    public BorderStatus getStatus() {
        return this.extent.getStatus();
    }

    public double getMinX() {
        return this.extent.getMinX();
    }

    public double getMinZ() {
        return this.extent.getMinZ();
    }

    public double getMaxX() {
        return this.extent.getMaxX();
    }

    public double getMaxZ() {
        return this.extent.getMaxZ();
    }

    public double getCenterX() {
        return this.centerX;
    }

    public double getCenterZ() {
        return this.centerZ;
    }

    public void setCenter(double x, double z) {
        // Paper start
        if (this.world != null) {
            io.papermc.paper.event.world.border.WorldBorderCenterChangeEvent event = new io.papermc.paper.event.world.border.WorldBorderCenterChangeEvent(world.getWorld(), world.getWorld().getWorldBorder(), new org.bukkit.Location(world.getWorld(), this.getCenterX(), 0, this.getCenterZ()), new org.bukkit.Location(world.getWorld(), x, 0, z));
            if (!event.callEvent()) return;
            x = event.getNewCenter().getX();
            z = event.getNewCenter().getZ();
        }
        // Paper end
        this.centerX = x;
        this.centerZ = z;
        this.extent.onCenterChange();
        Iterator iterator = this.getListeners().iterator();

        while (iterator.hasNext()) {
            BorderChangeListener iworldborderlistener = (BorderChangeListener) iterator.next();

            iworldborderlistener.onBorderCenterSet(this, x, z);
        }

    }

    public double getSize() {
        return this.extent.getSize();
    }

    public long getLerpRemainingTime() {
        return this.extent.getLerpRemainingTime();
    }

    public double getLerpTarget() {
        return this.extent.getLerpTarget();
    }

    public void setSize(double size) {
        // Paper start
        if (this.world != null) {
            io.papermc.paper.event.world.border.WorldBorderBoundsChangeEvent event = new io.papermc.paper.event.world.border.WorldBorderBoundsChangeEvent(world.getWorld(), world.getWorld().getWorldBorder(), io.papermc.paper.event.world.border.WorldBorderBoundsChangeEvent.Type.INSTANT_MOVE, getSize(), size, 0);
            if (!event.callEvent()) return;
            if (event.getType() == io.papermc.paper.event.world.border.WorldBorderBoundsChangeEvent.Type.STARTED_MOVE && event.getDuration() > 0) { // If changed to a timed transition
                lerpSizeBetween(event.getOldSize(), event.getNewSize(), event.getDuration());
                return;
            }
            size = event.getNewSize();
        }
        // Paper end
        this.extent = new WorldBorder.StaticBorderExtent(size);
        Iterator iterator = this.getListeners().iterator();

        while (iterator.hasNext()) {
            BorderChangeListener iworldborderlistener = (BorderChangeListener) iterator.next();

            iworldborderlistener.onBorderSizeSet(this, size);
        }

    }

    public void lerpSizeBetween(double fromSize, double toSize, long time) {
        // Paper start
        if (this.world != null) {
            io.papermc.paper.event.world.border.WorldBorderBoundsChangeEvent.Type type;
            if (fromSize == toSize) { // new size = old size
                type = io.papermc.paper.event.world.border.WorldBorderBoundsChangeEvent.Type.INSTANT_MOVE; // Use INSTANT_MOVE because below it creates a Static border if they are equal.
            } else {
                type = io.papermc.paper.event.world.border.WorldBorderBoundsChangeEvent.Type.STARTED_MOVE;
            }
            io.papermc.paper.event.world.border.WorldBorderBoundsChangeEvent event = new io.papermc.paper.event.world.border.WorldBorderBoundsChangeEvent(world.getWorld(), world.getWorld().getWorldBorder(), type, fromSize, toSize, time);
            if (!event.callEvent()) return;
            toSize = event.getNewSize();
            time = event.getDuration();
        }
        // Paper end
        this.extent = (WorldBorder.BorderExtent) (fromSize == toSize ? new WorldBorder.StaticBorderExtent(toSize) : new WorldBorder.MovingBorderExtent(fromSize, toSize, time));
        Iterator iterator = this.getListeners().iterator();

        while (iterator.hasNext()) {
            BorderChangeListener iworldborderlistener = (BorderChangeListener) iterator.next();

            iworldborderlistener.onBorderSizeLerping(this, fromSize, toSize, time);
        }

    }

    protected List<BorderChangeListener> getListeners() {
        return Lists.newArrayList(this.listeners);
    }

    public void addListener(BorderChangeListener listener) {
        if (this.listeners.contains(listener)) return; // CraftBukkit
        this.listeners.add(listener);
    }

    public void removeListener(BorderChangeListener listener) {
        this.listeners.remove(listener);
    }

    public void setAbsoluteMaxSize(int maxRadius) {
        this.absoluteMaxSize = maxRadius;
        this.extent.onAbsoluteMaxSizeChange();
    }

    public int getAbsoluteMaxSize() {
        return this.absoluteMaxSize;
    }

    public double getDamageSafeZone() {
        return this.damageSafeZone;
    }

    public void setDamageSafeZone(double safeZone) {
        this.damageSafeZone = safeZone;
        Iterator iterator = this.getListeners().iterator();

        while (iterator.hasNext()) {
            BorderChangeListener iworldborderlistener = (BorderChangeListener) iterator.next();

            iworldborderlistener.onBorderSetDamageSafeZOne(this, safeZone);
        }

    }

    public double getDamagePerBlock() {
        return this.damagePerBlock;
    }

    public void setDamagePerBlock(double damagePerBlock) {
        this.damagePerBlock = damagePerBlock;
        Iterator iterator = this.getListeners().iterator();

        while (iterator.hasNext()) {
            BorderChangeListener iworldborderlistener = (BorderChangeListener) iterator.next();

            iworldborderlistener.onBorderSetDamagePerBlock(this, damagePerBlock);
        }

    }

    public double getLerpSpeed() {
        return this.extent.getLerpSpeed();
    }

    public int getWarningTime() {
        return this.warningTime;
    }

    public void setWarningTime(int warningTime) {
        this.warningTime = warningTime;
        Iterator iterator = this.getListeners().iterator();

        while (iterator.hasNext()) {
            BorderChangeListener iworldborderlistener = (BorderChangeListener) iterator.next();

            iworldborderlistener.onBorderSetWarningTime(this, warningTime);
        }

    }

    public int getWarningBlocks() {
        return this.warningBlocks;
    }

    public void setWarningBlocks(int warningBlocks) {
        this.warningBlocks = warningBlocks;
        Iterator iterator = this.getListeners().iterator();

        while (iterator.hasNext()) {
            BorderChangeListener iworldborderlistener = (BorderChangeListener) iterator.next();

            iworldborderlistener.onBorderSetWarningBlocks(this, warningBlocks);
        }

    }

    public void tick() {
        this.extent = this.extent.update();
    }

    public WorldBorder.Settings createSettings() {
        return new WorldBorder.Settings(this);
    }

    public void applySettings(WorldBorder.Settings properties) {
        this.setCenter(properties.getCenterX(), properties.getCenterZ());
        this.setDamagePerBlock(properties.getDamagePerBlock());
        this.setDamageSafeZone(properties.getSafeZone());
        this.setWarningBlocks(properties.getWarningBlocks());
        this.setWarningTime(properties.getWarningTime());
        if (properties.getSizeLerpTime() > 0L) {
            this.lerpSizeBetween(properties.getSize(), properties.getSizeLerpTarget(), properties.getSizeLerpTime());
        } else {
            this.setSize(properties.getSize());
        }

    }

    private class StaticBorderExtent implements WorldBorder.BorderExtent {

        private final double size;
        private double minX;
        private double minZ;
        private double maxX;
        private double maxZ;
        private VoxelShape shape;

        public StaticBorderExtent(double d0) {
            this.size = d0;
            this.updateBox();
        }

        @Override
        public double getMinX() {
            return this.minX;
        }

        @Override
        public double getMaxX() {
            return this.maxX;
        }

        @Override
        public double getMinZ() {
            return this.minZ;
        }

        @Override
        public double getMaxZ() {
            return this.maxZ;
        }

        @Override
        public double getSize() {
            return this.size;
        }

        @Override
        public BorderStatus getStatus() {
            return BorderStatus.STATIONARY;
        }

        @Override
        public double getLerpSpeed() {
            return 0.0D;
        }

        @Override
        public long getLerpRemainingTime() {
            return 0L;
        }

        @Override
        public double getLerpTarget() {
            return this.size;
        }

        private void updateBox() {
            this.minX = Mth.clamp(WorldBorder.this.getCenterX() - this.size / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
            this.minZ = Mth.clamp(WorldBorder.this.getCenterZ() - this.size / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
            this.maxX = Mth.clamp(WorldBorder.this.getCenterX() + this.size / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
            this.maxZ = Mth.clamp(WorldBorder.this.getCenterZ() + this.size / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
            this.shape = Shapes.join(Shapes.INFINITY, Shapes.box(Math.floor(this.getMinX()), Double.NEGATIVE_INFINITY, Math.floor(this.getMinZ()), Math.ceil(this.getMaxX()), Double.POSITIVE_INFINITY, Math.ceil(this.getMaxZ())), BooleanOp.ONLY_FIRST);
        }

        @Override
        public void onAbsoluteMaxSizeChange() {
            this.updateBox();
        }

        @Override
        public void onCenterChange() {
            this.updateBox();
        }

        @Override
        public WorldBorder.BorderExtent update() {
            return this;
        }

        @Override
        public VoxelShape getCollisionShape() {
            return this.shape;
        }
    }

    private interface BorderExtent {

        double getMinX();

        double getMaxX();

        double getMinZ();

        double getMaxZ();

        double getSize();

        double getLerpSpeed();

        long getLerpRemainingTime();

        double getLerpTarget();

        BorderStatus getStatus();

        void onAbsoluteMaxSizeChange();

        void onCenterChange();

        WorldBorder.BorderExtent update();

        VoxelShape getCollisionShape();
    }

    private class MovingBorderExtent implements WorldBorder.BorderExtent {

        private final double from;
        private final double to;
        private final long lerpEnd;
        private final long lerpBegin;
        private final double lerpDuration;

        MovingBorderExtent(double d0, double d1, long i) {
            this.from = d0;
            this.to = d1;
            this.lerpDuration = (double) i;
            this.lerpBegin = Util.getMillis();
            this.lerpEnd = this.lerpBegin + i;
        }

        @Override
        public double getMinX() {
            return Mth.clamp(WorldBorder.this.getCenterX() - this.getSize() / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
        }

        @Override
        public double getMinZ() {
            return Mth.clamp(WorldBorder.this.getCenterZ() - this.getSize() / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
        }

        @Override
        public double getMaxX() {
            return Mth.clamp(WorldBorder.this.getCenterX() + this.getSize() / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
        }

        @Override
        public double getMaxZ() {
            return Mth.clamp(WorldBorder.this.getCenterZ() + this.getSize() / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
        }

        @Override
        public double getSize() {
            double d0 = (double) (Util.getMillis() - this.lerpBegin) / this.lerpDuration;

            return d0 < 1.0D ? Mth.lerp(d0, this.from, this.to) : this.to;
        }

        @Override
        public double getLerpSpeed() {
            return Math.abs(this.from - this.to) / (double) (this.lerpEnd - this.lerpBegin);
        }

        @Override
        public long getLerpRemainingTime() {
            return this.lerpEnd - Util.getMillis();
        }

        @Override
        public double getLerpTarget() {
            return this.to;
        }

        @Override
        public BorderStatus getStatus() {
            return this.to < this.from ? BorderStatus.SHRINKING : BorderStatus.GROWING;
        }

        @Override
        public void onCenterChange() {}

        @Override
        public void onAbsoluteMaxSizeChange() {}

        @Override
        public WorldBorder.BorderExtent update() {
            if (world != null && this.getLerpRemainingTime() <= 0L) new io.papermc.paper.event.world.border.WorldBorderBoundsChangeFinishEvent(world.getWorld(), world.getWorld().getWorldBorder(), this.from, this.to, this.lerpDuration).callEvent(); // Paper
            return (WorldBorder.BorderExtent) (this.getLerpRemainingTime() <= 0L ? WorldBorder.this.new StaticBorderExtent(this.to) : this);
        }

        @Override
        public VoxelShape getCollisionShape() {
            return Shapes.join(Shapes.INFINITY, Shapes.box(Math.floor(this.getMinX()), Double.NEGATIVE_INFINITY, Math.floor(this.getMinZ()), Math.ceil(this.getMaxX()), Double.POSITIVE_INFINITY, Math.ceil(this.getMaxZ())), BooleanOp.ONLY_FIRST);
        }
    }

    public static class Settings {

        private final double centerX;
        private final double centerZ;
        private final double damagePerBlock;
        private final double safeZone;
        private final int warningBlocks;
        private final int warningTime;
        private final double size;
        private final long sizeLerpTime;
        private final double sizeLerpTarget;

        Settings(double centerX, double centerZ, double damagePerBlock, double safeZone, int warningBlocks, int warningTime, double size, long sizeLerpTime, double sizeLerpTarget) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.damagePerBlock = damagePerBlock;
            this.safeZone = safeZone;
            this.warningBlocks = warningBlocks;
            this.warningTime = warningTime;
            this.size = size;
            this.sizeLerpTime = sizeLerpTime;
            this.sizeLerpTarget = sizeLerpTarget;
        }

        Settings(WorldBorder worldBorder) {
            this.centerX = worldBorder.getCenterX();
            this.centerZ = worldBorder.getCenterZ();
            this.damagePerBlock = worldBorder.getDamagePerBlock();
            this.safeZone = worldBorder.getDamageSafeZone();
            this.warningBlocks = worldBorder.getWarningBlocks();
            this.warningTime = worldBorder.getWarningTime();
            this.size = worldBorder.getSize();
            this.sizeLerpTime = worldBorder.getLerpRemainingTime();
            this.sizeLerpTarget = worldBorder.getLerpTarget();
        }

        public double getCenterX() {
            return this.centerX;
        }

        public double getCenterZ() {
            return this.centerZ;
        }

        public double getDamagePerBlock() {
            return this.damagePerBlock;
        }

        public double getSafeZone() {
            return this.safeZone;
        }

        public int getWarningBlocks() {
            return this.warningBlocks;
        }

        public int getWarningTime() {
            return this.warningTime;
        }

        public double getSize() {
            return this.size;
        }

        public long getSizeLerpTime() {
            return this.sizeLerpTime;
        }

        public double getSizeLerpTarget() {
            return this.sizeLerpTarget;
        }

        public static WorldBorder.Settings read(DynamicLike<?> dynamic, WorldBorder.Settings properties) {
            double d0 = Mth.clamp(dynamic.get("BorderCenterX").asDouble(properties.centerX), -2.9999984E7D, 2.9999984E7D);
            double d1 = Mth.clamp(dynamic.get("BorderCenterZ").asDouble(properties.centerZ), -2.9999984E7D, 2.9999984E7D);
            double d2 = dynamic.get("BorderSize").asDouble(properties.size);
            long i = dynamic.get("BorderSizeLerpTime").asLong(properties.sizeLerpTime);
            double d3 = dynamic.get("BorderSizeLerpTarget").asDouble(properties.sizeLerpTarget);
            double d4 = dynamic.get("BorderSafeZone").asDouble(properties.safeZone);
            double d5 = dynamic.get("BorderDamagePerBlock").asDouble(properties.damagePerBlock);
            int j = dynamic.get("BorderWarningBlocks").asInt(properties.warningBlocks);
            int k = dynamic.get("BorderWarningTime").asInt(properties.warningTime);

            return new WorldBorder.Settings(d0, d1, d5, d4, j, k, d2, i, d3);
        }

        public void write(CompoundTag nbt) {
            nbt.putDouble("BorderCenterX", this.centerX);
            nbt.putDouble("BorderCenterZ", this.centerZ);
            nbt.putDouble("BorderSize", this.size);
            nbt.putLong("BorderSizeLerpTime", this.sizeLerpTime);
            nbt.putDouble("BorderSafeZone", this.safeZone);
            nbt.putDouble("BorderDamagePerBlock", this.damagePerBlock);
            nbt.putDouble("BorderSizeLerpTarget", this.sizeLerpTarget);
            nbt.putDouble("BorderWarningBlocks", (double) this.warningBlocks);
            nbt.putDouble("BorderWarningTime", (double) this.warningTime);
        }
    }
}
