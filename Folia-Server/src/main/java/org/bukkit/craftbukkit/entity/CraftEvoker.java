package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Evoker;

public class CraftEvoker extends CraftSpellcaster implements Evoker {

    public CraftEvoker(CraftServer server, net.minecraft.world.entity.monster.Evoker entity) {
        super(server, entity);
    }

    // Folia start - region threading
    @Override
    public net.minecraft.world.entity.monster.Evoker getHandleRaw() {
        return (net.minecraft.world.entity.monster.Evoker)this.entity;
    }
    // Folia end - region threading

    @Override
    public net.minecraft.world.entity.monster.Evoker getHandle() {
        io.papermc.paper.util.TickThread.ensureTickThread(this.entity, "Accessing entity state off owning region's thread"); // Folia - region threading
        return (net.minecraft.world.entity.monster.Evoker) super.getHandle();
    }

    @Override
    public String toString() {
        return "CraftEvoker";
    }

    @Override
    public Evoker.Spell getCurrentSpell() {
        return Evoker.Spell.values()[this.getHandle().getCurrentSpell().ordinal()];
    }

    @Override
    public void setCurrentSpell(Evoker.Spell spell) {
        this.getHandle().setIsCastingSpell(spell == null ? SpellcasterIllager.IllagerSpell.NONE : SpellcasterIllager.IllagerSpell.byId(spell.ordinal()));
    }

    // Paper start
    @Override
    public org.bukkit.entity.Sheep getWololoTarget() {
        Sheep sheep = getHandle().getWololoTarget();
        return sheep == null ? null : (org.bukkit.entity.Sheep) sheep.getBukkitEntity();
    }

    @Override
    public void setWololoTarget(org.bukkit.entity.Sheep sheep) {
        getHandle().setWololoTarget(sheep == null ? null : ((org.bukkit.craftbukkit.entity.CraftSheep) sheep).getHandle());
    }
    // Paper end
}
