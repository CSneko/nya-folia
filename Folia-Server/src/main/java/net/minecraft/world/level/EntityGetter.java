package net.minecraft.world.level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface EntityGetter {

    // Paper start
    List<Entity> getHardCollidingEntities(Entity except, AABB box, Predicate<? super Entity> predicate);

    void getEntities(Entity except, AABB box, Predicate<? super Entity> predicate, List<Entity> into);

    void getHardCollidingEntities(Entity except, AABB box, Predicate<? super Entity> predicate, List<Entity> into);

    <T> void getEntitiesByClass(Class<? extends T> clazz, Entity except, final AABB box, List<? super T> into,
                                Predicate<? super T> predicate);
    // Paper end

    List<Entity> getEntities(@Nullable Entity except, AABB box, Predicate<? super Entity> predicate);

    <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> filter, AABB box, Predicate<? super T> predicate);

    default <T extends Entity> List<T> getEntitiesOfClass(Class<T> entityClass, AABB box, Predicate<? super T> predicate) {
        return this.getEntities(EntityTypeTest.forClass(entityClass), box, predicate);
    }

    // Folia start - region threading
    default List<? extends Player> getLocalPlayers() {
        return java.util.Collections.emptyList();
    }
    // Folia end - region threading

    List<? extends Player> players();

    default List<Entity> getEntities(@Nullable Entity except, AABB box) {
        return this.getEntities(except, box, EntitySelector.NO_SPECTATORS);
    }

    default boolean isUnobstructed(@Nullable Entity except, VoxelShape shape) {
        // Paper start - optimise collisions
        if (shape.isEmpty()) {
            return false;
        }

        final AABB singleAABB = shape.getSingleAABBRepresentation();
        final List<Entity> entities = this.getEntities(
                except,
                singleAABB == null ? shape.bounds() : singleAABB.inflate(-io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON, -io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON, -io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON)
        );

        for (int i = 0, len = entities.size(); i < len; ++i) {
            final Entity otherEntity = entities.get(i);

            if (otherEntity.isRemoved() || !otherEntity.blocksBuilding || (except != null && otherEntity.isPassengerOfSameVehicle(except))) {
                continue;
            }

            if (singleAABB == null) {
                final AABB entityBB = otherEntity.getBoundingBox();
                if (io.papermc.paper.util.CollisionUtil.isEmpty(entityBB) || !io.papermc.paper.util.CollisionUtil.voxelShapeIntersectNoEmpty(shape, entityBB)) {
                    continue;
                }
            }

            return false;
        }

        return true;
        // Paper end - optimise collisions
    }

    default <T extends Entity> List<T> getEntitiesOfClass(Class<T> entityClass, AABB box) {
        return this.getEntitiesOfClass(entityClass, box, EntitySelector.NO_SPECTATORS);
    }

    default List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB box) {
        // Paper start - optimise collisions
        // first behavior change is to correctly check for empty AABB
        if (io.papermc.paper.util.CollisionUtil.isEmpty(box)) {
            // reduce indirection by always returning type with same class
            return new java.util.ArrayList<>();
        }

        // to comply with vanilla intersection rules, expand by -epsilon so that we only get stuff we definitely collide with.
        // Vanilla for hard collisions has this backwards, and they expand by +epsilon but this causes terrible problems
        // specifically with boat collisions.
        box = box.inflate(-io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON, -io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON, -io.papermc.paper.util.CollisionUtil.COLLISION_EPSILON);

        final List<Entity> entities;
        if (entity != null && entity.hardCollides()) {
            entities = this.getEntities(entity, box, null);
        } else {
            entities = this.getHardCollidingEntities(entity, box, null);
        }

        final List<VoxelShape> ret = new java.util.ArrayList<>(Math.min(25, entities.size()));

        for (int i = 0, len = entities.size(); i < len; ++i) {
            final Entity otherEntity = entities.get(i);

            if (otherEntity.isSpectator()) {
                continue;
            }

            if ((entity == null && otherEntity.canBeCollidedWith()) || (entity != null && entity.canCollideWith(otherEntity))) {
                ret.add(Shapes.create(otherEntity.getBoundingBox()));
            }
        }

        return ret;
        // Paper end - optimise collisions
    }

    // Paper start
    default @Nullable Player findNearbyPlayer(Entity entity, double maxDistance, @Nullable Predicate<Entity> predicate) {
        return this.getNearestPlayer(entity.getX(), entity.getY(), entity.getZ(), maxDistance, predicate);
    }
    // Paper end
    @Nullable
    default Player getNearestPlayer(double x, double y, double z, double maxDistance, @Nullable Predicate<Entity> targetPredicate) {
        double d = -1.0D;
        Player player = null;

        for(Player player2 : this.getLocalPlayers()) { // Folia - region threading
            if (targetPredicate == null || targetPredicate.test(player2)) {
                double e = player2.distanceToSqr(x, y, z);
                if ((maxDistance < 0.0D || e < maxDistance * maxDistance) && (d == -1.0D || e < d)) {
                    d = e;
                    player = player2;
                }
            }
        }

        return player;
    }

    // Paper start
    default List<org.bukkit.entity.HumanEntity> findNearbyBukkitPlayers(double x, double y, double z, double radius, boolean notSpectator) {
        return findNearbyBukkitPlayers(x, y, z, radius, notSpectator ? EntitySelector.NO_SPECTATORS : net.minecraft.world.entity.EntitySelector.NO_CREATIVE_OR_SPECTATOR);
    }

    default List<org.bukkit.entity.HumanEntity> findNearbyBukkitPlayers(double x, double y, double z, double radius, @Nullable Predicate<Entity> predicate) {
        com.google.common.collect.ImmutableList.Builder<org.bukkit.entity.HumanEntity> builder = com.google.common.collect.ImmutableList.builder();

        for (Player human : this.getLocalPlayers()) { // Folia - region threading
            if (predicate == null || predicate.test(human)) {
                double distanceSquared = human.distanceToSqr(x, y, z);

                if (radius < 0.0D || distanceSquared < radius * radius) {
                    builder.add(human.getBukkitEntity());
                }
            }
        }

        return builder.build();
    }
    // Paper end

    @Nullable
    default Player getNearestPlayer(Entity entity, double maxDistance) {
        return this.getNearestPlayer(entity.getX(), entity.getY(), entity.getZ(), maxDistance, false);
    }

    @Nullable
    default Player getNearestPlayer(double x, double y, double z, double maxDistance, boolean ignoreCreative) {
        Predicate<Entity> predicate = ignoreCreative ? EntitySelector.NO_CREATIVE_OR_SPECTATOR : EntitySelector.NO_SPECTATORS;
        return this.getNearestPlayer(x, y, z, maxDistance, predicate);
    }

    // Paper start
    default boolean hasNearbyAlivePlayerThatAffectsSpawning(double x, double y, double z, double range) {
        for (Player player : this.getLocalPlayers()) { // Folia - region threading
            if (EntitySelector.PLAYER_AFFECTS_SPAWNING.test(player)) { // combines NO_SPECTATORS and LIVING_ENTITY_STILL_ALIVE with an "affects spawning" check
                double distanceSqr = player.distanceToSqr(x, y, z);
                if (range < 0.0D || distanceSqr < range * range) {
                    return true;
                }
            }
        }
        return false;
    }
    // Paper end

    default boolean hasNearbyAlivePlayer(double x, double y, double z, double range) {
        for(Player player : this.getLocalPlayers()) { // Folia - region threading
            if (EntitySelector.NO_SPECTATORS.test(player) && EntitySelector.LIVING_ENTITY_STILL_ALIVE.test(player)) {
                double d = player.distanceToSqr(x, y, z);
                if (range < 0.0D || d < range * range) {
                    return true;
                }
            }
        }

        return false;
    }

    @Nullable
    default Player getNearestPlayer(TargetingConditions targetPredicate, LivingEntity entity) {
        return this.getNearestEntity(this.getLocalPlayers(), targetPredicate, entity, entity.getX(), entity.getY(), entity.getZ()); // Folia - region threading
    }

    @Nullable
    default Player getNearestPlayer(TargetingConditions targetPredicate, LivingEntity entity, double x, double y, double z) {
        return this.getNearestEntity(this.getLocalPlayers(), targetPredicate, entity, x, y, z); // Folia - region threading
    }

    @Nullable
    default Player getNearestPlayer(TargetingConditions targetPredicate, double x, double y, double z) {
        return this.getNearestEntity(this.getLocalPlayers(), targetPredicate, (LivingEntity)null, x, y, z); // Folia - region threading
    }

    @Nullable
    default <T extends LivingEntity> T getNearestEntity(Class<? extends T> entityClass, TargetingConditions targetPredicate, @Nullable LivingEntity entity, double x, double y, double z, AABB box) {
        return this.getNearestEntity(this.getEntitiesOfClass(entityClass, box, (entityOfClass) -> {
            return true;
        }), targetPredicate, entity, x, y, z);
    }

    @Nullable
    default <T extends LivingEntity> T getNearestEntity(List<? extends T> entityList, TargetingConditions targetPredicate, @Nullable LivingEntity entity, double x, double y, double z) {
        double d = -1.0D;
        T livingEntity = null;

        for(T livingEntity2 : entityList) {
            // Paper start - move up
            // don't check entities outside closest range
            double e = livingEntity2.distanceToSqr(x, y, z);
            if (d == -1.0D || e < d) {
            // Paper end - move up
            if (targetPredicate.test(entity, livingEntity2)) {
                // Paper - move up
                    d = e;
                    livingEntity = livingEntity2;
                }
            }
        }

        return livingEntity;
    }

    default List<Player> getNearbyPlayers(TargetingConditions targetPredicate, LivingEntity entity, AABB box) {
        List<Player> list = Lists.newArrayList();

        for(Player player : this.getLocalPlayers()) { // Folia - region threading
            if (box.contains(player.getX(), player.getY(), player.getZ()) && targetPredicate.test(entity, player)) {
                list.add(player);
            }
        }

        return list;
    }

    default <T extends LivingEntity> List<T> getNearbyEntities(Class<T> entityClass, TargetingConditions targetPredicate, LivingEntity targetingEntity, AABB box) {
        List<T> list = this.getEntitiesOfClass(entityClass, box, (livingEntityx) -> {
            return true;
        });
        List<T> list2 = Lists.newArrayList();

        for(T livingEntity : list) {
            if (targetPredicate.test(targetingEntity, livingEntity)) {
                list2.add(livingEntity);
            }
        }

        return list2;
    }

    @Nullable
    default Player getPlayerByUUID(UUID uuid) {
        for(Player player : this.getLocalPlayers()) { // Folia - region threading
            if (uuid.equals(player.getUUID())) {
                return player;
            }
        }

        return null;
    }

    // Paper start
    @Nullable
    default Player getGlobalPlayerByUUID(UUID uuid) {
        return this.getPlayerByUUID(uuid);
    }
    // Paper end
}
