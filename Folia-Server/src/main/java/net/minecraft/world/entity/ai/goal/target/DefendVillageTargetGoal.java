package net.minecraft.world.entity.ai.goal.target;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public class DefendVillageTargetGoal extends TargetGoal {

    private final IronGolem golem;
    @Nullable
    private LivingEntity potentialTarget;
    private final TargetingConditions attackTargeting = TargetingConditions.forCombat().range(64.0D);

    public DefendVillageTargetGoal(IronGolem golem) {
        super(golem, false, true);
        this.golem = golem;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        AABB axisalignedbb = this.golem.getBoundingBox().inflate(10.0D, 8.0D, 10.0D);
        List<? extends LivingEntity> list = this.golem.level().getNearbyEntities(Villager.class, this.attackTargeting, this.golem, axisalignedbb);
        List<Player> list1 = this.golem.level().getNearbyPlayers(this.attackTargeting, this.golem, axisalignedbb);
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            LivingEntity entityliving = (LivingEntity) iterator.next();
            Villager entityvillager = (Villager) entityliving;
            Iterator iterator1 = list1.iterator();

            while (iterator1.hasNext()) {
                Player entityhuman = (Player) iterator1.next();
                int i = entityvillager.getPlayerReputation(entityhuman);

                if (i <= -100) {
                    this.potentialTarget = entityhuman;
                }
            }
        }

        if (this.potentialTarget == null) {
            return false;
        } else if (this.potentialTarget instanceof Player && (this.potentialTarget.isSpectator() || ((Player) this.potentialTarget).isCreative())) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void start() {
        this.golem.setTarget(this.potentialTarget, org.bukkit.event.entity.EntityTargetEvent.TargetReason.DEFEND_VILLAGE, true); // CraftBukkit - reason
        super.start();
    }
}
