package net.minecraft.world.level.gameevent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftGameEvent;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.event.world.GenericGameEvent;
// CraftBukkit end

public class GameEventDispatcher {

    private final ServerLevel level;

    public GameEventDispatcher(ServerLevel world) {
        this.level = world;
    }

    public void post(GameEvent event, Vec3 emitterPos, GameEvent.Context emitter) {
        int i = event.getNotificationRadius();
        BlockPos blockposition = BlockPos.containing(emitterPos);
        // CraftBukkit start
        GenericGameEvent event1 = new GenericGameEvent(CraftGameEvent.minecraftToBukkit(event), CraftLocation.toBukkit(blockposition, this.level.getWorld()), (emitter.sourceEntity() == null) ? null : emitter.sourceEntity().getBukkitEntity(), i, !Bukkit.isPrimaryThread());
        this.level.getCraftServer().getPluginManager().callEvent(event1);
        if (event1.isCancelled()) {
            return;
        }
        i = event1.getRadius();
        // CraftBukkit end
        int j = SectionPos.blockToSectionCoord(blockposition.getX() - i);
        int k = SectionPos.blockToSectionCoord(blockposition.getY() - i);
        int l = SectionPos.blockToSectionCoord(blockposition.getZ() - i);
        int i1 = SectionPos.blockToSectionCoord(blockposition.getX() + i);
        int j1 = SectionPos.blockToSectionCoord(blockposition.getY() + i);
        int k1 = SectionPos.blockToSectionCoord(blockposition.getZ() + i);
        List<GameEvent.ListenerInfo> list = new ArrayList();
        GameEventListenerRegistry.ListenerVisitor gameeventlistenerregistry_a = (gameeventlistener, vec3d1) -> {
            if (gameeventlistener.getDeliveryMode() == GameEventListener.DeliveryMode.BY_DISTANCE) {
                list.add(new GameEvent.ListenerInfo(event, emitterPos, emitter, gameeventlistener, vec3d1));
            } else {
                gameeventlistener.handleGameEvent(this.level, event, emitter, emitterPos);
            }

        };
        boolean flag = false;

        for (int l1 = j; l1 <= i1; ++l1) {
            for (int i2 = l; i2 <= k1; ++i2) {
                LevelChunk chunk = (LevelChunk) this.level.getChunkIfLoadedImmediately(l1, i2); // Paper

                if (chunk != null) {
                    for (int j2 = k; j2 <= j1; ++j2) {
                        flag |= chunk.getListenerRegistry(j2).visitInRangeListeners(event, emitterPos, emitter, gameeventlistenerregistry_a);
                    }
                }
            }
        }

        if (!list.isEmpty()) {
            this.handleGameEventMessagesInQueue(list);
        }

        if (flag) {
            DebugPackets.sendGameEventInfo(this.level, event, emitterPos);
        }

    }

    private void handleGameEventMessagesInQueue(List<GameEvent.ListenerInfo> messages) {
        Collections.sort(messages);
        Iterator iterator = messages.iterator();

        while (iterator.hasNext()) {
            GameEvent.ListenerInfo gameevent_b = (GameEvent.ListenerInfo) iterator.next();
            GameEventListener gameeventlistener = gameevent_b.recipient();

            gameeventlistener.handleGameEvent(this.level, gameevent_b.gameEvent(), gameevent_b.context(), gameevent_b.source());
        }

    }
}
