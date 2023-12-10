package net.minecraft.world.entity;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddExperienceOrbPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerExpCooldownChangeEvent;
// CraftBukkit end

public class ExperienceOrb extends Entity {

    private static final int LIFETIME = 6000;
    private static final int ENTITY_SCAN_PERIOD = 20;
    private static final int MAX_FOLLOW_DIST = 8;
    private static final int ORB_GROUPS_PER_AREA = 40;
    private static final double ORB_MERGE_DISTANCE = 0.5D;
    private int age;
    private int health;
    public int value;
    private int count;
    private Player followingPlayer;
    // Paper start
    @javax.annotation.Nullable
    public java.util.UUID sourceEntityId;
    @javax.annotation.Nullable
    public java.util.UUID triggerEntityId;
    public org.bukkit.entity.ExperienceOrb.SpawnReason spawnReason = org.bukkit.entity.ExperienceOrb.SpawnReason.UNKNOWN;

    private void loadPaperNBT(CompoundTag nbttagcompound) {
        if (!nbttagcompound.contains("Paper.ExpData", 10)) { // 10 = compound
            return;
        }
        CompoundTag comp = nbttagcompound.getCompound("Paper.ExpData");
        if (comp.hasUUID("source")) {
            this.sourceEntityId = comp.getUUID("source");
        }
        if (comp.hasUUID("trigger")) {
            this.triggerEntityId = comp.getUUID("trigger");
        }
        if (comp.contains("reason")) {
            String reason = comp.getString("reason");
            try {
                this.spawnReason = org.bukkit.entity.ExperienceOrb.SpawnReason.valueOf(reason);
            } catch (Exception e) {
                this.level().getCraftServer().getLogger().warning("Invalid spawnReason set for experience orb: " + e.getMessage() + " - " + reason);
            }
        }
    }
    private void savePaperNBT(CompoundTag nbttagcompound) {
        CompoundTag comp = new CompoundTag();
        if (this.sourceEntityId != null) {
            comp.putUUID("source", this.sourceEntityId);
        }
        if (this.triggerEntityId != null) {
            comp.putUUID("trigger", triggerEntityId);
        }
        if (this.spawnReason != null && this.spawnReason != org.bukkit.entity.ExperienceOrb.SpawnReason.UNKNOWN) {
            comp.putString("reason", this.spawnReason.name());
        }
        nbttagcompound.put("Paper.ExpData", comp);
    }

    @io.papermc.paper.annotation.DoNotUse
    @Deprecated
    public ExperienceOrb(Level world, double x, double y, double z, int amount) {
        this(world, x, y, z, amount, null, null);
    }

    public ExperienceOrb(Level world, double d0, double d1, double d2, int i, @javax.annotation.Nullable org.bukkit.entity.ExperienceOrb.SpawnReason reason, @javax.annotation.Nullable Entity triggerId) {
        this(world, d0, d1, d2, i, reason, triggerId, null);
    }

    public ExperienceOrb(Level world, double d0, double d1, double d2, int i, @javax.annotation.Nullable org.bukkit.entity.ExperienceOrb.SpawnReason reason, @javax.annotation.Nullable Entity triggerId, @javax.annotation.Nullable Entity sourceId) {
        this(EntityType.EXPERIENCE_ORB, world);
        this.sourceEntityId = sourceId != null ? sourceId.getUUID() : null;
        this.triggerEntityId = triggerId != null ? triggerId.getUUID() : null;
        this.spawnReason = reason != null ? reason : org.bukkit.entity.ExperienceOrb.SpawnReason.UNKNOWN;
        // Paper end
        this.setPos(d0, d1, d2);
        this.setYRot((float) (this.random.nextDouble() * 360.0D));
        this.setDeltaMovement((this.random.nextDouble() * 0.20000000298023224D - 0.10000000149011612D) * 2.0D, this.random.nextDouble() * 0.2D * 2.0D, (this.random.nextDouble() * 0.20000000298023224D - 0.10000000149011612D) * 2.0D);
        this.value = i;
    }

    public ExperienceOrb(EntityType<? extends ExperienceOrb> type, Level world) {
        super(type, world);
        this.health = 5;
        this.count = 1;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public void tick() {
        super.tick();
        Player prevTarget = this.followingPlayer;// CraftBukkit - store old target
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        if (this.isEyeInFluid(FluidTags.WATER)) {
            this.setUnderwaterMovement();
        } else if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.03D, 0.0D));
        }

        if (this.level().getFluidState(this.blockPosition()).is(FluidTags.LAVA)) {
            this.setDeltaMovement((double) ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F), 0.20000000298023224D, (double) ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F));
        }

        if (!this.level().noCollision(this.getBoundingBox())) {
            this.moveTowardsClosestSpace(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0D, this.getZ());
        }

        if (this.tickCount % 20 == 1) {
            this.scanForEntities();
        }

        if (this.followingPlayer != null && (this.followingPlayer.isSpectator() || this.followingPlayer.isDeadOrDying())) {
            this.followingPlayer = null;
        }

        // CraftBukkit start
        boolean cancelled = false;
        if (this.followingPlayer != prevTarget) {
            EntityTargetLivingEntityEvent event = CraftEventFactory.callEntityTargetLivingEvent(this, this.followingPlayer, (this.followingPlayer != null) ? EntityTargetEvent.TargetReason.CLOSEST_PLAYER : EntityTargetEvent.TargetReason.FORGOT_TARGET);
            LivingEntity target = (event.getTarget() == null) ? null : ((org.bukkit.craftbukkit.entity.CraftLivingEntity) event.getTarget()).getHandle();
            cancelled = event.isCancelled();

            if (cancelled) {
                this.followingPlayer = prevTarget;
            } else {
                this.followingPlayer = (target instanceof Player) ? (Player) target : null;
            }
        }

        if (this.followingPlayer != null && !cancelled) {
            // CraftBukkit end
            Vec3 vec3d = new Vec3(this.followingPlayer.getX() - this.getX(), this.followingPlayer.getY() + (double) this.followingPlayer.getEyeHeight() / 2.0D - this.getY(), this.followingPlayer.getZ() - this.getZ());
            double d0 = vec3d.lengthSqr();

            if (d0 < 64.0D) {
                double d1 = 1.0D - Math.sqrt(d0) / 8.0D;

                this.setDeltaMovement(this.getDeltaMovement().add(vec3d.normalize().scale(d1 * d1 * 0.1D)));
            }
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        float f = 0.98F;

        if (this.onGround()) {
            f = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.98F;
        }

        this.setDeltaMovement(this.getDeltaMovement().multiply((double) f, 0.98D, (double) f));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0D, -0.9D, 1.0D));
        }

        ++this.age;
        if (this.age >= 6000) {
            this.discard();
        }

    }

    @Override
    protected BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.999999F);
    }

    private void scanForEntities() {
        if (this.followingPlayer == null || this.followingPlayer.distanceToSqr((Entity) this) > 64.0D) {
            this.followingPlayer = this.level().getNearestPlayer(this, 8.0D);
        }

        if (this.level() instanceof ServerLevel) {
            List<ExperienceOrb> list = this.level().getEntities(EntityTypeTest.forClass(ExperienceOrb.class), this.getBoundingBox().inflate(0.5D), this::canMerge);
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                ExperienceOrb entityexperienceorb = (ExperienceOrb) iterator.next();

                this.merge(entityexperienceorb);
            }
        }

    }

    public static void award(ServerLevel world, Vec3 pos, int amount) {
        // Paper start - add reasons for orbs
        award(world, pos, amount, null, null, null);
    }
    public static void award(ServerLevel world, Vec3 pos, int amount, org.bukkit.entity.ExperienceOrb.SpawnReason reason, Entity triggerId) {
        award(world, pos, amount, reason, triggerId, null);
    }
    public static void award(ServerLevel world, Vec3 pos, int amount, org.bukkit.entity.ExperienceOrb.SpawnReason reason, Entity triggerId, Entity sourceId) {
        // Paper end - add reasons for orbs
        while (amount > 0) {
            int j = ExperienceOrb.getExperienceValue(amount);

            amount -= j;
            if (!ExperienceOrb.tryMergeToExisting(world, pos, j)) {
                world.addFreshEntity(new ExperienceOrb(world, pos.x(), pos.y(), pos.z(), j, reason, triggerId, sourceId)); // Paper - add reason
            }
        }

    }

    private static boolean tryMergeToExisting(ServerLevel world, Vec3 pos, int amount) {
        AABB axisalignedbb = AABB.ofSize(pos, 1.0D, 1.0D, 1.0D);
        int j = world.getRandom().nextInt(40);
        List<ExperienceOrb> list = world.getEntities(EntityTypeTest.forClass(ExperienceOrb.class), axisalignedbb, (entityexperienceorb) -> {
            return ExperienceOrb.canMerge(entityexperienceorb, j, amount);
        });

        if (!list.isEmpty()) {
            ExperienceOrb entityexperienceorb = (ExperienceOrb) list.get(0);

            ++entityexperienceorb.count;
            entityexperienceorb.age = 0;
            return true;
        } else {
            return false;
        }
    }

    private boolean canMerge(ExperienceOrb other) {
        return other != this && ExperienceOrb.canMerge(other, this.getId(), this.value);
    }

    private static boolean canMerge(ExperienceOrb orb, int seed, int amount) {
        return !orb.isRemoved() && (orb.getId() - seed) % 40 == 0 && orb.value == amount;
    }

    private void merge(ExperienceOrb other) {
        this.count += other.count;
        this.age = Math.min(this.age, other.age);
        other.discard();
    }

    private void setUnderwaterMovement() {
        Vec3 vec3d = this.getDeltaMovement();

        this.setDeltaMovement(vec3d.x * 0.9900000095367432D, Math.min(vec3d.y + 5.000000237487257E-4D, 0.05999999865889549D), vec3d.z * 0.9900000095367432D);
    }

    @Override
    protected void doWaterSplashEffect() {}

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else if (this.level().isClientSide) {
            return true;
        } else {
            this.markHurt();
            this.health = (int) ((float) this.health - amount);
            if (this.health <= 0) {
                this.discard();
            }

            return true;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putShort("Health", (short) this.health);
        nbt.putShort("Age", (short) this.age);
        nbt.putInt("Value", this.value); // Paper - save as Integer
        nbt.putInt("Count", this.count);
        this.savePaperNBT(nbt); // Paper
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        this.health = nbt.getShort("Health");
        this.age = nbt.getShort("Age");
        this.value = nbt.getInt("Value"); // Paper - load as Integer
        this.count = Math.max(nbt.getInt("Count"), 1);
        this.loadPaperNBT(nbt); // Paper
    }

    @Override
    public void playerTouch(Player player) {
        if (!this.level().isClientSide) {
            if (player.takeXpDelay == 0 && new com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent(((net.minecraft.server.level.ServerPlayer) player).getBukkitEntity(), (org.bukkit.entity.ExperienceOrb) this.getBukkitEntity()).callEvent()) { // Paper
                player.takeXpDelay = CraftEventFactory.callPlayerXpCooldownEvent(player, 2, PlayerExpCooldownChangeEvent.ChangeReason.PICKUP_ORB).getNewCooldown(); // CraftBukkit - entityhuman.takeXpDelay = 2;
                player.take(this, 1);
                int i = this.repairPlayerItems(player, this.value);

                if (i > 0) {
                    player.giveExperiencePoints(CraftEventFactory.callPlayerExpChangeEvent(player, this).getAmount()); // CraftBukkit - this.value -> event.getAmount() // Paper - supply experience orb object
                }

                --this.count;
                if (this.count == 0) {
                    this.discard();
                }
            }

        }
    }

    private int repairPlayerItems(Player player, int amount) {
        Entry<EquipmentSlot, ItemStack> entry = EnchantmentHelper.getRandomItemWith(Enchantments.MENDING, player, ItemStack::isDamaged);

        if (entry != null) {
            ItemStack itemstack = (ItemStack) entry.getValue();
            int j = Math.min(this.xpToDurability(this.value), itemstack.getDamageValue());
            // CraftBukkit start
            org.bukkit.event.player.PlayerItemMendEvent event = CraftEventFactory.callPlayerItemMendEvent(player, this, itemstack, entry.getKey(), j, this::durabilityToXp); // Paper
            j = event.getRepairAmount();
            if (event.isCancelled()) {
                return amount;
            }
            // CraftBukkit end

            itemstack.setDamageValue(itemstack.getDamageValue() - j);
            int k = amount - event.getDurabilityToXpOperation().applyAsInt(j); // Paper
            this.value = k; // CraftBukkit - update exp value of orb for PlayerItemMendEvent calls
            // Paper start
            if (j == 0 && amount == k) { // if repair amount is 0 and no xp was removed, don't do recursion; treat as cancelled
                return k;
            }
            // Paper end

            return k > 0 ? this.repairPlayerItems(player, k) : 0;
        } else {
            return amount;
        }
    }

    public int durabilityToXp(int repairAmount) {
        return repairAmount / 2;
    }

    public int xpToDurability(int experienceAmount) {
        return experienceAmount * 2;
    }

    public int getValue() {
        return this.value;
    }

    public int getIcon() {
        return this.value >= 2477 ? 10 : (this.value >= 1237 ? 9 : (this.value >= 617 ? 8 : (this.value >= 307 ? 7 : (this.value >= 149 ? 6 : (this.value >= 73 ? 5 : (this.value >= 37 ? 4 : (this.value >= 17 ? 3 : (this.value >= 7 ? 2 : (this.value >= 3 ? 1 : 0)))))))));
    }

    public static int getExperienceValue(int value) {
        // CraftBukkit start
        if (value > 162670129) return value - 100000;
        if (value > 81335063) return 81335063;
        if (value > 40667527) return 40667527;
        if (value > 20333759) return 20333759;
        if (value > 10166857) return 10166857;
        if (value > 5083423) return 5083423;
        if (value > 2541701) return 2541701;
        if (value > 1270849) return 1270849;
        if (value > 635413) return 635413;
        if (value > 317701) return 317701;
        if (value > 158849) return 158849;
        if (value > 79423) return 79423;
        if (value > 39709) return 39709;
        if (value > 19853) return 19853;
        if (value > 9923) return 9923;
        if (value > 4957) return 4957;
        // CraftBukkit end
        return value >= 2477 ? 2477 : (value >= 1237 ? 1237 : (value >= 617 ? 617 : (value >= 307 ? 307 : (value >= 149 ? 149 : (value >= 73 ? 73 : (value >= 37 ? 37 : (value >= 17 ? 17 : (value >= 7 ? 7 : (value >= 3 ? 3 : 1)))))))));
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddExperienceOrbPacket(this);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.AMBIENT;
    }
}
