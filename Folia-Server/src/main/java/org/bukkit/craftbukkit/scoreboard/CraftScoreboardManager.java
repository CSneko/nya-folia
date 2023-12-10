package org.bukkit.craftbukkit.scoreboard;

import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.WeakCollection;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.ScoreboardManager;

public final class CraftScoreboardManager implements ScoreboardManager {
    private final CraftScoreboard mainScoreboard;
    private final MinecraftServer server;
    private final Collection<CraftScoreboard> scoreboards = new WeakCollection<>();
    private final Map<CraftPlayer, CraftScoreboard> playerBoards = new HashMap<>();

    public CraftScoreboardManager(MinecraftServer minecraftserver, net.minecraft.world.scores.Scoreboard scoreboardServer) {
        this.mainScoreboard = new CraftScoreboard(scoreboardServer);
        mainScoreboard.registeredGlobally = true; // Paper
        this.server = minecraftserver;
        this.scoreboards.add(this.mainScoreboard);
    }

    @Override
    public CraftScoreboard getMainScoreboard() {
        return this.mainScoreboard;
    }

    @Override
    public CraftScoreboard getNewScoreboard() {
        if (true) throw new UnsupportedOperationException(); // Folia - not supported yet
        org.spigotmc.AsyncCatcher.catchOp("scoreboard creation"); // Spigot
        CraftScoreboard scoreboard = new CraftScoreboard(new ServerScoreboard(this.server));
        // Paper start
        if (io.papermc.paper.configuration.GlobalConfiguration.get().scoreboards.trackPluginScoreboards) {
            scoreboard.registeredGlobally = true;
            scoreboards.add(scoreboard);
        }
        // Paper end
        return scoreboard;
    }

    // Paper start
    public void registerScoreboardForVanilla(CraftScoreboard scoreboard) {
        org.spigotmc.AsyncCatcher.catchOp("scoreboard registration");
        scoreboards.add(scoreboard);
    }
    // Paper end

    // CraftBukkit method
    public CraftScoreboard getPlayerBoard(CraftPlayer player) {
        CraftScoreboard board = this.playerBoards.get(player);
        return board == null ? this.getMainScoreboard() : board;
    }

    // CraftBukkit method
    public void setPlayerBoard(CraftPlayer player, org.bukkit.scoreboard.Scoreboard bukkitScoreboard) {
        if (true) throw new UnsupportedOperationException(); // Folia - not supported yet
        Preconditions.checkArgument(bukkitScoreboard instanceof CraftScoreboard, "Cannot set player scoreboard to an unregistered Scoreboard");

        CraftScoreboard scoreboard = (CraftScoreboard) bukkitScoreboard;
        net.minecraft.world.scores.Scoreboard oldboard = this.getPlayerBoard(player).getHandle();
        net.minecraft.world.scores.Scoreboard newboard = scoreboard.getHandle();
        ServerPlayer entityplayer = player.getHandle();

        if (oldboard == newboard) {
            return;
        }

        if (scoreboard == this.mainScoreboard) {
            this.playerBoards.remove(player);
        } else {
            this.playerBoards.put(player, scoreboard);
        }

        // Old objective tracking
        HashSet<Objective> removed = new HashSet<>();
        for (net.minecraft.world.scores.DisplaySlot slot : net.minecraft.world.scores.DisplaySlot.values()) { // Paper - clear all display slots
            Objective scoreboardobjective = oldboard.getDisplayObjective(slot); // Paper - clear all display slots
            if (scoreboardobjective != null && !removed.contains(scoreboardobjective)) {
                entityplayer.connection.send(new ClientboundSetObjectivePacket(scoreboardobjective, 1));
                removed.add(scoreboardobjective);
            }
        }

        // Old team tracking
        Iterator<?> iterator = oldboard.getPlayerTeams().iterator();
        while (iterator.hasNext()) {
            PlayerTeam scoreboardteam = (PlayerTeam) iterator.next();
            entityplayer.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(scoreboardteam));
        }

        // The above is the reverse of the below method.
        this.server.getPlayerList().updateEntireScoreboard((ServerScoreboard) newboard, player.getHandle());
    }

    // CraftBukkit method
    public void removePlayer(Player player) {
        this.playerBoards.remove(player);
    }

    // CraftBukkit method
    public void getScoreboardScores(ObjectiveCriteria criteria, String name, Consumer<Score> consumer) {
        // Paper start - add timings for scoreboard search
        // plugins leaking scoreboards will make this very expensive, let server owners debug it easily
        co.aikar.timings.MinecraftTimings.scoreboardScoreSearch.startTimingIfSync();
        try {
        // Paper end - add timings for scoreboard search
        for (CraftScoreboard scoreboard : this.scoreboards) {
            Scoreboard board = scoreboard.board;
            board.forAllObjectives(criteria, name, (score) -> consumer.accept(score));
        }
        } finally { // Paper start - add timings for scoreboard search
            co.aikar.timings.MinecraftTimings.scoreboardScoreSearch.stopTimingIfSync();
        }
        // Paper end - add timings for scoreboard search
    }
}
