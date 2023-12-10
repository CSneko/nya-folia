package net.minecraft.world.entity.boss.enderdragon.phases;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class DragonStrafePlayerPhase extends AbstractDragonPhaseInstance {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FIREBALL_CHARGE_AMOUNT = 5;
    private int fireballCharge;
    @Nullable
    private Path currentPath;
    @Nullable
    private Vec3 targetLocation;
    @Nullable
    private LivingEntity attackTarget;
    private boolean holdingPatternClockwise;

    public DragonStrafePlayerPhase(EnderDragon dragon) {
        super(dragon);
    }

    @Override
    public void doServerTick() {
        if (this.attackTarget == null) {
            LOGGER.warn("Skipping player strafe phase because no player was found");
            this.dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
        } else {
            if (this.currentPath != null && this.currentPath.isDone()) {
                double d = this.attackTarget.getX();
                double e = this.attackTarget.getZ();
                double f = d - this.dragon.getX();
                double g = e - this.dragon.getZ();
                double h = Math.sqrt(f * f + g * g);
                double i = Math.min((double)0.4F + h / 80.0D - 1.0D, 10.0D);
                this.targetLocation = new Vec3(d, this.attackTarget.getY() + i, e);
            }

            double j = this.targetLocation == null ? 0.0D : this.targetLocation.distanceToSqr(this.dragon.getX(), this.dragon.getY(), this.dragon.getZ());
            if (j < 100.0D || j > 22500.0D) {
                this.findNewTarget();
            }

            double k = 64.0D;
            if (this.attackTarget.distanceToSqr(this.dragon) < 4096.0D) {
                if (this.dragon.hasLineOfSight(this.attackTarget)) {
                    ++this.fireballCharge;
                    Vec3 vec3 = (new Vec3(this.attackTarget.getX() - this.dragon.getX(), 0.0D, this.attackTarget.getZ() - this.dragon.getZ())).normalize();
                    Vec3 vec32 = (new Vec3((double)Mth.sin(this.dragon.getYRot() * ((float)Math.PI / 180F)), 0.0D, (double)(-Mth.cos(this.dragon.getYRot() * ((float)Math.PI / 180F))))).normalize();
                    float l = (float)vec32.dot(vec3);
                    float m = (float)(Math.acos((double)l) * (double)(180F / (float)Math.PI));
                    m += 0.5F;
                    if (this.fireballCharge >= 5 && m >= 0.0F && m < 10.0F) {
                        double n = 1.0D;
                        Vec3 vec33 = this.dragon.getViewVector(1.0F);
                        double o = this.dragon.head.getX() - vec33.x * 1.0D;
                        double p = this.dragon.head.getY(0.5D) + 0.5D;
                        double q = this.dragon.head.getZ() - vec33.z * 1.0D;
                        double r = this.attackTarget.getX() - o;
                        double s = this.attackTarget.getY(0.5D) - p;
                        double t = this.attackTarget.getZ() - q;
                        if (!this.dragon.isSilent()) {
                            this.dragon.level().levelEvent((Player)null, 1017, this.dragon.blockPosition(), 0);
                        }

                        DragonFireball dragonFireball = new DragonFireball(this.dragon.level(), this.dragon, r, s, t);
                        dragonFireball.moveTo(o, p, q, 0.0F, 0.0F);
                        if (new com.destroystokyo.paper.event.entity.EnderDragonShootFireballEvent((org.bukkit.entity.EnderDragon) dragon.getBukkitEntity(), (org.bukkit.entity.DragonFireball) dragonFireball.getBukkitEntity()).callEvent()) // Paper
                        this.dragon.level().addFreshEntity(dragonFireball);
                        else dragonFireball.discard(); // Paper
                        this.fireballCharge = 0;
                        if (this.currentPath != null) {
                            while(!this.currentPath.isDone()) {
                                this.currentPath.advance();
                            }
                        }

                        this.dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
                    }
                } else if (this.fireballCharge > 0) {
                    --this.fireballCharge;
                }
            } else if (this.fireballCharge > 0) {
                --this.fireballCharge;
            }

        }
    }

    private void findNewTarget() {
        if (this.currentPath == null || this.currentPath.isDone()) {
            int i = this.dragon.findClosestNode();
            int j = i;
            if (this.dragon.getRandom().nextInt(8) == 0) {
                this.holdingPatternClockwise = !this.holdingPatternClockwise;
                j = i + 6;
            }

            if (this.holdingPatternClockwise) {
                ++j;
            } else {
                --j;
            }

            if (this.dragon.getDragonFight() != null && this.dragon.getDragonFight().getCrystalsAlive() > 0) {
                j %= 12;
                if (j < 0) {
                    j += 12;
                }
            } else {
                j -= 12;
                j &= 7;
                j += 12;
            }

            this.currentPath = this.dragon.findPath(i, j, (Node)null);
            if (this.currentPath != null) {
                this.currentPath.advance();
            }
        }

        this.navigateToNextPathNode();
    }

    private void navigateToNextPathNode() {
        if (this.currentPath != null && !this.currentPath.isDone()) {
            Vec3i vec3i = this.currentPath.getNextNodePos();
            this.currentPath.advance();
            double d = (double)vec3i.getX();
            double e = (double)vec3i.getZ();

            double f;
            do {
                f = (double)((float)vec3i.getY() + this.dragon.getRandom().nextFloat() * 20.0F);
            } while(f < (double)vec3i.getY());

            this.targetLocation = new Vec3(d, f, e);
        }

    }

    @Override
    public void begin() {
        this.fireballCharge = 0;
        this.targetLocation = null;
        this.currentPath = null;
        this.attackTarget = null;
    }

    public void setTarget(LivingEntity targetEntity) {
        this.attackTarget = targetEntity;
        int i = this.dragon.findClosestNode();
        int j = this.dragon.findClosestNode(this.attackTarget.getX(), this.attackTarget.getY(), this.attackTarget.getZ());
        int k = this.attackTarget.getBlockX();
        int l = this.attackTarget.getBlockZ();
        double d = (double)k - this.dragon.getX();
        double e = (double)l - this.dragon.getZ();
        double f = Math.sqrt(d * d + e * e);
        double g = Math.min((double)0.4F + f / 80.0D - 1.0D, 10.0D);
        int m = Mth.floor(this.attackTarget.getY() + g);
        Node node = new Node(k, m, l);
        this.currentPath = this.dragon.findPath(i, j, node);
        if (this.currentPath != null) {
            this.currentPath.advance();
            this.navigateToNextPathNode();
        }

    }

    @Nullable
    @Override
    public Vec3 getFlyTargetLocation() {
        return this.targetLocation;
    }

    @Override
    public EnderDragonPhase<DragonStrafePlayerPhase> getPhase() {
        return EnderDragonPhase.STRAFE_PLAYER;
    }
}
