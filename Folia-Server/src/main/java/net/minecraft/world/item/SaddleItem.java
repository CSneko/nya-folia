package net.minecraft.world.item;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;

public class SaddleItem extends Item {
    public SaddleItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity entity, InteractionHand hand) {
        if (entity instanceof Saddleable saddleable) {
            if (entity.isAlive() && !saddleable.isSaddled() && saddleable.isSaddleable()) {
                if (!user.level().isClientSide) {
                    saddleable.equipSaddle(SoundSource.NEUTRAL, stack.copyWithCount(1)); // Paper - Fix saddles losing nbt data - MC-191591
                    entity.level().gameEvent(entity, GameEvent.EQUIP, entity.position());
                    stack.shrink(1);
                }

                return InteractionResult.sidedSuccess(user.level().isClientSide);
            }
        }

        return InteractionResult.PASS;
    }
}
