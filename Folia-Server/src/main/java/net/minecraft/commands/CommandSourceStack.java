package net.minecraft.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.ResultConsumer;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.TaskChainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import com.mojang.brigadier.tree.CommandNode; // CraftBukkit

public class CommandSourceStack implements SharedSuggestionProvider, com.destroystokyo.paper.brigadier.BukkitBrigadierCommandSource { // Paper

    public static final SimpleCommandExceptionType ERROR_NOT_PLAYER = new SimpleCommandExceptionType(Component.translatable("permissions.requires.player"));
    public static final SimpleCommandExceptionType ERROR_NOT_ENTITY = new SimpleCommandExceptionType(Component.translatable("permissions.requires.entity"));
    public final CommandSource source;
    private final Vec3 worldPosition;
    private final ServerLevel level;
    private final int permissionLevel;
    private final String textName;
    private final Component displayName;
    private final MinecraftServer server;
    private final boolean silent;
    @Nullable
    private final Entity entity;
    @Nullable
    private final ResultConsumer<CommandSourceStack> consumer;
    private final EntityAnchorArgument.Anchor anchor;
    private final Vec2 rotation;
    private final CommandSigningContext signingContext;
    private final TaskChainer chatMessageChainer;
    private final IntConsumer returnValueConsumer;
    public java.util.Map<Thread, CommandNode> currentCommand = new java.util.concurrent.ConcurrentHashMap<>(); // CraftBukkit // Paper
    public boolean bypassSelectorPermissions = false; // Paper

    public CommandSourceStack(CommandSource output, Vec3 pos, Vec2 rot, ServerLevel world, int level, String name, Component displayName, MinecraftServer server, @Nullable Entity entity) {
        this(output, pos, rot, world, level, name, displayName, server, entity, false, (commandcontext, flag, j) -> {
        }, EntityAnchorArgument.Anchor.FEET, CommandSigningContext.ANONYMOUS, TaskChainer.immediate((Runnable run) -> { io.papermc.paper.threadedregions.RegionizedServer.getInstance().addTask(run);}), (j) -> { // Folia - region threading
        });
    }

    protected CommandSourceStack(CommandSource output, Vec3 pos, Vec2 rot, ServerLevel world, int level, String name, Component displayName, MinecraftServer server, @Nullable Entity entity, boolean silent, @Nullable ResultConsumer<CommandSourceStack> consumer, EntityAnchorArgument.Anchor entityAnchor, CommandSigningContext signedArguments, TaskChainer messageChainTaskQueue, IntConsumer returnValueConsumer) {
        this.source = output;
        this.worldPosition = pos;
        this.level = world;
        this.silent = silent;
        this.entity = entity;
        this.permissionLevel = level;
        this.textName = name;
        this.displayName = displayName;
        this.server = server;
        this.consumer = consumer;
        this.anchor = entityAnchor;
        this.rotation = rot;
        this.signingContext = signedArguments;
        this.chatMessageChainer = messageChainTaskQueue;
        this.returnValueConsumer = returnValueConsumer;
    }

    public CommandSourceStack withSource(CommandSource output) {
        return this.source == output ? this : new CommandSourceStack(output, this.worldPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor, this.signingContext, this.chatMessageChainer, this.returnValueConsumer);
    }

    public CommandSourceStack withEntity(Entity entity) {
        return this.entity == entity ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, entity.getName().getString(), entity.getDisplayName(), this.server, entity, this.silent, this.consumer, this.anchor, this.signingContext, this.chatMessageChainer, this.returnValueConsumer);
    }

    public CommandSourceStack withPosition(Vec3 position) {
        return this.worldPosition.equals(position) ? this : new CommandSourceStack(this.source, position, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor, this.signingContext, this.chatMessageChainer, this.returnValueConsumer);
    }

    public CommandSourceStack withRotation(Vec2 rotation) {
        return this.rotation.equals(rotation) ? this : new CommandSourceStack(this.source, this.worldPosition, rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor, this.signingContext, this.chatMessageChainer, this.returnValueConsumer);
    }

    public CommandSourceStack withCallback(ResultConsumer<CommandSourceStack> consumer) {
        return Objects.equals(this.consumer, consumer) ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, consumer, this.anchor, this.signingContext, this.chatMessageChainer, this.returnValueConsumer);
    }

    public CommandSourceStack withCallback(ResultConsumer<CommandSourceStack> consumer, BinaryOperator<ResultConsumer<CommandSourceStack>> merger) {
        ResultConsumer<CommandSourceStack> resultconsumer1 = (ResultConsumer) merger.apply(this.consumer, consumer);

        return this.withCallback(resultconsumer1);
    }

    public CommandSourceStack withSuppressedOutput() {
        return !this.silent && !this.source.alwaysAccepts() ? new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, true, this.consumer, this.anchor, this.signingContext, this.chatMessageChainer, this.returnValueConsumer) : this;
    }

    public CommandSourceStack withPermission(int level) {
        return level == this.permissionLevel ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, level, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor, this.signingContext, this.chatMessageChainer, this.returnValueConsumer);
    }

    public CommandSourceStack withMaximumPermission(int level) {
        return level <= this.permissionLevel ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, level, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor, this.signingContext, this.chatMessageChainer, this.returnValueConsumer);
    }

    public CommandSourceStack withAnchor(EntityAnchorArgument.Anchor anchor) {
        return anchor == this.anchor ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, anchor, this.signingContext, this.chatMessageChainer, this.returnValueConsumer);
    }

    public CommandSourceStack withLevel(ServerLevel world) {
        if (world == this.level) {
            return this;
        } else {
            double d0 = DimensionType.getTeleportationScale(this.level.dimensionType(), world.dimensionType());
            Vec3 vec3d = new Vec3(this.worldPosition.x * d0, this.worldPosition.y, this.worldPosition.z * d0);

            return new CommandSourceStack(this.source, vec3d, this.rotation, world, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor, this.signingContext, this.chatMessageChainer, this.returnValueConsumer);
        }
    }

    public CommandSourceStack facing(Entity entity, EntityAnchorArgument.Anchor anchor) {
        return this.facing(anchor.apply(entity));
    }

    public CommandSourceStack facing(Vec3 position) {
        Vec3 vec3d1 = this.anchor.apply(this);
        double d0 = position.x - vec3d1.x;
        double d1 = position.y - vec3d1.y;
        double d2 = position.z - vec3d1.z;
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        float f = Mth.wrapDegrees((float) (-(Mth.atan2(d1, d3) * 57.2957763671875D)));
        float f1 = Mth.wrapDegrees((float) (Mth.atan2(d2, d0) * 57.2957763671875D) - 90.0F);

        return this.withRotation(new Vec2(f, f1));
    }

    public CommandSourceStack withSigningContext(CommandSigningContext signedArguments, TaskChainer messageChainTaskQueue) {
        return signedArguments == this.signingContext && messageChainTaskQueue == this.chatMessageChainer ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor, signedArguments, messageChainTaskQueue, this.returnValueConsumer);
    }

    public CommandSourceStack withReturnValueConsumer(IntConsumer returnValueConsumer) {
        return returnValueConsumer == this.returnValueConsumer ? this : new CommandSourceStack(this.source, this.worldPosition, this.rotation, this.level, this.permissionLevel, this.textName, this.displayName, this.server, this.entity, this.silent, this.consumer, this.anchor, this.signingContext, this.chatMessageChainer, returnValueConsumer);
    }

    public Component getDisplayName() {
        return this.displayName;
    }

    public String getTextName() {
        return this.textName;
    }

    // Paper start
    @Override
    public org.bukkit.entity.Entity getBukkitEntity() {
        return getEntity() != null ? getEntity().getBukkitEntity() : null;
    }

    @Override
    public org.bukkit.World getBukkitWorld() {
        return getLevel() != null ? getLevel().getWorld() : null;
    }

    @Override
    public org.bukkit.Location getBukkitLocation() {
        Vec3 pos = getPosition();
        org.bukkit.World world = getBukkitWorld();
        Vec2 rot = getRotation();
        return world != null && pos != null ? new org.bukkit.Location(world, pos.x, pos.y, pos.z, rot != null ? rot.y : 0, rot != null ? rot.x : 0) : null;
    }
    // Paper end

    @Override
    public boolean hasPermission(int level) {
        // CraftBukkit start
        // Paper start - fix concurrency issue
        CommandNode currentCommand = this.currentCommand.get(Thread.currentThread());
        if (currentCommand != null) {
            return this.hasPermission(level, org.bukkit.craftbukkit.command.VanillaCommandWrapper.getPermission(currentCommand));
            // Paper end
        }
        // CraftBukkit end

        return this.permissionLevel >= level;
    }

    // CraftBukkit start
    public boolean hasPermission(int i, String bukkitPermission) {
        // Paper start
        boolean hasPermissionLevel = this.permissionLevel >= i;
        if (this.source == CommandSource.NULL) {
            return hasPermissionLevel;
        } else {
            return (!this.getLevel().getCraftServer().ignoreVanillaPermissions && hasPermissionLevel) || this.getBukkitSender().hasPermission(bukkitPermission);
        }
        // Paper end
    }
    // CraftBukkit end

    public Vec3 getPosition() {
        return this.worldPosition;
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    @Nullable
    public Entity getEntity() {
        return this.entity;
    }

    public Entity getEntityOrException() throws CommandSyntaxException {
        if (this.entity == null) {
            throw CommandSourceStack.ERROR_NOT_ENTITY.create();
        } else {
            return this.entity;
        }
    }

    public ServerPlayer getPlayerOrException() throws CommandSyntaxException {
        Entity entity = this.entity;

        if (entity instanceof ServerPlayer) {
            ServerPlayer entityplayer = (ServerPlayer) entity;

            return entityplayer;
        } else {
            throw CommandSourceStack.ERROR_NOT_PLAYER.create();
        }
    }

    @Nullable
    public ServerPlayer getPlayer() {
        Entity entity = this.entity;
        ServerPlayer entityplayer;

        if (entity instanceof ServerPlayer) {
            ServerPlayer entityplayer1 = (ServerPlayer) entity;

            entityplayer = entityplayer1;
        } else {
            entityplayer = null;
        }

        return entityplayer;
    }

    public boolean isPlayer() {
        return this.entity instanceof ServerPlayer;
    }

    public Vec2 getRotation() {
        return this.rotation;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public EntityAnchorArgument.Anchor getAnchor() {
        return this.anchor;
    }

    public CommandSigningContext getSigningContext() {
        return this.signingContext;
    }

    public TaskChainer getChatMessageChainer() {
        return this.chatMessageChainer;
    }

    public IntConsumer getReturnValueConsumer() {
        return this.returnValueConsumer;
    }

    public boolean shouldFilterMessageTo(ServerPlayer recipient) {
        ServerPlayer entityplayer1 = this.getPlayer();

        return recipient == entityplayer1 ? false : entityplayer1 != null && entityplayer1.isTextFilteringEnabled() || recipient.isTextFilteringEnabled();
    }

    public void sendChatMessage(OutgoingChatMessage message, boolean filterMaskEnabled, ChatType.Bound params) {
        if (!this.silent) {
            ServerPlayer entityplayer = this.getPlayer();

            if (entityplayer != null) {
                entityplayer.sendChatMessage(message, filterMaskEnabled, params);
            } else {
                this.source.sendSystemMessage(params.decorate(message.content()));
            }

        }
    }

    public void sendSystemMessage(Component message) {
        if (!this.silent) {
            ServerPlayer entityplayer = this.getPlayer();

            if (entityplayer != null) {
                entityplayer.sendSystemMessage(message);
            } else {
                this.source.sendSystemMessage(message);
            }

        }
    }

    public void sendSuccess(Supplier<Component> feedbackSupplier, boolean broadcastToOps) {
        boolean flag1 = this.source.acceptsSuccess() && !this.silent;
        boolean flag2 = broadcastToOps && this.source.shouldInformAdmins() && !this.silent;

        if (flag1 || flag2) {
            Component ichatbasecomponent = (Component) feedbackSupplier.get();

            if (flag1) {
                this.source.sendSystemMessage(ichatbasecomponent);
            }

            if (flag2) {
                this.broadcastToAdmins(ichatbasecomponent);
            }

        }
    }

    private void broadcastToAdmins(Component message) {
        MutableComponent ichatmutablecomponent = Component.translatable("chat.type.admin", this.getDisplayName(), message).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);

        if (this.server.getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK)) {
            Iterator iterator = this.server.getPlayerList().getPlayers().iterator();

            while (iterator.hasNext()) {
                ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                if (entityplayer != this.source && entityplayer.getBukkitEntity().hasPermission("minecraft.admin.command_feedback")) { // CraftBukkit
                    entityplayer.sendSystemMessage(ichatmutablecomponent);
                }
            }
        }

        if (this.source != this.server && this.server.getGameRules().getBoolean(GameRules.RULE_LOGADMINCOMMANDS) && !org.spigotmc.SpigotConfig.silentCommandBlocks) { // Spigot
            this.server.sendSystemMessage(ichatmutablecomponent);
        }

    }

    public void sendFailure(Component message) {
        // Paper start
        this.sendFailure(message, true);
    }
    public void sendFailure(Component message, boolean withStyle) {
        // Paper end
        if (this.source.acceptsFailure() && !this.silent) {
            this.source.sendSystemMessage(withStyle ? Component.empty().append(message).withStyle(ChatFormatting.RED) : message); // Paper
        }

    }

    public void onCommandComplete(CommandContext<CommandSourceStack> context, boolean success, int result) {
        if (this.consumer != null) {
            this.consumer.onCommandComplete(context, success, result);
        }

    }

    @Override
    public Collection<String> getOnlinePlayerNames() {
        return Lists.newArrayList(this.server.getPlayerNames());
    }

    @Override
    public Collection<String> getAllTeams() {
        return this.server.getScoreboard().getTeamNames();
    }

    @Override
    public Stream<ResourceLocation> getAvailableSounds() {
        return BuiltInRegistries.SOUND_EVENT.stream().map(SoundEvent::getLocation);
    }

    @Override
    public Stream<ResourceLocation> getRecipeNames() {
        return this.server.getRecipeManager().getRecipeIds();
    }

    @Override
    public CompletableFuture<Suggestions> customSuggestion(CommandContext<?> context) {
        return Suggestions.empty();
    }

    @Override
    public CompletableFuture<Suggestions> suggestRegistryElements(ResourceKey<? extends Registry<?>> registryRef, SharedSuggestionProvider.ElementSuggestionType suggestedIdType, SuggestionsBuilder builder, CommandContext<?> context) {
        return (CompletableFuture) this.registryAccess().registry(registryRef).map((iregistry) -> {
            this.suggestRegistryElements(iregistry, suggestedIdType, builder);
            return builder.buildFuture();
        }).orElseGet(Suggestions::empty);
    }

    @Override
    public Set<ResourceKey<Level>> levels() {
        return this.server.levelKeys();
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.server.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.level.enabledFeatures();
    }

    // CraftBukkit start
    public org.bukkit.command.CommandSender getBukkitSender() {
        return this.source.getBukkitSender(this);
    }
    // CraftBukkit end
    // Paper start - override getSelectedEntities
    @Override
    public Collection<String> getSelectedEntities() {
        if (io.papermc.paper.configuration.GlobalConfiguration.get().commands.fixTargetSelectorTagCompletion && this.source instanceof ServerPlayer player) {
            final Entity cameraEntity = player.getCamera();
            final double pickDistance = player.gameMode.getGameModeForPlayer().isCreative() ? 6.0F : 4.5F;
            final Vec3 min = cameraEntity.getEyePosition(1.0F);
            final Vec3 viewVector = cameraEntity.getViewVector(1.0F);
            final Vec3 max = min.add(viewVector.x * pickDistance, viewVector.y * pickDistance, viewVector.z * pickDistance);
            final net.minecraft.world.phys.AABB aabb = cameraEntity.getBoundingBox().expandTowards(viewVector.scale(pickDistance)).inflate(1.0D, 1.0D, 1.0D);
            final net.minecraft.world.phys.EntityHitResult hitResult = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(cameraEntity, min, max, aabb, (e) -> !e.isSpectator() && e.isPickable(), pickDistance * pickDistance);
            return hitResult != null ? java.util.Collections.singletonList(hitResult.getEntity().getStringUUID()) : SharedSuggestionProvider.super.getSelectedEntities();
        }
        return SharedSuggestionProvider.super.getSelectedEntities();
    }
    // Paper end
}
