package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.state.BlockState;

public class BannerBlockEntity extends BlockEntity implements Nameable {

    public static final int MAX_PATTERNS = 6;
    public static final String TAG_PATTERNS = "Patterns";
    public static final String TAG_PATTERN = "Pattern";
    public static final String TAG_COLOR = "Color";
    @Nullable
    private Component name;
    public DyeColor baseColor;
    @Nullable
    public ListTag itemPatterns;
    @Nullable
    private List<Pair<Holder<BannerPattern>, DyeColor>> patterns;

    public BannerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.BANNER, pos, state);
        this.baseColor = ((AbstractBannerBlock) state.getBlock()).getColor();
    }

    public BannerBlockEntity(BlockPos pos, BlockState state, DyeColor baseColor) {
        this(pos, state);
        this.baseColor = baseColor;
    }

    @Nullable
    public static ListTag getItemPatterns(ItemStack stack) {
        ListTag nbttaglist = null;
        CompoundTag nbttagcompound = BlockItem.getBlockEntityData(stack);

        if (nbttagcompound != null && nbttagcompound.contains("Patterns", 9)) {
            nbttaglist = nbttagcompound.getList("Patterns", 10).copy();
        }

        return nbttaglist;
    }

    public void fromItem(ItemStack stack, DyeColor baseColor) {
        this.baseColor = baseColor;
        this.fromItem(stack);
    }

    public void fromItem(ItemStack stack) {
        this.itemPatterns = BannerBlockEntity.getItemPatterns(stack);
        this.patterns = null;
        this.name = stack.hasCustomHoverName() ? stack.getHoverName() : null;
    }

    @Override
    public Component getName() {
        return (Component) (this.name != null ? this.name : Component.translatable("block.minecraft.banner"));
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return this.name;
    }

    public void setCustomName(Component customName) {
        this.name = customName;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        if (this.itemPatterns != null) {
            nbt.put("Patterns", this.itemPatterns);
        }

        if (this.name != null) {
            nbt.putString("CustomName", Component.Serializer.toJson(this.name));
        }

    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        if (nbt.contains("CustomName", 8)) {
            this.name = io.papermc.paper.util.MCUtil.getBaseComponentFromNbt("CustomName", nbt); // Paper - Catch ParseException
        }

        this.itemPatterns = nbt.getList("Patterns", 10);
        // CraftBukkit start
        while (this.itemPatterns.size() > 20) {
            this.itemPatterns.remove(20);
        }
        // CraftBukkit end
        this.patterns = null;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    public static int getPatternCount(ItemStack stack) {
        CompoundTag nbttagcompound = BlockItem.getBlockEntityData(stack);

        return nbttagcompound != null && nbttagcompound.contains("Patterns") ? nbttagcompound.getList("Patterns", 10).size() : 0;
    }

    public List<Pair<Holder<BannerPattern>, DyeColor>> getPatterns() {
        if (this.patterns == null) {
            this.patterns = BannerBlockEntity.createPatterns(this.baseColor, this.itemPatterns);
        }

        return this.patterns;
    }

    public static List<Pair<Holder<BannerPattern>, DyeColor>> createPatterns(DyeColor baseColor, @Nullable ListTag patternListNbt) {
        List<Pair<Holder<BannerPattern>, DyeColor>> list = Lists.newArrayList();

        list.add(Pair.of(BuiltInRegistries.BANNER_PATTERN.getHolderOrThrow(BannerPatterns.BASE), baseColor));
        if (patternListNbt != null) {
            for (int i = 0; i < patternListNbt.size(); ++i) {
                CompoundTag nbttagcompound = patternListNbt.getCompound(i);
                Holder<BannerPattern> holder = BannerPattern.byHash(nbttagcompound.getString("Pattern"));

                if (holder != null) {
                    int j = nbttagcompound.getInt("Color");

                    list.add(Pair.of(holder, DyeColor.byId(j)));
                }
            }
        }

        return list;
    }

    public static void removeLastPattern(ItemStack stack) {
        CompoundTag nbttagcompound = BlockItem.getBlockEntityData(stack);

        if (nbttagcompound != null && nbttagcompound.contains("Patterns", 9)) {
            ListTag nbttaglist = nbttagcompound.getList("Patterns", 10);

            if (!nbttaglist.isEmpty()) {
                nbttaglist.remove(nbttaglist.size() - 1);
                if (nbttaglist.isEmpty()) {
                    nbttagcompound.remove("Patterns");
                }
            }

            nbttagcompound.remove("id");
            BlockItem.setBlockEntityData(stack, BlockEntityType.BANNER, nbttagcompound);
        }
    }

    public ItemStack getItem() {
        ItemStack itemstack = new ItemStack(BannerBlock.byColor(this.baseColor));

        if (this.itemPatterns != null && !this.itemPatterns.isEmpty()) {
            CompoundTag nbttagcompound = new CompoundTag();

            nbttagcompound.put("Patterns", this.itemPatterns.copy());
            BlockItem.setBlockEntityData(itemstack, this.getType(), nbttagcompound);
        }

        if (this.name != null) {
            itemstack.setHoverName(this.name);
        }

        return itemstack;
    }

    public DyeColor getBaseColor() {
        return this.baseColor;
    }
}
