package net.minecraft.world.inventory;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;

public class MenuType<T extends AbstractContainerMenu> implements FeatureElement {

    public static final MenuType<ChestMenu> GENERIC_9x1 = MenuType.register("generic_9x1", ChestMenu::oneRow);
    public static final MenuType<ChestMenu> GENERIC_9x2 = MenuType.register("generic_9x2", ChestMenu::twoRows);
    public static final MenuType<ChestMenu> GENERIC_9x3 = MenuType.register("generic_9x3", ChestMenu::threeRows);
    public static final MenuType<ChestMenu> GENERIC_9x4 = MenuType.register("generic_9x4", ChestMenu::fourRows);
    public static final MenuType<ChestMenu> GENERIC_9x5 = MenuType.register("generic_9x5", ChestMenu::fiveRows);
    public static final MenuType<ChestMenu> GENERIC_9x6 = MenuType.register("generic_9x6", ChestMenu::sixRows);
    public static final MenuType<DispenserMenu> GENERIC_3x3 = MenuType.register("generic_3x3", DispenserMenu::new);
    public static final MenuType<AnvilMenu> ANVIL = MenuType.register("anvil", AnvilMenu::new);
    public static final MenuType<BeaconMenu> BEACON = MenuType.register("beacon", BeaconMenu::new);
    public static final MenuType<BlastFurnaceMenu> BLAST_FURNACE = MenuType.register("blast_furnace", BlastFurnaceMenu::new);
    public static final MenuType<BrewingStandMenu> BREWING_STAND = MenuType.register("brewing_stand", BrewingStandMenu::new);
    public static final MenuType<CraftingMenu> CRAFTING = MenuType.register("crafting", CraftingMenu::new);
    public static final MenuType<EnchantmentMenu> ENCHANTMENT = MenuType.register("enchantment", EnchantmentMenu::new);
    public static final MenuType<FurnaceMenu> FURNACE = MenuType.register("furnace", FurnaceMenu::new);
    public static final MenuType<GrindstoneMenu> GRINDSTONE = MenuType.register("grindstone", GrindstoneMenu::new);
    public static final MenuType<HopperMenu> HOPPER = MenuType.register("hopper", HopperMenu::new);
    public static final MenuType<LecternMenu> LECTERN = MenuType.register("lectern", (i, playerinventory) -> {
        return new LecternMenu(i, playerinventory); // CraftBukkit
    });
    public static final MenuType<LoomMenu> LOOM = MenuType.register("loom", LoomMenu::new);
    public static final MenuType<MerchantMenu> MERCHANT = MenuType.register("merchant", MerchantMenu::new);
    public static final MenuType<ShulkerBoxMenu> SHULKER_BOX = MenuType.register("shulker_box", ShulkerBoxMenu::new);
    public static final MenuType<SmithingMenu> SMITHING = MenuType.register("smithing", SmithingMenu::new);
    public static final MenuType<SmokerMenu> SMOKER = MenuType.register("smoker", SmokerMenu::new);
    public static final MenuType<CartographyTableMenu> CARTOGRAPHY_TABLE = MenuType.register("cartography_table", CartographyTableMenu::new);
    public static final MenuType<StonecutterMenu> STONECUTTER = MenuType.register("stonecutter", StonecutterMenu::new);
    private final FeatureFlagSet requiredFeatures;
    private final MenuType.MenuSupplier<T> constructor;

    private static <T extends AbstractContainerMenu> MenuType<T> register(String id, MenuType.MenuSupplier<T> factory) {
        return (MenuType) Registry.register(BuiltInRegistries.MENU, id, new MenuType<>(factory, FeatureFlags.VANILLA_SET));
    }

    private static <T extends AbstractContainerMenu> MenuType<T> register(String id, MenuType.MenuSupplier<T> factory, FeatureFlag... requiredFeatures) {
        return (MenuType) Registry.register(BuiltInRegistries.MENU, id, new MenuType<>(factory, FeatureFlags.REGISTRY.subset(requiredFeatures)));
    }

    private MenuType(MenuType.MenuSupplier<T> factory, FeatureFlagSet requiredFeatures) {
        this.constructor = factory;
        this.requiredFeatures = requiredFeatures;
    }

    public T create(int syncId, Inventory playerInventory) {
        return this.constructor.create(syncId, playerInventory);
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    private interface MenuSupplier<T extends AbstractContainerMenu> {

        T create(int syncId, Inventory playerInventory);
    }
}
