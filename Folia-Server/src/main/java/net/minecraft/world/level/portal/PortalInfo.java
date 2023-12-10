package net.minecraft.world.level.portal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.event.CraftPortalEvent;
// CraftBukkit end

public class PortalInfo {

    public final Vec3 pos;
    public final Vec3 speed;
    public final float yRot;
    public final float xRot;
    // CraftBukkit start
    public final ServerLevel world;
    public final CraftPortalEvent portalEventInfo;

    public PortalInfo(Vec3 vec3d, Vec3 vec3d1, float f, float f1, ServerLevel world, CraftPortalEvent portalEventInfo) {
        this.world = world;
        this.portalEventInfo = portalEventInfo;
        // CraftBukkit end
        this.pos = vec3d;
        this.speed = vec3d1;
        this.yRot = f;
        this.xRot = f1;
    }
}
