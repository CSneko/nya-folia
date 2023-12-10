package net.minecraft.world.item;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;

public class CompassItem extends Item implements Vanishable {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String TAG_LODESTONE_POS = "LodestonePos";
    public static final String TAG_LODESTONE_DIMENSION = "LodestoneDimension";
    public static final String TAG_LODESTONE_TRACKED = "LodestoneTracked";

    public CompassItem(Item.Properties settings) {
        super(settings);
    }

    public static boolean isLodestoneCompass(ItemStack stack) {
        CompoundTag compoundTag = stack.getTag();
        return compoundTag != null && (compoundTag.contains("LodestoneDimension") || compoundTag.contains("LodestonePos"));
    }

    private static Optional<ResourceKey<Level>> getLodestoneDimension(CompoundTag nbt) {
        return Level.RESOURCE_KEY_CODEC.parse(NbtOps.INSTANCE, nbt.get("LodestoneDimension")).result();
    }

    @Nullable
    public static GlobalPos getLodestonePosition(CompoundTag nbt) {
        boolean bl = nbt.contains("LodestonePos");
        boolean bl2 = nbt.contains("LodestoneDimension");
        if (bl && bl2) {
            Optional<ResourceKey<Level>> optional = getLodestoneDimension(nbt);
            if (optional.isPresent()) {
                BlockPos blockPos = NbtUtils.readBlockPos(nbt.getCompound("LodestonePos"));
                return GlobalPos.of(optional.get(), blockPos);
            }
        }

        return null;
    }

    @Nullable
    public static GlobalPos getSpawnPosition(Level world) {
        return world.dimensionType().natural() ? GlobalPos.of(world.dimension(), world.getSharedSpawnPos()) : null;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isLodestoneCompass(stack) || super.isFoil(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
        if (!world.isClientSide) {
            if (isLodestoneCompass(stack)) {
                CompoundTag compoundTag = stack.getOrCreateTag();
                if (compoundTag.contains("LodestoneTracked") && !compoundTag.getBoolean("LodestoneTracked")) {
                    return;
                }

                Optional<ResourceKey<Level>> optional = getLodestoneDimension(compoundTag);
                if (optional.isPresent() && optional.get() == world.dimension() && compoundTag.contains("LodestonePos")) {
                    BlockPos blockPos = NbtUtils.readBlockPos(compoundTag.getCompound("LodestonePos"));

                    // Folia start - do not access the POI data off-region
                    net.minecraft.world.level.chunk.LevelChunk chunk = world.getChunkIfLoaded(blockPos);
                    if (!world.isInWorldBounds(blockPos) || chunk != null && chunk.getBlockState(blockPos).getBlock() != Blocks.LODESTONE) { // Paper
                        // Folia end - do not access the POI data off-region
                        compoundTag.remove("LodestonePos");
                    }
                }
            }

        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos blockPos = context.getClickedPos();
        Level level = context.getLevel();
        if (!level.getBlockState(blockPos).is(Blocks.LODESTONE)) {
            return super.useOn(context);
        } else {
            level.playSound((Player)null, blockPos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
            Player player = context.getPlayer();
            ItemStack itemStack = context.getItemInHand();
            boolean bl = !player.getAbilities().instabuild && itemStack.getCount() == 1;
            if (bl) {
                this.addLodestoneTags(level.dimension(), blockPos, itemStack.getOrCreateTag());
            } else {
                ItemStack itemStack2 = new ItemStack(Items.COMPASS, 1);
                CompoundTag compoundTag = itemStack.hasTag() ? itemStack.getTag().copy() : new CompoundTag();
                itemStack2.setTag(compoundTag);
                if (!player.getAbilities().instabuild) {
                    itemStack.shrink(1);
                }

                this.addLodestoneTags(level.dimension(), blockPos, compoundTag);
                if (!player.getInventory().add(itemStack2)) {
                    player.drop(itemStack2, false);
                }
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    private void addLodestoneTags(ResourceKey<Level> worldKey, BlockPos pos, CompoundTag nbt) {
        nbt.put("LodestonePos", NbtUtils.writeBlockPos(pos));
        Level.RESOURCE_KEY_CODEC.encodeStart(NbtOps.INSTANCE, worldKey).resultOrPartial(LOGGER::error).ifPresent((tag) -> {
            nbt.put("LodestoneDimension", tag);
        });
        nbt.putBoolean("LodestoneTracked", true);
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return isLodestoneCompass(stack) ? "item.minecraft.lodestone_compass" : super.getDescriptionId(stack);
    }
}
