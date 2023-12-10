package org.bukkit.craftbukkit.entity;

import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.GlowItemFrame;

public class CraftGlowItemFrame extends CraftItemFrame implements GlowItemFrame {

    public CraftGlowItemFrame(CraftServer server, net.minecraft.world.entity.decoration.GlowItemFrame entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.decoration.GlowItemFrame getHandleRaw() {
        return (net.minecraft.world.entity.decoration.GlowItemFrame)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.decoration.GlowItemFrame getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.decoration.GlowItemFrame) super.getHandle();
    }

    @Override
    public String toString() {
        return "CraftGlowItemFrame{item=" + this.getItem() + ", rotation=" + this.getRotation() + "}";
    }
}
