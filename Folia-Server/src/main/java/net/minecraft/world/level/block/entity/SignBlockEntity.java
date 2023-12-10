package net.minecraft.world.level.block.entity;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.bukkit.block.sign.Side;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
// CraftBukkit end

public class SignBlockEntity extends BlockEntity implements CommandSource { // CraftBukkit - implements

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TEXT_LINE_WIDTH = 90;
    private static final int TEXT_LINE_HEIGHT = 10;
    @Nullable
    private UUID playerWhoMayEdit;
    private SignText frontText;
    private SignText backText;
    private boolean isWaxed;

    public SignBlockEntity(BlockPos pos, BlockState state) {
        this(BlockEntityType.SIGN, pos, state);
    }

    public SignBlockEntity(BlockEntityType type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.frontText = this.createDefaultSignText();
        this.backText = this.createDefaultSignText();
    }

    protected SignText createDefaultSignText() {
        return new SignText();
    }

    public boolean isFacingFrontText(net.minecraft.world.entity.player.Player player) {
        // Paper start
        return this.isFacingFrontText(player.getX(), player.getZ());
    }
    public boolean isFacingFrontText(double x, double z) {
        // Paper end
        Block block = this.getBlockState().getBlock();

        if (block instanceof SignBlock) {
            SignBlock blocksign = (SignBlock) block;
            Vec3 vec3d = blocksign.getSignHitboxCenterPosition(this.getBlockState());
            double d0 = x - ((double) this.getBlockPos().getX() + vec3d.x); // Paper
            double d1 = z - ((double) this.getBlockPos().getZ() + vec3d.z); // Paper
            float f = blocksign.getYRotationDegrees(this.getBlockState());
            float f1 = (float) (Mth.atan2(d1, d0) * 57.2957763671875D) - 90.0F;

            return Mth.degreesDifferenceAbs(f, f1) <= 90.0F;
        } else {
            return false;
        }
    }

    public SignText getTextFacingPlayer(net.minecraft.world.entity.player.Player player) {
        return this.getText(this.isFacingFrontText(player));
    }

    public SignText getText(boolean front) {
        return front ? this.frontText : this.backText;
    }

    public SignText getFrontText() {
        return this.frontText;
    }

    public SignText getBackText() {
        return this.backText;
    }

    public int getTextLineHeight() {
        return 10;
    }

    public int getMaxTextLineWidth() {
        return 90;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        DataResult<Tag> dataresult = SignText.DIRECT_CODEC.encodeStart(NbtOps.INSTANCE, this.frontText); // CraftBukkit - decompile error
        Logger logger = SignBlockEntity.LOGGER;

        Objects.requireNonNull(logger);
        dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
            nbt.put("front_text", nbtbase);
        });
        dataresult = SignText.DIRECT_CODEC.encodeStart(NbtOps.INSTANCE, this.backText);
        logger = SignBlockEntity.LOGGER;
        Objects.requireNonNull(logger);
        dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
            nbt.put("back_text", nbtbase);
        });
        nbt.putBoolean("is_waxed", this.isWaxed);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        DataResult<SignText> dataresult; // CraftBukkit - decompile error
        Logger logger;

        if (nbt.contains("front_text")) {
            dataresult = SignText.DIRECT_CODEC.parse(NbtOps.INSTANCE, nbt.getCompound("front_text"));
            logger = SignBlockEntity.LOGGER;
            Objects.requireNonNull(logger);
            dataresult.resultOrPartial(logger::error).ifPresent((signtext) -> {
                this.frontText = this.loadLines(signtext);
            });
        }

        if (nbt.contains("back_text")) {
            dataresult = SignText.DIRECT_CODEC.parse(NbtOps.INSTANCE, nbt.getCompound("back_text"));
            logger = SignBlockEntity.LOGGER;
            Objects.requireNonNull(logger);
            dataresult.resultOrPartial(logger::error).ifPresent((signtext) -> {
                this.backText = this.loadLines(signtext);
            });
        }

        this.isWaxed = nbt.getBoolean("is_waxed");
    }

    private SignText loadLines(SignText signText) {
        for (int i = 0; i < 4; ++i) {
            Component ichatbasecomponent = this.loadLine(signText.getMessage(i, false));
            Component ichatbasecomponent1 = this.loadLine(signText.getMessage(i, true));

            signText = signText.setMessage(i, ichatbasecomponent, ichatbasecomponent1);
        }

        return signText;
    }

    private Component loadLine(Component text) {
        Level world = this.level;

        if (world instanceof ServerLevel) {
            ServerLevel worldserver = (ServerLevel) world;

            try {
                return ComponentUtils.updateForEntity(this.createCommandSourceStack((net.minecraft.world.entity.player.Player) null, worldserver, this.worldPosition), text, (Entity) null, 0);
            } catch (CommandSyntaxException commandsyntaxexception) {
                ;
            }
        }

        return text;
    }

    public void updateSignText(net.minecraft.world.entity.player.Player player, boolean front, List<FilteredText> messages) {
        if (!this.isWaxed() && player.getUUID().equals(this.getPlayerWhoMayEdit()) && this.level != null) {
            this.updateText((signtext) -> {
                return this.setMessages(player, messages, signtext, front); // CraftBukkit
            }, front);
            this.setAllowedPlayerEditor((UUID) null);
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        } else {
            SignBlockEntity.LOGGER.warn("Player {} just tried to change non-editable sign", player.getName().getString());
            if (player.distanceToSqr(this.getBlockPos().getX(), this.getBlockPos().getY(), this.getBlockPos().getZ()) < 32 * 32) // Paper
            ((ServerPlayer) player).connection.send(this.getUpdatePacket()); // CraftBukkit
        }
    }

    public boolean updateText(UnaryOperator<SignText> textChanger, boolean front) {
        SignText signtext = this.getText(front);

        return this.setText((SignText) textChanger.apply(signtext), front);
    }

    private SignText setMessages(net.minecraft.world.entity.player.Player entityhuman, List<FilteredText> list, SignText signtext, boolean front) { // CraftBukkit
        SignText originalText = signtext; // CraftBukkit
        for (int i = 0; i < list.size(); ++i) {
            FilteredText filteredtext = (FilteredText) list.get(i);
            Style chatmodifier = signtext.getMessage(i, entityhuman.isTextFilteringEnabled()).getStyle();

            if (entityhuman.isTextFilteringEnabled()) {
                signtext = signtext.setMessage(i, Component.literal(net.minecraft.SharedConstants.filterText(filteredtext.filteredOrEmpty())).setStyle(chatmodifier)); // Paper - filter sign text to chat only
            } else {
                signtext = signtext.setMessage(i, Component.literal(net.minecraft.SharedConstants.filterText(filteredtext.raw())).setStyle(chatmodifier), Component.literal(net.minecraft.SharedConstants.filterText(filteredtext.filteredOrEmpty())).setStyle(chatmodifier)); // Paper - filter sign text to chat only
            }
        }

        // CraftBukkit start
        Player player = ((ServerPlayer) entityhuman).getBukkitEntity();
        List<net.kyori.adventure.text.Component> lines = new java.util.ArrayList<>(); // Paper - adventure

        for (int i = 0; i < list.size(); ++i) {
            lines.add(io.papermc.paper.adventure.PaperAdventure.asAdventure(signtext.getMessage(i, entityhuman.isTextFilteringEnabled()))); // Paper - Adventure
        }

        SignChangeEvent event = new SignChangeEvent(CraftBlock.at(this.level, this.worldPosition), player, new java.util.ArrayList<>(lines), (front) ? Side.FRONT : Side.BACK); // Paper - Adventure
        entityhuman.level().getCraftServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return originalText;
        }

        Component[] components = org.bukkit.craftbukkit.block.CraftSign.sanitizeLines(event.lines()); // Paper - Adventure
        for (int i = 0; i < components.length; i++) {
            if (!Objects.equals(lines.get(i), event.line(i))) { // Paper - Adventure
                signtext = signtext.setMessage(i, components[i]);
            }
        }
        // CraftBukkit end

        return signtext;
    }

    public boolean setText(SignText text, boolean front) {
        return front ? this.setFrontText(text) : this.setBackText(text);
    }

    private boolean setBackText(SignText backText) {
        if (backText != this.backText) {
            this.backText = backText;
            this.markUpdated();
            return true;
        } else {
            return false;
        }
    }

    private boolean setFrontText(SignText frontText) {
        if (frontText != this.frontText) {
            this.frontText = frontText;
            this.markUpdated();
            return true;
        } else {
            return false;
        }
    }

    public boolean canExecuteClickCommands(boolean front, net.minecraft.world.entity.player.Player player) {
        return this.isWaxed() && this.getText(front).hasAnyClickCommands(player);
    }

    public boolean executeClickCommandsIfPresent(net.minecraft.world.entity.player.Player player, Level world, BlockPos pos, boolean front) {
        boolean flag1 = false;
        Component[] aichatbasecomponent = this.getText(front).getMessages(player.isTextFilteringEnabled());
        int i = aichatbasecomponent.length;

        for (int j = 0; j < i; ++j) {
            Component ichatbasecomponent = aichatbasecomponent[j];
            Style chatmodifier = ichatbasecomponent.getStyle();
            ClickEvent chatclickable = chatmodifier.getClickEvent();

            if (chatclickable != null && chatclickable.getAction() == ClickEvent.Action.RUN_COMMAND) {
                // Paper start
                String command = chatclickable.getValue().startsWith("/") ? chatclickable.getValue() : "/" + chatclickable.getValue();
                if (org.spigotmc.SpigotConfig.logCommands)  {
                    LOGGER.info("{} issued server command: {}", player.getScoreboardName(), command);
                }
                io.papermc.paper.event.player.PlayerSignCommandPreprocessEvent event = new io.papermc.paper.event.player.PlayerSignCommandPreprocessEvent((org.bukkit.entity.Player) player.getBukkitEntity(), command, new org.bukkit.craftbukkit.util.LazyPlayerSet(player.getServer()), (org.bukkit.block.Sign) io.papermc.paper.util.MCUtil.toBukkitBlock(this.level, this.worldPosition).getState(), front ? Side.FRONT : Side.BACK);
                if (!event.callEvent()) {
                    return false;
                }
                player.getServer().getCommands().performPrefixedCommand(this.createCommandSourceStack(((org.bukkit.craftbukkit.entity.CraftPlayer) event.getPlayer()).getHandle(), world, pos), event.getMessage());
                // Paper end
                flag1 = true;
            }
        }

        return flag1;
    }

    // CraftBukkit start
    @Override
    public void sendSystemMessage(Component message) {}

    @Override
    public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
        return wrapper.getEntity() != null ? wrapper.getEntity().getBukkitSender(wrapper) : new org.bukkit.craftbukkit.command.CraftBlockCommandSender(wrapper, this);
    }

    @Override
    public boolean acceptsSuccess() {
        return false;
    }

    @Override
    public boolean acceptsFailure() {
        return false;
    }

    @Override
    public boolean shouldInformAdmins() {
        return false;
    }

    private CommandSourceStack createCommandSourceStack(@Nullable net.minecraft.world.entity.player.Player player, Level world, BlockPos pos) {
        // CraftBukkit end
        String s = player == null ? "Sign" : player.getName().getString();
        Object object = player == null ? Component.literal("Sign") : player.getDisplayName();

        // Paper start - send messages back to the player
        CommandSource commandSource = this.level.paperConfig().misc.showSignClickCommandFailureMsgsToPlayer ? new io.papermc.paper.commands.DelegatingCommandSource(this) {
            @Override
            public void sendSystemMessage(Component message) {
                if (player != null) {
                    player.sendSystemMessage(message);
                }
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }
        } : this;
        // Paper end
        // CraftBukkit - this
        return new CommandSourceStack(commandSource, Vec3.atCenterOf(pos), Vec2.ZERO, (ServerLevel) world, 2, s, (Component) object, world.getServer(), player); // Paper
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    public void setAllowedPlayerEditor(@Nullable UUID editor) {
        this.playerWhoMayEdit = editor;
    }

    @Nullable
    public UUID getPlayerWhoMayEdit() {
        // Paper start
        if (this.hasLevel() && this.playerWhoMayEdit != null) {
            // Manually invalidate the value lazily.
            this.clearInvalidPlayerWhoMayEdit(this, this.getLevel(), this.playerWhoMayEdit);
        }
        // Paper end
        return this.playerWhoMayEdit;
    }

    private void markUpdated() {
        this.setChanged();
        if (this.level != null) this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3); // CraftBukkit - skip notify if world is null (SPIGOT-5122)
    }

    public boolean isWaxed() {
        return this.isWaxed;
    }

    public boolean setWaxed(boolean waxed) {
        if (this.isWaxed != waxed) {
            this.isWaxed = waxed;
            this.markUpdated();
            return true;
        } else {
            return false;
        }
    }

    public boolean playerIsTooFarAwayToEdit(UUID uuid) {
        net.minecraft.world.entity.player.Player entityhuman = this.level.getPlayerByUUID(uuid);

        return entityhuman == null || entityhuman.distanceToSqr((double) this.getBlockPos().getX(), (double) this.getBlockPos().getY(), (double) this.getBlockPos().getZ()) > 64.0D;
    }

    public static void tick(Level world, BlockPos pos, BlockState state, SignBlockEntity blockEntity) {
        UUID uuid = blockEntity.getPlayerWhoMayEdit();

        if (uuid != null) {
            blockEntity.clearInvalidPlayerWhoMayEdit(blockEntity, world, uuid);
        }

    }

    private void clearInvalidPlayerWhoMayEdit(SignBlockEntity blockEntity, Level world, UUID uuid) {
        if (blockEntity.playerIsTooFarAwayToEdit(uuid)) {
            blockEntity.setAllowedPlayerEditor((UUID) null);
        }

    }
}
