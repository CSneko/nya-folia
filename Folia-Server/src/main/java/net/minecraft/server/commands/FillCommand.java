package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class FillCommand {
    private static final Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE = new Dynamic2CommandExceptionType((maxCount, count) -> {
        return Component.translatable("commands.fill.toobig", maxCount, count);
    });
    static final BlockInput HOLLOW_CORE = new BlockInput(Blocks.AIR.defaultBlockState(), Collections.emptySet(), (CompoundTag)null);
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.fill.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext commandRegistryAccess) {
        dispatcher.register(Commands.literal("fill").requires((source) -> {
            return source.hasPermission(2);
        }).then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to", BlockPosArgument.blockPos()).then(Commands.argument("block", BlockStateArgument.block(commandRegistryAccess)).executes((context) -> {
            return fillBlocks(context.getSource(), BoundingBox.fromCorners(BlockPosArgument.getLoadedBlockPos(context, "from"), BlockPosArgument.getLoadedBlockPos(context, "to")), BlockStateArgument.getBlock(context, "block"), FillCommand.Mode.REPLACE, (Predicate<BlockInWorld>)null);
        }).then(Commands.literal("replace").executes((context) -> {
            return fillBlocks(context.getSource(), BoundingBox.fromCorners(BlockPosArgument.getLoadedBlockPos(context, "from"), BlockPosArgument.getLoadedBlockPos(context, "to")), BlockStateArgument.getBlock(context, "block"), FillCommand.Mode.REPLACE, (Predicate<BlockInWorld>)null);
        }).then(Commands.argument("filter", BlockPredicateArgument.blockPredicate(commandRegistryAccess)).executes((context) -> {
            return fillBlocks(context.getSource(), BoundingBox.fromCorners(BlockPosArgument.getLoadedBlockPos(context, "from"), BlockPosArgument.getLoadedBlockPos(context, "to")), BlockStateArgument.getBlock(context, "block"), FillCommand.Mode.REPLACE, BlockPredicateArgument.getBlockPredicate(context, "filter"));
        }))).then(Commands.literal("keep").executes((context) -> {
            return fillBlocks(context.getSource(), BoundingBox.fromCorners(BlockPosArgument.getLoadedBlockPos(context, "from"), BlockPosArgument.getLoadedBlockPos(context, "to")), BlockStateArgument.getBlock(context, "block"), FillCommand.Mode.REPLACE, (pos) -> {
                return pos.getLevel().isEmptyBlock(pos.getPos());
            });
        })).then(Commands.literal("outline").executes((context) -> {
            return fillBlocks(context.getSource(), BoundingBox.fromCorners(BlockPosArgument.getLoadedBlockPos(context, "from"), BlockPosArgument.getLoadedBlockPos(context, "to")), BlockStateArgument.getBlock(context, "block"), FillCommand.Mode.OUTLINE, (Predicate<BlockInWorld>)null);
        })).then(Commands.literal("hollow").executes((context) -> {
            return fillBlocks(context.getSource(), BoundingBox.fromCorners(BlockPosArgument.getLoadedBlockPos(context, "from"), BlockPosArgument.getLoadedBlockPos(context, "to")), BlockStateArgument.getBlock(context, "block"), FillCommand.Mode.HOLLOW, (Predicate<BlockInWorld>)null);
        })).then(Commands.literal("destroy").executes((context) -> {
            return fillBlocks(context.getSource(), BoundingBox.fromCorners(BlockPosArgument.getLoadedBlockPos(context, "from"), BlockPosArgument.getLoadedBlockPos(context, "to")), BlockStateArgument.getBlock(context, "block"), FillCommand.Mode.DESTROY, (Predicate<BlockInWorld>)null);
        }))))));
    }

    // Folia start - region threading
    private static void sendMessage(CommandSourceStack src, CommandSyntaxException ex) {
        src.sendFailure((Component)ex.getRawMessage());
    }
    // Folia end - region threading

    private static int fillBlocks(CommandSourceStack source, BoundingBox range, BlockInput block, FillCommand.Mode mode, @Nullable Predicate<BlockInWorld> filter) throws CommandSyntaxException {
        int i = range.getXSpan() * range.getYSpan() * range.getZSpan();
        int j = source.getLevel().getGameRules().getInt(GameRules.RULE_COMMAND_MODIFICATION_BLOCK_LIMIT);
        if (i > j) {
            throw ERROR_AREA_TOO_LARGE.create(j, i);
        } else {
            List<BlockPos> list = Lists.newArrayList();
            ServerLevel serverLevel = source.getLevel();
            // Folia start - region threading
            int buffer = 32;
            // physics may spill into neighbour chunks, so use a buffer
            serverLevel.loadChunksAsync(
                (range.minX() - buffer) >> 4,
                (range.maxX() + buffer) >> 4,
                (range.minZ() - buffer) >> 4,
                (range.maxZ() + buffer) >> 4,
                net.minecraft.world.level.chunk.ChunkStatus.FULL,
                ca.spottedleaf.concurrentutil.executor.standard.PrioritisedExecutor.Priority.NORMAL,
                (chunks) -> {
                    try { // Folia end - region threading
            int k = 0;

            for(BlockPos blockPos : BlockPos.betweenClosed(range.minX(), range.minY(), range.minZ(), range.maxX(), range.maxY(), range.maxZ())) {
                if (filter == null || filter.test(new BlockInWorld(serverLevel, blockPos, true))) {
                    BlockInput blockInput = mode.filter.filter(range, blockPos, block, serverLevel);
                    if (blockInput != null) {
                        BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);
                        Clearable.tryClear(blockEntity);
                        if (blockInput.place(serverLevel, blockPos, 2)) {
                            list.add(blockPos.immutable());
                            ++k;
                        }
                    }
                }
            }

            for(BlockPos blockPos2 : list) {
                Block block2 = serverLevel.getBlockState(blockPos2).getBlock();
                serverLevel.blockUpdated(blockPos2, block2);
            }

            if (k == 0) {
                throw ERROR_FAILED.create();
            } else {
                int l = k;
                source.sendSuccess(() -> {
                    return Component.translatable("commands.fill.success", l);
                }, true);
                return; // Folia - region threading
            }
            // Folia start - region threading
                } catch (CommandSyntaxException ex) {
                    sendMessage(source, ex);
                }
            }); return 0; // Folia end - region threading
        }
    }

    static enum Mode {
        REPLACE((range, pos, block, world) -> {
            return block;
        }),
        OUTLINE((range, pos, block, world) -> {
            return pos.getX() != range.minX() && pos.getX() != range.maxX() && pos.getY() != range.minY() && pos.getY() != range.maxY() && pos.getZ() != range.minZ() && pos.getZ() != range.maxZ() ? null : block;
        }),
        HOLLOW((range, pos, block, world) -> {
            return pos.getX() != range.minX() && pos.getX() != range.maxX() && pos.getY() != range.minY() && pos.getY() != range.maxY() && pos.getZ() != range.minZ() && pos.getZ() != range.maxZ() ? FillCommand.HOLLOW_CORE : block;
        }),
        DESTROY((range, pos, block, world) -> {
            world.destroyBlock(pos, true);
            return block;
        });

        public final SetBlockCommand.Filter filter;

        private Mode(SetBlockCommand.Filter filter) {
            this.filter = filter;
        }
    }
}
