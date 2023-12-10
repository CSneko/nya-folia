package net.minecraft.world.level.block.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.LockCode;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
// CraftBukkit start
import org.bukkit.craftbukkit.potion.CraftPotionUtil;
import org.bukkit.potion.PotionEffect;
// CraftBukkit end
// Paper start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import com.destroystokyo.paper.event.block.BeaconEffectEvent;
// Paper end

public class BeaconBlockEntity extends BlockEntity implements MenuProvider, Nameable {

    private static final int MAX_LEVELS = 4;
    public static final MobEffect[][] BEACON_EFFECTS = new MobEffect[][]{{MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED}, {MobEffects.DAMAGE_RESISTANCE, MobEffects.JUMP}, {MobEffects.DAMAGE_BOOST}, {MobEffects.REGENERATION}};
    private static final Set<MobEffect> VALID_EFFECTS = (Set) Arrays.stream(BeaconBlockEntity.BEACON_EFFECTS).flatMap(Arrays::stream).collect(Collectors.toSet());
    public static final int DATA_LEVELS = 0;
    public static final int DATA_PRIMARY = 1;
    public static final int DATA_SECONDARY = 2;
    public static final int NUM_DATA_VALUES = 3;
    private static final int BLOCKS_CHECK_PER_TICK = 10;
    private static final Component DEFAULT_NAME = Component.translatable("container.beacon");
    private static final String TAG_PRIMARY = "primary_effect";
    private static final String TAG_SECONDARY = "secondary_effect";
    List<BeaconBlockEntity.BeaconBeamSection> beamSections = Lists.newArrayList();
    private List<BeaconBlockEntity.BeaconBeamSection> checkingBeamSections = Lists.newArrayList();
    public int levels;
    private int lastCheckY;
    @Nullable
    public MobEffect primaryPower;
    @Nullable
    public MobEffect secondaryPower;
    @Nullable
    public Component name;
    public LockCode lockKey;
    private final ContainerData dataAccess;
    // CraftBukkit start - add fields and methods
    public PotionEffect getPrimaryEffect() {
        return (this.primaryPower != null) ? CraftPotionUtil.toBukkit(new MobEffectInstance(this.primaryPower, BeaconBlockEntity.getLevel(this.levels), BeaconBlockEntity.getAmplification(this.levels, this.primaryPower, this.secondaryPower), true, true)) : null;
    }

    public PotionEffect getSecondaryEffect() {
        return (BeaconBlockEntity.hasSecondaryEffect(this.levels, this.primaryPower, this.secondaryPower)) ? CraftPotionUtil.toBukkit(new MobEffectInstance(this.secondaryPower, BeaconBlockEntity.getLevel(this.levels), BeaconBlockEntity.getAmplification(this.levels, this.primaryPower, this.secondaryPower), true, true)) : null;
    }
    // CraftBukkit end
    // Paper start - add field/methods for custom range
    private final String PAPER_RANGE_TAG = "Paper.Range";
    private double effectRange = -1;

    public double getEffectRange() {
        if (this.effectRange < 0) {
            return this.levels * 10 + 10;
        } else {
            return effectRange;
        }
    }

    public void setEffectRange(double range) {
        this.effectRange = range;
    }

    public void resetEffectRange() {
        this.effectRange = -1;
    }
    // Paper end

    @Nullable
    static MobEffect filterEffect(@Nullable MobEffect effect) {
        return BeaconBlockEntity.VALID_EFFECTS.contains(effect) ? effect : null;
    }

    public BeaconBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.BEACON, pos, state);
        this.lockKey = LockCode.NO_LOCK;
        this.dataAccess = new ContainerData() {
            @Override
            public int get(int index) {
                int j;

                switch (index) {
                    case 0:
                        j = BeaconBlockEntity.this.levels;
                        break;
                    case 1:
                        j = BeaconMenu.encodeEffect(BeaconBlockEntity.this.primaryPower);
                        break;
                    case 2:
                        j = BeaconMenu.encodeEffect(BeaconBlockEntity.this.secondaryPower);
                        break;
                    default:
                        j = 0;
                }

                return j;
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0:
                        BeaconBlockEntity.this.levels = value;
                        break;
                    case 1:
                        if (!BeaconBlockEntity.this.level.isClientSide && !BeaconBlockEntity.this.beamSections.isEmpty()) {
                            BeaconBlockEntity.playSound(BeaconBlockEntity.this.level, BeaconBlockEntity.this.worldPosition, SoundEvents.BEACON_POWER_SELECT);
                        }

                        BeaconBlockEntity.this.primaryPower = BeaconBlockEntity.filterEffect(BeaconMenu.decodeEffect(value));
                        break;
                    case 2:
                        BeaconBlockEntity.this.secondaryPower = BeaconBlockEntity.filterEffect(BeaconMenu.decodeEffect(value));
                }

            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    public static void tick(Level world, BlockPos pos, BlockState state, BeaconBlockEntity blockEntity) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        BlockPos blockposition1;

        if (blockEntity.lastCheckY < j) {
            blockposition1 = pos;
            blockEntity.checkingBeamSections = Lists.newArrayList();
            blockEntity.lastCheckY = pos.getY() - 1;
        } else {
            blockposition1 = new BlockPos(i, blockEntity.lastCheckY + 1, k);
        }

        BeaconBlockEntity.BeaconBeamSection tileentitybeacon_beaconcolortracker = blockEntity.checkingBeamSections.isEmpty() ? null : (BeaconBlockEntity.BeaconBeamSection) blockEntity.checkingBeamSections.get(blockEntity.checkingBeamSections.size() - 1);
        int l = world.getHeight(Heightmap.Types.WORLD_SURFACE, i, k);

        int i1;

        for (i1 = 0; i1 < 10 && blockposition1.getY() <= l; ++i1) {
            BlockState iblockdata1 = world.getBlockState(blockposition1);
            Block block = iblockdata1.getBlock();

            if (block instanceof BeaconBeamBlock) {
                float[] afloat = ((BeaconBeamBlock) block).getColor().getTextureDiffuseColors();

                if (blockEntity.checkingBeamSections.size() <= 1) {
                    tileentitybeacon_beaconcolortracker = new BeaconBlockEntity.BeaconBeamSection(afloat);
                    blockEntity.checkingBeamSections.add(tileentitybeacon_beaconcolortracker);
                } else if (tileentitybeacon_beaconcolortracker != null) {
                    if (Arrays.equals(afloat, tileentitybeacon_beaconcolortracker.color)) {
                        tileentitybeacon_beaconcolortracker.increaseHeight();
                    } else {
                        tileentitybeacon_beaconcolortracker = new BeaconBlockEntity.BeaconBeamSection(new float[]{(tileentitybeacon_beaconcolortracker.color[0] + afloat[0]) / 2.0F, (tileentitybeacon_beaconcolortracker.color[1] + afloat[1]) / 2.0F, (tileentitybeacon_beaconcolortracker.color[2] + afloat[2]) / 2.0F});
                        blockEntity.checkingBeamSections.add(tileentitybeacon_beaconcolortracker);
                    }
                }
            } else {
                if (tileentitybeacon_beaconcolortracker == null || iblockdata1.getLightBlock(world, blockposition1) >= 15 && !iblockdata1.is(Blocks.BEDROCK)) {
                    blockEntity.checkingBeamSections.clear();
                    blockEntity.lastCheckY = l;
                    break;
                }

                tileentitybeacon_beaconcolortracker.increaseHeight();
            }

            blockposition1 = blockposition1.above();
            ++blockEntity.lastCheckY;
        }

        i1 = blockEntity.levels;
        if (world.getRedstoneGameTime() % 80L == 0L) { // Folia - region threading
            if (!blockEntity.beamSections.isEmpty()) {
                blockEntity.levels = BeaconBlockEntity.updateBase(world, i, j, k);
            }

            if (blockEntity.levels > 0 && !blockEntity.beamSections.isEmpty()) {
                BeaconBlockEntity.applyEffects(world, pos, blockEntity.levels, blockEntity.primaryPower, blockEntity.secondaryPower, blockEntity); // Paper
                BeaconBlockEntity.playSound(world, pos, SoundEvents.BEACON_AMBIENT);
            }
        }
        // Paper start - beacon activation/deactivation events
        if (i1 <= 0 && blockEntity.levels > 0) {
            org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(world, pos);
            new io.papermc.paper.event.block.BeaconActivatedEvent(block).callEvent();
        } else if (i1 > 0 && blockEntity.levels <= 0) {
            org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(world, pos);
            new io.papermc.paper.event.block.BeaconDeactivatedEvent(block).callEvent();
        }
        // Paper end

        if (blockEntity.lastCheckY >= l) {
            blockEntity.lastCheckY = world.getMinBuildHeight() - 1;
            boolean flag = i1 > 0;

            blockEntity.beamSections = blockEntity.checkingBeamSections;
            if (!world.isClientSide) {
                boolean flag1 = blockEntity.levels > 0;

                if (!flag && flag1) {
                    BeaconBlockEntity.playSound(world, pos, SoundEvents.BEACON_ACTIVATE);
                    Iterator iterator = world.getEntitiesOfClass(ServerPlayer.class, (new AABB((double) i, (double) j, (double) k, (double) i, (double) (j - 4), (double) k)).inflate(10.0D, 5.0D, 10.0D)).iterator();

                    while (iterator.hasNext()) {
                        ServerPlayer entityplayer = (ServerPlayer) iterator.next();

                        CriteriaTriggers.CONSTRUCT_BEACON.trigger(entityplayer, blockEntity.levels);
                    }
                } else if (flag && !flag1) {
                    BeaconBlockEntity.playSound(world, pos, SoundEvents.BEACON_DEACTIVATE);
                }
            }
        }

    }

    private static int updateBase(Level world, int x, int y, int z) {
        int l = 0;

        for (int i1 = 1; i1 <= 4; l = i1++) {
            int j1 = y - i1;

            if (j1 < world.getMinBuildHeight()) {
                break;
            }

            boolean flag = true;

            for (int k1 = x - i1; k1 <= x + i1 && flag; ++k1) {
                for (int l1 = z - i1; l1 <= z + i1; ++l1) {
                    if (!world.getBlockState(new BlockPos(k1, j1, l1)).is(BlockTags.BEACON_BASE_BLOCKS)) {
                        flag = false;
                        break;
                    }
                }
            }

            if (!flag) {
                break;
            }
        }

        return l;
    }

    @Override
    public void setRemoved() {
        // Paper start - BeaconDeactivatedEvent
        org.bukkit.block.Block block = org.bukkit.craftbukkit.block.CraftBlock.at(level, worldPosition);
        new io.papermc.paper.event.block.BeaconDeactivatedEvent(block).callEvent();
        // Paper end
        // Paper start - fix MC-153086
        if (this.levels > 0 && !this.beamSections.isEmpty()) {
        BeaconBlockEntity.playSound(this.level, this.worldPosition, SoundEvents.BEACON_DEACTIVATE);
        }
        // Paper end
        super.setRemoved();
    }

    // CraftBukkit start - split into components
    private static byte getAmplification(int i, @Nullable MobEffect mobeffectlist, @Nullable MobEffect mobeffectlist1) {
        {
            byte b0 = 0;

            if (i >= 4 && mobeffectlist == mobeffectlist1) {
                b0 = 1;
            }

            return b0;
        }
    }

    private static int getLevel(int i) {
        {
            int j = (9 + i * 2) * 20;
            return j;
        }
    }

    public static List getHumansInRange(Level world, BlockPos blockposition, int i) {
        // Paper start
        return BeaconBlockEntity.getHumansInRange(world, blockposition, i, null);
    }
    public static List getHumansInRange(Level world, BlockPos blockposition, int i, @Nullable BeaconBlockEntity blockEntity) {
        // Paper end
        {
            double d0 = blockEntity != null ? blockEntity.getEffectRange() : (i * 10 + 10);// Paper - custom beacon ranges

            AABB axisalignedbb = (new AABB(blockposition)).inflate(d0).expandTowards(0.0D, (double) world.getHeight(), 0.0D);
            // Paper start - optimize player lookup for beacons
            List<Player> list;
            if (d0 <= 128.0) {
                list = world.getEntitiesOfClass(Player.class, axisalignedbb);
            } else {
                list = new java.util.ArrayList<>();
                for (Player player : world.players()) {
                    if (player.isSpectator()) {
                        continue;
                    }
                    if (player.getBoundingBox().intersects(axisalignedbb)) {
                        list.add(player);
                    }
                }
            }
            // Paper end - optimize player lookup for beacons

            return list;
        }
    }

    private static void applyEffect(List list, MobEffect effects, int i, int b0, boolean isPrimary, BlockPos worldPosition) { // Paper - BeaconEffectEvent
        if (!list.isEmpty()) { // Paper - BeaconEffectEvent
            Iterator iterator = list.iterator();

            Player entityhuman;
            // Paper start - BeaconEffectEvent
            org.bukkit.block.Block block = ((Player) list.get(0)).level().getWorld().getBlockAt(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
            PotionEffect effect = CraftPotionUtil.toBukkit(new MobEffectInstance(effects, i, b0, true, true));
            // Paper end

            while (iterator.hasNext()) {
                // Paper start - BeaconEffectEvent
                entityhuman = (ServerPlayer) iterator.next();
                BeaconEffectEvent event = new BeaconEffectEvent(block, effect, (org.bukkit.entity.Player) entityhuman.getBukkitEntity(), isPrimary);
                if (CraftEventFactory.callEvent(event).isCancelled()) continue;
                entityhuman.addEffect(new MobEffectInstance(CraftPotionUtil.fromBukkit(event.getEffect())), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.BEACON);
                // Paper end
            }
        }
    }

    private static boolean hasSecondaryEffect(int i, @Nullable MobEffect mobeffectlist, @Nullable MobEffect mobeffectlist1) {
        {
            if (i >= 4 && mobeffectlist != mobeffectlist1 && mobeffectlist1 != null) {
                return true;
            }

            return false;
        }
    }

    private static void applyEffects(Level world, BlockPos pos, int beaconLevel, @Nullable MobEffect primaryEffect, @Nullable MobEffect secondaryEffect) {
        // Paper start
        BeaconBlockEntity.applyEffects(world, pos, beaconLevel, primaryEffect, secondaryEffect, null);
    }
    private static void applyEffects(Level world, BlockPos pos, int beaconLevel, @Nullable MobEffect primaryEffect, @Nullable MobEffect secondaryEffect, @Nullable BeaconBlockEntity blockEntity) {
        // Paper end
        if (!world.isClientSide && primaryEffect != null) {
            double d0 = (double) (beaconLevel * 10 + 10);
            byte b0 = BeaconBlockEntity.getAmplification(beaconLevel, primaryEffect, secondaryEffect);

            int j = BeaconBlockEntity.getLevel(beaconLevel);
            List list = BeaconBlockEntity.getHumansInRange(world, pos, beaconLevel, blockEntity); // Paper

            BeaconBlockEntity.applyEffect(list, primaryEffect, j, b0, true, pos); // Paper - BeaconEffectEvent

            if (BeaconBlockEntity.hasSecondaryEffect(beaconLevel, primaryEffect, secondaryEffect)) {
                BeaconBlockEntity.applyEffect(list, secondaryEffect, j, 0, false, pos); // Paper - BeaconEffectEvent
            }
        }

    }
    // CraftBukkit end

    public static void playSound(Level world, BlockPos pos, SoundEvent sound) {
        world.playSound((Player) null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    public List<BeaconBlockEntity.BeaconBeamSection> getBeamSections() {
        return (List) (this.levels == 0 ? ImmutableList.of() : this.beamSections);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    private static void storeEffect(CompoundTag nbt, String key, @Nullable MobEffect effect) {
        if (effect != null) {
            ResourceLocation minecraftkey = BuiltInRegistries.MOB_EFFECT.getKey(effect);

            if (minecraftkey != null) {
                nbt.putString(key, minecraftkey.toString());
            }
        }

    }

    @Nullable
    private static MobEffect loadEffect(CompoundTag nbt, String key) {
        if (nbt.contains(key, 8)) {
            ResourceLocation minecraftkey = ResourceLocation.tryParse(nbt.getString(key));

            return (MobEffect) BuiltInRegistries.MOB_EFFECT.get(minecraftkey); // CraftBukkit - persist manually set non-default beacon effects (SPIGOT-3598)
        } else {
            return null;
        }
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        this.primaryPower = BeaconBlockEntity.loadEffect(nbt, "primary_effect");
        this.secondaryPower = BeaconBlockEntity.loadEffect(nbt, "secondary_effect");
        this.levels = nbt.getInt("Levels"); // CraftBukkit - SPIGOT-5053, use where available
        if (nbt.contains("CustomName", 8)) {
            this.name = io.papermc.paper.util.MCUtil.getBaseComponentFromNbt("CustomName", nbt); // Paper - Catch ParseException
        }

        this.lockKey = LockCode.fromTag(nbt);
        this.effectRange = nbt.contains(PAPER_RANGE_TAG, 6) ? nbt.getDouble(PAPER_RANGE_TAG) : -1; // Paper
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        BeaconBlockEntity.storeEffect(nbt, "primary_effect", this.primaryPower);
        BeaconBlockEntity.storeEffect(nbt, "secondary_effect", this.secondaryPower);
        nbt.putInt("Levels", this.levels);
        if (this.name != null) {
            nbt.putString("CustomName", Component.Serializer.toJson(this.name));
        }

        this.lockKey.addToTag(nbt);
        nbt.putDouble(PAPER_RANGE_TAG, this.effectRange); // Paper
    }

    public void setCustomName(@Nullable Component customName) {
        this.name = customName;
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return this.name;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return BaseContainerBlockEntity.canUnlock(player, this.lockKey, this.getDisplayName(), this) ? new BeaconMenu(syncId, playerInventory, this.dataAccess, ContainerLevelAccess.create(this.level, this.getBlockPos())) : null;
    }

    @Override
    public Component getDisplayName() {
        return this.getName();
    }

    @Override
    public Component getName() {
        return this.name != null ? this.name : BeaconBlockEntity.DEFAULT_NAME;
    }

    @Override
    public void setLevel(Level world) {
        super.setLevel(world);
        this.lastCheckY = world.getMinBuildHeight() - 1;
    }

    public static class BeaconBeamSection {

        final float[] color;
        private int height;

        public BeaconBeamSection(float[] color) {
            this.color = color;
            this.height = 1;
        }

        protected void increaseHeight() {
            ++this.height;
        }

        public float[] getColor() {
            return this.color;
        }

        public int getHeight() {
            return this.height;
        }
    }
}
