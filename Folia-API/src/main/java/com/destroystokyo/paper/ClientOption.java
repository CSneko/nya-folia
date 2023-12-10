package com.destroystokyo.paper;

import net.kyori.adventure.translation.Translatable;
import net.kyori.adventure.util.Index;
import org.jetbrains.annotations.NotNull;

import org.bukkit.inventory.MainHand;

public final class ClientOption<T> {

    public static final ClientOption<SkinParts> SKIN_PARTS = new ClientOption<>(SkinParts.class);
    public static final ClientOption<Boolean> CHAT_COLORS_ENABLED = new ClientOption<>(Boolean.class);
    public static final ClientOption<ChatVisibility> CHAT_VISIBILITY = new ClientOption<>(ChatVisibility.class);
    public static final ClientOption<String> LOCALE = new ClientOption<>(String.class);
    public static final ClientOption<MainHand> MAIN_HAND = new ClientOption<>(MainHand.class);
    public static final ClientOption<Integer> VIEW_DISTANCE = new ClientOption<>(Integer.class);
    public static final ClientOption<Boolean> ALLOW_SERVER_LISTINGS = new ClientOption<>(Boolean.class);
    public static final ClientOption<Boolean> TEXT_FILTERING_ENABLED = new ClientOption<>(Boolean.class);

    private final Class<T> type;

    private ClientOption(@NotNull Class<T> type) {
        this.type = type;
    }

    @NotNull
    public Class<T> getType() {
        return type;
    }

    public enum ChatVisibility implements Translatable {
        FULL("full"),
        SYSTEM("system"),
        HIDDEN("hidden"),
        UNKNOWN("unknown");

        public static Index<String, ChatVisibility> NAMES = Index.create(ChatVisibility.class, chatVisibility -> chatVisibility.name);
        private final String name;

        ChatVisibility(String name) {
            this.name = name;
        }

        @Override
        public @NotNull String translationKey() {
            if (this == UNKNOWN) {
                throw new UnsupportedOperationException(this.name + " doesn't have a translation key");
            }
            return "options.chat.visibility." + this.name;
        }
    }
}
