package net.minecraft.world.entity.boss.enderdragon.phases;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;

public class DragonSittingFlamingPhase extends AbstractDragonSittingPhase {
    private static final int FLAME_DURATION = 200;
    private static final int SITTING_FLAME_ATTACKS_COUNT = 4;
    private static final int WARMUP_TIME = 10;
    private int flameTicks;
    private int flameCount;
    @Nullable
    private AreaEffectCloud flame;

    public DragonSittingFlamingPhase(EnderDragon dragon) {
        super(dragon);
    }

    @Override
    public void doClientTick() {
        ++this.flameTicks;
        if (this.flameTicks % 2 == 0 && this.flameTicks < 10) {
            Vec3 vec3 = this.dragon.getHeadLookVector(1.0F).normalize();
            vec3.yRot((-(float)Math.PI / 4F));
            double d = this.dragon.head.getX();
            double e = this.dragon.head.getY(0.5D);
            double f = this.dragon.head.getZ();

            for(int i = 0; i < 8; ++i) {
                double g = d + this.dragon.getRandom().nextGaussian() / 2.0D;
                double h = e + this.dragon.getRandom().nextGaussian() / 2.0D;
                double j = f + this.dragon.getRandom().nextGaussian() / 2.0D;

                for(int k = 0; k < 6; ++k) {
                    this.dragon.level().addParticle(ParticleTypes.DRAGON_BREATH, g, h, j, -vec3.x * (double)0.08F * (double)k, -vec3.y * (double)0.6F, -vec3.z * (double)0.08F * (double)k);
                }

                vec3.yRot(0.19634955F);
            }
        }

    }

    @Override
    public void doServerTick() {
        ++this.flameTicks;
        if (this.flameTicks >= 200) {
            if (this.flameCount >= 4) {
                this.dragon.getPhaseManager().setPhase(EnderDragonPhase.TAKEOFF);
            } else {
                this.dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_SCANNING);
            }
        } else if (this.flameTicks == 10) {
            Vec3 vec3 = (new Vec3(this.dragon.head.getX() - this.dragon.getX(), 0.0D, this.dragon.head.getZ() - this.dragon.getZ())).normalize();
            float f = 5.0F;
            double d = this.dragon.head.getX() + vec3.x * 5.0D / 2.0D;
            double e = this.dragon.head.getZ() + vec3.z * 5.0D / 2.0D;
            double g = this.dragon.head.getY(0.5D);
            double h = g;
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(d, g, e);

            while(this.dragon.level().isEmptyBlock(mutableBlockPos)) {
                --h;
                if (h < 0.0D) {
                    h = g;
                    break;
                }

                mutableBlockPos.set(d, h, e);
            }

            h = (double)(Mth.floor(h) + 1);
            this.flame = new AreaEffectCloud(this.dragon.level(), d, h, e);
            this.flame.setOwner(this.dragon);
            this.flame.setRadius(5.0F);
            this.flame.setDuration(200);
            this.flame.setParticle(ParticleTypes.DRAGON_BREATH);
            this.flame.addEffect(new MobEffectInstance(MobEffects.HARM));
            if (new com.destroystokyo.paper.event.entity.EnderDragonFlameEvent((org.bukkit.entity.EnderDragon) this.dragon.getBukkitEntity(), (org.bukkit.entity.AreaEffectCloud) this.flame.getBukkitEntity()).callEvent()) { // Paper
            this.dragon.level().addFreshEntity(this.flame);
            // Paper start
            } else {
                this.end();
            }
            // Paper end
        }

    }

    @Override
    public void begin() {
        this.flameTicks = 0;
        ++this.flameCount;
    }

    @Override
    public void end() {
        if (this.flame != null) {
            this.flame.discard();
            this.flame = null;
        }

    }

    @Override
    public EnderDragonPhase<DragonSittingFlamingPhase> getPhase() {
        return EnderDragonPhase.SITTING_FLAMING;
    }

    public void resetFlameCount() {
        this.flameCount = 0;
    }
}
