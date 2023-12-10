package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
// CraftBukkit end

public class TemptingSensor extends Sensor<PathfinderMob> {

    public static final int TEMPTATION_RANGE = 10;
    private static final TargetingConditions TEMPT_TARGETING = TargetingConditions.forNonCombat().range(10.0D).ignoreLineOfSight();
    private final Ingredient temptations;

    public TemptingSensor(Ingredient ingredient) {
        this.temptations = ingredient;
    }

    protected void doTick(ServerLevel world, PathfinderMob entity) {
        Brain<?> behaviorcontroller = entity.getBrain();
        Stream<net.minecraft.server.level.ServerPlayer> stream = world.getLocalPlayers().stream().filter(EntitySelector.NO_SPECTATORS).filter((entityplayer) -> { // CraftBukkit - decompile error // Folia - region threading
            return TemptingSensor.TEMPT_TARGETING.test(entity, entityplayer);
        }).filter((entityplayer) -> {
            return entity.closerThan(entityplayer, 10.0D);
        }).filter(this::playerHoldingTemptation).filter((entityplayer) -> {
            return !entity.hasPassenger((Entity) entityplayer);
        });

        Objects.requireNonNull(entity);
        List<Player> list = (List) stream.sorted(Comparator.comparingDouble(entity::distanceToSqr)).collect(Collectors.toList());

        if (!list.isEmpty()) {
            Player entityhuman = (Player) list.get(0);

            // CraftBukkit start
            EntityTargetLivingEntityEvent event = CraftEventFactory.callEntityTargetLivingEvent(entity, entityhuman, EntityTargetEvent.TargetReason.TEMPT);
            if (event.isCancelled()) {
                return;
            }
            if (event.getTarget() instanceof HumanEntity) {
                behaviorcontroller.setMemory(MemoryModuleType.TEMPTING_PLAYER, ((CraftHumanEntity) event.getTarget()).getHandle());
            } else {
                behaviorcontroller.eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
            }
            // CraftBukkit end
        } else {
            behaviorcontroller.eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
        }

    }

    private boolean playerHoldingTemptation(Player player) {
        return this.isTemptation(player.getMainHandItem()) || this.isTemptation(player.getOffhandItem());
    }

    private boolean isTemptation(ItemStack stack) {
        return this.temptations.test(stack);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.TEMPTING_PLAYER);
    }
}
