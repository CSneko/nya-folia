package net.minecraft.server;

import com.mojang.logging.LogUtils;
import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.util.datafix.fixes.BlockStateData;
import net.minecraft.util.datafix.fixes.ItemIdFix;
import net.minecraft.util.datafix.fixes.ItemSpawnEggFix;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.FireBlock;
import org.slf4j.Logger;

public class Bootstrap {

    public static final PrintStream STDOUT = System.out;
    private static volatile boolean isBootstrapped;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final AtomicLong bootstrapDuration = new AtomicLong(-1L);

    public Bootstrap() {}

    public static void bootStrap() {
        if (!Bootstrap.isBootstrapped) {
            // CraftBukkit start
            /*String name = Bootstrap.class.getSimpleName(); // Paper
            switch (name) {
                case "DispenserRegistry":
                    break;
                case "Bootstrap":
                    System.err.println("***************************************************************************");
                    System.err.println("*** WARNING: This server jar may only be used for development purposes. ***");
                    System.err.println("***************************************************************************");
                    break;
                default:
                    System.err.println("**********************************************************************");
                    System.err.println("*** WARNING: This server jar is unsupported, use at your own risk. ***");
                    System.err.println("**********************************************************************");
                    break;
            }*/ // Paper
            // CraftBukkit end
            Bootstrap.isBootstrapped = true;
            Instant instant = Instant.now();

            if (BuiltInRegistries.REGISTRY.keySet().isEmpty()) {
                throw new IllegalStateException("Unable to load registries");
            } else {
                FireBlock.bootStrap();
                ComposterBlock.bootStrap();
                if (EntityType.getKey(EntityType.PLAYER) == null) {
                    throw new IllegalStateException("Failed loading EntityTypes");
                } else {
                    PotionBrewing.bootStrap();
                    EntitySelectorOptions.bootStrap();
                    DispenseItemBehavior.bootStrap();
                    CauldronInteraction.bootStrap();
                    // Paper start
                    BuiltInRegistries.bootStrap(() -> {
                        io.papermc.paper.world.worldgen.OptionallyFlatBedrockConditionSource.bootstrap(); // Paper - optional flat bedrock
                        io.papermc.paper.plugin.entrypoint.LaunchEntryPointHandler.enterBootstrappers(); // Paper - Entrypoint for bootstrapping
                    });
                    // Paper end
                    CreativeModeTabs.validate();
                    Bootstrap.wrapStreams();
                    Bootstrap.bootstrapDuration.set(Duration.between(instant, Instant.now()).toMillis());
                }
                // CraftBukkit start - easier than fixing the decompile
                BlockStateData.register(1008, "{Name:'minecraft:oak_sign',Properties:{rotation:'0'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'0'}}");
                BlockStateData.register(1009, "{Name:'minecraft:oak_sign',Properties:{rotation:'1'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'1'}}");
                BlockStateData.register(1010, "{Name:'minecraft:oak_sign',Properties:{rotation:'2'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'2'}}");
                BlockStateData.register(1011, "{Name:'minecraft:oak_sign',Properties:{rotation:'3'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'3'}}");
                BlockStateData.register(1012, "{Name:'minecraft:oak_sign',Properties:{rotation:'4'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'4'}}");
                BlockStateData.register(1013, "{Name:'minecraft:oak_sign',Properties:{rotation:'5'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'5'}}");
                BlockStateData.register(1014, "{Name:'minecraft:oak_sign',Properties:{rotation:'6'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'6'}}");
                BlockStateData.register(1015, "{Name:'minecraft:oak_sign',Properties:{rotation:'7'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'7'}}");
                BlockStateData.register(1016, "{Name:'minecraft:oak_sign',Properties:{rotation:'8'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'8'}}");
                BlockStateData.register(1017, "{Name:'minecraft:oak_sign',Properties:{rotation:'9'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'9'}}");
                BlockStateData.register(1018, "{Name:'minecraft:oak_sign',Properties:{rotation:'10'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'10'}}");
                BlockStateData.register(1019, "{Name:'minecraft:oak_sign',Properties:{rotation:'11'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'11'}}");
                BlockStateData.register(1020, "{Name:'minecraft:oak_sign',Properties:{rotation:'12'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'12'}}");
                BlockStateData.register(1021, "{Name:'minecraft:oak_sign',Properties:{rotation:'13'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'13'}}");
                BlockStateData.register(1022, "{Name:'minecraft:oak_sign',Properties:{rotation:'14'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'14'}}");
                BlockStateData.register(1023, "{Name:'minecraft:oak_sign',Properties:{rotation:'15'}}", "{Name:'minecraft:standing_sign',Properties:{rotation:'15'}}");
                ItemIdFix.ITEM_NAMES.put(323, "minecraft:oak_sign");

                BlockStateData.register(1440, "{Name:\'minecraft:portal\',Properties:{axis:\'x\'}}", new String[]{"{Name:\'minecraft:portal\',Properties:{axis:\'x\'}}"});

                ItemIdFix.ITEM_NAMES.put(409, "minecraft:prismarine_shard");
                ItemIdFix.ITEM_NAMES.put(410, "minecraft:prismarine_crystals");
                ItemIdFix.ITEM_NAMES.put(411, "minecraft:rabbit");
                ItemIdFix.ITEM_NAMES.put(412, "minecraft:cooked_rabbit");
                ItemIdFix.ITEM_NAMES.put(413, "minecraft:rabbit_stew");
                ItemIdFix.ITEM_NAMES.put(414, "minecraft:rabbit_foot");
                ItemIdFix.ITEM_NAMES.put(415, "minecraft:rabbit_hide");
                ItemIdFix.ITEM_NAMES.put(416, "minecraft:armor_stand");

                ItemIdFix.ITEM_NAMES.put(423, "minecraft:mutton");
                ItemIdFix.ITEM_NAMES.put(424, "minecraft:cooked_mutton");
                ItemIdFix.ITEM_NAMES.put(425, "minecraft:banner");
                ItemIdFix.ITEM_NAMES.put(426, "minecraft:end_crystal");
                ItemIdFix.ITEM_NAMES.put(427, "minecraft:spruce_door");
                ItemIdFix.ITEM_NAMES.put(428, "minecraft:birch_door");
                ItemIdFix.ITEM_NAMES.put(429, "minecraft:jungle_door");
                ItemIdFix.ITEM_NAMES.put(430, "minecraft:acacia_door");
                ItemIdFix.ITEM_NAMES.put(431, "minecraft:dark_oak_door");
                ItemIdFix.ITEM_NAMES.put(432, "minecraft:chorus_fruit");
                ItemIdFix.ITEM_NAMES.put(433, "minecraft:chorus_fruit_popped");
                ItemIdFix.ITEM_NAMES.put(434, "minecraft:beetroot");
                ItemIdFix.ITEM_NAMES.put(435, "minecraft:beetroot_seeds");
                ItemIdFix.ITEM_NAMES.put(436, "minecraft:beetroot_soup");
                ItemIdFix.ITEM_NAMES.put(437, "minecraft:dragon_breath");
                ItemIdFix.ITEM_NAMES.put(438, "minecraft:splash_potion");
                ItemIdFix.ITEM_NAMES.put(439, "minecraft:spectral_arrow");
                ItemIdFix.ITEM_NAMES.put(440, "minecraft:tipped_arrow");
                ItemIdFix.ITEM_NAMES.put(441, "minecraft:lingering_potion");
                ItemIdFix.ITEM_NAMES.put(442, "minecraft:shield");
                ItemIdFix.ITEM_NAMES.put(443, "minecraft:elytra");
                ItemIdFix.ITEM_NAMES.put(444, "minecraft:spruce_boat");
                ItemIdFix.ITEM_NAMES.put(445, "minecraft:birch_boat");
                ItemIdFix.ITEM_NAMES.put(446, "minecraft:jungle_boat");
                ItemIdFix.ITEM_NAMES.put(447, "minecraft:acacia_boat");
                ItemIdFix.ITEM_NAMES.put(448, "minecraft:dark_oak_boat");
                ItemIdFix.ITEM_NAMES.put(449, "minecraft:totem_of_undying");
                ItemIdFix.ITEM_NAMES.put(450, "minecraft:shulker_shell");
                ItemIdFix.ITEM_NAMES.put(452, "minecraft:iron_nugget");
                ItemIdFix.ITEM_NAMES.put(453, "minecraft:knowledge_book");

                ItemSpawnEggFix.ID_TO_ENTITY[23] = "Arrow";
                // CraftBukkit end
            }
        }
    }

    private static <T> void checkTranslations(Iterable<T> registry, Function<T, String> keyExtractor, Set<String> translationKeys) {
        Language localelanguage = Language.getInstance();

        registry.forEach((object) -> {
            String s = (String) keyExtractor.apply(object);

            if (!localelanguage.has(s)) {
                translationKeys.add(s);
            }

        });
    }

    private static void checkGameruleTranslations(final Set<String> translations) {
        final Language localelanguage = Language.getInstance();

        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                if (!localelanguage.has(key.getDescriptionId())) {
                    translations.add(key.getId());
                }

            }
        });
    }

    public static Set<String> getMissingTranslations() {
        Set<String> set = new TreeSet();

        Bootstrap.checkTranslations(BuiltInRegistries.ATTRIBUTE, Attribute::getDescriptionId, set);
        Bootstrap.checkTranslations(BuiltInRegistries.ENTITY_TYPE, EntityType::getDescriptionId, set);
        Bootstrap.checkTranslations(BuiltInRegistries.MOB_EFFECT, MobEffect::getDescriptionId, set);
        Bootstrap.checkTranslations(BuiltInRegistries.ITEM, Item::getDescriptionId, set);
        Bootstrap.checkTranslations(BuiltInRegistries.ENCHANTMENT, Enchantment::getDescriptionId, set);
        Bootstrap.checkTranslations(BuiltInRegistries.BLOCK, Block::getDescriptionId, set);
        Bootstrap.checkTranslations(BuiltInRegistries.CUSTOM_STAT, (minecraftkey) -> {
            String s = minecraftkey.toString();

            return "stat." + s.replace(':', '.');
        }, set);
        Bootstrap.checkGameruleTranslations(set);
        return set;
    }

    public static void checkBootstrapCalled(Supplier<String> callerGetter) {
        if (!Bootstrap.isBootstrapped) {
            throw Bootstrap.createBootstrapException(callerGetter);
        }
    }

    private static RuntimeException createBootstrapException(Supplier<String> callerGetter) {
        try {
            String s = (String) callerGetter.get();

            return new IllegalArgumentException("Not bootstrapped (called from " + s + ")");
        } catch (Exception exception) {
            IllegalArgumentException illegalargumentexception = new IllegalArgumentException("Not bootstrapped (failed to resolve location)");

            illegalargumentexception.addSuppressed(exception);
            return illegalargumentexception;
        }
    }

    public static void validate() {
        Bootstrap.checkBootstrapCalled(() -> {
            return "validate";
        });
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            Bootstrap.getMissingTranslations().forEach((s) -> {
                Bootstrap.LOGGER.error("Missing translations: {}", s);
            });
            Commands.validate();
        }

        DefaultAttributes.validate();
    }

    private static void wrapStreams() {
        if (Bootstrap.LOGGER.isDebugEnabled()) {
            System.setErr(new DebugLoggedPrintStream("STDERR", System.err));
            System.setOut(new DebugLoggedPrintStream("STDOUT", Bootstrap.STDOUT));
        } else {
            System.setErr(new LoggedPrintStream("STDERR", System.err));
            System.setOut(new LoggedPrintStream("STDOUT", Bootstrap.STDOUT));
        }

    }

    public static void realStdoutPrintln(String str) {
        Bootstrap.STDOUT.println(str);
    }
}
