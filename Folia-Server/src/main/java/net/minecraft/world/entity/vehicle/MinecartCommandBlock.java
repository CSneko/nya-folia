package net.minecraft.world.entity.vehicle;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class MinecartCommandBlock extends AbstractMinecart {

    public static final EntityDataAccessor<String> DATA_ID_COMMAND_NAME = SynchedEntityData.defineId(MinecartCommandBlock.class, EntityDataSerializers.STRING);
    static final EntityDataAccessor<Component> DATA_ID_LAST_OUTPUT = SynchedEntityData.defineId(MinecartCommandBlock.class, EntityDataSerializers.COMPONENT);
    private final BaseCommandBlock commandBlock = new MinecartCommandBlock.MinecartCommandBase();
    private static final int ACTIVATION_DELAY = 4;
    private int lastActivated;

    public MinecartCommandBlock(EntityType<? extends MinecartCommandBlock> type, Level world) {
        super(type, world);
    }

    public MinecartCommandBlock(Level world, double x, double y, double z) {
        super(EntityType.COMMAND_BLOCK_MINECART, world, x, y, z);
    }

    @Override
    protected Item getDropItem() {
        return Items.MINECART;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.getEntityData().define(MinecartCommandBlock.DATA_ID_COMMAND_NAME, "");
        this.getEntityData().define(MinecartCommandBlock.DATA_ID_LAST_OUTPUT, CommonComponents.EMPTY);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.commandBlock.load(nbt);
        this.getEntityData().set(MinecartCommandBlock.DATA_ID_COMMAND_NAME, this.getCommandBlock().getCommand());
        this.getEntityData().set(MinecartCommandBlock.DATA_ID_LAST_OUTPUT, this.getCommandBlock().getLastOutput());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        this.commandBlock.save(nbt);
    }

    @Override
    public AbstractMinecart.Type getMinecartType() {
        return AbstractMinecart.Type.COMMAND_BLOCK;
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return Blocks.COMMAND_BLOCK.defaultBlockState();
    }

    public BaseCommandBlock getCommandBlock() {
        return this.commandBlock;
    }

    @Override
    public void activateMinecart(int x, int y, int z, boolean powered) {
        if (powered && this.tickCount - this.lastActivated >= 4) {
            this.getCommandBlock().performCommand(this.level());
            this.lastActivated = this.tickCount;
        }

    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return this.commandBlock.usedBy(player);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
        if (MinecartCommandBlock.DATA_ID_LAST_OUTPUT.equals(data)) {
            try {
                this.commandBlock.setLastOutput((Component) this.getEntityData().get(MinecartCommandBlock.DATA_ID_LAST_OUTPUT));
            } catch (Throwable throwable) {
                ;
            }
        } else if (MinecartCommandBlock.DATA_ID_COMMAND_NAME.equals(data)) {
            this.commandBlock.setCommand((String) this.getEntityData().get(MinecartCommandBlock.DATA_ID_COMMAND_NAME));
        }

    }

    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    public class MinecartCommandBase extends BaseCommandBlock {

        public MinecartCommandBase() {}

        @Override
        public ServerLevel getLevel() {
            return (ServerLevel) MinecartCommandBlock.this.level();
        }

        @Override
        public void onUpdated() {
            MinecartCommandBlock.this.getEntityData().set(MinecartCommandBlock.DATA_ID_COMMAND_NAME, this.getCommand());
            MinecartCommandBlock.this.getEntityData().set(MinecartCommandBlock.DATA_ID_LAST_OUTPUT, this.getLastOutput());
        }

        @Override
        public Vec3 getPosition() {
            return MinecartCommandBlock.this.position();
        }

        public MinecartCommandBlock getMinecart() {
            return MinecartCommandBlock.this;
        }

        @Override
        public CommandSourceStack createCommandSourceStack() {
            return new CommandSourceStack(this, MinecartCommandBlock.this.position(), MinecartCommandBlock.this.getRotationVector(), this.getLevel(), 2, this.getName().getString(), MinecartCommandBlock.this.getDisplayName(), this.getLevel().getServer(), MinecartCommandBlock.this);
        }

        @Override
        public boolean isValid() {
            return !MinecartCommandBlock.this.isRemoved();
        }

        // CraftBukkit start
        @Override
        public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
            return (org.bukkit.craftbukkit.entity.CraftMinecartCommand) MinecartCommandBlock.this.getBukkitEntity();
        }
        // CraftBukkit end
        // Folia start
        @Override
        public void threadCheck() {
            io.papermc.paper.util.TickThread.ensureTickThread(MinecartCommandBlock.this, "Asynchronous sendSystemMessage to a command block");
        }
        // Folia end
    }
}
