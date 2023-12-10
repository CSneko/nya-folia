package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class BeehiveBlockEntity extends BlockEntity {

    public static final String TAG_FLOWER_POS = "FlowerPos";
    public static final String MIN_OCCUPATION_TICKS = "MinOccupationTicks";
    public static final String ENTITY_DATA = "EntityData";
    public static final String TICKS_IN_HIVE = "TicksInHive";
    public static final String HAS_NECTAR = "HasNectar";
    public static final String BEES = "Bees";
    private static final List<String> IGNORED_BEE_TAGS = Arrays.asList("Air", "ArmorDropChances", "ArmorItems", "Brain", "CanPickUpLoot", "DeathTime", "FallDistance", "FallFlying", "Fire", "HandDropChances", "HandItems", "HurtByTimestamp", "HurtTime", "LeftHanded", "Motion", "NoGravity", "OnGround", "PortalCooldown", "Pos", "Rotation", "CannotEnterHiveTicks", "TicksSincePollination", "CropsGrownSincePollination", "HivePos", "Passengers", "Leash", "UUID");
    public static final int MAX_OCCUPANTS = 3;
    private static final int MIN_TICKS_BEFORE_REENTERING_HIVE = 400;
    private static final int MIN_OCCUPATION_TICKS_NECTAR = 2400;
    public static final int MIN_OCCUPATION_TICKS_NECTARLESS = 600;
    private final List<BeehiveBlockEntity.BeeData> stored = Lists.newArrayList();
    @Nullable
    public BlockPos savedFlowerPos;
    public int maxBees = 3; // CraftBukkit - allow setting max amount of bees a hive can hold

    public BeehiveBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.BEEHIVE, pos, state);
    }

    @Override
    public void setChanged() {
        if (this.isFireNearby()) {
            this.emptyAllLivingFromHive((Player) null, this.level.getBlockState(this.getBlockPos()), BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        }

        super.setChanged();
    }

    public boolean isFireNearby() {
        if (this.level == null) {
            return false;
        } else {
            Iterator iterator = BlockPos.betweenClosed(this.worldPosition.offset(-1, -1, -1), this.worldPosition.offset(1, 1, 1)).iterator();

            BlockPos blockposition;

            do {
                if (!iterator.hasNext()) {
                    return false;
                }

                blockposition = (BlockPos) iterator.next();
            } while (!(this.level.getBlockState(blockposition).getBlock() instanceof FireBlock));

            return true;
        }
    }

    public boolean isEmpty() {
        return this.stored.isEmpty();
    }

    public boolean isFull() {
        return this.stored.size() == this.maxBees; // CraftBukkit
    }

    public void emptyAllLivingFromHive(@Nullable Player player, BlockState state, BeehiveBlockEntity.BeeReleaseStatus beeState) {
        List<Entity> list = this.releaseAllOccupants(state, beeState);

        if (player != null) {
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Entity entity = (Entity) iterator.next();

                if (entity instanceof Bee) {
                    Bee entitybee = (Bee) entity;

                    if (player.position().distanceToSqr(entity.position()) <= 16.0D) {
                        if (!this.isSedated()) {
                            entitybee.setTarget(player, org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER, true); // CraftBukkit
                        } else {
                            entitybee.setStayOutOfHiveCountdown(400);
                        }
                    }
                }
            }
        }

    }

    private List<Entity> releaseAllOccupants(BlockState state, BeehiveBlockEntity.BeeReleaseStatus beeState) {
        // CraftBukkit start - This allows us to bypass the night/rain/emergency check
        return this.releaseBees(state, beeState, false);
    }

    public List<Entity> releaseBees(BlockState iblockdata, BeehiveBlockEntity.BeeReleaseStatus tileentitybeehive_releasestatus, boolean force) {
        List<Entity> list = Lists.newArrayList();

        this.stored.removeIf((tileentitybeehive_hivebee) -> {
            return BeehiveBlockEntity.releaseBee(this.level, this.worldPosition, iblockdata, tileentitybeehive_hivebee, list, tileentitybeehive_releasestatus, this.savedFlowerPos, force);
            // CraftBukkit end
        });
        if (!list.isEmpty()) {
            super.setChanged();
        }

        return list;
    }

    public void addOccupant(Entity entity, boolean hasNectar) {
        this.addOccupantWithPresetTicks(entity, hasNectar, 0);
    }

    @VisibleForDebug
    public int getOccupantCount() {
        return this.stored.size();
    }

    // Paper start - Add EntityBlockStorage clearEntities
    public void clearBees() {
        this.stored.clear();
    }
    // Paper end
    public static int getHoneyLevel(BlockState state) {
        return (Integer) state.getValue(BeehiveBlock.HONEY_LEVEL);
    }

    @VisibleForDebug
    public boolean isSedated() {
        return CampfireBlock.isSmokeyPos(this.level, this.getBlockPos());
    }

    public void addOccupantWithPresetTicks(Entity entity, boolean hasNectar, int ticksInHive) {
        if (this.stored.size() < this.maxBees) { // CraftBukkit
            // CraftBukkit start
            if (this.level != null) {
                org.bukkit.event.entity.EntityEnterBlockEvent event = new org.bukkit.event.entity.EntityEnterBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(this.level, this.getBlockPos()));
                org.bukkit.Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    if (entity instanceof Bee) {
                        ((Bee) entity).setStayOutOfHiveCountdown(400);
                    }
                    return;
                }
            }
            // CraftBukkit end
            entity.stopRiding();
            entity.ejectPassengers();
            CompoundTag nbttagcompound = new CompoundTag();

            entity.save(nbttagcompound);
            this.storeBee(nbttagcompound, ticksInHive, hasNectar);
            if (this.level != null) {
                if (entity instanceof Bee) {
                    Bee entitybee = (Bee) entity;

                    if (entitybee.hasSavedFlowerPos() && (!this.hasSavedFlowerPos() || this.level.random.nextBoolean())) {
                        this.savedFlowerPos = entitybee.getSavedFlowerPos();
                    }
                }

                BlockPos blockposition = this.getBlockPos();

                this.level.playSound((Player) null, (double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 1.0F, 1.0F);
                this.level.gameEvent(GameEvent.BLOCK_CHANGE, blockposition, GameEvent.Context.of(entity, this.getBlockState()));
            }

            entity.discard();
            super.setChanged();
        }
    }

    public void storeBee(CompoundTag nbtCompound, int ticksInHive, boolean hasNectar) {
        this.stored.add(new BeehiveBlockEntity.BeeData(nbtCompound, ticksInHive, hasNectar ? 2400 : 600));
    }

    private static boolean releaseOccupant(Level world, BlockPos pos, BlockState state, BeehiveBlockEntity.BeeData bee, @Nullable List<Entity> entities, BeehiveBlockEntity.BeeReleaseStatus beeState, @Nullable BlockPos flowerPos) {
        // CraftBukkit start - This allows us to bypass the night/rain/emergency check
        return BeehiveBlockEntity.releaseBee(world, pos, state, bee, entities, beeState, flowerPos, false);
    }

    private static boolean releaseBee(Level world, BlockPos blockposition, BlockState iblockdata, BeehiveBlockEntity.BeeData tileentitybeehive_hivebee, @Nullable List<Entity> list, BeehiveBlockEntity.BeeReleaseStatus tileentitybeehive_releasestatus, @Nullable BlockPos blockposition1, boolean force) {
        if (!force && (world.isNight() || world.isRaining()) && tileentitybeehive_releasestatus != BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY) {
            // CraftBukkit end
            return false;
        } else {
            CompoundTag nbttagcompound = tileentitybeehive_hivebee.entityData.copy();

            BeehiveBlockEntity.removeIgnoredBeeTags(nbttagcompound);
            nbttagcompound.put("HivePos", NbtUtils.writeBlockPos(blockposition));
            nbttagcompound.putBoolean("NoGravity", true);
            Direction enumdirection = (Direction) iblockdata.getValue(BeehiveBlock.FACING);
            BlockPos blockposition2 = blockposition.relative(enumdirection);
            boolean flag = !world.getBlockState(blockposition2).getCollisionShape(world, blockposition2).isEmpty();

            if (flag && tileentitybeehive_releasestatus != BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY) {
                return false;
            } else {
                Entity entity = EntityType.loadEntityRecursive(nbttagcompound, world, (entity1) -> {
                    return entity1;
                });

                if (entity != null) {
                    if (!entity.getType().is(EntityTypeTags.BEEHIVE_INHABITORS)) {
                        return false;
                    } else {
                        // CraftBukkit start
                        if (entity instanceof Bee) {
                            float f = entity.getBbWidth();
                            double d0 = flag ? 0.0D : 0.55D + (double) (f / 2.0F);
                            double d1 = (double) blockposition.getX() + 0.5D + d0 * (double) enumdirection.getStepX();
                            double d2 = (double) blockposition.getY() + 0.5D - (double) (entity.getBbHeight() / 2.0F);
                            double d3 = (double) blockposition.getZ() + 0.5D + d0 * (double) enumdirection.getStepZ();

                            entity.moveTo(d1, d2, d3, entity.getYRot(), entity.getXRot());
                        }
                        if (!world.addFreshEntity(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BEEHIVE)) return false; // CraftBukkit - SpawnReason, moved from below
                        // CraftBukkit end
                        if (entity instanceof Bee) {
                            Bee entitybee = (Bee) entity;

                            if (blockposition1 != null && !entitybee.hasSavedFlowerPos() && world.random.nextFloat() < 0.9F) {
                                entitybee.setSavedFlowerPos(blockposition1);
                            }

                            if (tileentitybeehive_releasestatus == BeehiveBlockEntity.BeeReleaseStatus.HONEY_DELIVERED) {
                                entitybee.dropOffNectar();
                                if (iblockdata.is(BlockTags.BEEHIVES, (blockbase_blockdata) -> {
                                    return blockbase_blockdata.hasProperty(BeehiveBlock.HONEY_LEVEL);
                                })) {
                                    int i = BeehiveBlockEntity.getHoneyLevel(iblockdata);

                                    if (i < 5) {
                                        int j = world.random.nextInt(100) == 0 ? 2 : 1;

                                        if (i + j > 5) {
                                            --j;
                                        }

                                        world.setBlockAndUpdate(blockposition, (BlockState) iblockdata.setValue(BeehiveBlock.HONEY_LEVEL, i + j));
                                    }
                                }
                            }

                            BeehiveBlockEntity.setBeeReleaseData(tileentitybeehive_hivebee.ticksInHive, entitybee);
                            if (list != null) {
                                list.add(entitybee);
                            }

                            /* // CraftBukkit start
                            float f = entity.getBbWidth();
                            double d0 = flag ? 0.0D : 0.55D + (double) (f / 2.0F);
                            double d1 = (double) blockposition.getX() + 0.5D + d0 * (double) enumdirection.getStepX();
                            double d2 = (double) blockposition.getY() + 0.5D - (double) (entity.getBbHeight() / 2.0F);
                            double d3 = (double) blockposition.getZ() + 0.5D + d0 * (double) enumdirection.getStepZ();

                            entity.moveTo(d1, d2, d3, entity.getYRot(), entity.getXRot());
                             */ // CraftBukkit end
                        }

                        world.playSound((Player) null, blockposition, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 1.0F);
                        world.gameEvent(GameEvent.BLOCK_CHANGE, blockposition, GameEvent.Context.of(entity, world.getBlockState(blockposition)));
                        return true; // return this.world.addFreshEntity(entity); // CraftBukkit - moved up
                    }
                } else {
                    return false;
                }
            }
        }
    }

    static void removeIgnoredBeeTags(CompoundTag compound) {
        Iterator iterator = BeehiveBlockEntity.IGNORED_BEE_TAGS.iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();

            compound.remove(s);
        }

    }

    private static void setBeeReleaseData(int ticks, Bee bee) {
        if (!bee.ageLocked) { // Paper - respect age lock
        int j = bee.getAge();

        if (j < 0) {
            bee.setAge(Math.min(0, j + ticks));
        } else if (j > 0) {
            bee.setAge(Math.max(0, j - ticks));
        }
        } // Paper - respect age lock

        bee.setInLoveTime(Math.max(0, bee.getInLoveTime() - ticks));
    }

    private boolean hasSavedFlowerPos() {
        return this.savedFlowerPos != null;
    }

    private static void tickOccupants(Level world, BlockPos pos, BlockState state, List<BeehiveBlockEntity.BeeData> bees, @Nullable BlockPos flowerPos) {
        boolean flag = false;

        BeehiveBlockEntity.BeeData tileentitybeehive_hivebee;

        for (Iterator iterator = bees.iterator(); iterator.hasNext(); ++tileentitybeehive_hivebee.ticksInHive) {
            tileentitybeehive_hivebee = (BeehiveBlockEntity.BeeData) iterator.next();
            if (tileentitybeehive_hivebee.exitTickCounter > tileentitybeehive_hivebee.minOccupationTicks) { // Paper - use exitTickCounter
                BeehiveBlockEntity.BeeReleaseStatus tileentitybeehive_releasestatus = tileentitybeehive_hivebee.entityData.getBoolean("HasNectar") ? BeehiveBlockEntity.BeeReleaseStatus.HONEY_DELIVERED : BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED;

                if (BeehiveBlockEntity.releaseOccupant(world, pos, state, tileentitybeehive_hivebee, (List) null, tileentitybeehive_releasestatus, flowerPos)) {
                    flag = true;
                    iterator.remove();
                    // CraftBukkit start
                } else {
                    tileentitybeehive_hivebee.exitTickCounter = tileentitybeehive_hivebee.minOccupationTicks / 2; // Not strictly Vanilla behaviour in cases where bees cannot spawn but still reasonable // Paper - use exitTickCounter to keep actual bee life
                    // CraftBukkit end
                }
            }
            tileentitybeehive_hivebee.exitTickCounter++; // Paper
        }

        if (flag) {
            setChanged(world, pos, state);
        }

    }

    public static void serverTick(Level world, BlockPos pos, BlockState state, BeehiveBlockEntity blockEntity) {
        BeehiveBlockEntity.tickOccupants(world, pos, state, blockEntity.stored, blockEntity.savedFlowerPos);
        if (!blockEntity.stored.isEmpty() && world.getRandom().nextDouble() < 0.005D) {
            double d0 = (double) pos.getX() + 0.5D;
            double d1 = (double) pos.getY();
            double d2 = (double) pos.getZ() + 0.5D;

            world.playSound((Player) null, d0, d1, d2, SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        DebugPackets.sendHiveInfo(world, pos, state, blockEntity);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        this.stored.clear();
        ListTag nbttaglist = nbt.getList("Bees", 10);

        for (int i = 0; i < nbttaglist.size(); ++i) {
            CompoundTag nbttagcompound1 = nbttaglist.getCompound(i);
            BeehiveBlockEntity.BeeData tileentitybeehive_hivebee = new BeehiveBlockEntity.BeeData(nbttagcompound1.getCompound("EntityData"), nbttagcompound1.getInt("TicksInHive"), nbttagcompound1.getInt("MinOccupationTicks"));

            this.stored.add(tileentitybeehive_hivebee);
        }

        this.savedFlowerPos = null;
        if (nbt.contains("FlowerPos")) {
            this.savedFlowerPos = NbtUtils.readBlockPos(nbt.getCompound("FlowerPos"));
        }

        // CraftBukkit start
        if (nbt.contains("Bukkit.MaxEntities")) {
            this.maxBees = nbt.getInt("Bukkit.MaxEntities");
        }
        // CraftBukkit end
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.put("Bees", this.writeBees());
        if (this.hasSavedFlowerPos()) {
            nbt.put("FlowerPos", NbtUtils.writeBlockPos(this.savedFlowerPos));
        }
        nbt.putInt("Bukkit.MaxEntities", this.maxBees); // CraftBukkit

    }

    public ListTag writeBees() {
        ListTag nbttaglist = new ListTag();
        Iterator iterator = this.stored.iterator();

        while (iterator.hasNext()) {
            BeehiveBlockEntity.BeeData tileentitybeehive_hivebee = (BeehiveBlockEntity.BeeData) iterator.next();
            CompoundTag nbttagcompound = tileentitybeehive_hivebee.entityData.copy();

            nbttagcompound.remove("UUID");
            CompoundTag nbttagcompound1 = new CompoundTag();

            nbttagcompound1.put("EntityData", nbttagcompound);
            nbttagcompound1.putInt("TicksInHive", tileentitybeehive_hivebee.ticksInHive);
            nbttagcompound1.putInt("MinOccupationTicks", tileentitybeehive_hivebee.minOccupationTicks);
            nbttaglist.add(nbttagcompound1);
        }

        return nbttaglist;
    }

    public static enum BeeReleaseStatus {

        HONEY_DELIVERED, BEE_RELEASED, EMERGENCY;

        private BeeReleaseStatus() {}
    }

    private static class BeeData {

        final CompoundTag entityData;
        int ticksInHive;
        int exitTickCounter; // Paper - separate counter for checking if bee should exit to reduce exit attempts
        final int minOccupationTicks;

        BeeData(CompoundTag entityData, int ticksInHive, int minOccupationTicks) {
            BeehiveBlockEntity.removeIgnoredBeeTags(entityData);
            this.entityData = entityData;
            this.ticksInHive = ticksInHive;
            this.exitTickCounter = ticksInHive; // Paper
            this.minOccupationTicks = minOccupationTicks;
        }
    }
}
