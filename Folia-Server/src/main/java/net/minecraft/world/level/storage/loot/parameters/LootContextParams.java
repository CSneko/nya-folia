package net.minecraft.world.level.storage.loot.parameters;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class LootContextParams {

    public static final LootContextParam<Entity> THIS_ENTITY = LootContextParams.create("this_entity");
    public static final LootContextParam<Player> LAST_DAMAGE_PLAYER = LootContextParams.create("last_damage_player");
    public static final LootContextParam<DamageSource> DAMAGE_SOURCE = LootContextParams.create("damage_source");
    public static final LootContextParam<Entity> KILLER_ENTITY = LootContextParams.create("killer_entity");
    public static final LootContextParam<Entity> DIRECT_KILLER_ENTITY = LootContextParams.create("direct_killer_entity");
    public static final LootContextParam<Vec3> ORIGIN = LootContextParams.create("origin");
    public static final LootContextParam<BlockState> BLOCK_STATE = LootContextParams.create("block_state");
    public static final LootContextParam<BlockEntity> BLOCK_ENTITY = LootContextParams.create("block_entity");
    public static final LootContextParam<ItemStack> TOOL = LootContextParams.create("tool");
    public static final LootContextParam<Float> EXPLOSION_RADIUS = LootContextParams.create("explosion_radius");
    public static final LootContextParam<Integer> LOOTING_MOD = new LootContextParam<>(new ResourceLocation("bukkit:looting_mod")); // CraftBukkit

    public LootContextParams() {}

    private static <T> LootContextParam<T> create(String name) {
        return new LootContextParam<>(new ResourceLocation(name));
    }
}
