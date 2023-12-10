package net.minecraft.world.level.block;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.event.block.SculkBloomEvent;
// CraftBukkit end

public class SculkSpreader {

    public static final int MAX_GROWTH_RATE_RADIUS = 24;
    public static final int MAX_CHARGE = 1000;
    public static final float MAX_DECAY_FACTOR = 0.5F;
    private static final int MAX_CURSORS = 32;
    public static final int SHRIEKER_PLACEMENT_RATE = 11;
    final boolean isWorldGeneration;
    private final TagKey<Block> replaceableBlocks;
    private final int growthSpawnCost;
    private final int noGrowthRadius;
    private final int chargeDecayRate;
    private final int additionalDecayRate;
    private List<SculkSpreader.ChargeCursor> cursors = new ArrayList();
    private static final Logger LOGGER = LogUtils.getLogger();
    public Level level; // CraftBukkit

    public SculkSpreader(boolean worldGen, TagKey<Block> replaceableTag, int extraBlockChance, int maxDistance, int spreadChance, int decayChance) {
        this.isWorldGeneration = worldGen;
        this.replaceableBlocks = replaceableTag;
        this.growthSpawnCost = extraBlockChance;
        this.noGrowthRadius = maxDistance;
        this.chargeDecayRate = spreadChance;
        this.additionalDecayRate = decayChance;
    }

    public static SculkSpreader createLevelSpreader() {
        return new SculkSpreader(false, BlockTags.SCULK_REPLACEABLE, 10, 4, 10, 5);
    }

    public static SculkSpreader createWorldGenSpreader() {
        return new SculkSpreader(true, BlockTags.SCULK_REPLACEABLE_WORLD_GEN, 50, 1, 5, 10);
    }

    public TagKey<Block> replaceableBlocks() {
        return this.replaceableBlocks;
    }

    public int growthSpawnCost() {
        return this.growthSpawnCost;
    }

    public int noGrowthRadius() {
        return this.noGrowthRadius;
    }

    public int chargeDecayRate() {
        return this.chargeDecayRate;
    }

    public int additionalDecayRate() {
        return this.additionalDecayRate;
    }

    public boolean isWorldGeneration() {
        return this.isWorldGeneration;
    }

    @VisibleForTesting
    public List<SculkSpreader.ChargeCursor> getCursors() {
        return this.cursors;
    }

    public void clear() {
        this.cursors.clear();
    }

    public void load(CompoundTag nbt) {
        if (nbt.contains("cursors", 9)) {
            this.cursors.clear();
            DataResult<List<SculkSpreader.ChargeCursor>> dataresult = SculkSpreader.ChargeCursor.CODEC.listOf().parse(new Dynamic<>(NbtOps.INSTANCE, nbt.getList("cursors", 10))); // CraftBukkit - decompile error
            Logger logger = SculkSpreader.LOGGER;

            Objects.requireNonNull(logger);
            List<SculkSpreader.ChargeCursor> list = (List) dataresult.resultOrPartial(logger::error).orElseGet(ArrayList::new);
            int i = Math.min(list.size(), 32);

            for (int j = 0; j < i; ++j) {
                this.addCursor((SculkSpreader.ChargeCursor) list.get(j));
            }
        }

    }

    public void save(CompoundTag nbt) {
        DataResult<Tag> dataresult = SculkSpreader.ChargeCursor.CODEC.listOf().encodeStart(NbtOps.INSTANCE, this.cursors); // CraftBukkit - decompile error
        Logger logger = SculkSpreader.LOGGER;

        Objects.requireNonNull(logger);
        dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
            nbt.put("cursors", nbtbase);
        });
    }

    public void addCursors(BlockPos pos, int charge) {
        while (charge > 0) {
            int j = Math.min(charge, 1000);

            this.addCursor(new SculkSpreader.ChargeCursor(pos, j));
            charge -= j;
        }

    }

    private void addCursor(SculkSpreader.ChargeCursor cursor) {
        if (this.cursors.size() < 32) {
            // CraftBukkit start
            if (!this.isWorldGeneration()) { // CraftBukkit - SPIGOT-7475: Don't call event during world generation
                CraftBlock bukkitBlock = CraftBlock.at(this.level, cursor.pos);
                SculkBloomEvent event = new SculkBloomEvent(bukkitBlock, cursor.getCharge());
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    return;
                }

                cursor.charge = event.getCharge();
            }
            // CraftBukkit end

            this.cursors.add(cursor);
        }
    }

    public void updateCursors(LevelAccessor world, BlockPos pos, RandomSource random, boolean shouldConvertToBlock) {
        if (!this.cursors.isEmpty()) {
            List<SculkSpreader.ChargeCursor> list = new ArrayList();
            Map<BlockPos, SculkSpreader.ChargeCursor> map = new HashMap();
            Object2IntMap<BlockPos> object2intmap = new Object2IntOpenHashMap();
            Iterator iterator = this.cursors.iterator();

            BlockPos blockposition1;

            while (iterator.hasNext()) {
                SculkSpreader.ChargeCursor sculkspreader_a = (SculkSpreader.ChargeCursor) iterator.next();

                sculkspreader_a.update(world, pos, random, this, shouldConvertToBlock);
                if (sculkspreader_a.charge <= 0) {
                    world.levelEvent(3006, sculkspreader_a.getPos(), 0);
                } else {
                    blockposition1 = sculkspreader_a.getPos();
                    object2intmap.computeInt(blockposition1, (blockposition2, integer) -> {
                        return (integer == null ? 0 : integer) + sculkspreader_a.charge;
                    });
                    SculkSpreader.ChargeCursor sculkspreader_a1 = (SculkSpreader.ChargeCursor) map.get(blockposition1);

                    if (sculkspreader_a1 == null) {
                        map.put(blockposition1, sculkspreader_a);
                        list.add(sculkspreader_a);
                    } else if (!this.isWorldGeneration() && sculkspreader_a.charge + sculkspreader_a1.charge <= 1000) {
                        sculkspreader_a1.mergeWith(sculkspreader_a);
                    } else {
                        list.add(sculkspreader_a);
                        if (sculkspreader_a.charge < sculkspreader_a1.charge) {
                            map.put(blockposition1, sculkspreader_a);
                        }
                    }
                }
            }

            ObjectIterator objectiterator = object2intmap.object2IntEntrySet().iterator();

            while (objectiterator.hasNext()) {
                Entry<BlockPos> entry = (Entry) objectiterator.next();

                blockposition1 = (BlockPos) entry.getKey();
                int i = entry.getIntValue();
                SculkSpreader.ChargeCursor sculkspreader_a2 = (SculkSpreader.ChargeCursor) map.get(blockposition1);
                Collection<Direction> collection = sculkspreader_a2 == null ? null : sculkspreader_a2.getFacingData();

                if (i > 0 && collection != null) {
                    int j = (int) (Math.log1p((double) i) / 2.299999952316284D) + 1;
                    int k = (j << 6) + MultifaceBlock.pack(collection);

                    world.levelEvent(3006, blockposition1, k);
                }
            }

            this.cursors = list;
        }
    }

    public static class ChargeCursor {

        private static final ObjectArrayList<Vec3i> NON_CORNER_NEIGHBOURS = (ObjectArrayList) Util.make(new ObjectArrayList(18), (objectarraylist) -> {
            Stream stream = BlockPos.betweenClosedStream(new BlockPos(-1, -1, -1), new BlockPos(1, 1, 1)).filter((blockposition) -> {
                return (blockposition.getX() == 0 || blockposition.getY() == 0 || blockposition.getZ() == 0) && !blockposition.equals(BlockPos.ZERO);
            }).map(BlockPos::immutable);

            Objects.requireNonNull(objectarraylist);
            stream.forEach(objectarraylist::add);
        });
        public static final int MAX_CURSOR_DECAY_DELAY = 1;
        private BlockPos pos;
        int charge;
        private int updateDelay;
        private int decayDelay;
        @Nullable
        private Set<Direction> facings;
        private static final Codec<Set<Direction>> DIRECTION_SET = Direction.CODEC.listOf().xmap((list) -> {
            return Sets.newEnumSet(list, Direction.class);
        }, Lists::newArrayList);
        public static final Codec<SculkSpreader.ChargeCursor> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(BlockPos.CODEC.fieldOf("pos").forGetter(SculkSpreader.ChargeCursor::getPos), Codec.intRange(0, 1000).fieldOf("charge").orElse(0).forGetter(SculkSpreader.ChargeCursor::getCharge), Codec.intRange(0, 1).fieldOf("decay_delay").orElse(1).forGetter(SculkSpreader.ChargeCursor::getDecayDelay), Codec.intRange(0, Integer.MAX_VALUE).fieldOf("update_delay").orElse(0).forGetter((sculkspreader_a) -> {
                return sculkspreader_a.updateDelay;
            }), SculkSpreader.ChargeCursor.DIRECTION_SET.optionalFieldOf("facings").forGetter((sculkspreader_a) -> {
                return Optional.ofNullable(sculkspreader_a.getFacingData());
            })).apply(instance, SculkSpreader.ChargeCursor::new);
        });

        private ChargeCursor(BlockPos pos, int charge, int decay, int update, Optional<Set<Direction>> faces) {
            this.pos = pos;
            this.charge = charge;
            this.decayDelay = decay;
            this.updateDelay = update;
            this.facings = (Set) faces.orElse(null); // CraftBukkit - decompile error
        }

        public ChargeCursor(BlockPos pos, int charge) {
            this(pos, charge, 1, 0, Optional.empty());
        }

        public BlockPos getPos() {
            return this.pos;
        }

        public int getCharge() {
            return this.charge;
        }

        public int getDecayDelay() {
            return this.decayDelay;
        }

        @Nullable
        public Set<Direction> getFacingData() {
            return this.facings;
        }

        private boolean shouldUpdate(LevelAccessor world, BlockPos pos, boolean worldGen) {
            if (this.charge <= 0) {
                return false;
            } else if (worldGen) {
                return true;
            } else if (world instanceof ServerLevel) {
                ServerLevel worldserver = (ServerLevel) world;

                return worldserver.shouldTickBlocksAt(pos);
            } else {
                return false;
            }
        }

        public void update(LevelAccessor world, BlockPos pos, RandomSource random, SculkSpreader spreadManager, boolean shouldConvertToBlock) {
            if (this.shouldUpdate(world, pos, spreadManager.isWorldGeneration)) {
                if (this.updateDelay > 0) {
                    --this.updateDelay;
                } else {
                    BlockState iblockdata = world.getBlockState(this.pos);
                    SculkBehaviour sculkbehaviour = ChargeCursor.getBlockBehaviour(iblockdata);

                    if (shouldConvertToBlock && sculkbehaviour.attemptSpreadVein(world, this.pos, iblockdata, this.facings, spreadManager.isWorldGeneration())) {
                        if (sculkbehaviour.canChangeBlockStateOnSpread()) {
                            iblockdata = world.getBlockState(this.pos);
                            sculkbehaviour = ChargeCursor.getBlockBehaviour(iblockdata);
                        }

                        world.playSound((Player) null, this.pos, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }

                    this.charge = sculkbehaviour.attemptUseCharge(this, world, pos, random, spreadManager, shouldConvertToBlock);
                    if (this.charge <= 0) {
                        sculkbehaviour.onDischarged(world, iblockdata, this.pos, random);
                    } else {
                        BlockPos blockposition1 = ChargeCursor.getValidMovementPos(world, this.pos, random);

                        if (blockposition1 != null) {
                            sculkbehaviour.onDischarged(world, iblockdata, this.pos, random);
                            this.pos = blockposition1.immutable();
                            if (spreadManager.isWorldGeneration() && !this.pos.closerThan(new Vec3i(pos.getX(), this.pos.getY(), pos.getZ()), 15.0D)) {
                                this.charge = 0;
                                return;
                            }

                            iblockdata = world.getBlockState(blockposition1);
                        }

                        if (iblockdata.getBlock() instanceof SculkBehaviour) {
                            this.facings = MultifaceBlock.availableFaces(iblockdata);
                        }

                        this.decayDelay = sculkbehaviour.updateDecayDelay(this.decayDelay);
                        this.updateDelay = sculkbehaviour.getSculkSpreadDelay();
                    }
                }
            }
        }

        void mergeWith(SculkSpreader.ChargeCursor cursor) {
            this.charge += cursor.charge;
            cursor.charge = 0;
            this.updateDelay = Math.min(this.updateDelay, cursor.updateDelay);
        }

        private static SculkBehaviour getBlockBehaviour(BlockState state) {
            Block block = state.getBlock();
            SculkBehaviour sculkbehaviour;

            if (block instanceof SculkBehaviour) {
                SculkBehaviour sculkbehaviour1 = (SculkBehaviour) block;

                sculkbehaviour = sculkbehaviour1;
            } else {
                sculkbehaviour = SculkBehaviour.DEFAULT;
            }

            return sculkbehaviour;
        }

        private static List<Vec3i> getRandomizedNonCornerNeighbourOffsets(RandomSource random) {
            return Util.shuffledCopy(SculkSpreader.ChargeCursor.NON_CORNER_NEIGHBOURS, random);
        }

        @Nullable
        private static BlockPos getValidMovementPos(LevelAccessor world, BlockPos pos, RandomSource random) {
            BlockPos.MutableBlockPos blockposition_mutableblockposition = pos.mutable();
            BlockPos.MutableBlockPos blockposition_mutableblockposition1 = pos.mutable();
            Iterator iterator = ChargeCursor.getRandomizedNonCornerNeighbourOffsets(random).iterator();

            while (iterator.hasNext()) {
                Vec3i baseblockposition = (Vec3i) iterator.next();

                blockposition_mutableblockposition1.setWithOffset(pos, baseblockposition);
                BlockState iblockdata = world.getBlockState(blockposition_mutableblockposition1);

                if (iblockdata.getBlock() instanceof SculkBehaviour && ChargeCursor.isMovementUnobstructed(world, pos, blockposition_mutableblockposition1)) {
                    blockposition_mutableblockposition.set(blockposition_mutableblockposition1);
                    if (SculkVeinBlock.hasSubstrateAccess(world, iblockdata, blockposition_mutableblockposition1)) {
                        break;
                    }
                }
            }

            return blockposition_mutableblockposition.equals(pos) ? null : blockposition_mutableblockposition;
        }

        private static boolean isMovementUnobstructed(LevelAccessor world, BlockPos sourcePos, BlockPos targetPos) {
            if (sourcePos.distManhattan(targetPos) == 1) {
                return true;
            } else {
                BlockPos blockposition2 = targetPos.subtract(sourcePos);
                Direction enumdirection = Direction.fromAxisAndDirection(Direction.Axis.X, blockposition2.getX() < 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
                Direction enumdirection1 = Direction.fromAxisAndDirection(Direction.Axis.Y, blockposition2.getY() < 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
                Direction enumdirection2 = Direction.fromAxisAndDirection(Direction.Axis.Z, blockposition2.getZ() < 0 ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);

                return blockposition2.getX() == 0 ? ChargeCursor.isUnobstructed(world, sourcePos, enumdirection1) || ChargeCursor.isUnobstructed(world, sourcePos, enumdirection2) : (blockposition2.getY() == 0 ? ChargeCursor.isUnobstructed(world, sourcePos, enumdirection) || ChargeCursor.isUnobstructed(world, sourcePos, enumdirection2) : ChargeCursor.isUnobstructed(world, sourcePos, enumdirection) || ChargeCursor.isUnobstructed(world, sourcePos, enumdirection1));
            }
        }

        private static boolean isUnobstructed(LevelAccessor world, BlockPos pos, Direction direction) {
            BlockPos blockposition1 = pos.relative(direction);

            return !world.getBlockState(blockposition1).isFaceSturdy(world, blockposition1, direction.getOpposite());
        }
    }
}
