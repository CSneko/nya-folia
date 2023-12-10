package net.minecraft.world.level;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.Location;
import org.bukkit.event.block.BlockExplodeEvent;
// CraftBukkit end

public class Explosion {

    private static final ExplosionDamageCalculator EXPLOSION_DAMAGE_CALCULATOR = new ExplosionDamageCalculator();
    private static final int MAX_DROPS_PER_COMBINED_STACK = 16;
    private final boolean fire;
    private final Explosion.BlockInteraction blockInteraction;
    private final RandomSource random;
    private final Level level;
    private final double x;
    private final double y;
    private final double z;
    @Nullable
    public final Entity source;
    private final float radius;
    private final DamageSource damageSource;
    private final ExplosionDamageCalculator damageCalculator;
    private final ObjectArrayList<BlockPos> toBlow;
    private final Map<Player, Vec3> hitPlayers;
    public boolean wasCanceled = false; // CraftBukkit - add field

    public Explosion(Level world, @Nullable Entity entity, double x, double y, double z, float power, List<BlockPos> affectedBlocks) {
        this(world, entity, x, y, z, power, false, Explosion.BlockInteraction.DESTROY_WITH_DECAY, affectedBlocks);
    }

    public Explosion(Level world, @Nullable Entity entity, double x, double y, double z, float power, boolean createFire, Explosion.BlockInteraction destructionType, List<BlockPos> affectedBlocks) {
        this(world, entity, x, y, z, power, createFire, destructionType);
        this.toBlow.addAll(affectedBlocks);
    }

    public Explosion(Level world, @Nullable Entity entity, double x, double y, double z, float power, boolean createFire, Explosion.BlockInteraction destructionType) {
        this(world, entity, (DamageSource) null, (ExplosionDamageCalculator) null, x, y, z, power, createFire, destructionType);
    }

    public Explosion(Level world, @Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionDamageCalculator behavior, double x, double y, double z, float power, boolean createFire, Explosion.BlockInteraction destructionType) {
        this.random = RandomSource.create();
        this.toBlow = new ObjectArrayList();
        this.hitPlayers = Maps.newHashMap();
        this.level = world;
        this.source = entity;
        this.radius = (float) Math.max(power, 0.0); // CraftBukkit - clamp bad values
        this.x = x;
        this.y = y;
        this.z = z;
        this.fire = createFire;
        this.blockInteraction = destructionType;
        this.damageSource = damageSource == null ? world.damageSources().explosion(this) : damageSource;
        this.damageCalculator = behavior == null ? this.makeDamageCalculator(entity) : behavior;
    }

    // Paper start - optimise collisions
    private static final double[] CACHED_RAYS;
    static {
        final it.unimi.dsi.fastutil.doubles.DoubleArrayList rayCoords = new it.unimi.dsi.fastutil.doubles.DoubleArrayList();

        for (int x = 0; x <= 15; ++x) {
            for (int y = 0; y <= 15; ++y) {
                for (int z = 0; z <= 15; ++z) {
                    if ((x == 0 || x == 15) || (y == 0 || y == 15) || (z == 0 || z == 15)) {
                        double xDir = (double)((float)x / 15.0F * 2.0F - 1.0F);
                        double yDir = (double)((float)y / 15.0F * 2.0F - 1.0F);
                        double zDir = (double)((float)z / 15.0F * 2.0F - 1.0F);

                        double mag = Math.sqrt(
                                xDir * xDir + yDir * yDir + zDir * zDir
                        );

                        rayCoords.add((xDir / mag) * (double)0.3F);
                        rayCoords.add((yDir / mag) * (double)0.3F);
                        rayCoords.add((zDir / mag) * (double)0.3F);
                    }
                }
            }
        }

        CACHED_RAYS = rayCoords.toDoubleArray();
    }

    private static final int CHUNK_CACHE_SHIFT = 2;
    private static final int CHUNK_CACHE_MASK = (1 << CHUNK_CACHE_SHIFT) - 1;
    private static final int CHUNK_CACHE_WIDTH = 1 << CHUNK_CACHE_SHIFT;

    private static final int BLOCK_EXPLOSION_CACHE_SHIFT = 3;
    private static final int BLOCK_EXPLOSION_CACHE_MASK = (1 << BLOCK_EXPLOSION_CACHE_SHIFT) - 1;
    private static final int BLOCK_EXPLOSION_CACHE_WIDTH = 1 << BLOCK_EXPLOSION_CACHE_SHIFT;

    // resistance = (res + 0.3F) * 0.3F;
    // so for resistance = 0, we need res = -0.3F
    private static final Float ZERO_RESISTANCE = Float.valueOf(-0.3f);
    private it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<ExplosionBlockCache> blockCache = null;

    public static final class ExplosionBlockCache {

        public final long key;
        public final BlockPos immutablePos;
        public final BlockState blockState;
        public final FluidState fluidState;
        public final float resistance;
        public final boolean outOfWorld;
        public Boolean shouldExplode; // null -> not called yet
        public net.minecraft.world.phys.shapes.VoxelShape cachedCollisionShape;

        public ExplosionBlockCache(long key, BlockPos immutablePos, BlockState blockState, FluidState fluidState, float resistance,
                                   boolean outOfWorld) {
            this.key = key;
            this.immutablePos = immutablePos;
            this.blockState = blockState;
            this.fluidState = fluidState;
            this.resistance = resistance;
            this.outOfWorld = outOfWorld;
        }
    }

    private long[] chunkPosCache = null;
    private net.minecraft.world.level.chunk.LevelChunk[] chunkCache = null;

    private ExplosionBlockCache getOrCacheExplosionBlock(final int x, final int y, final int z,
                                                         final long key, final boolean calculateResistance) {
        ExplosionBlockCache ret = this.blockCache.get(key);
        if (ret != null) {
            return ret;
        }

        BlockPos pos = new BlockPos(x, y, z);

        if (!this.level.isInWorldBounds(pos)) {
            ret = new ExplosionBlockCache(key, pos, null, null, 0.0f, true);
        } else {
            net.minecraft.world.level.chunk.LevelChunk chunk;
            long chunkKey = io.papermc.paper.util.CoordinateUtils.getChunkKey(x >> 4, z >> 4);
            int chunkCacheKey = ((x >> 4) & CHUNK_CACHE_MASK) | (((z >> 4) << CHUNK_CACHE_SHIFT) & (CHUNK_CACHE_MASK << CHUNK_CACHE_SHIFT));
            if (this.chunkPosCache[chunkCacheKey] == chunkKey) {
                chunk = this.chunkCache[chunkCacheKey];
            } else {
                this.chunkPosCache[chunkCacheKey] = chunkKey;
                this.chunkCache[chunkCacheKey] = chunk = this.level.getChunk(x >> 4, z >> 4);
            }

            BlockState blockState = chunk.getBlockStateFinal(x, y, z);
            FluidState fluidState = blockState.getFluidState();

            Optional<Float> resistance = !calculateResistance ? Optional.empty() : this.damageCalculator.getBlockExplosionResistance((Explosion)(Object)this, this.level, pos, blockState, fluidState);

            ret = new ExplosionBlockCache(
                    key, pos, blockState, fluidState,
                    (resistance.orElse(ZERO_RESISTANCE).floatValue() + 0.3f) * 0.3f,
                    false
            );
        }

        this.blockCache.put(key, ret);

        return ret;
    }

    private boolean clipsAnything(final Vec3 from, final Vec3 to,
                                  final io.papermc.paper.util.CollisionUtil.LazyEntityCollisionContext context,
                                  final ExplosionBlockCache[] blockCache,
                                  final BlockPos.MutableBlockPos currPos) {
        // assume that context.delegated = false
        final double adjX = io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON * (from.x - to.x);
        final double adjY = io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON * (from.y - to.y);
        final double adjZ = io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON * (from.z - to.z);

        if (adjX == 0.0 && adjY == 0.0 && adjZ == 0.0) {
            return false;
        }

        final double toXAdj = to.x - adjX;
        final double toYAdj = to.y - adjY;
        final double toZAdj = to.z - adjZ;
        final double fromXAdj = from.x + adjX;
        final double fromYAdj = from.y + adjY;
        final double fromZAdj = from.z + adjZ;

        int currX = Mth.floor(fromXAdj);
        int currY = Mth.floor(fromYAdj);
        int currZ = Mth.floor(fromZAdj);

        final double diffX = toXAdj - fromXAdj;
        final double diffY = toYAdj - fromYAdj;
        final double diffZ = toZAdj - fromZAdj;

        final double dxDouble = Math.signum(diffX);
        final double dyDouble = Math.signum(diffY);
        final double dzDouble = Math.signum(diffZ);

        final int dx = (int)dxDouble;
        final int dy = (int)dyDouble;
        final int dz = (int)dzDouble;

        final double normalizedDiffX = diffX == 0.0 ? Double.MAX_VALUE : dxDouble / diffX;
        final double normalizedDiffY = diffY == 0.0 ? Double.MAX_VALUE : dyDouble / diffY;
        final double normalizedDiffZ = diffZ == 0.0 ? Double.MAX_VALUE : dzDouble / diffZ;

        double normalizedCurrX = normalizedDiffX * (diffX > 0.0 ? (1.0 - Mth.frac(fromXAdj)) : Mth.frac(fromXAdj));
        double normalizedCurrY = normalizedDiffY * (diffY > 0.0 ? (1.0 - Mth.frac(fromYAdj)) : Mth.frac(fromYAdj));
        double normalizedCurrZ = normalizedDiffZ * (diffZ > 0.0 ? (1.0 - Mth.frac(fromZAdj)) : Mth.frac(fromZAdj));

        for (;;) {
            currPos.set(currX, currY, currZ);

            // ClipContext.Block.COLLIDER -> BlockBehaviour.BlockStateBase::getCollisionShape
            // ClipContext.Fluid.NONE -> ignore fluids

            // read block from cache
            final long key = BlockPos.asLong(currX, currY, currZ);

            final int cacheKey =
                    (currX & BLOCK_EXPLOSION_CACHE_MASK) |
                    (currY & BLOCK_EXPLOSION_CACHE_MASK) << (BLOCK_EXPLOSION_CACHE_SHIFT) |
                    (currZ & BLOCK_EXPLOSION_CACHE_MASK) << (BLOCK_EXPLOSION_CACHE_SHIFT + BLOCK_EXPLOSION_CACHE_SHIFT);
            ExplosionBlockCache cachedBlock = blockCache[cacheKey];
            if (cachedBlock == null || cachedBlock.key != key) {
                blockCache[cacheKey] = cachedBlock = this.getOrCacheExplosionBlock(currX, currY, currZ, key, false);
            }

            final BlockState blockState = cachedBlock.blockState;
            if (blockState != null && !blockState.emptyCollisionShape()) {
                net.minecraft.world.phys.shapes.VoxelShape collision = cachedBlock.cachedCollisionShape;
                if (collision == null) {
                    collision = blockState.getConstantCollisionShape();
                    if (collision == null) {
                        collision = blockState.getCollisionShape(this.level, currPos, context);
                        if (!context.isDelegated()) {
                            // if it was not delegated during this call, assume that for any future ones it will not be delegated
                            // again, and cache the result
                            cachedBlock.cachedCollisionShape = collision;
                        }
                    } else {
                        cachedBlock.cachedCollisionShape = collision;
                    }
                }

                if (!collision.isEmpty() && collision.clip(from, to, currPos) != null) {
                    return true;
                }
            }

            if (normalizedCurrX > 1.0 && normalizedCurrY > 1.0 && normalizedCurrZ > 1.0) {
                return false;
            }

            // inc the smallest normalized coordinate

            if (normalizedCurrX < normalizedCurrY) {
                if (normalizedCurrX < normalizedCurrZ) {
                    currX += dx;
                    normalizedCurrX += normalizedDiffX;
                } else {
                    // x < y && x >= z <--> z < y && z <= x
                    currZ += dz;
                    normalizedCurrZ += normalizedDiffZ;
                }
            } else if (normalizedCurrY < normalizedCurrZ) {
                // y <= x && y < z
                currY += dy;
                normalizedCurrY += normalizedDiffY;
            } else {
                // y <= x && z <= y <--> z <= y && z <= x
                currZ += dz;
                normalizedCurrZ += normalizedDiffZ;
            }
        }
    }

    private float getSeenFraction(final Vec3 source, final Entity target,
                                   final ExplosionBlockCache[] blockCache,
                                   final BlockPos.MutableBlockPos blockPos) {
        final AABB boundingBox = target.getBoundingBox();
        final double diffX = boundingBox.maxX - boundingBox.minX;
        final double diffY = boundingBox.maxY - boundingBox.minY;
        final double diffZ = boundingBox.maxZ - boundingBox.minZ;

        final double incX = 1.0 / (diffX * 2.0 + 1.0);
        final double incY = 1.0 / (diffY * 2.0 + 1.0);
        final double incZ = 1.0 / (diffZ * 2.0 + 1.0);

        if (incX < 0.0 || incY < 0.0 || incZ < 0.0) {
            return 0.0f;
        }

        final double offX = (1.0 - Math.floor(1.0 / incX) * incX) * 0.5 + boundingBox.minX;
        final double offY = boundingBox.minY;
        final double offZ = (1.0 - Math.floor(1.0 / incZ) * incZ) * 0.5 + boundingBox.minZ;

        final io.papermc.paper.util.CollisionUtil.LazyEntityCollisionContext context = new io.papermc.paper.util.CollisionUtil.LazyEntityCollisionContext(target);

        int totalRays = 0;
        int missedRays = 0;

        for (double dx = 0.0; dx <= 1.0; dx += incX) {
            final double fromX = Math.fma(dx, diffX, offX);
            for (double dy = 0.0; dy <= 1.0; dy += incY) {
                final double fromY = Math.fma(dy, diffY, offY);
                for (double dz = 0.0; dz <= 1.0; dz += incZ) {
                    ++totalRays;

                    final Vec3 from = new Vec3(
                            fromX,
                            fromY,
                            Math.fma(dz, diffZ, offZ)
                    );

                    if (!this.clipsAnything(from, source, context, blockCache, blockPos)) {
                        ++missedRays;
                    }
                }
            }
        }

        return (float)missedRays / (float)totalRays;
    }
    // Paper end - optimise collisions

    private ExplosionDamageCalculator makeDamageCalculator(@Nullable Entity entity) {
        return (ExplosionDamageCalculator) (entity == null ? Explosion.EXPLOSION_DAMAGE_CALCULATOR : new EntityBasedExplosionDamageCalculator(entity));
    }

    public static float getSeenPercent(Vec3 source, Entity entity) {
        AABB axisalignedbb = entity.getBoundingBox();
        double d0 = 1.0D / ((axisalignedbb.maxX - axisalignedbb.minX) * 2.0D + 1.0D);
        double d1 = 1.0D / ((axisalignedbb.maxY - axisalignedbb.minY) * 2.0D + 1.0D);
        double d2 = 1.0D / ((axisalignedbb.maxZ - axisalignedbb.minZ) * 2.0D + 1.0D);
        double d3 = (1.0D - Math.floor(1.0D / d0) * d0) / 2.0D;
        double d4 = (1.0D - Math.floor(1.0D / d2) * d2) / 2.0D;

        if (d0 >= 0.0D && d1 >= 0.0D && d2 >= 0.0D) {
            int i = 0;
            int j = 0;

            for (double d5 = 0.0D; d5 <= 1.0D; d5 += d0) {
                for (double d6 = 0.0D; d6 <= 1.0D; d6 += d1) {
                    for (double d7 = 0.0D; d7 <= 1.0D; d7 += d2) {
                        double d8 = Mth.lerp(d5, axisalignedbb.minX, axisalignedbb.maxX);
                        double d9 = Mth.lerp(d6, axisalignedbb.minY, axisalignedbb.maxY);
                        double d10 = Mth.lerp(d7, axisalignedbb.minZ, axisalignedbb.maxZ);
                        Vec3 vec3d1 = new Vec3(d8 + d3, d9, d10 + d4);

                        if (entity.level().clip(new ClipContext(vec3d1, source, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.MISS) {
                            ++i;
                        }

                        ++j;
                    }
                }
            }

            return (float) i / (float) j;
        } else {
            return 0.0F;
        }
    }

    public void explode() {
        // CraftBukkit start
        if (this.radius < 0.1F) {
            return;
        }
        // CraftBukkit end
        this.level.gameEvent(this.source, GameEvent.EXPLODE, new Vec3(this.x, this.y, this.z));
        Set<BlockPos> set = Sets.newHashSet();
        boolean flag = true;

        int i;
        int j;

        // Paper start - optimise explosions
        this.blockCache = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();

        this.chunkPosCache = new long[CHUNK_CACHE_WIDTH * CHUNK_CACHE_WIDTH];
        java.util.Arrays.fill(this.chunkPosCache, ChunkPos.INVALID_CHUNK_POS);

        this.chunkCache = new net.minecraft.world.level.chunk.LevelChunk[CHUNK_CACHE_WIDTH * CHUNK_CACHE_WIDTH];

        final ExplosionBlockCache[] blockCache = new ExplosionBlockCache[BLOCK_EXPLOSION_CACHE_WIDTH * BLOCK_EXPLOSION_CACHE_WIDTH * BLOCK_EXPLOSION_CACHE_WIDTH];
        // use initial cache value that is most likely to be used: the source position
        final ExplosionBlockCache initialCache;
        {
            final int blockX = Mth.floor(this.x);
            final int blockY = Mth.floor(this.y);
            final int blockZ = Mth.floor(this.z);

            final long key = BlockPos.asLong(blockX, blockY, blockZ);

            initialCache = this.getOrCacheExplosionBlock(blockX, blockY, blockZ, key, true);
        }
        // only ~1/3rd of the loop iterations in vanilla will result in a ray, as it is iterating the perimeter of
        // a 16x16x16 cube
        // we can cache the rays and their normals as well, so that we eliminate the excess iterations / checks and
        // calculations in one go
        // additional aggressive caching of block retrieval is very significant, as at low power (i.e tnt) most
        // block retrievals are not unique
        for (int ray = 0, len = CACHED_RAYS.length; ray < len;) {
            {
                {
                    {
                        ExplosionBlockCache cachedBlock = initialCache;

                        double d0 = CACHED_RAYS[ray];
                        double d1 = CACHED_RAYS[ray + 1];
                        double d2 = CACHED_RAYS[ray + 2];
                        ray += 3;
                        // Paper end - optimise explosions
                        float f = this.radius * (0.7F + this.level.random.nextFloat() * 0.6F);
                        double d4 = this.x;
                        double d5 = this.y;
                        double d6 = this.z;

                        for (float f1 = 0.3F; f > 0.0F; f -= 0.22500001F) {
                            // Paper start - optimise explosions
                            final int blockX = Mth.floor(d4);
                            final int blockY = Mth.floor(d5);
                            final int blockZ = Mth.floor(d6);

                            final long key = BlockPos.asLong(blockX, blockY, blockZ);

                            if (cachedBlock.key != key) {
                                final int cacheKey =
                                    (blockX & BLOCK_EXPLOSION_CACHE_MASK) |
                                        (blockY & BLOCK_EXPLOSION_CACHE_MASK) << (BLOCK_EXPLOSION_CACHE_SHIFT) |
                                        (blockZ & BLOCK_EXPLOSION_CACHE_MASK) << (BLOCK_EXPLOSION_CACHE_SHIFT + BLOCK_EXPLOSION_CACHE_SHIFT);
                                cachedBlock = blockCache[cacheKey];
                                if (cachedBlock == null || cachedBlock.key != key) {
                                    blockCache[cacheKey] = cachedBlock = this.getOrCacheExplosionBlock(blockX, blockY, blockZ, key, true);
                                }
                            }

                            if (cachedBlock.outOfWorld) {
                                break;
                            }

                            BlockPos blockposition = cachedBlock.immutablePos;
                            BlockState iblockdata = cachedBlock.blockState;
                            // Paper end - optimise explosions

                            if (!iblockdata.isDestroyable()) continue; // Paper
                            // Paper - optimise explosions

                            // Paper - optimise explosions

                            f -= cachedBlock.resistance; // Paper - optimise explosions

                            if (f > 0.0F && cachedBlock.shouldExplode == null) { // Paper - optimise explosions
                                // Paper start - optimise explosions
                                // note: we expect shouldBlockExplode to be pure with respect to power, as Vanilla currently is.
                                // basically, it is unused, which allows us to cache the result
                                final boolean shouldExplode = this.damageCalculator.shouldBlockExplode(this, this.level, cachedBlock.immutablePos, cachedBlock.blockState, f);
                                cachedBlock.shouldExplode = shouldExplode ? Boolean.TRUE : Boolean.FALSE;
                                if (shouldExplode && (this.fire || !cachedBlock.blockState.isAir())) {
                                // Paper end - optimise explosions
                                set.add(blockposition);
                                // Paper start - prevent headless pistons from forming
                                if (!io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.allowHeadlessPistons && iblockdata.getBlock() == Blocks.MOVING_PISTON) {
                                    BlockEntity extension = this.level.getBlockEntity(blockposition);
                                    if (extension instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity blockEntity && blockEntity.isSourcePiston()) {
                                       net.minecraft.core.Direction direction = iblockdata.getValue(net.minecraft.world.level.block.piston.PistonHeadBlock.FACING);
                                       set.add(blockposition.relative(direction.getOpposite()));
                                    }
                                }
                                // Paper end
                                } // Paper - optimise explosions
                            }

                            d4 += d0; // Paper - optimise explosions
                            d5 += d1; // Paper - optimise explosions
                            d6 += d2; // Paper - optimise explosions
                        }
                    }
                }
            }
        }

        this.toBlow.addAll(set);
        float f2 = this.radius * 2.0F;

        i = Mth.floor(this.x - (double) f2 - 1.0D);
        j = Mth.floor(this.x + (double) f2 + 1.0D);
        int l = Mth.floor(this.y - (double) f2 - 1.0D);
        int i1 = Mth.floor(this.y + (double) f2 + 1.0D);
        int j1 = Mth.floor(this.z - (double) f2 - 1.0D);
        int k1 = Mth.floor(this.z + (double) f2 + 1.0D);
        List<Entity> list = this.level.getEntities(this.source, new AABB((double) i, (double) l, (double) j1, (double) j, (double) i1, (double) k1), (com.google.common.base.Predicate<Entity>) entity -> entity.isAlive() && !entity.isSpectator()); // Paper - Fix lag from explosions processing dead entities
        Vec3 vec3d = new Vec3(this.x, this.y, this.z);
        Iterator iterator = list.iterator();

        final BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(); // Paper - optimise explosions

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            if (!entity.ignoreExplosion()) {
                double d7 = Math.sqrt(entity.distanceToSqr(vec3d)) / (double) f2;

                if (d7 <= 1.0D) {
                    double d8 = entity.getX() - this.x;
                    double d9 = (entity instanceof PrimedTnt ? entity.getY() : entity.getEyeY()) - this.y;
                    double d10 = entity.getZ() - this.z;
                    double d11 = Math.sqrt(d8 * d8 + d9 * d9 + d10 * d10);

                    if (d11 != 0.0D) {
                        d8 /= d11;
                        d9 /= d11;
                        d10 /= d11;
                        double d12 = this.getBlockDensity(vec3d, entity, blockCache, blockPos); // Paper - Optimize explosions // Paper - optimise explosions
                        double d13 = (1.0D - d7) * d12;

                        // CraftBukkit start

                        // Special case ender dragon only give knockback if no damage is cancelled
                        // Thinks to note:
                        // - Setting a velocity to a ComplexEntityPart is ignored (and therefore not needed)
                        // - Damaging ComplexEntityPart while forward the damage to EntityEnderDragon
                        // - Damaging EntityEnderDragon does nothing
                        // - EntityEnderDragon hitbock always covers the other parts and is therefore always present
                        if (entity instanceof EnderDragonPart) {
                            continue;
                        }

                        CraftEventFactory.entityDamageRT.set(this.source); // Folia - region threading
                        entity.lastDamageCancelled = false;

                        if (entity instanceof EnderDragon) {
                            for (EnderDragonPart entityComplexPart : ((EnderDragon) entity).subEntities) {
                                // Calculate damage separately for each EntityComplexPart
                                double d7part;
                                if (list.contains(entityComplexPart) && (d7part = Math.sqrt(entityComplexPart.distanceToSqr(vec3d)) / f2) <= 1.0D) {
                                    double d13part = (1.0D - d7part) * this.getSeenFraction(vec3d, entityComplexPart, blockCache, blockPos); // Paper - optimise explosions
                                    entityComplexPart.hurt(this.getDamageSource(), (float) ((int) ((d13part * d13part + d13part) / 2.0D * 7.0D * (double) f2 + 1.0D)));
                                }
                            }
                        } else {
                            entity.hurt(this.getDamageSource(), (float) ((int) ((d13 * d13 + d13) / 2.0D * 7.0D * (double) f2 + 1.0D)));
                        }

                        CraftEventFactory.entityDamageRT.set(null); // Folia - region threading
                        if (entity.lastDamageCancelled) { // SPIGOT-5339, SPIGOT-6252, SPIGOT-6777: Skip entity if damage event was cancelled
                            continue;
                        }
                        // CraftBukkit end
                        double d14;

                        if (entity instanceof LivingEntity) {
                            LivingEntity entityliving = (LivingEntity) entity;

                            d14 = entity instanceof Player && level.paperConfig().environment.disableExplosionKnockback ? 0 : ProtectionEnchantment.getExplosionKnockbackAfterDampener(entityliving, d13); // Paper - disable explosion knockback
                        } else {
                            d14 = d13;
                        }

                        d8 *= d14;
                        d9 *= d14;
                        d10 *= d14;
                        Vec3 vec3d1 = new Vec3(d8, d9, d10);

                        entity.setDeltaMovement(entity.getDeltaMovement().add(vec3d1));
                        if (entity instanceof Player) {
                            Player entityhuman = (Player) entity;

                            if (!entityhuman.isSpectator() && (!entityhuman.isCreative() || !entityhuman.getAbilities().flying) && !level.paperConfig().environment.disableExplosionKnockback) { // Paper - Disable explosion knockback
                                this.hitPlayers.put(entityhuman, vec3d1);
                            }
                        }
                    }
                }
            }
        }

        this.blockCache = null; // Paper - optimise explosions
        this.chunkPosCache = null; // Paper - optimise explosions
        this.chunkCache = null; // Paper - optimise explosions

    }

    public void finalizeExplosion(boolean particles) {
        if (this.level.isClientSide) {
            this.level.playLocalSound(this.x, this.y, this.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F, (1.0F + (this.level.random.nextFloat() - this.level.random.nextFloat()) * 0.2F) * 0.7F, false);
        }

        boolean flag1 = this.interactsWithBlocks();

        if (particles) {
            if (this.radius >= 2.0F && flag1) {
                this.level.addParticle(ParticleTypes.EXPLOSION_EMITTER, this.x, this.y, this.z, 1.0D, 0.0D, 0.0D);
            } else {
                this.level.addParticle(ParticleTypes.EXPLOSION, this.x, this.y, this.z, 1.0D, 0.0D, 0.0D);
            }
        }

        if (flag1) {
            ObjectArrayList<Pair<ItemStack, BlockPos>> objectarraylist = new ObjectArrayList();
            boolean flag2 = this.getIndirectSourceEntity() instanceof Player;

            Util.shuffle(this.toBlow, this.level.random);
            ObjectListIterator objectlistiterator = this.toBlow.iterator();
            // CraftBukkit start
            org.bukkit.World bworld = this.level.getWorld();
            org.bukkit.entity.Entity explode = this.source == null ? null : this.source.getBukkitEntity();
            Location location = new Location(bworld, this.x, this.y, this.z);

            List<org.bukkit.block.Block> blockList = new ObjectArrayList<>();
            for (int i1 = this.toBlow.size() - 1; i1 >= 0; i1--) {
                BlockPos cpos = this.toBlow.get(i1);
                org.bukkit.block.Block bblock = bworld.getBlockAt(cpos.getX(), cpos.getY(), cpos.getZ());
                if (!bblock.getType().isAir()) {
                    blockList.add(bblock);
                }
            }

            boolean cancelled;
            List<org.bukkit.block.Block> bukkitBlocks;
            float yield;

            if (explode != null) {
                EntityExplodeEvent event = new EntityExplodeEvent(explode, location, blockList, this.blockInteraction == Explosion.BlockInteraction.DESTROY_WITH_DECAY ? 1.0F / this.radius : 1.0F);
                this.level.getCraftServer().getPluginManager().callEvent(event);
                cancelled = event.isCancelled();
                bukkitBlocks = event.blockList();
                yield = event.getYield();
            } else {
                BlockExplodeEvent event = new BlockExplodeEvent(location.getBlock(), blockList, this.blockInteraction == Explosion.BlockInteraction.DESTROY_WITH_DECAY ? 1.0F / this.radius : 1.0F, this.damageSource.explodedBlockState); // Paper - exploded block state
                this.level.getCraftServer().getPluginManager().callEvent(event);
                cancelled = event.isCancelled();
                bukkitBlocks = event.blockList();
                yield = event.getYield();
            }

            this.toBlow.clear();

            for (org.bukkit.block.Block bblock : bukkitBlocks) {
                BlockPos coords = new BlockPos(bblock.getX(), bblock.getY(), bblock.getZ());
                this.toBlow.add(coords);
            }

            if (cancelled) {
                this.wasCanceled = true;
                return;
            }
            // CraftBukkit end
            objectlistiterator = this.toBlow.iterator();

            while (objectlistiterator.hasNext()) {
                BlockPos blockposition = (BlockPos) objectlistiterator.next();
                BlockState iblockdata = this.level.getBlockState(blockposition);
                Block block = iblockdata.getBlock();
                // CraftBukkit start - TNTPrimeEvent
                if (block instanceof net.minecraft.world.level.block.TntBlock) {
                    Entity sourceEntity = this.source == null ? null : this.source;
                    BlockPos sourceBlock = sourceEntity == null ? BlockPos.containing(this.x, this.y, this.z) : null;
                    if (!CraftEventFactory.callTNTPrimeEvent(this.level, blockposition, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.EXPLOSION, sourceEntity, sourceBlock)) {
                        this.level.sendBlockUpdated(blockposition, Blocks.AIR.defaultBlockState(), iblockdata, 3); // Update the block on the client
                        continue;
                    }
                }
                // CraftBukkit end

                if (!iblockdata.isAir() && iblockdata.isDestroyable()) { // Paper
                    BlockPos blockposition1 = blockposition.immutable();

                    this.level.getProfiler().push("explosion_blocks");
                    if (block.dropFromExplosion(this)) {
                        Level world = this.level;

                        if (world instanceof ServerLevel) {
                            ServerLevel worldserver = (ServerLevel) world;
                            BlockEntity tileentity = iblockdata.hasBlockEntity() ? this.level.getBlockEntity(blockposition) : null;
                            LootParams.Builder lootparams_a = (new LootParams.Builder(worldserver)).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(blockposition)).withParameter(LootContextParams.TOOL, ItemStack.EMPTY).withOptionalParameter(LootContextParams.BLOCK_ENTITY, tileentity).withOptionalParameter(LootContextParams.THIS_ENTITY, this.source);

                            if (yield < 1.0F) { // CraftBukkit - add yield
                                lootparams_a.withParameter(LootContextParams.EXPLOSION_RADIUS, 1.0F / yield); // CraftBukkit - add yield
                            }

                            iblockdata.spawnAfterBreak(worldserver, blockposition, ItemStack.EMPTY, flag2);
                            iblockdata.getDrops(lootparams_a).forEach((itemstack) -> {
                                Explosion.addBlockDrops(objectarraylist, itemstack, blockposition1);
                            });
                        }
                    }

                    this.level.setBlock(blockposition, Blocks.AIR.defaultBlockState(), 3);
                    block.wasExploded(this.level, blockposition, this);
                    this.level.getProfiler().pop();
                }
            }

            objectlistiterator = objectarraylist.iterator();

            while (objectlistiterator.hasNext()) {
                Pair<ItemStack, BlockPos> pair = (Pair) objectlistiterator.next();

                Block.popResource(this.level, (BlockPos) pair.getSecond(), (ItemStack) pair.getFirst());
            }
        }

        if (this.fire) {
            ObjectListIterator objectlistiterator1 = this.toBlow.iterator();

            while (objectlistiterator1.hasNext()) {
                BlockPos blockposition2 = (BlockPos) objectlistiterator1.next();

                if (this.random.nextInt(3) == 0 && this.level.getBlockState(blockposition2).isAir() && this.level.getBlockState(blockposition2.below()).isSolidRender(this.level, blockposition2.below())) {
                    // CraftBukkit start - Ignition by explosion
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(this.level, blockposition2.getX(), blockposition2.getY(), blockposition2.getZ(), this).isCancelled()) {
                        this.level.setBlockAndUpdate(blockposition2, BaseFireBlock.getState(this.level, blockposition2));
                    }
                    // CraftBukkit end
                }
            }
        }

    }

    public boolean interactsWithBlocks() {
        return this.blockInteraction != Explosion.BlockInteraction.KEEP;
    }

    private static void addBlockDrops(ObjectArrayList<Pair<ItemStack, BlockPos>> stacks, ItemStack stack, BlockPos pos) {
        if (stack.isEmpty()) return; // CraftBukkit - SPIGOT-5425
        int i = stacks.size();

        for (int j = 0; j < i; ++j) {
            Pair<ItemStack, BlockPos> pair = (Pair) stacks.get(j);
            ItemStack itemstack1 = (ItemStack) pair.getFirst();

            if (ItemEntity.areMergable(itemstack1, stack)) {
                ItemStack itemstack2 = ItemEntity.merge(itemstack1, stack, 16);

                stacks.set(j, Pair.of(itemstack2, (BlockPos) pair.getSecond()));
                if (stack.isEmpty()) {
                    return;
                }
            }
        }

        stacks.add(Pair.of(stack, pos));
    }

    public DamageSource getDamageSource() {
        return this.damageSource;
    }

    public Map<Player, Vec3> getHitPlayers() {
        return this.hitPlayers;
    }

    @Nullable
    public LivingEntity getIndirectSourceEntity() {
        if (this.source == null) {
            return null;
        } else {
            Entity entity = this.source;

            if (entity instanceof PrimedTnt) {
                PrimedTnt entitytntprimed = (PrimedTnt) entity;

                return entitytntprimed.getOwner();
            } else {
                entity = this.source;
                if (entity instanceof LivingEntity) {
                    LivingEntity entityliving = (LivingEntity) entity;

                    return entityliving;
                } else {
                    entity = this.source;
                    if (entity instanceof Projectile) {
                        Projectile iprojectile = (Projectile) entity;

                        entity = iprojectile.getOwner();
                        if (entity instanceof LivingEntity) {
                            LivingEntity entityliving1 = (LivingEntity) entity;

                            return entityliving1;
                        }
                    }

                    return null;
                }
            }
        }
    }

    @Nullable
    public Entity getDirectSourceEntity() {
        return this.source;
    }

    public void clearToBlow() {
        this.toBlow.clear();
    }

    public List<BlockPos> getToBlow() {
        return this.toBlow;
    }

    public static enum BlockInteraction {

        KEEP, DESTROY, DESTROY_WITH_DECAY;

        private BlockInteraction() {}
    }
    // Paper start - Optimize explosions
    private float getBlockDensity(Vec3 vec3d, Entity entity, ExplosionBlockCache[] blockCache, BlockPos.MutableBlockPos blockPos) { // Paper - optimise explosions
        if (!this.level.paperConfig().environment.optimizeExplosions) {
            return this.getSeenFraction(vec3d, entity, blockCache, blockPos); // Paper - optimise explosions
        }
        io.papermc.paper.threadedregions.RegionizedWorldData worldData = this.level.getCurrentWorldData(); // Folia - region threading
        CacheKey key = new CacheKey(this, entity.getBoundingBox());
        Float blockDensity = worldData.explosionDensityCache.get(key); // Folia - region threading
        if (blockDensity == null) {
            blockDensity = this.getSeenFraction(vec3d, entity, blockCache, blockPos); // Paper - optimise explosions;
            worldData.explosionDensityCache.put(key, blockDensity); // Folia - region threading
        }

        return blockDensity;
    }

    public static class CacheKey { // Folia - region threading - public
        private final Level world;
        private final double posX, posY, posZ;
        private final double minX, minY, minZ;
        private final double maxX, maxY, maxZ;

        public CacheKey(Explosion explosion, AABB aabb) {
            this.world = explosion.level;
            this.posX = explosion.x;
            this.posY = explosion.y;
            this.posZ = explosion.z;
            this.minX = aabb.minX;
            this.minY = aabb.minY;
            this.minZ = aabb.minZ;
            this.maxX = aabb.maxX;
            this.maxY = aabb.maxY;
            this.maxZ = aabb.maxZ;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CacheKey cacheKey = (CacheKey) o;

            if (Double.compare(cacheKey.posX, posX) != 0) return false;
            if (Double.compare(cacheKey.posY, posY) != 0) return false;
            if (Double.compare(cacheKey.posZ, posZ) != 0) return false;
            if (Double.compare(cacheKey.minX, minX) != 0) return false;
            if (Double.compare(cacheKey.minY, minY) != 0) return false;
            if (Double.compare(cacheKey.minZ, minZ) != 0) return false;
            if (Double.compare(cacheKey.maxX, maxX) != 0) return false;
            if (Double.compare(cacheKey.maxY, maxY) != 0) return false;
            if (Double.compare(cacheKey.maxZ, maxZ) != 0) return false;
            return world.equals(cacheKey.world);
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            result = world.hashCode();
            temp = Double.doubleToLongBits(posX);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(posY);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(posZ);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(minX);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(minY);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(minZ);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(maxX);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(maxY);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(maxZ);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }
    }
    // Paper end
}
