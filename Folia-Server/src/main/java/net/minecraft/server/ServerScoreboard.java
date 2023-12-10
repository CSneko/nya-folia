package net.minecraft.server;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreboardSaveData;

public class ServerScoreboard extends Scoreboard {

    private final MinecraftServer server;
    private final Set<Objective> trackedObjectives = Sets.newHashSet();
    private final List<Runnable> dirtyListeners = Lists.newArrayList();

    public ServerScoreboard(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void onScoreChanged(Score score) {
        super.onScoreChanged(score);
        if (this.trackedObjectives.contains(score.getObjective())) {
            this.broadcastAll(new ClientboundSetScorePacket(ServerScoreboard.Method.CHANGE, score.getObjective().getName(), score.getOwner(), score.getScore()));
        }

        this.setDirty();
    }

    @Override
    public void onPlayerRemoved(String playerName) {
        super.onPlayerRemoved(playerName);
        this.broadcastAll(new ClientboundSetScorePacket(ServerScoreboard.Method.REMOVE, (String) null, playerName, 0));
        this.setDirty();
    }

    @Override
    public void onPlayerScoreRemoved(String playerName, Objective objective) {
        super.onPlayerScoreRemoved(playerName, objective);
        if (this.trackedObjectives.contains(objective)) {
            this.broadcastAll(new ClientboundSetScorePacket(ServerScoreboard.Method.REMOVE, objective.getName(), playerName, 0));
        }

        this.setDirty();
    }

    @Override
    public void setDisplayObjective(DisplaySlot slot, @Nullable Objective objective) {
        Objective scoreboardobjective1 = this.getDisplayObjective(slot);

        super.setDisplayObjective(slot, objective);
        if (scoreboardobjective1 != objective && scoreboardobjective1 != null) {
            if (this.getObjectiveDisplaySlotCount(scoreboardobjective1) > 0) {
                this.broadcastAll(new ClientboundSetDisplayObjectivePacket(slot, objective));
            } else {
                this.stopTrackingObjective(scoreboardobjective1);
            }
        }

        if (objective != null) {
            if (this.trackedObjectives.contains(objective)) {
                this.broadcastAll(new ClientboundSetDisplayObjectivePacket(slot, objective));
            } else {
                this.startTrackingObjective(objective);
            }
        }

        this.setDirty();
    }

    @Override
    public boolean addPlayerToTeam(String playerName, PlayerTeam team) {
        if (super.addPlayerToTeam(playerName, team)) {
            this.broadcastAll(ClientboundSetPlayerTeamPacket.createPlayerPacket(team, playerName, ClientboundSetPlayerTeamPacket.Action.ADD));
            this.setDirty();
            return true;
        } else {
            return false;
        }
    }

    // Paper start
    public boolean addPlayersToTeam(java.util.Collection<String> players, PlayerTeam team) {
        boolean anyAdded = false;
        for (String playerName : players) {
            if (super.addPlayerToTeam(playerName, team)) {
                anyAdded = true;
            }
        }

        if (anyAdded) {
            this.broadcastAll(ClientboundSetPlayerTeamPacket.createMultiplePlayerPacket(team, players, ClientboundSetPlayerTeamPacket.Action.ADD));
            this.setDirty();
            return true;
        } else {
            return false;
        }
    }
    // Paper end

    @Override
    public void removePlayerFromTeam(String playerName, PlayerTeam team) {
        super.removePlayerFromTeam(playerName, team);
        this.broadcastAll(ClientboundSetPlayerTeamPacket.createPlayerPacket(team, playerName, ClientboundSetPlayerTeamPacket.Action.REMOVE));
        this.setDirty();
    }

    // Paper start
    public void removePlayersFromTeam(java.util.Collection<String> players, PlayerTeam team) {
        for (String playerName : players) {
            super.removePlayerFromTeam(playerName, team);
        }

        this.broadcastAll(ClientboundSetPlayerTeamPacket.createMultiplePlayerPacket(team, players, ClientboundSetPlayerTeamPacket.Action.REMOVE));
        this.setDirty();
    }
    // Paper end

    @Override
    public void onObjectiveAdded(Objective objective) {
        super.onObjectiveAdded(objective);
        this.setDirty();
    }

    @Override
    public void onObjectiveChanged(Objective objective) {
        super.onObjectiveChanged(objective);
        if (this.trackedObjectives.contains(objective)) {
            this.broadcastAll(new ClientboundSetObjectivePacket(objective, 2));
        }

        this.setDirty();
    }

    @Override
    public void onObjectiveRemoved(Objective objective) {
        super.onObjectiveRemoved(objective);
        if (this.trackedObjectives.contains(objective)) {
            this.stopTrackingObjective(objective);
        }

        this.setDirty();
    }

    @Override
    public void onTeamAdded(PlayerTeam team) {
        super.onTeamAdded(team);
        this.broadcastAll(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
        this.setDirty();
    }

    @Override
    public void onTeamChanged(PlayerTeam team) {
        super.onTeamChanged(team);
        this.broadcastAll(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, false));
        this.setDirty();
    }

    @Override
    public void onTeamRemoved(PlayerTeam team) {
        super.onTeamRemoved(team);
        this.broadcastAll(ClientboundSetPlayerTeamPacket.createRemovePacket(team));
        this.setDirty();
    }

    public void addDirtyListener(Runnable listener) {
        this.dirtyListeners.add(listener);
    }

    protected void setDirty() {
        Iterator iterator = this.dirtyListeners.iterator();

        while (iterator.hasNext()) {
            Runnable runnable = (Runnable) iterator.next();

            runnable.run();
        }

    }

    public List<Packet<?>> getStartTrackingPackets(Objective objective) {
        List<Packet<?>> list = Lists.newArrayList();

        list.add(new ClientboundSetObjectivePacket(objective, 0));
        DisplaySlot[] adisplayslot = DisplaySlot.values();
        int i = adisplayslot.length;

        for (int j = 0; j < i; ++j) {
            DisplaySlot displayslot = adisplayslot[j];

            if (this.getDisplayObjective(displayslot) == objective) {
                list.add(new ClientboundSetDisplayObjectivePacket(displayslot, objective));
            }
        }

        Iterator iterator = this.getPlayerScores(objective).iterator();

        while (iterator.hasNext()) {
            Score scoreboardscore = (Score) iterator.next();

            list.add(new ClientboundSetScorePacket(ServerScoreboard.Method.CHANGE, scoreboardscore.getObjective().getName(), scoreboardscore.getOwner(), scoreboardscore.getScore()));
        }

        return list;
    }

    public void startTrackingObjective(Objective objective) {
        List<Packet<?>> list = this.getStartTrackingPackets(objective);
        Iterator iterator = this.server.getPlayerList().getPlayers().iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();
            if (entityplayer.getBukkitEntity().getScoreboard().getHandle() != this) continue; // CraftBukkit - Only players on this board
            Iterator iterator1 = list.iterator();

            while (iterator1.hasNext()) {
                Packet<?> packet = (Packet) iterator1.next();

                entityplayer.connection.send(packet);
            }
        }

        this.trackedObjectives.add(objective);
    }

    public List<Packet<?>> getStopTrackingPackets(Objective objective) {
        List<Packet<?>> list = Lists.newArrayList();

        list.add(new ClientboundSetObjectivePacket(objective, 1));
        DisplaySlot[] adisplayslot = DisplaySlot.values();
        int i = adisplayslot.length;

        for (int j = 0; j < i; ++j) {
            DisplaySlot displayslot = adisplayslot[j];

            if (this.getDisplayObjective(displayslot) == objective) {
                list.add(new ClientboundSetDisplayObjectivePacket(displayslot, objective));
            }
        }

        return list;
    }

    public void stopTrackingObjective(Objective objective) {
        List<Packet<?>> list = this.getStopTrackingPackets(objective);
        Iterator iterator = this.server.getPlayerList().getPlayers().iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();
            if (entityplayer.getBukkitEntity().getScoreboard().getHandle() != this) continue; // CraftBukkit - Only players on this board
            Iterator iterator1 = list.iterator();

            while (iterator1.hasNext()) {
                Packet<?> packet = (Packet) iterator1.next();

                entityplayer.connection.send(packet);
            }
        }

        this.trackedObjectives.remove(objective);
    }

    public int getObjectiveDisplaySlotCount(Objective objective) {
        int i = 0;
        DisplaySlot[] adisplayslot = DisplaySlot.values();
        int j = adisplayslot.length;

        for (int k = 0; k < j; ++k) {
            DisplaySlot displayslot = adisplayslot[k];

            if (this.getDisplayObjective(displayslot) == objective) {
                ++i;
            }
        }

        return i;
    }

    public SavedData.Factory<ScoreboardSaveData> dataFactory() {
        return new SavedData.Factory<>(this::createData, this::createData, DataFixTypes.SAVED_DATA_SCOREBOARD);
    }

    private ScoreboardSaveData createData() {
        ScoreboardSaveData persistentscoreboard = new ScoreboardSaveData(this);

        Objects.requireNonNull(persistentscoreboard);
        this.addDirtyListener(persistentscoreboard::setDirty);
        return persistentscoreboard;
    }

    private ScoreboardSaveData createData(CompoundTag nbt) {
        return this.createData().load(nbt);
    }

    // CraftBukkit start - Send to players
    private void broadcastAll(Packet packet) {
        for (ServerPlayer entityplayer : (List<ServerPlayer>) this.server.getPlayerList().players) {
            if (entityplayer.getBukkitEntity().getScoreboard().getHandle() == this) {
                entityplayer.connection.send(packet);
            }
        }
    }
    // CraftBukkit end

    public static enum Method {

        CHANGE, REMOVE;

        private Method() {}
    }
}
