package net.minecraft.advancements;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class DisplayInfo {
    private final Component title;
    private final Component description;
    private final ItemStack icon;
    @Nullable
    private final ResourceLocation background;
    private final FrameType frame;
    private final boolean showToast;
    private final boolean announceChat;
    private final boolean hidden;
    private float x;
    private float y;
    public final io.papermc.paper.advancement.AdvancementDisplay paper = new io.papermc.paper.advancement.PaperAdvancementDisplay(this); // Paper

    public DisplayInfo(ItemStack icon, Component title, Component description, @Nullable ResourceLocation background, FrameType frame, boolean showToast, boolean announceToChat, boolean hidden) {
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.background = background;
        this.frame = frame;
        this.showToast = showToast;
        this.announceChat = announceToChat;
        this.hidden = hidden;
    }

    public void setLocation(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Component getTitle() {
        return this.title;
    }

    public Component getDescription() {
        return this.description;
    }

    public ItemStack getIcon() {
        return this.icon;
    }

    @Nullable
    public ResourceLocation getBackground() {
        return this.background;
    }

    public FrameType getFrame() {
        return this.frame;
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public boolean shouldShowToast() {
        return this.showToast;
    }

    public boolean shouldAnnounceChat() {
        return this.announceChat;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public static DisplayInfo fromJson(JsonObject obj) {
        Component component = Component.Serializer.fromJson(obj.get("title"));
        Component component2 = Component.Serializer.fromJson(obj.get("description"));
        if (component != null && component2 != null) {
            ItemStack itemStack = getIcon(GsonHelper.getAsJsonObject(obj, "icon"));
            ResourceLocation resourceLocation = obj.has("background") ? new ResourceLocation(GsonHelper.getAsString(obj, "background")) : null;
            FrameType frameType = obj.has("frame") ? FrameType.byName(GsonHelper.getAsString(obj, "frame")) : FrameType.TASK;
            boolean bl = GsonHelper.getAsBoolean(obj, "show_toast", true);
            boolean bl2 = GsonHelper.getAsBoolean(obj, "announce_to_chat", true);
            boolean bl3 = GsonHelper.getAsBoolean(obj, "hidden", false);
            return new DisplayInfo(itemStack, component, component2, resourceLocation, frameType, bl, bl2, bl3);
        } else {
            throw new JsonSyntaxException("Both title and description must be set");
        }
    }

    private static ItemStack getIcon(JsonObject json) {
        if (!json.has("item")) {
            throw new JsonSyntaxException("Unsupported icon type, currently only items are supported (add 'item' key)");
        } else {
            Holder<Item> holder = GsonHelper.getAsItem(json, "item");
            if (json.has("data")) {
                throw new JsonParseException("Disallowed data tag found");
            } else {
                ItemStack itemStack = new ItemStack(holder);
                if (json.has("nbt")) {
                    try {
                        CompoundTag compoundTag = TagParser.parseTag(GsonHelper.convertToString(json.get("nbt"), "nbt"));
                        itemStack.setTag(compoundTag);
                    } catch (CommandSyntaxException var4) {
                        throw new JsonSyntaxException("Invalid nbt tag: " + var4.getMessage());
                    }
                }

                return itemStack;
            }
        }
    }

    public void serializeToNetwork(FriendlyByteBuf buf) {
        buf.writeComponent(this.title);
        buf.writeComponent(this.description);
        buf.writeItem(this.icon);
        buf.writeEnum(this.frame);
        int i = 0;
        if (this.background != null) {
            i |= 1;
        }

        if (this.showToast) {
            i |= 2;
        }

        if (this.hidden) {
            i |= 4;
        }

        buf.writeInt(i);
        if (this.background != null) {
            buf.writeResourceLocation(this.background);
        }

        buf.writeFloat(this.x);
        buf.writeFloat(this.y);
    }

    public static DisplayInfo fromNetwork(FriendlyByteBuf buf) {
        Component component = buf.readComponent();
        Component component2 = buf.readComponent();
        ItemStack itemStack = buf.readItem();
        FrameType frameType = buf.readEnum(FrameType.class);
        int i = buf.readInt();
        ResourceLocation resourceLocation = (i & 1) != 0 ? buf.readResourceLocation() : null;
        boolean bl = (i & 2) != 0;
        boolean bl2 = (i & 4) != 0;
        DisplayInfo displayInfo = new DisplayInfo(itemStack, component, component2, resourceLocation, frameType, bl, false, bl2);
        displayInfo.setLocation(buf.readFloat(), buf.readFloat());
        return displayInfo;
    }

    public JsonElement serializeToJson() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("icon", this.serializeIcon());
        jsonObject.add("title", Component.Serializer.toJsonTree(this.title));
        jsonObject.add("description", Component.Serializer.toJsonTree(this.description));
        jsonObject.addProperty("frame", this.frame.getName());
        jsonObject.addProperty("show_toast", this.showToast);
        jsonObject.addProperty("announce_to_chat", this.announceChat);
        jsonObject.addProperty("hidden", this.hidden);
        if (this.background != null) {
            jsonObject.addProperty("background", this.background.toString());
        }

        return jsonObject;
    }

    private JsonObject serializeIcon() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("item", BuiltInRegistries.ITEM.getKey(this.icon.getItem()).toString());
        if (this.icon.hasTag()) {
            jsonObject.addProperty("nbt", this.icon.getTag().toString());
        }

        return jsonObject;
    }
}
