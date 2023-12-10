package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class RunAroundLikeCrazyGoal extends Goal {

    private final AbstractHorse horse;
    private final double speedModifier;
    private double posX;
    private double posY;
    private double posZ;

    public RunAroundLikeCrazyGoal(AbstractHorse horse, double speed) {
        this.horse = horse;
        this.speedModifier = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.horse.isTamed() && this.horse.isVehicle()) {
            Vec3 vec3d = DefaultRandomPos.getPos(this.horse, 5, 4);

            if (vec3d == null) {
                return false;
            } else {
                this.posX = vec3d.x;
                this.posY = vec3d.y;
                this.posZ = vec3d.z;
                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    public void start() {
        this.horse.getNavigation().moveTo(this.posX, this.posY, this.posZ, this.speedModifier);
    }

    @Override
    public boolean canContinueToUse() {
        return !this.horse.isTamed() && !this.horse.getNavigation().isDone() && this.horse.isVehicle();
    }

    @Override
    public void tick() {
        if (!this.horse.isTamed() && this.horse.getRandom().nextInt(this.adjustedTickDelay(50)) == 0) {
            Entity entity = this.horse.getFirstPassenger();

            if (entity == null) {
                return;
            }

            if (entity instanceof Player) {
                Player entityhuman = (Player) entity;
                int i = this.horse.getTemper();
                int j = this.horse.getMaxTemper();

                if (j > 0 && this.horse.getRandom().nextInt(j) < i && !CraftEventFactory.callEntityTameEvent(this.horse, ((CraftHumanEntity) this.horse.getBukkitEntity().getPassenger()).getHandle()).isCancelled()) { // CraftBukkit - fire EntityTameEvent
                    this.horse.tameWithName(entityhuman);
                    return;
                }

                this.horse.modifyTemper(5);
            }

            this.horse.ejectPassengers();
            this.horse.makeMad();
            this.horse.level().broadcastEntityEvent(this.horse, (byte) 6);
        }

    }
}
