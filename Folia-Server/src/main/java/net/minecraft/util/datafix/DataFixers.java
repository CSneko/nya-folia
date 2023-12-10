package net.minecraft.util.datafix;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.DataFixerBuilder;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.util.datafix.fixes.*;
import net.minecraft.util.datafix.schemas.NamespacedSchema;
import net.minecraft.util.datafix.schemas.V100;
import net.minecraft.util.datafix.schemas.V102;
import net.minecraft.util.datafix.schemas.V1022;
import net.minecraft.util.datafix.schemas.V106;
import net.minecraft.util.datafix.schemas.V107;
import net.minecraft.util.datafix.schemas.V1125;
import net.minecraft.util.datafix.schemas.V135;
import net.minecraft.util.datafix.schemas.V143;
import net.minecraft.util.datafix.schemas.V1451;
import net.minecraft.util.datafix.schemas.V1451_1;
import net.minecraft.util.datafix.schemas.V1451_2;
import net.minecraft.util.datafix.schemas.V1451_3;
import net.minecraft.util.datafix.schemas.V1451_4;
import net.minecraft.util.datafix.schemas.V1451_5;
import net.minecraft.util.datafix.schemas.V1451_6;
import net.minecraft.util.datafix.schemas.V1460;
import net.minecraft.util.datafix.schemas.V1466;
import net.minecraft.util.datafix.schemas.V1470;
import net.minecraft.util.datafix.schemas.V1481;
import net.minecraft.util.datafix.schemas.V1483;
import net.minecraft.util.datafix.schemas.V1486;
import net.minecraft.util.datafix.schemas.V1510;
import net.minecraft.util.datafix.schemas.V1800;
import net.minecraft.util.datafix.schemas.V1801;
import net.minecraft.util.datafix.schemas.V1904;
import net.minecraft.util.datafix.schemas.V1906;
import net.minecraft.util.datafix.schemas.V1909;
import net.minecraft.util.datafix.schemas.V1920;
import net.minecraft.util.datafix.schemas.V1928;
import net.minecraft.util.datafix.schemas.V1929;
import net.minecraft.util.datafix.schemas.V1931;
import net.minecraft.util.datafix.schemas.V2100;
import net.minecraft.util.datafix.schemas.V2501;
import net.minecraft.util.datafix.schemas.V2502;
import net.minecraft.util.datafix.schemas.V2505;
import net.minecraft.util.datafix.schemas.V2509;
import net.minecraft.util.datafix.schemas.V2519;
import net.minecraft.util.datafix.schemas.V2522;
import net.minecraft.util.datafix.schemas.V2551;
import net.minecraft.util.datafix.schemas.V2568;
import net.minecraft.util.datafix.schemas.V2571;
import net.minecraft.util.datafix.schemas.V2684;
import net.minecraft.util.datafix.schemas.V2686;
import net.minecraft.util.datafix.schemas.V2688;
import net.minecraft.util.datafix.schemas.V2704;
import net.minecraft.util.datafix.schemas.V2707;
import net.minecraft.util.datafix.schemas.V2831;
import net.minecraft.util.datafix.schemas.V2832;
import net.minecraft.util.datafix.schemas.V2842;
import net.minecraft.util.datafix.schemas.V3076;
import net.minecraft.util.datafix.schemas.V3078;
import net.minecraft.util.datafix.schemas.V3081;
import net.minecraft.util.datafix.schemas.V3082;
import net.minecraft.util.datafix.schemas.V3083;
import net.minecraft.util.datafix.schemas.V3202;
import net.minecraft.util.datafix.schemas.V3203;
import net.minecraft.util.datafix.schemas.V3204;
import net.minecraft.util.datafix.schemas.V3325;
import net.minecraft.util.datafix.schemas.V3326;
import net.minecraft.util.datafix.schemas.V3327;
import net.minecraft.util.datafix.schemas.V3328;
import net.minecraft.util.datafix.schemas.V3438;
import net.minecraft.util.datafix.schemas.V3448;
import net.minecraft.util.datafix.schemas.V501;
import net.minecraft.util.datafix.schemas.V700;
import net.minecraft.util.datafix.schemas.V701;
import net.minecraft.util.datafix.schemas.V702;
import net.minecraft.util.datafix.schemas.V703;
import net.minecraft.util.datafix.schemas.V704;
import net.minecraft.util.datafix.schemas.V705;
import net.minecraft.util.datafix.schemas.V808;
import net.minecraft.util.datafix.schemas.V99;

public class DataFixers {

    private static final BiFunction<Integer, Schema, Schema> SAME = Schema::new;
    private static final BiFunction<Integer, Schema, Schema> SAME_NAMESPACED = NamespacedSchema::new;
    private static final DataFixer dataFixer = DataFixers.createFixerUpper(SharedConstants.DATA_FIX_TYPES_TO_OPTIMIZE);
    public static final int BLENDING_VERSION = 3441;

    private DataFixers() {}

    public static DataFixer getDataFixer() {
        return DataFixers.dataFixer;
    }

    private static synchronized DataFixer createFixerUpper(Set<TypeReference> requiredTypes) {
        DataFixerBuilder datafixerbuilder = new DataFixerBuilder(SharedConstants.getCurrentVersion().getDataVersion().getVersion());

        DataFixers.addFixers(datafixerbuilder);
        if (requiredTypes.isEmpty()) {
            return datafixerbuilder.buildUnoptimized();
        } else {
            ExecutorService executorservice = Executors.newSingleThreadExecutor((new ThreadFactoryBuilder()).setNameFormat("Datafixer Bootstrap").setDaemon(true).setPriority(1).build());

            return datafixerbuilder.buildOptimized(requiredTypes, executorservice);
        }
    }

    private static void addFixers(DataFixerBuilder builder) {
        builder.addSchema(99, V99::new);
        Schema schema = builder.addSchema(100, V100::new);

        builder.addFixer(new EntityEquipmentToArmorAndHandFix(schema, true));
        Schema schema1 = builder.addSchema(101, DataFixers.SAME);

        builder.addFixer(new BlockEntitySignTextStrictJsonFix(schema1, false));
        Schema schema2 = builder.addSchema(102, V102::new);

        builder.addFixer(new ItemIdFix(schema2, true));
        builder.addFixer(new ItemPotionFix(schema2, false));
        Schema schema3 = builder.addSchema(105, DataFixers.SAME);

        builder.addFixer(new ItemSpawnEggFix(schema3, true));
        Schema schema4 = builder.addSchema(106, V106::new);

        builder.addFixer(new MobSpawnerEntityIdentifiersFix(schema4, true));
        Schema schema5 = builder.addSchema(107, V107::new);

        builder.addFixer(new EntityMinecartIdentifiersFix(schema5, true));
        Schema schema6 = builder.addSchema(108, DataFixers.SAME);

        builder.addFixer(new EntityStringUuidFix(schema6, true));
        Schema schema7 = builder.addSchema(109, DataFixers.SAME);

        builder.addFixer(new EntityHealthFix(schema7, true));
        Schema schema8 = builder.addSchema(110, DataFixers.SAME);

        builder.addFixer(new EntityHorseSaddleFix(schema8, true));
        Schema schema9 = builder.addSchema(111, DataFixers.SAME);

        builder.addFixer(new EntityPaintingItemFrameDirectionFix(schema9, true));
        Schema schema10 = builder.addSchema(113, DataFixers.SAME);

        builder.addFixer(new EntityRedundantChanceTagsFix(schema10, true));
        Schema schema11 = builder.addSchema(135, V135::new);

        builder.addFixer(new EntityRidingToPassengersFix(schema11, true));
        Schema schema12 = builder.addSchema(143, V143::new);

        builder.addFixer(new EntityTippedArrowFix(schema12, true));
        Schema schema13 = builder.addSchema(147, DataFixers.SAME);

        builder.addFixer(new EntityArmorStandSilentFix(schema13, true));
        Schema schema14 = builder.addSchema(165, DataFixers.SAME);

        builder.addFixer(new ItemWrittenBookPagesStrictJsonFix(schema14, true));
        Schema schema15 = builder.addSchema(501, V501::new);

        builder.addFixer(new AddNewChoices(schema15, "Add 1.10 entities fix", References.ENTITY));
        Schema schema16 = builder.addSchema(502, DataFixers.SAME);

        builder.addFixer(ItemRenameFix.create(schema16, "cooked_fished item renamer", (s) -> {
            return Objects.equals(NamespacedSchema.ensureNamespaced(s), "minecraft:cooked_fished") ? "minecraft:cooked_fish" : s;
        }));
        builder.addFixer(new EntityZombieVillagerTypeFix(schema16, false));
        Schema schema17 = builder.addSchema(505, DataFixers.SAME);

        builder.addFixer(new OptionsForceVBOFix(schema17, false));
        Schema schema18 = builder.addSchema(700, V700::new);

        builder.addFixer(new EntityElderGuardianSplitFix(schema18, true));
        Schema schema19 = builder.addSchema(701, V701::new);

        builder.addFixer(new EntitySkeletonSplitFix(schema19, true));
        Schema schema20 = builder.addSchema(702, V702::new);

        builder.addFixer(new EntityZombieSplitFix(schema20, true));
        Schema schema21 = builder.addSchema(703, V703::new);

        builder.addFixer(new EntityHorseSplitFix(schema21, true));
        Schema schema22 = builder.addSchema(704, V704::new);

        builder.addFixer(new BlockEntityIdFix(schema22, true));
        Schema schema23 = builder.addSchema(705, V705::new);

        builder.addFixer(new EntityIdFix(schema23, true));
        Schema schema24 = builder.addSchema(804, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ItemBannerColorFix(schema24, true));
        Schema schema25 = builder.addSchema(806, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ItemWaterPotionFix(schema25, false));
        Schema schema26 = builder.addSchema(808, V808::new);

        builder.addFixer(new AddNewChoices(schema26, "added shulker box", References.BLOCK_ENTITY));
        Schema schema27 = builder.addSchema(808, 1, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new EntityShulkerColorFix(schema27, false));
        Schema schema28 = builder.addSchema(813, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ItemShulkerBoxColorFix(schema28, false));
        builder.addFixer(new BlockEntityShulkerBoxColorFix(schema28, false));
        Schema schema29 = builder.addSchema(816, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new OptionsLowerCaseLanguageFix(schema29, false));
        Schema schema30 = builder.addSchema(820, DataFixers.SAME_NAMESPACED);

        builder.addFixer(ItemRenameFix.create(schema30, "totem item renamer", DataFixers.createRenamer("minecraft:totem", "minecraft:totem_of_undying")));
        Schema schema31 = builder.addSchema(1022, V1022::new);

        builder.addFixer(new WriteAndReadFix(schema31, "added shoulder entities to players", References.PLAYER));
        Schema schema32 = builder.addSchema(1125, V1125::new);

        builder.addFixer(new ChunkBedBlockEntityInjecterFix(schema32, true));
        builder.addFixer(new BedItemColorFix(schema32, false));
        Schema schema33 = builder.addSchema(1344, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new OptionsKeyLwjgl3Fix(schema33, false));
        Schema schema34 = builder.addSchema(1446, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new OptionsKeyTranslationFix(schema34, false));
        Schema schema35 = builder.addSchema(1450, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new BlockStateStructureTemplateFix(schema35, false));
        Schema schema36 = builder.addSchema(1451, V1451::new);

        builder.addFixer(new AddNewChoices(schema36, "AddTrappedChestFix", References.BLOCK_ENTITY));
        Schema schema37 = builder.addSchema(1451, 1, V1451_1::new);

        builder.addFixer(new ChunkPalettedStorageFix(schema37, true));
        Schema schema38 = builder.addSchema(1451, 2, V1451_2::new);

        builder.addFixer(new BlockEntityBlockStateFix(schema38, true));
        Schema schema39 = builder.addSchema(1451, 3, V1451_3::new);

        builder.addFixer(new EntityBlockStateFix(schema39, true));
        builder.addFixer(new ItemStackMapIdFix(schema39, false));
        Schema schema40 = builder.addSchema(1451, 4, V1451_4::new);

        builder.addFixer(new BlockNameFlatteningFix(schema40, true));
        builder.addFixer(new ItemStackTheFlatteningFix(schema40, false));
        Schema schema41 = builder.addSchema(1451, 5, V1451_5::new);

        builder.addFixer(new ItemRemoveBlockEntityTagFix(schema41, false, Set.of("minecraft:note_block", "minecraft:flower_pot", "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid", "minecraft:allium", "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:orange_tulip", "minecraft:white_tulip", "minecraft:pink_tulip", "minecraft:oxeye_daisy", "minecraft:cactus", "minecraft:brown_mushroom", "minecraft:red_mushroom", "minecraft:oak_sapling", "minecraft:spruce_sapling", "minecraft:birch_sapling", "minecraft:jungle_sapling", "minecraft:acacia_sapling", "minecraft:dark_oak_sapling", "minecraft:dead_bush", "minecraft:fern")));
        builder.addFixer(new AddNewChoices(schema41, "RemoveNoteBlockFlowerPotFix", References.BLOCK_ENTITY));
        builder.addFixer(new ItemStackSpawnEggFix(schema41, false, "minecraft:spawn_egg"));
        builder.addFixer(new EntityWolfColorFix(schema41, false));
        builder.addFixer(new BlockEntityBannerColorFix(schema41, false));
        builder.addFixer(new LevelFlatGeneratorInfoFix(schema41, false));
        Schema schema42 = builder.addSchema(1451, 6, V1451_6::new);

        builder.addFixer(new StatsCounterFix(schema42, true));
        builder.addFixer(new BlockEntityJukeboxFix(schema42, false));
        Schema schema43 = builder.addSchema(1451, 7, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new VillagerTradeFix(schema43, false));
        Schema schema44 = builder.addSchema(1456, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new EntityItemFrameDirectionFix(schema44, false));
        Schema schema45 = builder.addSchema(1458, DataFixers.SAME_NAMESPACED);

        // CraftBukkit start
        builder.addFixer(new com.mojang.datafixers.DataFix(schema45, false) {
            @Override
            protected com.mojang.datafixers.TypeRewriteRule makeRule() {
                return this.fixTypeEverywhereTyped("Player CustomName", this.getInputSchema().getType(References.PLAYER), (typed) -> {
                    return typed.update(DSL.remainderFinder(), (dynamic) -> {
                        return EntityCustomNameToComponentFix.fixTagCustomName(dynamic);
                    });
                });
            }
        });
        // CraftBukkit end
        builder.addFixer(new EntityCustomNameToComponentFix(schema45, false));
        builder.addFixer(new ItemCustomNameToComponentFix(schema45, false));
        builder.addFixer(new BlockEntityCustomNameToComponentFix(schema45, false));
        Schema schema46 = builder.addSchema(1460, V1460::new);

        builder.addFixer(new EntityPaintingMotiveFix(schema46, false));
        Schema schema47 = builder.addSchema(1466, V1466::new);

        builder.addFixer(new AddNewChoices(schema47, "Add DUMMY block entity", References.BLOCK_ENTITY));
        builder.addFixer(new ChunkToProtochunkFix(schema47, true));
        Schema schema48 = builder.addSchema(1470, V1470::new);

        builder.addFixer(new AddNewChoices(schema48, "Add 1.13 entities fix", References.ENTITY));
        Schema schema49 = builder.addSchema(1474, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ColorlessShulkerEntityFix(schema49, false));
        builder.addFixer(BlockRenameFix.create(schema49, "Colorless shulker block fixer", (s) -> {
            return Objects.equals(NamespacedSchema.ensureNamespaced(s), "minecraft:purple_shulker_box") ? "minecraft:shulker_box" : s;
        }));
        builder.addFixer(ItemRenameFix.create(schema49, "Colorless shulker item fixer", (s) -> {
            return Objects.equals(NamespacedSchema.ensureNamespaced(s), "minecraft:purple_shulker_box") ? "minecraft:shulker_box" : s;
        }));
        Schema schema50 = builder.addSchema(1475, DataFixers.SAME_NAMESPACED);

        builder.addFixer(BlockRenameFix.create(schema50, "Flowing fixer", DataFixers.createRenamer(ImmutableMap.of("minecraft:flowing_water", "minecraft:water", "minecraft:flowing_lava", "minecraft:lava"))));
        Schema schema51 = builder.addSchema(1480, DataFixers.SAME_NAMESPACED);

        builder.addFixer(BlockRenameFix.create(schema51, "Rename coral blocks", DataFixers.createRenamer(RenamedCoralFix.RENAMED_IDS)));
        builder.addFixer(ItemRenameFix.create(schema51, "Rename coral items", DataFixers.createRenamer(RenamedCoralFix.RENAMED_IDS)));
        Schema schema52 = builder.addSchema(1481, V1481::new);

        builder.addFixer(new AddNewChoices(schema52, "Add conduit", References.BLOCK_ENTITY));
        Schema schema53 = builder.addSchema(1483, V1483::new);

        builder.addFixer(new EntityPufferfishRenameFix(schema53, true));
        builder.addFixer(ItemRenameFix.create(schema53, "Rename pufferfish egg item", DataFixers.createRenamer(EntityPufferfishRenameFix.RENAMED_IDS)));
        Schema schema54 = builder.addSchema(1484, DataFixers.SAME_NAMESPACED);

        builder.addFixer(ItemRenameFix.create(schema54, "Rename seagrass items", DataFixers.createRenamer(ImmutableMap.of("minecraft:sea_grass", "minecraft:seagrass", "minecraft:tall_sea_grass", "minecraft:tall_seagrass"))));
        builder.addFixer(BlockRenameFix.create(schema54, "Rename seagrass blocks", DataFixers.createRenamer(ImmutableMap.of("minecraft:sea_grass", "minecraft:seagrass", "minecraft:tall_sea_grass", "minecraft:tall_seagrass"))));
        builder.addFixer(new HeightmapRenamingFix(schema54, false));
        Schema schema55 = builder.addSchema(1486, V1486::new);

        builder.addFixer(new EntityCodSalmonFix(schema55, true));
        builder.addFixer(ItemRenameFix.create(schema55, "Rename cod/salmon egg items", DataFixers.createRenamer(EntityCodSalmonFix.RENAMED_EGG_IDS)));
        Schema schema56 = builder.addSchema(1487, DataFixers.SAME_NAMESPACED);

        builder.addFixer(ItemRenameFix.create(schema56, "Rename prismarine_brick(s)_* blocks", DataFixers.createRenamer(ImmutableMap.of("minecraft:prismarine_bricks_slab", "minecraft:prismarine_brick_slab", "minecraft:prismarine_bricks_stairs", "minecraft:prismarine_brick_stairs"))));
        builder.addFixer(BlockRenameFix.create(schema56, "Rename prismarine_brick(s)_* items", DataFixers.createRenamer(ImmutableMap.of("minecraft:prismarine_bricks_slab", "minecraft:prismarine_brick_slab", "minecraft:prismarine_bricks_stairs", "minecraft:prismarine_brick_stairs"))));
        Schema schema57 = builder.addSchema(1488, DataFixers.SAME_NAMESPACED);

        builder.addFixer(BlockRenameFix.create(schema57, "Rename kelp/kelptop", DataFixers.createRenamer(ImmutableMap.of("minecraft:kelp_top", "minecraft:kelp", "minecraft:kelp", "minecraft:kelp_plant"))));
        builder.addFixer(ItemRenameFix.create(schema57, "Rename kelptop", DataFixers.createRenamer("minecraft:kelp_top", "minecraft:kelp")));
        builder.addFixer(new NamedEntityFix(schema57, false, "Command block block entity custom name fix", References.BLOCK_ENTITY, "minecraft:command_block") {
            @Override
            protected Typed<?> fix(Typed<?> inputType) {
                return inputType.update(DSL.remainderFinder(), EntityCustomNameToComponentFix::fixTagCustomName);
            }
        });
        builder.addFixer(new NamedEntityFix(schema57, false, "Command block minecart custom name fix", References.ENTITY, "minecraft:commandblock_minecart") {
            @Override
            protected Typed<?> fix(Typed<?> inputType) {
                return inputType.update(DSL.remainderFinder(), EntityCustomNameToComponentFix::fixTagCustomName);
            }
        });
        builder.addFixer(new IglooMetadataRemovalFix(schema57, false));
        Schema schema58 = builder.addSchema(1490, DataFixers.SAME_NAMESPACED);

        builder.addFixer(BlockRenameFix.create(schema58, "Rename melon_block", DataFixers.createRenamer("minecraft:melon_block", "minecraft:melon")));
        builder.addFixer(ItemRenameFix.create(schema58, "Rename melon_block/melon/speckled_melon", DataFixers.createRenamer(ImmutableMap.of("minecraft:melon_block", "minecraft:melon", "minecraft:melon", "minecraft:melon_slice", "minecraft:speckled_melon", "minecraft:glistering_melon_slice"))));
        Schema schema59 = builder.addSchema(1492, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ChunkStructuresTemplateRenameFix(schema59, false));
        Schema schema60 = builder.addSchema(1494, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ItemStackEnchantmentNamesFix(schema60, false));
        Schema schema61 = builder.addSchema(1496, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new LeavesFix(schema61, false));
        Schema schema62 = builder.addSchema(1500, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new BlockEntityKeepPacked(schema62, false));
        Schema schema63 = builder.addSchema(1501, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new AdvancementsFix(schema63, false));
        Schema schema64 = builder.addSchema(1502, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new NamespacedTypeRenameFix(schema64, "Recipes fix", References.RECIPE, DataFixers.createRenamer(RecipesFix.RECIPES)));
        Schema schema65 = builder.addSchema(1506, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new LevelDataGeneratorOptionsFix(schema65, false));
        Schema schema66 = builder.addSchema(1510, V1510::new);

        builder.addFixer(BlockRenameFix.create(schema66, "Block renamening fix", DataFixers.createRenamer(EntityTheRenameningFix.RENAMED_BLOCKS)));
        builder.addFixer(ItemRenameFix.create(schema66, "Item renamening fix", DataFixers.createRenamer(EntityTheRenameningFix.RENAMED_ITEMS)));
        builder.addFixer(new NamespacedTypeRenameFix(schema66, "Recipes renamening fix", References.RECIPE, DataFixers.createRenamer(RecipesRenameningFix.RECIPES)));
        builder.addFixer(new EntityTheRenameningFix(schema66, true));
        builder.addFixer(new StatsRenameFix(schema66, "SwimStatsRenameFix", ImmutableMap.of("minecraft:swim_one_cm", "minecraft:walk_on_water_one_cm", "minecraft:dive_one_cm", "minecraft:walk_under_water_one_cm")));
        Schema schema67 = builder.addSchema(1514, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ObjectiveDisplayNameFix(schema67, false));
        builder.addFixer(new TeamDisplayNameFix(schema67, false));
        builder.addFixer(new ObjectiveRenderTypeFix(schema67, false));
        Schema schema68 = builder.addSchema(1515, DataFixers.SAME_NAMESPACED);

        builder.addFixer(BlockRenameFix.create(schema68, "Rename coral fan blocks", DataFixers.createRenamer(RenamedCoralFansFix.RENAMED_IDS)));
        Schema schema69 = builder.addSchema(1624, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new TrappedChestBlockEntityFix(schema69, false));
        Schema schema70 = builder.addSchema(1800, V1800::new);

        builder.addFixer(new AddNewChoices(schema70, "Added 1.14 mobs fix", References.ENTITY));
        builder.addFixer(ItemRenameFix.create(schema70, "Rename dye items", DataFixers.createRenamer(DyeItemRenameFix.RENAMED_IDS)));
        Schema schema71 = builder.addSchema(1801, V1801::new);

        builder.addFixer(new AddNewChoices(schema71, "Added Illager Beast", References.ENTITY));
        Schema schema72 = builder.addSchema(1802, DataFixers.SAME_NAMESPACED);

        builder.addFixer(BlockRenameFix.create(schema72, "Rename sign blocks & stone slabs", DataFixers.createRenamer(ImmutableMap.of("minecraft:stone_slab", "minecraft:smooth_stone_slab", "minecraft:sign", "minecraft:oak_sign", "minecraft:wall_sign", "minecraft:oak_wall_sign"))));
        builder.addFixer(ItemRenameFix.create(schema72, "Rename sign item & stone slabs", DataFixers.createRenamer(ImmutableMap.of("minecraft:stone_slab", "minecraft:smooth_stone_slab", "minecraft:sign", "minecraft:oak_sign"))));
        Schema schema73 = builder.addSchema(1803, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ItemLoreFix(schema73, false));
        Schema schema74 = builder.addSchema(1904, V1904::new);

        builder.addFixer(new AddNewChoices(schema74, "Added Cats", References.ENTITY));
        builder.addFixer(new EntityCatSplitFix(schema74, false));
        Schema schema75 = builder.addSchema(1905, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ChunkStatusFix(schema75, false));
        Schema schema76 = builder.addSchema(1906, V1906::new);

        builder.addFixer(new AddNewChoices(schema76, "Add POI Blocks", References.BLOCK_ENTITY));
        Schema schema77 = builder.addSchema(1909, V1909::new);

        builder.addFixer(new AddNewChoices(schema77, "Add jigsaw", References.BLOCK_ENTITY));
        Schema schema78 = builder.addSchema(1911, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ChunkStatusFix2(schema78, false));
        Schema schema79 = builder.addSchema(1914, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new WeaponSmithChestLootTableFix(schema79, false));
        Schema schema80 = builder.addSchema(1917, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new CatTypeFix(schema80, false));
        Schema schema81 = builder.addSchema(1918, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new VillagerDataFix(schema81, "minecraft:villager"));
        builder.addFixer(new VillagerDataFix(schema81, "minecraft:zombie_villager"));
        Schema schema82 = builder.addSchema(1920, V1920::new);

        builder.addFixer(new NewVillageFix(schema82, false));
        builder.addFixer(new AddNewChoices(schema82, "Add campfire", References.BLOCK_ENTITY));
        Schema schema83 = builder.addSchema(1925, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new MapIdFix(schema83, false));
        Schema schema84 = builder.addSchema(1928, V1928::new);

        builder.addFixer(new EntityRavagerRenameFix(schema84, true));
        builder.addFixer(ItemRenameFix.create(schema84, "Rename ravager egg item", DataFixers.createRenamer(EntityRavagerRenameFix.RENAMED_IDS)));
        Schema schema85 = builder.addSchema(1929, V1929::new);

        builder.addFixer(new AddNewChoices(schema85, "Add Wandering Trader and Trader Llama", References.ENTITY));
        Schema schema86 = builder.addSchema(1931, V1931::new);

        builder.addFixer(new AddNewChoices(schema86, "Added Fox", References.ENTITY));
        Schema schema87 = builder.addSchema(1936, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new OptionsAddTextBackgroundFix(schema87, false));
        Schema schema88 = builder.addSchema(1946, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ReorganizePoi(schema88, false));
        Schema schema89 = builder.addSchema(1948, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new OminousBannerRenameFix(schema89));
        Schema schema90 = builder.addSchema(1953, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new OminousBannerBlockEntityRenameFix(schema90, false));
        Schema schema91 = builder.addSchema(1955, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new VillagerRebuildLevelAndXpFix(schema91, false));
        builder.addFixer(new ZombieVillagerRebuildXpFix(schema91, false));
        Schema schema92 = builder.addSchema(1961, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ChunkLightRemoveFix(schema92, false));
        Schema schema93 = builder.addSchema(1963, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new RemoveGolemGossipFix(schema93, false));
        Schema schema94 = builder.addSchema(2100, V2100::new);

        builder.addFixer(new AddNewChoices(schema94, "Added Bee and Bee Stinger", References.ENTITY));
        builder.addFixer(new AddNewChoices(schema94, "Add beehive", References.BLOCK_ENTITY));
        builder.addFixer(new NamespacedTypeRenameFix(schema94, "Rename sugar recipe", References.RECIPE, DataFixers.createRenamer("minecraft:sugar", "sugar_from_sugar_cane")));
        builder.addFixer(new AdvancementsRenameFix(schema94, false, "Rename sugar recipe advancement", DataFixers.createRenamer("minecraft:recipes/misc/sugar", "minecraft:recipes/misc/sugar_from_sugar_cane")));
        Schema schema95 = builder.addSchema(2202, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ChunkBiomeFix(schema95, false));
        Schema schema96 = builder.addSchema(2209, DataFixers.SAME_NAMESPACED);
        UnaryOperator<String> unaryoperator = DataFixers.createRenamer("minecraft:bee_hive", "minecraft:beehive");

        builder.addFixer(ItemRenameFix.create(schema96, "Rename bee_hive item to beehive", unaryoperator));
        builder.addFixer(new PoiTypeRenameFix(schema96, "Rename bee_hive poi to beehive", unaryoperator));
        builder.addFixer(BlockRenameFix.create(schema96, "Rename bee_hive block to beehive", unaryoperator));
        Schema schema97 = builder.addSchema(2211, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new StructureReferenceCountFix(schema97, false));
        Schema schema98 = builder.addSchema(2218, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ForcePoiRebuild(schema98, false));
        Schema schema99 = builder.addSchema(2501, V2501::new);

        builder.addFixer(new FurnaceRecipeFix(schema99, true));
        Schema schema100 = builder.addSchema(2502, V2502::new);

        builder.addFixer(new AddNewChoices(schema100, "Added Hoglin", References.ENTITY));
        Schema schema101 = builder.addSchema(2503, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new WallPropertyFix(schema101, false));
        builder.addFixer(new AdvancementsRenameFix(schema101, false, "Composter category change", DataFixers.createRenamer("minecraft:recipes/misc/composter", "minecraft:recipes/decorations/composter")));
        Schema schema102 = builder.addSchema(2505, V2505::new);

        builder.addFixer(new AddNewChoices(schema102, "Added Piglin", References.ENTITY));
        builder.addFixer(new MemoryExpiryDataFix(schema102, "minecraft:villager"));
        Schema schema103 = builder.addSchema(2508, DataFixers.SAME_NAMESPACED);

        builder.addFixer(ItemRenameFix.create(schema103, "Renamed fungi items to fungus", DataFixers.createRenamer(ImmutableMap.of("minecraft:warped_fungi", "minecraft:warped_fungus", "minecraft:crimson_fungi", "minecraft:crimson_fungus"))));
        builder.addFixer(BlockRenameFix.create(schema103, "Renamed fungi blocks to fungus", DataFixers.createRenamer(ImmutableMap.of("minecraft:warped_fungi", "minecraft:warped_fungus", "minecraft:crimson_fungi", "minecraft:crimson_fungus"))));
        Schema schema104 = builder.addSchema(2509, V2509::new);

        builder.addFixer(new EntityZombifiedPiglinRenameFix(schema104));
        builder.addFixer(ItemRenameFix.create(schema104, "Rename zombie pigman egg item", DataFixers.createRenamer(EntityZombifiedPiglinRenameFix.RENAMED_IDS)));
        Schema schema105 = builder.addSchema(2511, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new EntityProjectileOwnerFix(schema105));
        Schema schema106 = builder.addSchema(2514, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new EntityUUIDFix(schema106));
        builder.addFixer(new BlockEntityUUIDFix(schema106));
        builder.addFixer(new PlayerUUIDFix(schema106));
        builder.addFixer(new LevelUUIDFix(schema106));
        builder.addFixer(new SavedDataUUIDFix(schema106));
        builder.addFixer(new ItemStackUUIDFix(schema106));
        Schema schema107 = builder.addSchema(2516, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new GossipUUIDFix(schema107, "minecraft:villager"));
        builder.addFixer(new GossipUUIDFix(schema107, "minecraft:zombie_villager"));
        Schema schema108 = builder.addSchema(2518, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new JigsawPropertiesFix(schema108, false));
        builder.addFixer(new JigsawRotationFix(schema108, false));
        Schema schema109 = builder.addSchema(2519, V2519::new);

        builder.addFixer(new AddNewChoices(schema109, "Added Strider", References.ENTITY));
        Schema schema110 = builder.addSchema(2522, V2522::new);

        builder.addFixer(new AddNewChoices(schema110, "Added Zoglin", References.ENTITY));
        Schema schema111 = builder.addSchema(2523, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new AttributesRename(schema111));
        Schema schema112 = builder.addSchema(2527, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new BitStorageAlignFix(schema112));
        Schema schema113 = builder.addSchema(2528, DataFixers.SAME_NAMESPACED);

        builder.addFixer(ItemRenameFix.create(schema113, "Rename soul fire torch and soul fire lantern", DataFixers.createRenamer(ImmutableMap.of("minecraft:soul_fire_torch", "minecraft:soul_torch", "minecraft:soul_fire_lantern", "minecraft:soul_lantern"))));
        builder.addFixer(BlockRenameFix.create(schema113, "Rename soul fire torch and soul fire lantern", DataFixers.createRenamer(ImmutableMap.of("minecraft:soul_fire_torch", "minecraft:soul_torch", "minecraft:soul_fire_wall_torch", "minecraft:soul_wall_torch", "minecraft:soul_fire_lantern", "minecraft:soul_lantern"))));
        Schema schema114 = builder.addSchema(2529, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new StriderGravityFix(schema114, false));
        Schema schema115 = builder.addSchema(2531, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new RedstoneWireConnectionsFix(schema115));
        Schema schema116 = builder.addSchema(2533, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new VillagerFollowRangeFix(schema116));
        Schema schema117 = builder.addSchema(2535, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new EntityShulkerRotationFix(schema117));
        Schema schema118 = builder.addSchema(2550, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new WorldGenSettingsFix(schema118));
        Schema schema119 = builder.addSchema(2551, V2551::new);

        builder.addFixer(new WriteAndReadFix(schema119, "add types to WorldGenData", References.WORLD_GEN_SETTINGS));
        Schema schema120 = builder.addSchema(2552, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new NamespacedTypeRenameFix(schema120, "Nether biome rename", References.BIOME, DataFixers.createRenamer("minecraft:nether", "minecraft:nether_wastes")));
        Schema schema121 = builder.addSchema(2553, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new NamespacedTypeRenameFix(schema121, "Biomes fix", References.BIOME, DataFixers.createRenamer(BiomeFix.BIOMES)));
        Schema schema122 = builder.addSchema(2558, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new MissingDimensionFix(schema122, false));
        builder.addFixer(new OptionsRenameFieldFix(schema122, false, "Rename swapHands setting", "key_key.swapHands", "key_key.swapOffhand"));
        Schema schema123 = builder.addSchema(2568, V2568::new);

        builder.addFixer(new AddNewChoices(schema123, "Added Piglin Brute", References.ENTITY));
        Schema schema124 = builder.addSchema(2571, V2571::new);

        builder.addFixer(new AddNewChoices(schema124, "Added Goat", References.ENTITY));
        Schema schema125 = builder.addSchema(2679, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new CauldronRenameFix(schema125, false));
        Schema schema126 = builder.addSchema(2680, DataFixers.SAME_NAMESPACED);

        builder.addFixer(ItemRenameFix.create(schema126, "Renamed grass path item to dirt path", DataFixers.createRenamer("minecraft:grass_path", "minecraft:dirt_path")));
        builder.addFixer(BlockRenameFixWithJigsaw.create(schema126, "Renamed grass path block to dirt path", DataFixers.createRenamer("minecraft:grass_path", "minecraft:dirt_path")));
        Schema schema127 = builder.addSchema(2684, V2684::new);

        builder.addFixer(new AddNewChoices(schema127, "Added Sculk Sensor", References.BLOCK_ENTITY));
        Schema schema128 = builder.addSchema(2686, V2686::new);

        builder.addFixer(new AddNewChoices(schema128, "Added Axolotl", References.ENTITY));
        Schema schema129 = builder.addSchema(2688, V2688::new);

        builder.addFixer(new AddNewChoices(schema129, "Added Glow Squid", References.ENTITY));
        builder.addFixer(new AddNewChoices(schema129, "Added Glow Item Frame", References.ENTITY));
        Schema schema130 = builder.addSchema(2690, DataFixers.SAME_NAMESPACED);
        // CraftBukkit - decompile error
        ImmutableMap<String, String> immutablemap = ImmutableMap.<String, String>builder().put("minecraft:weathered_copper_block", "minecraft:oxidized_copper_block").put("minecraft:semi_weathered_copper_block", "minecraft:weathered_copper_block").put("minecraft:lightly_weathered_copper_block", "minecraft:exposed_copper_block").put("minecraft:weathered_cut_copper", "minecraft:oxidized_cut_copper").put("minecraft:semi_weathered_cut_copper", "minecraft:weathered_cut_copper").put("minecraft:lightly_weathered_cut_copper", "minecraft:exposed_cut_copper").put("minecraft:weathered_cut_copper_stairs", "minecraft:oxidized_cut_copper_stairs").put("minecraft:semi_weathered_cut_copper_stairs", "minecraft:weathered_cut_copper_stairs").put("minecraft:lightly_weathered_cut_copper_stairs", "minecraft:exposed_cut_copper_stairs").put("minecraft:weathered_cut_copper_slab", "minecraft:oxidized_cut_copper_slab").put("minecraft:semi_weathered_cut_copper_slab", "minecraft:weathered_cut_copper_slab").put("minecraft:lightly_weathered_cut_copper_slab", "minecraft:exposed_cut_copper_slab").put("minecraft:waxed_semi_weathered_copper", "minecraft:waxed_weathered_copper").put("minecraft:waxed_lightly_weathered_copper", "minecraft:waxed_exposed_copper").put("minecraft:waxed_semi_weathered_cut_copper", "minecraft:waxed_weathered_cut_copper").put("minecraft:waxed_lightly_weathered_cut_copper", "minecraft:waxed_exposed_cut_copper").put("minecraft:waxed_semi_weathered_cut_copper_stairs", "minecraft:waxed_weathered_cut_copper_stairs").put("minecraft:waxed_lightly_weathered_cut_copper_stairs", "minecraft:waxed_exposed_cut_copper_stairs").put("minecraft:waxed_semi_weathered_cut_copper_slab", "minecraft:waxed_weathered_cut_copper_slab").put("minecraft:waxed_lightly_weathered_cut_copper_slab", "minecraft:waxed_exposed_cut_copper_slab").build();

        builder.addFixer(ItemRenameFix.create(schema130, "Renamed copper block items to new oxidized terms", DataFixers.createRenamer(immutablemap)));
        builder.addFixer(BlockRenameFixWithJigsaw.create(schema130, "Renamed copper blocks to new oxidized terms", DataFixers.createRenamer(immutablemap)));
        Schema schema131 = builder.addSchema(2691, DataFixers.SAME_NAMESPACED);
        // CraftBukkit - decompile error
        ImmutableMap<String, String> immutablemap1 = ImmutableMap.<String, String>builder().put("minecraft:waxed_copper", "minecraft:waxed_copper_block").put("minecraft:oxidized_copper_block", "minecraft:oxidized_copper").put("minecraft:weathered_copper_block", "minecraft:weathered_copper").put("minecraft:exposed_copper_block", "minecraft:exposed_copper").build();

        builder.addFixer(ItemRenameFix.create(schema131, "Rename copper item suffixes", DataFixers.createRenamer(immutablemap1)));
        builder.addFixer(BlockRenameFixWithJigsaw.create(schema131, "Rename copper blocks suffixes", DataFixers.createRenamer(immutablemap1)));
        Schema schema132 = builder.addSchema(2693, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new AddFlagIfNotPresentFix(schema132, References.WORLD_GEN_SETTINGS, "has_increased_height_already", false));
        Schema schema133 = builder.addSchema(2696, DataFixers.SAME_NAMESPACED);
        // CraftBukkit - decompile error
        ImmutableMap<String, String> immutablemap2 = ImmutableMap.<String, String>builder().put("minecraft:grimstone", "minecraft:deepslate").put("minecraft:grimstone_slab", "minecraft:cobbled_deepslate_slab").put("minecraft:grimstone_stairs", "minecraft:cobbled_deepslate_stairs").put("minecraft:grimstone_wall", "minecraft:cobbled_deepslate_wall").put("minecraft:polished_grimstone", "minecraft:polished_deepslate").put("minecraft:polished_grimstone_slab", "minecraft:polished_deepslate_slab").put("minecraft:polished_grimstone_stairs", "minecraft:polished_deepslate_stairs").put("minecraft:polished_grimstone_wall", "minecraft:polished_deepslate_wall").put("minecraft:grimstone_tiles", "minecraft:deepslate_tiles").put("minecraft:grimstone_tile_slab", "minecraft:deepslate_tile_slab").put("minecraft:grimstone_tile_stairs", "minecraft:deepslate_tile_stairs").put("minecraft:grimstone_tile_wall", "minecraft:deepslate_tile_wall").put("minecraft:grimstone_bricks", "minecraft:deepslate_bricks").put("minecraft:grimstone_brick_slab", "minecraft:deepslate_brick_slab").put("minecraft:grimstone_brick_stairs", "minecraft:deepslate_brick_stairs").put("minecraft:grimstone_brick_wall", "minecraft:deepslate_brick_wall").put("minecraft:chiseled_grimstone", "minecraft:chiseled_deepslate").build();

        builder.addFixer(ItemRenameFix.create(schema133, "Renamed grimstone block items to deepslate", DataFixers.createRenamer(immutablemap2)));
        builder.addFixer(BlockRenameFixWithJigsaw.create(schema133, "Renamed grimstone blocks to deepslate", DataFixers.createRenamer(immutablemap2)));
        Schema schema134 = builder.addSchema(2700, DataFixers.SAME_NAMESPACED);

        builder.addFixer(BlockRenameFixWithJigsaw.create(schema134, "Renamed cave vines blocks", DataFixers.createRenamer(ImmutableMap.of("minecraft:cave_vines_head", "minecraft:cave_vines", "minecraft:cave_vines_body", "minecraft:cave_vines_plant"))));
        Schema schema135 = builder.addSchema(2701, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new SavedDataFeaturePoolElementFix(schema135));
        Schema schema136 = builder.addSchema(2702, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new AbstractArrowPickupFix(schema136));
        Schema schema137 = builder.addSchema(2704, V2704::new);

        builder.addFixer(new AddNewChoices(schema137, "Added Goat", References.ENTITY));
        Schema schema138 = builder.addSchema(2707, V2707::new);

        builder.addFixer(new AddNewChoices(schema138, "Added Marker", References.ENTITY));
        builder.addFixer(new AddFlagIfNotPresentFix(schema138, References.WORLD_GEN_SETTINGS, "has_increased_height_already", true));
        Schema schema139 = builder.addSchema(2710, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new StatsRenameFix(schema139, "Renamed play_one_minute stat to play_time", ImmutableMap.of("minecraft:play_one_minute", "minecraft:play_time")));
        Schema schema140 = builder.addSchema(2717, DataFixers.SAME_NAMESPACED);

        builder.addFixer(ItemRenameFix.create(schema140, "Rename azalea_leaves_flowers", DataFixers.createRenamer(ImmutableMap.of("minecraft:azalea_leaves_flowers", "minecraft:flowering_azalea_leaves"))));
        builder.addFixer(BlockRenameFix.create(schema140, "Rename azalea_leaves_flowers items", DataFixers.createRenamer(ImmutableMap.of("minecraft:azalea_leaves_flowers", "minecraft:flowering_azalea_leaves"))));
        Schema schema141 = builder.addSchema(2825, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new AddFlagIfNotPresentFix(schema141, References.WORLD_GEN_SETTINGS, "has_increased_height_already", false));
        Schema schema142 = builder.addSchema(2831, V2831::new);

        builder.addFixer(new SpawnerDataFix(schema142));
        Schema schema143 = builder.addSchema(2832, V2832::new);

        builder.addFixer(new WorldGenSettingsHeightAndBiomeFix(schema143));
        builder.addFixer(new ChunkHeightAndBiomeFix(schema143));
        Schema schema144 = builder.addSchema(2833, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new WorldGenSettingsDisallowOldCustomWorldsFix(schema144));
        Schema schema145 = builder.addSchema(2838, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new NamespacedTypeRenameFix(schema145, "Caves and Cliffs biome renames", References.BIOME, DataFixers.createRenamer(CavesAndCliffsRenames.RENAMES)));
        Schema schema146 = builder.addSchema(2841, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ChunkProtoTickListFix(schema146));
        Schema schema147 = builder.addSchema(2842, V2842::new);

        builder.addFixer(new ChunkRenamesFix(schema147));
        Schema schema148 = builder.addSchema(2843, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new OverreachingTickFix(schema148));
        builder.addFixer(new NamespacedTypeRenameFix(schema148, "Remove Deep Warm Ocean", References.BIOME, DataFixers.createRenamer("minecraft:deep_warm_ocean", "minecraft:warm_ocean")));
        Schema schema149 = builder.addSchema(2846, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new AdvancementsRenameFix(schema149, false, "Rename some C&C part 2 advancements", DataFixers.createRenamer(ImmutableMap.of("minecraft:husbandry/play_jukebox_in_meadows", "minecraft:adventure/play_jukebox_in_meadows", "minecraft:adventure/caves_and_cliff", "minecraft:adventure/fall_from_world_height", "minecraft:adventure/ride_strider_in_overworld_lava", "minecraft:nether/ride_strider_in_overworld_lava"))));
        Schema schema150 = builder.addSchema(2852, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new WorldGenSettingsDisallowOldCustomWorldsFix(schema150));
        Schema schema151 = builder.addSchema(2967, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new StructureSettingsFlattenFix(schema151));
        Schema schema152 = builder.addSchema(2970, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new StructuresBecomeConfiguredFix(schema152));
        Schema schema153 = builder.addSchema(3076, V3076::new);

        builder.addFixer(new AddNewChoices(schema153, "Added Sculk Catalyst", References.BLOCK_ENTITY));
        Schema schema154 = builder.addSchema(3077, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ChunkDeleteIgnoredLightDataFix(schema154));
        Schema schema155 = builder.addSchema(3078, V3078::new);

        builder.addFixer(new AddNewChoices(schema155, "Added Frog", References.ENTITY));
        builder.addFixer(new AddNewChoices(schema155, "Added Tadpole", References.ENTITY));
        builder.addFixer(new AddNewChoices(schema155, "Added Sculk Shrieker", References.BLOCK_ENTITY));
        Schema schema156 = builder.addSchema(3081, V3081::new);

        builder.addFixer(new AddNewChoices(schema156, "Added Warden", References.ENTITY));
        Schema schema157 = builder.addSchema(3082, V3082::new);

        builder.addFixer(new AddNewChoices(schema157, "Added Chest Boat", References.ENTITY));
        Schema schema158 = builder.addSchema(3083, V3083::new);

        builder.addFixer(new AddNewChoices(schema158, "Added Allay", References.ENTITY));
        Schema schema159 = builder.addSchema(3084, DataFixers.SAME_NAMESPACED);

        // CraftBukkit - decompile error
        builder.addFixer(new NamespacedTypeRenameFix(schema159, "game_event_renames_3084", References.GAME_EVENT_NAME, DataFixers.createRenamer(ImmutableMap.<String, String>builder().put("minecraft:block_press", "minecraft:block_activate").put("minecraft:block_switch", "minecraft:block_activate").put("minecraft:block_unpress", "minecraft:block_deactivate").put("minecraft:block_unswitch", "minecraft:block_deactivate").put("minecraft:drinking_finish", "minecraft:drink").put("minecraft:elytra_free_fall", "minecraft:elytra_glide").put("minecraft:entity_damaged", "minecraft:entity_damage").put("minecraft:entity_dying", "minecraft:entity_die").put("minecraft:entity_killed", "minecraft:entity_die").put("minecraft:mob_interact", "minecraft:entity_interact").put("minecraft:ravager_roar", "minecraft:entity_roar").put("minecraft:ring_bell", "minecraft:block_change").put("minecraft:shulker_close", "minecraft:container_close").put("minecraft:shulker_open", "minecraft:container_open").put("minecraft:wolf_shaking", "minecraft:entity_shake").build())));
        Schema schema160 = builder.addSchema(3086, DataFixers.SAME_NAMESPACED);
        TypeReference typereference = References.ENTITY;
        Int2ObjectOpenHashMap<String> int2objectopenhashmap = (Int2ObjectOpenHashMap) Util.make(new Int2ObjectOpenHashMap(), (int2objectopenhashmap1) -> { // CraftBukkit - decompile error
            int2objectopenhashmap1.defaultReturnValue("minecraft:tabby");
            int2objectopenhashmap1.put(0, "minecraft:tabby");
            int2objectopenhashmap1.put(1, "minecraft:black");
            int2objectopenhashmap1.put(2, "minecraft:red");
            int2objectopenhashmap1.put(3, "minecraft:siamese");
            int2objectopenhashmap1.put(4, "minecraft:british");
            int2objectopenhashmap1.put(5, "minecraft:calico");
            int2objectopenhashmap1.put(6, "minecraft:persian");
            int2objectopenhashmap1.put(7, "minecraft:ragdoll");
            int2objectopenhashmap1.put(8, "minecraft:white");
            int2objectopenhashmap1.put(9, "minecraft:jellie");
            int2objectopenhashmap1.put(10, "minecraft:all_black");
        });

        Objects.requireNonNull(int2objectopenhashmap);
        builder.addFixer(new EntityVariantFix(schema160, "Change cat variant type", typereference, "minecraft:cat", "CatType", int2objectopenhashmap::get));
        // CraftBukkit - decompile error
        ImmutableMap<String, String> immutablemap3 = ImmutableMap.<String, String>builder().put("textures/entity/cat/tabby.png", "minecraft:tabby").put("textures/entity/cat/black.png", "minecraft:black").put("textures/entity/cat/red.png", "minecraft:red").put("textures/entity/cat/siamese.png", "minecraft:siamese").put("textures/entity/cat/british_shorthair.png", "minecraft:british").put("textures/entity/cat/calico.png", "minecraft:calico").put("textures/entity/cat/persian.png", "minecraft:persian").put("textures/entity/cat/ragdoll.png", "minecraft:ragdoll").put("textures/entity/cat/white.png", "minecraft:white").put("textures/entity/cat/jellie.png", "minecraft:jellie").put("textures/entity/cat/all_black.png", "minecraft:all_black").build();

        builder.addFixer(new CriteriaRenameFix(schema160, "Migrate cat variant advancement", "minecraft:husbandry/complete_catalogue", (s) -> {
            return (String) immutablemap3.getOrDefault(s, s);
        }));
        Schema schema161 = builder.addSchema(3087, DataFixers.SAME_NAMESPACED);

        typereference = References.ENTITY;
        int2objectopenhashmap = (Int2ObjectOpenHashMap) Util.make(new Int2ObjectOpenHashMap(), (int2objectopenhashmap1) -> {
            int2objectopenhashmap1.put(0, "minecraft:temperate");
            int2objectopenhashmap1.put(1, "minecraft:warm");
            int2objectopenhashmap1.put(2, "minecraft:cold");
        });
        Objects.requireNonNull(int2objectopenhashmap);
        builder.addFixer(new EntityVariantFix(schema161, "Change frog variant type", typereference, "minecraft:frog", "Variant", int2objectopenhashmap::get));
        Schema schema162 = builder.addSchema(3090, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new EntityPaintingFieldsRenameFix(schema162));
        Schema schema163 = builder.addSchema(3093, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new EntityGoatMissingStateFix(schema163));
        Schema schema164 = builder.addSchema(3094, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new GoatHornIdFix(schema164));
        Schema schema165 = builder.addSchema(3097, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new FilteredBooksFix(schema165));
        builder.addFixer(new FilteredSignsFix(schema165));
        Map<String, String> map = Map.of("minecraft:british", "minecraft:british_shorthair");

        builder.addFixer(new VariantRenameFix(schema165, "Rename british shorthair", References.ENTITY, "minecraft:cat", map));
        builder.addFixer(new CriteriaRenameFix(schema165, "Migrate cat variant advancement for british shorthair", "minecraft:husbandry/complete_catalogue", (s) -> {
            return (String) map.getOrDefault(s, s);
        }));
        Set set = Set.of("minecraft:unemployed", "minecraft:nitwit");

        Objects.requireNonNull(set);
        builder.addFixer(new PoiTypeRemoveFix(schema165, "Remove unpopulated villager PoI types", set::contains));
        Schema schema166 = builder.addSchema(3108, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new BlendingDataRemoveFromNetherEndFix(schema166));
        Schema schema167 = builder.addSchema(3201, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new OptionsProgrammerArtFix(schema167));
        Schema schema168 = builder.addSchema(3202, V3202::new);

        builder.addFixer(new AddNewChoices(schema168, "Added Hanging Sign", References.BLOCK_ENTITY));
        Schema schema169 = builder.addSchema(3203, V3203::new);

        builder.addFixer(new AddNewChoices(schema169, "Added Camel", References.ENTITY));
        Schema schema170 = builder.addSchema(3204, V3204::new);

        builder.addFixer(new AddNewChoices(schema170, "Added Chiseled Bookshelf", References.BLOCK_ENTITY));
        Schema schema171 = builder.addSchema(3209, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ItemStackSpawnEggFix(schema171, false, "minecraft:pig_spawn_egg"));
        Schema schema172 = builder.addSchema(3214, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new OptionsAmbientOcclusionFix(schema172));
        Schema schema173 = builder.addSchema(3319, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new OptionsAccessibilityOnboardFix(schema173));
        Schema schema174 = builder.addSchema(3322, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new EffectDurationFix(schema174));
        Schema schema175 = builder.addSchema(3325, V3325::new);

        builder.addFixer(new AddNewChoices(schema175, "Added displays", References.ENTITY));
        Schema schema176 = builder.addSchema(3326, V3326::new);

        builder.addFixer(new AddNewChoices(schema176, "Added Sniffer", References.ENTITY));
        Schema schema177 = builder.addSchema(3327, V3327::new);

        builder.addFixer(new AddNewChoices(schema177, "Archaeology", References.BLOCK_ENTITY));
        Schema schema178 = builder.addSchema(3328, V3328::new);

        builder.addFixer(new AddNewChoices(schema178, "Added interaction", References.ENTITY));
        Schema schema179 = builder.addSchema(3438, V3438::new);

        builder.addFixer(BlockEntityRenameFix.create(schema179, "Rename Suspicious Sand to Brushable Block", DataFixers.createRenamer("minecraft:suspicious_sand", "minecraft:brushable_block")));
        builder.addFixer(new EntityBrushableBlockFieldsRenameFix(schema179));
        builder.addFixer(ItemRenameFix.create(schema179, "Pottery shard renaming", DataFixers.createRenamer(ImmutableMap.of("minecraft:pottery_shard_archer", "minecraft:archer_pottery_shard", "minecraft:pottery_shard_prize", "minecraft:prize_pottery_shard", "minecraft:pottery_shard_arms_up", "minecraft:arms_up_pottery_shard", "minecraft:pottery_shard_skull", "minecraft:skull_pottery_shard"))));
        builder.addFixer(new AddNewChoices(schema179, "Added calibrated sculk sensor", References.BLOCK_ENTITY));
        Schema schema180 = builder.addSchema(3439, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new BlockEntitySignDoubleSidedEditableTextFix(schema180, "Updated sign text format for Signs", "minecraft:sign"));
        builder.addFixer(new BlockEntitySignDoubleSidedEditableTextFix(schema180, "Updated sign text format for Hanging Signs", "minecraft:hanging_sign"));
        Schema schema181 = builder.addSchema(3440, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new NamespacedTypeRenameFix(schema181, "Replace experimental 1.20 overworld", References.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, DataFixers.createRenamer("minecraft:overworld_update_1_20", "minecraft:overworld")));
        builder.addFixer(new FeatureFlagRemoveFix(schema181, "Remove 1.20 feature toggle", Set.of("minecraft:update_1_20")));
        Schema schema182 = builder.addSchema(3441, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new BlendingDataFix(schema182));
        Schema schema183 = builder.addSchema(3447, DataFixers.SAME_NAMESPACED);

        builder.addFixer(ItemRenameFix.create(schema183, "Pottery shard item renaming to Pottery sherd", DataFixers.createRenamer((Map) Stream.of("minecraft:angler_pottery_shard", "minecraft:archer_pottery_shard", "minecraft:arms_up_pottery_shard", "minecraft:blade_pottery_shard", "minecraft:brewer_pottery_shard", "minecraft:burn_pottery_shard", "minecraft:danger_pottery_shard", "minecraft:explorer_pottery_shard", "minecraft:friend_pottery_shard", "minecraft:heart_pottery_shard", "minecraft:heartbreak_pottery_shard", "minecraft:howl_pottery_shard", "minecraft:miner_pottery_shard", "minecraft:mourner_pottery_shard", "minecraft:plenty_pottery_shard", "minecraft:prize_pottery_shard", "minecraft:sheaf_pottery_shard", "minecraft:shelter_pottery_shard", "minecraft:skull_pottery_shard", "minecraft:snort_pottery_shard").collect(Collectors.toMap(Function.identity(), (s) -> {
            return s.replace("_pottery_shard", "_pottery_sherd");
        })))));
        Schema schema184 = builder.addSchema(3448, V3448::new);

        builder.addFixer(new DecoratedPotFieldRenameFix(schema184));
        Schema schema185 = builder.addSchema(3450, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new RemapChunkStatusFix(schema185, "Remove liquid_carvers and heightmap chunk statuses", DataFixers.createRenamer(Map.of("minecraft:liquid_carvers", "minecraft:carvers", "minecraft:heightmaps", "minecraft:spawn"))));
        Schema schema186 = builder.addSchema(3451, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ChunkDeleteLightFix(schema186));
        Schema schema187 = builder.addSchema(3459, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new LegacyDragonFightFix(schema187));
        Schema schema188 = builder.addSchema(3564, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new DropInvalidSignDataFix(schema188, "Drop invalid sign datafix data", "minecraft:sign"));
        builder.addFixer(new DropInvalidSignDataFix(schema188, "Drop invalid hanging sign datafix data", "minecraft:hanging_sign"));
        Schema schema189 = builder.addSchema(3565, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new RandomSequenceSettingsFix(schema189));
        Schema schema190 = builder.addSchema(3566, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new ScoreboardDisplaySlotFix(schema190));
        Schema schema191 = builder.addSchema(3568, DataFixers.SAME_NAMESPACED);

        builder.addFixer(new MobEffectIdFix(schema191));
    }

    private static UnaryOperator<String> createRenamer(Map<String, String> replacements) {
        return (s) -> {
            return (String) replacements.getOrDefault(s, s);
        };
    }

    private static UnaryOperator<String> createRenamer(String old, String current) {
        return (s2) -> {
            return Objects.equals(s2, old) ? current : s2;
        };
    }
}
