package net.minecraft.world.entity;

import com.google.common.collect.ImmutableSet;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.animal.Pufferfish;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Salmon;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.animal.TropicalFish;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.animal.horse.ZombieHorse;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.entity.vehicle.MinecartCommandBlock;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.entity.vehicle.MinecartHopper;
import net.minecraft.world.entity.vehicle.MinecartSpawner;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.slf4j.Logger;

public class EntityType<T extends Entity> implements FeatureElement, EntityTypeTest<Entity, T> {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String ENTITY_TAG = "EntityTag";
    private final Holder.Reference<EntityType<?>> builtInRegistryHolder;
    private static final float MAGIC_HORSE_WIDTH = 1.3964844F;
    private static final int DISPLAY_TRACKING_RANGE = 10;
    public static final EntityType<Allay> ALLAY = EntityType.register("allay", EntityType.Builder.of(Allay::new, MobCategory.CREATURE).sized(0.35F, 0.6F).clientTrackingRange(8).updateInterval(2));
    public static final EntityType<AreaEffectCloud> AREA_EFFECT_CLOUD = EntityType.register("area_effect_cloud", EntityType.Builder.of(AreaEffectCloud::new, MobCategory.MISC).fireImmune().sized(6.0F, 0.5F).clientTrackingRange(10).updateInterval(10)); // CraftBukkit - SPIGOT-3729: track area effect clouds
    public static final EntityType<ArmorStand> ARMOR_STAND = EntityType.register("armor_stand", EntityType.Builder.of(ArmorStand::new, MobCategory.MISC).sized(0.5F, 1.975F).clientTrackingRange(10));
    public static final EntityType<Arrow> ARROW = EntityType.register("arrow", EntityType.Builder.of(Arrow::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(4).updateInterval(20));
    public static final EntityType<Axolotl> AXOLOTL = EntityType.register("axolotl", EntityType.Builder.of(Axolotl::new, MobCategory.AXOLOTLS).sized(0.75F, 0.42F).clientTrackingRange(10));
    public static final EntityType<Bat> BAT = EntityType.register("bat", EntityType.Builder.of(Bat::new, MobCategory.AMBIENT).sized(0.5F, 0.9F).clientTrackingRange(5));
    public static final EntityType<Bee> BEE = EntityType.register("bee", EntityType.Builder.of(Bee::new, MobCategory.CREATURE).sized(0.7F, 0.6F).clientTrackingRange(8));
    public static final EntityType<Blaze> BLAZE = EntityType.register("blaze", EntityType.Builder.of(Blaze::new, MobCategory.MONSTER).fireImmune().sized(0.6F, 1.8F).clientTrackingRange(8));
    public static final EntityType<Display.BlockDisplay> BLOCK_DISPLAY = EntityType.register("block_display", EntityType.Builder.of(Display.BlockDisplay::new, MobCategory.MISC).sized(0.0F, 0.0F).clientTrackingRange(10).updateInterval(1));
    public static final EntityType<Boat> BOAT = EntityType.register("boat", EntityType.Builder.of(Boat::new, MobCategory.MISC).sized(1.375F, 0.5625F).clientTrackingRange(10));
    public static final EntityType<Camel> CAMEL = EntityType.register("camel", EntityType.Builder.of(Camel::new, MobCategory.CREATURE).sized(1.7F, 2.375F).clientTrackingRange(10));
    public static final EntityType<Cat> CAT = EntityType.register("cat", EntityType.Builder.of(Cat::new, MobCategory.CREATURE).sized(0.6F, 0.7F).clientTrackingRange(8));
    public static final EntityType<CaveSpider> CAVE_SPIDER = EntityType.register("cave_spider", EntityType.Builder.of(CaveSpider::new, MobCategory.MONSTER).sized(0.7F, 0.5F).clientTrackingRange(8));
    public static final EntityType<ChestBoat> CHEST_BOAT = EntityType.register("chest_boat", EntityType.Builder.of(ChestBoat::new, MobCategory.MISC).sized(1.375F, 0.5625F).clientTrackingRange(10));
    public static final EntityType<MinecartChest> CHEST_MINECART = EntityType.register("chest_minecart", EntityType.Builder.of(MinecartChest::new, MobCategory.MISC).sized(0.98F, 0.7F).clientTrackingRange(8));
    public static final EntityType<Chicken> CHICKEN = EntityType.register("chicken", EntityType.Builder.of(Chicken::new, MobCategory.CREATURE).sized(0.4F, 0.7F).clientTrackingRange(10));
    public static final EntityType<Cod> COD = EntityType.register("cod", EntityType.Builder.of(Cod::new, MobCategory.WATER_AMBIENT).sized(0.5F, 0.3F).clientTrackingRange(4));
    public static final EntityType<MinecartCommandBlock> COMMAND_BLOCK_MINECART = EntityType.register("command_block_minecart", EntityType.Builder.of(MinecartCommandBlock::new, MobCategory.MISC).sized(0.98F, 0.7F).clientTrackingRange(8));
    public static final EntityType<Cow> COW = EntityType.register("cow", EntityType.Builder.of(Cow::new, MobCategory.CREATURE).sized(0.9F, 1.4F).clientTrackingRange(10));
    public static final EntityType<Creeper> CREEPER = EntityType.register("creeper", EntityType.Builder.of(Creeper::new, MobCategory.MONSTER).sized(0.6F, 1.7F).clientTrackingRange(8));
    public static final EntityType<Dolphin> DOLPHIN = EntityType.register("dolphin", EntityType.Builder.of(Dolphin::new, MobCategory.WATER_CREATURE).sized(0.9F, 0.6F));
    public static final EntityType<Donkey> DONKEY = EntityType.register("donkey", EntityType.Builder.of(Donkey::new, MobCategory.CREATURE).sized(1.3964844F, 1.5F).clientTrackingRange(10));
    public static final EntityType<DragonFireball> DRAGON_FIREBALL = EntityType.register("dragon_fireball", EntityType.Builder.of(DragonFireball::new, MobCategory.MISC).sized(1.0F, 1.0F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<Drowned> DROWNED = EntityType.register("drowned", EntityType.Builder.of(Drowned::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8));
    public static final EntityType<ThrownEgg> EGG = EntityType.register("egg", EntityType.Builder.of(ThrownEgg::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<ElderGuardian> ELDER_GUARDIAN = EntityType.register("elder_guardian", EntityType.Builder.of(ElderGuardian::new, MobCategory.MONSTER).sized(1.9975F, 1.9975F).clientTrackingRange(10));
    public static final EntityType<EndCrystal> END_CRYSTAL = EntityType.register("end_crystal", EntityType.Builder.of(EndCrystal::new, MobCategory.MISC).sized(2.0F, 2.0F).clientTrackingRange(16).updateInterval(Integer.MAX_VALUE));
    public static final EntityType<EnderDragon> ENDER_DRAGON = EntityType.register("ender_dragon", EntityType.Builder.of(EnderDragon::new, MobCategory.MONSTER).fireImmune().sized(16.0F, 8.0F).clientTrackingRange(10));
    public static final EntityType<ThrownEnderpearl> ENDER_PEARL = EntityType.register("ender_pearl", EntityType.Builder.of(ThrownEnderpearl::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<EnderMan> ENDERMAN = EntityType.register("enderman", EntityType.Builder.of(EnderMan::new, MobCategory.MONSTER).sized(0.6F, 2.9F).clientTrackingRange(8));
    public static final EntityType<Endermite> ENDERMITE = EntityType.register("endermite", EntityType.Builder.of(Endermite::new, MobCategory.MONSTER).sized(0.4F, 0.3F).clientTrackingRange(8));
    public static final EntityType<Evoker> EVOKER = EntityType.register("evoker", EntityType.Builder.of(Evoker::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8));
    public static final EntityType<EvokerFangs> EVOKER_FANGS = EntityType.register("evoker_fangs", EntityType.Builder.of(EvokerFangs::new, MobCategory.MISC).sized(0.5F, 0.8F).clientTrackingRange(6).updateInterval(2));
    public static final EntityType<ThrownExperienceBottle> EXPERIENCE_BOTTLE = EntityType.register("experience_bottle", EntityType.Builder.of(ThrownExperienceBottle::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<ExperienceOrb> EXPERIENCE_ORB = EntityType.register("experience_orb", EntityType.Builder.of(ExperienceOrb::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(20));
    public static final EntityType<EyeOfEnder> EYE_OF_ENDER = EntityType.register("eye_of_ender", EntityType.Builder.of(EyeOfEnder::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(4));
    public static final EntityType<FallingBlockEntity> FALLING_BLOCK = EntityType.register("falling_block", EntityType.Builder.of(FallingBlockEntity::new, MobCategory.MISC).sized(0.98F, 0.98F).clientTrackingRange(10).updateInterval(20));
    public static final EntityType<FireworkRocketEntity> FIREWORK_ROCKET = EntityType.register("firework_rocket", EntityType.Builder.of(FireworkRocketEntity::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<Fox> FOX = EntityType.register("fox", EntityType.Builder.of(Fox::new, MobCategory.CREATURE).sized(0.6F, 0.7F).clientTrackingRange(8).immuneTo(Blocks.SWEET_BERRY_BUSH));
    public static final EntityType<Frog> FROG = EntityType.register("frog", EntityType.Builder.of(Frog::new, MobCategory.CREATURE).sized(0.5F, 0.5F).clientTrackingRange(10));
    public static final EntityType<MinecartFurnace> FURNACE_MINECART = EntityType.register("furnace_minecart", EntityType.Builder.of(MinecartFurnace::new, MobCategory.MISC).sized(0.98F, 0.7F).clientTrackingRange(8));
    public static final EntityType<Ghast> GHAST = EntityType.register("ghast", EntityType.Builder.of(Ghast::new, MobCategory.MONSTER).fireImmune().sized(4.0F, 4.0F).clientTrackingRange(10));
    public static final EntityType<Giant> GIANT = EntityType.register("giant", EntityType.Builder.of(Giant::new, MobCategory.MONSTER).sized(3.6F, 12.0F).clientTrackingRange(10));
    public static final EntityType<GlowItemFrame> GLOW_ITEM_FRAME = EntityType.register("glow_item_frame", EntityType.Builder.of(GlowItemFrame::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE));
    public static final EntityType<GlowSquid> GLOW_SQUID = EntityType.register("glow_squid", EntityType.Builder.of(GlowSquid::new, MobCategory.UNDERGROUND_WATER_CREATURE).sized(0.8F, 0.8F).clientTrackingRange(10));
    public static final EntityType<Goat> GOAT = EntityType.register("goat", EntityType.Builder.of(Goat::new, MobCategory.CREATURE).sized(0.9F, 1.3F).clientTrackingRange(10));
    public static final EntityType<Guardian> GUARDIAN = EntityType.register("guardian", EntityType.Builder.of(Guardian::new, MobCategory.MONSTER).sized(0.85F, 0.85F).clientTrackingRange(8));
    public static final EntityType<Hoglin> HOGLIN = EntityType.register("hoglin", EntityType.Builder.of(Hoglin::new, MobCategory.MONSTER).sized(1.3964844F, 1.4F).clientTrackingRange(8));
    public static final EntityType<MinecartHopper> HOPPER_MINECART = EntityType.register("hopper_minecart", EntityType.Builder.of(MinecartHopper::new, MobCategory.MISC).sized(0.98F, 0.7F).clientTrackingRange(8));
    public static final EntityType<Horse> HORSE = EntityType.register("horse", EntityType.Builder.of(Horse::new, MobCategory.CREATURE).sized(1.3964844F, 1.6F).clientTrackingRange(10));
    public static final EntityType<Husk> HUSK = EntityType.register("husk", EntityType.Builder.of(Husk::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8));
    public static final EntityType<Illusioner> ILLUSIONER = EntityType.register("illusioner", EntityType.Builder.of(Illusioner::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8));
    public static final EntityType<Interaction> INTERACTION = EntityType.register("interaction", EntityType.Builder.of(Interaction::new, MobCategory.MISC).sized(0.0F, 0.0F).clientTrackingRange(10));
    public static final EntityType<IronGolem> IRON_GOLEM = EntityType.register("iron_golem", EntityType.Builder.of(IronGolem::new, MobCategory.MISC).sized(1.4F, 2.7F).clientTrackingRange(10));
    public static final EntityType<ItemEntity> ITEM = EntityType.register("item", EntityType.Builder.of(ItemEntity::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(20));
    public static final EntityType<Display.ItemDisplay> ITEM_DISPLAY = EntityType.register("item_display", EntityType.Builder.of(Display.ItemDisplay::new, MobCategory.MISC).sized(0.0F, 0.0F).clientTrackingRange(10).updateInterval(1));
    public static final EntityType<ItemFrame> ITEM_FRAME = EntityType.register("item_frame", EntityType.Builder.of(ItemFrame::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE));
    public static final EntityType<LargeFireball> FIREBALL = EntityType.register("fireball", EntityType.Builder.of(LargeFireball::new, MobCategory.MISC).sized(1.0F, 1.0F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<LeashFenceKnotEntity> LEASH_KNOT = EntityType.register("leash_knot", EntityType.Builder.of(LeashFenceKnotEntity::new, MobCategory.MISC).noSave().sized(0.375F, 0.5F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE));
    public static final EntityType<LightningBolt> LIGHTNING_BOLT = EntityType.register("lightning_bolt", EntityType.Builder.of(LightningBolt::new, MobCategory.MISC).noSave().sized(0.0F, 0.0F).clientTrackingRange(16).updateInterval(Integer.MAX_VALUE));
    public static final EntityType<Llama> LLAMA = EntityType.register("llama", EntityType.Builder.of(Llama::new, MobCategory.CREATURE).sized(0.9F, 1.87F).clientTrackingRange(10));
    public static final EntityType<LlamaSpit> LLAMA_SPIT = EntityType.register("llama_spit", EntityType.Builder.of(LlamaSpit::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<MagmaCube> MAGMA_CUBE = EntityType.register("magma_cube", EntityType.Builder.of(MagmaCube::new, MobCategory.MONSTER).fireImmune().sized(2.04F, 2.04F).clientTrackingRange(8));
    public static final EntityType<Marker> MARKER = EntityType.register("marker", EntityType.Builder.of(Marker::new, MobCategory.MISC).sized(0.0F, 0.0F).clientTrackingRange(0));
    public static final EntityType<Minecart> MINECART = EntityType.register("minecart", EntityType.Builder.of(Minecart::new, MobCategory.MISC).sized(0.98F, 0.7F).clientTrackingRange(8));
    public static final EntityType<MushroomCow> MOOSHROOM = EntityType.register("mooshroom", EntityType.Builder.of(MushroomCow::new, MobCategory.CREATURE).sized(0.9F, 1.4F).clientTrackingRange(10));
    public static final EntityType<Mule> MULE = EntityType.register("mule", EntityType.Builder.of(Mule::new, MobCategory.CREATURE).sized(1.3964844F, 1.6F).clientTrackingRange(8));
    public static final EntityType<Ocelot> OCELOT = EntityType.register("ocelot", EntityType.Builder.of(Ocelot::new, MobCategory.CREATURE).sized(0.6F, 0.7F).clientTrackingRange(10));
    public static final EntityType<Painting> PAINTING = EntityType.register("painting", EntityType.Builder.of(Painting::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE));
    public static final EntityType<Panda> PANDA = EntityType.register("panda", EntityType.Builder.of(Panda::new, MobCategory.CREATURE).sized(1.3F, 1.25F).clientTrackingRange(10));
    public static final EntityType<Parrot> PARROT = EntityType.register("parrot", EntityType.Builder.of(Parrot::new, MobCategory.CREATURE).sized(0.5F, 0.9F).clientTrackingRange(8));
    public static final EntityType<Phantom> PHANTOM = EntityType.register("phantom", EntityType.Builder.of(Phantom::new, MobCategory.MONSTER).sized(0.9F, 0.5F).clientTrackingRange(8));
    public static final EntityType<Pig> PIG = EntityType.register("pig", EntityType.Builder.of(Pig::new, MobCategory.CREATURE).sized(0.9F, 0.9F).clientTrackingRange(10));
    public static final EntityType<Piglin> PIGLIN = EntityType.register("piglin", EntityType.Builder.of(Piglin::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8));
    public static final EntityType<PiglinBrute> PIGLIN_BRUTE = EntityType.register("piglin_brute", EntityType.Builder.of(PiglinBrute::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8));
    public static final EntityType<Pillager> PILLAGER = EntityType.register("pillager", EntityType.Builder.of(Pillager::new, MobCategory.MONSTER).canSpawnFarFromPlayer().sized(0.6F, 1.95F).clientTrackingRange(8));
    public static final EntityType<PolarBear> POLAR_BEAR = EntityType.register("polar_bear", EntityType.Builder.of(PolarBear::new, MobCategory.CREATURE).immuneTo(Blocks.POWDER_SNOW).sized(1.4F, 1.4F).clientTrackingRange(10));
    public static final EntityType<ThrownPotion> POTION = EntityType.register("potion", EntityType.Builder.of(ThrownPotion::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<Pufferfish> PUFFERFISH = EntityType.register("pufferfish", EntityType.Builder.of(Pufferfish::new, MobCategory.WATER_AMBIENT).sized(0.7F, 0.7F).clientTrackingRange(4));
    public static final EntityType<Rabbit> RABBIT = EntityType.register("rabbit", EntityType.Builder.of(Rabbit::new, MobCategory.CREATURE).sized(0.4F, 0.5F).clientTrackingRange(8));
    public static final EntityType<Ravager> RAVAGER = EntityType.register("ravager", EntityType.Builder.of(Ravager::new, MobCategory.MONSTER).sized(1.95F, 2.2F).clientTrackingRange(10));
    public static final EntityType<Salmon> SALMON = EntityType.register("salmon", EntityType.Builder.of(Salmon::new, MobCategory.WATER_AMBIENT).sized(0.7F, 0.4F).clientTrackingRange(4));
    public static final EntityType<Sheep> SHEEP = EntityType.register("sheep", EntityType.Builder.of(Sheep::new, MobCategory.CREATURE).sized(0.9F, 1.3F).clientTrackingRange(10));
    public static final EntityType<Shulker> SHULKER = EntityType.register("shulker", EntityType.Builder.of(Shulker::new, MobCategory.MONSTER).fireImmune().canSpawnFarFromPlayer().sized(1.0F, 1.0F).clientTrackingRange(10));
    public static final EntityType<ShulkerBullet> SHULKER_BULLET = EntityType.register("shulker_bullet", EntityType.Builder.of(ShulkerBullet::new, MobCategory.MISC).sized(0.3125F, 0.3125F).clientTrackingRange(8));
    public static final EntityType<Silverfish> SILVERFISH = EntityType.register("silverfish", EntityType.Builder.of(Silverfish::new, MobCategory.MONSTER).sized(0.4F, 0.3F).clientTrackingRange(8));
    public static final EntityType<Skeleton> SKELETON = EntityType.register("skeleton", EntityType.Builder.of(Skeleton::new, MobCategory.MONSTER).sized(0.6F, 1.99F).clientTrackingRange(8));
    public static final EntityType<SkeletonHorse> SKELETON_HORSE = EntityType.register("skeleton_horse", EntityType.Builder.of(SkeletonHorse::new, MobCategory.CREATURE).sized(1.3964844F, 1.6F).clientTrackingRange(10));
    public static final EntityType<Slime> SLIME = EntityType.register("slime", EntityType.Builder.of(Slime::new, MobCategory.MONSTER).sized(2.04F, 2.04F).clientTrackingRange(10));
    public static final EntityType<SmallFireball> SMALL_FIREBALL = EntityType.register("small_fireball", EntityType.Builder.of(SmallFireball::new, MobCategory.MISC).sized(0.3125F, 0.3125F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<Sniffer> SNIFFER = EntityType.register("sniffer", EntityType.Builder.of(Sniffer::new, MobCategory.CREATURE).sized(1.9F, 1.75F).clientTrackingRange(10));
    public static final EntityType<SnowGolem> SNOW_GOLEM = EntityType.register("snow_golem", EntityType.Builder.of(SnowGolem::new, MobCategory.MISC).immuneTo(Blocks.POWDER_SNOW).sized(0.7F, 1.9F).clientTrackingRange(8));
    public static final EntityType<Snowball> SNOWBALL = EntityType.register("snowball", EntityType.Builder.of(Snowball::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<MinecartSpawner> SPAWNER_MINECART = EntityType.register("spawner_minecart", EntityType.Builder.of(MinecartSpawner::new, MobCategory.MISC).sized(0.98F, 0.7F).clientTrackingRange(8));
    public static final EntityType<SpectralArrow> SPECTRAL_ARROW = EntityType.register("spectral_arrow", EntityType.Builder.of(SpectralArrow::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(4).updateInterval(20));
    public static final EntityType<Spider> SPIDER = EntityType.register("spider", EntityType.Builder.of(Spider::new, MobCategory.MONSTER).sized(1.4F, 0.9F).clientTrackingRange(8));
    public static final EntityType<Squid> SQUID = EntityType.register("squid", EntityType.Builder.of(Squid::new, MobCategory.WATER_CREATURE).sized(0.8F, 0.8F).clientTrackingRange(8));
    public static final EntityType<Stray> STRAY = EntityType.register("stray", EntityType.Builder.of(Stray::new, MobCategory.MONSTER).sized(0.6F, 1.99F).immuneTo(Blocks.POWDER_SNOW).clientTrackingRange(8));
    public static final EntityType<Strider> STRIDER = EntityType.register("strider", EntityType.Builder.of(Strider::new, MobCategory.CREATURE).fireImmune().sized(0.9F, 1.7F).clientTrackingRange(10));
    public static final EntityType<Tadpole> TADPOLE = EntityType.register("tadpole", EntityType.Builder.of(Tadpole::new, MobCategory.CREATURE).sized(Tadpole.HITBOX_WIDTH, Tadpole.HITBOX_HEIGHT).clientTrackingRange(10));
    public static final EntityType<Display.TextDisplay> TEXT_DISPLAY = EntityType.register("text_display", EntityType.Builder.of(Display.TextDisplay::new, MobCategory.MISC).sized(0.0F, 0.0F).clientTrackingRange(10).updateInterval(1));
    public static final EntityType<PrimedTnt> TNT = EntityType.register("tnt", EntityType.Builder.of(PrimedTnt::new, MobCategory.MISC).fireImmune().sized(0.98F, 0.98F).clientTrackingRange(10).updateInterval(10));
    public static final EntityType<MinecartTNT> TNT_MINECART = EntityType.register("tnt_minecart", EntityType.Builder.of(MinecartTNT::new, MobCategory.MISC).sized(0.98F, 0.7F).clientTrackingRange(8));
    public static final EntityType<TraderLlama> TRADER_LLAMA = EntityType.register("trader_llama", EntityType.Builder.of(TraderLlama::new, MobCategory.CREATURE).sized(0.9F, 1.87F).clientTrackingRange(10));
    public static final EntityType<ThrownTrident> TRIDENT = EntityType.register("trident", EntityType.Builder.of(ThrownTrident::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(4).updateInterval(20));
    public static final EntityType<TropicalFish> TROPICAL_FISH = EntityType.register("tropical_fish", EntityType.Builder.of(TropicalFish::new, MobCategory.WATER_AMBIENT).sized(0.5F, 0.4F).clientTrackingRange(4));
    public static final EntityType<Turtle> TURTLE = EntityType.register("turtle", EntityType.Builder.of(Turtle::new, MobCategory.CREATURE).sized(1.2F, 0.4F).clientTrackingRange(10));
    public static final EntityType<Vex> VEX = EntityType.register("vex", EntityType.Builder.of(Vex::new, MobCategory.MONSTER).fireImmune().sized(0.4F, 0.8F).clientTrackingRange(8));
    public static final EntityType<Villager> VILLAGER = EntityType.register("villager", EntityType.Builder.of(Villager::new, MobCategory.MISC).sized(0.6F, 1.95F).clientTrackingRange(10));
    public static final EntityType<Vindicator> VINDICATOR = EntityType.register("vindicator", EntityType.Builder.of(Vindicator::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8));
    public static final EntityType<WanderingTrader> WANDERING_TRADER = EntityType.register("wandering_trader", EntityType.Builder.of(WanderingTrader::new, MobCategory.CREATURE).sized(0.6F, 1.95F).clientTrackingRange(10));
    public static final EntityType<Warden> WARDEN = EntityType.register("warden", EntityType.Builder.of(Warden::new, MobCategory.MONSTER).sized(0.9F, 2.9F).clientTrackingRange(16).fireImmune());
    public static final EntityType<Witch> WITCH = EntityType.register("witch", EntityType.Builder.of(Witch::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8));
    public static final EntityType<WitherBoss> WITHER = EntityType.register("wither", EntityType.Builder.of(WitherBoss::new, MobCategory.MONSTER).fireImmune().immuneTo(Blocks.WITHER_ROSE).sized(0.9F, 3.5F).clientTrackingRange(10));
    public static final EntityType<WitherSkeleton> WITHER_SKELETON = EntityType.register("wither_skeleton", EntityType.Builder.of(WitherSkeleton::new, MobCategory.MONSTER).fireImmune().immuneTo(Blocks.WITHER_ROSE).sized(0.7F, 2.4F).clientTrackingRange(8));
    public static final EntityType<WitherSkull> WITHER_SKULL = EntityType.register("wither_skull", EntityType.Builder.of(WitherSkull::new, MobCategory.MISC).sized(0.3125F, 0.3125F).clientTrackingRange(4).updateInterval(10));
    public static final EntityType<Wolf> WOLF = EntityType.register("wolf", EntityType.Builder.of(Wolf::new, MobCategory.CREATURE).sized(0.6F, 0.85F).clientTrackingRange(10));
    public static final EntityType<Zoglin> ZOGLIN = EntityType.register("zoglin", EntityType.Builder.of(Zoglin::new, MobCategory.MONSTER).fireImmune().sized(1.3964844F, 1.4F).clientTrackingRange(8));
    public static final EntityType<Zombie> ZOMBIE = EntityType.register("zombie", EntityType.Builder.of(Zombie::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8));
    public static final EntityType<ZombieHorse> ZOMBIE_HORSE = EntityType.register("zombie_horse", EntityType.Builder.of(ZombieHorse::new, MobCategory.CREATURE).sized(1.3964844F, 1.6F).clientTrackingRange(10));
    public static final EntityType<ZombieVillager> ZOMBIE_VILLAGER = EntityType.register("zombie_villager", EntityType.Builder.of(ZombieVillager::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8));
    public static final EntityType<ZombifiedPiglin> ZOMBIFIED_PIGLIN = EntityType.register("zombified_piglin", EntityType.Builder.of(ZombifiedPiglin::new, MobCategory.MONSTER).fireImmune().sized(0.6F, 1.95F).clientTrackingRange(8));
    public static final EntityType<Player> PLAYER = EntityType.register("player", EntityType.Builder.createNothing(MobCategory.MISC).noSave().noSummon().sized(0.6F, 1.8F).clientTrackingRange(32).updateInterval(2));
    public static final EntityType<FishingHook> FISHING_BOBBER = EntityType.register("fishing_bobber", EntityType.Builder.of(FishingHook::new, MobCategory.MISC).noSave().noSummon().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(5));
    private final EntityType.EntityFactory<T> factory;
    private final MobCategory category;
    private final ImmutableSet<Block> immuneTo;
    private final boolean serialize;
    private final boolean summon;
    private final boolean fireImmune;
    private final boolean canSpawnFarFromPlayer;
    private final int clientTrackingRange;
    private final int updateInterval;
    @Nullable
    private String descriptionId;
    @Nullable
    private Component description;
    @Nullable
    private ResourceLocation lootTable;
    private final EntityDimensions dimensions;
    private final FeatureFlagSet requiredFeatures;

    private static <T extends Entity> EntityType<T> register(String id, EntityType.Builder type) { // CraftBukkit - decompile error
        return (EntityType) Registry.register(BuiltInRegistries.ENTITY_TYPE, id, (EntityType<T>) type.build(id)); // CraftBukkit - decompile error
    }

    public static ResourceLocation getKey(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type);
    }

    public static Optional<EntityType<?>> byString(String id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.tryParse(id));
    }

    // Folia start - profiler
    public final int tickTimerId;
    public final int inactiveTickTimerId;
    public final int passengerTickTimerId;
    public final int passengerInactiveTickTimerId;
    // Folia end - profiler

    public EntityType(EntityType.EntityFactory<T> factory, MobCategory spawnGroup, boolean saveable, boolean summonable, boolean fireImmune, boolean spawnableFarFromPlayer, ImmutableSet<Block> canSpawnInside, EntityDimensions dimensions, int maxTrackDistance, int trackTickInterval, FeatureFlagSet requiredFeatures) {
       // Paper start
        this(factory, spawnGroup, saveable, summonable, fireImmune, spawnableFarFromPlayer, canSpawnInside, dimensions, maxTrackDistance, trackTickInterval, requiredFeatures, "custom");
    }
    public EntityType(EntityType.EntityFactory<T> factory, MobCategory spawnGroup, boolean saveable, boolean summonable, boolean fireImmune, boolean spawnableFarFromPlayer, ImmutableSet<Block> canSpawnInside, EntityDimensions dimensions, int maxTrackDistance, int trackTickInterval, FeatureFlagSet requiredFeatures, String id) {
        this.tickTimer = co.aikar.timings.MinecraftTimings.getEntityTimings(id, "tick");
        this.inactiveTickTimer = co.aikar.timings.MinecraftTimings.getEntityTimings(id, "inactiveTick");
        this.passengerTickTimer = co.aikar.timings.MinecraftTimings.getEntityTimings(id, "passengerTick");
        this.passengerInactiveTickTimer = co.aikar.timings.MinecraftTimings.getEntityTimings(id, "passengerInactiveTick");
        // Paper end
        // Folia start - profiler
        this.tickTimerId = ca.spottedleaf.leafprofiler.LProfilerRegistry.GLOBAL_REGISTRY.getOrCreateTimer("Entity Tick: " + id);
        this.inactiveTickTimerId = ca.spottedleaf.leafprofiler.LProfilerRegistry.GLOBAL_REGISTRY.getOrCreateTimer("Inactive Entity Tick: " + id);
        this.passengerTickTimerId = ca.spottedleaf.leafprofiler.LProfilerRegistry.GLOBAL_REGISTRY.getOrCreateTimer("Passenger Entity Tick: " + id);
        this.passengerInactiveTickTimerId = ca.spottedleaf.leafprofiler.LProfilerRegistry.GLOBAL_REGISTRY.getOrCreateTimer("Passenger Inactive Entity Tick: " + id);
        // Folia end - profiler
        this.builtInRegistryHolder = BuiltInRegistries.ENTITY_TYPE.createIntrusiveHolder(this);
        this.factory = factory;
        this.category = spawnGroup;
        this.canSpawnFarFromPlayer = spawnableFarFromPlayer;
        this.serialize = saveable;
        this.summon = summonable;
        this.fireImmune = fireImmune;
        this.immuneTo = canSpawnInside;
        this.dimensions = dimensions;
        this.clientTrackingRange = maxTrackDistance;
        this.updateInterval = trackTickInterval;
        this.requiredFeatures = requiredFeatures;
    }

    @Nullable
    public T spawn(ServerLevel world, @Nullable ItemStack stack, @Nullable Player player, BlockPos pos, MobSpawnType spawnReason, boolean alignPosition, boolean invertY) {
        // CraftBukkit start
        return this.spawn(world, stack, player, pos, spawnReason, alignPosition, invertY, spawnReason == MobSpawnType.DISPENSER ? org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DISPENSE_EGG : org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER_EGG); // Paper - use correct spawn reason for dispenser spawn eggs
    }

    @Nullable
    public T spawn(ServerLevel worldserver, @Nullable ItemStack itemstack, @Nullable Player entityhuman, BlockPos blockposition, MobSpawnType enummobspawn, boolean flag, boolean flag1, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason spawnReason) {
        // CraftBukkit end
        CompoundTag nbttagcompound;
        Consumer<T> consumer; // CraftBukkit - decompile error

        if (itemstack != null) {
            nbttagcompound = itemstack.getTag();
            consumer = EntityType.createDefaultStackConfig(worldserver, itemstack, entityhuman);
        } else {
            consumer = (entity) -> {
            };
            nbttagcompound = null;
        }

        return this.spawn(worldserver, nbttagcompound, consumer, blockposition, enummobspawn, flag, flag1, spawnReason); // CraftBukkit
    }

    public static <T extends Entity> Consumer<T> createDefaultStackConfig(ServerLevel world, ItemStack stack, @Nullable Player player) {
        return EntityType.appendDefaultStackConfig((entity) -> {
        }, world, stack, player);
    }

    public static <T extends Entity> Consumer<T> appendDefaultStackConfig(Consumer<T> chained, ServerLevel world, ItemStack stack, @Nullable Player player) {
        return EntityType.appendCustomEntityStackConfig(EntityType.appendCustomNameConfig(chained, stack), world, stack, player);
    }

    public static <T extends Entity> Consumer<T> appendCustomNameConfig(Consumer<T> chained, ItemStack stack) {
        return stack.hasCustomHoverName() ? chained.andThen((entity) -> {
            entity.setCustomName(stack.getHoverName());
        }) : chained;
    }

    public static <T extends Entity> Consumer<T> appendCustomEntityStackConfig(Consumer<T> chained, ServerLevel world, ItemStack stack, @Nullable Player player) {
        CompoundTag nbttagcompound = stack.getTag();

        return nbttagcompound != null ? chained.andThen((entity) -> {
            try { EntityType.updateCustomEntityTag(world, player, entity, nbttagcompound); } catch (Throwable t) { EntityType.LOGGER.warn("Error loading spawn egg NBT", t); } // CraftBukkit - SPIGOT-5665
        }) : chained;
    }

    @Nullable
    public T spawn(ServerLevel world, BlockPos pos, MobSpawnType reason) {
        // CraftBukkit start
        return this.spawn(world, pos, reason, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    @Nullable
    public T spawn(ServerLevel worldserver, BlockPos blockposition, MobSpawnType enummobspawn, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason spawnReason) {
        return this.spawn(worldserver, (CompoundTag) null, null, blockposition, enummobspawn, false, false, spawnReason); // CraftBukkit - decompile error
        // CraftBukkit end
    }

    @Nullable
    public T spawn(ServerLevel world, @Nullable CompoundTag itemNbt, @Nullable Consumer<T> afterConsumer, BlockPos pos, MobSpawnType reason, boolean alignPosition, boolean invertY) {
        // CraftBukkit start
        return this.spawn(world, itemNbt, afterConsumer, pos, reason, alignPosition, invertY, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    @Nullable
    public T spawn(ServerLevel worldserver, @Nullable CompoundTag nbttagcompound, @Nullable Consumer<T> consumer, BlockPos blockposition, MobSpawnType enummobspawn, boolean flag, boolean flag1, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason spawnReason) {
        // CraftBukkit end
        // Paper start - Call PreCreatureSpawnEvent
        org.bukkit.entity.EntityType type = org.bukkit.entity.EntityType.fromName(EntityType.getKey(this).getPath());
        if (type != null) {
            com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent event;
            event = new com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent(
                io.papermc.paper.util.MCUtil.toLocation(worldserver, blockposition),
                type,
                spawnReason
            );
            if (!event.callEvent()) {
                return null;
            }
        }
        // Paper end
        T t0 = this.create(worldserver, nbttagcompound, consumer, blockposition, enummobspawn, flag, flag1);

        if (t0 != null) {
            worldserver.addFreshEntityWithPassengers(t0, spawnReason);
            return !t0.isRemoved() ? t0 : null; // Don't return an entity when CreatureSpawnEvent is canceled
            // CraftBukkit end
        }

        return t0;
    }

    @Nullable
    public T create(ServerLevel world, @Nullable CompoundTag itemNbt, @Nullable Consumer<T> afterConsumer, BlockPos pos, MobSpawnType reason, boolean alignPosition, boolean invertY) {
        T t0 = this.create(world);

        if (t0 == null) {
            return null;
        } else {
            double d0;

            if (alignPosition) {
                t0.setPos((double) pos.getX() + 0.5D, (double) (pos.getY() + 1), (double) pos.getZ() + 0.5D);
                d0 = EntityType.getYOffset(world, pos, invertY, t0.getBoundingBox());
            } else {
                d0 = 0.0D;
            }

            t0.moveTo((double) pos.getX() + 0.5D, (double) pos.getY() + d0, (double) pos.getZ() + 0.5D, Mth.wrapDegrees(world.random.nextFloat() * 360.0F), 0.0F);
            if (t0 instanceof Mob) {
                Mob entityinsentient = (Mob) t0;

                entityinsentient.yHeadRot = entityinsentient.getYRot();
                entityinsentient.yBodyRot = entityinsentient.getYRot();
                entityinsentient.finalizeSpawn(world, world.getCurrentDifficultyAt(entityinsentient.blockPosition()), reason, (SpawnGroupData) null, itemNbt);
                entityinsentient.playAmbientSound();
            }

            if (afterConsumer != null) {
                afterConsumer.accept(t0);
            }

            return t0;
        }
    }

    protected static double getYOffset(LevelReader world, BlockPos pos, boolean invertY, AABB boundingBox) {
        AABB axisalignedbb1 = new AABB(pos);

        if (invertY) {
            axisalignedbb1 = axisalignedbb1.expandTowards(0.0D, -1.0D, 0.0D);
        }

        Iterable<VoxelShape> iterable = world.getCollisions((Entity) null, axisalignedbb1);

        return 1.0D + Shapes.collide(Direction.Axis.Y, boundingBox, iterable, invertY ? -2.0D : -1.0D);
    }

    public static void updateCustomEntityTag(Level world, @Nullable Player player, @Nullable Entity entity, @Nullable CompoundTag itemNbt) {
        if (itemNbt != null && itemNbt.contains("EntityTag", 10)) {
            MinecraftServer minecraftserver = world.getServer();

            if (minecraftserver != null && entity != null) {
                if (world.isClientSide || !entity.onlyOpCanSetNbt() || player != null && minecraftserver.getPlayerList().isOp(player.getGameProfile())) {
                    CompoundTag nbttagcompound1 = entity.saveWithoutId(new CompoundTag());
                    UUID uuid = entity.getUUID();
                    // Paper start - filter out protected tags
                    if (player == null || !player.getBukkitEntity().hasPermission("minecraft.nbt.place")) {
                        for (net.minecraft.commands.arguments.NbtPathArgument.NbtPath tag : world.paperConfig().entities.spawning.filteredEntityTagNbtPaths) {
                            tag.remove(itemNbt.getCompound("EntityTag"));
                        }
                    }
                    // Paper end

                    nbttagcompound1.merge(itemNbt.getCompound("EntityTag"));
                    entity.setUUID(uuid);
                    entity.load(nbttagcompound1);
                }
            }
        }
    }

    public boolean canSerialize() {
        return this.serialize;
    }

    public boolean canSummon() {
        return this.summon;
    }

    public boolean fireImmune() {
        return this.fireImmune;
    }

    public boolean canSpawnFarFromPlayer() {
        return this.canSpawnFarFromPlayer;
    }

    public MobCategory getCategory() {
        return this.category;
    }

    public String getDescriptionId() {
        if (this.descriptionId == null) {
            this.descriptionId = Util.makeDescriptionId("entity", BuiltInRegistries.ENTITY_TYPE.getKey(this));
        }

        return this.descriptionId;
    }

    public Component getDescription() {
        if (this.description == null) {
            this.description = Component.translatable(this.getDescriptionId());
        }

        return this.description;
    }

    public String toString() {
        return this.getDescriptionId();
    }

    public String toShortString() {
        int i = this.getDescriptionId().lastIndexOf(46);

        return i == -1 ? this.getDescriptionId() : this.getDescriptionId().substring(i + 1);
    }

    public ResourceLocation getDefaultLootTable() {
        if (this.lootTable == null) {
            ResourceLocation minecraftkey = BuiltInRegistries.ENTITY_TYPE.getKey(this);

            this.lootTable = minecraftkey.withPrefix("entities/");
        }

        return this.lootTable;
    }

    public float getWidth() {
        return this.dimensions.width;
    }

    public float getHeight() {
        return this.dimensions.height;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    @Nullable
    public T create(Level world) {
        return !this.isEnabled(world.enabledFeatures()) ? null : this.factory.create(this, world);
    }

    public static Optional<Entity> create(CompoundTag nbt, Level world) {
        return Util.ifElse(EntityType.by(nbt).map((entitytypes) -> {
            return entitytypes.create(world);
        }), (entity) -> {
            entity.load(nbt);
        }, () -> {
            EntityType.LOGGER.warn("Skipping Entity with id {}", nbt.getString("id"));
        });
    }

    public AABB getAABB(double feetX, double feetY, double feetZ) {
        float f = this.getWidth() / 2.0F;

        return new AABB(feetX - (double) f, feetY, feetZ - (double) f, feetX + (double) f, feetY + (double) this.getHeight(), feetZ + (double) f);
    }

    public boolean isBlockDangerous(BlockState state) {
        return this.immuneTo.contains(state.getBlock()) ? false : (!this.fireImmune && WalkNodeEvaluator.isBurningBlock(state) ? true : state.is(Blocks.WITHER_ROSE) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.CACTUS) || state.is(Blocks.POWDER_SNOW));
    }

    public EntityDimensions getDimensions() {
        return this.dimensions;
    }

    public static Optional<EntityType<?>> by(CompoundTag nbt) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(new ResourceLocation(nbt.getString("id")));
    }

    @Nullable
    public static Entity loadEntityRecursive(CompoundTag nbt, Level world, Function<Entity, Entity> entityProcessor) {
        return (Entity) EntityType.loadStaticEntity(nbt, world).map(entityProcessor).map((entity) -> {
            if (nbt.contains("Passengers", 9)) {
                ListTag nbttaglist = nbt.getList("Passengers", 10);

                for (int i = 0; i < nbttaglist.size(); ++i) {
                    Entity entity1 = EntityType.loadEntityRecursive(nbttaglist.getCompound(i), world, entityProcessor);

                    if (entity1 != null) {
                        entity1.startRiding(entity, true);
                    }
                }
            }

            return entity;
        }).orElse(null); // CraftBukkit - decompile error
    }

    public static Stream<Entity> loadEntitiesRecursive(final List<? extends Tag> entityNbtList, final Level world) {
        final Spliterator<? extends Tag> spliterator = entityNbtList.spliterator();

        return StreamSupport.stream(new Spliterator<Entity>() {
            final java.util.Map<EntityType<?>, Integer> loadedEntityCounts = new java.util.HashMap<>(); // Paper
            public boolean tryAdvance(Consumer<? super Entity> consumer) {
                return spliterator.tryAdvance((nbtbase) -> {
                    EntityType.loadEntityRecursive((CompoundTag) nbtbase, world, (entity) -> {
                        // Paper start
                        final EntityType<?> entityType = entity.getType();
                        final int saveLimit = world.paperConfig().chunks.entityPerChunkSaveLimit.getOrDefault(entityType, -1);
                        if (saveLimit > -1) {
                            if (this.loadedEntityCounts.getOrDefault(entityType, 0) >= saveLimit) {
                                return null;
                            }
                            this.loadedEntityCounts.merge(entityType, 1, Integer::sum);
                        }
                        // Paper end
                        consumer.accept(entity);
                        return entity;
                    });
                });
            }

            public Spliterator<Entity> trySplit() {
                return null;
            }

            public long estimateSize() {
                return (long) entityNbtList.size();
            }

            public int characteristics() {
                return 1297;
            }
        }, false);
    }

    private static Optional<Entity> loadStaticEntity(CompoundTag nbt, Level world) {
        try {
            return EntityType.create(nbt, world);
        } catch (RuntimeException runtimeexception) {
            EntityType.LOGGER.warn("Exception loading entity: ", runtimeexception);
            return Optional.empty();
        }
    }

    public int clientTrackingRange() {
        return this.clientTrackingRange;
    }

    public int updateInterval() {
        return this.updateInterval;
    }

    // Paper start - timings
    public final co.aikar.timings.Timing tickTimer;
    public final co.aikar.timings.Timing inactiveTickTimer;
    public final co.aikar.timings.Timing passengerTickTimer;
    public final co.aikar.timings.Timing passengerInactiveTickTimer;
    // Paper end
    public boolean trackDeltas() {
        return this != EntityType.PLAYER && this != EntityType.LLAMA_SPIT && this != EntityType.WITHER && this != EntityType.BAT && this != EntityType.ITEM_FRAME && this != EntityType.GLOW_ITEM_FRAME && this != EntityType.LEASH_KNOT && this != EntityType.PAINTING && this != EntityType.END_CRYSTAL && this != EntityType.EVOKER_FANGS;
    }

    public boolean is(TagKey<EntityType<?>> tag) {
        return this.builtInRegistryHolder.is(tag);
    }

    public boolean is(HolderSet<EntityType<?>> entityTypeEntryList) {
        return entityTypeEntryList.contains(this.builtInRegistryHolder);
    }

    @Nullable
    public T tryCast(Entity obj) {
        return obj.getType() == this ? (T) obj : null; // CraftBukkit - decompile error
    }

    @Override
    public Class<? extends Entity> getBaseClass() {
        return Entity.class;
    }

    /** @deprecated */
    @Deprecated
    public Holder.Reference<EntityType<?>> builtInRegistryHolder() {
        return this.builtInRegistryHolder;
    }

    public static class Builder<T extends Entity> {

        private final EntityType.EntityFactory<T> factory;
        private final MobCategory category;
        private ImmutableSet<Block> immuneTo = ImmutableSet.of();
        private boolean serialize = true;
        private boolean summon = true;
        private boolean fireImmune;
        private boolean canSpawnFarFromPlayer;
        private int clientTrackingRange = 5;
        private int updateInterval = 3;
        private EntityDimensions dimensions = EntityDimensions.scalable(0.6F, 1.8F);
        private FeatureFlagSet requiredFeatures;

        private Builder(EntityType.EntityFactory<T> factory, MobCategory spawnGroup) {
            this.requiredFeatures = FeatureFlags.VANILLA_SET;
            this.factory = factory;
            this.category = spawnGroup;
            this.canSpawnFarFromPlayer = spawnGroup == MobCategory.CREATURE || spawnGroup == MobCategory.MISC;
        }

        public static <T extends Entity> EntityType.Builder<T> of(EntityType.EntityFactory factory, MobCategory spawnGroup) { // CraftBukkit - decompile error
            return new EntityType.Builder<>(factory, spawnGroup);
        }

        public static <T extends Entity> EntityType.Builder<T> createNothing(MobCategory spawnGroup) {
            return new EntityType.Builder<>((entitytypes, world) -> {
                return null;
            }, spawnGroup);
        }

        public EntityType.Builder<T> sized(float width, float height) {
            this.dimensions = EntityDimensions.scalable(width, height);
            return this;
        }

        public EntityType.Builder<T> noSummon() {
            this.summon = false;
            return this;
        }

        public EntityType.Builder<T> noSave() {
            this.serialize = false;
            return this;
        }

        public EntityType.Builder<T> fireImmune() {
            this.fireImmune = true;
            return this;
        }

        public EntityType.Builder<T> immuneTo(Block... blocks) {
            this.immuneTo = ImmutableSet.copyOf(blocks);
            return this;
        }

        public EntityType.Builder<T> canSpawnFarFromPlayer() {
            this.canSpawnFarFromPlayer = true;
            return this;
        }

        public EntityType.Builder<T> clientTrackingRange(int maxTrackingRange) {
            this.clientTrackingRange = maxTrackingRange;
            return this;
        }

        public EntityType.Builder<T> updateInterval(int trackingTickInterval) {
            this.updateInterval = trackingTickInterval;
            return this;
        }

        public EntityType.Builder<T> requiredFeatures(FeatureFlag... features) {
            this.requiredFeatures = FeatureFlags.REGISTRY.subset(features);
            return this;
        }

        public EntityType<T> build(String id) {
            if (this.serialize) {
                Util.fetchChoiceType(References.ENTITY_TREE, id);
            }

            return new EntityType<>(this.factory, this.category, this.serialize, this.summon, this.fireImmune, this.canSpawnFarFromPlayer, this.immuneTo, this.dimensions, this.clientTrackingRange, this.updateInterval, this.requiredFeatures, id); // Paper - add id
        }
    }

    public interface EntityFactory<T extends Entity> {

        T create(EntityType<T> type, Level world);
    }
}
