package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.minecart.PoweredMinecart;

@SuppressWarnings("deprecation")
public class CraftMinecartFurnace extends CraftMinecart implements PoweredMinecart {
    public CraftMinecartFurnace(CraftServer server, MinecartFurnace entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public MinecartFurnace getHandleRaw() {
        return (MinecartFurnace)this.entity;
    }
    // Folia end - region threading

    @Override
    public MinecartFurnace getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (MinecartFurnace) this.entity;
    }

    @Override
    public int getFuel() {
        return this.getHandle().fuel;
    }

    @Override
    public void setFuel(int fuel) {
        Preconditions.checkArgument(fuel >= 0, "ticks cannot be negative");
        this.getHandle().fuel = fuel;
    }

    // Paper start
    @Override
    public double getPushX() {
        return getHandle().xPush;
    }

    @Override
    public double getPushZ() {
        return getHandle().zPush;
    }

    @Override
    public void setPushX(double xPush) {
        getHandle().xPush = xPush;
    }

    @Override
    public void setPushZ(double zPush) {
        getHandle().zPush = zPush;
    }
    // Paper end

    @Override
    public String toString() {
        return "CraftMinecartFurnace";
    }
}
