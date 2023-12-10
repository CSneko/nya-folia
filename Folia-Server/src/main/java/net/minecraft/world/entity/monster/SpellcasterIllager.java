package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public abstract class SpellcasterIllager extends AbstractIllager {

    private static final EntityDataAccessor<Byte> DATA_SPELL_CASTING_ID = SynchedEntityData.defineId(SpellcasterIllager.class, EntityDataSerializers.BYTE);
    protected int spellCastingTickCount;
    private SpellcasterIllager.IllagerSpell currentSpell;

    protected SpellcasterIllager(EntityType<? extends SpellcasterIllager> type, Level world) {
        super(type, world);
        this.currentSpell = SpellcasterIllager.IllagerSpell.NONE;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SpellcasterIllager.DATA_SPELL_CASTING_ID, (byte) 0);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.spellCastingTickCount = nbt.getInt("SpellTicks");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("SpellTicks", this.spellCastingTickCount);
    }

    @Override
    public AbstractIllager.IllagerArmPose getArmPose() {
        return this.isCastingSpell() ? AbstractIllager.IllagerArmPose.SPELLCASTING : (this.isCelebrating() ? AbstractIllager.IllagerArmPose.CELEBRATING : AbstractIllager.IllagerArmPose.CROSSED);
    }

    public boolean isCastingSpell() {
        return this.level().isClientSide ? (Byte) this.entityData.get(SpellcasterIllager.DATA_SPELL_CASTING_ID) > 0 : this.spellCastingTickCount > 0;
    }

    public void setIsCastingSpell(SpellcasterIllager.IllagerSpell spell) {
        this.currentSpell = spell;
        this.entityData.set(SpellcasterIllager.DATA_SPELL_CASTING_ID, (byte) spell.id);
    }

    public SpellcasterIllager.IllagerSpell getCurrentSpell() {
        return !this.level().isClientSide ? this.currentSpell : SpellcasterIllager.IllagerSpell.byId((Byte) this.entityData.get(SpellcasterIllager.DATA_SPELL_CASTING_ID));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.spellCastingTickCount > 0) {
            --this.spellCastingTickCount;
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide && this.isCastingSpell()) {
            SpellcasterIllager.IllagerSpell entityillagerwizard_spell = this.getCurrentSpell();
            double d0 = entityillagerwizard_spell.spellColor[0];
            double d1 = entityillagerwizard_spell.spellColor[1];
            double d2 = entityillagerwizard_spell.spellColor[2];
            float f = this.yBodyRot * 0.017453292F + Mth.cos((float) this.tickCount * 0.6662F) * 0.25F;
            float f1 = Mth.cos(f);
            float f2 = Mth.sin(f);

            this.level().addParticle(ParticleTypes.ENTITY_EFFECT, this.getX() + (double) f1 * 0.6D, this.getY() + 1.8D, this.getZ() + (double) f2 * 0.6D, d0, d1, d2);
            this.level().addParticle(ParticleTypes.ENTITY_EFFECT, this.getX() - (double) f1 * 0.6D, this.getY() + 1.8D, this.getZ() - (double) f2 * 0.6D, d0, d1, d2);
        }

    }

    protected int getSpellCastingTime() {
        return this.spellCastingTickCount;
    }

    protected abstract SoundEvent getCastingSoundEvent();

    public static enum IllagerSpell {

        NONE(0, 0.0D, 0.0D, 0.0D), SUMMON_VEX(1, 0.7D, 0.7D, 0.8D), FANGS(2, 0.4D, 0.3D, 0.35D), WOLOLO(3, 0.7D, 0.5D, 0.2D), DISAPPEAR(4, 0.3D, 0.3D, 0.8D), BLINDNESS(5, 0.1D, 0.1D, 0.2D);

        private static final IntFunction<SpellcasterIllager.IllagerSpell> BY_ID = ByIdMap.continuous((entityillagerwizard_spell) -> {
            return entityillagerwizard_spell.id;
        }, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        final int id;
        final double[] spellColor;

        private IllagerSpell(int i, double d0, double d1, double d2) {
            this.id = i;
            this.spellColor = new double[]{d0, d1, d2};
        }

        public static SpellcasterIllager.IllagerSpell byId(int id) {
            return (SpellcasterIllager.IllagerSpell) SpellcasterIllager.IllagerSpell.BY_ID.apply(id);
        }
    }

    protected abstract class SpellcasterUseSpellGoal extends Goal {

        protected int attackWarmupDelay;
        protected int nextAttackTickCount;

        protected SpellcasterUseSpellGoal() {}

        @Override
        public boolean canUse() {
            LivingEntity entityliving = SpellcasterIllager.this.getTarget();

            return entityliving != null && entityliving.isAlive() ? (SpellcasterIllager.this.isCastingSpell() ? false : SpellcasterIllager.this.tickCount >= this.nextAttackTickCount) : false;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity entityliving = SpellcasterIllager.this.getTarget();

            return entityliving != null && entityliving.isAlive() && this.attackWarmupDelay > 0;
        }

        @Override
        public void start() {
            this.attackWarmupDelay = this.adjustedTickDelay(this.getCastWarmupTime());
            SpellcasterIllager.this.spellCastingTickCount = this.getCastingTime();
            this.nextAttackTickCount = SpellcasterIllager.this.tickCount + this.getCastingInterval();
            SoundEvent soundeffect = this.getSpellPrepareSound();

            if (soundeffect != null) {
                SpellcasterIllager.this.playSound(soundeffect, 1.0F, 1.0F);
            }

            SpellcasterIllager.this.setIsCastingSpell(this.getSpell());
        }

        @Override
        public void tick() {
            --this.attackWarmupDelay;
            if (this.attackWarmupDelay == 0) {
                // CraftBukkit start
                if (!CraftEventFactory.handleEntitySpellCastEvent(SpellcasterIllager.this, this.getSpell())) {
                    return;
                }
                // CraftBukkit end
                this.performSpellCasting();
                SpellcasterIllager.this.playSound(SpellcasterIllager.this.getCastingSoundEvent(), 1.0F, 1.0F);
            }

        }

        protected abstract void performSpellCasting();

        protected int getCastWarmupTime() {
            return 20;
        }

        protected abstract int getCastingTime();

        protected abstract int getCastingInterval();

        @Nullable
        protected abstract SoundEvent getSpellPrepareSound();

        protected abstract SpellcasterIllager.IllagerSpell getSpell();
    }

    protected class SpellcasterCastingSpellGoal extends Goal {

        public SpellcasterCastingSpellGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return SpellcasterIllager.this.getSpellCastingTime() > 0;
        }

        @Override
        public void start() {
            super.start();
            SpellcasterIllager.this.navigation.stop();
        }

        @Override
        public void stop() {
            super.stop();
            SpellcasterIllager.this.setIsCastingSpell(SpellcasterIllager.IllagerSpell.NONE);
        }

        @Override
        public void tick() {
            if (SpellcasterIllager.this.getTarget() != null) {
                SpellcasterIllager.this.getLookControl().setLookAt(SpellcasterIllager.this.getTarget(), (float) SpellcasterIllager.this.getMaxHeadYRot(), (float) SpellcasterIllager.this.getMaxHeadXRot());
            }

        }
    }
}
