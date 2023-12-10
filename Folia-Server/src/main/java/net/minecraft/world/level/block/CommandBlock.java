package net.minecraft.world.level.block;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;

import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public class CommandBlock extends BaseEntityBlock implements GameMasterBlock {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DirectionProperty FACING = DirectionalBlock.FACING;
    public static final BooleanProperty CONDITIONAL = BlockStateProperties.CONDITIONAL;
    private final boolean automatic;

    public CommandBlock(BlockBehaviour.Properties settings, boolean auto) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) ((BlockState) this.stateDefinition.any()).setValue(CommandBlock.FACING, Direction.NORTH)).setValue(CommandBlock.CONDITIONAL, false));
        this.automatic = auto;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        CommandBlockEntity tileentitycommand = new CommandBlockEntity(pos, state);

        tileentitycommand.setAutomatic(this.automatic);
        return tileentitycommand;
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (!world.isClientSide) {
            BlockEntity tileentity = world.getBlockEntity(pos);

            if (tileentity instanceof CommandBlockEntity) {
                CommandBlockEntity tileentitycommand = (CommandBlockEntity) tileentity;
                boolean flag1 = world.hasNeighborSignal(pos);
                boolean flag2 = tileentitycommand.isPowered();
                // CraftBukkit start
                org.bukkit.block.Block bukkitBlock = world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
                int old = flag2 ? 15 : 0;
                int current = flag1 ? 15 : 0;

                BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(bukkitBlock, old, current);
                world.getCraftServer().getPluginManager().callEvent(eventRedstone);
                flag1 = eventRedstone.getNewCurrent() > 0;
                // CraftBukkit end

                tileentitycommand.setPowered(flag1);
                if (!flag2 && !tileentitycommand.isAutomatic() && tileentitycommand.getMode() != CommandBlockEntity.Mode.SEQUENCE) {
                    if (flag1) {
                        tileentitycommand.markConditionMet();
                        world.scheduleTick(pos, (Block) this, 1);
                    }

                }
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof CommandBlockEntity) {
            CommandBlockEntity tileentitycommand = (CommandBlockEntity) tileentity;
            BaseCommandBlock commandblocklistenerabstract = tileentitycommand.getCommandBlock();
            boolean flag = !StringUtil.isNullOrEmpty(commandblocklistenerabstract.getCommand());
            CommandBlockEntity.Mode tileentitycommand_type = tileentitycommand.getMode();
            boolean flag1 = tileentitycommand.wasConditionMet();

            if (tileentitycommand_type == CommandBlockEntity.Mode.AUTO) {
                tileentitycommand.markConditionMet();
                if (flag1) {
                    this.execute(state, world, pos, commandblocklistenerabstract, flag);
                } else if (tileentitycommand.isConditional()) {
                    commandblocklistenerabstract.setSuccessCount(0);
                }

                if (tileentitycommand.isPowered() || tileentitycommand.isAutomatic()) {
                    world.scheduleTick(pos, (Block) this, 1);
                }
            } else if (tileentitycommand_type == CommandBlockEntity.Mode.REDSTONE) {
                if (flag1) {
                    this.execute(state, world, pos, commandblocklistenerabstract, flag);
                } else if (tileentitycommand.isConditional()) {
                    commandblocklistenerabstract.setSuccessCount(0);
                }
            }

            world.updateNeighbourForOutputSignal(pos, this);
        }

    }

    private void execute(BlockState state, Level world, BlockPos pos, BaseCommandBlock executor, boolean hasCommand) {
        if (hasCommand) {
            executor.performCommand(world);
        } else {
            executor.setSuccessCount(0);
        }

        CommandBlock.executeChain(world, pos, (Direction) state.getValue(CommandBlock.FACING));
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof CommandBlockEntity && (player.canUseGameMasterBlocks() || (player.isCreative() && player.getBukkitEntity().hasPermission("minecraft.commandblock")))) { // Paper - command block permission
            player.openCommandBlock((CommandBlockEntity) tileentity);
            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        return tileentity instanceof CommandBlockEntity ? ((CommandBlockEntity) tileentity).getCommandBlock().getSuccessCount() : 0;
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof CommandBlockEntity) {
            CommandBlockEntity tileentitycommand = (CommandBlockEntity) tileentity;
            BaseCommandBlock commandblocklistenerabstract = tileentitycommand.getCommandBlock();

            if (itemStack.hasCustomHoverName()) {
                commandblocklistenerabstract.setName(itemStack.getHoverName());
            }

            if (!world.isClientSide) {
                if (BlockItem.getBlockEntityData(itemStack) == null) {
                    commandblocklistenerabstract.setTrackOutput(world.getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK));
                    tileentitycommand.setAutomatic(this.automatic);
                }

                if (tileentitycommand.getMode() == CommandBlockEntity.Mode.SEQUENCE) {
                    boolean flag = world.hasNeighborSignal(pos);

                    tileentitycommand.setPowered(flag);
                }
            }

        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState) state.setValue(CommandBlock.FACING, rotation.rotate((Direction) state.getValue(CommandBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction) state.getValue(CommandBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CommandBlock.FACING, CommandBlock.CONDITIONAL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return (BlockState) this.defaultBlockState().setValue(CommandBlock.FACING, ctx.getNearestLookingDirection().getOpposite());
    }

    private static void executeChain(Level world, BlockPos pos, Direction facing) {
        BlockPos.MutableBlockPos blockposition_mutableblockposition = pos.mutable();
        GameRules gamerules = world.getGameRules();

        BlockState iblockdata;
        int i;

        for (i = gamerules.getInt(GameRules.RULE_MAX_COMMAND_CHAIN_LENGTH); i-- > 0; facing = (Direction) iblockdata.getValue(CommandBlock.FACING)) {
            blockposition_mutableblockposition.move(facing);
            iblockdata = world.getBlockState(blockposition_mutableblockposition);
            Block block = iblockdata.getBlock();

            if (!iblockdata.is(Blocks.CHAIN_COMMAND_BLOCK)) {
                break;
            }

            BlockEntity tileentity = world.getBlockEntity(blockposition_mutableblockposition);

            if (!(tileentity instanceof CommandBlockEntity)) {
                break;
            }

            CommandBlockEntity tileentitycommand = (CommandBlockEntity) tileentity;

            if (tileentitycommand.getMode() != CommandBlockEntity.Mode.SEQUENCE) {
                break;
            }

            if (tileentitycommand.isPowered() || tileentitycommand.isAutomatic()) {
                BaseCommandBlock commandblocklistenerabstract = tileentitycommand.getCommandBlock();

                if (tileentitycommand.markConditionMet()) {
                    if (!commandblocklistenerabstract.performCommand(world)) {
                        break;
                    }

                    world.updateNeighbourForOutputSignal(blockposition_mutableblockposition, block);
                } else if (tileentitycommand.isConditional()) {
                    commandblocklistenerabstract.setSuccessCount(0);
                }
            }
        }

        if (i <= 0) {
            int j = Math.max(gamerules.getInt(GameRules.RULE_MAX_COMMAND_CHAIN_LENGTH), 0);

            CommandBlock.LOGGER.warn("Command Block chain tried to execute more than {} steps!", j);
        }

    }
}
