package net.minecraft.world.level.gameevent.vibrations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Optional;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.VibrationParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.GameEventTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.CraftGameEvent;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.event.block.BlockReceiveGameEvent;
// CraftBukkit end

public interface VibrationSystem {

    GameEvent[] RESONANCE_EVENTS = new GameEvent[]{GameEvent.RESONATE_1, GameEvent.RESONATE_2, GameEvent.RESONATE_3, GameEvent.RESONATE_4, GameEvent.RESONATE_5, GameEvent.RESONATE_6, GameEvent.RESONATE_7, GameEvent.RESONATE_8, GameEvent.RESONATE_9, GameEvent.RESONATE_10, GameEvent.RESONATE_11, GameEvent.RESONATE_12, GameEvent.RESONATE_13, GameEvent.RESONATE_14, GameEvent.RESONATE_15};
    ToIntFunction<GameEvent> VIBRATION_FREQUENCY_FOR_EVENT = (ToIntFunction) Util.make(new Object2IntOpenHashMap(), (object2intopenhashmap) -> {
        object2intopenhashmap.defaultReturnValue(0);
        object2intopenhashmap.put(GameEvent.STEP, 1);
        object2intopenhashmap.put(GameEvent.SWIM, 1);
        object2intopenhashmap.put(GameEvent.FLAP, 1);
        object2intopenhashmap.put(GameEvent.PROJECTILE_LAND, 2);
        object2intopenhashmap.put(GameEvent.HIT_GROUND, 2);
        object2intopenhashmap.put(GameEvent.SPLASH, 2);
        object2intopenhashmap.put(GameEvent.ITEM_INTERACT_FINISH, 3);
        object2intopenhashmap.put(GameEvent.PROJECTILE_SHOOT, 3);
        object2intopenhashmap.put(GameEvent.INSTRUMENT_PLAY, 3);
        object2intopenhashmap.put(GameEvent.ENTITY_ACTION, 4);
        object2intopenhashmap.put(GameEvent.ELYTRA_GLIDE, 4);
        object2intopenhashmap.put(GameEvent.UNEQUIP, 4);
        object2intopenhashmap.put(GameEvent.ENTITY_DISMOUNT, 5);
        object2intopenhashmap.put(GameEvent.EQUIP, 5);
        object2intopenhashmap.put(GameEvent.ENTITY_INTERACT, 6);
        object2intopenhashmap.put(GameEvent.SHEAR, 6);
        object2intopenhashmap.put(GameEvent.ENTITY_MOUNT, 6);
        object2intopenhashmap.put(GameEvent.ENTITY_DAMAGE, 7);
        object2intopenhashmap.put(GameEvent.DRINK, 8);
        object2intopenhashmap.put(GameEvent.EAT, 8);
        object2intopenhashmap.put(GameEvent.CONTAINER_CLOSE, 9);
        object2intopenhashmap.put(GameEvent.BLOCK_CLOSE, 9);
        object2intopenhashmap.put(GameEvent.BLOCK_DEACTIVATE, 9);
        object2intopenhashmap.put(GameEvent.BLOCK_DETACH, 9);
        object2intopenhashmap.put(GameEvent.CONTAINER_OPEN, 10);
        object2intopenhashmap.put(GameEvent.BLOCK_OPEN, 10);
        object2intopenhashmap.put(GameEvent.BLOCK_ACTIVATE, 10);
        object2intopenhashmap.put(GameEvent.BLOCK_ATTACH, 10);
        object2intopenhashmap.put(GameEvent.PRIME_FUSE, 10);
        object2intopenhashmap.put(GameEvent.NOTE_BLOCK_PLAY, 10);
        object2intopenhashmap.put(GameEvent.BLOCK_CHANGE, 11);
        object2intopenhashmap.put(GameEvent.BLOCK_DESTROY, 12);
        object2intopenhashmap.put(GameEvent.FLUID_PICKUP, 12);
        object2intopenhashmap.put(GameEvent.BLOCK_PLACE, 13);
        object2intopenhashmap.put(GameEvent.FLUID_PLACE, 13);
        object2intopenhashmap.put(GameEvent.ENTITY_PLACE, 14);
        object2intopenhashmap.put(GameEvent.LIGHTNING_STRIKE, 14);
        object2intopenhashmap.put(GameEvent.TELEPORT, 14);
        object2intopenhashmap.put(GameEvent.ENTITY_DIE, 15);
        object2intopenhashmap.put(GameEvent.EXPLODE, 15);

        for (int i = 1; i <= 15; ++i) {
            object2intopenhashmap.put(VibrationSystem.getResonanceEventByFrequency(i), i);
        }

    });

    VibrationSystem.Data getVibrationData();

    VibrationSystem.User getVibrationUser();

    static int getGameEventFrequency(GameEvent event) {
        return VibrationSystem.VIBRATION_FREQUENCY_FOR_EVENT.applyAsInt(event);
    }

    static GameEvent getResonanceEventByFrequency(int frequency) {
        return VibrationSystem.RESONANCE_EVENTS[frequency - 1];
    }

    static int getRedstoneStrengthForDistance(float distance, int range) {
        double d0 = 15.0D / (double) range;

        return Math.max(1, 15 - Mth.floor(d0 * (double) distance));
    }

    public interface User {

        int getListenerRadius();

        PositionSource getPositionSource();

        boolean canReceiveVibration(ServerLevel world, BlockPos pos, GameEvent event, GameEvent.Context emitter);

        void onReceiveVibration(ServerLevel world, BlockPos pos, GameEvent event, @Nullable Entity sourceEntity, @Nullable Entity entity, float distance);

        default TagKey<GameEvent> getListenableEvents() {
            return GameEventTags.VIBRATIONS;
        }

        default boolean canTriggerAvoidVibration() {
            return false;
        }

        default boolean requiresAdjacentChunksToBeTicking() {
            return false;
        }

        default int calculateTravelTimeInTicks(float distance) {
            return Mth.floor(distance);
        }

        default boolean isValidVibration(GameEvent gameEvent, GameEvent.Context emitter) {
            if (!gameEvent.is(this.getListenableEvents())) {
                return false;
            } else {
                Entity entity = emitter.sourceEntity();

                if (entity != null) {
                    if (entity.isSpectator()) {
                        return false;
                    }

                    if (entity.isSteppingCarefully() && gameEvent.is(GameEventTags.IGNORE_VIBRATIONS_SNEAKING)) {
                        if (this.canTriggerAvoidVibration() && entity instanceof ServerPlayer) {
                            ServerPlayer entityplayer = (ServerPlayer) entity;

                            CriteriaTriggers.AVOID_VIBRATION.trigger(entityplayer);
                        }

                        return false;
                    }

                    if (entity.dampensVibrations()) {
                        return false;
                    }
                }

                return emitter.affectedState() != null ? !emitter.affectedState().is(BlockTags.DAMPENS_VIBRATIONS) : true;
            }
        }

        default void onDataChanged() {}
    }

    public interface Ticker {

        static void tick(Level world, VibrationSystem.Data listenerData, VibrationSystem.User callback) {
            if (world instanceof ServerLevel) {
                ServerLevel worldserver = (ServerLevel) world;

                if (listenerData.currentVibration == null) {
                    Ticker.trySelectAndScheduleVibration(worldserver, listenerData, callback);
                }

                if (listenerData.currentVibration != null) {
                    boolean flag = listenerData.getTravelTimeInTicks() > 0;

                    Ticker.tryReloadVibrationParticle(worldserver, listenerData, callback);
                    listenerData.decrementTravelTime();
                    if (listenerData.getTravelTimeInTicks() <= 0) {
                        flag = Ticker.receiveVibration(worldserver, listenerData, callback, listenerData.currentVibration);
                    }

                    if (flag) {
                        callback.onDataChanged();
                    }

                }
            }
        }

        private static void trySelectAndScheduleVibration(ServerLevel world, VibrationSystem.Data listenerData, VibrationSystem.User callback) {
            listenerData.getSelectionStrategy().chosenCandidate(world.getGameTime()).ifPresent((vibrationinfo) -> {
                listenerData.setCurrentVibration(vibrationinfo);
                Vec3 vec3d = vibrationinfo.pos();

                listenerData.setTravelTimeInTicks(callback.calculateTravelTimeInTicks(vibrationinfo.distance()));
                world.sendParticles(new VibrationParticleOption(callback.getPositionSource(), listenerData.getTravelTimeInTicks()), vec3d.x, vec3d.y, vec3d.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                callback.onDataChanged();
                listenerData.getSelectionStrategy().startOver();
            });
        }

        private static void tryReloadVibrationParticle(ServerLevel world, VibrationSystem.Data listenerData, VibrationSystem.User callback) {
            if (listenerData.shouldReloadVibrationParticle()) {
                if (listenerData.currentVibration == null) {
                    listenerData.setReloadVibrationParticle(false);
                } else {
                    Vec3 vec3d = listenerData.currentVibration.pos();
                    PositionSource positionsource = callback.getPositionSource();
                    Vec3 vec3d1 = (Vec3) positionsource.getPosition(world).orElse(vec3d);
                    int i = listenerData.getTravelTimeInTicks();
                    int j = callback.calculateTravelTimeInTicks(listenerData.currentVibration.distance());
                    double d0 = 1.0D - (double) i / (double) j;
                    double d1 = Mth.lerp(d0, vec3d.x, vec3d1.x);
                    double d2 = Mth.lerp(d0, vec3d.y, vec3d1.y);
                    double d3 = Mth.lerp(d0, vec3d.z, vec3d1.z);
                    boolean flag = world.sendParticles(new VibrationParticleOption(positionsource, i), d1, d2, d3, 1, 0.0D, 0.0D, 0.0D, 0.0D) > 0;

                    if (flag) {
                        listenerData.setReloadVibrationParticle(false);
                    }

                }
            }
        }

        private static boolean receiveVibration(ServerLevel world, VibrationSystem.Data listenerData, VibrationSystem.User callback, VibrationInfo vibration) {
            BlockPos blockposition = BlockPos.containing(vibration.pos());
            BlockPos blockposition1 = (BlockPos) callback.getPositionSource().getPosition(world).map(BlockPos::containing).orElse(blockposition);

            if (callback.requiresAdjacentChunksToBeTicking() && !Ticker.areAdjacentChunksTicking(world, blockposition1)) {
                return false;
            } else {
                // CraftBukkit - decompile error
                callback.onReceiveVibration(world, blockposition, vibration.gameEvent(), (Entity) vibration.getEntity(world).orElse(null), (Entity) vibration.getProjectileOwner(world).orElse(null), VibrationSystem.Listener.distanceBetweenInBlocks(blockposition, blockposition1));
                listenerData.setCurrentVibration((VibrationInfo) null);
                return true;
            }
        }

        private static boolean areAdjacentChunksTicking(Level world, BlockPos pos) {
            ChunkPos chunkcoordintpair = new ChunkPos(pos);

            for (int i = chunkcoordintpair.x - 1; i <= chunkcoordintpair.x + 1; ++i) {
                for (int j = chunkcoordintpair.z - 1; j <= chunkcoordintpair.z + 1; ++j) {
                    if (!world.shouldTickBlocksAt(ChunkPos.asLong(i, j)) || world.getChunkSource().getChunkNow(i, j) == null) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    public static class Listener implements GameEventListener {

        private final VibrationSystem system;

        public Listener(VibrationSystem receiver) {
            this.system = receiver;
        }

        @Override
        public PositionSource getListenerSource() {
            return this.system.getVibrationUser().getPositionSource();
        }

        @Override
        public int getListenerRadius() {
            return this.system.getVibrationUser().getListenerRadius();
        }

        @Override
        public boolean handleGameEvent(ServerLevel world, GameEvent event, GameEvent.Context emitter, Vec3 emitterPos) {
            VibrationSystem.Data vibrationsystem_a = this.system.getVibrationData();
            VibrationSystem.User vibrationsystem_d = this.system.getVibrationUser();

            if (vibrationsystem_a.getCurrentVibration() != null) {
                return false;
            } else if (!vibrationsystem_d.isValidVibration(event, emitter)) {
                return false;
            } else {
                Optional<Vec3> optional = vibrationsystem_d.getPositionSource().getPosition(world);

                if (optional.isEmpty()) {
                    return false;
                } else {
                    Vec3 vec3d1 = (Vec3) optional.get();
                    // CraftBukkit start
                    boolean defaultCancel = !vibrationsystem_d.canReceiveVibration(world, BlockPos.containing(emitterPos), event, emitter);
                    Entity entity = emitter.sourceEntity();
                    BlockReceiveGameEvent event1 = new BlockReceiveGameEvent(CraftGameEvent.minecraftToBukkit(event), CraftBlock.at(world, BlockPos.containing(vec3d1)), (entity == null) ? null : entity.getBukkitEntity());
                    event1.setCancelled(defaultCancel);
                    world.getCraftServer().getPluginManager().callEvent(event1);
                    if (event1.isCancelled()) {
                        // CraftBukkit end
                        return false;
                    } else if (Listener.isOccluded(world, emitterPos, vec3d1)) {
                        return false;
                    } else {
                        this.scheduleVibration(world, vibrationsystem_a, event, emitter, emitterPos, vec3d1);
                        return true;
                    }
                }
            }
        }

        public void forceScheduleVibration(ServerLevel world, GameEvent event, GameEvent.Context emitter, Vec3 emitterPos) {
            this.system.getVibrationUser().getPositionSource().getPosition(world).ifPresent((vec3d1) -> {
                this.scheduleVibration(world, this.system.getVibrationData(), event, emitter, emitterPos, vec3d1);
            });
        }

        private void scheduleVibration(ServerLevel world, VibrationSystem.Data listenerData, GameEvent event, GameEvent.Context emitter, Vec3 emitterPos, Vec3 listenerPos) {
            listenerData.selectionStrategy.addCandidate(new VibrationInfo(event, (float) emitterPos.distanceTo(listenerPos), emitterPos, emitter.sourceEntity()), world.getGameTime());
        }

        public static float distanceBetweenInBlocks(BlockPos emitterPos, BlockPos listenerPos) {
            return (float) Math.sqrt(emitterPos.distSqr(listenerPos));
        }

        private static boolean isOccluded(Level world, Vec3 emitterPos, Vec3 listenerPos) {
            Vec3 vec3d2 = new Vec3((double) Mth.floor(emitterPos.x) + 0.5D, (double) Mth.floor(emitterPos.y) + 0.5D, (double) Mth.floor(emitterPos.z) + 0.5D);
            Vec3 vec3d3 = new Vec3((double) Mth.floor(listenerPos.x) + 0.5D, (double) Mth.floor(listenerPos.y) + 0.5D, (double) Mth.floor(listenerPos.z) + 0.5D);
            Direction[] aenumdirection = Direction.values();
            int i = aenumdirection.length;

            for (int j = 0; j < i; ++j) {
                Direction enumdirection = aenumdirection[j];
                Vec3 vec3d4 = vec3d2.relative(enumdirection, 9.999999747378752E-6D);

                if (world.isBlockInLine(new ClipBlockStateContext(vec3d4, vec3d3, (iblockdata) -> {
                    return iblockdata.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS);
                })).getType() != HitResult.Type.BLOCK) {
                    return false;
                }
            }

            return true;
        }
    }

    public static final class Data {

        public static Codec<VibrationSystem.Data> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(VibrationInfo.CODEC.optionalFieldOf("event").forGetter((vibrationsystem_a) -> {
                return Optional.ofNullable(vibrationsystem_a.currentVibration);
            }), Codec.optionalField("selector", VibrationSelector.CODEC).xmap(o -> o.orElseGet(VibrationSelector::new), Optional::of).forGetter(VibrationSystem.Data::getSelectionStrategy), ExtraCodecs.NON_NEGATIVE_INT.fieldOf("event_delay").orElse(0).forGetter(VibrationSystem.Data::getTravelTimeInTicks)).apply(instance, (optional, vibrationselector, integer) -> { // Paper - fix MapLike spam for missing "selector" in 1.19.2
                return new VibrationSystem.Data((VibrationInfo) optional.orElse(null), vibrationselector, integer, true); // CraftBukkit - decompile error
            });
        });
        public static final String NBT_TAG_KEY = "listener";
        @Nullable
        VibrationInfo currentVibration;
        private int travelTimeInTicks;
        final VibrationSelector selectionStrategy;
        private boolean reloadVibrationParticle;

        private Data(@Nullable VibrationInfo vibration, VibrationSelector vibrationSelector, int delay, boolean spawnParticle) {
            this.currentVibration = vibration;
            this.travelTimeInTicks = delay;
            this.selectionStrategy = vibrationSelector;
            this.reloadVibrationParticle = spawnParticle;
        }

        public Data() {
            this((VibrationInfo) null, new VibrationSelector(), 0, false);
        }

        public VibrationSelector getSelectionStrategy() {
            return this.selectionStrategy;
        }

        @Nullable
        public VibrationInfo getCurrentVibration() {
            return this.currentVibration;
        }

        public void setCurrentVibration(@Nullable VibrationInfo vibration) {
            this.currentVibration = vibration;
        }

        public int getTravelTimeInTicks() {
            return this.travelTimeInTicks;
        }

        public void setTravelTimeInTicks(int delay) {
            this.travelTimeInTicks = delay;
        }

        public void decrementTravelTime() {
            this.travelTimeInTicks = Math.max(0, this.travelTimeInTicks - 1);
        }

        public boolean shouldReloadVibrationParticle() {
            return this.reloadVibrationParticle;
        }

        public void setReloadVibrationParticle(boolean spawnParticle) {
            this.reloadVibrationParticle = spawnParticle;
        }
    }
}
