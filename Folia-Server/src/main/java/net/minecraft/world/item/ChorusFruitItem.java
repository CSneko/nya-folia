package net.minecraft.world.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class ChorusFruitItem extends Item {

    public ChorusFruitItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        ItemStack itemstack1 = super.finishUsingItem(stack, world, user);

        if (!world.isClientSide) {
            double d0 = user.getX();
            double d1 = user.getY();
            double d2 = user.getZ();

            for (int i = 0; i < 16; ++i) {
                double d3 = user.getX() + (user.getRandom().nextDouble() - 0.5D) * 16.0D;
                double d4 = Mth.clamp(user.getY() + (double) (user.getRandom().nextInt(16) - 8), (double) world.getMinBuildHeight(), (double) (world.getMinBuildHeight() + ((ServerLevel) world).getLogicalHeight() - 1));
                double d5 = user.getZ() + (user.getRandom().nextDouble() - 0.5D) * 16.0D;

                if (user.isPassenger()) {
                    user.stopRiding();
                }

                Vec3 vec3d = user.position();

                // CraftBukkit start - handle canceled status of teleport event
                java.util.Optional<Boolean> status = user.randomTeleport(d3, d4, d5, true, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT);

                if (!status.isPresent()) {
                    // teleport event was canceled, no more tries
                    break;
                }

                if (status.get()) {
                    // CraftBukkit end
                    world.gameEvent(GameEvent.TELEPORT, vec3d, GameEvent.Context.of((Entity) user));
                    SoundEvent soundeffect = user instanceof Fox ? SoundEvents.FOX_TELEPORT : SoundEvents.CHORUS_FRUIT_TELEPORT;

                    world.playSound((Player) null, d0, d1, d2, soundeffect, SoundSource.PLAYERS, 1.0F, 1.0F);
                    user.playSound(soundeffect, 1.0F, 1.0F);
                    user.resetFallDistance();
                    break;
                }
            }

            if (user instanceof Player) {
                ((Player) user).getCooldowns().addCooldown(this, 20);
            }
        }

        return itemstack1;
    }
}
