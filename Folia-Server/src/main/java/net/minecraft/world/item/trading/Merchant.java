package net.minecraft.world.item.trading;

import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;

public interface Merchant {

    void setTradingPlayer(@Nullable Player customer);

    @Nullable
    Player getTradingPlayer();

    MerchantOffers getOffers();

    void overrideOffers(MerchantOffers offers);

    default void processTrade(MerchantOffer merchantRecipe, @Nullable io.papermc.paper.event.player.PlayerPurchaseEvent event) { this.notifyTrade(merchantRecipe); } // Paper
    void notifyTrade(MerchantOffer offer);

    void notifyTradeUpdated(ItemStack stack);

    int getVillagerXp();

    void overrideXp(int experience);

    boolean showProgressBar();

    SoundEvent getNotifyTradeSound();

    default boolean canRestock() {
        return false;
    }

    default void openTradingScreen(Player player, Component name, int levelProgress) {
        OptionalInt optionalint = player.openMenu(new SimpleMenuProvider((j, playerinventory, entityhuman1) -> {
            return new MerchantMenu(j, playerinventory, this);
        }, name));

        if (optionalint.isPresent()) {
            MerchantOffers merchantrecipelist = this.getOffers();

            if (!merchantrecipelist.isEmpty()) {
                player.sendMerchantOffers(optionalint.getAsInt(), merchantrecipelist, levelProgress, this.getVillagerXp(), this.showProgressBar(), this.canRestock());
            }
        }

    }

    boolean isClientSide();

    org.bukkit.craftbukkit.inventory.CraftMerchant getCraftMerchant(); // CraftBukkit
}
