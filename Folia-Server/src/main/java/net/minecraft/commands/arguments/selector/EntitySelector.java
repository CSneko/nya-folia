package net.minecraft.commands.arguments.selector;

import com.google.common.collect.Lists;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class EntitySelector {

    public static final int INFINITE = Integer.MAX_VALUE;
    public static final BiConsumer<Vec3, List<? extends Entity>> ORDER_ARBITRARY = (vec3d, list) -> {
    };
    private static final EntityTypeTest<Entity, ?> ANY_TYPE = new EntityTypeTest<Entity, Entity>() {
        public Entity tryCast(Entity obj) {
            return obj;
        }

        @Override
        public Class<? extends Entity> getBaseClass() {
            return Entity.class;
        }
    };
    private final int maxResults;
    private final boolean includesEntities;
    private final boolean worldLimited;
    private final Predicate<Entity> predicate;
    private final MinMaxBounds.Doubles range;
    private final Function<Vec3, Vec3> position;
    @Nullable
    private final AABB aabb;
    private final BiConsumer<Vec3, List<? extends Entity>> order;
    private final boolean currentEntity;
    @Nullable
    private final String playerName;
    @Nullable
    private final UUID entityUUID;
    private final EntityTypeTest<Entity, ?> type;
    private final boolean usesSelector;

    public EntitySelector(int count, boolean includesNonPlayers, boolean localWorldOnly, Predicate<Entity> basePredicate, MinMaxBounds.Doubles distance, Function<Vec3, Vec3> positionOffset, @Nullable AABB box, BiConsumer<Vec3, List<? extends Entity>> sorter, boolean senderOnly, @Nullable String playerName, @Nullable UUID uuid, @Nullable EntityType<?> type, boolean usesAt) {
        this.maxResults = count;
        this.includesEntities = includesNonPlayers;
        this.worldLimited = localWorldOnly;
        this.predicate = basePredicate;
        this.range = distance;
        this.position = positionOffset;
        this.aabb = box;
        this.order = sorter;
        this.currentEntity = senderOnly;
        this.playerName = playerName;
        this.entityUUID = uuid;
        this.type = (EntityTypeTest) (type == null ? EntitySelector.ANY_TYPE : type);
        this.usesSelector = usesAt;
    }

    public int getMaxResults() {
        return this.maxResults;
    }

    public boolean includesEntities() {
        return this.includesEntities;
    }

    public boolean isSelfSelector() {
        return this.currentEntity;
    }

    public boolean isWorldLimited() {
        return this.worldLimited;
    }

    public boolean usesSelector() {
        return this.usesSelector;
    }

    private void checkPermissions(CommandSourceStack source) throws CommandSyntaxException {
        if (source.bypassSelectorPermissions || (this.usesSelector && !source.hasPermission(2, "minecraft.command.selector"))) { // CraftBukkit // Paper
            throw EntityArgument.ERROR_SELECTORS_NOT_ALLOWED.create();
        }
    }

    public Entity findSingleEntity(CommandSourceStack source) throws CommandSyntaxException {
        this.checkPermissions(source);
        List<? extends Entity> list = this.findEntities(source);

        if (list.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        } else if (list.size() > 1) {
            throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.create();
        } else {
            return (Entity) list.get(0);
        }
    }

    public List<? extends Entity> findEntities(CommandSourceStack source) throws CommandSyntaxException {
        return this.findEntitiesRaw(source).stream().filter((entity) -> {
            return entity.getType().isEnabled(source.enabledFeatures());
        }).toList();
    }

    private List<? extends Entity> findEntitiesRaw(CommandSourceStack source) throws CommandSyntaxException {
        this.checkPermissions(source);
        if (!this.includesEntities) {
            return this.findPlayers(source);
        } else if (this.playerName != null) {
            ServerPlayer entityplayer = source.getServer().getPlayerList().getPlayerByName(this.playerName);

            return (List) (entityplayer == null ? Collections.emptyList() : Lists.newArrayList(new ServerPlayer[]{entityplayer}));
        } else if (this.entityUUID != null) {
            Iterator iterator = source.getServer().getAllLevels().iterator();

            Entity entity;

            do {
                if (!iterator.hasNext()) {
                    return Collections.emptyList();
                }

                ServerLevel worldserver = (ServerLevel) iterator.next();

                entity = worldserver.getEntity(this.entityUUID);
            } while (entity == null);

            return Lists.newArrayList(new Entity[]{entity});
        } else {
            Vec3 vec3d = (Vec3) this.position.apply(source.getPosition());
            Predicate<Entity> predicate = this.getPredicate(vec3d);

            if (this.currentEntity) {
                return (List) (source.getEntity() != null && predicate.test(source.getEntity()) ? Lists.newArrayList(new Entity[]{source.getEntity()}) : Collections.emptyList());
            } else {
                List<Entity> list = Lists.newArrayList();

                if (this.isWorldLimited()) {
                    this.addEntities(list, source.getLevel(), vec3d, predicate);
                } else {
                    Iterator iterator1 = source.getServer().getAllLevels().iterator();

                    while (iterator1.hasNext()) {
                        ServerLevel worldserver1 = (ServerLevel) iterator1.next();

                        this.addEntities(list, worldserver1, vec3d, predicate);
                    }
                }

                return this.sortAndLimit(vec3d, list);
            }
        }
    }

    private void addEntities(List<Entity> entities, ServerLevel world, Vec3 pos, Predicate<Entity> predicate) {
        int i = this.getResultLimit();

        if (entities.size() < i) {
            if (this.aabb != null) {
                world.getEntities(this.type, this.aabb.move(pos), predicate, entities, i);
            } else {
                world.getEntities(this.type, predicate, entities, i);
            }

        }
    }

    private int getResultLimit() {
        return this.order == EntitySelector.ORDER_ARBITRARY ? this.maxResults : Integer.MAX_VALUE;
    }

    public ServerPlayer findSinglePlayer(CommandSourceStack source) throws CommandSyntaxException {
        this.checkPermissions(source);
        List<ServerPlayer> list = this.findPlayers(source);

        if (list.size() != 1) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        } else {
            return (ServerPlayer) list.get(0);
        }
    }

    public List<ServerPlayer> findPlayers(CommandSourceStack source) throws CommandSyntaxException {
        this.checkPermissions(source);
        ServerPlayer entityplayer;

        if (this.playerName != null) {
            entityplayer = source.getServer().getPlayerList().getPlayerByName(this.playerName);
            return (List) (entityplayer == null ? Collections.emptyList() : Lists.newArrayList(new ServerPlayer[]{entityplayer}));
        } else if (this.entityUUID != null) {
            entityplayer = source.getServer().getPlayerList().getPlayer(this.entityUUID);
            return (List) (entityplayer == null ? Collections.emptyList() : Lists.newArrayList(new ServerPlayer[]{entityplayer}));
        } else {
            Vec3 vec3d = (Vec3) this.position.apply(source.getPosition());
            Predicate<Entity> predicate = this.getPredicate(vec3d);

            if (this.currentEntity) {
                Entity entity = source.getEntity();

                if (entity instanceof ServerPlayer) {
                    ServerPlayer entityplayer1 = (ServerPlayer) entity;

                    if (predicate.test(entityplayer1)) {
                        return Lists.newArrayList(new ServerPlayer[]{entityplayer1});
                    }
                }

                return Collections.emptyList();
            } else {
                int i = this.getResultLimit();
                Object object;

                if (this.isWorldLimited()) {
                    object = source.getLevel().getPlayers(predicate, i);
                } else {
                    object = Lists.newArrayList();
                    Iterator iterator = source.getServer().getPlayerList().getPlayers().iterator();

                    while (iterator.hasNext()) {
                        ServerPlayer entityplayer2 = (ServerPlayer) iterator.next();

                        if (predicate.test(entityplayer2)) {
                            ((List) object).add(entityplayer2);
                            if (((List) object).size() >= i) {
                                return (List) object;
                            }
                        }
                    }
                }

                return this.sortAndLimit(vec3d, (List) object);
            }
        }
    }

    private Predicate<Entity> getPredicate(Vec3 pos) {
        Predicate<Entity> predicate = this.predicate;

        if (this.aabb != null) {
            AABB axisalignedbb = this.aabb.move(pos);

            predicate = predicate.and((entity) -> {
                return axisalignedbb.intersects(entity.getBoundingBox());
            });
        }

        if (!this.range.isAny()) {
            predicate = predicate.and((entity) -> {
                return this.range.matchesSqr(entity.distanceToSqr(pos));
            });
        }

        return predicate;
    }

    private <T extends Entity> List<T> sortAndLimit(Vec3 pos, List<T> entities) {
        if (entities.size() > 1) {
            this.order.accept(pos, entities);
        }

        return entities.subList(0, Math.min(this.maxResults, entities.size()));
    }

    public static Component joinNames(List<? extends Entity> entities) {
        return ComponentUtils.formatList(entities, Entity::getDisplayName);
    }
}
