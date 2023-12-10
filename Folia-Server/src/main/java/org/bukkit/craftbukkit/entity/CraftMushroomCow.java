package org.bukkit.craftbukkit.entity;

import com.google.common.base.Preconditions;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.MushroomCow.Variant;

public class CraftMushroomCow extends CraftCow implements MushroomCow, io.papermc.paper.entity.PaperShearable { // Paper
    public CraftMushroomCow(CraftServer server, net.minecraft.world.entity.animal.MushroomCow entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.animal.MushroomCow getHandleRaw() {
        return (net.minecraft.world.entity.animal.MushroomCow)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.animal.MushroomCow getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.animal.MushroomCow) this.entity;
    }

    @Override
    public Variant getVariant() {
        return Variant.values()[this.getHandle().getVariant().ordinal()];
    }

    @Override
    public void setVariant(Variant variant) {
        Preconditions.checkArgument(variant != null, "variant");

        this.getHandle().setVariant(net.minecraft.world.entity.animal.MushroomCow.MushroomType.values()[variant.ordinal()]);
    }

    // Paper start
    @Override
    public int getStewEffectDuration() {
        throw new UnsupportedOperationException(); // TODO https://github.com/PaperMC/Paper/issues/9742
    }

    @Override
    public void setStewEffectDuration(int duration) {
        throw new UnsupportedOperationException(); // TODO https://github.com/PaperMC/Paper/issues/9742
    }

    @Override
    public org.bukkit.potion.PotionEffectType getStewEffectType() {
        throw new UnsupportedOperationException(); // TODO https://github.com/PaperMC/Paper/issues/9742
    }

    @Override
    public void setStewEffect(org.bukkit.potion.PotionEffectType type) {
        throw new UnsupportedOperationException(); // TODO https://github.com/PaperMC/Paper/issues/9742
    }
    // Paper end

    @Override
    public String toString() {
        return "CraftMushroomCow";
    }
}
