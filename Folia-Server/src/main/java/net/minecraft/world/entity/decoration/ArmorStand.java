package net.minecraft.world.entity.decoration;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Rotations;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.craftbukkit.CraftEquipmentSlot;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
// CraftBukkit end

public class ArmorStand extends LivingEntity {

    public static final int WOBBLE_TIME = 5;
    private static final boolean ENABLE_ARMS = true;
    private static final Rotations DEFAULT_HEAD_POSE = new Rotations(0.0F, 0.0F, 0.0F);
    private static final Rotations DEFAULT_BODY_POSE = new Rotations(0.0F, 0.0F, 0.0F);
    private static final Rotations DEFAULT_LEFT_ARM_POSE = new Rotations(-10.0F, 0.0F, -10.0F);
    private static final Rotations DEFAULT_RIGHT_ARM_POSE = new Rotations(-15.0F, 0.0F, 10.0F);
    private static final Rotations DEFAULT_LEFT_LEG_POSE = new Rotations(-1.0F, 0.0F, -1.0F);
    private static final Rotations DEFAULT_RIGHT_LEG_POSE = new Rotations(1.0F, 0.0F, 1.0F);
    private static final EntityDimensions MARKER_DIMENSIONS = new EntityDimensions(0.0F, 0.0F, true);
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.ARMOR_STAND.getDimensions().scale(0.5F);
    private static final double FEET_OFFSET = 0.1D;
    private static final double CHEST_OFFSET = 0.9D;
    private static final double LEGS_OFFSET = 0.4D;
    private static final double HEAD_OFFSET = 1.6D;
    public static final int DISABLE_TAKING_OFFSET = 8;
    public static final int DISABLE_PUTTING_OFFSET = 16;
    public static final int CLIENT_FLAG_SMALL = 1;
    public static final int CLIENT_FLAG_SHOW_ARMS = 4;
    public static final int CLIENT_FLAG_NO_BASEPLATE = 8;
    public static final int CLIENT_FLAG_MARKER = 16;
    public static final EntityDataAccessor<Byte> DATA_CLIENT_FLAGS = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.BYTE);
    public static final EntityDataAccessor<Rotations> DATA_HEAD_POSE = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_BODY_POSE = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_LEFT_ARM_POSE = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_RIGHT_ARM_POSE = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_LEFT_LEG_POSE = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
    public static final EntityDataAccessor<Rotations> DATA_RIGHT_LEG_POSE = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
    private static final Predicate<Entity> RIDABLE_MINECARTS = (entity) -> {
        return entity instanceof AbstractMinecart && ((AbstractMinecart) entity).getMinecartType() == AbstractMinecart.Type.RIDEABLE;
    };
    private final NonNullList<ItemStack> handItems;
    private final NonNullList<ItemStack> armorItems;
    private boolean invisible;
    public long lastHit;
    public int disabledSlots;
    public Rotations headPose;
    public Rotations bodyPose;
    public Rotations leftArmPose;
    public Rotations rightArmPose;
    public Rotations leftLegPose;
    public Rotations rightLegPose;
    public boolean canMove = true; // Paper
    // Paper start - Allow ArmorStands not to tick
    public boolean canTick = true;
    public boolean canTickSetByAPI = false;
    private boolean noTickPoseDirty = false;
    private boolean noTickEquipmentDirty = false;
    // Paper end

    public ArmorStand(EntityType<? extends ArmorStand> type, Level world) {
        super(type, world);
        if (world != null) this.canTick = world.paperConfig().entities.armorStands.tick; // Paper - armour stand ticking
        this.handItems = NonNullList.withSize(2, ItemStack.EMPTY);
        this.armorItems = NonNullList.withSize(4, ItemStack.EMPTY);
        this.headPose = ArmorStand.DEFAULT_HEAD_POSE;
        this.bodyPose = ArmorStand.DEFAULT_BODY_POSE;
        this.leftArmPose = ArmorStand.DEFAULT_LEFT_ARM_POSE;
        this.rightArmPose = ArmorStand.DEFAULT_RIGHT_ARM_POSE;
        this.leftLegPose = ArmorStand.DEFAULT_LEFT_LEG_POSE;
        this.rightLegPose = ArmorStand.DEFAULT_RIGHT_LEG_POSE;
        this.setMaxUpStep(0.0F);
    }

    public ArmorStand(Level world, double x, double y, double z) {
        this(EntityType.ARMOR_STAND, world);
        this.setPos(x, y, z);
    }

    // CraftBukkit start - SPIGOT-3607, SPIGOT-3637
    @Override
    public float getBukkitYaw() {
        return this.getYRot();
    }
    // CraftBukkit end

    @Override
    public void refreshDimensions() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        super.refreshDimensions();
        this.setPos(d0, d1, d2);
    }

    private boolean hasPhysics() {
        return !this.isMarker() && !this.isNoGravity();
    }

    @Override
    public boolean isEffectiveAi() {
        return super.isEffectiveAi() && this.hasPhysics();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ArmorStand.DATA_CLIENT_FLAGS, (byte) 0);
        this.entityData.define(ArmorStand.DATA_HEAD_POSE, ArmorStand.DEFAULT_HEAD_POSE);
        this.entityData.define(ArmorStand.DATA_BODY_POSE, ArmorStand.DEFAULT_BODY_POSE);
        this.entityData.define(ArmorStand.DATA_LEFT_ARM_POSE, ArmorStand.DEFAULT_LEFT_ARM_POSE);
        this.entityData.define(ArmorStand.DATA_RIGHT_ARM_POSE, ArmorStand.DEFAULT_RIGHT_ARM_POSE);
        this.entityData.define(ArmorStand.DATA_LEFT_LEG_POSE, ArmorStand.DEFAULT_LEFT_LEG_POSE);
        this.entityData.define(ArmorStand.DATA_RIGHT_LEG_POSE, ArmorStand.DEFAULT_RIGHT_LEG_POSE);
    }

    @Override
    public Iterable<ItemStack> getHandSlots() {
        return this.handItems;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return this.armorItems;
    }

    @Override
    public ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot) {
        switch (slot.getType()) {
            case HAND:
                return (ItemStack) this.handItems.get(slot.getIndex());
            case ARMOR:
                return (ItemStack) this.armorItems.get(slot.getIndex());
            default:
                return ItemStack.EMPTY;
        }
    }

    @Override
    public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot, ItemStack stack) {
        // CraftBukkit start
        this.setItemSlot(slot, stack, false);
    }

    @Override
    public void setItemSlot(net.minecraft.world.entity.EquipmentSlot enumitemslot, ItemStack itemstack, boolean silent) {
        // CraftBukkit end
        this.verifyEquippedItem(itemstack);
        switch (enumitemslot.getType()) {
            case HAND:
                this.onEquipItem(enumitemslot, (ItemStack) this.handItems.set(enumitemslot.getIndex(), itemstack), itemstack, silent); // CraftBukkit
                break;
            case ARMOR:
                this.onEquipItem(enumitemslot, (ItemStack) this.armorItems.set(enumitemslot.getIndex(), itemstack), itemstack, silent); // CraftBukkit
        }

        this.noTickEquipmentDirty = true; // Paper - Allow equipment to be updated even when tick disabled
    }

    @Override
    public boolean canTakeItem(ItemStack stack) {
        net.minecraft.world.entity.EquipmentSlot enumitemslot = Mob.getEquipmentSlotForItem(stack);

        return this.getItemBySlot(enumitemslot).isEmpty() && !this.isDisabled(enumitemslot);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        ListTag nbttaglist = new ListTag();

        CompoundTag nbttagcompound1;

        for (Iterator iterator = this.armorItems.iterator(); iterator.hasNext(); nbttaglist.add(nbttagcompound1)) {
            ItemStack itemstack = (ItemStack) iterator.next();

            nbttagcompound1 = new CompoundTag();
            if (!itemstack.isEmpty()) {
                itemstack.save(nbttagcompound1);
            }
        }

        nbt.put("ArmorItems", nbttaglist);
        ListTag nbttaglist1 = new ListTag();

        CompoundTag nbttagcompound2;

        for (Iterator iterator1 = this.handItems.iterator(); iterator1.hasNext(); nbttaglist1.add(nbttagcompound2)) {
            ItemStack itemstack1 = (ItemStack) iterator1.next();

            nbttagcompound2 = new CompoundTag();
            if (!itemstack1.isEmpty()) {
                itemstack1.save(nbttagcompound2);
            }
        }

        nbt.put("HandItems", nbttaglist1);
        nbt.putBoolean("Invisible", this.isInvisible());
        nbt.putBoolean("Small", this.isSmall());
        nbt.putBoolean("ShowArms", this.isShowArms());
        nbt.putInt("DisabledSlots", this.disabledSlots);
        nbt.putBoolean("NoBasePlate", this.isNoBasePlate());
        if (this.isMarker()) {
            nbt.putBoolean("Marker", this.isMarker());
        }

        nbt.put("Pose", this.writePose());
        if (this.canTickSetByAPI) nbt.putBoolean("Paper.CanTickOverride", this.canTick); // Paper - persist no tick setting
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        ListTag nbttaglist;
        int i;

        if (nbt.contains("ArmorItems", 9)) {
            nbttaglist = nbt.getList("ArmorItems", 10);

            for (i = 0; i < this.armorItems.size(); ++i) {
                this.armorItems.set(i, ItemStack.of(nbttaglist.getCompound(i)));
            }
        }

        if (nbt.contains("HandItems", 9)) {
            nbttaglist = nbt.getList("HandItems", 10);

            for (i = 0; i < this.handItems.size(); ++i) {
                this.handItems.set(i, ItemStack.of(nbttaglist.getCompound(i)));
            }
        }

        this.setInvisible(nbt.getBoolean("Invisible"));
        this.setSmall(nbt.getBoolean("Small"));
        this.setShowArms(nbt.getBoolean("ShowArms"));
        this.disabledSlots = nbt.getInt("DisabledSlots");
        this.setNoBasePlate(nbt.getBoolean("NoBasePlate"));
        this.setMarker(nbt.getBoolean("Marker"));
        this.noPhysics = !this.hasPhysics();
        // Paper start - persist no tick
        if (nbt.contains("Paper.CanTickOverride")) {
            this.canTick = nbt.getBoolean("Paper.CanTickOverride");
            this.canTickSetByAPI = true;
        }
        // Paper end
        CompoundTag nbttagcompound1 = nbt.getCompound("Pose");

        this.readPose(nbttagcompound1);
    }

    private void readPose(CompoundTag nbt) {
        ListTag nbttaglist = nbt.getList("Head", 5);

        this.setHeadPose(nbttaglist.isEmpty() ? ArmorStand.DEFAULT_HEAD_POSE : new Rotations(nbttaglist));
        ListTag nbttaglist1 = nbt.getList("Body", 5);

        this.setBodyPose(nbttaglist1.isEmpty() ? ArmorStand.DEFAULT_BODY_POSE : new Rotations(nbttaglist1));
        ListTag nbttaglist2 = nbt.getList("LeftArm", 5);

        this.setLeftArmPose(nbttaglist2.isEmpty() ? ArmorStand.DEFAULT_LEFT_ARM_POSE : new Rotations(nbttaglist2));
        ListTag nbttaglist3 = nbt.getList("RightArm", 5);

        this.setRightArmPose(nbttaglist3.isEmpty() ? ArmorStand.DEFAULT_RIGHT_ARM_POSE : new Rotations(nbttaglist3));
        ListTag nbttaglist4 = nbt.getList("LeftLeg", 5);

        this.setLeftLegPose(nbttaglist4.isEmpty() ? ArmorStand.DEFAULT_LEFT_LEG_POSE : new Rotations(nbttaglist4));
        ListTag nbttaglist5 = nbt.getList("RightLeg", 5);

        this.setRightLegPose(nbttaglist5.isEmpty() ? ArmorStand.DEFAULT_RIGHT_LEG_POSE : new Rotations(nbttaglist5));
    }

    private CompoundTag writePose() {
        CompoundTag nbttagcompound = new CompoundTag();

        if (!ArmorStand.DEFAULT_HEAD_POSE.equals(this.headPose)) {
            nbttagcompound.put("Head", this.headPose.save());
        }

        if (!ArmorStand.DEFAULT_BODY_POSE.equals(this.bodyPose)) {
            nbttagcompound.put("Body", this.bodyPose.save());
        }

        if (!ArmorStand.DEFAULT_LEFT_ARM_POSE.equals(this.leftArmPose)) {
            nbttagcompound.put("LeftArm", this.leftArmPose.save());
        }

        if (!ArmorStand.DEFAULT_RIGHT_ARM_POSE.equals(this.rightArmPose)) {
            nbttagcompound.put("RightArm", this.rightArmPose.save());
        }

        if (!ArmorStand.DEFAULT_LEFT_LEG_POSE.equals(this.leftLegPose)) {
            nbttagcompound.put("LeftLeg", this.leftLegPose.save());
        }

        if (!ArmorStand.DEFAULT_RIGHT_LEG_POSE.equals(this.rightLegPose)) {
            nbttagcompound.put("RightLeg", this.rightLegPose.save());
        }

        return nbttagcompound;
    }

    @Override
    public boolean isCollidable(boolean ignoreClimbing) { // Paper
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    protected void pushEntities() {
        if (!this.level().paperConfig().entities.armorStands.doCollisionEntityLookups) return; // Paper
        List<AbstractMinecart> list = this.level().getEntitiesOfClass(AbstractMinecart.class, this.getBoundingBox(), ArmorStand.RIDABLE_MINECARTS); // Paper - optimise collisions
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            Entity entity = (Entity) iterator.next();

            if (this.distanceToSqr(entity) <= 0.2D) {
                entity.push(this);
            }
        }

    }

    @Override
    public InteractionResult interactAt(net.minecraft.world.entity.player.Player player, Vec3 hitPos, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (!this.isMarker() && !itemstack.is(Items.NAME_TAG)) {
            if (player.isSpectator()) {
                return InteractionResult.SUCCESS;
            } else if (player.level().isClientSide) {
                return InteractionResult.CONSUME;
            } else {
                net.minecraft.world.entity.EquipmentSlot enumitemslot = Mob.getEquipmentSlotForItem(itemstack);

                if (itemstack.isEmpty()) {
                    net.minecraft.world.entity.EquipmentSlot enumitemslot1 = this.getClickedSlot(hitPos);
                    net.minecraft.world.entity.EquipmentSlot enumitemslot2 = this.isDisabled(enumitemslot1) ? enumitemslot : enumitemslot1;

                    if (this.hasItemInSlot(enumitemslot2) && this.swapItem(player, enumitemslot2, itemstack, hand)) {
                        return InteractionResult.SUCCESS;
                    }
                } else {
                    if (this.isDisabled(enumitemslot)) {
                        return InteractionResult.FAIL;
                    }

                    if (enumitemslot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HAND && !this.isShowArms()) {
                        return InteractionResult.FAIL;
                    }

                    if (this.swapItem(player, enumitemslot, itemstack, hand)) {
                        return InteractionResult.SUCCESS;
                    }
                }

                return InteractionResult.PASS;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    private net.minecraft.world.entity.EquipmentSlot getClickedSlot(Vec3 hitPos) {
        net.minecraft.world.entity.EquipmentSlot enumitemslot = net.minecraft.world.entity.EquipmentSlot.MAINHAND;
        boolean flag = this.isSmall();
        double d0 = flag ? hitPos.y * 2.0D : hitPos.y;
        net.minecraft.world.entity.EquipmentSlot enumitemslot1 = net.minecraft.world.entity.EquipmentSlot.FEET;

        if (d0 >= 0.1D && d0 < 0.1D + (flag ? 0.8D : 0.45D) && this.hasItemInSlot(enumitemslot1)) {
            enumitemslot = net.minecraft.world.entity.EquipmentSlot.FEET;
        } else if (d0 >= 0.9D + (flag ? 0.3D : 0.0D) && d0 < 0.9D + (flag ? 1.0D : 0.7D) && this.hasItemInSlot(net.minecraft.world.entity.EquipmentSlot.CHEST)) {
            enumitemslot = net.minecraft.world.entity.EquipmentSlot.CHEST;
        } else if (d0 >= 0.4D && d0 < 0.4D + (flag ? 1.0D : 0.8D) && this.hasItemInSlot(net.minecraft.world.entity.EquipmentSlot.LEGS)) {
            enumitemslot = net.minecraft.world.entity.EquipmentSlot.LEGS;
        } else if (d0 >= 1.6D && this.hasItemInSlot(net.minecraft.world.entity.EquipmentSlot.HEAD)) {
            enumitemslot = net.minecraft.world.entity.EquipmentSlot.HEAD;
        } else if (!this.hasItemInSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND) && this.hasItemInSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND)) {
            enumitemslot = net.minecraft.world.entity.EquipmentSlot.OFFHAND;
        }

        return enumitemslot;
    }

    public boolean isDisabled(net.minecraft.world.entity.EquipmentSlot slot) {
        return (this.disabledSlots & 1 << slot.getFilterFlag()) != 0 || slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HAND && !this.isShowArms();
    }

    private boolean swapItem(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.EquipmentSlot slot, ItemStack stack, InteractionHand hand) {
        ItemStack itemstack1 = this.getItemBySlot(slot);

        if (!itemstack1.isEmpty() && (this.disabledSlots & 1 << slot.getFilterFlag() + 8) != 0) {
            return false;
        } else if (itemstack1.isEmpty() && (this.disabledSlots & 1 << slot.getFilterFlag() + 16) != 0) {
            return false;
            // CraftBukkit start
        } else {
            org.bukkit.inventory.ItemStack armorStandItem = CraftItemStack.asCraftMirror(itemstack1);
            org.bukkit.inventory.ItemStack playerHeldItem = CraftItemStack.asCraftMirror(stack);

            Player player1 = (Player) player.getBukkitEntity();
            org.bukkit.entity.ArmorStand self = (org.bukkit.entity.ArmorStand) this.getBukkitEntity();

            EquipmentSlot slot1 = CraftEquipmentSlot.getSlot(slot);
            EquipmentSlot hand1 = CraftEquipmentSlot.getHand(hand);
            PlayerArmorStandManipulateEvent armorStandManipulateEvent = new PlayerArmorStandManipulateEvent(player1, self, playerHeldItem, armorStandItem, slot1, hand1);
            this.level().getCraftServer().getPluginManager().callEvent(armorStandManipulateEvent);

            if (armorStandManipulateEvent.isCancelled()) {
                return true;
            }

        if (player.getAbilities().instabuild && itemstack1.isEmpty() && !stack.isEmpty()) {
            // CraftBukkit end
            this.setItemSlot(slot, stack.copyWithCount(1));
            return true;
        } else if (!stack.isEmpty() && stack.getCount() > 1) {
            if (!itemstack1.isEmpty()) {
                return false;
            } else {
                this.setItemSlot(slot, stack.split(1));
                return true;
            }
        } else {
            this.setItemSlot(slot, stack);
            player.setItemInHand(hand, itemstack1);
            return true;
        }
        } // CraftBukkit
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && !this.isRemoved()) {
            if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                // CraftBukkit start
                if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, source, amount)) {
                    return false;
                }
                // CraftBukkit end
                this.kill();
                return false;
            } else if (!this.isInvulnerableTo(source) && (true || !this.invisible) && !this.isMarker()) { // CraftBukkit
                // CraftBukkit start
                if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, source, amount, true, this.invisible)) {
                    return false;
                }
                // CraftBukkit end
                if (source.is(DamageTypeTags.IS_EXPLOSION)) {
                    // Paper start - avoid duplicate event call
                    org.bukkit.event.entity.EntityDeathEvent event = this.brokenByAnything(source);
                    if (!event.isCancelled()) this.kill(false);
                    // Paper end
                    return false;
                } else if (source.is(DamageTypeTags.IGNITES_ARMOR_STANDS)) {
                    if (this.isOnFire()) {
                        this.causeDamage(source, 0.15F);
                    } else {
                        this.setSecondsOnFire(5);
                    }

                    return false;
                } else if (source.is(DamageTypeTags.BURNS_ARMOR_STANDS) && this.getHealth() > 0.5F) {
                    this.causeDamage(source, 4.0F);
                    return false;
                } else {
                    boolean flag = "player".equals(source.getMsgId());
                    boolean flag1 = source.is(DamageTypeTags.ALWAYS_KILLS_ARMOR_STANDS);

                    if (!flag && !flag1) {
                        return false;
                    } else {
                        Entity entity = source.getEntity();

                        if (entity instanceof net.minecraft.world.entity.player.Player) {
                            net.minecraft.world.entity.player.Player entityhuman = (net.minecraft.world.entity.player.Player) entity;

                            if (!entityhuman.getAbilities().mayBuild) {
                                return false;
                            }
                        }

                        if (source.isCreativePlayer()) {
                            this.playBrokenSound();
                            this.showBreakingParticles();
                            this.kill();
                            entity = source.getDirectEntity();
                            boolean flag2;

                            if (entity instanceof AbstractArrow) {
                                AbstractArrow entityarrow = (AbstractArrow) entity;

                                if (entityarrow.getPierceLevel() > 0) {
                                    flag2 = true;
                                    return flag2;
                                }
                            }

                            flag2 = false;
                            return flag2;
                        } else {
                            long i = this.level().getGameTime();

                            if (i - this.lastHit > 5L && !flag1) {
                                this.level().broadcastEntityEvent(this, (byte) 32);
                                this.gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
                                this.lastHit = i;
                            } else {
                                org.bukkit.event.entity.EntityDeathEvent event = this.brokenByPlayer(source); // Paper
                                this.showBreakingParticles();
                                if (!event.isCancelled()) this.kill(false); // Paper - we still need to kill to follow vanilla logic (emit the game event etc...)
                            }

                            return true;
                        }
                    }
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 32) {
            if (this.level().isClientSide) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_STAND_HIT, this.getSoundSource(), 0.3F, 1.0F, false);
                this.lastHit = this.level().getGameTime();
            }
        } else {
            super.handleEntityEvent(status);
        }

    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d1 = this.getBoundingBox().getSize() * 4.0D;

        if (Double.isNaN(d1) || d1 == 0.0D) {
            d1 = 4.0D;
        }

        d1 *= 64.0D;
        return distance < d1 * d1;
    }

    private void showBreakingParticles() {
        if (this.level() instanceof ServerLevel) {
            ((ServerLevel) this.level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()), this.getX(), this.getY(0.6666666666666666D), this.getZ(), 10, (double) (this.getBbWidth() / 4.0F), (double) (this.getBbHeight() / 4.0F), (double) (this.getBbWidth() / 4.0F), 0.05D);
        }

    }

    private void causeDamage(DamageSource damageSource, float amount) {
        float f1 = this.getHealth();

        f1 -= amount;
        if (f1 <= 0.5F) {
            // Paper start - avoid duplicate event call
            org.bukkit.event.entity.EntityDeathEvent event = this.brokenByAnything(damageSource);
            if (!event.isCancelled()) this.kill(false);
            // Paper end
        } else {
            this.setHealth(f1);
            this.gameEvent(GameEvent.ENTITY_DAMAGE, damageSource.getEntity());
        }

    }

    private org.bukkit.event.entity.EntityDeathEvent brokenByPlayer(DamageSource damageSource) { // Paper
        ItemStack itemstack = new ItemStack(Items.ARMOR_STAND);

        if (this.hasCustomName()) {
            itemstack.setHoverName(this.getCustomName());
        }

        this.drops.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(itemstack)); // CraftBukkit - add to drops
        return this.brokenByAnything(damageSource); // Paper
    }

    private org.bukkit.event.entity.EntityDeathEvent brokenByAnything(DamageSource damageSource) { // Paper
        this.playBrokenSound();
        // this.dropAllDeathLoot(damagesource); // CraftBukkit - moved down

        ItemStack itemstack;
        int i;

        for (i = 0; i < this.handItems.size(); ++i) {
            itemstack = (ItemStack) this.handItems.get(i);
            if (!itemstack.isEmpty()) {
                this.drops.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemstack)); // CraftBukkit - add to drops // Paper - mirror so we can destroy it later - though this call site was safe
                this.handItems.set(i, ItemStack.EMPTY);
            }
        }

        for (i = 0; i < this.armorItems.size(); ++i) {
            itemstack = (ItemStack) this.armorItems.get(i);
            if (!itemstack.isEmpty()) {
                this.drops.add(org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemstack)); // CraftBukkit - add to drops // Paper - mirror so we can destroy it later - though this call site was safe
                this.armorItems.set(i, ItemStack.EMPTY);
            }
        }
        return this.dropAllDeathLoot(damageSource); // CraftBukkit - moved from above // Paper

    }

    private void playBrokenSound() {
        this.level().playSound((net.minecraft.world.entity.player.Player) null, this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_STAND_BREAK, this.getSoundSource(), 1.0F, 1.0F);
    }

    @Override
    protected float tickHeadTurn(float bodyRotation, float headRotation) {
        this.yBodyRotO = this.yRotO;
        this.yBodyRot = this.getYRot();
        return 0.0F;
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return dimensions.height * (this.isBaby() ? 0.5F : 0.9F);
    }

    @Override
    public void travel(Vec3 movementInput) {
        if (this.hasPhysics()) {
            super.travel(movementInput);
        }
    }

    @Override
    public void setYBodyRot(float bodyYaw) {
        this.yBodyRotO = this.yRotO = bodyYaw;
        this.yHeadRotO = this.yHeadRot = bodyYaw;
    }

    @Override
    public void setYHeadRot(float headYaw) {
        this.yBodyRotO = this.yRotO = headYaw;
        this.yHeadRotO = this.yHeadRot = headYaw;
    }

    @Override
    public void tick() {
        // Paper start
        if (!this.canTick) {
            if (this.noTickPoseDirty) {
                this.noTickPoseDirty = false;
                this.updatePose();
            }

            if (this.noTickEquipmentDirty) {
                this.noTickEquipmentDirty = false;
                this.detectEquipmentUpdatesPublic();
            }

            return;
        }
        // Paper end

        super.tick();
        // Paper start - Split into separate method
        updatePose();
    }

    public void updatePose() {
        // Paper end
        Rotations vector3f = (Rotations) this.entityData.get(ArmorStand.DATA_HEAD_POSE);

        if (!this.headPose.equals(vector3f)) {
            this.setHeadPose(vector3f);
        }

        Rotations vector3f1 = (Rotations) this.entityData.get(ArmorStand.DATA_BODY_POSE);

        if (!this.bodyPose.equals(vector3f1)) {
            this.setBodyPose(vector3f1);
        }

        Rotations vector3f2 = (Rotations) this.entityData.get(ArmorStand.DATA_LEFT_ARM_POSE);

        if (!this.leftArmPose.equals(vector3f2)) {
            this.setLeftArmPose(vector3f2);
        }

        Rotations vector3f3 = (Rotations) this.entityData.get(ArmorStand.DATA_RIGHT_ARM_POSE);

        if (!this.rightArmPose.equals(vector3f3)) {
            this.setRightArmPose(vector3f3);
        }

        Rotations vector3f4 = (Rotations) this.entityData.get(ArmorStand.DATA_LEFT_LEG_POSE);

        if (!this.leftLegPose.equals(vector3f4)) {
            this.setLeftLegPose(vector3f4);
        }

        Rotations vector3f5 = (Rotations) this.entityData.get(ArmorStand.DATA_RIGHT_LEG_POSE);

        if (!this.rightLegPose.equals(vector3f5)) {
            this.setRightLegPose(vector3f5);
        }

    }

    @Override
    protected void updateInvisibilityStatus() {
        this.setInvisible(this.invisible);
    }

    @Override
    public void setInvisible(boolean invisible) {
        this.invisible = invisible;
        super.setInvisible(invisible);
    }

    @Override
    public boolean isBaby() {
        return this.isSmall();
    }

    // CraftBukkit start
    @Override
    public boolean shouldDropExperience() {
        return true; // MC-157395, SPIGOT-5193 even baby (small) armor stands should drop
    }
    // CraftBukkit end

    @Override
    public void kill() {
        // Paper start
        kill(true);
    }

    public void kill(boolean callEvent) {
        if (callEvent) {
        // Paper end
        org.bukkit.event.entity.EntityDeathEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityDeathEvent(this, this.drops); // CraftBukkit - call event // Paper - make cancellable
        if (event.isCancelled()) return; // Paper - make cancellable
        } // Paper
        this.remove(Entity.RemovalReason.KILLED);
        this.gameEvent(GameEvent.ENTITY_DIE);
    }

    @Override
    public boolean ignoreExplosion() {
        return this.isInvisible();
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return this.isMarker() ? PushReaction.IGNORE : super.getPistonPushReaction();
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return this.isMarker();
    }

    public void setSmall(boolean small) {
        this.entityData.set(ArmorStand.DATA_CLIENT_FLAGS, this.setBit((Byte) this.entityData.get(ArmorStand.DATA_CLIENT_FLAGS), 1, small));
    }

    public boolean isSmall() {
        return ((Byte) this.entityData.get(ArmorStand.DATA_CLIENT_FLAGS) & 1) != 0;
    }

    public void setShowArms(boolean showArms) {
        this.entityData.set(ArmorStand.DATA_CLIENT_FLAGS, this.setBit((Byte) this.entityData.get(ArmorStand.DATA_CLIENT_FLAGS), 4, showArms));
    }

    public boolean isShowArms() {
        return ((Byte) this.entityData.get(ArmorStand.DATA_CLIENT_FLAGS) & 4) != 0;
    }

    public void setNoBasePlate(boolean hideBasePlate) {
        this.entityData.set(ArmorStand.DATA_CLIENT_FLAGS, this.setBit((Byte) this.entityData.get(ArmorStand.DATA_CLIENT_FLAGS), 8, hideBasePlate));
    }

    public boolean isNoBasePlate() {
        return ((Byte) this.entityData.get(ArmorStand.DATA_CLIENT_FLAGS) & 8) != 0;
    }

    public void setMarker(boolean marker) {
        this.entityData.set(ArmorStand.DATA_CLIENT_FLAGS, this.setBit((Byte) this.entityData.get(ArmorStand.DATA_CLIENT_FLAGS), 16, marker));
    }

    public boolean isMarker() {
        return ((Byte) this.entityData.get(ArmorStand.DATA_CLIENT_FLAGS) & 16) != 0;
    }

    private byte setBit(byte value, int bitField, boolean set) {
        if (set) {
            value = (byte) (value | bitField);
        } else {
            value = (byte) (value & ~bitField);
        }

        return value;
    }

    public void setHeadPose(Rotations angle) {
        this.headPose = angle;
        this.entityData.set(ArmorStand.DATA_HEAD_POSE, angle);
        this.noTickPoseDirty = true; // Paper - Allow updates when not ticking
    }

    public void setBodyPose(Rotations angle) {
        this.bodyPose = angle;
        this.entityData.set(ArmorStand.DATA_BODY_POSE, angle);
        this.noTickPoseDirty = true; // Paper - Allow updates when not ticking
    }

    public void setLeftArmPose(Rotations angle) {
        this.leftArmPose = angle;
        this.entityData.set(ArmorStand.DATA_LEFT_ARM_POSE, angle);
        this.noTickPoseDirty = true; // Paper - Allow updates when not ticking
    }

    public void setRightArmPose(Rotations angle) {
        this.rightArmPose = angle;
        this.entityData.set(ArmorStand.DATA_RIGHT_ARM_POSE, angle);
        this.noTickPoseDirty = true; // Paper - Allow updates when not ticking
    }

    public void setLeftLegPose(Rotations angle) {
        this.leftLegPose = angle;
        this.entityData.set(ArmorStand.DATA_LEFT_LEG_POSE, angle);
        this.noTickPoseDirty = true; // Paper - Allow updates when not ticking
    }

    public void setRightLegPose(Rotations angle) {
        this.rightLegPose = angle;
        this.entityData.set(ArmorStand.DATA_RIGHT_LEG_POSE, angle);
        this.noTickPoseDirty = true; // Paper - Allow updates when not ticking
    }

    public Rotations getHeadPose() {
        return this.headPose;
    }

    public Rotations getBodyPose() {
        return this.bodyPose;
    }

    public Rotations getLeftArmPose() {
        return this.leftArmPose;
    }

    public Rotations getRightArmPose() {
        return this.rightArmPose;
    }

    public Rotations getLeftLegPose() {
        return this.leftLegPose;
    }

    public Rotations getRightLegPose() {
        return this.rightLegPose;
    }

    @Override
    public boolean isPickable() {
        return super.isPickable() && !this.isMarker();
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return attacker instanceof net.minecraft.world.entity.player.Player && !this.level().mayInteract((net.minecraft.world.entity.player.Player) attacker, this.blockPosition());
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public LivingEntity.Fallsounds getFallSounds() {
        return new LivingEntity.Fallsounds(SoundEvents.ARMOR_STAND_FALL, SoundEvents.ARMOR_STAND_FALL);
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ARMOR_STAND_HIT;
    }

    @Nullable
    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.ARMOR_STAND_BREAK;
    }

    @Override
    public void thunderHit(ServerLevel world, LightningBolt lightning) {}

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (ArmorStand.DATA_CLIENT_FLAGS.equals(data)) {
            this.refreshDimensions();
            this.blocksBuilding = !this.isMarker();
        }

        super.onSyncedDataUpdated(data);
    }

    @Override
    public boolean attackable() {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.getDimensionsMarker(this.isMarker());
    }

    private EntityDimensions getDimensionsMarker(boolean marker) {
        return marker ? ArmorStand.MARKER_DIMENSIONS : (this.isBaby() ? ArmorStand.BABY_DIMENSIONS : this.getType().getDimensions());
    }

    @Override
    public Vec3 getLightProbePosition(float tickDelta) {
        if (this.isMarker()) {
            AABB axisalignedbb = this.getDimensionsMarker(false).makeBoundingBox(this.position());
            BlockPos blockposition = this.blockPosition();
            int i = Integer.MIN_VALUE;
            Iterator iterator = BlockPos.betweenClosed(BlockPos.containing(axisalignedbb.minX, axisalignedbb.minY, axisalignedbb.minZ), BlockPos.containing(axisalignedbb.maxX, axisalignedbb.maxY, axisalignedbb.maxZ)).iterator();

            while (iterator.hasNext()) {
                BlockPos blockposition1 = (BlockPos) iterator.next();
                int j = Math.max(this.level().getBrightness(LightLayer.BLOCK, blockposition1), this.level().getBrightness(LightLayer.SKY, blockposition1));

                if (j == 15) {
                    return Vec3.atCenterOf(blockposition1);
                }

                if (j > i) {
                    i = j;
                    blockposition = blockposition1.immutable();
                }
            }

            return Vec3.atCenterOf(blockposition);
        } else {
            return super.getLightProbePosition(tickDelta);
        }
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.ARMOR_STAND);
    }

    @Override
    public boolean canBeSeenByAnyone() {
        return !this.isInvisible() && !this.isMarker();
    }

    // Paper start
    @Override
    public void move(net.minecraft.world.entity.MoverType type, Vec3 movement) {
        if (this.canMove) {
            super.move(type, movement);
        }
    }

    // Paper start
    @Override
    public boolean canBreatheUnderwater() { // Skips a bit of damage handling code, probably a micro-optimization
        return true;
    }
    // Paper end
    // Paper end
}
