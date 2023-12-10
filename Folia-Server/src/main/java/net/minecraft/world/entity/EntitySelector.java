package net.minecraft.world.entity;

import com.google.common.base.Predicates;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Team;

public final class EntitySelector {

    public static final Predicate<Entity> ENTITY_STILL_ALIVE = Entity::isAlive;
    public static final Predicate<Entity> LIVING_ENTITY_STILL_ALIVE = (entity) -> {
        return entity.isAlive() && entity instanceof LivingEntity;
    };
    public static final Predicate<Entity> ENTITY_NOT_BEING_RIDDEN = (entity) -> {
        return entity.isAlive() && !entity.isVehicle() && !entity.isPassenger();
    };
    public static final Predicate<Entity> CONTAINER_ENTITY_SELECTOR = (entity) -> {
        return entity instanceof Container && entity.isAlive();
    };
    public static final Predicate<Entity> NO_CREATIVE_OR_SPECTATOR = (entity) -> {
        return !(entity instanceof Player) || !entity.isSpectator() && !((Player) entity).isCreative();
    };
    public static final Predicate<Entity> NO_SPECTATORS = (entity) -> {
        return !entity.isSpectator();
    };
    public static final Predicate<Entity> CAN_BE_COLLIDED_WITH = EntitySelector.NO_SPECTATORS.and(Entity::canBeCollidedWith);
    // Paper start
    public static Predicate<Player> IS_INSOMNIAC = (player) -> {
        net.minecraft.server.level.ServerPlayer serverPlayer = (net.minecraft.server.level.ServerPlayer) player;
        int playerInsomniaTicks = serverPlayer.level().paperConfig().entities.behavior.playerInsomniaStartTicks;

        if (playerInsomniaTicks <= 0) {
            return false;
        }

        return net.minecraft.util.Mth.clamp(serverPlayer.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.TIME_SINCE_REST)), 1, Integer.MAX_VALUE) >= playerInsomniaTicks;
    };
    // Paper end

    private EntitySelector() {}
    // Paper start
    public static final Predicate<Entity> PLAYER_AFFECTS_SPAWNING = (entity) -> {
        return !entity.isSpectator() && entity.isAlive() && entity instanceof Player player && player.affectsSpawning;
    };
    // Paper end

    public static Predicate<Entity> withinDistance(double x, double y, double z, double max) {
        double d4 = max * max;

        return (entity) -> {
            return entity != null && entity.distanceToSqr(x, y, z) <= d4;
        };
    }

    public static Predicate<Entity> pushableBy(Entity entity) {
        // Paper start - ignoreClimbing param
        return pushable(entity, false);
    }

    public static Predicate<Entity> pushable(Entity entity, boolean ignoreClimbing) {
        // Paper end
        Team scoreboardteambase = entity.getTeam();
        Team.CollisionRule scoreboardteambase_enumteampush = scoreboardteambase == null ? Team.CollisionRule.ALWAYS : scoreboardteambase.getCollisionRule();

        return (Predicate) (scoreboardteambase_enumteampush == Team.CollisionRule.NEVER ? Predicates.alwaysFalse() : EntitySelector.NO_SPECTATORS.and((entity1) -> {
            if (!entity1.isCollidable(ignoreClimbing) || !entity1.canCollideWithBukkit(entity) || !entity.canCollideWithBukkit(entity1)) { // CraftBukkit - collidable API // Paper - isCollidable
                return false;
            } else if (entity.level().isClientSide && (!(entity1 instanceof Player) || !((Player) entity1).isLocalPlayer())) {
                return false;
            } else {
                Team scoreboardteambase1 = entity1.getTeam();
                Team.CollisionRule scoreboardteambase_enumteampush1 = scoreboardteambase1 == null ? Team.CollisionRule.ALWAYS : scoreboardteambase1.getCollisionRule();

                if (scoreboardteambase_enumteampush1 == Team.CollisionRule.NEVER) {
                    return false;
                } else {
                    boolean flag = scoreboardteambase != null && scoreboardteambase.isAlliedTo(scoreboardteambase1);

                    return (scoreboardteambase_enumteampush == Team.CollisionRule.PUSH_OWN_TEAM || scoreboardteambase_enumteampush1 == Team.CollisionRule.PUSH_OWN_TEAM) && flag ? false : scoreboardteambase_enumteampush != Team.CollisionRule.PUSH_OTHER_TEAMS && scoreboardteambase_enumteampush1 != Team.CollisionRule.PUSH_OTHER_TEAMS || flag;
                }
            }
        }));
    }

    public static Predicate<Entity> notRiding(Entity entity) {
        return (entity1) -> {
            while (true) {
                if (entity1.isPassenger()) {
                    entity1 = entity1.getVehicle();
                    if (entity1 != entity) {
                        continue;
                    }

                    return false;
                }

                return true;
            }
        };
    }

    public static class MobCanWearArmorEntitySelector implements Predicate<Entity> {

        private final ItemStack itemStack;

        public MobCanWearArmorEntitySelector(ItemStack stack) {
            this.itemStack = stack;
        }

        public boolean test(@Nullable Entity entity) {
            if (!entity.isAlive()) {
                return false;
            } else if (!(entity instanceof LivingEntity)) {
                return false;
            } else {
                LivingEntity entityliving = (LivingEntity) entity;

                return entityliving.canTakeItem(this.itemStack);
            }
        }
    }
}
