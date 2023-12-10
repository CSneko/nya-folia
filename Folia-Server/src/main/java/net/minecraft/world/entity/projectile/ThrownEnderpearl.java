package net.minecraft.world.entity.projectile;

import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
// CraftBukkit end

public class ThrownEnderpearl extends ThrowableItemProjectile {

    public ThrownEnderpearl(EntityType<? extends ThrownEnderpearl> type, Level world) {
        super(type, world);
    }

    public ThrownEnderpearl(Level world, LivingEntity owner) {
        super(EntityType.ENDER_PEARL, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.ENDER_PEARL;
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        entityHitResult.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
    }

    // Folia start - region threading
    private static void attemptTeleport(Entity source, ServerLevel checkWorld, net.minecraft.world.phys.Vec3 to) {
        // ignore retired callback, in those cases we do not want to teleport
        source.getBukkitEntity().taskScheduler.schedule(
            (Entity entity) -> {
                // source is now an invalid reference, do not use it, use the entity parameter

                if (entity.level() != checkWorld) {
                    // cannot teleport cross-world
                    return;
                }
                if (entity.isVehicle()) {
                    // cannot teleport vehicles
                    return;
                }
                // dismount from any vehicles, so we can teleport and to prevent desync
                if (entity.isPassenger()) {
                    entity.stopRiding();
                }

                // reset fall damage so that if the entity was falling they do not instantly die
                entity.resetFallDistance();

                entity.teleportAsync(
                    checkWorld, to, null, null, null,
                    PlayerTeleportEvent.TeleportCause.ENDER_PEARL,
                    // chunk could have been unloaded
                    Entity.TELEPORT_FLAG_LOAD_CHUNK,
                    (Entity teleported) -> {
                        // entity is now an invalid reference, do not use it, instead use teleported
                        if (teleported instanceof ServerPlayer player) {
                            // connection teleport is already done
                            ServerLevel world = player.serverLevel();

                            // endermite spawn chance
                            if (world.random.nextFloat() < 0.05F && world.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                                Endermite entityendermite = (Endermite) EntityType.ENDERMITE.create(world);

                                if (entityendermite != null) {
                                    entityendermite.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                                    world.addFreshEntity(entityendermite, CreatureSpawnEvent.SpawnReason.ENDER_PEARL);
                                }
                            }

                            // damage player
                            player.hurt(player.damageSources().fall(), 5.0F);
                        }
                    }
                );
            },
            null,
            1L
        );
    }
    // Folia end - region threading

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);

        for (int i = 0; i < 32; ++i) {
            this.level().addParticle(ParticleTypes.PORTAL, this.getX(), this.getY() + this.random.nextDouble() * 2.0D, this.getZ(), this.random.nextGaussian(), 0.0D, this.random.nextGaussian());
        }

        if (!this.level().isClientSide && !this.isRemoved()) {
            // Folia start - region threading
            if (true) {
                // we can't fire events, because we do not actually know where the other entity is located
                if (!io.papermc.paper.util.TickThread.isTickThreadFor(this)) {
                    throw new IllegalStateException("Must be on tick thread for ticking entity: " + this);
                }
                Entity entity = this.getOwnerRaw();
                if (entity != null) {
                    attemptTeleport(entity, (ServerLevel)this.level(), this.position());
                }
                this.discard();
                return;
            }
            // Folia end - region threading
            Entity entity = this.getOwner();

            if (entity instanceof ServerPlayer) {
                ServerPlayer entityplayer = (ServerPlayer) entity;

                if (entityplayer.connection.isAcceptingMessages() && entityplayer.level() == this.level() && !entityplayer.isSleeping()) {
                    // CraftBukkit start - Fire PlayerTeleportEvent
                    org.bukkit.craftbukkit.entity.CraftPlayer player = entityplayer.getBukkitEntity();
                    org.bukkit.Location location = this.getBukkitEntity().getLocation();
                    location.setPitch(player.getLocation().getPitch());
                    location.setYaw(player.getLocation().getYaw());

                    PlayerTeleportEvent teleEvent = new PlayerTeleportEvent(player, player.getLocation(), location, PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
                    Bukkit.getPluginManager().callEvent(teleEvent);

                    if (!teleEvent.isCancelled() && entityplayer.connection.isAcceptingMessages()) {
                        if (this.random.nextFloat() < 0.05F && this.level().getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                            Endermite entityendermite = (Endermite) EntityType.ENDERMITE.create(this.level());

                            if (entityendermite != null) {
                                entityendermite.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                                this.level().addFreshEntity(entityendermite, CreatureSpawnEvent.SpawnReason.ENDER_PEARL);
                            }
                        }

                        if (entity.isPassenger()) {
                            entity.stopRiding();
                        }

                        entityplayer.connection.teleport(teleEvent.getTo());
                        entity.resetFallDistance();
                        CraftEventFactory.entityDamageRT.set(this); // Folia - region threading
                        entity.hurt(this.damageSources().fall(), 5.0F);
                        CraftEventFactory.entityDamageRT.set(null); // Folia - region threading
                    }
                    // CraftBukkit end
                }
            } else if (entity != null) {
                entity.teleportTo(this.getX(), this.getY(), this.getZ());
                entity.resetFallDistance();
            }

            this.discard();
        }

    }

    @Override
    public void tick() {
        Entity entity = this.getOwner();

        if (entity instanceof ServerPlayer && !entity.isAlive() && this.level().getGameRules().getBoolean(GameRules.RULE_ENDER_PEARLS_VANISH_ON_DEATH)) {
            this.discard();
        } else {
            super.tick();
        }

    }

    // Folia start - region threading
    @Override
    public void preChangeDimension() {
        super.preChangeDimension();
        // Don't change the owner here, since the tick logic will consider it anyways.
    }
    // Folia end - region threading

    @Nullable
    @Override
    public Entity changeDimension(ServerLevel destination) {
        Entity entity = this.getOwner();

        if (entity != null && destination != null && entity.level().dimension() != destination.dimension()) { // CraftBukkit - SPIGOT-6113
            this.setOwner((Entity) null);
        }

        return super.changeDimension(destination);
    }
}
