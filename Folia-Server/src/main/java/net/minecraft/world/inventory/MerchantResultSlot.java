package net.minecraft.world.inventory;

import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;

public class MerchantResultSlot extends Slot {
    private final MerchantContainer slots;
    private final Player player;
    private int removeCount;
    private final Merchant merchant;

    public MerchantResultSlot(Player player, Merchant merchant, MerchantContainer merchantInventory, int index, int x, int y) {
        super(merchantInventory, index, x, y);
        this.player = player;
        this.merchant = merchant;
        this.slots = merchantInventory;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public ItemStack remove(int amount) {
        if (this.hasItem()) {
            this.removeCount += Math.min(amount, this.getItem().getCount());
        }

        return super.remove(amount);
    }

    @Override
    protected void onQuickCraft(ItemStack stack, int amount) {
        this.removeCount += amount;
        this.checkTakeAchievements(stack);
    }

    @Override
    protected void checkTakeAchievements(ItemStack stack) {
        stack.onCraftedBy(this.player.level(), this.player, this.removeCount);
        this.removeCount = 0;
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        // this.checkTakeAchievements(stack); // Paper - move to after event is called and not cancelled
        MerchantOffer merchantOffer = this.slots.getActiveOffer();
        // Paper start
        io.papermc.paper.event.player.PlayerPurchaseEvent event = null;
        if (merchantOffer != null && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (this.merchant instanceof net.minecraft.world.entity.npc.AbstractVillager abstractVillager) {
                event = new io.papermc.paper.event.player.PlayerTradeEvent(serverPlayer.getBukkitEntity(), (org.bukkit.entity.AbstractVillager) abstractVillager.getBukkitEntity(), merchantOffer.asBukkit(), true, true);
            } else if (this.merchant instanceof org.bukkit.craftbukkit.inventory.CraftMerchantCustom.MinecraftMerchant) {
                event = new io.papermc.paper.event.player.PlayerPurchaseEvent(serverPlayer.getBukkitEntity(), merchantOffer.asBukkit(), false, true);
            }
            if (event != null) {
                if (!event.callEvent()) {
                    stack.setCount(0);
                    event.getPlayer().updateInventory();
                    return;
                }
                merchantOffer = org.bukkit.craftbukkit.inventory.CraftMerchantRecipe.fromBukkit(event.getTrade()).toMinecraft();
            }
        }
        this.checkTakeAchievements(stack);
        // Paper end
        if (merchantOffer != null) {
            ItemStack itemStack = this.slots.getItem(0);
            ItemStack itemStack2 = this.slots.getItem(1);
            if (merchantOffer.take(itemStack, itemStack2) || merchantOffer.take(itemStack2, itemStack)) {
                this.merchant.processTrade(merchantOffer, event); // Paper
                player.awardStat(Stats.TRADED_WITH_VILLAGER);
                this.slots.setItem(0, itemStack);
                this.slots.setItem(1, itemStack2);
            }

            this.merchant.overrideXp(this.merchant.getVillagerXp() + merchantOffer.getXp());
        }

    }
}
