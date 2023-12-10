package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class SetBlockCommand {
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.setblock.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext commandRegistryAccess) {
        dispatcher.register(Commands.literal("setblock").requires((source) -> {
            return source.hasPermission(2);
        }).then(Commands.argument("pos", BlockPosArgument.blockPos()).then(Commands.argument("block", BlockStateArgument.block(commandRegistryAccess)).executes((context) -> {
            return setBlock(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos"), BlockStateArgument.getBlock(context, "block"), SetBlockCommand.Mode.REPLACE, (Predicate<BlockInWorld>)null);
        }).then(Commands.literal("destroy").executes((context) -> {
            return setBlock(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos"), BlockStateArgument.getBlock(context, "block"), SetBlockCommand.Mode.DESTROY, (Predicate<BlockInWorld>)null);
        })).then(Commands.literal("keep").executes((context) -> {
            return setBlock(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos"), BlockStateArgument.getBlock(context, "block"), SetBlockCommand.Mode.REPLACE, (pos) -> {
                return pos.getLevel().isEmptyBlock(pos.getPos());
            });
        })).then(Commands.literal("replace").executes((context) -> {
            return setBlock(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos"), BlockStateArgument.getBlock(context, "block"), SetBlockCommand.Mode.REPLACE, (Predicate<BlockInWorld>)null);
        })))));
    }

    // Folia start - region threading
    private static void sendMessage(CommandSourceStack src, CommandSyntaxException ex) {
        src.sendFailure((Component)ex.getRawMessage());
    }
    // Folia end - region threading

    private static int setBlock(CommandSourceStack source, BlockPos pos, BlockInput block, SetBlockCommand.Mode mode, @Nullable Predicate<BlockInWorld> condition) throws CommandSyntaxException {
        ServerLevel serverLevel = source.getLevel();
        // Folia start - region threading
        io.papermc.paper.threadedregions.RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
            serverLevel, pos.getX() >> 4, pos.getZ() >> 4, () -> {
                try {
                    if (condition != null && !condition.test(new BlockInWorld(serverLevel, pos, true))) {
                        throw ERROR_FAILED.create();
                    } else {
                        boolean bl;
                        if (mode == SetBlockCommand.Mode.DESTROY) {
                            serverLevel.destroyBlock(pos, true);
                            bl = !block.getState().isAir() || !serverLevel.getBlockState(pos).isAir();
                        } else {
                            BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
                            Clearable.tryClear(blockEntity);
                            bl = true;
                        }

                        if (bl && !block.place(serverLevel, pos, 2)) {
                            throw ERROR_FAILED.create();
                        } else {
                            serverLevel.blockUpdated(pos, block.getState().getBlock());
                            source.sendSuccess(() -> {
                                return Component.translatable("commands.setblock.success", pos.getX(), pos.getY(), pos.getZ());
                            }, true);
                        }
                    }
                } catch (CommandSyntaxException ex) {
                    sendMessage(source, ex);
                }
            }
        );
        return 1;
        // Folia end - region threading
    }

    public interface Filter {
        @Nullable
        BlockInput filter(BoundingBox box, BlockPos pos, BlockInput block, ServerLevel world);
    }

    public static enum Mode {
        REPLACE,
        DESTROY;
    }
}
