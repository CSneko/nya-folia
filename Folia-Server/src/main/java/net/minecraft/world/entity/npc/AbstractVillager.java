package net.minecraft.world.entity.npc;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.inventory.CraftMerchant;
import org.bukkit.craftbukkit.inventory.CraftMerchantRecipe;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
// CraftBukkit end

public abstract class AbstractVillager extends AgeableMob implements InventoryCarrier, Npc, Merchant {

    // CraftBukkit start
    private CraftMerchant craftMerchant;

    @Override
    public CraftMerchant getCraftMerchant() {
        return (this.craftMerchant == null) ? this.craftMerchant = new CraftMerchant(this) : this.craftMerchant;
    }
    // CraftBukkit end
    private static final EntityDataAccessor<Integer> DATA_UNHAPPY_COUNTER = SynchedEntityData.defineId(AbstractVillager.class, EntityDataSerializers.INT);
    public static final int VILLAGER_SLOT_OFFSET = 300;
    private static final int VILLAGER_INVENTORY_SIZE = 8;
    @Nullable
    private Player tradingPlayer;
    @Nullable
    protected MerchantOffers offers;
    private final SimpleContainer inventory = new SimpleContainer(8, (org.bukkit.craftbukkit.entity.CraftAbstractVillager) this.getBukkitEntity()); // CraftBukkit add argument

    public AbstractVillager(EntityType<? extends AbstractVillager> type, Level world) {
        super(type, world);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData, @Nullable CompoundTag entityNbt) {
        if (entityData == null) {
            entityData = new AgeableMob.AgeableMobGroupData(false);
        }

        return super.finalizeSpawn(world, difficulty, spawnReason, (SpawnGroupData) entityData, entityNbt);
    }

    public int getUnhappyCounter() {
        return (Integer) this.entityData.get(AbstractVillager.DATA_UNHAPPY_COUNTER);
    }

    public void setUnhappyCounter(int ticks) {
        this.entityData.set(AbstractVillager.DATA_UNHAPPY_COUNTER, ticks);
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return this.isBaby() ? 0.81F : 1.62F;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(AbstractVillager.DATA_UNHAPPY_COUNTER, 0);
    }

    @Override
    public void setTradingPlayer(@Nullable Player customer) {
        this.tradingPlayer = customer;
    }

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    public boolean isTrading() {
        return this.tradingPlayer != null;
    }

    // Paper start
    public void resetOffers() {
        this.offers = new MerchantOffers();
        this.updateTrades();
    }
    // Paper end

    @Override
    public MerchantOffers getOffers() {
        if (this.offers == null) {
            this.offers = new MerchantOffers();
            this.updateTrades();
        }

        return this.offers;
    }

    @Override
    public void overrideOffers(@Nullable MerchantOffers offers) {}

    @Override
    public void overrideXp(int experience) {}

    // Paper start
    @Override
    public void processTrade(MerchantOffer recipe, @Nullable io.papermc.paper.event.player.PlayerPurchaseEvent event) { // The MerchantRecipe passed in here is the one set by the PlayerPurchaseEvent
        if (event == null || event.willIncreaseTradeUses()) {
            recipe.increaseUses();
        }
        if (event == null || event.isRewardingExp()) {
            this.rewardTradeXp(recipe);
        }
        this.notifyTrade(recipe);
    }
    // Paper end

    @Override
    public void notifyTrade(MerchantOffer offer) {
        // offer.increaseUses(); // Paper - handled in processTrade
        this.ambientSoundTime = -this.getAmbientSoundInterval();
        // this.rewardTradeXp(offer); // Paper - handled in processTrade
        if (this.tradingPlayer instanceof ServerPlayer) {
            CriteriaTriggers.TRADE.trigger((ServerPlayer) this.tradingPlayer, this, offer.getResult());
        }

    }

    protected abstract void rewardTradeXp(MerchantOffer offer);

    @Override
    public boolean showProgressBar() {
        return true;
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
        if (!this.level().isClientSide && this.ambientSoundTime > -this.getAmbientSoundInterval() + 20) {
            this.ambientSoundTime = -this.getAmbientSoundInterval();
            this.playSound(this.getTradeUpdatedSound(!stack.isEmpty()), this.getSoundVolume(), this.getVoicePitch());
        }

    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    protected SoundEvent getTradeUpdatedSound(boolean sold) {
        return sold ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO;
    }

    public void playCelebrateSound() {
        this.playSound(SoundEvents.VILLAGER_CELEBRATE, this.getSoundVolume(), this.getVoicePitch());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        MerchantOffers merchantrecipelist = this.getOffers();

        if (!merchantrecipelist.isEmpty()) {
            nbt.put("Offers", merchantrecipelist.createTag());
        }

        this.writeInventoryToTag(nbt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("Offers", 10)) {
            this.offers = new MerchantOffers(nbt.getCompound("Offers"));
        }

        this.readInventoryFromTag(nbt);
    }

    // Folia start - region threading
    @Override
    public void preChangeDimension() {
        super.preChangeDimension();
        this.stopTrading();
    }
    // Folia end - region threading

    @Nullable
    @Override
    public Entity changeDimension(ServerLevel destination) {
        this.preChangeDimension(); // Folia - region threading - move into preChangeDimension
        return super.changeDimension(destination);
    }

    protected void stopTrading() {
        this.setTradingPlayer((Player) null);
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        this.stopTrading();
    }

    protected void addParticlesAroundSelf(ParticleOptions parameters) {
        for (int i = 0; i < 5; ++i) {
            double d0 = this.random.nextGaussian() * 0.02D;
            double d1 = this.random.nextGaussian() * 0.02D;
            double d2 = this.random.nextGaussian() * 0.02D;

            this.level().addParticle(parameters, this.getRandomX(1.0D), this.getRandomY() + 1.0D, this.getRandomZ(1.0D), d0, d1, d2);
        }

    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public SimpleContainer getInventory() {
        return this.inventory;
    }

    @Override
    public SlotAccess getSlot(int mappedIndex) {
        int j = mappedIndex - 300;

        return j >= 0 && j < this.inventory.getContainerSize() ? SlotAccess.forContainer(this.inventory, j) : super.getSlot(mappedIndex);
    }

    protected abstract void updateTrades();

    protected void addOffersFromItemListings(MerchantOffers recipeList, VillagerTrades.ItemListing[] pool, int count) {
        ArrayList<VillagerTrades.ItemListing> arraylist = Lists.newArrayList(pool);
        int j = 0;

        while (j < count && !arraylist.isEmpty()) {
            MerchantOffer merchantrecipe = ((VillagerTrades.ItemListing) arraylist.remove(this.random.nextInt(arraylist.size()))).getOffer(this, this.random);

            if (merchantrecipe != null) {
                // CraftBukkit start
                VillagerAcquireTradeEvent event = new VillagerAcquireTradeEvent((org.bukkit.entity.AbstractVillager) this.getBukkitEntity(), merchantrecipe.asBukkit());
                // Suppress during worldgen
                if (this.valid) {
                    Bukkit.getPluginManager().callEvent(event);
                }
                if (!event.isCancelled()) {
                    // Paper start
                    final CraftMerchantRecipe craftMerchantRecipe = CraftMerchantRecipe.fromBukkit(event.getRecipe());
                    if (craftMerchantRecipe.getIngredients().isEmpty()) return;
                    recipeList.add(craftMerchantRecipe.toMinecraft());
                    // Paper end
                }
                // CraftBukkit end
                ++j;
            }
        }

    }

    @Override
    public Vec3 getRopeHoldPosition(float delta) {
        float f1 = Mth.lerp(delta, this.yBodyRotO, this.yBodyRot) * 0.017453292F;
        Vec3 vec3d = new Vec3(0.0D, this.getBoundingBox().getYsize() - 1.0D, 0.2D);

        return this.getPosition(delta).add(vec3d.yRot(-f1));
    }

    @Override
    public boolean isClientSide() {
        return this.level().isClientSide;
    }
}
