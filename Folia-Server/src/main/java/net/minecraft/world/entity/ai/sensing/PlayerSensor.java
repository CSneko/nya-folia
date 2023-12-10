package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;

public class PlayerSensor extends Sensor<LivingEntity> {
    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.NEAREST_PLAYERS, MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER);
    }

    @Override
    protected void doTick(ServerLevel world, LivingEntity entity) {
        // Paper start - remove streams
        io.papermc.paper.util.player.NearbyPlayers nearbyPlayers = world.chunkSource.chunkMap.getNearbyPlayers();
        net.minecraft.world.phys.Vec3 entityPos = entity.position();
        com.destroystokyo.paper.util.maplist.ReferenceList<net.minecraft.server.level.ServerPlayer> nearby = nearbyPlayers.getPlayersByChunk(
            entity.chunkPosition().x,
            entity.chunkPosition().z,
            io.papermc.paper.util.player.NearbyPlayers.NearbyMapType.GENERAL_REALLY_SMALL
        );

        List<Player> players = new java.util.ArrayList<>(nearby == null ? 0 : nearby.size());
        if (nearby != null) {
            Object[] rawData = nearby.getRawData();
            for (int index = 0, len = nearby.size(); index < len; ++index) {
                net.minecraft.server.level.ServerPlayer player = (net.minecraft.server.level.ServerPlayer)rawData[index];
                if (player.isSpectator()) {
                    continue;
                }
                if (player.distanceToSqr(entityPos.x, entityPos.y, entityPos.z) >= (16.0 * 16.0)) {
                    continue;
                }
                players.add((Player)player);
            }
        }
        players.sort((e1, e2) -> Double.compare(entity.distanceToSqr(e1), entity.distanceToSqr(e2)));
        Brain<?> brain = entity.getBrain();

        brain.setMemory(MemoryModuleType.NEAREST_PLAYERS, players);

        Player firstTargetable = null;
        Player firstAttackable = null;
        for (int index = 0, len = players.size(); index < len; ++index) {
            Player player = players.get(index);
            if (firstTargetable == null && isEntityTargetable(entity, player)) {
                firstTargetable = player;
            }
            if (firstAttackable == null && isEntityAttackable(entity, player)) {
                firstAttackable = player;
            }

            if (firstAttackable != null && firstTargetable != null) {
                break;
            }
        }
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER, firstTargetable);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, Optional.ofNullable(firstAttackable));
        // Paper end - remove streams
    }
}
