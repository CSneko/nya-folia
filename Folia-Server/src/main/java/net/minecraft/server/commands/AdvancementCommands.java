package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.commands.CommandRuntimeException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class AdvancementCommands {
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ADVANCEMENTS = (context, builder) -> {
        Collection<AdvancementHolder> collection = context.getSource().getServer().getAdvancements().getAllAdvancements();
        return SharedSuggestionProvider.suggestResource(collection.stream().map(AdvancementHolder::id), builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("advancement").requires((source) -> {
            return source.hasPermission(2);
        }).then(Commands.literal("grant").then(Commands.argument("targets", EntityArgument.players()).then(Commands.literal("only").then(Commands.argument("advancement", ResourceLocationArgument.id()).suggests(SUGGEST_ADVANCEMENTS).executes((context) -> {
            return perform(context.getSource(), EntityArgument.getPlayers(context, "targets"), AdvancementCommands.Action.GRANT, getAdvancements(context, ResourceLocationArgument.getAdvancement(context, "advancement"), AdvancementCommands.Mode.ONLY));
        }).then(Commands.argument("criterion", StringArgumentType.greedyString()).suggests((context, builder) -> {
            return SharedSuggestionProvider.suggest(ResourceLocationArgument.getAdvancement(context, "advancement").value().criteria().keySet(), builder);
        }).executes((context) -> {
            return performCriterion(context.getSource(), EntityArgument.getPlayers(context, "targets"), AdvancementCommands.Action.GRANT, ResourceLocationArgument.getAdvancement(context, "advancement"), StringArgumentType.getString(context, "criterion"));
        })))).then(Commands.literal("from").then(Commands.argument("advancement", ResourceLocationArgument.id()).suggests(SUGGEST_ADVANCEMENTS).executes((context) -> {
            return perform(context.getSource(), EntityArgument.getPlayers(context, "targets"), AdvancementCommands.Action.GRANT, getAdvancements(context, ResourceLocationArgument.getAdvancement(context, "advancement"), AdvancementCommands.Mode.FROM));
        }))).then(Commands.literal("until").then(Commands.argument("advancement", ResourceLocationArgument.id()).suggests(SUGGEST_ADVANCEMENTS).executes((context) -> {
            return perform(context.getSource(), EntityArgument.getPlayers(context, "targets"), AdvancementCommands.Action.GRANT, getAdvancements(context, ResourceLocationArgument.getAdvancement(context, "advancement"), AdvancementCommands.Mode.UNTIL));
        }))).then(Commands.literal("through").then(Commands.argument("advancement", ResourceLocationArgument.id()).suggests(SUGGEST_ADVANCEMENTS).executes((context) -> {
            return perform(context.getSource(), EntityArgument.getPlayers(context, "targets"), AdvancementCommands.Action.GRANT, getAdvancements(context, ResourceLocationArgument.getAdvancement(context, "advancement"), AdvancementCommands.Mode.THROUGH));
        }))).then(Commands.literal("everything").executes((context) -> {
            return perform(context.getSource(), EntityArgument.getPlayers(context, "targets"), AdvancementCommands.Action.GRANT, context.getSource().getServer().getAdvancements().getAllAdvancements());
        })))).then(Commands.literal("revoke").then(Commands.argument("targets", EntityArgument.players()).then(Commands.literal("only").then(Commands.argument("advancement", ResourceLocationArgument.id()).suggests(SUGGEST_ADVANCEMENTS).executes((context) -> {
            return perform(context.getSource(), EntityArgument.getPlayers(context, "targets"), AdvancementCommands.Action.REVOKE, getAdvancements(context, ResourceLocationArgument.getAdvancement(context, "advancement"), AdvancementCommands.Mode.ONLY));
        }).then(Commands.argument("criterion", StringArgumentType.greedyString()).suggests((context, builder) -> {
            return SharedSuggestionProvider.suggest(ResourceLocationArgument.getAdvancement(context, "advancement").value().criteria().keySet(), builder);
        }).executes((context) -> {
            return performCriterion(context.getSource(), EntityArgument.getPlayers(context, "targets"), AdvancementCommands.Action.REVOKE, ResourceLocationArgument.getAdvancement(context, "advancement"), StringArgumentType.getString(context, "criterion"));
        })))).then(Commands.literal("from").then(Commands.argument("advancement", ResourceLocationArgument.id()).suggests(SUGGEST_ADVANCEMENTS).executes((context) -> {
            return perform(context.getSource(), EntityArgument.getPlayers(context, "targets"), AdvancementCommands.Action.REVOKE, getAdvancements(context, ResourceLocationArgument.getAdvancement(context, "advancement"), AdvancementCommands.Mode.FROM));
        }))).then(Commands.literal("until").then(Commands.argument("advancement", ResourceLocationArgument.id()).suggests(SUGGEST_ADVANCEMENTS).executes((context) -> {
            return perform(context.getSource(), EntityArgument.getPlayers(context, "targets"), AdvancementCommands.Action.REVOKE, getAdvancements(context, ResourceLocationArgument.getAdvancement(context, "advancement"), AdvancementCommands.Mode.UNTIL));
        }))).then(Commands.literal("through").then(Commands.argument("advancement", ResourceLocationArgument.id()).suggests(SUGGEST_ADVANCEMENTS).executes((context) -> {
            return perform(context.getSource(), EntityArgument.getPlayers(context, "targets"), AdvancementCommands.Action.REVOKE, getAdvancements(context, ResourceLocationArgument.getAdvancement(context, "advancement"), AdvancementCommands.Mode.THROUGH));
        }))).then(Commands.literal("everything").executes((context) -> {
            return perform(context.getSource(), EntityArgument.getPlayers(context, "targets"), AdvancementCommands.Action.REVOKE, context.getSource().getServer().getAdvancements().getAllAdvancements());
        })))));
    }

    private static int perform(CommandSourceStack source, Collection<ServerPlayer> targets, AdvancementCommands.Action operation, Collection<AdvancementHolder> selection) {
        int i = 0;

        for(ServerPlayer serverPlayer : targets) {
            i += 1; // Folia - region threading
            serverPlayer.getBukkitEntity().taskScheduler.schedule((ServerPlayer player) -> { // Folia - region threading
            operation.perform(serverPlayer, selection);
            }, null, 1L); // Folia - region threading

        }

        if (i == 0) {
            if (selection.size() == 1) {
                if (targets.size() == 1) {
                    throw new CommandRuntimeException(Component.translatable(operation.getKey() + ".one.to.one.failure", Advancement.name(selection.iterator().next()), targets.iterator().next().getDisplayName()));
                } else {
                    throw new CommandRuntimeException(Component.translatable(operation.getKey() + ".one.to.many.failure", Advancement.name(selection.iterator().next()), targets.size()));
                }
            } else if (targets.size() == 1) {
                throw new CommandRuntimeException(Component.translatable(operation.getKey() + ".many.to.one.failure", selection.size(), targets.iterator().next().getDisplayName()));
            } else {
                throw new CommandRuntimeException(Component.translatable(operation.getKey() + ".many.to.many.failure", selection.size(), targets.size()));
            }
        } else {
            if (selection.size() == 1) {
                if (targets.size() == 1) {
                    source.sendSuccess(() -> {
                        return Component.translatable(operation.getKey() + ".one.to.one.success", Advancement.name(selection.iterator().next()), targets.iterator().next().getDisplayName());
                    }, true);
                } else {
                    source.sendSuccess(() -> {
                        return Component.translatable(operation.getKey() + ".one.to.many.success", Advancement.name(selection.iterator().next()), targets.size());
                    }, true);
                }
            } else if (targets.size() == 1) {
                source.sendSuccess(() -> {
                    return Component.translatable(operation.getKey() + ".many.to.one.success", selection.size(), targets.iterator().next().getDisplayName());
                }, true);
            } else {
                source.sendSuccess(() -> {
                    return Component.translatable(operation.getKey() + ".many.to.many.success", selection.size(), targets.size());
                }, true);
            }

            return i;
        }
    }

    private static int performCriterion(CommandSourceStack source, Collection<ServerPlayer> targets, AdvancementCommands.Action operation, AdvancementHolder advancement, String criterion) {
        int i = 0;
        Advancement advancement2 = advancement.value();
        if (!advancement2.criteria().containsKey(criterion)) {
            throw new CommandRuntimeException(Component.translatable("commands.advancement.criterionNotFound", Advancement.name(advancement), criterion));
        } else {
            for(ServerPlayer serverPlayer : targets) {
                ++i; // Folia - region threading
                serverPlayer.getBukkitEntity().taskScheduler.schedule((ServerPlayer player) -> { // Folia - region threading
                if (operation.performCriterion(serverPlayer, advancement, criterion)) {
                    // Folia - region threading
                }
                }, null, 1L); // Folia - region threading

            }

            if (i == 0) {
                if (targets.size() == 1) {
                    throw new CommandRuntimeException(Component.translatable(operation.getKey() + ".criterion.to.one.failure", criterion, Advancement.name(advancement), targets.iterator().next().getDisplayName()));
                } else {
                    throw new CommandRuntimeException(Component.translatable(operation.getKey() + ".criterion.to.many.failure", criterion, Advancement.name(advancement), targets.size()));
                }
            } else {
                if (targets.size() == 1) {
                    source.sendSuccess(() -> {
                        return Component.translatable(operation.getKey() + ".criterion.to.one.success", criterion, Advancement.name(advancement), targets.iterator().next().getDisplayName());
                    }, true);
                } else {
                    source.sendSuccess(() -> {
                        return Component.translatable(operation.getKey() + ".criterion.to.many.success", criterion, Advancement.name(advancement), targets.size());
                    }, true);
                }

                return i;
            }
        }
    }

    private static List<AdvancementHolder> getAdvancements(CommandContext<CommandSourceStack> context, AdvancementHolder advancement, AdvancementCommands.Mode selection) {
        AdvancementTree advancementTree = context.getSource().getServer().getAdvancements().tree();
        AdvancementNode advancementNode = advancementTree.get(advancement);
        if (advancementNode == null) {
            return List.of(advancement);
        } else {
            List<AdvancementHolder> list = new ArrayList<>();
            if (selection.parents) {
                for(AdvancementNode advancementNode2 = advancementNode.parent(); advancementNode2 != null; advancementNode2 = advancementNode2.parent()) {
                    list.add(advancementNode2.holder());
                }
            }

            list.add(advancement);
            if (selection.children) {
                addChildren(advancementNode, list);
            }

            return list;
        }
    }

    private static void addChildren(AdvancementNode parent, List<AdvancementHolder> childList) {
        for(AdvancementNode advancementNode : parent.children()) {
            childList.add(advancementNode.holder());
            addChildren(advancementNode, childList);
        }

    }

    static enum Action {
        GRANT("grant") {
            @Override
            protected boolean perform(ServerPlayer player, AdvancementHolder advancement) {
                AdvancementProgress advancementProgress = player.getAdvancements().getOrStartProgress(advancement);
                if (advancementProgress.isDone()) {
                    return false;
                } else {
                    for(String string : advancementProgress.getRemainingCriteria()) {
                        player.getAdvancements().award(advancement, string);
                    }

                    return true;
                }
            }

            @Override
            protected boolean performCriterion(ServerPlayer player, AdvancementHolder advancement, String criterion) {
                return player.getAdvancements().award(advancement, criterion);
            }
        },
        REVOKE("revoke") {
            @Override
            protected boolean perform(ServerPlayer player, AdvancementHolder advancement) {
                AdvancementProgress advancementProgress = player.getAdvancements().getOrStartProgress(advancement);
                if (!advancementProgress.hasProgress()) {
                    return false;
                } else {
                    for(String string : advancementProgress.getCompletedCriteria()) {
                        player.getAdvancements().revoke(advancement, string);
                    }

                    return true;
                }
            }

            @Override
            protected boolean performCriterion(ServerPlayer player, AdvancementHolder advancement, String criterion) {
                return player.getAdvancements().revoke(advancement, criterion);
            }
        };

        private final String key;

        Action(String name) {
            this.key = "commands.advancement." + name;
        }

        public int perform(ServerPlayer player, Iterable<AdvancementHolder> advancements) {
            int i = 0;

            for(AdvancementHolder advancementHolder : advancements) {
                if (this.perform(player, advancementHolder)) {
                    ++i;
                }
            }

            return i;
        }

        protected abstract boolean perform(ServerPlayer player, AdvancementHolder advancement);

        protected abstract boolean performCriterion(ServerPlayer player, AdvancementHolder advancement, String criterion);

        protected String getKey() {
            return this.key;
        }
    }

    static enum Mode {
        ONLY(false, false),
        THROUGH(true, true),
        FROM(false, true),
        UNTIL(true, false),
        EVERYTHING(true, true);

        final boolean parents;
        final boolean children;

        private Mode(boolean before, boolean after) {
            this.parents = before;
            this.children = after;
        }
    }
}
