package io.papermc.paper.inventory.item;

import com.destroystokyo.paper.MaterialTags;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.support.AbstractTestingBase;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class MaterialTagsTest extends AbstractTestingBase {

    private final static EnchantmentCategory[] ENCHANTMENT_CATEGORIES = EnchantmentCategory.values();

    @ParameterizedTest
    @MethodSource("items")
    public void testEnchantables(@NotNull final Item item) {
        final List<EnchantmentCategory> enchantableCategories = new ObjectArrayList<>();
        for (final EnchantmentCategory enchantmentCategory : ENCHANTMENT_CATEGORIES) {
            if (enchantmentCategory.canEnchant(item)) enchantableCategories.add(enchantmentCategory);
        }

        final boolean taggedAsEnchantable = MaterialTags.ENCHANTABLE.isTagged(CraftMagicNumbers.getMaterial(item));
        final boolean requiresTagByInternals = !enchantableCategories.isEmpty();
        Assertions.assertEquals(
            requiresTagByInternals,
            taggedAsEnchantable,
            () -> "%s matches enchantment categories [%s] but was tagged by material tags as enchantable: %s".formatted(
                item.getDescriptionId(),
                enchantableCategories.stream().map(Enum::name).collect(Collectors.joining(", ")),
                taggedAsEnchantable
            )
        );
    }

    private static Stream<Item> items() {
        return BuiltInRegistries.ITEM.stream();
    }
}
