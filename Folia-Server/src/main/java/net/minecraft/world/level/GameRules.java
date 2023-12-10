package net.minecraft.world.level;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicLike;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public class GameRules {

    public static final int DEFAULT_RANDOM_TICK_SPEED = 3;
    static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<GameRules.Key<?>, GameRules.Type<?>> GAME_RULE_TYPES = Maps.newTreeMap(Comparator.comparing((gamerules_gamerulekey) -> {
        return gamerules_gamerulekey.id;
    }));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DOFIRETICK = GameRules.register("doFireTick", GameRules.Category.UPDATES, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_MOBGRIEFING = GameRules.register("mobGriefing", GameRules.Category.MOBS, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_KEEPINVENTORY = GameRules.register("keepInventory", GameRules.Category.PLAYER, GameRules.BooleanValue.create(false));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DOMOBSPAWNING = GameRules.register("doMobSpawning", GameRules.Category.SPAWNING, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DOMOBLOOT = GameRules.register("doMobLoot", GameRules.Category.DROPS, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DOBLOCKDROPS = GameRules.register("doTileDrops", GameRules.Category.DROPS, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DOENTITYDROPS = GameRules.register("doEntityDrops", GameRules.Category.DROPS, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_COMMANDBLOCKOUTPUT = GameRules.register("commandBlockOutput", GameRules.Category.CHAT, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_NATURAL_REGENERATION = GameRules.register("naturalRegeneration", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DAYLIGHT = GameRules.register("doDaylightCycle", GameRules.Category.UPDATES, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_LOGADMINCOMMANDS = GameRules.register("logAdminCommands", GameRules.Category.CHAT, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_SHOWDEATHMESSAGES = GameRules.register("showDeathMessages", GameRules.Category.CHAT, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.IntegerValue> RULE_RANDOMTICKING = GameRules.register("randomTickSpeed", GameRules.Category.UPDATES, GameRules.IntegerValue.create(3));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_SENDCOMMANDFEEDBACK = GameRules.register("sendCommandFeedback", GameRules.Category.CHAT, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_REDUCEDDEBUGINFO = GameRules.register("reducedDebugInfo", GameRules.Category.MISC, GameRules.BooleanValue.create(false, (minecraftserver, gamerules_gameruleboolean) -> {
        int i = gamerules_gameruleboolean.get() ? 22 : 23;
        Iterator iterator = minecraftserver.players().iterator(); // Paper

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            entityplayer.connection.send(new ClientboundEntityEventPacket(entityplayer, (byte) i));
        }

    }));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_SPECTATORSGENERATECHUNKS = GameRules.register("spectatorsGenerateChunks", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.IntegerValue> RULE_SPAWN_RADIUS = GameRules.register("spawnRadius", GameRules.Category.PLAYER, GameRules.IntegerValue.create(10));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DISABLE_ELYTRA_MOVEMENT_CHECK = GameRules.register("disableElytraMovementCheck", GameRules.Category.PLAYER, GameRules.BooleanValue.create(false));
    public static final GameRules.Key<GameRules.IntegerValue> RULE_MAX_ENTITY_CRAMMING = GameRules.register("maxEntityCramming", GameRules.Category.MOBS, GameRules.IntegerValue.create(24));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_WEATHER_CYCLE = GameRules.register("doWeatherCycle", GameRules.Category.UPDATES, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_LIMITED_CRAFTING = GameRules.register("doLimitedCrafting", GameRules.Category.PLAYER, GameRules.BooleanValue.create(false, (minecraftserver, gamerules_gameruleboolean) -> {
        Iterator iterator = minecraftserver.players().iterator(); // Paper

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            entityplayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.LIMITED_CRAFTING, gamerules_gameruleboolean.get() ? 1.0F : 0.0F));
        }

    }));
    public static final GameRules.Key<GameRules.IntegerValue> RULE_MAX_COMMAND_CHAIN_LENGTH = GameRules.register("maxCommandChainLength", GameRules.Category.MISC, GameRules.IntegerValue.create(65536));
    public static final GameRules.Key<GameRules.IntegerValue> RULE_COMMAND_MODIFICATION_BLOCK_LIMIT = GameRules.register("commandModificationBlockLimit", GameRules.Category.MISC, GameRules.IntegerValue.create(32768));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_ANNOUNCE_ADVANCEMENTS = GameRules.register("announceAdvancements", GameRules.Category.CHAT, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DISABLE_RAIDS = GameRules.register("disableRaids", GameRules.Category.MOBS, GameRules.BooleanValue.create(false));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DOINSOMNIA = GameRules.register("doInsomnia", GameRules.Category.SPAWNING, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DO_IMMEDIATE_RESPAWN = GameRules.register("doImmediateRespawn", GameRules.Category.PLAYER, GameRules.BooleanValue.create(false, (minecraftserver, gamerules_gameruleboolean) -> {
        Iterator iterator = minecraftserver.players().iterator(); // Paper

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            entityplayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.IMMEDIATE_RESPAWN, gamerules_gameruleboolean.get() ? 1.0F : 0.0F));
        }

    }));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DROWNING_DAMAGE = GameRules.register("drowningDamage", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_FALL_DAMAGE = GameRules.register("fallDamage", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_FIRE_DAMAGE = GameRules.register("fireDamage", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_FREEZE_DAMAGE = GameRules.register("freezeDamage", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DO_PATROL_SPAWNING = GameRules.register("doPatrolSpawning", GameRules.Category.SPAWNING, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DO_TRADER_SPAWNING = GameRules.register("doTraderSpawning", GameRules.Category.SPAWNING, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DO_WARDEN_SPAWNING = GameRules.register("doWardenSpawning", GameRules.Category.SPAWNING, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_FORGIVE_DEAD_PLAYERS = GameRules.register("forgiveDeadPlayers", GameRules.Category.MOBS, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_UNIVERSAL_ANGER = GameRules.register("universalAnger", GameRules.Category.MOBS, GameRules.BooleanValue.create(false));
    public static final GameRules.Key<GameRules.IntegerValue> RULE_PLAYERS_SLEEPING_PERCENTAGE = GameRules.register("playersSleepingPercentage", GameRules.Category.PLAYER, GameRules.IntegerValue.create(100));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_BLOCK_EXPLOSION_DROP_DECAY = GameRules.register("blockExplosionDropDecay", GameRules.Category.DROPS, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_MOB_EXPLOSION_DROP_DECAY = GameRules.register("mobExplosionDropDecay", GameRules.Category.DROPS, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_TNT_EXPLOSION_DROP_DECAY = GameRules.register("tntExplosionDropDecay", GameRules.Category.DROPS, GameRules.BooleanValue.create(false));
    public static final GameRules.Key<GameRules.IntegerValue> RULE_SNOW_ACCUMULATION_HEIGHT = GameRules.register("snowAccumulationHeight", GameRules.Category.UPDATES, GameRules.IntegerValue.create(1));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_WATER_SOURCE_CONVERSION = GameRules.register("waterSourceConversion", GameRules.Category.UPDATES, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_LAVA_SOURCE_CONVERSION = GameRules.register("lavaSourceConversion", GameRules.Category.UPDATES, GameRules.BooleanValue.create(false));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_GLOBAL_SOUND_EVENTS = GameRules.register("globalSoundEvents", GameRules.Category.MISC, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_DO_VINES_SPREAD = GameRules.register("doVinesSpread", GameRules.Category.UPDATES, GameRules.BooleanValue.create(true));
    public static final GameRules.Key<GameRules.BooleanValue> RULE_ENDER_PEARLS_VANISH_ON_DEATH = GameRules.register("enderPearlsVanishOnDeath", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true));
    private final Map<GameRules.Key<?>, GameRules.Value<?>> rules;
    private final GameRules.Value<?>[] gameruleArray; // Paper

    private static <T extends GameRules.Value<T>> GameRules.Key<T> register(String name, GameRules.Category category, GameRules.Type<T> type) {
        GameRules.Key<T> gamerules_gamerulekey = new GameRules.Key<>(name, category);
        GameRules.Type<?> gamerules_gameruledefinition1 = (GameRules.Type) GameRules.GAME_RULE_TYPES.put(gamerules_gamerulekey, type);

        if (gamerules_gameruledefinition1 != null) {
            throw new IllegalStateException("Duplicate game rule registration for " + name);
        } else {
            return gamerules_gamerulekey;
        }
    }

    public GameRules(DynamicLike<?> dynamic) {
        this();
        this.loadFromTag(dynamic);
    }

    public GameRules() {
        // Paper start - use this to ensure gameruleArray is initialized
        this((Map) GameRules.GAME_RULE_TYPES.entrySet().stream().collect(ImmutableMap.toImmutableMap(Entry::getKey, (entry) -> {
            return ((GameRules.Type) entry.getValue()).createRule();
        })));
        // Paper end
    }

    private GameRules(Map<GameRules.Key<?>, GameRules.Value<?>> rules) {
        this.rules = rules;

        // Paper start
        int arraySize = rules.keySet().stream().mapToInt(key -> key.gameRuleIndex).max().orElse(-1) + 1;
        GameRules.Value<?>[] values = new GameRules.Value[arraySize];

        for (Entry<GameRules.Key<?>, GameRules.Value<?>> entry : rules.entrySet()) {
            values[entry.getKey().gameRuleIndex] = entry.getValue();
        }

        this.gameruleArray = values;
        // Paper end
    }

    public <T extends GameRules.Value<T>> T getRule(GameRules.Key<T> key) {
        return key == null ? null : (T) this.gameruleArray[key.gameRuleIndex]; // Paper
    }

    public CompoundTag createTag() {
        CompoundTag nbttagcompound = new CompoundTag();

        this.rules.forEach((gamerules_gamerulekey, gamerules_gamerulevalue) -> {
            nbttagcompound.putString(gamerules_gamerulekey.id, gamerules_gamerulevalue.serialize());
        });
        return nbttagcompound;
    }

    private void loadFromTag(DynamicLike<?> dynamic) {
        this.rules.forEach((gamerules_gamerulekey, gamerules_gamerulevalue) -> {
            Optional<String> optional = dynamic.get(gamerules_gamerulekey.id).asString().result(); // CraftBukkit - decompile error

            Objects.requireNonNull(gamerules_gamerulevalue);
            optional.ifPresent(gamerules_gamerulevalue::deserialize);
        });
    }

    public GameRules copy() {
        return new GameRules((Map) this.rules.entrySet().stream().collect(ImmutableMap.toImmutableMap(Entry::getKey, (entry) -> {
            return ((GameRules.Value) entry.getValue()).copy();
        })));
    }

    public static void visitGameRuleTypes(GameRules.GameRuleTypeVisitor visitor) {
        GameRules.GAME_RULE_TYPES.forEach((gamerules_gamerulekey, gamerules_gameruledefinition) -> {
            GameRules.callVisitorCap(visitor, gamerules_gamerulekey, gamerules_gameruledefinition);
        });
    }

    private static <T extends GameRules.Value<T>> void callVisitorCap(GameRules.GameRuleTypeVisitor consumer, GameRules.Key<?> key, GameRules.Type<?> type) {
        consumer.visit((GameRules.Key<T>) key, (GameRules.Type<T>) type); // CraftBukkit - decompile error
        ((GameRules.Type<T>) type).callVisitor(consumer, (GameRules.Key<T>) key); // CraftBukkit - decompile error
    }

    public void assignFrom(GameRules rules, @Nullable net.minecraft.server.level.ServerLevel server) { // Paper
        rules.rules.keySet().forEach((gamerules_gamerulekey) -> {
            this.assignCap(gamerules_gamerulekey, rules, server);
        });
    }

    private <T extends GameRules.Value<T>> void assignCap(GameRules.Key<T> key, GameRules rules, @Nullable net.minecraft.server.level.ServerLevel server) { // Paper
        T t0 = rules.getRule(key);

        this.getRule(key).setFrom(t0, server);
    }

    public boolean getBoolean(GameRules.Key<GameRules.BooleanValue> rule) {
        return ((GameRules.BooleanValue) this.getRule(rule)).get();
    }

    public int getInt(GameRules.Key<GameRules.IntegerValue> rule) {
        return ((GameRules.IntegerValue) this.getRule(rule)).get();
    }

    public static final class Key<T extends GameRules.Value<T>> {
        // Paper start
        private static int lastGameRuleIndex = 0;
        public final int gameRuleIndex = lastGameRuleIndex++;
        // Paper end

        final String id;
        private final GameRules.Category category;

        public Key(String name, GameRules.Category category) {
            this.id = name;
            this.category = category;
        }

        public String toString() {
            return this.id;
        }

        public boolean equals(Object object) {
            return this == object ? true : object instanceof GameRules.Key && ((GameRules.Key) object).id.equals(this.id);
        }

        public int hashCode() {
            return this.id.hashCode();
        }

        public String getId() {
            return this.id;
        }

        public String getDescriptionId() {
            return "gamerule." + this.id;
        }

        public GameRules.Category getCategory() {
            return this.category;
        }
    }

    public static enum Category {

        PLAYER("gamerule.category.player"), MOBS("gamerule.category.mobs"), SPAWNING("gamerule.category.spawning"), DROPS("gamerule.category.drops"), UPDATES("gamerule.category.updates"), CHAT("gamerule.category.chat"), MISC("gamerule.category.misc");

        private final String descriptionId;

        private Category(String s) {
            this.descriptionId = s;
        }

        public String getDescriptionId() {
            return this.descriptionId;
        }
    }

    public static class Type<T extends GameRules.Value<T>> {

        private final Supplier<ArgumentType<?>> argument;
        private final Function<GameRules.Type<T>, T> constructor;
        final BiConsumer<net.minecraft.server.level.ServerLevel, T> callback; // Paper
        private final GameRules.VisitorCaller<T> visitorCaller;

        Type(Supplier<ArgumentType<?>> argumentType, Function<GameRules.Type<T>, T> ruleFactory, BiConsumer<net.minecraft.server.level.ServerLevel, T> changeCallback, GameRules.VisitorCaller<T> ruleAcceptor) { // Paper
            this.argument = argumentType;
            this.constructor = ruleFactory;
            this.callback = changeCallback;
            this.visitorCaller = ruleAcceptor;
        }

        public RequiredArgumentBuilder<CommandSourceStack, ?> createArgument(String name) {
            return Commands.argument(name, (ArgumentType) this.argument.get());
        }

        public T createRule() {
            return this.constructor.apply(this); // CraftBukkit - decompile error
        }

        public void callVisitor(GameRules.GameRuleTypeVisitor consumer, GameRules.Key<T> key) {
            this.visitorCaller.call(consumer, key, this);
        }
    }

    public abstract static class Value<T extends GameRules.Value<T>> {

        protected final GameRules.Type<T> type;

        public Value(GameRules.Type<T> type) {
            this.type = type;
        }

        protected abstract void updateFromArgument(CommandContext<CommandSourceStack> context, String name, GameRules.Key<T> gameRuleKey); // Paper

        public void setFromArgument(CommandContext<CommandSourceStack> context, String name, GameRules.Key<T> gameRuleKey) { // Paper
            this.updateFromArgument(context, name, gameRuleKey); // Paper
            this.onChanged(((CommandSourceStack) context.getSource()).getLevel()); // Paper
        }

        public void onChanged(@Nullable net.minecraft.server.level.ServerLevel server) { // Paper
            if (server != null) {
                this.type.callback.accept(server, this.getSelf());
            }

        }

        public abstract void deserialize(String value); // PAIL - private->public

        public abstract String serialize();

        public String toString() {
            return this.serialize();
        }

        public abstract int getCommandResult();

        protected abstract T getSelf();

        protected abstract T copy();

        public abstract void setFrom(T rule, @Nullable net.minecraft.server.level.ServerLevel level); // Paper
    }

    public interface GameRuleTypeVisitor {

        default <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {}

        default void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {}

        default void visitInteger(GameRules.Key<GameRules.IntegerValue> key, GameRules.Type<GameRules.IntegerValue> type) {}
    }

    public static class BooleanValue extends GameRules.Value<GameRules.BooleanValue> {

        private boolean value;

        static GameRules.Type<GameRules.BooleanValue> create(boolean initialValue, BiConsumer<net.minecraft.server.level.ServerLevel, GameRules.BooleanValue> changeCallback) { // Paper
            return new GameRules.Type<>(BoolArgumentType::bool, (gamerules_gameruledefinition) -> {
                return new GameRules.BooleanValue(gamerules_gameruledefinition, initialValue);
            }, changeCallback, GameRules.GameRuleTypeVisitor::visitBoolean);
        }

        static GameRules.Type<GameRules.BooleanValue> create(boolean initialValue) {
            return BooleanValue.create(initialValue, (minecraftserver, gamerules_gameruleboolean) -> {
            });
        }

        public BooleanValue(GameRules.Type<GameRules.BooleanValue> type, boolean initialValue) {
            super(type);
            this.value = initialValue;
        }

        @Override
        protected void updateFromArgument(CommandContext<CommandSourceStack> context, String name, GameRules.Key<BooleanValue> gameRuleKey) { // Paper start
            io.papermc.paper.event.world.WorldGameRuleChangeEvent event = new io.papermc.paper.event.world.WorldGameRuleChangeEvent(context.getSource().getBukkitWorld(), context.getSource().getBukkitSender(), (org.bukkit.GameRule<Boolean>) org.bukkit.GameRule.getByName(gameRuleKey.toString()), String.valueOf(BoolArgumentType.getBool(context, name)));
            if (!event.callEvent()) return;
            this.value = Boolean.parseBoolean(event.getValue());
            // Paper end
        }

        public boolean get() {
            return this.value;
        }

        public void set(boolean value, @Nullable net.minecraft.server.level.ServerLevel server) { // Paper
            this.value = value;
            this.onChanged(server);
        }

        @Override
        public String serialize() {
            return Boolean.toString(this.value);
        }

        @Override
        public void deserialize(String value) { // PAIL - protected->public
            this.value = Boolean.parseBoolean(value);
        }

        @Override
        public int getCommandResult() {
            return this.value ? 1 : 0;
        }

        @Override
        protected GameRules.BooleanValue getSelf() {
            return this;
        }

        @Override
        protected GameRules.BooleanValue copy() {
            return new GameRules.BooleanValue(this.type, this.value);
        }

        public void setFrom(GameRules.BooleanValue rule, @Nullable net.minecraft.server.level.ServerLevel server) { // Paper
            this.value = rule.value;
            this.onChanged(server);
        }
    }

    public static class IntegerValue extends GameRules.Value<GameRules.IntegerValue> {

        private int value;

        private static GameRules.Type<GameRules.IntegerValue> create(int initialValue, BiConsumer<net.minecraft.server.level.ServerLevel, GameRules.IntegerValue> changeCallback) { // Paper
            return new GameRules.Type<>(IntegerArgumentType::integer, (gamerules_gameruledefinition) -> {
                return new GameRules.IntegerValue(gamerules_gameruledefinition, initialValue);
            }, changeCallback, GameRules.GameRuleTypeVisitor::visitInteger);
        }

        static GameRules.Type<GameRules.IntegerValue> create(int initialValue) {
            return IntegerValue.create(initialValue, (minecraftserver, gamerules_gameruleint) -> {
            });
        }

        public IntegerValue(GameRules.Type<GameRules.IntegerValue> rule, int initialValue) {
            super(rule);
            this.value = initialValue;
        }

        @Override
        protected void updateFromArgument(CommandContext<CommandSourceStack> context, String name, GameRules.Key<IntegerValue> gameRuleKey) { // Paper start
            io.papermc.paper.event.world.WorldGameRuleChangeEvent event = new io.papermc.paper.event.world.WorldGameRuleChangeEvent(context.getSource().getBukkitWorld(), context.getSource().getBukkitSender(), (org.bukkit.GameRule<Integer>) org.bukkit.GameRule.getByName(gameRuleKey.toString()), String.valueOf(IntegerArgumentType.getInteger(context, name)));
            if (!event.callEvent()) return;
            this.value = Integer.parseInt(event.getValue());
            // Paper end
        }

        public int get() {
            return this.value;
        }

        public void set(int value, @Nullable net.minecraft.server.level.ServerLevel server) { // Paper
            this.value = value;
            this.onChanged(server);
        }

        @Override
        public String serialize() {
            return Integer.toString(this.value);
        }

        @Override
        public void deserialize(String value) { // PAIL - protected->public
            this.value = IntegerValue.safeParse(value);
        }

        public boolean tryDeserialize(String input) {
            try {
                this.value = Integer.parseInt(input);
                return true;
            } catch (NumberFormatException numberformatexception) {
                return false;
            }
        }

        private static int safeParse(String input) {
            if (!input.isEmpty()) {
                try {
                    return Integer.parseInt(input);
                } catch (NumberFormatException numberformatexception) {
                    GameRules.LOGGER.warn("Failed to parse integer {}", input);
                }
            }

            return 0;
        }

        @Override
        public int getCommandResult() {
            return this.value;
        }

        @Override
        protected GameRules.IntegerValue getSelf() {
            return this;
        }

        @Override
        protected GameRules.IntegerValue copy() {
            return new GameRules.IntegerValue(this.type, this.value);
        }

        public void setFrom(GameRules.IntegerValue rule, @Nullable net.minecraft.server.level.ServerLevel server) { // Paper
            this.value = rule.value;
            this.onChanged(server);
        }
    }

    private interface VisitorCaller<T extends GameRules.Value<T>> {

        void call(GameRules.GameRuleTypeVisitor consumer, GameRules.Key<T> key, GameRules.Type<T> type);
    }
}
