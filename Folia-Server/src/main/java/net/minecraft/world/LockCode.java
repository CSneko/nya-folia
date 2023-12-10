package net.minecraft.world;

import javax.annotation.concurrent.Immutable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

// CraftBukkit start
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.util.CraftChatMessage;
// CraftBukkit end

@Immutable
public class LockCode {

    public static final LockCode NO_LOCK = new LockCode("");
    public static final String TAG_LOCK = "Lock";
    public final String key;

    public LockCode(String key) {
        this.key = key;
    }

    public boolean unlocksWith(ItemStack stack) {
        // CraftBukkit start - SPIGOT-6307: Check for color codes if the lock contains color codes
        if (this.key.isEmpty()) return true;
        if (!stack.isEmpty() && stack.hasCustomHoverName()) {
            if (this.key.indexOf(ChatColor.COLOR_CHAR) == -1) {
                // The lock key contains no color codes, so let's ignore colors in the item display name (vanilla Minecraft behavior):
                return this.key.equals(stack.getHoverName().getString());
            } else {
                // The lock key contains color codes, so let's take them into account:
                return this.key.equals(CraftChatMessage.fromComponent(stack.getHoverName()));
            }
        }
        return false;
        // CraftBukkit end
    }

    public void addToTag(CompoundTag nbt) {
        if (!this.key.isEmpty()) {
            nbt.putString("Lock", this.key);
        }

    }

    public static LockCode fromTag(CompoundTag nbt) {
        return nbt.contains("Lock", 8) ? new LockCode(nbt.getString("Lock")) : LockCode.NO_LOCK;
    }
}
