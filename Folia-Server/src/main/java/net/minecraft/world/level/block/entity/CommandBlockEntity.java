package net.minecraft.world.level.block.entity;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class CommandBlockEntity extends BlockEntity {

    private boolean powered;
    private boolean auto;
    private boolean conditionMet;
    private final BaseCommandBlock commandBlock = new BaseCommandBlock() {
        // CraftBukkit start
        @Override
        public org.bukkit.command.CommandSender getBukkitSender(CommandSourceStack wrapper) {
            return new org.bukkit.craftbukkit.command.CraftBlockCommandSender(wrapper, CommandBlockEntity.this);
        }
        // CraftBukkit end

        @Override
        public void setCommand(String command) {
            super.setCommand(command);
            CommandBlockEntity.this.setChanged();
        }

        @Override
        public ServerLevel getLevel() {
            return (ServerLevel) CommandBlockEntity.this.level;
        }

        @Override
        public void onUpdated() {
            BlockState iblockdata = CommandBlockEntity.this.level.getBlockState(CommandBlockEntity.this.worldPosition);

            this.getLevel().sendBlockUpdated(CommandBlockEntity.this.worldPosition, iblockdata, iblockdata, 3);
        }

        @Override
        public Vec3 getPosition() {
            return Vec3.atCenterOf(CommandBlockEntity.this.worldPosition);
        }

        @Override
        public CommandSourceStack createCommandSourceStack() {
            Direction enumdirection = (Direction) CommandBlockEntity.this.getBlockState().getValue(CommandBlock.FACING);

            return new CommandSourceStack(this, Vec3.atCenterOf(CommandBlockEntity.this.worldPosition), new Vec2(0.0F, enumdirection.toYRot()), this.getLevel(), 2, this.getName().getString(), this.getName(), this.getLevel().getServer(), (Entity) null);
        }

        // Folia start
        @Override
        public void threadCheck() {
            io.papermc.paper.util.TickThread.ensureTickThread((ServerLevel) CommandBlockEntity.this.level, CommandBlockEntity.this.worldPosition, "Asynchronous sendSystemMessage to a command block");
        }
        // Folia end

        @Override
        public boolean isValid() {
            return !CommandBlockEntity.this.isRemoved();
        }
    };

    public CommandBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.COMMAND_BLOCK, pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        this.commandBlock.save(nbt);
        nbt.putBoolean("powered", this.isPowered());
        nbt.putBoolean("conditionMet", this.wasConditionMet());
        nbt.putBoolean("auto", this.isAutomatic());
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        this.commandBlock.load(nbt);
        this.powered = nbt.getBoolean("powered");
        this.conditionMet = nbt.getBoolean("conditionMet");
        this.setAutomatic(nbt.getBoolean("auto"));
    }

    @Override
    public boolean onlyOpCanSetNbt() {
        return true;
    }

    public BaseCommandBlock getCommandBlock() {
        return this.commandBlock;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    public boolean isPowered() {
        return this.powered;
    }

    public boolean isAutomatic() {
        return this.auto;
    }

    public void setAutomatic(boolean auto) {
        boolean flag1 = this.auto;

        this.auto = auto;
        if (!flag1 && auto && !this.powered && this.level != null && this.getMode() != CommandBlockEntity.Mode.SEQUENCE) {
            this.scheduleTick();
        }

    }

    public void onModeSwitch() {
        CommandBlockEntity.Mode tileentitycommand_type = this.getMode();

        if (tileentitycommand_type == CommandBlockEntity.Mode.AUTO && (this.powered || this.auto) && this.level != null) {
            this.scheduleTick();
        }

    }

    private void scheduleTick() {
        Block block = this.getBlockState().getBlock();

        if (block instanceof CommandBlock) {
            this.markConditionMet();
            this.level.scheduleTick(this.worldPosition, block, 1);
        }

    }

    public boolean wasConditionMet() {
        return this.conditionMet;
    }

    public boolean markConditionMet() {
        this.conditionMet = true;
        if (this.isConditional()) {
            BlockPos blockposition = this.worldPosition.relative(((Direction) this.level.getBlockState(this.worldPosition).getValue(CommandBlock.FACING)).getOpposite());

            if (this.level.getBlockState(blockposition).getBlock() instanceof CommandBlock) {
                BlockEntity tileentity = this.level.getBlockEntity(blockposition);

                this.conditionMet = tileentity instanceof CommandBlockEntity && ((CommandBlockEntity) tileentity).getCommandBlock().getSuccessCount() > 0;
            } else {
                this.conditionMet = false;
            }
        }

        return this.conditionMet;
    }

    public CommandBlockEntity.Mode getMode() {
        BlockState iblockdata = this.getBlockState();

        return iblockdata.is(Blocks.COMMAND_BLOCK) ? CommandBlockEntity.Mode.REDSTONE : (iblockdata.is(Blocks.REPEATING_COMMAND_BLOCK) ? CommandBlockEntity.Mode.AUTO : (iblockdata.is(Blocks.CHAIN_COMMAND_BLOCK) ? CommandBlockEntity.Mode.SEQUENCE : CommandBlockEntity.Mode.REDSTONE));
    }

    public boolean isConditional() {
        BlockState iblockdata = this.level.getBlockState(this.getBlockPos());

        return iblockdata.getBlock() instanceof CommandBlock ? (Boolean) iblockdata.getValue(CommandBlock.CONDITIONAL) : false;
    }

    public static enum Mode {

        SEQUENCE, AUTO, REDSTONE;

        private Mode() {}
    }
}
