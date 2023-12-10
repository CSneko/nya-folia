package com.destroystokyo.paper.event.player;

import com.destroystokyo.paper.ClientOption;
import com.destroystokyo.paper.ClientOption.ChatVisibility;
import com.destroystokyo.paper.SkinParts;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.MainHand;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Called when the player changes their client settings
 */
public class PlayerClientOptionsChangeEvent extends PlayerEvent {

    private static final HandlerList handlers = new HandlerList();

    private final String locale;
    private final int viewDistance;
    private final ChatVisibility chatVisibility;
    private final boolean chatColors;
    private final SkinParts skinparts;
    private final MainHand mainHand;
    private final boolean allowsServerListings;
    private final boolean textFilteringEnabled;

    @Deprecated
    public PlayerClientOptionsChangeEvent(@NotNull Player player, @NotNull String locale, int viewDistance, @NotNull ChatVisibility chatVisibility, boolean chatColors, @NotNull SkinParts skinParts, @NotNull MainHand mainHand) {
        super(player);
        this.locale = locale;
        this.viewDistance = viewDistance;
        this.chatVisibility = chatVisibility;
        this.chatColors = chatColors;
        this.skinparts = skinParts;
        this.mainHand = mainHand;
        this.allowsServerListings = false;
        this.textFilteringEnabled = false;
    }

    public PlayerClientOptionsChangeEvent(@NotNull Player player, @NotNull Map<ClientOption<?>, ?> options) {
        super(player);

        this.locale = (String) options.get(ClientOption.LOCALE);
        this.viewDistance = (int) options.get(ClientOption.VIEW_DISTANCE);
        this.chatVisibility = (ChatVisibility) options.get(ClientOption.CHAT_VISIBILITY);
        this.chatColors = (boolean) options.get(ClientOption.CHAT_COLORS_ENABLED);
        this.skinparts = (SkinParts) options.get(ClientOption.SKIN_PARTS);
        this.mainHand = (MainHand) options.get(ClientOption.MAIN_HAND);
        this.allowsServerListings = (boolean) options.get(ClientOption.ALLOW_SERVER_LISTINGS);
        this.textFilteringEnabled = (boolean) options.get(ClientOption.TEXT_FILTERING_ENABLED);
    }

    @NotNull
    public String getLocale() {
        return locale;
    }

    public boolean hasLocaleChanged() {
        return !locale.equals(player.getClientOption(ClientOption.LOCALE));
    }

    public int getViewDistance() {
        return viewDistance;
    }

    public boolean hasViewDistanceChanged() {
        return viewDistance != player.getClientOption(ClientOption.VIEW_DISTANCE);
    }

    @NotNull
    public ChatVisibility getChatVisibility() {
        return chatVisibility;
    }

    public boolean hasChatVisibilityChanged() {
        return chatVisibility != player.getClientOption(ClientOption.CHAT_VISIBILITY);
    }

    public boolean hasChatColorsEnabled() {
        return chatColors;
    }

    public boolean hasChatColorsEnabledChanged() {
        return chatColors != player.getClientOption(ClientOption.CHAT_COLORS_ENABLED);
    }

    @NotNull
    public SkinParts getSkinParts() {
        return skinparts;
    }

    public boolean hasSkinPartsChanged() {
        return skinparts.getRaw() != player.getClientOption(ClientOption.SKIN_PARTS).getRaw();
    }

    @NotNull
    public MainHand getMainHand() {
        return mainHand;
    }

    public boolean hasMainHandChanged() {
        return mainHand != player.getClientOption(ClientOption.MAIN_HAND);
    }

    public boolean allowsServerListings() {
        return allowsServerListings;
    }

    public boolean hasAllowServerListingsChanged() {
        return allowsServerListings != player.getClientOption(ClientOption.ALLOW_SERVER_LISTINGS);
    }

    public boolean hasTextFilteringEnabled() {
        return textFilteringEnabled;
    }

    public boolean hasTextFilteringChanged() {
        return textFilteringEnabled != player.getClientOption(ClientOption.TEXT_FILTERING_ENABLED);
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
