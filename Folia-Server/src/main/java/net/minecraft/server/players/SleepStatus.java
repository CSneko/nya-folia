package net.minecraft.server.players;

import java.util.Iterator;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class SleepStatus {

    private int activePlayers;
    private int sleepingPlayers;

    public SleepStatus() {}

    public boolean areEnoughSleeping(int percentage) {
        return this.sleepingPlayers >= this.sleepersNeeded(percentage);
    }

    public boolean areEnoughDeepSleeping(int percentage, List<ServerPlayer> players) {
        // CraftBukkit start
        int j = (int) players.stream().filter((eh) -> { return eh.isSleepingLongEnough() || eh.fauxSleeping; }).count();
        boolean anyDeepSleep = players.stream().anyMatch(Player::isSleepingLongEnough);

        return anyDeepSleep && j >= this.sleepersNeeded(percentage);
        // CraftBukkit end
    }

    public int sleepersNeeded(int percentage) {
        return Math.max(1, Mth.ceil((float) (this.activePlayers * percentage) / 100.0F));
    }

    public void removeAllSleepers() {
        this.sleepingPlayers = 0;
    }

    public int amountSleeping() {
        return this.sleepingPlayers;
    }

    public boolean update(List<ServerPlayer> players) {
        int i = this.activePlayers;
        int j = this.sleepingPlayers;

        this.activePlayers = 0;
        this.sleepingPlayers = 0;
        Iterator iterator = players.iterator();
        boolean anySleep = false; // CraftBukkit

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            if (!entityplayer.isSpectator()) {
                ++this.activePlayers;
                if (entityplayer.isSleeping() || entityplayer.fauxSleeping) { // CraftBukkit
                    ++this.sleepingPlayers;
                }
                // CraftBukkit start
                if (entityplayer.isSleeping()) {
                    anySleep = true;
                }
                // CraftBukkit end
            }
        }

        return anySleep && (j > 0 || this.sleepingPlayers > 0) && (i != this.activePlayers || j != this.sleepingPlayers); // CraftBukkit
    }
}
