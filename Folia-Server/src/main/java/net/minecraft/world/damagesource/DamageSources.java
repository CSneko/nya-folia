package net.minecraft.world.damagesource;

import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;

public class DamageSources {

    private final Registry<DamageType> damageTypes;
    private final DamageSource inFire;
    private final DamageSource lightningBolt;
    private final DamageSource onFire;
    private final DamageSource lava;
    private final DamageSource hotFloor;
    private final DamageSource inWall;
    private final DamageSource cramming;
    private final DamageSource drown;
    private final DamageSource starve;
    private final DamageSource cactus;
    private final DamageSource fall;
    private final DamageSource flyIntoWall;
    private final DamageSource fellOutOfWorld;
    private final DamageSource generic;
    private final DamageSource magic;
    private final DamageSource wither;
    private final DamageSource dragonBreath;
    private final DamageSource dryOut;
    private final DamageSource sweetBerryBush;
    private final DamageSource freeze;
    private final DamageSource stalagmite;
    private final DamageSource outsideBorder;
    private final DamageSource genericKill;
    // CraftBukkit start
    public final DamageSource melting;
    public final DamageSource poison;

    public DamageSources(RegistryAccess registryManager) {
        this.damageTypes = registryManager.registryOrThrow(Registries.DAMAGE_TYPE);
        this.melting = this.source(DamageTypes.ON_FIRE).melting();
        this.poison = this.source(DamageTypes.MAGIC).poison();
        // CraftBukkit end
        this.inFire = this.source(DamageTypes.IN_FIRE);
        this.lightningBolt = this.source(DamageTypes.LIGHTNING_BOLT);
        this.onFire = this.source(DamageTypes.ON_FIRE);
        this.lava = this.source(DamageTypes.LAVA);
        this.hotFloor = this.source(DamageTypes.HOT_FLOOR);
        this.inWall = this.source(DamageTypes.IN_WALL);
        this.cramming = this.source(DamageTypes.CRAMMING);
        this.drown = this.source(DamageTypes.DROWN);
        this.starve = this.source(DamageTypes.STARVE);
        this.cactus = this.source(DamageTypes.CACTUS);
        this.fall = this.source(DamageTypes.FALL);
        this.flyIntoWall = this.source(DamageTypes.FLY_INTO_WALL);
        this.fellOutOfWorld = this.source(DamageTypes.FELL_OUT_OF_WORLD);
        this.generic = this.source(DamageTypes.GENERIC);
        this.magic = this.source(DamageTypes.MAGIC);
        this.wither = this.source(DamageTypes.WITHER);
        this.dragonBreath = this.source(DamageTypes.DRAGON_BREATH);
        this.dryOut = this.source(DamageTypes.DRY_OUT);
        this.sweetBerryBush = this.source(DamageTypes.SWEET_BERRY_BUSH);
        this.freeze = this.source(DamageTypes.FREEZE);
        this.stalagmite = this.source(DamageTypes.STALAGMITE);
        this.outsideBorder = this.source(DamageTypes.OUTSIDE_BORDER);
        this.genericKill = this.source(DamageTypes.GENERIC_KILL);
    }

    private DamageSource source(ResourceKey<DamageType> key) {
        return new DamageSource(this.damageTypes.getHolderOrThrow(key));
    }

    private DamageSource source(ResourceKey<DamageType> key, @Nullable Entity attacker) {
        return new DamageSource(this.damageTypes.getHolderOrThrow(key), attacker);
    }

    private DamageSource source(ResourceKey<DamageType> key, @Nullable Entity source, @Nullable Entity attacker) {
        return new DamageSource(this.damageTypes.getHolderOrThrow(key), source, attacker);
    }

    public DamageSource inFire() {
        return this.inFire;
    }

    public DamageSource lightningBolt() {
        return this.lightningBolt;
    }

    public DamageSource onFire() {
        return this.onFire;
    }

    public DamageSource lava() {
        return this.lava;
    }

    public DamageSource hotFloor() {
        return this.hotFloor;
    }

    public DamageSource inWall() {
        return this.inWall;
    }

    public DamageSource cramming() {
        return this.cramming;
    }

    public DamageSource drown() {
        return this.drown;
    }

    public DamageSource starve() {
        return this.starve;
    }

    public DamageSource cactus() {
        return this.cactus;
    }

    public DamageSource fall() {
        return this.fall;
    }

    public DamageSource flyIntoWall() {
        return this.flyIntoWall;
    }

    public DamageSource fellOutOfWorld() {
        return this.fellOutOfWorld;
    }

    public DamageSource generic() {
        return this.generic;
    }

    public DamageSource magic() {
        return this.magic;
    }

    public DamageSource wither() {
        return this.wither;
    }

    public DamageSource dragonBreath() {
        return this.dragonBreath;
    }

    public DamageSource dryOut() {
        return this.dryOut;
    }

    public DamageSource sweetBerryBush() {
        return this.sweetBerryBush;
    }

    public DamageSource freeze() {
        return this.freeze;
    }

    public DamageSource stalagmite() {
        return this.stalagmite;
    }

    public DamageSource fallingBlock(Entity attacker) {
        return this.source(DamageTypes.FALLING_BLOCK, attacker);
    }

    public DamageSource anvil(Entity attacker) {
        return this.source(DamageTypes.FALLING_ANVIL, attacker);
    }

    public DamageSource fallingStalactite(Entity attacker) {
        return this.source(DamageTypes.FALLING_STALACTITE, attacker);
    }

    public DamageSource sting(LivingEntity attacker) {
        return this.source(DamageTypes.STING, attacker);
    }

    public DamageSource mobAttack(LivingEntity attacker) {
        return this.source(DamageTypes.MOB_ATTACK, attacker);
    }

    public DamageSource noAggroMobAttack(LivingEntity attacker) {
        return this.source(DamageTypes.MOB_ATTACK_NO_AGGRO, attacker);
    }

    public DamageSource playerAttack(Player attacker) {
        return this.source(DamageTypes.PLAYER_ATTACK, attacker);
    }

    public DamageSource arrow(AbstractArrow source, @Nullable Entity attacker) {
        return this.source(DamageTypes.ARROW, source, attacker);
    }

    public DamageSource trident(Entity source, @Nullable Entity attacker) {
        return this.source(DamageTypes.TRIDENT, source, attacker);
    }

    public DamageSource mobProjectile(Entity source, @Nullable LivingEntity attacker) {
        return this.source(DamageTypes.MOB_PROJECTILE, source, attacker);
    }

    public DamageSource fireworks(FireworkRocketEntity source, @Nullable Entity attacker) {
        return this.source(DamageTypes.FIREWORKS, source, attacker);
    }

    public DamageSource fireball(Fireball source, @Nullable Entity attacker) {
        return attacker == null ? this.source(DamageTypes.UNATTRIBUTED_FIREBALL, source) : this.source(DamageTypes.FIREBALL, source, attacker);
    }

    public DamageSource witherSkull(WitherSkull source, Entity attacker) {
        return this.source(DamageTypes.WITHER_SKULL, source, attacker);
    }

    public DamageSource thrown(Entity source, @Nullable Entity attacker) {
        return this.source(DamageTypes.THROWN, source, attacker);
    }

    public DamageSource indirectMagic(Entity source, @Nullable Entity attacker) {
        return this.source(DamageTypes.INDIRECT_MAGIC, source, attacker);
    }

    public DamageSource thorns(Entity attacker) {
        return this.source(DamageTypes.THORNS, attacker);
    }

    public DamageSource explosion(@Nullable Explosion explosion) {
        return explosion != null ? this.explosion(explosion.getDirectSourceEntity(), explosion.getIndirectSourceEntity()) : this.explosion((Entity) null, (Entity) null);
    }

    public DamageSource explosion(@Nullable Entity source, @Nullable Entity attacker) {
        return this.source(attacker != null && source != null ? DamageTypes.PLAYER_EXPLOSION : DamageTypes.EXPLOSION, source, attacker);
    }

    public DamageSource sonicBoom(Entity attacker) {
        return this.source(DamageTypes.SONIC_BOOM, attacker);
    }

    @Deprecated @io.papermc.paper.annotation.DoNotUse // Paper
    public DamageSource badRespawnPointExplosion(Vec3 position) {
        // Paper start
        return this.badRespawnPointExplosion(position, null);
    }

    public DamageSource badRespawnPointExplosion(Vec3 position, @Nullable org.bukkit.block.BlockState explodedBlockState) {
        DamageSource source = new DamageSource(this.damageTypes.getHolderOrThrow(DamageTypes.BAD_RESPAWN_POINT), position);
        source.explodedBlockState = explodedBlockState;
        return source;
        // Paper end
    }

    public DamageSource outOfBorder() {
        return this.outsideBorder;
    }

    public DamageSource genericKill() {
        return this.genericKill;
    }
}
