package net.minecraft.server.commands;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic4CommandExceptionType;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.scores.Team;

public class SpreadPlayersCommand {

    private static final int MAX_ITERATION_COUNT = 10000;
    private static final Dynamic4CommandExceptionType ERROR_FAILED_TO_SPREAD_TEAMS = new Dynamic4CommandExceptionType((object, object1, object2, object3) -> {
        return Component.translatable("commands.spreadplayers.failed.teams", object, object1, object2, object3);
    });
    private static final Dynamic4CommandExceptionType ERROR_FAILED_TO_SPREAD_ENTITIES = new Dynamic4CommandExceptionType((object, object1, object2, object3) -> {
        return Component.translatable("commands.spreadplayers.failed.entities", object, object1, object2, object3);
    });
    private static final Dynamic2CommandExceptionType ERROR_INVALID_MAX_HEIGHT = new Dynamic2CommandExceptionType((object, object1) -> {
        return Component.translatable("commands.spreadplayers.failed.invalid.height", object, object1);
    });

    public SpreadPlayersCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("spreadplayers").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        })).then(net.minecraft.commands.Commands.argument("center", Vec2Argument.vec2()).then(net.minecraft.commands.Commands.argument("spreadDistance", FloatArgumentType.floatArg(0.0F)).then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("maxRange", FloatArgumentType.floatArg(1.0F)).then(net.minecraft.commands.Commands.argument("respectTeams", BoolArgumentType.bool()).then(net.minecraft.commands.Commands.argument("targets", EntityArgument.entities()).executes((commandcontext) -> {
            return SpreadPlayersCommand.spreadPlayers((CommandSourceStack) commandcontext.getSource(), Vec2Argument.getVec2(commandcontext, "center"), FloatArgumentType.getFloat(commandcontext, "spreadDistance"), FloatArgumentType.getFloat(commandcontext, "maxRange"), ((CommandSourceStack) commandcontext.getSource()).getLevel().getMaxBuildHeight(), BoolArgumentType.getBool(commandcontext, "respectTeams"), EntityArgument.getEntities(commandcontext, "targets"));
        })))).then(net.minecraft.commands.Commands.literal("under").then(net.minecraft.commands.Commands.argument("maxHeight", IntegerArgumentType.integer()).then(net.minecraft.commands.Commands.argument("respectTeams", BoolArgumentType.bool()).then(net.minecraft.commands.Commands.argument("targets", EntityArgument.entities()).executes((commandcontext) -> {
            return SpreadPlayersCommand.spreadPlayers((CommandSourceStack) commandcontext.getSource(), Vec2Argument.getVec2(commandcontext, "center"), FloatArgumentType.getFloat(commandcontext, "spreadDistance"), FloatArgumentType.getFloat(commandcontext, "maxRange"), IntegerArgumentType.getInteger(commandcontext, "maxHeight"), BoolArgumentType.getBool(commandcontext, "respectTeams"), EntityArgument.getEntities(commandcontext, "targets"));
        })))))))));
    }

    private static int spreadPlayers(CommandSourceStack source, Vec2 center, float spreadDistance, float maxRange, int maxY, boolean respectTeams, Collection<? extends Entity> players) throws CommandSyntaxException {
        ServerLevel worldserver = source.getLevel();
        int j = worldserver.getMinBuildHeight();

        if (maxY < j) {
            throw SpreadPlayersCommand.ERROR_INVALID_MAX_HEIGHT.create(maxY, j);
        } else {
            RandomSource randomsource = RandomSource.create();
            double d0 = (double) (center.x - maxRange);
            double d1 = (double) (center.y - maxRange);
            double d2 = (double) (center.x + maxRange);
            double d3 = (double) (center.y + maxRange);
            SpreadPlayersCommand.Position[] acommandspreadplayers_a = SpreadPlayersCommand.createInitialPositions(randomsource, respectTeams ? SpreadPlayersCommand.getNumberOfTeams(players) : players.size(), d0, d1, d2, d3);

            SpreadPlayersCommand.spreadPositions(center, (double) spreadDistance, worldserver, randomsource, d0, d1, d2, d3, maxY, acommandspreadplayers_a, respectTeams);
            double d4 = SpreadPlayersCommand.setPlayerPositions(players, worldserver, acommandspreadplayers_a, maxY, respectTeams);

            source.sendSuccess(() -> {
                return Component.translatable("commands.spreadplayers.success." + (respectTeams ? "teams" : "entities"), acommandspreadplayers_a.length, center.x, center.y, String.format(Locale.ROOT, "%.2f", d4));
            }, true);
            return acommandspreadplayers_a.length;
        }
    }

    private static int getNumberOfTeams(Collection<? extends Entity> entities) {
        Set<Team> set = Sets.newHashSet();
        Iterator iterator = entities.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            if (entity instanceof Player) {
                set.add(entity.getTeam());
            } else {
                set.add((Team) null); // CraftBukkit - decompile error
            }
        }

        return set.size();
    }

    private static void spreadPositions(Vec2 center, double spreadDistance, ServerLevel world, RandomSource random, double minX, double minZ, double maxX, double maxZ, int maxY, SpreadPlayersCommand.Position[] piles, boolean respectTeams) throws CommandSyntaxException {
        boolean flag1 = true;
        double d5 = 3.4028234663852886E38D;

        int j;

        for (j = 0; j < 10000 && flag1; ++j) {
            flag1 = false;
            d5 = 3.4028234663852886E38D;

            int k;
            SpreadPlayersCommand.Position commandspreadplayers_a;

            for (int l = 0; l < piles.length; ++l) {
                SpreadPlayersCommand.Position commandspreadplayers_a1 = piles[l];

                k = 0;
                commandspreadplayers_a = new SpreadPlayersCommand.Position();

                for (int i1 = 0; i1 < piles.length; ++i1) {
                    if (l != i1) {
                        SpreadPlayersCommand.Position commandspreadplayers_a2 = piles[i1];
                        double d6 = commandspreadplayers_a1.dist(commandspreadplayers_a2);

                        d5 = Math.min(d6, d5);
                        if (d6 < spreadDistance) {
                            ++k;
                            commandspreadplayers_a.x += commandspreadplayers_a2.x - commandspreadplayers_a1.x;
                            commandspreadplayers_a.z += commandspreadplayers_a2.z - commandspreadplayers_a1.z;
                        }
                    }
                }

                if (k > 0) {
                    commandspreadplayers_a.x /= (double) k;
                    commandspreadplayers_a.z /= (double) k;
                    double d7 = commandspreadplayers_a.getLength();

                    if (d7 > 0.0D) {
                        commandspreadplayers_a.normalize();
                        commandspreadplayers_a1.moveAway(commandspreadplayers_a);
                    } else {
                        commandspreadplayers_a1.randomize(random, minX, minZ, maxX, maxZ);
                    }

                    flag1 = true;
                }

                if (commandspreadplayers_a1.clamp(minX, minZ, maxX, maxZ)) {
                    flag1 = true;
                }
            }

            if (!flag1) {
                SpreadPlayersCommand.Position[] acommandspreadplayers_a1 = piles;
                int j1 = piles.length;

                for (k = 0; k < j1; ++k) {
                    commandspreadplayers_a = acommandspreadplayers_a1[k];
                    if (!commandspreadplayers_a.isSafe(world, maxY)) {
                        commandspreadplayers_a.randomize(random, minX, minZ, maxX, maxZ);
                        flag1 = true;
                    }
                }
            }
        }

        if (d5 == 3.4028234663852886E38D) {
            d5 = 0.0D;
        }

        if (j >= 10000) {
            if (respectTeams) {
                throw SpreadPlayersCommand.ERROR_FAILED_TO_SPREAD_TEAMS.create(piles.length, center.x, center.y, String.format(Locale.ROOT, "%.2f", d5));
            } else {
                throw SpreadPlayersCommand.ERROR_FAILED_TO_SPREAD_ENTITIES.create(piles.length, center.x, center.y, String.format(Locale.ROOT, "%.2f", d5));
            }
        }
    }

    private static double setPlayerPositions(Collection<? extends Entity> entities, ServerLevel world, SpreadPlayersCommand.Position[] piles, int maxY, boolean respectTeams) {
        double d0 = 0.0D;
        int j = 0;
        Map<Team, SpreadPlayersCommand.Position> map = Maps.newHashMap();

        double d1;

        for (Iterator iterator = entities.iterator(); iterator.hasNext(); d0 += d1) {
            Entity entity = (Entity) iterator.next();
            SpreadPlayersCommand.Position commandspreadplayers_a;

            if (respectTeams) {
                Team scoreboardteambase = entity instanceof Player ? entity.getTeam() : null;

                if (!map.containsKey(scoreboardteambase)) {
                    map.put(scoreboardteambase, piles[j++]);
                }

                commandspreadplayers_a = (SpreadPlayersCommand.Position) map.get(scoreboardteambase);
            } else {
                commandspreadplayers_a = piles[j++];
            }

            entity.teleportTo(world, (double) Mth.floor(commandspreadplayers_a.x) + 0.5D, (double) commandspreadplayers_a.getSpawnY(world, maxY), (double) Mth.floor(commandspreadplayers_a.z) + 0.5D, Set.of(), entity.getYRot(), entity.getXRot(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.COMMAND); // CraftBukkit - handle teleport reason
            d1 = Double.MAX_VALUE;
            SpreadPlayersCommand.Position[] acommandspreadplayers_a1 = piles;
            int k = piles.length;

            for (int l = 0; l < k; ++l) {
                SpreadPlayersCommand.Position commandspreadplayers_a1 = acommandspreadplayers_a1[l];

                if (commandspreadplayers_a != commandspreadplayers_a1) {
                    double d2 = commandspreadplayers_a.dist(commandspreadplayers_a1);

                    d1 = Math.min(d2, d1);
                }
            }
        }

        if (entities.size() < 2) {
            return 0.0D;
        } else {
            d0 /= (double) entities.size();
            return d0;
        }
    }

    private static SpreadPlayersCommand.Position[] createInitialPositions(RandomSource random, int count, double minX, double minZ, double maxX, double maxZ) {
        SpreadPlayersCommand.Position[] acommandspreadplayers_a = new SpreadPlayersCommand.Position[count];

        for (int j = 0; j < acommandspreadplayers_a.length; ++j) {
            SpreadPlayersCommand.Position commandspreadplayers_a = new SpreadPlayersCommand.Position();

            commandspreadplayers_a.randomize(random, minX, minZ, maxX, maxZ);
            acommandspreadplayers_a[j] = commandspreadplayers_a;
        }

        return acommandspreadplayers_a;
    }

    private static class Position {

        double x;
        double z;

        Position() {}

        double dist(SpreadPlayersCommand.Position other) {
            double d0 = this.x - other.x;
            double d1 = this.z - other.z;

            return Math.sqrt(d0 * d0 + d1 * d1);
        }

        void normalize() {
            double d0 = this.getLength();

            this.x /= d0;
            this.z /= d0;
        }

        double getLength() {
            return Math.sqrt(this.x * this.x + this.z * this.z);
        }

        public void moveAway(SpreadPlayersCommand.Position other) {
            this.x -= other.x;
            this.z -= other.z;
        }

        public boolean clamp(double minX, double minZ, double maxX, double maxZ) {
            boolean flag = false;

            if (this.x < minX) {
                this.x = minX;
                flag = true;
            } else if (this.x > maxX) {
                this.x = maxX;
                flag = true;
            }

            if (this.z < minZ) {
                this.z = minZ;
                flag = true;
            } else if (this.z > maxZ) {
                this.z = maxZ;
                flag = true;
            }

            return flag;
        }

        public int getSpawnY(BlockGetter blockView, int maxY) {
            BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos(this.x, (double) (maxY + 1), this.z);
            boolean flag = blockView.getBlockState(blockposition_mutableblockposition).isAir();

            blockposition_mutableblockposition.move(Direction.DOWN);

            boolean flag1;

            for (boolean flag2 = blockView.getBlockState(blockposition_mutableblockposition).isAir(); blockposition_mutableblockposition.getY() > blockView.getMinBuildHeight(); flag2 = flag1) {
                blockposition_mutableblockposition.move(Direction.DOWN);
                flag1 = blockView.getBlockState(blockposition_mutableblockposition).isAir();
                if (!flag1 && flag2 && flag) {
                    return blockposition_mutableblockposition.getY() + 1;
                }

                flag = flag2;
            }

            return maxY + 1;
        }

        public boolean isSafe(BlockGetter world, int maxY) {
            BlockPos blockposition = BlockPos.containing(this.x, (double) (this.getSpawnY(world, maxY) - 1), this.z);
            BlockState iblockdata = world.getBlockState(blockposition);

            return blockposition.getY() < maxY && !iblockdata.liquid() && !iblockdata.is(BlockTags.FIRE);
        }

        public void randomize(RandomSource random, double minX, double minZ, double maxX, double maxZ) {
            this.x = Mth.nextDouble(random, minX, maxX);
            this.z = Mth.nextDouble(random, minZ, maxZ);
        }
    }
}
