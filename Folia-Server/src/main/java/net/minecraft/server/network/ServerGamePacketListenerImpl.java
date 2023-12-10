package net.minecraft.server.network;

import com.google.common.collect.Lists;
import com.google.common.primitives.Floats;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.Connection;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.LastSeenMessagesValidator;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MessageSignatureCache;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.chat.SignableCommand;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.SignedMessageChain;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQuery;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundChatAckPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundEntityTagQuery;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket;
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.status.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.status.ServerboundPingRequestPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.util.FutureChain;
import net.minecraft.util.Mth;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

// CraftBukkit start
import io.papermc.paper.adventure.ChatProcessor; // Paper
import io.papermc.paper.adventure.PaperAdventure; // Paper
import com.mojang.datafixers.util.Pair;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.craftbukkit.util.LazyPlayerSet;
import org.bukkit.craftbukkit.util.Waitable;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent; // Paper
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent.RespawnReason;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.SmithingInventory;
// CraftBukkit end

public class ServerGamePacketListenerImpl extends ServerCommonPacketListenerImpl implements ServerGamePacketListener, ServerPlayerConnection, TickablePacketListener {

    static final Logger LOGGER = LogUtils.getLogger();
    public static final double MAX_INTERACTION_DISTANCE = Mth.square(6.0D);
    private static final int NO_BLOCK_UPDATES_TO_ACK = -1;
    private static final int TRACKED_MESSAGE_DISCONNECT_THRESHOLD = 4096;
    private static final Component CHAT_VALIDATION_FAILED = Component.translatable("multiplayer.disconnect.chat_validation_failed");
    public ServerPlayer player;
    public final PlayerChunkSender chunkSender;
    private int tickCount;
    private int ackBlockChangesUpTo = -1;
    // CraftBukkit start - multithreaded fields
    private final AtomicInteger chatSpamTickCount = new AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger tabSpamLimiter = new java.util.concurrent.atomic.AtomicInteger(); // Paper - configurable tab spam limits
    private final java.util.concurrent.atomic.AtomicInteger recipeSpamPackets =  new java.util.concurrent.atomic.AtomicInteger(); // Paper - auto recipe limit
    // CraftBukkit end
    private int dropSpamTickCount;
    private double firstGoodX;
    private double firstGoodY;
    private double firstGoodZ;
    private double lastGoodX;
    private double lastGoodY;
    private double lastGoodZ;
    @Nullable
    private Entity lastVehicle;
    private double vehicleFirstGoodX;
    private double vehicleFirstGoodY;
    private double vehicleFirstGoodZ;
    private double vehicleLastGoodX;
    private double vehicleLastGoodY;
    private double vehicleLastGoodZ;
    @Nullable
    private Vec3 awaitingPositionFromClient;
    private int awaitingTeleport;
    private int awaitingTeleportTime;
    private boolean clientIsFloating;
    private int aboveGroundTickCount;
    private boolean clientVehicleIsFloating;
    private int aboveGroundVehicleTickCount;
    private int receivedMovePacketCount;
    private int knownMovePacketCount;
    private final AtomicReference<Instant> lastChatTimeStamp;
    @Nullable
    private RemoteChatSession chatSession;
    private boolean hasLoggedExpiry = false; // Paper
    private SignedMessageChain.Decoder signedMessageDecoder;
    private final LastSeenMessagesValidator lastSeenMessages;
    private final MessageSignatureCache messageSignatureCache;
    private final FutureChain chatMessageChain;
    public volatile boolean waitingForSwitchToConfig; // Folia - rewrite login process - fix bad ordering of this field write + public
    private static final int MAX_SIGN_LINE_LENGTH = Integer.getInteger("Paper.maxSignLength", 80); // Paper

    public ServerGamePacketListenerImpl(MinecraftServer server, Connection connection, ServerPlayer player, CommonListenerCookie clientData) {
        super(server, connection, clientData, player); // CraftBukkit
        this.lastChatTimeStamp = new AtomicReference(Instant.EPOCH);
        this.lastSeenMessages = new LastSeenMessagesValidator(20);
        this.messageSignatureCache = MessageSignatureCache.createDefault();
        this.chunkSender = new PlayerChunkSender(connection.isMemoryConnection());
        connection.setListener(this);
        this.player = player;
        player.connection = this;
        player.getTextFilter().join();
        this.signedMessageDecoder = server.enforceSecureProfile() ? SignedMessageChain.Decoder.REJECT_ALL : SignedMessageChain.Decoder.unsigned(player.getUUID());
        this.chatMessageChain = new FutureChain(server.chatExecutor); // CraftBukkit - async chat
    }

    // CraftBukkit start - add fields
    private long lastTick = Util.getMillis() / 50L; // Folia - region threading
    private int allowedPlayerTicks = 1;
    private long lastDropTick = Util.getMillis() / 50L; // Folia - region threading
    private long lastBookTick  = Util.getMillis() / 50L; // Folia - region threading
    private int dropCount = 0;

    // Get position of last block hit for BlockDamageLevel.STOPPED
    private double lastPosX = Double.MAX_VALUE;
    private double lastPosY = Double.MAX_VALUE;
    private double lastPosZ = Double.MAX_VALUE;
    private float lastPitch = Float.MAX_VALUE;
    private float lastYaw = Float.MAX_VALUE;
    private boolean justTeleported = false;
    private boolean hasMoved; // Spigot
    // CraftBukkit end

    // Folia start - region threading
    public net.minecraft.world.level.ChunkPos disconnectPos;
    private static final java.util.concurrent.atomic.AtomicLong DISCONNECT_TICKET_ID_GENERATOR = new java.util.concurrent.atomic.AtomicLong();
    public static final net.minecraft.server.level.TicketType<Long> DISCONNECT_TICKET = net.minecraft.server.level.TicketType.create("disconnect_ticket", Long::compareTo);
    public final Long disconnectTicketId = Long.valueOf(DISCONNECT_TICKET_ID_GENERATOR.getAndIncrement());
    // Folia end - region threading

    @Override
    public void tick() {
        // Folia start - region threading
        this.keepConnectionAlive();
        if (this.player.wonGame) {
            return;
        }
        // Folia end - region threading
        if (this.ackBlockChangesUpTo > -1) {
            this.send(new ClientboundBlockChangedAckPacket(this.ackBlockChangesUpTo));
            this.ackBlockChangesUpTo = -1;
        }

        this.resetPosition();
        this.player.xo = this.player.getX();
        this.player.yo = this.player.getY();
        this.player.zo = this.player.getZ();
        this.player.doTick();
        this.player.absMoveTo(this.firstGoodX, this.firstGoodY, this.firstGoodZ, this.player.getYRot(), this.player.getXRot());
        ++this.tickCount;
        this.knownMovePacketCount = this.receivedMovePacketCount;
        if (this.clientIsFloating && !this.player.isSleeping() && !this.player.isPassenger() && !this.player.isDeadOrDying()) {
            if (++this.aboveGroundTickCount > 80) {
                ServerGamePacketListenerImpl.LOGGER.warn("{} was kicked for floating too long!", this.player.getName().getString());
                this.disconnect(io.papermc.paper.configuration.GlobalConfiguration.get().messages.kick.flyingPlayer, org.bukkit.event.player.PlayerKickEvent.Cause.FLYING_PLAYER); // Paper - use configurable kick message & kick event cause
                return;
            }
        } else {
            this.clientIsFloating = false;
            this.aboveGroundTickCount = 0;
        }

        this.lastVehicle = this.player.getRootVehicle();
        if (this.lastVehicle != this.player && this.lastVehicle.getControllingPassenger() == this.player) {
            this.vehicleFirstGoodX = this.lastVehicle.getX();
            this.vehicleFirstGoodY = this.lastVehicle.getY();
            this.vehicleFirstGoodZ = this.lastVehicle.getZ();
            this.vehicleLastGoodX = this.lastVehicle.getX();
            this.vehicleLastGoodY = this.lastVehicle.getY();
            this.vehicleLastGoodZ = this.lastVehicle.getZ();
            if (this.clientVehicleIsFloating && this.player.getRootVehicle().getControllingPassenger() == this.player) {
                if (++this.aboveGroundVehicleTickCount > 80) {
                    ServerGamePacketListenerImpl.LOGGER.warn("{} was kicked for floating a vehicle too long!", this.player.getName().getString());
                    this.disconnect(io.papermc.paper.configuration.GlobalConfiguration.get().messages.kick.flyingVehicle, org.bukkit.event.player.PlayerKickEvent.Cause.FLYING_VEHICLE); // Paper - use configurable kick message & kick event cause
                    return;
                }
            } else {
                this.clientVehicleIsFloating = false;
                this.aboveGroundVehicleTickCount = 0;
            }
        } else {
            this.lastVehicle = null;
            this.clientVehicleIsFloating = false;
            this.aboveGroundVehicleTickCount = 0;
        }

        // Folia - region threading - moved to beginning of method
        // CraftBukkit start
        for (int spam; (spam = this.chatSpamTickCount.get()) > 0 && !this.chatSpamTickCount.compareAndSet(spam, spam - 1); ) ;
        if (tabSpamLimiter.get() > 0) tabSpamLimiter.getAndDecrement(); // Paper - split to seperate variable
        if (recipeSpamPackets.get() > 0) recipeSpamPackets.getAndDecrement(); // Paper
        /* Use thread-safe field access instead
        if (this.chatSpamTickCount > 0) {
            --this.chatSpamTickCount;
        }
        */
        // CraftBukkit end

        if (this.dropSpamTickCount > 0) {
            --this.dropSpamTickCount;
        }

        if (this.player.getLastActionTime() > 0L && this.server.getPlayerIdleTimeout() > 0 && Util.getMillis() - this.player.getLastActionTime() > (long) this.server.getPlayerIdleTimeout() * 1000L * 60L && !this.player.wonGame) { // Paper - Prevent AFK kick while watching end credits.
            this.player.resetLastActionTime(); // CraftBukkit - SPIGOT-854
            this.disconnect(Component.translatable("multiplayer.disconnect.idling"), org.bukkit.event.player.PlayerKickEvent.Cause.IDLING); // Paper - kick event cause
        }

        // Paper start
        if (!hasLoggedExpiry && this.chatSession != null && this.chatSession.profilePublicKey().data().hasExpired()) {
            LOGGER.info("Player profile key for {} has expired!", this.player.getName().getString());
            hasLoggedExpiry = true;
        }
        // Paper end

    }

    public void resetPosition() {
        this.firstGoodX = this.player.getX();
        this.firstGoodY = this.player.getY();
        this.firstGoodZ = this.player.getZ();
        this.lastGoodX = this.player.getX();
        this.lastGoodY = this.player.getY();
        this.lastGoodZ = this.player.getZ();
        // Folia start - support vehicle teleportations
        this.lastVehicle = this.player.getRootVehicle();
        if (this.lastVehicle != this.player && this.lastVehicle.getControllingPassenger() == this.player) {
            this.vehicleFirstGoodX = this.lastVehicle.getX();
            this.vehicleFirstGoodY = this.lastVehicle.getY();
            this.vehicleFirstGoodZ = this.lastVehicle.getZ();
            this.vehicleLastGoodX = this.lastVehicle.getX();
            this.vehicleLastGoodY = this.lastVehicle.getY();
            this.vehicleLastGoodZ = this.lastVehicle.getZ();
        } else {
            this.lastVehicle = null;
        }
        // Folia end - support vehicle teleportations
    }

    @Override
    public boolean isAcceptingMessages() {
        return this.connection.isConnected() && !this.waitingForSwitchToConfig;
    }

    @Override
    public boolean shouldHandleMessage(Packet<?> packet) {
        return super.shouldHandleMessage(packet) ? true : this.waitingForSwitchToConfig && this.connection.isConnected() && packet instanceof ServerboundConfigurationAcknowledgedPacket;
    }

    @Override
    protected GameProfile playerProfile() {
        return this.player.getGameProfile();
    }

    private <T, R> CompletableFuture<R> filterTextPacket(T text, BiFunction<TextFilter, T, CompletableFuture<R>> filterer) {
        return ((CompletableFuture) filterer.apply(this.player.getTextFilter(), text)).thenApply((object) -> {
            if (!this.isAcceptingMessages()) {
                ServerGamePacketListenerImpl.LOGGER.debug("Ignoring packet due to disconnection");
                throw new CancellationException("disconnected");
            } else {
                return object;
            }
        });
    }

    private CompletableFuture<FilteredText> filterTextPacket(String text) {
        return this.filterTextPacket(text, TextFilter::processStreamMessage);
    }

    private CompletableFuture<List<FilteredText>> filterTextPacket(List<String> texts) {
        return this.filterTextPacket(texts, TextFilter::processMessageBundle);
    }

    @Override
    public void handlePlayerInput(ServerboundPlayerInputPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        this.player.setPlayerInput(packet.getXxa(), packet.getZza(), packet.isJumping(), packet.isShiftKeyDown());
    }

    private static boolean containsInvalidValues(double x, double y, double z, float yaw, float pitch) {
        return Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) || !Floats.isFinite(pitch) || !Floats.isFinite(yaw);
    }

    private static double clampHorizontal(double d) {
        return Mth.clamp(d, -3.0E7D, 3.0E7D);
    }

    private static double clampVertical(double d) {
        return Mth.clamp(d, -2.0E7D, 2.0E7D);
    }

    @Override
    public void handleMoveVehicle(ServerboundMoveVehiclePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (ServerGamePacketListenerImpl.containsInvalidValues(packet.getX(), packet.getY(), packet.getZ(), packet.getYRot(), packet.getXRot())) {
            this.disconnect(Component.translatable("multiplayer.disconnect.invalid_vehicle_movement"), org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_VEHICLE_MOVEMENT); // Paper - kick event cause
        } else {
            Entity entity = this.player.getRootVehicle();
            // Paper start
            if (this.awaitingPositionFromClient != null || this.player.isImmobile() || entity.isRemoved()) {
                return;
            }
            // Paper end

            if (entity != this.player && entity.getControllingPassenger() == this.player && entity == this.lastVehicle) {
                ServerLevel worldserver = this.player.serverLevel();
                double d0 = entity.getX();final double fromX = d0; // Paper - OBFHELPER
                double d1 = entity.getY();final double fromY = d1; // Paper - OBFHELPER
                double d2 = entity.getZ();final double fromZ = d2; // Paper - OBFHELPER
                double d3 = ServerGamePacketListenerImpl.clampHorizontal(packet.getX()); final double toX = d3; // Paper - OBFHELPER
                double d4 = ServerGamePacketListenerImpl.clampVertical(packet.getY()); final double toY = d4; // Paper - OBFHELPER
                double d5 = ServerGamePacketListenerImpl.clampHorizontal(packet.getZ()); final double toZ = d5; // Paper - OBFHELPER
                float f = Mth.wrapDegrees(packet.getYRot());
                float f1 = Mth.wrapDegrees(packet.getXRot());
                double d6 = d3 - this.vehicleFirstGoodX;
                double d7 = d4 - this.vehicleFirstGoodY;
                double d8 = d5 - this.vehicleFirstGoodZ;
                double d9 = entity.getDeltaMovement().lengthSqr();
                // Paper start - fix large move vectors killing the server
                double currDeltaX = toX - fromX;
                double currDeltaY = toY - fromY;
                double currDeltaZ = toZ - fromZ;
                double d10 = Math.max(d6 * d6 + d7 * d7 + d8 * d8, (currDeltaX * currDeltaX + currDeltaY * currDeltaY + currDeltaZ * currDeltaZ) - 1);
                // Paper end - fix large move vectors killing the server

                // Paper start - fix large move vectors killing the server
                double otherFieldX = d3 - this.vehicleLastGoodX;
                double otherFieldY = d4 - this.vehicleLastGoodY - 1.0E-6D;
                double otherFieldZ = d5 - this.vehicleLastGoodZ;
                d10 = Math.max(d10, (otherFieldX * otherFieldX + otherFieldY * otherFieldY + otherFieldZ * otherFieldZ) - 1);
                // Paper end - fix large move vectors killing the server

                // CraftBukkit start - handle custom speeds and skipped ticks
                int currTick = (int)(Util.getMillis() / 50); // Folia - region threading
                this.allowedPlayerTicks += currTick - this.lastTick; // Folia - region threading
                this.allowedPlayerTicks = Math.max(this.allowedPlayerTicks, 1);
                this.lastTick = (int) currTick; // Folia - region threading

                ++this.receivedMovePacketCount;
                int i = this.receivedMovePacketCount - this.knownMovePacketCount;
                if (i > Math.max(this.allowedPlayerTicks, 5)) {
                    ServerGamePacketListenerImpl.LOGGER.debug(this.player.getScoreboardName() + " is sending move packets too frequently (" + i + " packets since last tick)");
                    i = 1;
                }

                if (d10 > 0) {
                    this.allowedPlayerTicks -= 1;
                } else {
                    this.allowedPlayerTicks = 20;
                }
                double speed;
                if (this.player.getAbilities().flying) {
                    speed = this.player.getAbilities().flyingSpeed * 20f;
                } else {
                    speed = this.player.getAbilities().walkingSpeed * 10f;
                }
                speed *= 2f; // TODO: Get the speed of the vehicle instead of the player

                // Paper start - Prevent moving into unloaded chunks
                if (this.player.level().paperConfig().chunks.preventMovingIntoUnloadedChunks && (
                    !worldserver.areChunksLoadedForMove(this.player.getBoundingBox().expandTowards(new Vec3(toX, toY, toZ).subtract(this.player.position()))) ||
                        !worldserver.areChunksLoadedForMove(entity.getBoundingBox().expandTowards(new Vec3(toX, toY, toZ).subtract(entity.position())))
                    )) {
                    this.connection.send(new ClientboundMoveVehiclePacket(entity));
                    return;
                }
                // Paper end

                if (d10 - d9 > Math.max(100.0D, Math.pow((double) (org.spigotmc.SpigotConfig.movedTooQuicklyMultiplier * (float) i * speed), 2)) && !this.isSingleplayerOwner()) {
                // CraftBukkit end
                    ServerGamePacketListenerImpl.LOGGER.warn("{} (vehicle of {}) moved too quickly! {},{},{}", new Object[]{entity.getName().getString(), this.player.getName().getString(), d6, d7, d8});
                    this.send(new ClientboundMoveVehiclePacket(entity));
                    return;
                }

                AABB oldBox = entity.getBoundingBox(); // Paper - copy from player movement packet

                d6 = d3 - this.vehicleLastGoodX; // Paper - diff on change, used for checking large move vectors above
                d7 = d4 - this.vehicleLastGoodY - 1.0E-6D; // Paper - diff on change, used for checking large move vectors above
                d8 = d5 - this.vehicleLastGoodZ; // Paper - diff on change, used for checking large move vectors above
                boolean flag1 = entity.verticalCollisionBelow;

                if (entity instanceof LivingEntity) {
                    LivingEntity entityliving = (LivingEntity) entity;

                    if (entityliving.onClimbable()) {
                        entityliving.resetFallDistance();
                    }
                }

                entity.move(MoverType.PLAYER, new Vec3(d6, d7, d8));
                boolean didCollide = toX != entity.getX() || toY != entity.getY() || toZ != entity.getZ(); // Paper - needed here as the difference in Y can be reset - also note: this is only a guess at whether collisions took place, floating point errors can make this true when it shouldn't be...
                double d11 = d7;

                d6 = d3 - entity.getX();
                d7 = d4 - entity.getY();
                if (d7 > -0.5D || d7 < 0.5D) {
                    d7 = 0.0D;
                }

                d8 = d5 - entity.getZ();
                d10 = d6 * d6 + d7 * d7 + d8 * d8;
                boolean flag2 = false;

                if (d10 > org.spigotmc.SpigotConfig.movedWronglyThreshold) { // Spigot
                    flag2 = true; // Paper - diff on change, this should be moved wrongly
                    ServerGamePacketListenerImpl.LOGGER.warn("{} (vehicle of {}) moved wrongly! {}", new Object[]{entity.getName().getString(), this.player.getName().getString(), Math.sqrt(d10)});
                }
                Location curPos = this.getCraftPlayer().getLocation(); // Spigot

                entity.absMoveTo(d3, d4, d5, f, f1);
                //this.player.absMoveTo(d3, d4, d5, this.player.getYRot(), this.player.getXRot()); // CraftBukkit // Folia - move to repositionAllPassengers

                // Paper start - optimise out extra getCubes
                boolean teleportBack = flag2; // violating this is always a fail
                if (!teleportBack) {
                    // note: only call after setLocation, or else getBoundingBox is wrong
                    AABB newBox = entity.getBoundingBox();
                    if (didCollide || !oldBox.equals(newBox)) {
                        teleportBack = this.hasNewCollision(worldserver, entity, oldBox, newBox);
                    } // else: no collision at all detected, why do we care?
                }
                if (teleportBack) { // Paper end - optimise out extra getCubes
                    entity.absMoveTo(d0, d1, d2, f, f1);
                    //this.player.absMoveTo(d0, d1, d2, this.player.getYRot(), this.player.getXRot()); // CraftBukkit // Folia - not needed, the player is no longer updated
                    this.send(new ClientboundMoveVehiclePacket(entity));
                    return;
                }

                // Folia start - move to positionRider
                // this correction is required on folia since we move the connection tick to the beginning of the server
                // tick, which would make any desync here visible
                // this will correctly update the passenger positions for all mounted entities
                // this prevents desync and ensures that all passengers have the correct rider-adjusted position
                entity.repositionAllPassengers(false);
                // Folia end - move to positionRider

                // CraftBukkit start - fire PlayerMoveEvent
                Player player = this.getCraftPlayer();
                // Spigot Start
                if ( !this.hasMoved )
                {
                    this.lastPosX = curPos.getX();
                    this.lastPosY = curPos.getY();
                    this.lastPosZ = curPos.getZ();
                    this.lastYaw = curPos.getYaw();
                    this.lastPitch = curPos.getPitch();
                    this.hasMoved = true;
                }
                // Spigot End
                Location from = new Location(player.getWorld(), this.lastPosX, this.lastPosY, this.lastPosZ, this.lastYaw, this.lastPitch); // Get the Players previous Event location.
                Location to = player.getLocation().clone(); // Start off the To location as the Players current location.

                // If the packet contains movement information then we update the To location with the correct XYZ.
                to.setX(packet.getX());
                to.setY(packet.getY());
                to.setZ(packet.getZ());


                // If the packet contains look information then we update the To location with the correct Yaw & Pitch.
                to.setYaw(packet.getYRot());
                to.setPitch(packet.getXRot());

                // Prevent 40 event-calls for less than a single pixel of movement >.>
                double delta = Math.pow(this.lastPosX - to.getX(), 2) + Math.pow(this.lastPosY - to.getY(), 2) + Math.pow(this.lastPosZ - to.getZ(), 2);
                float deltaAngle = Math.abs(this.lastYaw - to.getYaw()) + Math.abs(this.lastPitch - to.getPitch());

                if ((delta > 1f / 256 || deltaAngle > 10f) && !this.player.isImmobile()) {
                    this.lastPosX = to.getX();
                    this.lastPosY = to.getY();
                    this.lastPosZ = to.getZ();
                    this.lastYaw = to.getYaw();
                    this.lastPitch = to.getPitch();

                    // Skip the first time we do this
                    if (true) { // Spigot - don't skip any move events
                        Location oldTo = to.clone();
                        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
                        this.cserver.getPluginManager().callEvent(event);

                        // If the event is cancelled we move the player back to their old location.
                        if (event.isCancelled()) {
                            this.player.getBukkitEntity().teleportAsync(from, PlayerTeleportEvent.TeleportCause.PLUGIN); // Folia - region threading
                            return;
                        }

                        // If a Plugin has changed the To destination then we teleport the Player
                        // there to avoid any 'Moved wrongly' or 'Moved too quickly' errors.
                        // We only do this if the Event was not cancelled.
                        if (!oldTo.equals(event.getTo()) && !event.isCancelled()) {
                            this.player.getBukkitEntity().teleportAsync(event.getTo(), PlayerTeleportEvent.TeleportCause.PLUGIN); // Folia - region threading
                            return;
                        }

                        // Check to see if the Players Location has some how changed during the call of the event.
                        // This can happen due to a plugin teleporting the player instead of using .setTo()
                        if (!from.equals(this.getCraftPlayer().getLocation()) && this.justTeleported) {
                            this.justTeleported = false;
                            return;
                        }
                    }
                }
                // CraftBukkit end

                this.player.serverLevel().getChunkSource().move(this.player);
                this.player.checkMovementStatistics(this.player.getX() - d0, this.player.getY() - d1, this.player.getZ() - d2);
                this.clientVehicleIsFloating = d11 >= -0.03125D && !flag1 && !this.server.isFlightAllowed() && !entity.isNoGravity() && this.noBlocksAround(entity);
                this.vehicleLastGoodX = entity.getX();
                this.vehicleLastGoodY = entity.getY();
                this.vehicleLastGoodZ = entity.getZ();
            }

        }
    }

    private boolean noBlocksAround(Entity entity) {
        // Paper start - stop using streams, this is already a known fixed problem in Entity#move
        AABB box = entity.getBoundingBox().inflate(0.0625D).expandTowards(0.0D, -0.55D, 0.0D);
        int minX = Mth.floor(box.minX);
        int minY = Mth.floor(box.minY);
        int minZ = Mth.floor(box.minZ);
        int maxX = Mth.floor(box.maxX);
        int maxY = Mth.floor(box.maxY);
        int maxZ = Mth.floor(box.maxZ);

        Level world = entity.level();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY; ++y) {
            for (int z = minZ; z <= maxZ; ++z) {
                for (int x = minX; x <= maxX; ++x) {
                    pos.set(x, y, z);
                    BlockState type = world.getBlockStateIfLoaded(pos);
                    if (type != null && !type.isAir()) {
                        return false;
                    }
                }
            }
        }

        return true;
        // Paper end - stop using streams, this is already a known fixed problem in Entity#move
    }

    @Override
    public void handleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (packet.getId() == this.awaitingTeleport) {
            if (this.awaitingPositionFromClient == null) {
                this.disconnect(Component.translatable("multiplayer.disconnect.invalid_player_movement"), org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_PLAYER_MOVEMENT); // Paper - kick event cause
                return;
            }

            this.player.moveTo(this.awaitingPositionFromClient.x, this.awaitingPositionFromClient.y, this.awaitingPositionFromClient.z, this.player.getYRot(), this.player.getXRot()); // Paper - use proper moveTo for teleportation
            this.lastGoodX = this.awaitingPositionFromClient.x;
            this.lastGoodY = this.awaitingPositionFromClient.y;
            this.lastGoodZ = this.awaitingPositionFromClient.z;
            if (this.player.isChangingDimension()) {
                this.player.hasChangedDimension();
            }

            this.awaitingPositionFromClient = null;
            this.player.serverLevel().getChunkSource().move(this.player); // CraftBukkit
        }

    }

    @Override
    public void handleRecipeBookSeenRecipePacket(ServerboundRecipeBookSeenRecipePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        Optional<? extends RecipeHolder<?>> optional = this.server.getRecipeManager().byKey(packet.getRecipe()); // CraftBukkit - decompile error
        ServerRecipeBook recipebookserver = this.player.getRecipeBook();

        Objects.requireNonNull(recipebookserver);
        optional.ifPresent(recipebookserver::removeHighlight);
    }

    @Override
    public void handleRecipeBookChangeSettingsPacket(ServerboundRecipeBookChangeSettingsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        CraftEventFactory.callRecipeBookSettingsEvent(this.player, packet.getBookType(), packet.isOpen(), packet.isFiltering()); // CraftBukkit
        this.player.getRecipeBook().setBookSetting(packet.getBookType(), packet.isOpen(), packet.isFiltering());
    }

    @Override
    public void handleSeenAdvancements(ServerboundSeenAdvancementsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (packet.getAction() == ServerboundSeenAdvancementsPacket.Action.OPENED_TAB) {
            ResourceLocation minecraftkey = (ResourceLocation) Objects.requireNonNull(packet.getTab());
            AdvancementHolder advancementholder = this.server.getAdvancements().get(minecraftkey);

            if (advancementholder != null) {
                this.player.getAdvancements().setSelectedTab(advancementholder);
            }
        }

    }

    // Paper start
    private static final java.util.concurrent.ExecutorService TAB_COMPLETE_EXECUTOR = java.util.concurrent.Executors.newFixedThreadPool(4,
        new com.google.common.util.concurrent.ThreadFactoryBuilder().setDaemon(true).setNameFormat("Async Tab Complete Thread - #%d").setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(net.minecraft.server.MinecraftServer.LOGGER)).build());
    // Paper end
    @Override
    public void handleCustomCommandSuggestions(ServerboundCommandSuggestionPacket packet) {
        // PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel()); // Paper - run this async
        // CraftBukkit start
        if (this.chatSpamTickCount.addAndGet(io.papermc.paper.configuration.GlobalConfiguration.get().spamLimiter.tabSpamIncrement) > io.papermc.paper.configuration.GlobalConfiguration.get().spamLimiter.tabSpamLimit && !this.server.getPlayerList().isOp(this.player.getGameProfile())) { // Paper start - split and make configurable
            this.disconnect(Component.translatable("disconnect.spam", new Object[0]), org.bukkit.event.player.PlayerKickEvent.Cause.SPAM); // Paper - kick event cause // Folia - region threading
            return;
        }
        // Paper start
        String str = packet.getCommand(); int index = -1;
        if (str.length() > 64 && ((index = str.indexOf(' ')) == -1 || index >= 64)) {
            this.disconnect(Component.translatable("disconnect.spam", new Object[0])); // Paper // Folia - region threading
            return;
        }
        // Paper end
        // CraftBukkit end
        // Paper start - Don't suggest if tab-complete is disabled
        if (org.spigotmc.SpigotConfig.tabComplete < 0) {
            return;
        }
        // Paper end - Don't suggest if tab-complete is disabled
        // Paper start - async tab completion
        TAB_COMPLETE_EXECUTOR.execute(() -> {
        StringReader stringreader = new StringReader(packet.getCommand());

        if (stringreader.canRead() && stringreader.peek() == '/') {
            stringreader.skip();
        }
            final String command = packet.getCommand();
            final com.destroystokyo.paper.event.server.AsyncTabCompleteEvent event = new com.destroystokyo.paper.event.server.AsyncTabCompleteEvent(this.getCraftPlayer(), command, true, null);
            event.callEvent();
            final java.util.List<com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion> completions = event.isCancelled() ? com.google.common.collect.ImmutableList.of() : event.completions();
            // If the event isn't handled, we can assume that we have no completions, and so we'll ask the server
            if (!event.isHandled()) {
                if (!event.isCancelled()) {

                    this.player.getBukkitEntity().taskScheduler.schedule((ServerPlayer player) -> { // Folia - region threading
                        ParseResults<CommandSourceStack> parseresults = this.server.getCommands().getDispatcher().parse(stringreader, this.player.createCommandSourceStack());

                        this.server.getCommands().getDispatcher().getCompletionSuggestions(parseresults).thenAccept((suggestions) -> {
                            // Paper start - Don't tab-complete namespaced commands if send-namespaced is false
                            if (!org.spigotmc.SpigotConfig.sendNamespaced && suggestions.getRange().getStart() <= 1) {
                                suggestions.getList().removeIf(suggestion -> suggestion.getText().contains(":"));
                            }
                            // Paper end
                            // Paper start - Brigadier API
                            com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent suggestEvent = new com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent(this.getCraftPlayer(), suggestions, command);
                            suggestEvent.setCancelled(suggestions.isEmpty());
                            if (!suggestEvent.callEvent()) return;
                            this.connection.send(new ClientboundCommandSuggestionsPacket(packet.getId(), suggestEvent.getSuggestions()));
                            // Paper end - Brigadier API
                        });
                    }, null, 1L); // Folia - region threading
                }
            } else if (!completions.isEmpty()) {
                final com.mojang.brigadier.suggestion.SuggestionsBuilder builder0 = new com.mojang.brigadier.suggestion.SuggestionsBuilder(command, stringreader.getTotalLength());
                final com.mojang.brigadier.suggestion.SuggestionsBuilder builder = builder0.createOffset(builder0.getInput().lastIndexOf(' ') + 1);
                completions.forEach(completion -> {
                    final Integer intSuggestion = com.google.common.primitives.Ints.tryParse(completion.suggestion());
                    if (intSuggestion != null) {
                        builder.suggest(intSuggestion, PaperAdventure.asVanilla(completion.tooltip()));
                    } else {
                        builder.suggest(completion.suggestion(), PaperAdventure.asVanilla(completion.tooltip()));
                    }
                });
                // Paper start - Brigadier API
                com.mojang.brigadier.suggestion.Suggestions suggestions = builder.buildFuture().join();
                com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent suggestEvent = new com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent(this.getCraftPlayer(), suggestions, command);
                suggestEvent.setCancelled(suggestions.isEmpty());
                if (!suggestEvent.callEvent()) return;
                this.connection.send(new ClientboundCommandSuggestionsPacket(packet.getId(), suggestEvent.getSuggestions()));
                // Paper end - Brigadier API
            }
        });
        // Paper end - async tab completion
    }

    @Override
    public void handleSetCommandBlock(ServerboundSetCommandBlockPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (!this.server.isCommandBlockEnabled()) {
            this.player.sendSystemMessage(Component.translatable("advMode.notEnabled"));
        } else if (!this.player.canUseGameMasterBlocks() && (!this.player.isCreative() || !this.player.getBukkitEntity().hasPermission("minecraft.commandblock"))) { // Paper - command block permission
            this.player.sendSystemMessage(Component.translatable("advMode.notAllowed"));
        } else {
            BaseCommandBlock commandblocklistenerabstract = null;
            CommandBlockEntity tileentitycommand = null;
            BlockPos blockposition = packet.getPos();
            BlockEntity tileentity = this.player.level().getBlockEntity(blockposition);

            if (tileentity instanceof CommandBlockEntity) {
                tileentitycommand = (CommandBlockEntity) tileentity;
                commandblocklistenerabstract = tileentitycommand.getCommandBlock();
            }

            String s = packet.getCommand();
            boolean flag = packet.isTrackOutput();

            if (commandblocklistenerabstract != null) {
                CommandBlockEntity.Mode tileentitycommand_type = tileentitycommand.getMode();
                BlockState iblockdata = this.player.level().getBlockState(blockposition);
                Direction enumdirection = (Direction) iblockdata.getValue(CommandBlock.FACING);
                BlockState iblockdata1;

                switch (packet.getMode()) {
                    case SEQUENCE:
                        iblockdata1 = Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState();
                        break;
                    case AUTO:
                        iblockdata1 = Blocks.REPEATING_COMMAND_BLOCK.defaultBlockState();
                        break;
                    case REDSTONE:
                    default:
                        iblockdata1 = Blocks.COMMAND_BLOCK.defaultBlockState();
                }

                BlockState iblockdata2 = (BlockState) ((BlockState) iblockdata1.setValue(CommandBlock.FACING, enumdirection)).setValue(CommandBlock.CONDITIONAL, packet.isConditional());

                if (iblockdata2 != iblockdata) {
                    this.player.level().setBlock(blockposition, iblockdata2, 2);
                    tileentity.setBlockState(iblockdata2);
                    this.player.level().getChunkAt(blockposition).setBlockEntity(tileentity);
                }

                commandblocklistenerabstract.setCommand(s);
                commandblocklistenerabstract.setTrackOutput(flag);
                if (!flag) {
                    commandblocklistenerabstract.setLastOutput((Component) null);
                }

                tileentitycommand.setAutomatic(packet.isAutomatic());
                if (tileentitycommand_type != packet.getMode()) {
                    tileentitycommand.onModeSwitch();
                }

                commandblocklistenerabstract.onUpdated();
                if (!StringUtil.isNullOrEmpty(s)) {
                    this.player.sendSystemMessage(Component.translatable("advMode.setCommand.success", s));
                }
            }

        }
    }

    @Override
    public void handleSetCommandMinecart(ServerboundSetCommandMinecartPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (!this.server.isCommandBlockEnabled()) {
            this.player.sendSystemMessage(Component.translatable("advMode.notEnabled"));
        } else if (!this.player.canUseGameMasterBlocks() && (!this.player.isCreative() || !this.player.getBukkitEntity().hasPermission("minecraft.commandblock"))) { // Paper - command block permission
            this.player.sendSystemMessage(Component.translatable("advMode.notAllowed"));
        } else {
            BaseCommandBlock commandblocklistenerabstract = packet.getCommandBlock(this.player.level());

            if (commandblocklistenerabstract != null) {
                commandblocklistenerabstract.setCommand(packet.getCommand());
                commandblocklistenerabstract.setTrackOutput(packet.isTrackOutput());
                if (!packet.isTrackOutput()) {
                    commandblocklistenerabstract.setLastOutput((Component) null);
                }

                commandblocklistenerabstract.onUpdated();
                this.player.sendSystemMessage(Component.translatable("advMode.setCommand.success", packet.getCommand()));
            }

        }
    }

    @Override
    public void handlePickItem(ServerboundPickItemPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        // Paper start - validate pick item position
        if (!(packet.getSlot() >= 0 && packet.getSlot() < this.player.getInventory().items.size())) {
            ServerGamePacketListenerImpl.LOGGER.warn("{} tried to set an invalid carried item", this.player.getName().getString());
            this.disconnect("Invalid hotbar selection (Hacking?)", org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION); // Paper - kick event cause
            return;
        }
        Player bukkitPlayer = this.player.getBukkitEntity();
        int targetSlot = this.player.getInventory().getSuitableHotbarSlot();
        int sourceSlot = packet.getSlot();

        io.papermc.paper.event.player.PlayerPickItemEvent event = new io.papermc.paper.event.player.PlayerPickItemEvent(bukkitPlayer, targetSlot, sourceSlot);
        if (!event.callEvent()) return;

        this.player.getInventory().pickSlot(event.getSourceSlot(), event.getTargetSlot());
        // Paper end
        this.player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, this.player.getInventory().selected, this.player.getInventory().getItem(this.player.getInventory().selected)));
        this.player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, packet.getSlot(), this.player.getInventory().getItem(packet.getSlot())));
        this.player.connection.send(new ClientboundSetCarriedItemPacket(this.player.getInventory().selected));
    }

    @Override
    public void handleRenameItem(ServerboundRenameItemPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        AbstractContainerMenu container = this.player.containerMenu;

        if (container instanceof AnvilMenu) {
            AnvilMenu containeranvil = (AnvilMenu) container;

            if (!containeranvil.stillValid(this.player)) {
                ServerGamePacketListenerImpl.LOGGER.debug("Player {} interacted with invalid menu {}", this.player, containeranvil);
                return;
            }

            containeranvil.setItemName(packet.getName());
        }

    }

    @Override
    public void handleSetBeaconPacket(ServerboundSetBeaconPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        AbstractContainerMenu container = this.player.containerMenu;

        if (container instanceof BeaconMenu) {
            BeaconMenu containerbeacon = (BeaconMenu) container;

            if (!this.player.containerMenu.stillValid(this.player)) {
                ServerGamePacketListenerImpl.LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
                return;
            }

            containerbeacon.updateEffects(packet.getPrimary(), packet.getSecondary());
        }

    }

    @Override
    public void handleSetStructureBlock(ServerboundSetStructureBlockPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos blockposition = packet.getPos();
            BlockState iblockdata = this.player.level().getBlockState(blockposition);
            BlockEntity tileentity = this.player.level().getBlockEntity(blockposition);

            if (tileentity instanceof StructureBlockEntity) {
                StructureBlockEntity tileentitystructure = (StructureBlockEntity) tileentity;

                tileentitystructure.setMode(packet.getMode());
                tileentitystructure.setStructureName(packet.getName());
                tileentitystructure.setStructurePos(packet.getOffset());
                tileentitystructure.setStructureSize(packet.getSize());
                tileentitystructure.setMirror(packet.getMirror());
                tileentitystructure.setRotation(packet.getRotation());
                tileentitystructure.setMetaData(packet.getData());
                tileentitystructure.setIgnoreEntities(packet.isIgnoreEntities());
                tileentitystructure.setShowAir(packet.isShowAir());
                tileentitystructure.setShowBoundingBox(packet.isShowBoundingBox());
                tileentitystructure.setIntegrity(packet.getIntegrity());
                tileentitystructure.setSeed(packet.getSeed());
                if (tileentitystructure.hasStructureName()) {
                    String s = tileentitystructure.getStructureName();

                    if (packet.getUpdateType() == StructureBlockEntity.UpdateType.SAVE_AREA) {
                        if (tileentitystructure.saveStructure()) {
                            this.player.displayClientMessage(Component.translatable("structure_block.save_success", s), false);
                        } else {
                            this.player.displayClientMessage(Component.translatable("structure_block.save_failure", s), false);
                        }
                    } else if (packet.getUpdateType() == StructureBlockEntity.UpdateType.LOAD_AREA) {
                        if (!tileentitystructure.isStructureLoadable()) {
                            this.player.displayClientMessage(Component.translatable("structure_block.load_not_found", s), false);
                        } else if (tileentitystructure.loadStructure(this.player.serverLevel())) {
                            this.player.displayClientMessage(Component.translatable("structure_block.load_success", s), false);
                        } else {
                            this.player.displayClientMessage(Component.translatable("structure_block.load_prepare", s), false);
                        }
                    } else if (packet.getUpdateType() == StructureBlockEntity.UpdateType.SCAN_AREA) {
                        if (tileentitystructure.detectSize()) {
                            this.player.displayClientMessage(Component.translatable("structure_block.size_success", s), false);
                        } else {
                            this.player.displayClientMessage(Component.translatable("structure_block.size_failure"), false);
                        }
                    }
                } else {
                    this.player.displayClientMessage(Component.translatable("structure_block.invalid_structure_name", packet.getName()), false);
                }

                tileentitystructure.setChanged();
                this.player.level().sendBlockUpdated(blockposition, iblockdata, iblockdata, 3);
            }

        }
    }

    @Override
    public void handleSetJigsawBlock(ServerboundSetJigsawBlockPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos blockposition = packet.getPos();
            BlockState iblockdata = this.player.level().getBlockState(blockposition);
            BlockEntity tileentity = this.player.level().getBlockEntity(blockposition);

            if (tileentity instanceof JigsawBlockEntity) {
                JigsawBlockEntity tileentityjigsaw = (JigsawBlockEntity) tileentity;

                tileentityjigsaw.setName(packet.getName());
                tileentityjigsaw.setTarget(packet.getTarget());
                tileentityjigsaw.setPool(ResourceKey.create(Registries.TEMPLATE_POOL, packet.getPool()));
                tileentityjigsaw.setFinalState(packet.getFinalState());
                tileentityjigsaw.setJoint(packet.getJoint());
                tileentityjigsaw.setChanged();
                this.player.level().sendBlockUpdated(blockposition, iblockdata, iblockdata, 3);
            }

        }
    }

    @Override
    public void handleJigsawGenerate(ServerboundJigsawGeneratePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.canUseGameMasterBlocks()) {
            BlockPos blockposition = packet.getPos();
            BlockEntity tileentity = this.player.level().getBlockEntity(blockposition);

            if (tileentity instanceof JigsawBlockEntity) {
                JigsawBlockEntity tileentityjigsaw = (JigsawBlockEntity) tileentity;

                tileentityjigsaw.generate(this.player.serverLevel(), packet.levels(), packet.keepJigsaws());
            }

        }
    }

    @Override
    public void handleSelectTrade(ServerboundSelectTradePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        int i = packet.getItem();
        AbstractContainerMenu container = this.player.containerMenu;

        if (container instanceof MerchantMenu) {
            MerchantMenu containermerchant = (MerchantMenu) container;
            // CraftBukkit start
            final org.bukkit.event.inventory.TradeSelectEvent tradeSelectEvent = CraftEventFactory.callTradeSelectEvent(this.player, i, containermerchant);
            if (tradeSelectEvent.isCancelled()) {
                this.player.getBukkitEntity().updateInventory();
                return;
            }
            // CraftBukkit end

            if (!containermerchant.stillValid(this.player)) {
                ServerGamePacketListenerImpl.LOGGER.debug("Player {} interacted with invalid menu {}", this.player, containermerchant);
                return;
            }

            containermerchant.setSelectionHint(i);
            containermerchant.tryMoveItems(i);
        }

    }

    @Override
    public void handleEditBook(ServerboundEditBookPacket packet) {
        // Paper start
        if (!this.cserver.isPrimaryThread()) {
            List<String> pageList = packet.getPages();
            long byteTotal = 0;
            int maxBookPageSize = io.papermc.paper.configuration.GlobalConfiguration.get().itemValidation.bookSize.pageMax;
            double multiplier = Math.max(0.3D, Math.min(1D, io.papermc.paper.configuration.GlobalConfiguration.get().itemValidation.bookSize.totalMultiplier));
            long byteAllowed = maxBookPageSize;
            for (String testString : pageList) {
                int byteLength = testString.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (byteLength > 256 * 4) {
                    ServerGamePacketListenerImpl.LOGGER.warn(this.player.getScoreboardName() + " tried to send a book with with a page too large!");
                    this.disconnect("Book too large!", org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION); // Paper - kick event cause // Folia - region threading
                    return;
                }
                byteTotal += byteLength;
                int length = testString.length();
                int multibytes = 0;
                if (byteLength != length) {
                    for (char c : testString.toCharArray()) {
                        if (c > 127) {
                            multibytes++;
                        }
                    }
                }
                byteAllowed += (maxBookPageSize * Math.min(1, Math.max(0.1D, (double) length / 255D))) * multiplier;

                if (multibytes > 1) {
                    // penalize MB
                    byteAllowed -= multibytes;
                }
            }

            if (byteTotal > byteAllowed) {
                ServerGamePacketListenerImpl.LOGGER.warn(this.player.getScoreboardName() + " tried to send too large of a book. Book Size: " + byteTotal + " - Allowed:  "+ byteAllowed + " - Pages: " + pageList.size());
                this.disconnect("Book too large!", org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION); // Paper - kick event cause // Folia - region threading
                return;
            }
        }
        // Paper end
        // CraftBukkit start
        if (this.lastBookTick + 20 > this.lastTick) {
            this.disconnect("Book edited too quickly!", org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION); // Paper - kick event cause // Paper - Also ensure this is called on main // Folia - region threading
            return;
        }
        this.lastBookTick = this.lastTick;
        // CraftBukkit end
        int i = packet.getSlot();

        if (Inventory.isHotbarSlot(i) || i == 40) {
            List<String> list = Lists.newArrayList();
            Optional<String> optional = packet.getTitle();

            Objects.requireNonNull(list);
            optional.ifPresent(list::add);
            Stream<String> stream = packet.getPages().stream().limit(100L); // CraftBukkit - decompile error

            Objects.requireNonNull(list);
            stream.forEach(list::add);
            Consumer<List<FilteredText>> consumer = optional.isPresent() ? (list1) -> {
                this.signBook((FilteredText) list1.get(0), list1.subList(1, list1.size()), i);
            } : (list1) -> {
                this.updateBookContents(list1, i);
            };

            this.filterTextPacket(list).thenAcceptAsync(consumer, // Folia start - region threading
                (Runnable run) -> {
                    this.player.getBukkitEntity().taskScheduler.schedule(
                        (player) -> {
                            run.run();
                        },
                        null, 1L);
            }).whenComplete((Object res, Throwable thr) -> {
                if (thr != null) {
                    LOGGER.error("Failed to handle book update packet", thr);
                }
            });
            // Folia end - region threading
        }
    }

    private void updateBookContents(List<FilteredText> pages, int slotId) {
        ItemStack itemstack = this.player.getInventory().getItem(slotId);

        if (itemstack.is(Items.WRITABLE_BOOK)) {
            this.updateBookPages(pages, UnaryOperator.identity(), itemstack.copy(), slotId, itemstack); // CraftBukkit
        }
    }

    private void signBook(FilteredText title, List<FilteredText> pages, int slotId) {
        ItemStack itemstack = this.player.getInventory().getItem(slotId);

        if (itemstack.is(Items.WRITABLE_BOOK)) {
            ItemStack itemstack1 = new ItemStack(Items.WRITTEN_BOOK);
            CompoundTag nbttagcompound = itemstack.getTag();

            if (nbttagcompound != null) {
                itemstack1.setTag(nbttagcompound.copy());
            }

            itemstack1.addTagElement("author", StringTag.valueOf(this.player.getName().getString()));
            if (this.player.isTextFilteringEnabled()) {
                itemstack1.addTagElement("title", StringTag.valueOf(title.filteredOrEmpty()));
            } else {
                itemstack1.addTagElement("filtered_title", StringTag.valueOf(title.filteredOrEmpty()));
                itemstack1.addTagElement("title", StringTag.valueOf(title.raw()));
            }

            this.updateBookPages(pages, (s) -> {
                return Component.Serializer.toJson(Component.literal(s));
            }, itemstack1, slotId, itemstack); // CraftBukkit
            this.player.getInventory().setItem(slotId, itemstack); // CraftBukkit - event factory updates the hand book
        }
    }

    private void updateBookPages(List<FilteredText> list, UnaryOperator<String> unaryoperator, ItemStack itemstack, int slot, ItemStack handItem) { // CraftBukkit
        ListTag nbttaglist = new ListTag();

        if (this.player.isTextFilteringEnabled()) {
            Stream<StringTag> stream = list.stream().map((filteredtext) -> { // CraftBukkit - decompile error
                return StringTag.valueOf((String) unaryoperator.apply(filteredtext.filteredOrEmpty()));
            });

            Objects.requireNonNull(nbttaglist);
            stream.forEach(nbttaglist::add);
        } else {
            CompoundTag nbttagcompound = new CompoundTag();
            int i = 0;

            for (int j = list.size(); i < j; ++i) {
                FilteredText filteredtext = (FilteredText) list.get(i);
                String s = filteredtext.raw();

                nbttaglist.add(StringTag.valueOf((String) unaryoperator.apply(s)));
                if (filteredtext.isFiltered()) {
                    nbttagcompound.putString(String.valueOf(i), (String) unaryoperator.apply(filteredtext.filteredOrEmpty()));
                }
            }

            if (!nbttagcompound.isEmpty()) {
                itemstack.addTagElement("filtered_pages", nbttagcompound);
            }
        }

        itemstack.addTagElement("pages", nbttaglist);
        this.player.getInventory().setItem(slot, CraftEventFactory.handleEditBookEvent(this.player, slot, handItem, itemstack)); // CraftBukkit // Paper - Don't ignore result (see other callsite for handleEditBookEvent)
    }

    @Override
    public void handleEntityTagQuery(ServerboundEntityTagQuery packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.hasPermissions(2)) {
            Entity entity = this.player.level().getEntity(packet.getEntityId());

            if (entity != null) {
                CompoundTag nbttagcompound = entity.saveWithoutId(new CompoundTag());

                this.player.connection.send(new ClientboundTagQueryPacket(packet.getTransactionId(), nbttagcompound));
            }

        }
    }

    @Override
    public void handleBlockEntityTagQuery(ServerboundBlockEntityTagQuery packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.hasPermissions(2)) {
            BlockEntity tileentity = this.player.level().getBlockEntity(packet.getPos());
            CompoundTag nbttagcompound = tileentity != null ? tileentity.saveWithoutMetadata() : null;

            this.player.connection.send(new ClientboundTagQueryPacket(packet.getTransactionId(), nbttagcompound));
        }
    }

    @Override
    public void handleMovePlayer(ServerboundMovePlayerPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (ServerGamePacketListenerImpl.containsInvalidValues(packet.getX(0.0D), packet.getY(0.0D), packet.getZ(0.0D), packet.getYRot(0.0F), packet.getXRot(0.0F))) {
            this.disconnect(Component.translatable("multiplayer.disconnect.invalid_player_movement"), org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_PLAYER_MOVEMENT); // Paper - kick event cause
        } else {
            ServerLevel worldserver = this.player.serverLevel();

            if (!this.player.wonGame && !this.player.isImmobile()) { // CraftBukkit
                if (this.tickCount == 0) {
                    this.resetPosition();
                }

                if (this.awaitingPositionFromClient != null) {
                    if (false && this.tickCount - this.awaitingTeleportTime > 20) { // Paper - this will greatly screw with clients with > 1000ms RTT
                        this.awaitingTeleportTime = this.tickCount;
                        this.teleport(this.awaitingPositionFromClient.x, this.awaitingPositionFromClient.y, this.awaitingPositionFromClient.z, this.player.getYRot(), this.player.getXRot());
                    }
                    this.allowedPlayerTicks = 20; // CraftBukkit
                } else {
                    this.awaitingTeleportTime = this.tickCount;
                    double d0 = ServerGamePacketListenerImpl.clampHorizontal(packet.getX(this.player.getX())); final double toX = d0; // Paper - OBFHELPER
                    double d1 = ServerGamePacketListenerImpl.clampVertical(packet.getY(this.player.getY())); final double toY = d1;
                    double d2 = ServerGamePacketListenerImpl.clampHorizontal(packet.getZ(this.player.getZ())); final double toZ = d2; // Paper - OBFHELPER
                    float f = Mth.wrapDegrees(packet.getYRot(this.player.getYRot())); final float toYaw = f; // Paper - OBFHELPER
                    float f1 = Mth.wrapDegrees(packet.getXRot(this.player.getXRot())); final float toPitch = f1; // Paper - OBFHELPER

                    if (this.player.isPassenger()) {
                        this.player.absMoveTo(this.player.getX(), this.player.getY(), this.player.getZ(), f, f1);
                        this.player.serverLevel().getChunkSource().move(this.player);
                        this.allowedPlayerTicks = 20; // CraftBukkit
                    } else {
                        // CraftBukkit - Make sure the move is valid but then reset it for plugins to modify
                        double prevX = this.player.getX();
                        double prevY = this.player.getY();
                        double prevZ = this.player.getZ();
                        float prevYaw = this.player.getYRot();
                        float prevPitch = this.player.getXRot();
                        // CraftBukkit end
                        double d3 = this.player.getX();
                        double d4 = this.player.getY();
                        double d5 = this.player.getZ();
                        double d6 = d0 - this.firstGoodX;
                        double d7 = d1 - this.firstGoodY;
                        double d8 = d2 - this.firstGoodZ;
                        double d9 = this.player.getDeltaMovement().lengthSqr();
                        // Paper start - fix large move vectors killing the server
                        double currDeltaX = toX - prevX;
                        double currDeltaY = toY - prevY;
                        double currDeltaZ = toZ - prevZ;
                        double d10 = Math.max(d6 * d6 + d7 * d7 + d8 * d8, (currDeltaX * currDeltaX + currDeltaY * currDeltaY + currDeltaZ * currDeltaZ) - 1);
                        // Paper end - fix large move vectors killing the server
                        // Paper start - fix large move vectors killing the server
                        double otherFieldX = d0 - this.lastGoodX;
                        double otherFieldY = d1 - this.lastGoodY;
                        double otherFieldZ = d2 - this.lastGoodZ;
                        d10 = Math.max(d10, (otherFieldX * otherFieldX + otherFieldY * otherFieldY + otherFieldZ * otherFieldZ) - 1);
                        // Paper end - fix large move vectors killing the server

                        if (this.player.isSleeping()) {
                            if (d10 > 1.0D) {
                                this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), f, f1);
                            }

                        } else {
                            ++this.receivedMovePacketCount;
                            int i = this.receivedMovePacketCount - this.knownMovePacketCount;

                            // CraftBukkit start - handle custom speeds and skipped ticks
                            int currTick = (int)(Util.getMillis() / 50); // Folia - region threading
                            this.allowedPlayerTicks += currTick - this.lastTick; // Folia - region threading
                            this.allowedPlayerTicks = Math.max(this.allowedPlayerTicks, 1);
                            this.lastTick = (int) currTick; // Folia - region threading

                            if (i > Math.max(this.allowedPlayerTicks, 5)) {
                                ServerGamePacketListenerImpl.LOGGER.debug("{} is sending move packets too frequently ({} packets since last tick)", this.player.getName().getString(), i);
                                i = 1;
                            }

                            if (packet.hasRot || d10 > 0) {
                                this.allowedPlayerTicks -= 1;
                            } else {
                                this.allowedPlayerTicks = 20;
                            }
                            double speed;
                            if (this.player.getAbilities().flying) {
                                speed = this.player.getAbilities().flyingSpeed * 20f;
                            } else {
                                speed = this.player.getAbilities().walkingSpeed * 10f;
                            }
                            // Paper start - Prevent moving into unloaded chunks
                            if (this.player.level().paperConfig().chunks.preventMovingIntoUnloadedChunks && (this.player.getX() != toX || this.player.getZ() != toZ) && !worldserver.areChunksLoadedForMove(this.player.getBoundingBox().expandTowards(new Vec3(toX, toY, toZ).subtract(this.player.position())))) {
                                // Paper start - Add fail move event
                                io.papermc.paper.event.player.PlayerFailMoveEvent event = fireFailMove(io.papermc.paper.event.player.PlayerFailMoveEvent.FailReason.MOVED_INTO_UNLOADED_CHUNK,
                                    toX, toY, toZ, toYaw, toPitch, false);
                                if (!event.isAllowed()) {
                                this.internalTeleport(this.player.getX(), this.player.getY(), this.player.getZ(), this.player.getYRot(), this.player.getXRot(), Collections.emptySet());
                                return;
                                }
                                // Paper end
                            }
                            // Paper end

                            if (!this.player.isChangingDimension() && (!this.player.level().getGameRules().getBoolean(GameRules.RULE_DISABLE_ELYTRA_MOVEMENT_CHECK) || !this.player.isFallFlying())) {
                                float f2 = this.player.isFallFlying() ? 300.0F : 100.0F;

                                if (d10 - d9 > Math.max(f2, Math.pow((double) (org.spigotmc.SpigotConfig.movedTooQuicklyMultiplier * (float) i * speed), 2)) && !this.isSingleplayerOwner()) {
                                // CraftBukkit end
                                    // Paper start - Add fail move event
                                    io.papermc.paper.event.player.PlayerFailMoveEvent event = fireFailMove(io.papermc.paper.event.player.PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY,
                                        toX, toY, toZ, toYaw, toPitch, true);
                                    if (!event.isAllowed()) {
                                        if (event.getLogWarning())
                                    ServerGamePacketListenerImpl.LOGGER.warn("{} moved too quickly! {},{},{}", new Object[]{this.player.getName().getString(), d6, d7, d8});
                                    this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), this.player.getYRot(), this.player.getXRot());
                                    return;
                                    }
                                    // Paper end
                                }
                            }

                            AABB axisalignedbb = this.player.getBoundingBox(); // Paper - diff on change, should be old AABB

                            d6 = d0 - this.lastGoodX; // Paper - diff on change, used for checking large move vectors above
                            d7 = d1 - this.lastGoodY; // Paper - diff on change, used for checking large move vectors above
                            d8 = d2 - this.lastGoodZ; // Paper - diff on change, used for checking large move vectors above
                            boolean flag = d7 > 0.0D;

                            if (this.player.onGround() && !packet.isOnGround() && flag) {
                                // Paper start - Add player jump event
                                Player player = this.getCraftPlayer();
                                Location from = new Location(player.getWorld(), lastPosX, lastPosY, lastPosZ, lastYaw, lastPitch); // Get the Players previous Event location.
                                Location to = player.getLocation().clone(); // Start off the To location as the Players current location.

                                // If the packet contains movement information then we update the To location with the correct XYZ.
                                if (packet.hasPos) {
                                    to.setX(packet.x);
                                    to.setY(packet.y);
                                    to.setZ(packet.z);
                                }

                                // If the packet contains look information then we update the To location with the correct Yaw & Pitch.
                                if (packet.hasRot) {
                                    to.setYaw(packet.yRot);
                                    to.setPitch(packet.xRot);
                                }

                                com.destroystokyo.paper.event.player.PlayerJumpEvent event = new com.destroystokyo.paper.event.player.PlayerJumpEvent(player, from, to);

                                if (event.callEvent()) {
                                    this.player.jumpFromGround();
                                } else {
                                    from = event.getFrom();
                                    this.internalTeleport(from.getX(), from.getY(), from.getZ(), from.getYaw(), from.getPitch(), Collections.emptySet());
                                    return;
                                }
                                // Paper end
                            }

                            boolean flag1 = this.player.verticalCollisionBelow;

                            this.player.move(MoverType.PLAYER, new Vec3(d6, d7, d8));
                            this.player.onGround = packet.isOnGround(); // CraftBukkit - SPIGOT-5810, SPIGOT-5835, SPIGOT-6828: reset by this.player.move
                            boolean didCollide = toX != this.player.getX() || toY != this.player.getY() || toZ != this.player.getZ(); // Paper - needed here as the difference in Y can be reset - also note: this is only a guess at whether collisions took place, floating point errors can make this true when it shouldn't be...
                            // Paper start - prevent position desync
                            if (this.awaitingPositionFromClient != null) {
                                return; // ... thanks Mojang for letting move calls teleport across dimensions.
                            }
                            // Paper end - prevent position desync
                            double d11 = d7;

                            d6 = d0 - this.player.getX();
                            d7 = d1 - this.player.getY();
                            if (d7 > -0.5D || d7 < 0.5D) {
                                d7 = 0.0D;
                            }

                            d8 = d2 - this.player.getZ();
                            d10 = d6 * d6 + d7 * d7 + d8 * d8;
                            boolean flag2 = false;

                            if (!this.player.isChangingDimension() && d10 > org.spigotmc.SpigotConfig.movedWronglyThreshold && !this.player.isSleeping() && !this.player.gameMode.isCreative() && this.player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) { // Spigot
                                // Paper start - Add fail move event
                                io.papermc.paper.event.player.PlayerFailMoveEvent event = fireFailMove(io.papermc.paper.event.player.PlayerFailMoveEvent.FailReason.MOVED_WRONGLY,
                                    toX, toY, toZ, toYaw, toPitch, true);
                                if (!event.isAllowed()) {
                                flag2 = true; // Paper - diff on change, this should be moved wrongly
                                    if (event.getLogWarning())
                                ServerGamePacketListenerImpl.LOGGER.warn("{} moved wrongly!", this.player.getName().getString());
                                }
                                // Paper end
                            }

                            // Paper start - optimise out extra getCubes
                            this.player.absMoveTo(d0, d1, d2, f, f1); // prevent desync by tping to the set position, dropped for unknown reasons by mojang
                            // Original for reference:
                            // boolean teleportBack = flag2 && worldserver.getCubes(this.player, axisalignedbb) || (didCollide && this.a((IWorldReader) worldserver, axisalignedbb));
                            boolean teleportBack = flag2; // violating this is always a fail
                            if (!this.player.noPhysics && !this.player.isSleeping() && !teleportBack) {
                                AABB newBox = this.player.getBoundingBox();
                                if (didCollide || !axisalignedbb.equals(newBox)) {
                                    // note: only call after setLocation, or else getBoundingBox is wrong
                                    teleportBack = this.hasNewCollision(worldserver, this.player, axisalignedbb, newBox);
                                    // Paper start - Add fail move event
                                    if (teleportBack) {
                                        io.papermc.paper.event.player.PlayerFailMoveEvent event = fireFailMove(io.papermc.paper.event.player.PlayerFailMoveEvent.FailReason.CLIPPED_INTO_BLOCK,
                                            toX, toY, toZ, toYaw, toPitch, false);
                                        if (event.isAllowed()) {
                                            teleportBack = false;
                                        }
                                    }
                                    // Paper end
                                } // else: no collision at all detected, why do we care?
                            }
                            if (!this.player.noPhysics && !this.player.isSleeping() && teleportBack) { // Paper end - optimise out extra getCubes
                                this.internalTeleport(d3, d4, d5, f, f1, Collections.emptySet()); // CraftBukkit - SPIGOT-1807: Don't call teleport event, when the client thinks the player is falling, because the chunks are not loaded on the client yet.
                                this.player.doCheckFallDamage(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5, packet.isOnGround());
                            } else {
                                // CraftBukkit start - fire PlayerMoveEvent
                                // Reset to old location first
                                this.player.absMoveTo(prevX, prevY, prevZ, prevYaw, prevPitch);

                                Player player = this.getCraftPlayer();
                                Location from = new Location(player.getWorld(), this.lastPosX, this.lastPosY, this.lastPosZ, this.lastYaw, this.lastPitch); // Get the Players previous Event location.
                                Location to = player.getLocation().clone(); // Start off the To location as the Players current location.

                                // If the packet contains movement information then we update the To location with the correct XYZ.
                                if (packet.hasPos) {
                                    to.setX(packet.x);
                                    to.setY(packet.y);
                                    to.setZ(packet.z);
                                }

                                // If the packet contains look information then we update the To location with the correct Yaw & Pitch.
                                if (packet.hasRot) {
                                    to.setYaw(packet.yRot);
                                    to.setPitch(packet.xRot);
                                }

                                // Prevent 40 event-calls for less than a single pixel of movement >.>
                                double delta = Math.pow(this.lastPosX - to.getX(), 2) + Math.pow(this.lastPosY - to.getY(), 2) + Math.pow(this.lastPosZ - to.getZ(), 2);
                                float deltaAngle = Math.abs(this.lastYaw - to.getYaw()) + Math.abs(this.lastPitch - to.getPitch());

                                if ((delta > 1f / 256 || deltaAngle > 10f) && !this.player.isImmobile()) {
                                    this.lastPosX = to.getX();
                                    this.lastPosY = to.getY();
                                    this.lastPosZ = to.getZ();
                                    this.lastYaw = to.getYaw();
                                    this.lastPitch = to.getPitch();

                                    // Skip the first time we do this
                                    if (from.getX() != Double.MAX_VALUE) {
                                        Location oldTo = to.clone();
                                        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
                                        this.cserver.getPluginManager().callEvent(event);

                                        // If the event is cancelled we move the player back to their old location.
                                        if (event.isCancelled()) {
                                            this.player.getBukkitEntity().teleportAsync(from, PlayerTeleportEvent.TeleportCause.PLUGIN); // Folia - region threading
                                            return;
                                        }

                                        // If a Plugin has changed the To destination then we teleport the Player
                                        // there to avoid any 'Moved wrongly' or 'Moved too quickly' errors.
                                        // We only do this if the Event was not cancelled.
                                        if (!oldTo.equals(event.getTo()) && !event.isCancelled()) {
                                            this.player.getBukkitEntity().teleportAsync(event.getTo(), PlayerTeleportEvent.TeleportCause.PLUGIN); // Folia - region threading
                                            return;
                                        }

                                        // Check to see if the Players Location has some how changed during the call of the event.
                                        // This can happen due to a plugin teleporting the player instead of using .setTo()
                                        if (!from.equals(this.getCraftPlayer().getLocation()) && this.justTeleported) {
                                            this.justTeleported = false;
                                            return;
                                        }
                                    }
                                }
                                // CraftBukkit end
                                this.player.absMoveTo(d0, d1, d2, f, f1);
                                this.clientIsFloating = d11 >= -0.03125D && !flag1 && this.player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR && !this.server.isFlightAllowed() && !this.player.getAbilities().mayfly && !this.player.hasEffect(MobEffects.LEVITATION) && !this.player.isFallFlying() && !this.player.isAutoSpinAttack() && this.noBlocksAround(this.player);
                                this.player.serverLevel().getChunkSource().move(this.player);
                                this.player.doCheckFallDamage(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5, packet.isOnGround());
                                this.player.setOnGroundWithKnownMovement(packet.isOnGround(), new Vec3(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5));
                                if (flag) {
                                    this.player.resetFallDistance();
                                }

                                this.player.checkMovementStatistics(this.player.getX() - d3, this.player.getY() - d4, this.player.getZ() - d5);
                                this.lastGoodX = this.player.getX();
                                this.lastGoodY = this.player.getY();
                                this.lastGoodZ = this.player.getZ();
                            }
                        }
                    }
                }
            }
        }
    }

    // Paper start - Add fail move event
    private io.papermc.paper.event.player.PlayerFailMoveEvent fireFailMove(io.papermc.paper.event.player.PlayerFailMoveEvent.FailReason failReason,
               double toX, double toY, double toZ, float toYaw, float toPitch, boolean logWarning) {
        Player player = this.getCraftPlayer();
        Location from = new Location(player.getWorld(), this.lastPosX, this.lastPosY, this.lastPosZ, this.lastYaw, this.lastPitch);
        Location to = new Location(player.getWorld(), toX, toY, toZ, toYaw, toPitch);
        io.papermc.paper.event.player.PlayerFailMoveEvent event = new io.papermc.paper.event.player.PlayerFailMoveEvent(player, failReason,
            false, logWarning, from, to);
        event.callEvent();
        return event;
    }
    // Paper end
    // Paper start - optimise out extra getCubes
    private boolean hasNewCollision(final ServerLevel world, final Entity entity, final AABB oldBox, final AABB newBox) {
        final List<AABB> collisionsBB = new java.util.ArrayList<>();
        final List<VoxelShape> collisionsVoxel = new java.util.ArrayList<>();
        io.papermc.paper.util.CollisionUtil.getCollisions(
            world, entity, newBox, collisionsVoxel, collisionsBB,
            io.papermc.paper.util.CollisionUtil.COLLISION_FLAG_COLLIDE_WITH_UNLOADED_CHUNKS | io.papermc.paper.util.CollisionUtil.COLLISION_FLAG_CHECK_BORDER,
            null, null
        );

        for (int i = 0, len = collisionsBB.size(); i < len; ++i) {
            final AABB box = collisionsBB.get(i);
            if (!io.papermc.paper.util.CollisionUtil.voxelShapeIntersect(box, oldBox)) {
                return true;
            }
        }

        for (int i = 0, len = collisionsVoxel.size(); i < len; ++i) {
            final VoxelShape voxel = collisionsVoxel.get(i);
            if (!io.papermc.paper.util.CollisionUtil.voxelShapeIntersectNoEmpty(voxel, oldBox)) {
                return true;
            }
        }

        return false;
    }
    // Paper end - optimise out extra getCubes
    private boolean isPlayerCollidingWithAnythingNew(LevelReader world, AABB box, double newX, double newY, double newZ) {
        AABB axisalignedbb1 = this.player.getBoundingBox().move(newX - this.player.getX(), newY - this.player.getY(), newZ - this.player.getZ());
        Iterable<VoxelShape> iterable = world.getCollisions(this.player, axisalignedbb1.deflate(9.999999747378752E-6D));
        VoxelShape voxelshape = Shapes.create(box.deflate(9.999999747378752E-6D));
        Iterator iterator = iterable.iterator();

        VoxelShape voxelshape1;

        do {
            if (!iterator.hasNext()) {
                return false;
            }

            voxelshape1 = (VoxelShape) iterator.next();
        } while (Shapes.joinIsNotEmpty(voxelshape1, voxelshape, BooleanOp.AND));

        return true;
    }

    // CraftBukkit start - Delegate to teleport(Location)
    public void teleport(double x, double y, double z, float yaw, float pitch) {
        this.teleport(x, y, z, yaw, pitch, PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    public void teleport(double d0, double d1, double d2, float f, float f1, PlayerTeleportEvent.TeleportCause cause) {
        this.teleport(d0, d1, d2, f, f1, Collections.emptySet(), cause);
    }

    public void teleport(double x, double y, double z, float yaw, float pitch, Set<RelativeMovement> flags) {
        this.teleport(x, y, z, yaw, pitch, flags, PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    public boolean teleport(double d0, double d1, double d2, float f, float f1, Set<RelativeMovement> set, PlayerTeleportEvent.TeleportCause cause) { // CraftBukkit - Return event status
        Player player = this.getCraftPlayer();
        Location from = player.getLocation();

        double x = d0;
        double y = d1;
        double z = d2;
        float yaw = f;
        float pitch = f1;

        Location to = new Location(this.getCraftPlayer().getWorld(), x, y, z, yaw, pitch);
        // SPIGOT-5171: Triggered on join
        if (from.equals(to)) {
            this.internalTeleport(d0, d1, d2, f, f1, set);
            return false; // CraftBukkit - Return event status
        }

        // Paper start - Teleport API
        Set<io.papermc.paper.entity.TeleportFlag.Relative> relativeFlags = java.util.EnumSet.noneOf(io.papermc.paper.entity.TeleportFlag.Relative.class);
        for (RelativeMovement relativeArgument : set) {
            relativeFlags.add(org.bukkit.craftbukkit.entity.CraftPlayer.toApiRelativeFlag(relativeArgument));
        }
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, from.clone(), to.clone(), cause, java.util.Set.copyOf(relativeFlags));
        // Paper end
        this.cserver.getPluginManager().callEvent(event);

        if (event.isCancelled() || !to.equals(event.getTo())) {
            //set.clear(); // Can't relative teleport // Paper - Teleport API: Now you can!
            to = event.isCancelled() ? event.getFrom() : event.getTo();
            d0 = to.getX();
            d1 = to.getY();
            d2 = to.getZ();
            f = to.getYaw();
            f1 = to.getPitch();
        }

        this.internalTeleport(d0, d1, d2, f, f1, set);
        return event.isCancelled(); // CraftBukkit - Return event status
    }

    public void teleport(Location dest) {
        this.internalTeleport(dest.getX(), dest.getY(), dest.getZ(), dest.getYaw(), dest.getPitch(), Collections.emptySet());
    }

    public void internalTeleport(double d0, double d1, double d2, float f, float f1, Set<RelativeMovement> set) { // Paper
        org.spigotmc.AsyncCatcher.catchOp("teleport"); // Paper
        // Paper start
        if (player.isRemoved()) {
            LOGGER.info("Attempt to teleport removed player {} restricted", player.getScoreboardName());
            if (server.isDebugging()) io.papermc.paper.util.TraceUtil.dumpTraceForThread("Attempt to teleport removed player");
            return;
        }
        // Paper end
        // CraftBukkit start
        if (Float.isNaN(f)) {
            f = 0;
        }
        if (Float.isNaN(f1)) {
            f1 = 0;
        }

        this.justTeleported = true;
        // CraftBukkit end
        double d3 = set.contains(RelativeMovement.X) ? this.player.getX() : 0.0D;
        double d4 = set.contains(RelativeMovement.Y) ? this.player.getY() : 0.0D;
        double d5 = set.contains(RelativeMovement.Z) ? this.player.getZ() : 0.0D;
        float f2 = set.contains(RelativeMovement.Y_ROT) ? this.player.getYRot() : 0.0F;
        float f3 = set.contains(RelativeMovement.X_ROT) ? this.player.getXRot() : 0.0F;

        this.awaitingPositionFromClient = new Vec3(d0, d1, d2);
        if (++this.awaitingTeleport == Integer.MAX_VALUE) {
            this.awaitingTeleport = 0;
        }

        // CraftBukkit start - update last location
        this.lastPosX = this.awaitingPositionFromClient.x;
        this.lastPosY = this.awaitingPositionFromClient.y;
        this.lastPosZ = this.awaitingPositionFromClient.z;
        this.lastYaw = f;
        this.lastPitch = f1;
        // CraftBukkit end

        this.awaitingTeleportTime = this.tickCount;
        this.player.moveTo(d0, d1, d2, f, f1); // Paper - use proper moveTo for teleportation
        this.player.connection.send(new ClientboundPlayerPositionPacket(d0 - d3, d1 - d4, d2 - d5, f - f2, f1 - f3, set, this.awaitingTeleport));
    }

    @Override
    public void handlePlayerAction(ServerboundPlayerActionPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        BlockPos blockposition = packet.getPos();

        this.player.resetLastActionTime();
        ServerboundPlayerActionPacket.Action packetplayinblockdig_enumplayerdigtype = packet.getAction();

        switch (packetplayinblockdig_enumplayerdigtype) {
            case SWAP_ITEM_WITH_OFFHAND:
                if (!this.player.isSpectator()) {
                    ItemStack itemstack = this.player.getItemInHand(InteractionHand.OFF_HAND);

                    // CraftBukkit start - inspiration taken from DispenserRegistry (See SpigotCraft#394)
                    CraftItemStack mainHand = CraftItemStack.asCraftMirror(itemstack);
                    CraftItemStack offHand = CraftItemStack.asCraftMirror(this.player.getItemInHand(InteractionHand.MAIN_HAND));
                    PlayerSwapHandItemsEvent swapItemsEvent = new PlayerSwapHandItemsEvent(this.getCraftPlayer(), mainHand.clone(), offHand.clone());
                    this.cserver.getPluginManager().callEvent(swapItemsEvent);
                    if (swapItemsEvent.isCancelled()) {
                        return;
                    }
                    if (swapItemsEvent.getOffHandItem().equals(offHand)) {
                        this.player.setItemInHand(InteractionHand.OFF_HAND, this.player.getItemInHand(InteractionHand.MAIN_HAND));
                    } else {
                        this.player.setItemInHand(InteractionHand.OFF_HAND, CraftItemStack.asNMSCopy(swapItemsEvent.getOffHandItem()));
                    }
                    if (swapItemsEvent.getMainHandItem().equals(mainHand)) {
                        this.player.setItemInHand(InteractionHand.MAIN_HAND, itemstack);
                    } else {
                        this.player.setItemInHand(InteractionHand.MAIN_HAND, CraftItemStack.asNMSCopy(swapItemsEvent.getMainHandItem()));
                    }
                    // CraftBukkit end
                    this.player.stopUsingItem();
                }

                return;
            case DROP_ITEM:
                if (!this.player.isSpectator()) {
                    // limit how quickly items can be dropped
                    // If the ticks aren't the same then the count starts from 0 and we update the lastDropTick.
                    if (this.lastDropTick != io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick()) {
                        this.dropCount = 0;
                        this.lastDropTick = io.papermc.paper.threadedregions.RegionizedServer.getCurrentTick();
                    } else {
                        // Else we increment the drop count and check the amount.
                        this.dropCount++;
                        if (this.dropCount >= 20) {
                            ServerGamePacketListenerImpl.LOGGER.warn(this.player.getScoreboardName() + " dropped their items too quickly!");
                            this.disconnect("You dropped your items too quickly (Hacking?)", org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION); // Paper - kick event cause
                            return;
                        }
                    }
                    // CraftBukkit end
                    this.player.drop(false);
                }

                return;
            case DROP_ALL_ITEMS:
                if (!this.player.isSpectator()) {
                    this.player.drop(true);
                }

                return;
            case RELEASE_USE_ITEM:
                this.player.releaseUsingItem();
                return;
            case START_DESTROY_BLOCK:
            case ABORT_DESTROY_BLOCK:
            case STOP_DESTROY_BLOCK:
                // Paper start - Don't allow digging in unloaded chunks
                if (!io.papermc.paper.util.TickThread.isTickThreadFor(this.player.serverLevel(), blockposition.getX() >> 4, blockposition.getZ() >> 4, 8) || this.player.level().getChunkIfLoadedImmediately(blockposition.getX() >> 4, blockposition.getZ() >> 4) == null) { // Folia - region threading - don't destroy blocks not owned
                    this.player.connection.ackBlockChangesUpTo(packet.getSequence());
                    return;
                }
                // Paper end - Don't allow digging in unloaded chunks
                // Paper start - send block entities after prediction
                this.player.gameMode.capturedBlockEntity = false;
                this.player.gameMode.captureSentBlockEntities = true;
                // Paper end - send block entities after prediction
                this.player.gameMode.handleBlockBreakAction(blockposition, packetplayinblockdig_enumplayerdigtype, packet.getDirection(), this.player.level().getMaxBuildHeight(), packet.getSequence());
                this.player.connection.ackBlockChangesUpTo(packet.getSequence());
                // Paper start - send block entities after prediction
                this.player.gameMode.captureSentBlockEntities = false;
                // If a block entity was modified speedup the block change ack to avoid the block entity
                // being overriden.
                if (this.player.gameMode.capturedBlockEntity) {
                    // manually tick
                    this.send(new ClientboundBlockChangedAckPacket(this.ackBlockChangesUpTo));
                    this.player.connection.ackBlockChangesUpTo = -1;

                    this.player.gameMode.capturedBlockEntity = false;
                    BlockEntity tileentity = this.player.level().getBlockEntity(blockposition);
                    if (tileentity != null) {
                        this.player.connection.send(tileentity.getUpdatePacket());
                    }
                }
                // Paper end - send block entities after prediction
                return;
            default:
                throw new IllegalArgumentException("Invalid player action");
        }
    }

    private static boolean wasBlockPlacementAttempt(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        } else {
            Item item = stack.getItem();

            return (item instanceof BlockItem || item instanceof BucketItem) && !player.getCooldowns().isOnCooldown(item);
        }
    }

    // Spigot start - limit place/interactions
    private int limitedPackets;
    private long lastLimitedPacket = -1;
    private static int getSpamThreshold() { return io.papermc.paper.configuration.GlobalConfiguration.get().spamLimiter.incomingPacketThreshold; } // Paper - Configurable threshold

    private boolean checkLimit(long timestamp) {
        if (this.lastLimitedPacket != -1 && timestamp - this.lastLimitedPacket < getSpamThreshold() && this.limitedPackets++ >= 8) { // Paper - Use threshold, raise packet limit to 8
            return false;
        }

        if (this.lastLimitedPacket == -1 || timestamp - this.lastLimitedPacket >= getSpamThreshold()) { // Paper
            this.lastLimitedPacket = timestamp;
            this.limitedPackets = 0;
            return true;
        }

        return true;
    }
    // Spigot end

    @Override
    public void handleUseItemOn(ServerboundUseItemOnPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (!this.checkLimit(packet.timestamp)) return; // Spigot - check limit
        this.player.connection.ackBlockChangesUpTo(packet.getSequence());
        ServerLevel worldserver = this.player.serverLevel();
        InteractionHand enumhand = packet.getHand();
        ItemStack itemstack = this.player.getItemInHand(enumhand);

        if (itemstack.isItemEnabled(worldserver.enabledFeatures())) {
            BlockHitResult movingobjectpositionblock = packet.getHitResult();
            Vec3 vec3d = movingobjectpositionblock.getLocation();
            // Paper start - improve distance check
            if (!Double.isFinite(vec3d.x) || !Double.isFinite(vec3d.y) || !Double.isFinite(vec3d.z)) {
                return;
            }
            // Paper end
            BlockPos blockposition = movingobjectpositionblock.getBlockPos();
            Vec3 vec3d1 = Vec3.atCenterOf(blockposition);

            if (io.papermc.paper.util.TickThread.isTickThreadFor(this.player.serverLevel(), blockposition.getX() >> 4, blockposition.getZ() >> 4, 8) && this.player.getEyePosition().distanceToSqr(vec3d1) <= ServerGamePacketListenerImpl.MAX_INTERACTION_DISTANCE) { // Folia - do not allow players to interact with blocks outside the current region
                Vec3 vec3d2 = vec3d.subtract(vec3d1);
                double d0 = 1.0000001D;

                if (Math.abs(vec3d2.x()) < 1.0000001D && Math.abs(vec3d2.y()) < 1.0000001D && Math.abs(vec3d2.z()) < 1.0000001D) {
                    Direction enumdirection = movingobjectpositionblock.getDirection();

                    this.player.resetLastActionTime();
                    int i = this.player.level().getMaxBuildHeight();

                    if (blockposition.getY() < i) {
                        if (this.awaitingPositionFromClient == null && this.player.distanceToSqr((double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.5D, (double) blockposition.getZ() + 0.5D) < 64.0D && (worldserver.mayInteract(this.player, blockposition)  || (worldserver.paperConfig().spawn.allowUsingSignsInsideSpawnProtection && worldserver.getBlockState(blockposition).getBlock() instanceof net.minecraft.world.level.block.SignBlock))) { // Paper - sign check
                            this.player.stopUsingItem(); // CraftBukkit - SPIGOT-4706
                            InteractionResult enuminteractionresult = this.player.gameMode.useItemOn(this.player, worldserver, itemstack, enumhand, movingobjectpositionblock);

                            if (enumdirection == Direction.UP && !enuminteractionresult.consumesAction() && blockposition.getY() >= i - 1 && ServerGamePacketListenerImpl.wasBlockPlacementAttempt(this.player, itemstack)) {
                                MutableComponent ichatmutablecomponent = Component.translatable("build.tooHigh", i - 1).withStyle(ChatFormatting.RED);

                                this.player.sendSystemMessage(ichatmutablecomponent, true);
                            } else if (enuminteractionresult.shouldSwing() && !this.player.gameMode.interactResult) { // Paper
                                this.player.swing(enumhand, true);
                            }
                        }
                    } else {
                        MutableComponent ichatmutablecomponent1 = Component.translatable("build.tooHigh", i - 1).withStyle(ChatFormatting.RED);

                        this.player.sendSystemMessage(ichatmutablecomponent1, true);
                    }

                    this.player.connection.send(new ClientboundBlockUpdatePacket(worldserver, blockposition));
                    this.player.connection.send(new ClientboundBlockUpdatePacket(worldserver, blockposition.relative(enumdirection)));
                } else {
                    ServerGamePacketListenerImpl.LOGGER.warn("Rejecting UseItemOnPacket from {}: Location {} too far away from hit block {}.", new Object[]{this.player.getGameProfile().getName(), vec3d, blockposition});
                }
            }
        }
    }

    @Override
    public void handleUseItem(ServerboundUseItemPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (!this.checkLimit(packet.timestamp)) return; // Spigot - check limit
        this.ackBlockChangesUpTo(packet.getSequence());
        ServerLevel worldserver = this.player.serverLevel();
        InteractionHand enumhand = packet.getHand();
        ItemStack itemstack = this.player.getItemInHand(enumhand);

        this.player.resetLastActionTime();
        if (!itemstack.isEmpty() && itemstack.isItemEnabled(worldserver.enabledFeatures())) {
            // CraftBukkit start
            // Raytrace to look for 'rogue armswings'
            float f1 = this.player.getXRot();
            float f2 = this.player.getYRot();
            double d0 = this.player.getX();
            double d1 = this.player.getY() + (double) this.player.getEyeHeight();
            double d2 = this.player.getZ();
            Vec3 vec3d = new Vec3(d0, d1, d2);

            float f3 = Mth.cos(-f2 * 0.017453292F - 3.1415927F);
            float f4 = Mth.sin(-f2 * 0.017453292F - 3.1415927F);
            float f5 = -Mth.cos(-f1 * 0.017453292F);
            float f6 = Mth.sin(-f1 * 0.017453292F);
            float f7 = f4 * f5;
            float f8 = f3 * f5;
            double d3 = this.player.gameMode.getGameModeForPlayer()== GameType.CREATIVE ? 5.0D : 4.5D;
            Vec3 vec3d1 = vec3d.add((double) f7 * d3, (double) f6 * d3, (double) f8 * d3);
            HitResult movingobjectposition = this.player.level().clip(new ClipContext(vec3d, vec3d1, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.player));

            boolean cancelled;
            if (movingobjectposition == null || movingobjectposition.getType() != HitResult.Type.BLOCK) {
                org.bukkit.event.player.PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(this.player, Action.RIGHT_CLICK_AIR, itemstack, enumhand);
                cancelled = event.useItemInHand() == Event.Result.DENY;
            } else {
                BlockHitResult movingobjectpositionblock = (BlockHitResult) movingobjectposition;
                if (this.player.gameMode.firedInteract && this.player.gameMode.interactPosition.equals(movingobjectpositionblock.getBlockPos()) && this.player.gameMode.interactHand == enumhand && ItemStack.isSameItemSameTags(this.player.gameMode.interactItemStack, itemstack)) {
                    cancelled = this.player.gameMode.interactResult;
                } else {
                    org.bukkit.event.player.PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(this.player, Action.RIGHT_CLICK_BLOCK, movingobjectpositionblock.getBlockPos(), movingobjectpositionblock.getDirection(), itemstack, true, enumhand, movingobjectpositionblock.getLocation());
                    cancelled = event.useItemInHand() == Event.Result.DENY;
                }
                this.player.gameMode.firedInteract = false;
            }

            if (cancelled) {
                this.player.resyncUsingItem(this.player); // Paper - Resend player's using item status
                this.player.getBukkitEntity().updateInventory(); // SPIGOT-2524
                return;
            }
            itemstack = this.player.getItemInHand(enumhand); // Update in case it was changed in the event
            if (itemstack.isEmpty()) {
                return;
            }
            // CraftBukkit end
            InteractionResult enuminteractionresult = this.player.gameMode.useItem(this.player, worldserver, itemstack, enumhand);

            if (enuminteractionresult.shouldSwing()) {
                this.player.swing(enumhand, true);
            }

        }
    }

    @Override
    public void handleTeleportToEntityPacket(ServerboundTeleportToEntityPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.isSpectator()) {
            Iterator iterator = this.server.getAllLevels().iterator();

            while (iterator.hasNext()) {
                ServerLevel worldserver = (ServerLevel) iterator.next();
                Entity entity = packet.getEntity(worldserver);

                if (entity != null) {
                    io.papermc.paper.threadedregions.TeleportUtils.teleport(this.player, false, entity, null, null, Entity.TELEPORT_FLAG_LOAD_CHUNK, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.SPECTATE, null); // Folia - region threading
                    return;
                }
            }
        }

    }

    @Override
    public void handlePaddleBoat(ServerboundPaddleBoatPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        Entity entity = this.player.getControlledVehicle();

        if (entity instanceof Boat) {
            Boat entityboat = (Boat) entity;

            entityboat.setPaddleState(packet.getLeft(), packet.getRight());
        }

    }

    @Override
    public void onDisconnect(Component reason) {
        // Paper start
        this.onDisconnect(reason, null);
    }
    @Override
    public void onDisconnect(Component reason, @Nullable net.kyori.adventure.text.Component quitMessage) {
        // Paper end
        // CraftBukkit start - Rarely it would send a disconnect line twice
        if (this.processedDisconnect) {
            return;
        } else {
            this.processedDisconnect = true;
        }
        // CraftBukkit end
        ServerGamePacketListenerImpl.LOGGER.info("{} lost connection: {}", this.player.getName().getString(), reason.getString());
        if (!this.waitingForSwitchToConfig) this.removePlayerFromWorld(quitMessage); // Paper // Folia - region threading
        super.onDisconnect(reason, quitMessage); // Paper
    }

    // Paper start
    private void removePlayerFromWorld() {
        this.removePlayerFromWorld(null);
    }

    public boolean hackSwitchingConfig; // Folia - rewrite login process

    private void removePlayerFromWorld(@Nullable net.kyori.adventure.text.Component quitMessage) {
        // Paper end
        this.chatMessageChain.close();
        // CraftBukkit start - Replace vanilla quit message handling with our own.
        /*
        this.server.invalidateStatus();
        this.server.getPlayerList().broadcastSystemMessage(IChatBaseComponent.translatable("multiplayer.player.left", this.player.getDisplayName()).withStyle(EnumChatFormat.YELLOW), false);
        */

        this.player.disconnect();
        // Paper start - Adventure
        quitMessage = quitMessage == null ? this.server.getPlayerList().remove(this.player) : this.server.getPlayerList().remove(this.player, quitMessage); // Paper - pass in quitMessage to fix kick message not being used
        if (!this.hackSwitchingConfig) this.disconnectPos = this.player.chunkPosition(); // Folia - region threading - note: only set after removing, since it can tick the player
        if (!this.hackSwitchingConfig) this.player.serverLevel().chunkSource.addTicketAtLevel(DISCONNECT_TICKET, this.disconnectPos, io.papermc.paper.chunk.system.scheduling.ChunkHolderManager.MAX_TICKET_LEVEL, this.disconnectTicketId); // Folia - region threading - force chunk to be loaded so that the region is not lost
        if ((quitMessage != null) && !quitMessage.equals(net.kyori.adventure.text.Component.empty())) {
            this.server.getPlayerList().broadcastSystemMessage(PaperAdventure.asVanilla(quitMessage), false);
            // Paper end
        }
        // CraftBukkit end
        this.player.getTextFilter().leave();
    }

    public void ackBlockChangesUpTo(int sequence) {
        if (sequence < 0) {
            this.disconnect("Expected packet sequence nr >= 0", org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION); // Paper
            throw new IllegalArgumentException("Expected packet sequence nr >= 0");
        } else {
            this.ackBlockChangesUpTo = Math.max(sequence, this.ackBlockChangesUpTo);
        }
    }

    @Override
    public void handleSetCarriedItem(ServerboundSetCarriedItemPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        if (packet.getSlot() >= 0 && packet.getSlot() < Inventory.getSelectionSize()) {
            if (packet.getSlot() == this.player.getInventory().selected) { return; } // Paper - don't fire itemheldevent when there wasn't a slot change
            PlayerItemHeldEvent event = new PlayerItemHeldEvent(this.getCraftPlayer(), this.player.getInventory().selected, packet.getSlot());
            this.cserver.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                this.send(new ClientboundSetCarriedItemPacket(this.player.getInventory().selected));
                this.player.resetLastActionTime();
                return;
            }
            // CraftBukkit end
            if (this.player.getInventory().selected != packet.getSlot() && this.player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                this.player.stopUsingItem();
            }

            this.player.getInventory().selected = packet.getSlot();
            this.player.resetLastActionTime();
        } else {
            ServerGamePacketListenerImpl.LOGGER.warn("{} tried to set an invalid carried item", this.player.getName().getString());
            this.disconnect("Invalid hotbar selection (Hacking?)", org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION); // CraftBukkit // Paper - kick event cause
        }
    }

    @Override
    public void handleChat(ServerboundChatPacket packet) {
        // CraftBukkit start - async chat
        // SPIGOT-3638
        if (this.server.isStopped()) {
            return;
        }
        // CraftBukkit end
        if (ServerGamePacketListenerImpl.isChatMessageIllegal(packet.message())) {
            // Folia - region threading
            this.disconnect(Component.translatable("multiplayer.disconnect.illegal_characters"), org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_CHARACTERS); // Paper - add cause
            // Folia - region threading
        } else {
            Optional<LastSeenMessages> optional = this.tryHandleChat(packet.message(), packet.timeStamp(), packet.lastSeenMessages());

            if (optional.isPresent()) {
                // this.server.submit(() -> { // CraftBukkit - async chat
                    PlayerChatMessage playerchatmessage;

                    try {
                        playerchatmessage = this.getSignedMessage(packet, (LastSeenMessages) optional.get());
                    } catch (SignedMessageChain.DecodeException signedmessagechain_a) {
                        this.handleMessageDecodeFailure(signedmessagechain_a);
                        return;
                    }

                    CompletableFuture<FilteredText> completablefuture = this.filterTextPacket(playerchatmessage.signedContent());
                    CompletableFuture<ChatDecorator.Result> componentFuture = this.server.getChatDecorator().decorate(this.player, null, playerchatmessage.decoratedContent()); // Paper

                    this.chatMessageChain.append((executor) -> {
                        return CompletableFuture.allOf(completablefuture, componentFuture).thenAcceptAsync((filtered) -> { // Paper
                            PlayerChatMessage playerchatmessage1 = playerchatmessage.filter(completablefuture.join().mask()).withResult(componentFuture.join()); // Paper
                            this.broadcastChatMessage(playerchatmessage1);
                        }, this.server.chatExecutor); // CraftBukkit - async chat
                    });
                // }); // CraftBukkit - async chat
            }

        }
    }

    @Override
    public void handleChatCommand(ServerboundChatCommandPacket packet) {
        if (ServerGamePacketListenerImpl.isChatMessageIllegal(packet.command())) {
            // Folia - region threading
            this.disconnect(Component.translatable("multiplayer.disconnect.illegal_characters"), org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_CHARACTERS); // Paper
            // Folia - region threading
        } else {
            Optional<LastSeenMessages> optional = this.tryHandleChat(packet.command(), packet.timeStamp(), packet.lastSeenMessages());

            if (optional.isPresent()) {
                this.player.getBukkitEntity().taskScheduler.schedule((ServerPlayer player) -> { // Folia - region threading
                    // CraftBukkit start - SPIGOT-7346: Prevent disconnected players from executing commands
                    if (this.player.hasDisconnected()) {
                        return;
                    }
                    // CraftBukkit end
                    this.performChatCommand(packet, (LastSeenMessages) optional.get());
                    this.detectRateSpam("/" + packet.command()); // Spigot
                }, null, 1L); // Folia - region threading
            }

        }
    }

    private void performChatCommand(ServerboundChatCommandPacket packet, LastSeenMessages lastSeenMessages) {
        // CraftBukkit start
        String command = "/" + packet.command();
        if (org.spigotmc.SpigotConfig.logCommands) { // Paper
        ServerGamePacketListenerImpl.LOGGER.info(this.player.getScoreboardName() + " issued server command: " + command);
        } // Paper

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(this.getCraftPlayer(), command, new LazyPlayerSet(this.server));
        this.cserver.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }
        command = event.getMessage().substring(1);

        ParseResults parseresults = this.parseCommand(command);
        // CraftBukkit end

        Map map;

        try {
            map = (packet.command().equals(command)) ? this.collectSignedArguments(packet, SignableCommand.of(parseresults), lastSeenMessages) : Collections.emptyMap(); // CraftBukkit
        } catch (SignedMessageChain.DecodeException signedmessagechain_a) {
            this.handleMessageDecodeFailure(signedmessagechain_a);
            return;
        }

        CommandSigningContext.SignedArguments commandsigningcontext_a = new CommandSigningContext.SignedArguments(map);

        parseresults = Commands.<CommandSourceStack>mapSource(parseresults, (commandlistenerwrapper) -> { // CraftBukkit - decompile error
            return commandlistenerwrapper.withSigningContext(commandsigningcontext_a, this.chatMessageChain);
        });
        this.server.getCommands().performCommand(parseresults, command); // CraftBukkit
    }

    private void handleMessageDecodeFailure(SignedMessageChain.DecodeException exception) {
        if (exception.shouldDisconnect()) {
            this.disconnect(exception.getComponent(), exception.kickCause); // Paper - kick event causes
        } else {
            this.player.sendSystemMessage(exception.getComponent().copy().withStyle(ChatFormatting.RED));
        }

    }

    private Map<String, PlayerChatMessage> collectSignedArguments(ServerboundChatCommandPacket packet, SignableCommand<?> arguments, LastSeenMessages lastSeenMessages) throws SignedMessageChain.DecodeException {
        Map<String, PlayerChatMessage> map = new Object2ObjectOpenHashMap();
        Iterator iterator = arguments.arguments().iterator();

        while (iterator.hasNext()) {
            SignableCommand.Argument<?> signablecommand_a = (SignableCommand.Argument) iterator.next();
            MessageSignature messagesignature = packet.argumentSignatures().get(signablecommand_a.name());
            SignedMessageBody signedmessagebody = new SignedMessageBody(signablecommand_a.value(), packet.timeStamp(), packet.salt(), lastSeenMessages);

            map.put(signablecommand_a.name(), this.signedMessageDecoder.unpack(messagesignature, signedmessagebody));
        }

        return map;
    }

    private ParseResults<CommandSourceStack> parseCommand(String command) {
        com.mojang.brigadier.CommandDispatcher<CommandSourceStack> com_mojang_brigadier_commanddispatcher = this.server.getCommands().getDispatcher();

        return com_mojang_brigadier_commanddispatcher.parse(command, this.player.createCommandSourceStack());
    }

    private Optional<LastSeenMessages> tryHandleChat(String message, Instant timestamp, LastSeenMessages.Update acknowledgment) {
        if (!this.updateChatOrder(timestamp)) {
            ServerGamePacketListenerImpl.LOGGER.warn("{} sent out-of-order chat: '{}': {} > {}", this.player.getName().getString(), message, this.lastChatTimeStamp.get().getEpochSecond(), timestamp.getEpochSecond()); // Paper
            // Folia - region threading
            this.disconnect(Component.translatable("multiplayer.disconnect.out_of_order_chat"), org.bukkit.event.player.PlayerKickEvent.Cause.OUT_OF_ORDER_CHAT); // Paper - kick event causes
            // Folia - region threading
            return Optional.empty();
        } else {
            Optional<LastSeenMessages> optional = this.unpackAndApplyLastSeen(acknowledgment);

            if (this.player.isRemoved() || this.player.getChatVisibility() == ChatVisiblity.HIDDEN) { // CraftBukkit - dead men tell no tales
                this.send(new ClientboundSystemChatPacket(PaperAdventure.asAdventure(Component.translatable("chat.disabled.options").withStyle(ChatFormatting.RED)), false)); // Paper - Adventure
                return Optional.empty();
            } else {
                this.player.resetLastActionTime();
                return optional;
            }
        }
    }

    private Optional<LastSeenMessages> unpackAndApplyLastSeen(LastSeenMessages.Update acknowledgment) {
        LastSeenMessagesValidator lastseenmessagesvalidator = this.lastSeenMessages;

        synchronized (this.lastSeenMessages) {
            Optional<LastSeenMessages> optional = this.lastSeenMessages.applyUpdate(acknowledgment);

            if (optional.isEmpty()) {
                ServerGamePacketListenerImpl.LOGGER.warn("Failed to validate message acknowledgements from {}", this.player.getName().getString());
                this.disconnect(ServerGamePacketListenerImpl.CHAT_VALIDATION_FAILED, org.bukkit.event.player.PlayerKickEvent.Cause.CHAT_VALIDATION_FAILED); // Paper - kick event causes
            }

            return optional;
        }
    }

    private boolean updateChatOrder(Instant timestamp) {
        Instant instant1;

        do {
            instant1 = (Instant) this.lastChatTimeStamp.get();
            if (timestamp.isBefore(instant1)) {
                return false;
            }
        } while (!this.lastChatTimeStamp.compareAndSet(instant1, timestamp));

        return true;
    }

    public static boolean isChatMessageIllegal(String message) {
        for (int i = 0; i < message.length(); ++i) {
            if (!SharedConstants.isAllowedChatCharacter(message.charAt(i))) {
                return true;
            }
        }

        return false;
    }

    // CraftBukkit start - add method
    public void chat(String s, PlayerChatMessage original, boolean async) {
        if (s.isEmpty() || this.player.getChatVisibility() == ChatVisiblity.HIDDEN) {
            return;
        }
        OutgoingChatMessage outgoing = OutgoingChatMessage.create(original);

        if (false && !async && s.startsWith("/")) { // Paper - Don't handle commands in chat logic
            this.handleCommand(s);
        } else if (this.player.getChatVisibility() == ChatVisiblity.SYSTEM) {
            // Do nothing, this is coming from a plugin
        // Paper start
        } else if (true) {
            final ChatProcessor cp = new ChatProcessor(this.server, this.player, original, async);
            cp.process();
            // Paper end
        } else if (false) { // Paper
            Player player = this.getCraftPlayer();
            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(async, player, s, new LazyPlayerSet(this.server));
            String originalFormat = event.getFormat(), originalMessage = event.getMessage();
            this.cserver.getPluginManager().callEvent(event);

            if (false && PlayerChatEvent.getHandlerList().getRegisteredListeners().length != 0) { // Folia - region threading
                // Evil plugins still listening to deprecated event
                final PlayerChatEvent queueEvent = new PlayerChatEvent(player, event.getMessage(), event.getFormat(), event.getRecipients());
                queueEvent.setCancelled(event.isCancelled());
                Waitable waitable = new Waitable() {
                    @Override
                    protected Object evaluate() {
                        org.bukkit.Bukkit.getPluginManager().callEvent(queueEvent);

                        if (queueEvent.isCancelled()) {
                            return null;
                        }

                        String message = String.format(queueEvent.getFormat(), queueEvent.getPlayer().getDisplayName(), queueEvent.getMessage());
                        if (((LazyPlayerSet) queueEvent.getRecipients()).isLazy()) {
                            if (!org.spigotmc.SpigotConfig.bungee && originalFormat.equals(queueEvent.getFormat()) && originalMessage.equals(queueEvent.getMessage()) && queueEvent.getPlayer().getName().equalsIgnoreCase(queueEvent.getPlayer().getDisplayName())) { // Spigot
                                ServerGamePacketListenerImpl.this.server.getPlayerList().broadcastChatMessage(original, ServerGamePacketListenerImpl.this.player, ChatType.bind(ChatType.CHAT, (Entity) ServerGamePacketListenerImpl.this.player));
                                return null;
                            }

                            for (ServerPlayer recipient : ServerGamePacketListenerImpl.this.server.getPlayerList().players) {
                                recipient.getBukkitEntity().sendMessage(ServerGamePacketListenerImpl.this.player.getUUID(), message);
                            }
                        } else {
                            for (Player player : queueEvent.getRecipients()) {
                                player.sendMessage(ServerGamePacketListenerImpl.this.player.getUUID(), message);
                            }
                        }
                        ServerGamePacketListenerImpl.this.server.console.sendMessage(message);

                        return null;
                    }};
                if (async) {
                    this.server.processQueue.add(waitable);
                } else {
                    waitable.run();
                }
                try {
                    waitable.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // This is proper habit for java. If we aren't handling it, pass it on!
                } catch (ExecutionException e) {
                    throw new RuntimeException("Exception processing chat event", e.getCause());
                }
            } else {
                if (event.isCancelled()) {
                    return;
                }

                s = String.format(event.getFormat(), event.getPlayer().getDisplayName(), event.getMessage());
                if (((LazyPlayerSet) event.getRecipients()).isLazy()) {
                    if (!org.spigotmc.SpigotConfig.bungee && originalFormat.equals(event.getFormat()) && originalMessage.equals(event.getMessage()) && event.getPlayer().getName().equalsIgnoreCase(event.getPlayer().getDisplayName())) { // Spigot
                        ServerGamePacketListenerImpl.this.server.getPlayerList().broadcastChatMessage(original, ServerGamePacketListenerImpl.this.player, ChatType.bind(ChatType.CHAT, (Entity) ServerGamePacketListenerImpl.this.player));
                        return;
                    }

                    for (ServerPlayer recipient : this.server.getPlayerList().players) {
                        recipient.getBukkitEntity().sendMessage(ServerGamePacketListenerImpl.this.player.getUUID(), s);
                    }
                } else {
                    for (Player recipient : event.getRecipients()) {
                        recipient.sendMessage(ServerGamePacketListenerImpl.this.player.getUUID(), s);
                    }
                }
                this.server.console.sendMessage(s);
            }
        }
    }

    public void handleCommand(String s) { // Paper - private -> public
        org.spigotmc.AsyncCatcher.catchOp("Command Dispatched Async: " + s); // Paper - Add async catcher
        co.aikar.timings.MinecraftTimings.playerCommandTimer.startTiming(); // Paper
        if ( org.spigotmc.SpigotConfig.logCommands ) // Spigot
        this.LOGGER.info(this.player.getScoreboardName() + " issued server command: " + s);

        CraftPlayer player = this.getCraftPlayer();

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, s, new LazyPlayerSet(this.server));
        this.cserver.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            co.aikar.timings.MinecraftTimings.playerCommandTimer.stopTiming(); // Paper
            return;
        }

        try {
            if (this.cserver.dispatchCommand(event.getPlayer(), event.getMessage().substring(1))) {
                return;
            }
        } catch (org.bukkit.command.CommandException ex) {
            player.sendMessage(org.bukkit.ChatColor.RED + "An internal error occurred while attempting to perform this command");
            java.util.logging.Logger.getLogger(ServerGamePacketListenerImpl.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            return;
        } finally {
            co.aikar.timings.MinecraftTimings.playerCommandTimer.stopTiming(); // Paper
        }
    }
    // CraftBukkit end

    private PlayerChatMessage getSignedMessage(ServerboundChatPacket packet, LastSeenMessages lastSeenMessages) throws SignedMessageChain.DecodeException {
        SignedMessageBody signedmessagebody = new SignedMessageBody(packet.message(), packet.timeStamp(), packet.salt(), lastSeenMessages);

        return this.signedMessageDecoder.unpack(packet.signature(), signedmessagebody);
    }

    private void broadcastChatMessage(PlayerChatMessage message) {
        // CraftBukkit start
        String s = message.signedContent();
        if (s.isEmpty()) {
            ServerGamePacketListenerImpl.LOGGER.warn(this.player.getScoreboardName() + " tried to send an empty message");
        } else if (this.getCraftPlayer().isConversing()) {
            if (true) throw new UnsupportedOperationException(); // Folia - region threading
            final String conversationInput = s;
            this.server.processQueue.add(new Runnable() {
                @Override
                public void run() {
                    ServerGamePacketListenerImpl.this.getCraftPlayer().acceptConversationInput(conversationInput);
                }
            });
        } else if (this.player.getChatVisibility() == ChatVisiblity.SYSTEM) { // Re-add "Command Only" flag check
            this.send(new ClientboundSystemChatPacket(PaperAdventure.asAdventure(Component.translatable("chat.cannotSend").withStyle(ChatFormatting.RED)), false)); // Paper - Adventure
        } else {
            this.chat(s, message, true);
        }
        // this.server.getPlayerList().broadcastChatMessage(playerchatmessage, this.player, ChatMessageType.bind(ChatMessageType.CHAT, (Entity) this.player));
        // CraftBukkit end
        this.detectRateSpam(s); // Spigot
    }

    // Spigot start - spam exclusions
    private void detectRateSpam(String s) {
        // CraftBukkit start - replaced with thread safe throttle
        boolean counted = true;
        for ( String exclude : org.spigotmc.SpigotConfig.spamExclusions )
        {
            if ( exclude != null && s.startsWith( exclude ) )
            {
                counted = false;
                break;
            }
        }
        // Spigot end
        // this.chatSpamTickCount += 20;
        if (counted && this.chatSpamTickCount.addAndGet(20) > 200 && !this.server.getPlayerList().isOp(this.player.getGameProfile())) { // Paper - exclude from SpigotConfig.spamExclusions
            // CraftBukkit end
            this.disconnect(Component.translatable("disconnect.spam"), org.bukkit.event.player.PlayerKickEvent.Cause.SPAM); // Paper - kick event cause
        }

    }

    @Override
    public void handleChatAck(ServerboundChatAckPacket packet) {
        LastSeenMessagesValidator lastseenmessagesvalidator = this.lastSeenMessages;

        synchronized (this.lastSeenMessages) {
            if (!this.lastSeenMessages.applyOffset(packet.offset())) {
                ServerGamePacketListenerImpl.LOGGER.warn("Failed to validate message acknowledgements from {}", this.player.getName().getString());
                this.disconnect(ServerGamePacketListenerImpl.CHAT_VALIDATION_FAILED, org.bukkit.event.player.PlayerKickEvent.Cause.CHAT_VALIDATION_FAILED); // Paper - kick event causes
            }

        }
    }

    @Override
    public void handleAnimate(ServerboundSwingPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        // CraftBukkit start - Raytrace to look for 'rogue armswings'
        float f1 = this.player.getXRot();
        float f2 = this.player.getYRot();
        double d0 = this.player.getX();
        double d1 = this.player.getY() + (double) this.player.getEyeHeight();
        double d2 = this.player.getZ();
        Location origin = new Location(this.player.level().getWorld(), d0, d1, d2, f2, f1);

        double d3 = this.player.gameMode.getGameModeForPlayer() == GameType.CREATIVE ? 5.0D : 4.5D;
        // SPIGOT-5607: Only call interact event if no block or entity is being clicked. Use bukkit ray trace method, because it handles blocks and entities at the same time
        // SPIGOT-7429: Make sure to call PlayerInteractEvent for spectators and non-pickable entities
        org.bukkit.util.RayTraceResult result = this.player.level().getWorld().rayTrace(origin, origin.getDirection(), d3, org.bukkit.FluidCollisionMode.NEVER, false, 0.0, entity -> { // Paper - change raySize from 0.1 to 0.0
            Entity handle = ((CraftEntity) entity).getHandle();
            return entity != this.player.getBukkitEntity() && this.player.getBukkitEntity().canSee(entity) && !handle.isSpectator() && handle.isPickable() && !handle.isPassengerOfSameVehicle(this.player);
        });
        if (result == null) {
            CraftEventFactory.callPlayerInteractEvent(this.player, Action.LEFT_CLICK_AIR, this.player.getInventory().getSelected(), InteractionHand.MAIN_HAND);
        } else { // Paper start - Fix interact event not being called sometimes
            GameType gameType = this.player.gameMode.getGameModeForPlayer();
            if (gameType == GameType.ADVENTURE && result.getHitBlock() != null) {
                CraftEventFactory.callPlayerInteractEvent(this.player, Action.LEFT_CLICK_BLOCK, io.papermc.paper.util.MCUtil.toBlockPosition(result.getHitPosition()), org.bukkit.craftbukkit.block.CraftBlock.blockFaceToNotch(result.getHitBlockFace()), this.player.getInventory().getSelected(), InteractionHand.MAIN_HAND);
            } else if (gameType != GameType.CREATIVE && result.getHitEntity() != null && origin.toVector().distanceSquared(result.getHitPosition()) > 3.0D * 3.0D) {
                CraftEventFactory.callPlayerInteractEvent(this.player, Action.LEFT_CLICK_AIR, this.player.getInventory().getSelected(), InteractionHand.MAIN_HAND);
            }
        } // Paper end

        // Arm swing animation
        io.papermc.paper.event.player.PlayerArmSwingEvent event = new io.papermc.paper.event.player.PlayerArmSwingEvent(this.getCraftPlayer(), packet.getHand() == InteractionHand.MAIN_HAND ? org.bukkit.inventory.EquipmentSlot.HAND : org.bukkit.inventory.EquipmentSlot.OFF_HAND); // Paper
        this.cserver.getPluginManager().callEvent(event);

        if (event.isCancelled()) return;
        // CraftBukkit end
        this.player.swing(packet.getHand());
    }

    @Override
    public void handlePlayerCommand(ServerboundPlayerCommandPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        // CraftBukkit start
        if (this.player.isRemoved()) return;
        switch (packet.getAction()) {
            case PRESS_SHIFT_KEY:
            case RELEASE_SHIFT_KEY:
                PlayerToggleSneakEvent event = new PlayerToggleSneakEvent(this.getCraftPlayer(), packet.getAction() == ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY);
                this.cserver.getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return;
                }
                break;
            case START_SPRINTING:
            case STOP_SPRINTING:
                PlayerToggleSprintEvent e2 = new PlayerToggleSprintEvent(this.getCraftPlayer(), packet.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING);
                this.cserver.getPluginManager().callEvent(e2);

                if (e2.isCancelled()) {
                    return;
                }
                break;
        }
        // CraftBukkit end
        this.player.resetLastActionTime();
        Entity entity;
        PlayerRideableJumping ijumpable;

        switch (packet.getAction()) {
            case PRESS_SHIFT_KEY:
                this.player.setShiftKeyDown(true);

                // Paper start - Hang on!
                if (this.player.level().paperConfig().entities.behavior.parrotsAreUnaffectedByPlayerMovement) {
                    this.player.removeEntitiesOnShoulder();
                }
                // Paper end

                break;
            case RELEASE_SHIFT_KEY:
                this.player.setShiftKeyDown(false);
                break;
            case START_SPRINTING:
                this.player.setSprinting(true);
                break;
            case STOP_SPRINTING:
                this.player.setSprinting(false);
                break;
            case STOP_SLEEPING:
                if (this.player.isSleeping()) {
                    this.player.stopSleepInBed(false, true);
                    this.awaitingPositionFromClient = this.player.position();
                }
                break;
            case START_RIDING_JUMP:
                entity = this.player.getControlledVehicle();
                if (entity instanceof PlayerRideableJumping) {
                    ijumpable = (PlayerRideableJumping) entity;
                    int i = packet.getData();

                    if (ijumpable.canJump() && i > 0) {
                        ijumpable.handleStartJump(i);
                    }
                }
                break;
            case STOP_RIDING_JUMP:
                entity = this.player.getControlledVehicle();
                if (entity instanceof PlayerRideableJumping) {
                    ijumpable = (PlayerRideableJumping) entity;
                    ijumpable.handleStopJump();
                }
                break;
            case OPEN_INVENTORY:
                entity = this.player.getVehicle();
                if (entity instanceof HasCustomInventoryScreen) {
                    HasCustomInventoryScreen hascustominventoryscreen = (HasCustomInventoryScreen) entity;

                    hascustominventoryscreen.openCustomInventoryScreen(this.player);
                }
                break;
            case START_FALL_FLYING:
                if (!this.player.tryToStartFallFlying()) {
                    this.player.stopFallFlying();
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid client command!");
        }

    }

    public void addPendingMessage(PlayerChatMessage message) {
        MessageSignature messagesignature = message.signature();

        if (messagesignature != null) {
            this.messageSignatureCache.push(message);
            LastSeenMessagesValidator lastseenmessagesvalidator = this.lastSeenMessages;
            int i;

            synchronized (this.lastSeenMessages) {
                this.lastSeenMessages.addPending(messagesignature);
                i = this.lastSeenMessages.trackedMessagesCount();
            }

            if (i > 4096) {
                this.disconnect(Component.translatable("multiplayer.disconnect.too_many_pending_chats"), org.bukkit.event.player.PlayerKickEvent.Cause.TOO_MANY_PENDING_CHATS); // Paper - kick event cause
            }

        }
    }

    public void sendPlayerChatMessage(PlayerChatMessage message, ChatType.Bound params) {
        // CraftBukkit start - SPIGOT-7262: if hidden we have to send as disguised message. Query whether we should send at all (but changing this may not be expected).
        if (!this.getCraftPlayer().canSee(message.link().sender())) {
            this.sendDisguisedChatMessage(message.decoratedContent(), params);
            return;
        }
        // CraftBukkit end
        this.send(new ClientboundPlayerChatPacket(message.link().sender(), message.link().index(), message.signature(), message.signedBody().pack(this.messageSignatureCache), message.unsignedContent(), message.filterMask(), params.toNetwork(this.player.level().registryAccess())));
        this.addPendingMessage(message);
    }

    public void sendDisguisedChatMessage(Component message, ChatType.Bound params) {
        this.send(new ClientboundDisguisedChatPacket(message, params.toNetwork(this.player.level().registryAccess())));
    }

    public SocketAddress getRemoteAddress() {
        return this.connection.getRemoteAddress();
    }

    // Spigot Start
    public SocketAddress getRawAddress()
    {
        // Paper start - this can be nullable in the case of a Unix domain socket, so if it is, fake something
        if (connection.channel.remoteAddress() == null) {
            return new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0);
        }
        // Paper end
        return this.connection.channel.remoteAddress();
    }
    // Spigot End

    public void switchToConfig() {
        // Folia start - rewrite login process
        io.papermc.paper.util.TickThread.ensureTickThread(this.player, "Cannot switch config off-main");
        if (io.papermc.paper.threadedregions.RegionizedServer.isGlobalTickThread()) {
            throw new IllegalStateException("Cannot switch config while on global tick thread");
        }
        // Folia end - rewrite login process
        // Folia start - rewrite login process - fix bad ordering of this field write - move after removed from world
        // the field write ordering is bad as it allows the client to send the response packet before the player is
        // removed from the world
        // Folia end - rewrite login process - fix bad ordering of this field write - move after removed from world
        try { // Folia - rewrite login process - move connection ownership to global region
            this.hackSwitchingConfig = true; // Folia - rewrite login process - avoid adding logout ticket here and retiring scheduler
        this.removePlayerFromWorld();
        } finally { // Folia start - rewrite login process - move connection ownership to global region
            io.papermc.paper.threadedregions.RegionizedWorldData worldData = this.player.serverLevel().getCurrentWorldData();
            worldData.connections.remove(this.connection);
            // once waitingForSwitchToConfig is set, the global tick thread will own the connection
        } // Folia end - rewrite login process - move connection ownership to global region
        this.waitingForSwitchToConfig = true; // Folia - rewrite login process - fix bad ordering of this field write - moved down
        this.send(new ClientboundStartConfigurationPacket());
    }

    @Override
    public void handlePingRequest(ServerboundPingRequestPacket packet) {
        this.connection.send(new ClientboundPongResponsePacket(packet.getTime()));
    }

    @Override
    public void handleInteract(ServerboundInteractPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        final ServerLevel worldserver = this.player.serverLevel();
        final Entity entity = packet.getTarget(worldserver);
        // Spigot Start
        if ( entity == this.player && !this.player.isSpectator() )
        {
            this.disconnect( "Cannot interact with self!" , org.bukkit.event.player.PlayerKickEvent.Cause.SELF_INTERACTION ); // Paper - kick event cause
            return;
        }
        // Spigot End

        this.player.resetLastActionTime();
        this.player.setShiftKeyDown(packet.isUsingSecondaryAction());
        if (io.papermc.paper.util.TickThread.isTickThreadFor(entity) && entity != null) { // Folia - region threading - do not allow interaction of entities outside the current region
            if (!worldserver.getWorldBorder().isWithinBounds(entity.blockPosition())) {
                return;
            }

            AABB axisalignedbb = entity.getBoundingBox();

            if (axisalignedbb.distanceToSqr(this.player.getEyePosition()) < ServerGamePacketListenerImpl.MAX_INTERACTION_DISTANCE) {
                packet.dispatch(new ServerboundInteractPacket.Handler() {
                    private void performInteraction(InteractionHand enumhand, ServerGamePacketListenerImpl.EntityInteraction playerconnection_a, PlayerInteractEntityEvent event) { // CraftBukkit
                        ItemStack itemstack = ServerGamePacketListenerImpl.this.player.getItemInHand(enumhand);

                        if (itemstack.isItemEnabled(worldserver.enabledFeatures())) {
                            ItemStack itemstack1 = itemstack.copy();
                            // CraftBukkit start
                            ItemStack itemInHand = ServerGamePacketListenerImpl.this.player.getItemInHand(enumhand);
                            boolean triggerLeashUpdate = itemInHand != null && itemInHand.getItem() == Items.LEAD && entity instanceof Mob;
                            Item origItem = ServerGamePacketListenerImpl.this.player.getInventory().getSelected() == null ? null : ServerGamePacketListenerImpl.this.player.getInventory().getSelected().getItem();

                            ServerGamePacketListenerImpl.this.cserver.getPluginManager().callEvent(event);

                            // Entity in bucket - SPIGOT-4048 and SPIGOT-6859a
                            if ((entity instanceof Bucketable && entity instanceof LivingEntity && origItem != null && origItem.asItem() == Items.WATER_BUCKET) && (event.isCancelled() || ServerGamePacketListenerImpl.this.player.getInventory().getSelected() == null || ServerGamePacketListenerImpl.this.player.getInventory().getSelected().getItem() != origItem)) {
                                entity.getEntityData().resendPossiblyDesyncedEntity(player); // Paper - The entire mob gets deleted, so resend it.
                                ServerGamePacketListenerImpl.this.player.containerMenu.sendAllDataToRemote();
                            }

                            if (triggerLeashUpdate && (event.isCancelled() || ServerGamePacketListenerImpl.this.player.getInventory().getSelected() == null || ServerGamePacketListenerImpl.this.player.getInventory().getSelected().getItem() != origItem)) {
                                // Refresh the current leash state
                                ServerGamePacketListenerImpl.this.send(new ClientboundSetEntityLinkPacket(entity, ((Mob) entity).getLeashHolder()));
                            }

                            if (event.isCancelled() || ServerGamePacketListenerImpl.this.player.getInventory().getSelected() == null || ServerGamePacketListenerImpl.this.player.getInventory().getSelected().getItem() != origItem) {
                                // Refresh the current entity metadata
                                entity.getEntityData().refresh(ServerGamePacketListenerImpl.this.player);
                                // SPIGOT-7136 - Allays
                                if (entity instanceof Allay allay) { // Paper
                                    ServerGamePacketListenerImpl.this.send(new ClientboundSetEquipmentPacket(entity.getId(), Arrays.stream(net.minecraft.world.entity.EquipmentSlot.values()).map((slot) -> Pair.of(slot, allay.stripMeta(allay.getItemBySlot(slot), true))).collect(Collectors.toList()))); // Paper - remove unnecessary item meta
                                    ServerGamePacketListenerImpl.this.player.containerMenu.sendAllDataToRemote();
                                }
                            }

                            if (event.isCancelled()) {
                            ServerGamePacketListenerImpl.this.player.containerMenu.sendAllDataToRemote(); // Paper - Refresh player inventory
                                return;
                            }
                            // CraftBukkit end
                            InteractionResult enuminteractionresult = playerconnection_a.run(ServerGamePacketListenerImpl.this.player, entity, enumhand);

                            // CraftBukkit start
                            if (!itemInHand.isEmpty() && itemInHand.getCount() <= -1) {
                                ServerGamePacketListenerImpl.this.player.containerMenu.sendAllDataToRemote();
                            }
                            // CraftBukkit end

                            if (enuminteractionresult.consumesAction()) {
                                CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.trigger(ServerGamePacketListenerImpl.this.player, itemstack1, entity);
                                if (enuminteractionresult.shouldSwing()) {
                                    ServerGamePacketListenerImpl.this.player.swing(enumhand, true);
                                }
                            }

                        }
                    }

                    @Override
                    public void onInteraction(InteractionHand hand) {
                        this.performInteraction(hand, net.minecraft.world.entity.player.Player::interactOn, new PlayerInteractEntityEvent(ServerGamePacketListenerImpl.this.getCraftPlayer(), entity.getBukkitEntity(), (hand == InteractionHand.OFF_HAND) ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND)); // CraftBukkit
                    }

                    @Override
                    public void onInteraction(InteractionHand hand, Vec3 pos) {
                        this.performInteraction(hand, (entityplayer, entity1, enumhand1) -> {
                            return entity1.interactAt(entityplayer, pos, enumhand1);
                        }, new PlayerInteractAtEntityEvent(ServerGamePacketListenerImpl.this.getCraftPlayer(), entity.getBukkitEntity(), new org.bukkit.util.Vector(pos.x, pos.y, pos.z), (hand == InteractionHand.OFF_HAND) ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND)); // CraftBukkit
                    }

                    @Override
                    public void onAttack() {
                        // CraftBukkit
                        if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb) && !(entity instanceof AbstractArrow) && (entity != ServerGamePacketListenerImpl.this.player || ServerGamePacketListenerImpl.this.player.isSpectator())) {
                            ItemStack itemstack = ServerGamePacketListenerImpl.this.player.getItemInHand(InteractionHand.MAIN_HAND);

                            if (itemstack.isItemEnabled(worldserver.enabledFeatures())) {
                                ServerGamePacketListenerImpl.this.player.attack(entity);
                                // CraftBukkit start
                                if (!itemstack.isEmpty() && itemstack.getCount() <= -1) {
                                    ServerGamePacketListenerImpl.this.player.containerMenu.sendAllDataToRemote();
                                }
                                // CraftBukkit end
                            }
                        } else {
                            ServerGamePacketListenerImpl.this.disconnect(Component.translatable("multiplayer.disconnect.invalid_entity_attacked"),  org.bukkit.event.player.PlayerKickEvent.Cause.INVALID_ENTITY_ATTACKED); // Paper - add cause
                            ServerGamePacketListenerImpl.LOGGER.warn("Player {} tried to attack an invalid entity", ServerGamePacketListenerImpl.this.player.getName().getString());
                        }
                    }
                });
            }
        }
        // Paper start - PlayerUseUnknownEntityEvent
        else {
            packet.dispatch(new net.minecraft.network.protocol.game.ServerboundInteractPacket.Handler() {
                @Override
                public void onInteraction(net.minecraft.world.InteractionHand hand) {
                    ServerGamePacketListenerImpl.this.callPlayerUseUnknownEntityEvent(packet, hand, null);
                }

                @Override
                public void onInteraction(net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.Vec3 pos) {
                    ServerGamePacketListenerImpl.this.callPlayerUseUnknownEntityEvent(packet, hand, pos);
                }

                @Override
                public void onAttack() {
                    ServerGamePacketListenerImpl.this.callPlayerUseUnknownEntityEvent(packet, net.minecraft.world.InteractionHand.MAIN_HAND, null);
                }
            });
        }

    }

    private void callPlayerUseUnknownEntityEvent(ServerboundInteractPacket packet, InteractionHand hand, @Nullable net.minecraft.world.phys.Vec3 vector) {
        this.cserver.getPluginManager().callEvent(new com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent(
            this.getCraftPlayer(),
            packet.getEntityId(),
            packet.isAttack(),
            hand == InteractionHand.MAIN_HAND ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND,
            vector != null ? new org.bukkit.util.Vector(vector.x, vector.y, vector.z) : null)
        );
    }
    // Paper end - PlayerUseUnknownEntityEvent

    @Override
    public void handleClientCommand(ServerboundClientCommandPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        this.player.resetLastActionTime();
        ServerboundClientCommandPacket.Action packetplayinclientcommand_enumclientcommand = packet.getAction();

        switch (packetplayinclientcommand_enumclientcommand) {
            case PERFORM_RESPAWN:
                if (this.player.wonGame) {
                    // Folia start - region threading
                    if (true) {
                        this.player.exitEndCredits();
                        return;
                    }
                    // Folia end - region threading
                    this.player.wonGame = false;
                    this.player = this.server.getPlayerList().respawn(this.player, this.server.getLevel(this.player.getRespawnDimension()), true, null, true, RespawnReason.END_PORTAL, org.bukkit.event.player.PlayerRespawnEvent.RespawnFlag.END_PORTAL); // Paper - add isEndCreditsRespawn argument
                    CriteriaTriggers.CHANGED_DIMENSION.trigger(this.player, Level.END, Level.OVERWORLD);
                } else {
                    if (this.player.getHealth() > 0.0F) {
                        return;
                    }

                    // Folia start - region threading
                    if (true) {
                        this.player.respawn((ServerPlayer player) -> {
                            if (ServerGamePacketListenerImpl.this.server.isHardcore()) {
                                player.setGameMode(GameType.SPECTATOR, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.HARDCORE_DEATH, null); // Paper
                                ((GameRules.BooleanValue) player.level().getGameRules().getRule(GameRules.RULE_SPECTATORSGENERATECHUNKS)).set(false, player.serverLevel()); // Paper
                            }
                        });
                        return;
                    }
                    // Folia end - region threading

                    this.player = this.server.getPlayerList().respawn(this.player, false, RespawnReason.DEATH);
                    if (this.server.isHardcore()) {
                        this.player.setGameMode(GameType.SPECTATOR, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.HARDCORE_DEATH, null); // Paper
                        ((GameRules.BooleanValue) this.player.level().getGameRules().getRule(GameRules.RULE_SPECTATORSGENERATECHUNKS)).set(false, this.player.serverLevel()); // Paper
                    }
                }
                break;
            case REQUEST_STATS:
                this.player.getStats().sendStats(this.player);
        }

    }

    @Override
    public void handleContainerClose(ServerboundContainerClosePacket packet) {
        // Paper start
        this.handleContainerClose(packet, InventoryCloseEvent.Reason.PLAYER);
    }
    public void handleContainerClose(ServerboundContainerClosePacket packet, InventoryCloseEvent.Reason reason) {
        // Paper end
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());

        if (this.player.isImmobile()) return; // CraftBukkit
        CraftEventFactory.handleInventoryCloseEvent(this.player, reason); // CraftBukkit // Paper

        this.player.doCloseContainer();
    }

    @Override
    public void handleContainerClick(ServerboundContainerClickPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        if (this.player.containerMenu.containerId == packet.getContainerId() && this.player.containerMenu.stillValid(this.player)) { // CraftBukkit
            boolean cancelled = this.player.isSpectator(); // CraftBukkit - see below if
            if (false/*this.player.isSpectator()*/) { // CraftBukkit
                this.player.containerMenu.sendAllDataToRemote();
            } else if (!this.player.containerMenu.stillValid(this.player)) {
                ServerGamePacketListenerImpl.LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            } else {
                int i = packet.getSlotNum();

                if (!this.player.containerMenu.isValidSlotIndex(i)) {
                    ServerGamePacketListenerImpl.LOGGER.debug("Player {} clicked invalid slot index: {}, available slots: {}", new Object[]{this.player.getName(), i, this.player.containerMenu.slots.size()});
                } else {
                    boolean flag = packet.getStateId() != this.player.containerMenu.getStateId();

                    this.player.containerMenu.suppressRemoteUpdates();
                    // CraftBukkit start - Call InventoryClickEvent
                    if (packet.getSlotNum() < -1 && packet.getSlotNum() != -999) {
                        return;
                    }

                    InventoryView inventory = this.player.containerMenu.getBukkitView();
                    SlotType type = inventory.getSlotType(packet.getSlotNum());

                    InventoryClickEvent event;
                    ClickType click = ClickType.UNKNOWN;
                    InventoryAction action = InventoryAction.UNKNOWN;

                    ItemStack itemstack = ItemStack.EMPTY;

                    switch (packet.getClickType()) {
                        case PICKUP:
                            if (packet.getButtonNum() == 0) {
                                click = ClickType.LEFT;
                            } else if (packet.getButtonNum() == 1) {
                                click = ClickType.RIGHT;
                            }
                            if (packet.getButtonNum() == 0 || packet.getButtonNum() == 1) {
                                action = InventoryAction.NOTHING; // Don't want to repeat ourselves
                                if (packet.getSlotNum() == -999) {
                                    if (!this.player.containerMenu.getCarried().isEmpty()) {
                                        action = packet.getButtonNum() == 0 ? InventoryAction.DROP_ALL_CURSOR : InventoryAction.DROP_ONE_CURSOR;
                                    }
                                } else if (packet.getSlotNum() < 0)  {
                                    action = InventoryAction.NOTHING;
                                } else {
                                    Slot slot = this.player.containerMenu.getSlot(packet.getSlotNum());
                                    if (slot != null) {
                                        ItemStack clickedItem = slot.getItem();
                                        ItemStack cursor = this.player.containerMenu.getCarried();
                                        if (clickedItem.isEmpty()) {
                                            if (!cursor.isEmpty()) {
                                                action = packet.getButtonNum() == 0 ? InventoryAction.PLACE_ALL : InventoryAction.PLACE_ONE;
                                            }
                                        } else if (slot.mayPickup(this.player)) {
                                            if (cursor.isEmpty()) {
                                                action = packet.getButtonNum() == 0 ? InventoryAction.PICKUP_ALL : InventoryAction.PICKUP_HALF;
                                            } else if (slot.mayPlace(cursor)) {
                                                if (ItemStack.isSameItemSameTags(clickedItem, cursor)) {
                                                    int toPlace = packet.getButtonNum() == 0 ? cursor.getCount() : 1;
                                                    toPlace = Math.min(toPlace, clickedItem.getMaxStackSize() - clickedItem.getCount());
                                                    toPlace = Math.min(toPlace, slot.container.getMaxStackSize() - clickedItem.getCount());
                                                    if (toPlace == 1) {
                                                        action = InventoryAction.PLACE_ONE;
                                                    } else if (toPlace == cursor.getCount()) {
                                                        action = InventoryAction.PLACE_ALL;
                                                    } else if (toPlace < 0) {
                                                        action = toPlace != -1 ? InventoryAction.PICKUP_SOME : InventoryAction.PICKUP_ONE; // this happens with oversized stacks
                                                    } else if (toPlace != 0) {
                                                        action = InventoryAction.PLACE_SOME;
                                                    }
                                                } else if (cursor.getCount() <= slot.getMaxStackSize()) {
                                                    action = InventoryAction.SWAP_WITH_CURSOR;
                                                }
                                            } else if (ItemStack.isSameItemSameTags(cursor, clickedItem)) {
                                                if (clickedItem.getCount() >= 0) {
                                                    if (clickedItem.getCount() + cursor.getCount() <= cursor.getMaxStackSize()) {
                                                        // As of 1.5, this is result slots only
                                                        action = InventoryAction.PICKUP_ALL;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            break;
                        // TODO check on updates
                        case QUICK_MOVE:
                            if (packet.getButtonNum() == 0) {
                                click = ClickType.SHIFT_LEFT;
                            } else if (packet.getButtonNum() == 1) {
                                click = ClickType.SHIFT_RIGHT;
                            }
                            if (packet.getButtonNum() == 0 || packet.getButtonNum() == 1) {
                                if (packet.getSlotNum() < 0) {
                                    action = InventoryAction.NOTHING;
                                } else {
                                    Slot slot = this.player.containerMenu.getSlot(packet.getSlotNum());
                                    if (slot != null && slot.mayPickup(this.player) && slot.hasItem()) {
                                        action = InventoryAction.MOVE_TO_OTHER_INVENTORY;
                                    } else {
                                        action = InventoryAction.NOTHING;
                                    }
                                }
                            }
                            break;
                        case SWAP:
                            if ((packet.getButtonNum() >= 0 && packet.getButtonNum() < 9) || packet.getButtonNum() == 40) {
                                // Paper start
                                if (packet.getSlotNum() < 0) {
                                    action = InventoryAction.NOTHING;
                                    break;
                                }
                                // Paper end
                                click = (packet.getButtonNum() == 40) ? ClickType.SWAP_OFFHAND : ClickType.NUMBER_KEY;
                                Slot clickedSlot = this.player.containerMenu.getSlot(packet.getSlotNum());
                                if (clickedSlot.mayPickup(this.player)) {
                                    ItemStack hotbar = this.player.getInventory().getItem(packet.getButtonNum());
                                    boolean canCleanSwap = hotbar.isEmpty() || (clickedSlot.container == this.player.getInventory() && clickedSlot.mayPlace(hotbar)); // the slot will accept the hotbar item
                                    if (clickedSlot.hasItem()) {
                                        if (canCleanSwap) {
                                            action = InventoryAction.HOTBAR_SWAP;
                                        } else {
                                            action = InventoryAction.HOTBAR_MOVE_AND_READD;
                                        }
                                    } else if (!clickedSlot.hasItem() && !hotbar.isEmpty() && clickedSlot.mayPlace(hotbar)) {
                                        action = InventoryAction.HOTBAR_SWAP;
                                    } else {
                                        action = InventoryAction.NOTHING;
                                    }
                                } else {
                                    action = InventoryAction.NOTHING;
                                }
                            }
                            break;
                        case CLONE:
                            if (packet.getButtonNum() == 2) {
                                click = ClickType.MIDDLE;
                                if (packet.getSlotNum() < 0) {
                                    action = InventoryAction.NOTHING;
                                } else {
                                    Slot slot = this.player.containerMenu.getSlot(packet.getSlotNum());
                                    if (slot != null && slot.hasItem() && this.player.getAbilities().instabuild && this.player.containerMenu.getCarried().isEmpty()) {
                                        action = InventoryAction.CLONE_STACK;
                                    } else {
                                        action = InventoryAction.NOTHING;
                                    }
                                }
                            } else {
                                click = ClickType.UNKNOWN;
                                action = InventoryAction.UNKNOWN;
                            }
                            break;
                        case THROW:
                            if (packet.getSlotNum() >= 0) {
                                if (packet.getButtonNum() == 0) {
                                    click = ClickType.DROP;
                                    Slot slot = this.player.containerMenu.getSlot(packet.getSlotNum());
                                    if (slot != null && slot.hasItem() && slot.mayPickup(this.player) && !slot.getItem().isEmpty() && slot.getItem().getItem() != Item.byBlock(Blocks.AIR)) {
                                        action = InventoryAction.DROP_ONE_SLOT;
                                    } else {
                                        action = InventoryAction.NOTHING;
                                    }
                                } else if (packet.getButtonNum() == 1) {
                                    click = ClickType.CONTROL_DROP;
                                    Slot slot = this.player.containerMenu.getSlot(packet.getSlotNum());
                                    if (slot != null && slot.hasItem() && slot.mayPickup(this.player) && !slot.getItem().isEmpty() && slot.getItem().getItem() != Item.byBlock(Blocks.AIR)) {
                                        action = InventoryAction.DROP_ALL_SLOT;
                                    } else {
                                        action = InventoryAction.NOTHING;
                                    }
                                }
                            } else {
                                // Sane default (because this happens when they are holding nothing. Don't ask why.)
                                click = ClickType.LEFT;
                                if (packet.getButtonNum() == 1) {
                                    click = ClickType.RIGHT;
                                }
                                action = InventoryAction.NOTHING;
                            }
                            break;
                        case QUICK_CRAFT:
                            this.player.containerMenu.clicked(packet.getSlotNum(), packet.getButtonNum(), packet.getClickType(), this.player);
                            break;
                        case PICKUP_ALL:
                            click = ClickType.DOUBLE_CLICK;
                            action = InventoryAction.NOTHING;
                            if (packet.getSlotNum() >= 0 && !this.player.containerMenu.getCarried().isEmpty()) {
                                ItemStack cursor = this.player.containerMenu.getCarried();
                                action = InventoryAction.NOTHING;
                                // Quick check for if we have any of the item
                                if (inventory.getTopInventory().contains(CraftMagicNumbers.getMaterial(cursor.getItem())) || inventory.getBottomInventory().contains(CraftMagicNumbers.getMaterial(cursor.getItem()))) {
                                    action = InventoryAction.COLLECT_TO_CURSOR;
                                }
                            }
                            break;
                        default:
                            break;
                    }

                    if (packet.getClickType() != net.minecraft.world.inventory.ClickType.QUICK_CRAFT) {
                        if (click == ClickType.NUMBER_KEY) {
                            event = new InventoryClickEvent(inventory, type, packet.getSlotNum(), click, action, packet.getButtonNum());
                        } else {
                            event = new InventoryClickEvent(inventory, type, packet.getSlotNum(), click, action);
                        }

                        org.bukkit.inventory.Inventory top = inventory.getTopInventory();
                        if (packet.getSlotNum() == 0 && top instanceof CraftingInventory) {
                            org.bukkit.inventory.Recipe recipe = ((CraftingInventory) top).getRecipe();
                            if (recipe != null) {
                                if (click == ClickType.NUMBER_KEY) {
                                    event = new CraftItemEvent(recipe, inventory, type, packet.getSlotNum(), click, action, packet.getButtonNum());
                                } else {
                                    event = new CraftItemEvent(recipe, inventory, type, packet.getSlotNum(), click, action);
                                }
                            }
                        }

                        if (packet.getSlotNum() == 3 && top instanceof SmithingInventory) {
                            org.bukkit.inventory.ItemStack result = ((SmithingInventory) top).getResult();
                            if (result != null) {
                                if (click == ClickType.NUMBER_KEY) {
                                    event = new SmithItemEvent(inventory, type, packet.getSlotNum(), click, action, packet.getButtonNum());
                                } else {
                                    event = new SmithItemEvent(inventory, type, packet.getSlotNum(), click, action);
                                }
                            }
                        }

                        event.setCancelled(cancelled);
                        AbstractContainerMenu oldContainer = this.player.containerMenu; // SPIGOT-1224
                        this.cserver.getPluginManager().callEvent(event);
                        if (this.player.containerMenu != oldContainer) {
                            return;
                        }

                        switch (event.getResult()) {
                            case ALLOW:
                            case DEFAULT:
                                this.player.containerMenu.clicked(i, packet.getButtonNum(), packet.getClickType(), this.player);
                                break;
                            case DENY:
                                /* Needs enum constructor in InventoryAction
                                if (action.modifiesOtherSlots()) {

                                } else {
                                    if (action.modifiesCursor()) {
                                        this.player.playerConnection.sendPacket(new Packet103SetSlot(-1, -1, this.player.inventory.getCarried()));
                                    }
                                    if (action.modifiesClicked()) {
                                        this.player.playerConnection.sendPacket(new Packet103SetSlot(this.player.activeContainer.windowId, packet102windowclick.slot, this.player.activeContainer.getSlot(packet102windowclick.slot).getItem()));
                                    }
                                }*/
                                switch (action) {
                                    // Modified other slots
                                    case PICKUP_ALL:
                                    case MOVE_TO_OTHER_INVENTORY:
                                    case HOTBAR_MOVE_AND_READD:
                                    case HOTBAR_SWAP:
                                    case COLLECT_TO_CURSOR:
                                    case UNKNOWN:
                                        this.player.containerMenu.sendAllDataToRemote();
                                        break;
                                    // Modified cursor and clicked
                                    case PICKUP_SOME:
                                    case PICKUP_HALF:
                                    case PICKUP_ONE:
                                    case PLACE_ALL:
                                    case PLACE_SOME:
                                    case PLACE_ONE:
                                    case SWAP_WITH_CURSOR:
                                        this.player.connection.send(new ClientboundContainerSetSlotPacket(-1, -1, this.player.inventoryMenu.incrementStateId(), this.player.containerMenu.getCarried()));
                                        this.player.connection.send(new ClientboundContainerSetSlotPacket(this.player.containerMenu.containerId, this.player.inventoryMenu.incrementStateId(), packet.getSlotNum(), this.player.containerMenu.getSlot(packet.getSlotNum()).getItem()));
                                        break;
                                    // Modified clicked only
                                    case DROP_ALL_SLOT:
                                    case DROP_ONE_SLOT:
                                        this.player.connection.send(new ClientboundContainerSetSlotPacket(this.player.containerMenu.containerId, this.player.inventoryMenu.incrementStateId(), packet.getSlotNum(), this.player.containerMenu.getSlot(packet.getSlotNum()).getItem()));
                                        break;
                                    // Modified cursor only
                                    case DROP_ALL_CURSOR:
                                    case DROP_ONE_CURSOR:
                                    case CLONE_STACK:
                                        this.player.connection.send(new ClientboundContainerSetSlotPacket(-1, -1, this.player.inventoryMenu.incrementStateId(), this.player.containerMenu.getCarried()));
                                        break;
                                    // Nothing
                                    case NOTHING:
                                        break;
                                }
                        }

                        if (event instanceof CraftItemEvent || event instanceof SmithItemEvent) {
                            // Need to update the inventory on crafting to
                            // correctly support custom recipes
                            this.player.containerMenu.sendAllDataToRemote();
                        }
                    }
                    // CraftBukkit end
                    ObjectIterator objectiterator = Int2ObjectMaps.fastIterable(packet.getChangedSlots()).iterator();

                    while (objectiterator.hasNext()) {
                        Entry<ItemStack> entry = (Entry) objectiterator.next();

                        this.player.containerMenu.setRemoteSlotNoCopy(entry.getIntKey(), (ItemStack) entry.getValue());
                    }

                    this.player.containerMenu.setRemoteCarried(packet.getCarriedItem());
                    this.player.containerMenu.resumeRemoteUpdates();
                    if (flag) {
                        this.player.containerMenu.broadcastFullState();
                    } else {
                        this.player.containerMenu.broadcastChanges();
                    }

                }
            }
        }
    }

    @Override
    public void handlePlaceRecipe(ServerboundPlaceRecipePacket packet) {
        // Paper start
        if (!org.bukkit.Bukkit.isPrimaryThread()) {
            if (this.recipeSpamPackets.addAndGet(io.papermc.paper.configuration.GlobalConfiguration.get().spamLimiter.recipeSpamIncrement) > io.papermc.paper.configuration.GlobalConfiguration.get().spamLimiter.recipeSpamLimit) {
                this.disconnect(net.minecraft.network.chat.Component.translatable("disconnect.spam", new Object[0]), org.bukkit.event.player.PlayerKickEvent.Cause.SPAM); // Paper - kick event cause // Folia - region threading
                return;
            }
        }
        // Paper end
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        this.player.resetLastActionTime();
        if (!this.player.isSpectator() && this.player.containerMenu.containerId == packet.getContainerId() && this.player.containerMenu instanceof RecipeBookMenu) {
            if (!this.player.containerMenu.stillValid(this.player)) {
                ServerGamePacketListenerImpl.LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            } else {
                // Paper start
                ResourceLocation recipeName = packet.getRecipe();
                boolean makeAll = packet.isShiftDown();
                com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent paperEvent = new com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent(
                    this.player.getBukkitEntity(), org.bukkit.craftbukkit.util.CraftNamespacedKey.fromMinecraft(recipeName), makeAll
                );
                if (!paperEvent.callEvent()) {
                    return;
                }
                recipeName = CraftNamespacedKey.toMinecraft(paperEvent.getRecipe());
                makeAll = paperEvent.isMakeAll();
                if (org.bukkit.event.player.PlayerRecipeBookClickEvent.getHandlerList().getRegisteredListeners().length > 0) {
                // Paper end
                // CraftBukkit start - implement PlayerRecipeBookClickEvent
                org.bukkit.inventory.Recipe recipe = this.cserver.getRecipe(CraftNamespacedKey.fromMinecraft(recipeName)); // Paper
                if (recipe == null) {
                    return;
                }
                // Paper start
                org.bukkit.event.player.PlayerRecipeBookClickEvent event = CraftEventFactory.callRecipeBookClickEvent(this.player, recipe, makeAll);
                recipeName = CraftNamespacedKey.toMinecraft(((org.bukkit.Keyed) event.getRecipe()).getKey());
                makeAll = event.isShiftClick();
                }
                if (!(this.player.containerMenu instanceof RecipeBookMenu<?> recipeBookMenu)) {
                    return;
                }
                // Paper end

                // Cast to keyed should be safe as the recipe will never be a MerchantRecipe.
                // Paper start
                final boolean finalMakeAll = makeAll;
                this.server.getRecipeManager().byKey(recipeName).ifPresent((recipeholder) -> {
                    recipeBookMenu.handlePlacement(finalMakeAll, recipeholder, this.player);
                    // Paper end
                });
                // CraftBukkit end
            }
        }
    }

    @Override
    public void handleContainerButtonClick(ServerboundContainerButtonClickPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        if (this.player.containerMenu.containerId == packet.getContainerId() && !this.player.isSpectator()) {
            if (!this.player.containerMenu.stillValid(this.player)) {
                ServerGamePacketListenerImpl.LOGGER.debug("Player {} interacted with invalid menu {}", this.player, this.player.containerMenu);
            } else {
                boolean flag = this.player.containerMenu.clickMenuButton(this.player, packet.getButtonId());

                if (flag) {
                    this.player.containerMenu.broadcastChanges();
                }

            }
        }
    }

    @Override
    public void handleSetCreativeModeSlot(ServerboundSetCreativeModeSlotPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.gameMode.isCreative()) {
            boolean flag = packet.getSlotNum() < 0;
            ItemStack itemstack = packet.getItem();

            if (!itemstack.isItemEnabled(this.player.level().enabledFeatures())) {
                return;
            }

            CompoundTag nbttagcompound = BlockItem.getBlockEntityData(itemstack);

            if (!itemstack.isEmpty() && nbttagcompound != null && nbttagcompound.contains("x") && nbttagcompound.contains("y") && nbttagcompound.contains("z") && this.player.getBukkitEntity().hasPermission("minecraft.nbt.copy")) { // Spigot
                BlockPos blockposition = BlockEntity.getPosFromTag(nbttagcompound);

                if (this.player.level().isLoaded(blockposition)) {
                    // Paper start
                    BlockEntity tileentity = null;
                    if (this.player.distanceToSqr(blockposition.getX(), blockposition.getY(), blockposition.getZ()) < 32 * 32 && this.player.serverLevel().isLoadedAndInBounds(blockposition)) {
                        tileentity = this.player.level().getBlockEntity(blockposition);
                    }
                    // Paper end

                    if (tileentity != null) {
                        tileentity.saveToItem(itemstack);
                    }
                }
            }

            boolean flag1 = packet.getSlotNum() >= 1 && packet.getSlotNum() <= 45;
            boolean flag2 = itemstack.isEmpty() || itemstack.getDamageValue() >= 0 && itemstack.getCount() <= 64 && !itemstack.isEmpty();
            if (flag || (flag1 && !ItemStack.matches(this.player.inventoryMenu.getSlot(packet.getSlotNum()).getItem(), packet.getItem()))) { // Insist on valid slot
                // CraftBukkit start - Call click event
                InventoryView inventory = this.player.inventoryMenu.getBukkitView();
                org.bukkit.inventory.ItemStack item = CraftItemStack.asBukkitCopy(packet.getItem());

                SlotType type = SlotType.QUICKBAR;
                if (flag) {
                    type = SlotType.OUTSIDE;
                } else if (packet.getSlotNum() < 36) {
                    if (packet.getSlotNum() >= 5 && packet.getSlotNum() < 9) {
                        type = SlotType.ARMOR;
                    } else {
                        type = SlotType.CONTAINER;
                    }
                }
                InventoryCreativeEvent event = new InventoryCreativeEvent(inventory, type, flag ? -999 : packet.getSlotNum(), item);
                this.cserver.getPluginManager().callEvent(event);

                itemstack = CraftItemStack.asNMSCopy(event.getCursor());

                switch (event.getResult()) {
                case ALLOW:
                    // Plugin cleared the id / stacksize checks
                    flag2 = true;
                    break;
                case DEFAULT:
                    break;
                case DENY:
                    // Reset the slot
                    if (packet.getSlotNum() >= 0) {
                        this.player.connection.send(new ClientboundContainerSetSlotPacket(this.player.inventoryMenu.containerId, this.player.inventoryMenu.incrementStateId(), packet.getSlotNum(), this.player.inventoryMenu.getSlot(packet.getSlotNum()).getItem()));
                        this.player.connection.send(new ClientboundContainerSetSlotPacket(-1, this.player.inventoryMenu.incrementStateId(), -1, ItemStack.EMPTY));
                    }
                    return;
                }
            }
            // CraftBukkit end

            if (flag1 && flag2) {
                this.player.inventoryMenu.getSlot(packet.getSlotNum()).setByPlayer(itemstack);
                this.player.inventoryMenu.broadcastChanges();
            } else if (flag && flag2 && this.dropSpamTickCount < 200) {
                this.dropSpamTickCount += 20;
                this.player.drop(itemstack, true);
            }
        }

    }

    @Override
    public void handleSignUpdate(ServerboundSignUpdatePacket packet) {
        // Paper start - cap line length - modified clients can send longer data than normal
        String[] lines = packet.getLines();
        for (int i = 0; i < lines.length; ++i) {
            if (MAX_SIGN_LINE_LENGTH > 0 && lines[i].length() > MAX_SIGN_LINE_LENGTH) {
                // This handles multibyte characters as 1
                int offset = lines[i].codePoints().limit(MAX_SIGN_LINE_LENGTH).map(Character::charCount).sum();
                if (offset < lines[i].length()) {
                    lines[i] = lines[i].substring(0, offset); // this will break any filtering, but filtering is NYI as of 1.17
                }
            }
        }
        List<String> list = (List) Stream.of(lines).map(ChatFormatting::stripFormatting).collect(Collectors.toList());
        // Paper end

        this.filterTextPacket(list).thenAcceptAsync((list1) -> {
            this.updateSignText(packet, list1);
        }, (Runnable run) -> { // Folia start - region threading
            this.player.getBukkitEntity().taskScheduler.schedule(
                (player) -> {
                    run.run();
                },
                null, 1L);
        }).whenComplete((Object res, Throwable thr) -> {
            if (thr != null) {
                LOGGER.error("Failed to handle sign update packet", thr);
            }
        });
        // Folia end - region threading
    }

    private void updateSignText(ServerboundSignUpdatePacket packet, List<FilteredText> signText) {
        if (this.player.isImmobile()) return; // CraftBukkit
        this.player.resetLastActionTime();
        ServerLevel worldserver = this.player.serverLevel();
        BlockPos blockposition = packet.getPos();

        if (worldserver.hasChunkAt(blockposition)) {
            BlockEntity tileentity = worldserver.getBlockEntity(blockposition);

            if (!(tileentity instanceof SignBlockEntity)) {
                return;
            }

            SignBlockEntity tileentitysign = (SignBlockEntity) tileentity;

            tileentitysign.updateSignText(this.player, packet.isFrontText(), signText);
        }

    }

    @Override
    public void handlePlayerAbilities(ServerboundPlayerAbilitiesPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        // CraftBukkit start
        if (this.player.getAbilities().mayfly && this.player.getAbilities().flying != packet.isFlying()) {
            PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(this.player.getBukkitEntity(), packet.isFlying());
            this.cserver.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                this.player.getAbilities().flying = packet.isFlying(); // Actually set the player's flying status
            } else {
                this.player.onUpdateAbilities(); // Tell the player their ability was reverted
            }
        }
        // CraftBukkit end
    }

    @Override
    public void handleClientInformation(ServerboundClientInformationPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        // Paper start - do not accept invalid information
        if (packet.information().viewDistance() < 0) {
            LOGGER.warn("Disconnecting " + this.player.getScoreboardName() + " for invalid view distance: " + packet.information().viewDistance());
            this.disconnect("Invalid client settings", org.bukkit.event.player.PlayerKickEvent.Cause.ILLEGAL_ACTION);
            return;
        }
        // Paper end - do not accept invalid information
        this.player.updateOptions(packet.information());
        this.connection.channel.attr(io.papermc.paper.adventure.PaperAdventure.LOCALE_ATTRIBUTE).set(net.kyori.adventure.translation.Translator.parseLocale(packet.information().language())); // Paper
    }

    @Override
    public void handleChangeDifficulty(ServerboundChangeDifficultyPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.hasPermissions(2) || this.isSingleplayerOwner()) {
            // this.server.setDifficulty(packet.getDifficulty(), false); // Paper - don't allow clients to change this
        }
    }

    @Override
    public void handleLockDifficulty(ServerboundLockDifficultyPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        if (this.player.hasPermissions(2) || this.isSingleplayerOwner()) {
            this.server.setDifficultyLocked(packet.isLocked());
        }
    }

    @Override
    public void handleChatSessionUpdate(ServerboundChatSessionUpdatePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        RemoteChatSession.Data remotechatsession_a = packet.chatSession();
        ProfilePublicKey.Data profilepublickey_a = this.chatSession != null ? this.chatSession.profilePublicKey().data() : null;
        ProfilePublicKey.Data profilepublickey_a1 = remotechatsession_a.profilePublicKey();

        if (!Objects.equals(profilepublickey_a, profilepublickey_a1)) {
            if (profilepublickey_a != null && profilepublickey_a1.expiresAt().isBefore(profilepublickey_a.expiresAt())) {
                this.disconnect(ProfilePublicKey.EXPIRED_PROFILE_PUBLIC_KEY, org.bukkit.event.player.PlayerKickEvent.Cause.EXPIRED_PROFILE_PUBLIC_KEY); // Paper - kick event causes
            } else {
                try {
                    SignatureValidator signaturevalidator = this.server.getProfileKeySignatureValidator();

                    if (signaturevalidator == null) {
                        ServerGamePacketListenerImpl.LOGGER.warn("Ignoring chat session from {} due to missing Services public key", this.player.getGameProfile().getName());
                        return;
                    }

                    this.resetPlayerChatState(remotechatsession_a.validate(this.player.getGameProfile(), signaturevalidator));
                } catch (ProfilePublicKey.ValidationException profilepublickey_b) {
                    // ServerGamePacketListenerImpl.LOGGER.error("Failed to validate profile key: {}", profilepublickey_b.getMessage()); // Paper - unnecessary log
                    this.disconnect(profilepublickey_b.getComponent(), profilepublickey_b.kickCause); // Paper - kick event causes
                }

            }
        }
    }

    @Override
    public void handleConfigurationAcknowledged(ServerboundConfigurationAcknowledgedPacket packet) {
        if (!this.waitingForSwitchToConfig) {
            throw new IllegalStateException("Client acknowledged config, but none was requested");
        } else {
            this.connection.setListener(new ServerConfigurationPacketListenerImpl(this.server, this.connection, this.createCookie(this.player.clientInformation()), this.player)); // CraftBukkit
        }
    }

    @Override
    public void handleChunkBatchReceived(ServerboundChunkBatchReceivedPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.player.serverLevel());
        this.chunkSender.onChunkBatchReceivedByClient(packet.desiredChunksPerTick());
    }

    private void resetPlayerChatState(RemoteChatSession session) {
        this.chatSession = session;
        this.hasLoggedExpiry = false; // Paper
        this.signedMessageDecoder = session.createMessageDecoder(this.player.getUUID());
        this.chatMessageChain.append((executor) -> {
            this.player.setChatSession(session);
            this.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT), List.of(this.player)), this.player); // Paper
            return CompletableFuture.completedFuture((Object) null);
        });
    }

    @Override
    public ServerPlayer getPlayer() {
        return this.player;
    }

    @FunctionalInterface
    private interface EntityInteraction {

        InteractionResult run(ServerPlayer player, Entity entity, InteractionHand hand);
    }
}
