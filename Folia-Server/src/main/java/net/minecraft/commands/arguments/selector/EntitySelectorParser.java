package net.minecraft.commands.arguments.selector;

import com.google.common.primitives.Doubles;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.WrappedMinMaxBounds;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class EntitySelectorParser {

    public static final char SYNTAX_SELECTOR_START = '@';
    private static final char SYNTAX_OPTIONS_START = '[';
    private static final char SYNTAX_OPTIONS_END = ']';
    public static final char SYNTAX_OPTIONS_KEY_VALUE_SEPARATOR = '=';
    private static final char SYNTAX_OPTIONS_SEPARATOR = ',';
    public static final char SYNTAX_NOT = '!';
    public static final char SYNTAX_TAG = '#';
    private static final char SELECTOR_NEAREST_PLAYER = 'p';
    private static final char SELECTOR_ALL_PLAYERS = 'a';
    private static final char SELECTOR_RANDOM_PLAYERS = 'r';
    private static final char SELECTOR_CURRENT_ENTITY = 's';
    private static final char SELECTOR_ALL_ENTITIES = 'e';
    public static final SimpleCommandExceptionType ERROR_INVALID_NAME_OR_UUID = new SimpleCommandExceptionType(Component.translatable("argument.entity.invalid"));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_SELECTOR_TYPE = new DynamicCommandExceptionType((object) -> {
        return Component.translatable("argument.entity.selector.unknown", object);
    });
    public static final SimpleCommandExceptionType ERROR_SELECTORS_NOT_ALLOWED = new SimpleCommandExceptionType(Component.translatable("argument.entity.selector.not_allowed"));
    public static final SimpleCommandExceptionType ERROR_MISSING_SELECTOR_TYPE = new SimpleCommandExceptionType(Component.translatable("argument.entity.selector.missing"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_END_OF_OPTIONS = new SimpleCommandExceptionType(Component.translatable("argument.entity.options.unterminated"));
    public static final DynamicCommandExceptionType ERROR_EXPECTED_OPTION_VALUE = new DynamicCommandExceptionType((object) -> {
        return Component.translatable("argument.entity.options.valueless", object);
    });
    public static final BiConsumer<Vec3, List<? extends Entity>> ORDER_NEAREST = (vec3d, list) -> {
        list.sort((entity, entity1) -> {
            return Doubles.compare(entity.distanceToSqr(vec3d), entity1.distanceToSqr(vec3d));
        });
    };
    public static final BiConsumer<Vec3, List<? extends Entity>> ORDER_FURTHEST = (vec3d, list) -> {
        list.sort((entity, entity1) -> {
            return Doubles.compare(entity1.distanceToSqr(vec3d), entity.distanceToSqr(vec3d));
        });
    };
    public static final BiConsumer<Vec3, List<? extends Entity>> ORDER_RANDOM = (vec3d, list) -> {
        Collections.shuffle(list);
    };
    public static final BiFunction<SuggestionsBuilder, Consumer<SuggestionsBuilder>, CompletableFuture<Suggestions>> SUGGEST_NOTHING = (suggestionsbuilder, consumer) -> {
        return suggestionsbuilder.buildFuture();
    };
    private final StringReader reader;
    private final boolean allowSelectors;
    private int maxResults;
    private boolean includesEntities;
    private boolean worldLimited;
    private MinMaxBounds.Doubles distance;
    private MinMaxBounds.Ints level;
    @Nullable
    private Double x;
    @Nullable
    private Double y;
    @Nullable
    private Double z;
    @Nullable
    private Double deltaX;
    @Nullable
    private Double deltaY;
    @Nullable
    private Double deltaZ;
    private WrappedMinMaxBounds rotX;
    private WrappedMinMaxBounds rotY;
    private Predicate<Entity> predicate;
    private BiConsumer<Vec3, List<? extends Entity>> order;
    private boolean currentEntity;
    @Nullable
    private String playerName;
    private int startPosition;
    @Nullable
    private UUID entityUUID;
    private BiFunction<SuggestionsBuilder, Consumer<SuggestionsBuilder>, CompletableFuture<Suggestions>> suggestions;
    private boolean hasNameEquals;
    private boolean hasNameNotEquals;
    private boolean isLimited;
    private boolean isSorted;
    private boolean hasGamemodeEquals;
    private boolean hasGamemodeNotEquals;
    private boolean hasTeamEquals;
    private boolean hasTeamNotEquals;
    @Nullable
    private EntityType<?> type;
    private boolean typeInverse;
    private boolean hasScores;
    private boolean hasAdvancements;
    private boolean usesSelectors;
    public boolean parsingEntityArgumentSuggestions; // Paper - track when parsing EntityArgument suggestions

    public EntitySelectorParser(StringReader reader) {
        this(reader, true);
    }

    public EntitySelectorParser(StringReader reader, boolean atAllowed) {
        // Paper start
        this(reader, atAllowed, false);
    }
    public EntitySelectorParser(StringReader reader, boolean atAllowed, boolean parsingEntityArgumentSuggestions) {
        this.parsingEntityArgumentSuggestions = parsingEntityArgumentSuggestions;
        // Paper end
        this.distance = MinMaxBounds.Doubles.ANY;
        this.level = MinMaxBounds.Ints.ANY;
        this.rotX = WrappedMinMaxBounds.ANY;
        this.rotY = WrappedMinMaxBounds.ANY;
        this.predicate = (entity) -> {
            return true;
        };
        this.order = EntitySelector.ORDER_ARBITRARY;
        this.suggestions = EntitySelectorParser.SUGGEST_NOTHING;
        this.reader = reader;
        this.allowSelectors = atAllowed;
    }

    public EntitySelector getSelector() {
        AABB axisalignedbb;

        if (this.deltaX == null && this.deltaY == null && this.deltaZ == null) {
            if (this.distance.max().isPresent()) {
                double d0 = (Double) this.distance.max().get();

                axisalignedbb = new AABB(-d0, -d0, -d0, d0 + 1.0D, d0 + 1.0D, d0 + 1.0D);
            } else {
                axisalignedbb = null;
            }
        } else {
            axisalignedbb = this.createAabb(this.deltaX == null ? 0.0D : this.deltaX, this.deltaY == null ? 0.0D : this.deltaY, this.deltaZ == null ? 0.0D : this.deltaZ);
        }

        Function<Vec3, Vec3> function; // CraftBukkit - decompile error

        if (this.x == null && this.y == null && this.z == null) {
            function = (vec3d) -> {
                return vec3d;
            };
        } else {
            function = (vec3d) -> {
                return new Vec3(this.x == null ? vec3d.x : this.x, this.y == null ? vec3d.y : this.y, this.z == null ? vec3d.z : this.z);
            };
        }

        return new EntitySelector(this.maxResults, this.includesEntities, this.worldLimited, this.predicate, this.distance, function, axisalignedbb, this.order, this.currentEntity, this.playerName, this.entityUUID, this.type, this.usesSelectors);
    }

    private AABB createAabb(double x, double y, double z) {
        boolean flag = x < 0.0D;
        boolean flag1 = y < 0.0D;
        boolean flag2 = z < 0.0D;
        double d3 = flag ? x : 0.0D;
        double d4 = flag1 ? y : 0.0D;
        double d5 = flag2 ? z : 0.0D;
        double d6 = (flag ? 0.0D : x) + 1.0D;
        double d7 = (flag1 ? 0.0D : y) + 1.0D;
        double d8 = (flag2 ? 0.0D : z) + 1.0D;

        return new AABB(d3, d4, d5, d6, d7, d8);
    }

    private void finalizePredicates() {
        if (this.rotX != WrappedMinMaxBounds.ANY) {
            this.predicate = this.predicate.and(this.createRotationPredicate(this.rotX, Entity::getXRot));
        }

        if (this.rotY != WrappedMinMaxBounds.ANY) {
            this.predicate = this.predicate.and(this.createRotationPredicate(this.rotY, Entity::getYRot));
        }

        if (!this.level.isAny()) {
            this.predicate = this.predicate.and((entity) -> {
                return !(entity instanceof ServerPlayer) ? false : this.level.matches(((ServerPlayer) entity).experienceLevel);
            });
        }

    }

    private Predicate<Entity> createRotationPredicate(WrappedMinMaxBounds angleRange, ToDoubleFunction<Entity> entityToAngle) {
        double d0 = (double) Mth.wrapDegrees(angleRange.min() == null ? 0.0F : angleRange.min());
        double d1 = (double) Mth.wrapDegrees(angleRange.max() == null ? 359.0F : angleRange.max());

        return (entity) -> {
            double d2 = Mth.wrapDegrees(entityToAngle.applyAsDouble(entity));

            return d0 > d1 ? d2 >= d0 || d2 <= d1 : d2 >= d0 && d2 <= d1;
        };
    }

    // CraftBukkit start
    protected void parseSelector(boolean overridePermissions) throws CommandSyntaxException {
        this.usesSelectors = !overridePermissions;
        // CraftBukkit end
        this.suggestions = this::suggestSelector;
        if (!this.reader.canRead()) {
            throw EntitySelectorParser.ERROR_MISSING_SELECTOR_TYPE.createWithContext(this.reader);
        } else {
            int i = this.reader.getCursor();
            char c0 = this.reader.read();

            if (c0 == 'p') {
                this.maxResults = 1;
                this.includesEntities = false;
                this.order = EntitySelectorParser.ORDER_NEAREST;
                this.limitToType(EntityType.PLAYER);
            } else if (c0 == 'a') {
                this.maxResults = Integer.MAX_VALUE;
                this.includesEntities = false;
                this.order = EntitySelector.ORDER_ARBITRARY;
                this.limitToType(EntityType.PLAYER);
            } else if (c0 == 'r') {
                this.maxResults = 1;
                this.includesEntities = false;
                this.order = EntitySelectorParser.ORDER_RANDOM;
                this.limitToType(EntityType.PLAYER);
            } else if (c0 == 's') {
                this.maxResults = 1;
                this.includesEntities = true;
                this.currentEntity = true;
            } else {
                if (c0 != 'e') {
                    this.reader.setCursor(i);
                    throw EntitySelectorParser.ERROR_UNKNOWN_SELECTOR_TYPE.createWithContext(this.reader, "@" + String.valueOf(c0));
                }

                this.maxResults = Integer.MAX_VALUE;
                this.includesEntities = true;
                this.order = EntitySelector.ORDER_ARBITRARY;
                this.predicate = Entity::isAlive;
            }

            this.suggestions = this::suggestOpenOptions;
            if (this.reader.canRead() && this.reader.peek() == '[') {
                this.reader.skip();
                this.suggestions = this::suggestOptionsKeyOrClose;
                this.parseOptions();
            }

        }
    }

    protected void parseNameOrUUID() throws CommandSyntaxException {
        if (this.reader.canRead()) {
            this.suggestions = this::suggestName;
        }

        int i = this.reader.getCursor();
        String s = this.reader.readString();

        try {
            this.entityUUID = UUID.fromString(s);
            this.includesEntities = true;
        } catch (IllegalArgumentException illegalargumentexception) {
            if (s.isEmpty() || s.length() > 16) {
                this.reader.setCursor(i);
                throw EntitySelectorParser.ERROR_INVALID_NAME_OR_UUID.createWithContext(this.reader);
            }

            this.includesEntities = false;
            this.playerName = s;
        }

        this.maxResults = 1;
    }

    protected void parseOptions() throws CommandSyntaxException {
        this.suggestions = this::suggestOptionsKey;
        this.reader.skipWhitespace();

        while (true) {
            if (this.reader.canRead() && this.reader.peek() != ']') {
                this.reader.skipWhitespace();
                int i = this.reader.getCursor();
                String s = this.reader.readString();
                EntitySelectorOptions.Modifier playerselector_a = EntitySelectorOptions.get(this, s, i);

                this.reader.skipWhitespace();
                if (!this.reader.canRead() || this.reader.peek() != '=') {
                    this.reader.setCursor(i);
                    throw EntitySelectorParser.ERROR_EXPECTED_OPTION_VALUE.createWithContext(this.reader, s);
                }

                this.reader.skip();
                this.reader.skipWhitespace();
                this.suggestions = EntitySelectorParser.SUGGEST_NOTHING;
                playerselector_a.handle(this);
                this.reader.skipWhitespace();
                this.suggestions = this::suggestOptionsNextOrClose;
                if (!this.reader.canRead()) {
                    continue;
                }

                if (this.reader.peek() == ',') {
                    this.reader.skip();
                    this.suggestions = this::suggestOptionsKey;
                    continue;
                }

                if (this.reader.peek() != ']') {
                    throw EntitySelectorParser.ERROR_EXPECTED_END_OF_OPTIONS.createWithContext(this.reader);
                }
            }

            if (this.reader.canRead()) {
                this.reader.skip();
                this.suggestions = EntitySelectorParser.SUGGEST_NOTHING;
                return;
            }

            throw EntitySelectorParser.ERROR_EXPECTED_END_OF_OPTIONS.createWithContext(this.reader);
        }
    }

    public boolean shouldInvertValue() {
        this.reader.skipWhitespace();
        if (this.reader.canRead() && this.reader.peek() == '!') {
            this.reader.skip();
            this.reader.skipWhitespace();
            return true;
        } else {
            return false;
        }
    }

    public boolean isTag() {
        this.reader.skipWhitespace();
        if (this.reader.canRead() && this.reader.peek() == '#') {
            this.reader.skip();
            this.reader.skipWhitespace();
            return true;
        } else {
            return false;
        }
    }

    public StringReader getReader() {
        return this.reader;
    }

    public void addPredicate(Predicate<Entity> predicate) {
        this.predicate = this.predicate.and(predicate);
    }

    public void setWorldLimited() {
        this.worldLimited = true;
    }

    public MinMaxBounds.Doubles getDistance() {
        return this.distance;
    }

    public void setDistance(MinMaxBounds.Doubles distance) {
        this.distance = distance;
    }

    public MinMaxBounds.Ints getLevel() {
        return this.level;
    }

    public void setLevel(MinMaxBounds.Ints levelRange) {
        this.level = levelRange;
    }

    public WrappedMinMaxBounds getRotX() {
        return this.rotX;
    }

    public void setRotX(WrappedMinMaxBounds pitchRange) {
        this.rotX = pitchRange;
    }

    public WrappedMinMaxBounds getRotY() {
        return this.rotY;
    }

    public void setRotY(WrappedMinMaxBounds yawRange) {
        this.rotY = yawRange;
    }

    @Nullable
    public Double getX() {
        return this.x;
    }

    @Nullable
    public Double getY() {
        return this.y;
    }

    @Nullable
    public Double getZ() {
        return this.z;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public void setDeltaX(double dx) {
        this.deltaX = dx;
    }

    public void setDeltaY(double dy) {
        this.deltaY = dy;
    }

    public void setDeltaZ(double dz) {
        this.deltaZ = dz;
    }

    @Nullable
    public Double getDeltaX() {
        return this.deltaX;
    }

    @Nullable
    public Double getDeltaY() {
        return this.deltaY;
    }

    @Nullable
    public Double getDeltaZ() {
        return this.deltaZ;
    }

    public void setMaxResults(int limit) {
        this.maxResults = limit;
    }

    public void setIncludesEntities(boolean includesNonPlayers) {
        this.includesEntities = includesNonPlayers;
    }

    public BiConsumer<Vec3, List<? extends Entity>> getOrder() {
        return this.order;
    }

    public void setOrder(BiConsumer<Vec3, List<? extends Entity>> sorter) {
        this.order = sorter;
    }

    public EntitySelector parse() throws CommandSyntaxException {
        // CraftBukkit start
        return this.parse(false);
    }

    public EntitySelector parse(boolean overridePermissions) throws CommandSyntaxException {
        // CraftBukkit end
        this.startPosition = this.reader.getCursor();
        this.suggestions = this::suggestNameOrSelector;
        if (this.reader.canRead() && this.reader.peek() == '@') {
            if (!this.allowSelectors) {
                throw EntitySelectorParser.ERROR_SELECTORS_NOT_ALLOWED.createWithContext(this.reader);
            }

            this.reader.skip();
            this.parseSelector(overridePermissions); // CraftBukkit
        } else {
            this.parseNameOrUUID();
        }

        this.finalizePredicates();
        return this.getSelector();
    }

    private static void fillSelectorSuggestions(SuggestionsBuilder builder) {
        builder.suggest("@p", Component.translatable("argument.entity.selector.nearestPlayer"));
        builder.suggest("@a", Component.translatable("argument.entity.selector.allPlayers"));
        builder.suggest("@r", Component.translatable("argument.entity.selector.randomPlayer"));
        builder.suggest("@s", Component.translatable("argument.entity.selector.self"));
        builder.suggest("@e", Component.translatable("argument.entity.selector.allEntities"));
    }

    private CompletableFuture<Suggestions> suggestNameOrSelector(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        consumer.accept(builder);
        if (this.allowSelectors) {
            EntitySelectorParser.fillSelectorSuggestions(builder);
        }

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestName(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        SuggestionsBuilder suggestionsbuilder1 = builder.createOffset(this.startPosition);

        consumer.accept(suggestionsbuilder1);
        return builder.add(suggestionsbuilder1).buildFuture();
    }

    private CompletableFuture<Suggestions> suggestSelector(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        SuggestionsBuilder suggestionsbuilder1 = builder.createOffset(builder.getStart() - 1);

        EntitySelectorParser.fillSelectorSuggestions(suggestionsbuilder1);
        builder.add(suggestionsbuilder1);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOpenOptions(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest(String.valueOf('['));
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOptionsKeyOrClose(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest(String.valueOf(']'));
        EntitySelectorOptions.suggestNames(this, builder);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOptionsKey(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        EntitySelectorOptions.suggestNames(this, builder);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOptionsNextOrClose(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest(String.valueOf(','));
        builder.suggest(String.valueOf(']'));
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestEquals(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        builder.suggest(String.valueOf('='));
        return builder.buildFuture();
    }

    public boolean isCurrentEntity() {
        return this.currentEntity;
    }

    public void setSuggestions(BiFunction<SuggestionsBuilder, Consumer<SuggestionsBuilder>, CompletableFuture<Suggestions>> suggestionProvider) {
        this.suggestions = suggestionProvider;
    }

    public CompletableFuture<Suggestions> fillSuggestions(SuggestionsBuilder builder, Consumer<SuggestionsBuilder> consumer) {
        return (CompletableFuture) this.suggestions.apply(builder.createOffset(this.reader.getCursor()), consumer);
    }

    public boolean hasNameEquals() {
        return this.hasNameEquals;
    }

    public void setHasNameEquals(boolean selectsName) {
        this.hasNameEquals = selectsName;
    }

    public boolean hasNameNotEquals() {
        return this.hasNameNotEquals;
    }

    public void setHasNameNotEquals(boolean excludesName) {
        this.hasNameNotEquals = excludesName;
    }

    public boolean isLimited() {
        return this.isLimited;
    }

    public void setLimited(boolean hasLimit) {
        this.isLimited = hasLimit;
    }

    public boolean isSorted() {
        return this.isSorted;
    }

    public void setSorted(boolean hasSorter) {
        this.isSorted = hasSorter;
    }

    public boolean hasGamemodeEquals() {
        return this.hasGamemodeEquals;
    }

    public void setHasGamemodeEquals(boolean selectsGameMode) {
        this.hasGamemodeEquals = selectsGameMode;
    }

    public boolean hasGamemodeNotEquals() {
        return this.hasGamemodeNotEquals;
    }

    public void setHasGamemodeNotEquals(boolean excludesGameMode) {
        this.hasGamemodeNotEquals = excludesGameMode;
    }

    public boolean hasTeamEquals() {
        return this.hasTeamEquals;
    }

    public void setHasTeamEquals(boolean selectsTeam) {
        this.hasTeamEquals = selectsTeam;
    }

    public boolean hasTeamNotEquals() {
        return this.hasTeamNotEquals;
    }

    public void setHasTeamNotEquals(boolean excludesTeam) {
        this.hasTeamNotEquals = excludesTeam;
    }

    public void limitToType(EntityType<?> entityType) {
        this.type = entityType;
    }

    public void setTypeLimitedInversely() {
        this.typeInverse = true;
    }

    public boolean isTypeLimited() {
        return this.type != null;
    }

    public boolean isTypeLimitedInversely() {
        return this.typeInverse;
    }

    public boolean hasScores() {
        return this.hasScores;
    }

    public void setHasScores(boolean selectsScores) {
        this.hasScores = selectsScores;
    }

    public boolean hasAdvancements() {
        return this.hasAdvancements;
    }

    public void setHasAdvancements(boolean selectsAdvancements) {
        this.hasAdvancements = selectsAdvancements;
    }
}
