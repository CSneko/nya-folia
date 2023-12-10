package net.minecraft.world.item;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.bukkit.event.entity.SheepDyeWoolEvent; // CraftBukkit

public class DyeItem extends Item implements SignApplicator {

    private static final Map<DyeColor, DyeItem> ITEM_BY_COLOR = Maps.newEnumMap(DyeColor.class);
    private final DyeColor dyeColor;

    public DyeItem(DyeColor color, Item.Properties settings) {
        super(settings);
        this.dyeColor = color;
        DyeItem.ITEM_BY_COLOR.put(color, this);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity entity, InteractionHand hand) {
        if (entity instanceof Sheep) {
            Sheep entitysheep = (Sheep) entity;

            if (entitysheep.isAlive() && !entitysheep.isSheared() && entitysheep.getColor() != this.dyeColor) {
                entitysheep.level().playSound(user, (Entity) entitysheep, SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
                if (!user.level().isClientSide) {
                    // CraftBukkit start
                    byte bColor = (byte) this.dyeColor.getId();
                    SheepDyeWoolEvent event = new SheepDyeWoolEvent((org.bukkit.entity.Sheep) entitysheep.getBukkitEntity(), org.bukkit.DyeColor.getByWoolData(bColor), (org.bukkit.entity.Player) user.getBukkitEntity());
                    entitysheep.level().getCraftServer().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return InteractionResult.PASS;
                    }

                    entitysheep.setColor(DyeColor.byId((byte) event.getColor().getWoolData()));
                    // CraftBukkit end
                    stack.shrink(1);
                }

                return InteractionResult.sidedSuccess(user.level().isClientSide);
            }
        }

        return InteractionResult.PASS;
    }

    public DyeColor getDyeColor() {
        return this.dyeColor;
    }

    public static DyeItem byColor(DyeColor color) {
        return (DyeItem) DyeItem.ITEM_BY_COLOR.get(color);
    }

    @Override
    public boolean tryApplyToSign(Level world, SignBlockEntity signBlockEntity, boolean front, Player player) {
        if (signBlockEntity.updateText((signtext) -> {
            return signtext.setColor(this.getDyeColor());
        }, front)) {
            world.playSound((Player) null, signBlockEntity.getBlockPos(), SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        } else {
            return false;
        }
    }
}
