package net.minecraft.world.entity;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
// CraftBukkit start
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityDamageEvent;
// CraftBukkit end

public class Interaction extends Entity implements Attackable, Targeting {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EntityDataAccessor<Float> DATA_WIDTH_ID = SynchedEntityData.defineId(Interaction.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT_ID = SynchedEntityData.defineId(Interaction.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_RESPONSE_ID = SynchedEntityData.defineId(Interaction.class, EntityDataSerializers.BOOLEAN);
    private static final String TAG_WIDTH = "width";
    private static final String TAG_HEIGHT = "height";
    private static final String TAG_ATTACK = "attack";
    private static final String TAG_INTERACTION = "interaction";
    private static final String TAG_RESPONSE = "response";
    @Nullable
    public Interaction.PlayerAction attack;
    @Nullable
    public Interaction.PlayerAction interaction;

    public Interaction(EntityType<?> type, Level world) {
        super(type, world);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(Interaction.DATA_WIDTH_ID, 1.0F);
        this.entityData.define(Interaction.DATA_HEIGHT_ID, 1.0F);
        this.entityData.define(Interaction.DATA_RESPONSE_ID, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        if (nbt.contains("width", 99)) {
            this.setWidth(nbt.getFloat("width"));
        }

        if (nbt.contains("height", 99)) {
            this.setHeight(nbt.getFloat("height"));
        }

        DataResult<com.mojang.datafixers.util.Pair<Interaction.PlayerAction, net.minecraft.nbt.Tag>> dataresult; // CraftBukkit - decompile error
        Logger logger;

        if (nbt.contains("attack")) {
            dataresult = Interaction.PlayerAction.CODEC.decode(NbtOps.INSTANCE, nbt.get("attack"));
            logger = Interaction.LOGGER;
            Objects.requireNonNull(logger);
            dataresult.resultOrPartial(Util.prefix("Interaction entity", logger::error)).ifPresent((pair) -> {
                this.attack = (Interaction.PlayerAction) pair.getFirst();
            });
        } else {
            this.attack = null;
        }

        if (nbt.contains("interaction")) {
            dataresult = Interaction.PlayerAction.CODEC.decode(NbtOps.INSTANCE, nbt.get("interaction"));
            logger = Interaction.LOGGER;
            Objects.requireNonNull(logger);
            dataresult.resultOrPartial(Util.prefix("Interaction entity", logger::error)).ifPresent((pair) -> {
                this.interaction = (Interaction.PlayerAction) pair.getFirst();
            });
        } else {
            this.interaction = null;
        }

        this.setResponse(nbt.getBoolean("response"));
        this.setBoundingBox(this.makeBoundingBox());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putFloat("width", this.getWidth());
        nbt.putFloat("height", this.getHeight());
        if (this.attack != null) {
            Interaction.PlayerAction.CODEC.encodeStart(NbtOps.INSTANCE, this.attack).result().ifPresent((nbtbase) -> {
                nbt.put("attack", nbtbase);
            });
        }

        if (this.interaction != null) {
            Interaction.PlayerAction.CODEC.encodeStart(NbtOps.INSTANCE, this.interaction).result().ifPresent((nbtbase) -> {
                nbt.put("interaction", nbtbase);
            });
        }

        nbt.putBoolean("response", this.getResponse());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
        if (Interaction.DATA_HEIGHT_ID.equals(data) || Interaction.DATA_WIDTH_ID.equals(data)) {
            this.setBoundingBox(this.makeBoundingBox());
        }

    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (attacker instanceof Player) {
            Player entityhuman = (Player) attacker;
            // CraftBukkit start
            DamageSource source = entityhuman.damageSources().playerAttack(entityhuman);
            EntityDamageEvent event = CraftEventFactory.callNonLivingEntityDamageEvent(this, source, 1.0F, false);
            if (event.isCancelled()) {
                return true;
            }
            // CraftBukkit end

            this.attack = new Interaction.PlayerAction(entityhuman.getUUID(), this.level().getGameTime());
            if (entityhuman instanceof ServerPlayer) {
                ServerPlayer entityplayer = (ServerPlayer) entityhuman;

                CriteriaTriggers.PLAYER_HURT_ENTITY.trigger(entityplayer, this, entityhuman.damageSources().generic(), 1.0F, (float) event.getFinalDamage(), false); // CraftBukkit // Paper - use correct source and fix taken/dealt param order
            }

            return !this.getResponse();
        } else {
            return false;
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return this.getResponse() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        } else {
            this.interaction = new Interaction.PlayerAction(player.getUUID(), this.level().getGameTime());
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public void tick() {}

    @Nullable
    @Override
    public LivingEntity getLastAttacker() {
        return this.attack != null ? this.level().getPlayerByUUID(this.attack.player()) : null;
    }

    @Nullable
    @Override
    public LivingEntity getTarget() {
        return this.interaction != null ? this.level().getPlayerByUUID(this.interaction.player()) : null;
    }

    public void setWidth(float width) {
        this.entityData.set(Interaction.DATA_WIDTH_ID, width);
    }

    public float getWidth() {
        return (Float) this.entityData.get(Interaction.DATA_WIDTH_ID);
    }

    public void setHeight(float height) {
        this.entityData.set(Interaction.DATA_HEIGHT_ID, height);
    }

    public float getHeight() {
        return (Float) this.entityData.get(Interaction.DATA_HEIGHT_ID);
    }

    public void setResponse(boolean response) {
        this.entityData.set(Interaction.DATA_RESPONSE_ID, response);
    }

    public boolean getResponse() {
        return (Boolean) this.entityData.get(Interaction.DATA_RESPONSE_ID);
    }

    private EntityDimensions getDimensions() {
        return EntityDimensions.scalable(this.getWidth(), this.getHeight());
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.getDimensions();
    }

    @Override
    protected AABB makeBoundingBox() {
        return this.getDimensions().makeBoundingBox(this.position());
    }

    public static record PlayerAction(UUID player, long timestamp) {

        public static final Codec<Interaction.PlayerAction> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(UUIDUtil.CODEC.fieldOf("player").forGetter(Interaction.PlayerAction::player), Codec.LONG.fieldOf("timestamp").forGetter(Interaction.PlayerAction::timestamp)).apply(instance, Interaction.PlayerAction::new);
        });
    }
}
