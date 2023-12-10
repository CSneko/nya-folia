package net.minecraft.world.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class EyeOfEnder extends Entity implements ItemSupplier {

    private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK = SynchedEntityData.defineId(EyeOfEnder.class, EntityDataSerializers.ITEM_STACK);
    public double tx;
    public double ty;
    public double tz;
    public int life;
    public boolean surviveAfterDeath;

    public EyeOfEnder(EntityType<? extends EyeOfEnder> type, Level world) {
        super(type, world);
    }

    public EyeOfEnder(Level world, double x, double y, double z) {
        this(EntityType.EYE_OF_ENDER, world);
        this.setPos(x, y, z);
    }

    public void setItem(ItemStack stack) {
        if (true || !stack.is(Items.ENDER_EYE) || stack.hasTag()) { // CraftBukkit - always allow item changing
            this.getEntityData().set(EyeOfEnder.DATA_ITEM_STACK, stack.copyWithCount(1));
        }

    }

    private ItemStack getItemRaw() {
        return (ItemStack) this.getEntityData().get(EyeOfEnder.DATA_ITEM_STACK);
    }

    @Override
    public ItemStack getItem() {
        ItemStack itemstack = this.getItemRaw();

        return itemstack.isEmpty() ? new ItemStack(Items.ENDER_EYE) : itemstack;
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(EyeOfEnder.DATA_ITEM_STACK, ItemStack.EMPTY);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = this.getBoundingBox().getSize() * 4.0D;

        if (Double.isNaN(d1)) {
            d1 = 4.0D;
        }

        d1 *= 64.0D;
        return distance < d1 * d1;
    }

    public void signalTo(BlockPos pos) {
        // Paper start
        this.signalTo(pos, true);
    }
    public void signalTo(BlockPos pos, boolean update) {
        // Paper end
        double d0 = (double) pos.getX();
        int i = pos.getY();
        double d1 = (double) pos.getZ();
        double d2 = d0 - this.getX();
        double d3 = d1 - this.getZ();
        double d4 = Math.sqrt(d2 * d2 + d3 * d3);

        if (d4 > 12.0D) {
            this.tx = this.getX() + d2 / d4 * 12.0D;
            this.tz = this.getZ() + d3 / d4 * 12.0D;
            this.ty = this.getY() + 8.0D;
        } else {
            this.tx = d0;
            this.ty = (double) i;
            this.tz = d1;
        }

        if (update) { // Paper
        this.life = 0;
        this.surviveAfterDeath = this.random.nextInt(5) > 0;
        } // Paper
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        this.setDeltaMovement(x, y, z);
        if (this.xRotO == 0.0F && this.yRotO == 0.0F) {
            double d3 = Math.sqrt(x * x + z * z);

            this.setYRot((float) (Mth.atan2(x, z) * 57.2957763671875D));
            this.setXRot((float) (Mth.atan2(y, d3) * 57.2957763671875D));
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
        }

    }

    @Override
    public void tick() {
        super.tick();
        Vec3 vec3d = this.getDeltaMovement();
        double d0 = this.getX() + vec3d.x;
        double d1 = this.getY() + vec3d.y;
        double d2 = this.getZ() + vec3d.z;
        double d3 = vec3d.horizontalDistance();

        this.setXRot(Projectile.lerpRotation(this.xRotO, (float) (Mth.atan2(vec3d.y, d3) * 57.2957763671875D)));
        this.setYRot(Projectile.lerpRotation(this.yRotO, (float) (Mth.atan2(vec3d.x, vec3d.z) * 57.2957763671875D)));
        if (!this.level().isClientSide) {
            double d4 = this.tx - d0;
            double d5 = this.tz - d2;
            float f = (float) Math.sqrt(d4 * d4 + d5 * d5);
            float f1 = (float) Mth.atan2(d5, d4);
            double d6 = Mth.lerp(0.0025D, d3, (double) f);
            double d7 = vec3d.y;

            if (f < 1.0F) {
                d6 *= 0.8D;
                d7 *= 0.8D;
            }

            int i = this.getY() < this.ty ? 1 : -1;

            vec3d = new Vec3(Math.cos((double) f1) * d6, d7 + ((double) i - d7) * 0.014999999664723873D, Math.sin((double) f1) * d6);
            this.setDeltaMovement(vec3d);
        }

        float f2 = 0.25F;

        if (this.isInWater()) {
            for (int j = 0; j < 4; ++j) {
                this.level().addParticle(ParticleTypes.BUBBLE, d0 - vec3d.x * 0.25D, d1 - vec3d.y * 0.25D, d2 - vec3d.z * 0.25D, vec3d.x, vec3d.y, vec3d.z);
            }
        } else {
            this.level().addParticle(ParticleTypes.PORTAL, d0 - vec3d.x * 0.25D + this.random.nextDouble() * 0.6D - 0.3D, d1 - vec3d.y * 0.25D - 0.5D, d2 - vec3d.z * 0.25D + this.random.nextDouble() * 0.6D - 0.3D, vec3d.x, vec3d.y, vec3d.z);
        }

        if (!this.level().isClientSide) {
            this.setPos(d0, d1, d2);
            ++this.life;
            if (this.life > 80 && !this.level().isClientSide) {
                this.playSound(SoundEvents.ENDER_EYE_DEATH, 1.0F, 1.0F);
                this.discard();
                if (this.surviveAfterDeath) {
                    this.level().addFreshEntity(new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), this.getItem()));
                } else {
                    this.level().levelEvent(2003, this.blockPosition(), 0);
                }
            }
        } else {
            this.setPosRaw(d0, d1, d2);
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        ItemStack itemstack = this.getItemRaw();

        if (!itemstack.isEmpty()) {
            nbt.put("Item", itemstack.save(new CompoundTag()));
        }

    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        ItemStack itemstack = ItemStack.of(nbt.getCompound("Item"));

        if (!itemstack.isEmpty()) this.setItem(itemstack); // CraftBukkit - SPIGOT-6103 summon, see also SPIGOT-5474
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }
}
