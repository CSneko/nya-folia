package net.minecraft.world.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SuspiciousEffectHolder;

public class SuspiciousStewItem extends Item {
    public static final String EFFECTS_TAG = "effects";
    public static final int DEFAULT_DURATION = 160;

    public SuspiciousStewItem(Item.Properties settings) {
        super(settings);
    }

    public static void saveMobEffects(ItemStack stew, List<SuspiciousEffectHolder.EffectEntry> stewEffects) {
        CompoundTag compoundTag = stew.getOrCreateTag();
        SuspiciousEffectHolder.EffectEntry.LIST_CODEC.encodeStart(NbtOps.INSTANCE, stewEffects).result().ifPresent((tag) -> {
            compoundTag.put("effects", tag);
        });
    }

    public static void appendMobEffects(ItemStack stew, List<SuspiciousEffectHolder.EffectEntry> stewEffects) {
        CompoundTag compoundTag = stew.getOrCreateTag();
        List<SuspiciousEffectHolder.EffectEntry> list = new ArrayList<>();
        listPotionEffects(stew, list::add);
        list.addAll(stewEffects);
        SuspiciousEffectHolder.EffectEntry.LIST_CODEC.encodeStart(NbtOps.INSTANCE, list).result().ifPresent((tag) -> {
            compoundTag.put("effects", tag);
        });
    }

    private static void listPotionEffects(ItemStack stew, Consumer<SuspiciousEffectHolder.EffectEntry> effectConsumer) {
        CompoundTag compoundTag = stew.getTag();
        if (compoundTag != null && compoundTag.contains("effects", 9)) {
            SuspiciousEffectHolder.EffectEntry.LIST_CODEC.parse(NbtOps.INSTANCE, compoundTag.getList("effects", 10)).result().ifPresent((list) -> {
                list.forEach(effectConsumer);
            });
        }

    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag context) {
        super.appendHoverText(stack, world, tooltip, context);
        if (context.isCreative()) {
            List<MobEffectInstance> list = new ArrayList<>();
            listPotionEffects(stack, (effect) -> {
                list.add(effect.createEffectInstance());
            });
            PotionUtils.addPotionTooltip(list, tooltip, 1.0F);
        }

    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        ItemStack itemStack = super.finishUsingItem(stack, world, user);
        listPotionEffects(itemStack, (effect) -> {
            user.addEffect(effect.createEffectInstance(), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.FOOD); // Paper
        });
        return user instanceof Player && ((Player)user).getAbilities().instabuild ? itemStack : new ItemStack(Items.BOWL);
    }
}
