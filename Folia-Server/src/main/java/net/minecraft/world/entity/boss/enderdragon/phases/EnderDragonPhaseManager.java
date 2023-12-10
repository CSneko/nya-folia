package net.minecraft.world.entity.boss.enderdragon.phases;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftEnderDragon;
import org.bukkit.event.entity.EnderDragonChangePhaseEvent;
// CraftBukkit end

public class EnderDragonPhaseManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final EnderDragon dragon;
    private final DragonPhaseInstance[] phases = new DragonPhaseInstance[EnderDragonPhase.getCount()];
    @Nullable
    private DragonPhaseInstance currentPhase;

    public EnderDragonPhaseManager(EnderDragon dragon) {
        this.dragon = dragon;
        this.setPhase(EnderDragonPhase.HOVERING);
    }

    public void setPhase(EnderDragonPhase<?> type) {
        if (this.currentPhase == null || type != this.currentPhase.getPhase()) {
            if (this.currentPhase != null) {
                this.currentPhase.end();
            }

            // CraftBukkit start - Call EnderDragonChangePhaseEvent
            EnderDragonChangePhaseEvent event = new EnderDragonChangePhaseEvent(
                    (CraftEnderDragon) this.dragon.getBukkitEntity(),
                    (this.currentPhase == null) ? null : CraftEnderDragon.getBukkitPhase(this.currentPhase.getPhase()),
                    CraftEnderDragon.getBukkitPhase(type)
            );
            this.dragon.level().getCraftServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
            type = CraftEnderDragon.getMinecraftPhase(event.getNewPhase());
            // CraftBukkit end

            this.currentPhase = this.getPhase(type);
            if (!this.dragon.level().isClientSide) {
                this.dragon.getEntityData().set(EnderDragon.DATA_PHASE, type.getId());
            }

            EnderDragonPhaseManager.LOGGER.debug("Dragon is now in phase {} on the {}", type, this.dragon.level().isClientSide ? "client" : "server");
            this.currentPhase.begin();
        }
    }

    public DragonPhaseInstance getCurrentPhase() {
        return this.currentPhase;
    }

    public <T extends DragonPhaseInstance> T getPhase(EnderDragonPhase<T> type) {
        int i = type.getId();

        if (this.phases[i] == null) {
            this.phases[i] = type.createInstance(this.dragon);
        }

        return (T) this.phases[i]; // CraftBukkit - decompile error
    }
}
