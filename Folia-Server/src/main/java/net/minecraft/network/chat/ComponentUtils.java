package net.minecraft.network.chat;

import com.google.common.collect.Lists;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFixUtils;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;

public class ComponentUtils {
    public static final String DEFAULT_SEPARATOR_TEXT = ", ";
    public static final Component DEFAULT_SEPARATOR = Component.literal(", ").withStyle(ChatFormatting.GRAY);
    public static final Component DEFAULT_NO_STYLE_SEPARATOR = Component.literal(", ");

    public static MutableComponent mergeStyles(MutableComponent text, Style style) {
        if (style.isEmpty()) {
            return text;
        } else {
            Style style2 = text.getStyle();
            if (style2.isEmpty()) {
                return text.setStyle(style);
            } else {
                return style2.equals(style) ? text : text.setStyle(style2.applyTo(style));
            }
        }
    }

    public static Optional<MutableComponent> updateForEntity(@Nullable CommandSourceStack source, Optional<Component> text, @Nullable Entity sender, int depth) throws CommandSyntaxException {
        return text.isPresent() ? Optional.of(updateForEntity(source, text.get(), sender, depth)) : Optional.empty();
    }

    public static MutableComponent updateForEntity(@Nullable CommandSourceStack source, Component text, @Nullable Entity sender, int depth) throws CommandSyntaxException {
        if (depth > 100) {
            return text.copy();
        } else {
            // Paper start
            if (text instanceof io.papermc.paper.adventure.AdventureComponent adventureComponent) {
                text = adventureComponent.deepConverted();
            }
            // Paper end
            MutableComponent mutableComponent = text.getContents().resolve(source, sender, depth + 1);

            for(Component component : text.getSiblings()) {
                mutableComponent.append(updateForEntity(source, component, sender, depth + 1));
            }

            return mutableComponent.withStyle(resolveStyle(source, text.getStyle(), sender, depth));
        }
    }

    private static Style resolveStyle(@Nullable CommandSourceStack source, Style style, @Nullable Entity sender, int depth) throws CommandSyntaxException {
        HoverEvent hoverEvent = style.getHoverEvent();
        if (hoverEvent != null) {
            Component component = hoverEvent.getValue(HoverEvent.Action.SHOW_TEXT);
            if (component != null) {
                HoverEvent hoverEvent2 = new HoverEvent(HoverEvent.Action.SHOW_TEXT, updateForEntity(source, component, sender, depth + 1));
                return style.withHoverEvent(hoverEvent2);
            }
        }

        return style;
    }

    public static Component formatList(Collection<String> strings) {
        return formatAndSortList(strings, (string) -> {
            return Component.literal(string).withStyle(ChatFormatting.GREEN);
        });
    }

    public static <T extends Comparable<T>> Component formatAndSortList(Collection<T> elements, Function<T, Component> transformer) {
        if (elements.isEmpty()) {
            return CommonComponents.EMPTY;
        } else if (elements.size() == 1) {
            return transformer.apply(elements.iterator().next());
        } else {
            List<T> list = Lists.newArrayList(elements);
            list.sort(Comparable::compareTo);
            return formatList(list, transformer);
        }
    }

    public static <T> Component formatList(Collection<? extends T> elements, Function<T, Component> transformer) {
        return formatList(elements, DEFAULT_SEPARATOR, transformer);
    }

    public static <T> MutableComponent formatList(Collection<? extends T> elements, Optional<? extends Component> separator, Function<T, Component> transformer) {
        return formatList(elements, DataFixUtils.orElse(separator, DEFAULT_SEPARATOR), transformer);
    }

    public static Component formatList(Collection<? extends Component> texts, Component separator) {
        return formatList(texts, separator, Function.identity());
    }

    public static <T> MutableComponent formatList(Collection<? extends T> elements, Component separator, Function<T, Component> transformer) {
        if (elements.isEmpty()) {
            return Component.empty();
        } else if (elements.size() == 1) {
            return transformer.apply(elements.iterator().next()).copy();
        } else {
            MutableComponent mutableComponent = Component.empty();
            boolean bl = true;

            for(T object : elements) {
                if (!bl) {
                    mutableComponent.append(separator);
                }

                mutableComponent.append(transformer.apply(object));
                bl = false;
            }

            return mutableComponent;
        }
    }

    public static MutableComponent wrapInSquareBrackets(Component text) {
        return Component.translatable("chat.square_brackets", text);
    }

    public static Component fromMessage(Message message) {
        return (Component)(message instanceof Component ? (Component)message : Component.literal(message.getString()));
    }

    public static boolean isTranslationResolvable(@Nullable Component text) {
        if (text != null) {
            ComponentContents string = text.getContents();
            if (string instanceof TranslatableContents) {
                TranslatableContents translatableContents = (TranslatableContents)string;
                String string2 = translatableContents.getFallback();
                return string2 != null || Language.getInstance().has(translatableContents.getKey()); // Paper - decompile fix
            }
        }

        return true;
    }

    public static MutableComponent copyOnClickText(String string) {
        return wrapInSquareBrackets(Component.literal(string).withStyle((style) -> {
            return style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, string)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.copy.click"))).withInsertion(string);
        }));
    }
}
