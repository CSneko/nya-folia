package net.minecraft.advancements;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
// CraftBukkit start
import org.bukkit.craftbukkit.advancement.CraftAdvancement;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
// CraftBukkit end

public record AdvancementHolder(ResourceLocation id, Advancement value) {

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.id);
        this.value.write(buf);
    }

    public static AdvancementHolder read(FriendlyByteBuf buf) {
        return new AdvancementHolder(buf.readResourceLocation(), Advancement.read(buf));
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else {
            boolean flag;

            if (object instanceof AdvancementHolder) {
                AdvancementHolder advancementholder = (AdvancementHolder) object;

                if (this.id.equals(advancementholder.id)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }
    }

    public int hashCode() {
        return this.id.hashCode();
    }

    public String toString() {
        return this.id.toString();
    }

    // CraftBukkit start
    public final org.bukkit.advancement.Advancement toBukkit() {
        return new CraftAdvancement(this);
    }
    // CraftBukkit end
}
