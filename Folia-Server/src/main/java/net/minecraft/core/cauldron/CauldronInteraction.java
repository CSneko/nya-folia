package net.minecraft.core.cauldron;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

// CraftBukkit start
import org.bukkit.event.block.CauldronLevelChangeEvent;
// CraftBukkit end

public interface CauldronInteraction {

    Map<Item, CauldronInteraction> EMPTY = CauldronInteraction.newInteractionMap();
    Map<Item, CauldronInteraction> WATER = CauldronInteraction.newInteractionMap();
    Map<Item, CauldronInteraction> LAVA = CauldronInteraction.newInteractionMap();
    Map<Item, CauldronInteraction> POWDER_SNOW = CauldronInteraction.newInteractionMap();
    CauldronInteraction FILL_WATER = (iblockdata, world, blockposition, entityhuman, enumhand, itemstack) -> {
        return CauldronInteraction.emptyBucket(world, blockposition, entityhuman, enumhand, itemstack, (BlockState) Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), SoundEvents.BUCKET_EMPTY);
    };
    CauldronInteraction FILL_LAVA = (iblockdata, world, blockposition, entityhuman, enumhand, itemstack) -> {
        return CauldronInteraction.emptyBucket(world, blockposition, entityhuman, enumhand, itemstack, Blocks.LAVA_CAULDRON.defaultBlockState(), SoundEvents.BUCKET_EMPTY_LAVA);
    };
    CauldronInteraction FILL_POWDER_SNOW = (iblockdata, world, blockposition, entityhuman, enumhand, itemstack) -> {
        return CauldronInteraction.emptyBucket(world, blockposition, entityhuman, enumhand, itemstack, (BlockState) Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), SoundEvents.BUCKET_EMPTY_POWDER_SNOW);
    };
    CauldronInteraction SHULKER_BOX = (iblockdata, world, blockposition, entityhuman, enumhand, itemstack) -> {
        Block block = Block.byItem(itemstack.getItem());

        if (!(block instanceof ShulkerBoxBlock)) {
            return InteractionResult.PASS;
        } else {
            if (!world.isClientSide) {
                // CraftBukkit start
                if (!LayeredCauldronBlock.lowerFillLevel(iblockdata, world, blockposition, entityhuman, CauldronLevelChangeEvent.ChangeReason.SHULKER_WASH)) {
                    return InteractionResult.SUCCESS;
                }
                // CraftBukkit end
                ItemStack itemstack1 = new ItemStack(Blocks.SHULKER_BOX);

                if (itemstack.hasTag()) {
                    itemstack1.setTag(itemstack.getTag().copy());
                }

                entityhuman.setItemInHand(enumhand, itemstack1);
                entityhuman.awardStat(Stats.CLEAN_SHULKER_BOX);
                // LayeredCauldronBlock.lowerFillLevel(iblockdata, world, blockposition); // CraftBukkit
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    };
    CauldronInteraction BANNER = (iblockdata, world, blockposition, entityhuman, enumhand, itemstack) -> {
        if (BannerBlockEntity.getPatternCount(itemstack) <= 0) {
            return InteractionResult.PASS;
        } else {
            if (!world.isClientSide) {
                // CraftBukkit start
                if (!LayeredCauldronBlock.lowerFillLevel(iblockdata, world, blockposition, entityhuman, CauldronLevelChangeEvent.ChangeReason.BANNER_WASH)) {
                    return InteractionResult.SUCCESS;
                }
                // CraftBukkit end
                ItemStack itemstack1 = itemstack.copyWithCount(1);

                BannerBlockEntity.removeLastPattern(itemstack1);
                if (!entityhuman.getAbilities().instabuild) {
                    itemstack.shrink(1);
                }

                if (itemstack.isEmpty()) {
                    entityhuman.setItemInHand(enumhand, itemstack1);
                } else if (entityhuman.getInventory().add(itemstack1)) {
                    entityhuman.inventoryMenu.sendAllDataToRemote();
                } else {
                    entityhuman.drop(itemstack1, false);
                }

                entityhuman.awardStat(Stats.CLEAN_BANNER);
                // LayeredCauldronBlock.lowerFillLevel(iblockdata, world, blockposition); // CraftBukkit
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    };
    CauldronInteraction DYED_ITEM = (iblockdata, world, blockposition, entityhuman, enumhand, itemstack) -> {
        Item item = itemstack.getItem();

        if (!(item instanceof DyeableLeatherItem)) {
            return InteractionResult.PASS;
        } else {
            DyeableLeatherItem idyeable = (DyeableLeatherItem) item;

            if (!idyeable.hasCustomColor(itemstack)) {
                return InteractionResult.PASS;
            } else {
                if (!world.isClientSide) {
                    // CraftBukkit start
                    if (!LayeredCauldronBlock.lowerFillLevel(iblockdata, world, blockposition, entityhuman, CauldronLevelChangeEvent.ChangeReason.ARMOR_WASH)) {
                        return InteractionResult.SUCCESS;
                    }
                    // CraftBukkit end
                    idyeable.clearColor(itemstack);
                    entityhuman.awardStat(Stats.CLEAN_ARMOR);
                    // LayeredCauldronBlock.lowerFillLevel(iblockdata, world, blockposition); // CraftBukkit
                }

                return InteractionResult.sidedSuccess(world.isClientSide);
            }
        }
    };

    static Object2ObjectOpenHashMap<Item, CauldronInteraction> newInteractionMap() {
        return Util.make(new Object2ObjectOpenHashMap<>(), (object2objectopenhashmap) -> { // CraftBukkit - decompile error
            object2objectopenhashmap.defaultReturnValue((iblockdata, world, blockposition, entityhuman, enumhand, itemstack) -> {
                return InteractionResult.PASS;
            });
        });
    }

    InteractionResult interact(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, ItemStack stack);

    static void bootStrap() {
        CauldronInteraction.addDefaultInteractions(CauldronInteraction.EMPTY);
        CauldronInteraction.EMPTY.put(Items.POTION, (iblockdata, world, blockposition, entityhuman, enumhand, itemstack) -> {
            if (PotionUtils.getPotion(itemstack) != Potions.WATER) {
                return InteractionResult.PASS;
            } else {
                if (!world.isClientSide) {
                    // CraftBukkit start
                    if (!LayeredCauldronBlock.changeLevel(iblockdata, world, blockposition, Blocks.WATER_CAULDRON.defaultBlockState(), entityhuman, CauldronLevelChangeEvent.ChangeReason.BOTTLE_EMPTY)) {
                        return InteractionResult.SUCCESS;
                    }
                    // CraftBukkit end
                    Item item = itemstack.getItem();

                    entityhuman.setItemInHand(enumhand, ItemUtils.createFilledResult(itemstack, entityhuman, new ItemStack(Items.GLASS_BOTTLE)));
                    entityhuman.awardStat(Stats.USE_CAULDRON);
                    entityhuman.awardStat(Stats.ITEM_USED.get(item));
                    // world.setBlockAndUpdate(blockposition, Blocks.WATER_CAULDRON.defaultBlockState()); // CraftBukkit
                    world.playSound((Player) null, blockposition, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                    world.gameEvent((Entity) null, GameEvent.FLUID_PLACE, blockposition);
                }

                return InteractionResult.sidedSuccess(world.isClientSide);
            }
        });
        CauldronInteraction.addDefaultInteractions(CauldronInteraction.WATER);
        CauldronInteraction.WATER.put(Items.BUCKET, (iblockdata, world, blockposition, entityhuman, enumhand, itemstack) -> {
            return CauldronInteraction.fillBucket(iblockdata, world, blockposition, entityhuman, enumhand, itemstack, new ItemStack(Items.WATER_BUCKET), (iblockdata1) -> {
                return (Integer) iblockdata1.getValue(LayeredCauldronBlock.LEVEL) == 3;
            }, SoundEvents.BUCKET_FILL);
        });
        CauldronInteraction.WATER.put(Items.GLASS_BOTTLE, (iblockdata, world, blockposition, entityhuman, enumhand, itemstack) -> {
            if (!world.isClientSide) {
                // CraftBukkit start
                if (!LayeredCauldronBlock.lowerFillLevel(iblockdata, world, blockposition, entityhuman, CauldronLevelChangeEvent.ChangeReason.BOTTLE_FILL)) {
                    return InteractionResult.SUCCESS;
                }
                // CraftBukkit end
                Item item = itemstack.getItem();

                entityhuman.setItemInHand(enumhand, ItemUtils.createFilledResult(itemstack, entityhuman, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER)));
                entityhuman.awardStat(Stats.USE_CAULDRON);
                entityhuman.awardStat(Stats.ITEM_USED.get(item));
                // LayeredCauldronBlock.lowerFillLevel(iblockdata, world, blockposition); // CraftBukkit
                world.playSound((Player) null, blockposition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                world.gameEvent((Entity) null, GameEvent.FLUID_PICKUP, blockposition);
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        });
        CauldronInteraction.WATER.put(Items.POTION, (iblockdata, world, blockposition, entityhuman, enumhand, itemstack) -> {
            if ((Integer) iblockdata.getValue(LayeredCauldronBlock.LEVEL) != 3 && PotionUtils.getPotion(itemstack) == Potions.WATER) {
                if (!world.isClientSide) {
                    // CraftBukkit start
                    if (!LayeredCauldronBlock.changeLevel(iblockdata, world, blockposition, iblockdata.cycle(LayeredCauldronBlock.LEVEL), entityhuman, CauldronLevelChangeEvent.ChangeReason.BOTTLE_EMPTY)) {
                        return InteractionResult.SUCCESS;
                    }
                    // CraftBukkit end
                    entityhuman.setItemInHand(enumhand, ItemUtils.createFilledResult(itemstack, entityhuman, new ItemStack(Items.GLASS_BOTTLE)));
                    entityhuman.awardStat(Stats.USE_CAULDRON);
                    entityhuman.awardStat(Stats.ITEM_USED.get(itemstack.getItem()));
                    // world.setBlockAndUpdate(blockposition, (IBlockData) iblockdata.cycle(LayeredCauldronBlock.LEVEL)); // CraftBukkit
                    world.playSound((Player) null, blockposition, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                    world.gameEvent((Entity) null, GameEvent.FLUID_PLACE, blockposition);
                }

                return InteractionResult.sidedSuccess(world.isClientSide);
            } else {
                return InteractionResult.PASS;
            }
        });
        CauldronInteraction.WATER.put(Items.LEATHER_BOOTS, CauldronInteraction.DYED_ITEM);
        CauldronInteraction.WATER.put(Items.LEATHER_LEGGINGS, CauldronInteraction.DYED_ITEM);
        CauldronInteraction.WATER.put(Items.LEATHER_CHESTPLATE, CauldronInteraction.DYED_ITEM);
        CauldronInteraction.WATER.put(Items.LEATHER_HELMET, CauldronInteraction.DYED_ITEM);
        CauldronInteraction.WATER.put(Items.LEATHER_HORSE_ARMOR, CauldronInteraction.DYED_ITEM);
        CauldronInteraction.WATER.put(Items.WHITE_BANNER, CauldronInteraction.BANNER);
        CauldronInteraction.WATER.put(Items.GRAY_BANNER, CauldronInteraction.BANNER);
        CauldronInteraction.WATER.put(Items.BLACK_BANNER, CauldronInteraction.BANNER);
        CauldronInteraction.WATER.put(Items.BLUE_BANNER, CauldronInteraction.BANNER);
        CauldronInteraction.WATER.put(Items.BROWN_BANNER, CauldronInteraction.BANNER);
        CauldronInteraction.WATER.put(Items.CYAN_BANNER, CauldronInteraction.BANNER);
        CauldronInteraction.WATER.put(Items.GREEN_BANNER, CauldronInteraction.BANNER);
        CauldronInteraction.WATER.put(Items.LIGHT_BLUE_BANNER, CauldronInteraction.BANNER);
        CauldronInteraction.WATER.put(Items.LIGHT_GRAY_BANNER, CauldronInteraction.BANNER);
        CauldronInteraction.WATER.put(Items.LIME_BANNER, CauldronInteraction.BANNER);
        CauldronInteraction.WATER.put(Items.MAGENTA_BANNER, CauldronInteraction.BANNER);
        CauldronInteraction.WATER.put(Items.ORANGE_BANNER, CauldronInteraction.BANNER);
        CauldronInteraction.WATER.put(Items.PINK_BANNER, CauldronInteraction.BANNER);
        CauldronInteraction.WATER.put(Items.PURPLE_BANNER, CauldronInteraction.BANNER);
        CauldronInteraction.WATER.put(Items.RED_BANNER, CauldronInteraction.BANNER);
        CauldronInteraction.WATER.put(Items.YELLOW_BANNER, CauldronInteraction.BANNER);
        CauldronInteraction.WATER.put(Items.WHITE_SHULKER_BOX, CauldronInteraction.SHULKER_BOX);
        CauldronInteraction.WATER.put(Items.GRAY_SHULKER_BOX, CauldronInteraction.SHULKER_BOX);
        CauldronInteraction.WATER.put(Items.BLACK_SHULKER_BOX, CauldronInteraction.SHULKER_BOX);
        CauldronInteraction.WATER.put(Items.BLUE_SHULKER_BOX, CauldronInteraction.SHULKER_BOX);
        CauldronInteraction.WATER.put(Items.BROWN_SHULKER_BOX, CauldronInteraction.SHULKER_BOX);
        CauldronInteraction.WATER.put(Items.CYAN_SHULKER_BOX, CauldronInteraction.SHULKER_BOX);
        CauldronInteraction.WATER.put(Items.GREEN_SHULKER_BOX, CauldronInteraction.SHULKER_BOX);
        CauldronInteraction.WATER.put(Items.LIGHT_BLUE_SHULKER_BOX, CauldronInteraction.SHULKER_BOX);
        CauldronInteraction.WATER.put(Items.LIGHT_GRAY_SHULKER_BOX, CauldronInteraction.SHULKER_BOX);
        CauldronInteraction.WATER.put(Items.LIME_SHULKER_BOX, CauldronInteraction.SHULKER_BOX);
        CauldronInteraction.WATER.put(Items.MAGENTA_SHULKER_BOX, CauldronInteraction.SHULKER_BOX);
        CauldronInteraction.WATER.put(Items.ORANGE_SHULKER_BOX, CauldronInteraction.SHULKER_BOX);
        CauldronInteraction.WATER.put(Items.PINK_SHULKER_BOX, CauldronInteraction.SHULKER_BOX);
        CauldronInteraction.WATER.put(Items.PURPLE_SHULKER_BOX, CauldronInteraction.SHULKER_BOX);
        CauldronInteraction.WATER.put(Items.RED_SHULKER_BOX, CauldronInteraction.SHULKER_BOX);
        CauldronInteraction.WATER.put(Items.YELLOW_SHULKER_BOX, CauldronInteraction.SHULKER_BOX);
        CauldronInteraction.LAVA.put(Items.BUCKET, (iblockdata, world, blockposition, entityhuman, enumhand, itemstack) -> {
            return CauldronInteraction.fillBucket(iblockdata, world, blockposition, entityhuman, enumhand, itemstack, new ItemStack(Items.LAVA_BUCKET), (iblockdata1) -> {
                return true;
            }, SoundEvents.BUCKET_FILL_LAVA);
        });
        CauldronInteraction.addDefaultInteractions(CauldronInteraction.LAVA);
        CauldronInteraction.POWDER_SNOW.put(Items.BUCKET, (iblockdata, world, blockposition, entityhuman, enumhand, itemstack) -> {
            return CauldronInteraction.fillBucket(iblockdata, world, blockposition, entityhuman, enumhand, itemstack, new ItemStack(Items.POWDER_SNOW_BUCKET), (iblockdata1) -> {
                return (Integer) iblockdata1.getValue(LayeredCauldronBlock.LEVEL) == 3;
            }, SoundEvents.BUCKET_FILL_POWDER_SNOW);
        });
        CauldronInteraction.addDefaultInteractions(CauldronInteraction.POWDER_SNOW);
    }

    static void addDefaultInteractions(Map<Item, CauldronInteraction> behavior) {
        behavior.put(Items.LAVA_BUCKET, CauldronInteraction.FILL_LAVA);
        behavior.put(Items.WATER_BUCKET, CauldronInteraction.FILL_WATER);
        behavior.put(Items.POWDER_SNOW_BUCKET, CauldronInteraction.FILL_POWDER_SNOW);
    }

    static InteractionResult fillBucket(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, ItemStack stack, ItemStack output, Predicate<BlockState> fullPredicate, SoundEvent soundEvent) {
        if (!fullPredicate.test(state)) {
            return InteractionResult.PASS;
        } else {
            if (!world.isClientSide) {
                // CraftBukkit start
                if (!LayeredCauldronBlock.changeLevel(state, world, pos, Blocks.CAULDRON.defaultBlockState(), player, CauldronLevelChangeEvent.ChangeReason.BUCKET_FILL)) {
                    return InteractionResult.SUCCESS;
                }
                // CraftBukkit end
                Item item = stack.getItem();

                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, output));
                player.awardStat(Stats.USE_CAULDRON);
                player.awardStat(Stats.ITEM_USED.get(item));
                // world.setBlockAndUpdate(blockposition, Blocks.CAULDRON.defaultBlockState()); // CraftBukkit
                world.playSound((Player) null, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
                world.gameEvent((Entity) null, GameEvent.FLUID_PICKUP, pos);
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        }
    }

    static InteractionResult emptyBucket(Level world, BlockPos pos, Player player, InteractionHand hand, ItemStack stack, BlockState state, SoundEvent soundEvent) {
        if (!world.isClientSide) {
            // CraftBukkit start
            if (!LayeredCauldronBlock.changeLevel(state, world, pos, state, player, CauldronLevelChangeEvent.ChangeReason.BUCKET_EMPTY)) {
                return InteractionResult.SUCCESS;
            }
            // CraftBukkit end
            Item item = stack.getItem();

            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
            player.awardStat(Stats.FILL_CAULDRON);
            player.awardStat(Stats.ITEM_USED.get(item));
            // world.setBlockAndUpdate(blockposition, iblockdata); // CraftBukkit
            world.playSound((Player) null, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
            world.gameEvent((Entity) null, GameEvent.FLUID_PLACE, pos);
        }

        return InteractionResult.sidedSuccess(world.isClientSide);
    }
}
