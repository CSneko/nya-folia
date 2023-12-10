package net.minecraft.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.advancements.AdvancementVisibilityEvaluator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.GameRules;
import org.slf4j.Logger;

public class PlayerAdvancements {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
    private final PlayerList playerList;
    private final Path playerSavePath;
    private AdvancementTree tree;
    private final Map<AdvancementHolder, AdvancementProgress> progress = new LinkedHashMap();
    private final Set<AdvancementHolder> visible = new HashSet();
    private final Set<AdvancementHolder> progressChanged = new HashSet();
    private final Set<AdvancementNode> rootsToUpdate = new HashSet();
    private ServerPlayer player;
    @Nullable
    private AdvancementHolder lastSelectedTab;
    private boolean isFirstPacket = true;
    private final Codec<PlayerAdvancements.Data> codec;
    public final Map<net.minecraft.advancements.critereon.SimpleCriterionTrigger<?>, Set<CriterionTrigger.Listener<?>>> criterionData = new java.util.IdentityHashMap<>(); // Paper - fix advancement data player leakage

    public PlayerAdvancements(DataFixer dataFixer, PlayerList playerManager, ServerAdvancementManager advancementLoader, Path filePath, ServerPlayer owner) {
        this.playerList = playerManager;
        this.playerSavePath = filePath;
        this.player = owner;
        this.tree = advancementLoader.tree();
        boolean flag = true;

        this.codec = DataFixTypes.ADVANCEMENTS.wrapCodec(PlayerAdvancements.Data.CODEC, dataFixer, 1343);
        this.load(advancementLoader);
    }

    public void setPlayer(ServerPlayer owner) {
        this.player = owner;
    }

    public void stopListening() {
        Iterator iterator = CriteriaTriggers.all().iterator();

        while (iterator.hasNext()) {
            CriterionTrigger<?> criteriontrigger = (CriterionTrigger) iterator.next();

            criteriontrigger.removePlayerListeners(this);
        }

    }

    public void reload(ServerAdvancementManager advancementLoader) {
        this.stopListening();
        this.progress.clear();
        this.visible.clear();
        this.rootsToUpdate.clear();
        this.progressChanged.clear();
        this.isFirstPacket = true;
        this.lastSelectedTab = null;
        this.tree = advancementLoader.tree();
        this.load(advancementLoader);
    }

    private void registerListeners(ServerAdvancementManager advancementLoader) {
        Iterator iterator = advancementLoader.getAllAdvancements().iterator();

        while (iterator.hasNext()) {
            AdvancementHolder advancementholder = (AdvancementHolder) iterator.next();

            this.registerListeners(advancementholder);
        }

    }

    private void checkForAutomaticTriggers(ServerAdvancementManager advancementLoader) {
        Iterator iterator = advancementLoader.getAllAdvancements().iterator();

        while (iterator.hasNext()) {
            AdvancementHolder advancementholder = (AdvancementHolder) iterator.next();
            Advancement advancement = advancementholder.value();

            if (advancement.criteria().isEmpty()) {
                this.award(advancementholder, "");
                advancement.rewards().grant(this.player);
            }
        }

    }

    private void load(ServerAdvancementManager advancementLoader) {
        if (Files.isRegularFile(this.playerSavePath, new LinkOption[0])) {
            try {
                JsonReader jsonreader = new JsonReader(Files.newBufferedReader(this.playerSavePath, StandardCharsets.UTF_8));

                try {
                    jsonreader.setLenient(false);
                    JsonElement jsonelement = Streams.parse(jsonreader);
                    PlayerAdvancements.Data advancementdataplayer_a = (PlayerAdvancements.Data) Util.getOrThrow(this.codec.parse(JsonOps.INSTANCE, jsonelement), JsonParseException::new);

                    this.applyFrom(advancementLoader, advancementdataplayer_a);
                } catch (Throwable throwable) {
                    try {
                        jsonreader.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }

                    throw throwable;
                }

                jsonreader.close();
            } catch (JsonParseException jsonparseexception) {
                PlayerAdvancements.LOGGER.error("Couldn't parse player advancements in {}", this.playerSavePath, jsonparseexception);
            } catch (IOException ioexception) {
                PlayerAdvancements.LOGGER.error("Couldn't access player advancements in {}", this.playerSavePath, ioexception);
            }
        }

        this.checkForAutomaticTriggers(advancementLoader);
        this.registerListeners(advancementLoader);
    }

    public void save() {
        if (org.spigotmc.SpigotConfig.disableAdvancementSaving) return; // Spigot
        JsonElement jsonelement = (JsonElement) Util.getOrThrow(this.codec.encodeStart(JsonOps.INSTANCE, this.asData()), IllegalStateException::new);

        try {
            FileUtil.createDirectoriesSafe(this.playerSavePath.getParent());
            BufferedWriter bufferedwriter = Files.newBufferedWriter(this.playerSavePath, StandardCharsets.UTF_8);

            try {
                PlayerAdvancements.GSON.toJson(jsonelement, bufferedwriter);
            } catch (Throwable throwable) {
                if (bufferedwriter != null) {
                    try {
                        bufferedwriter.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }
                }

                throw throwable;
            }

            if (bufferedwriter != null) {
                bufferedwriter.close();
            }
        } catch (IOException ioexception) {
            PlayerAdvancements.LOGGER.error("Couldn't save player advancements to {}", this.playerSavePath, ioexception);
        }

    }

    private void applyFrom(ServerAdvancementManager loader, PlayerAdvancements.Data progressMap) {
        progressMap.forEach((minecraftkey, advancementprogress) -> {
            AdvancementHolder advancementholder = loader.get(minecraftkey);

            if (advancementholder == null) {
                if (!minecraftkey.getNamespace().equals("minecraft")) return; // CraftBukkit
                PlayerAdvancements.LOGGER.warn("Ignored advancement '{}' in progress file {} - it doesn't exist anymore?", minecraftkey, this.playerSavePath);
            } else {
                this.startProgress(advancementholder, advancementprogress);
                this.progressChanged.add(advancementholder);
                this.markForVisibilityUpdate(advancementholder);
            }
        });
    }

    private PlayerAdvancements.Data asData() {
        Map<ResourceLocation, AdvancementProgress> map = new LinkedHashMap();

        this.progress.forEach((advancementholder, advancementprogress) -> {
            if (advancementprogress.hasProgress()) {
                map.put(advancementholder.id(), advancementprogress);
            }

        });
        return new PlayerAdvancements.Data(map);
    }

    public boolean award(AdvancementHolder advancement, String criterionName) {
        boolean flag = false;
        AdvancementProgress advancementprogress = this.getOrStartProgress(advancement);
        boolean flag1 = advancementprogress.isDone();

        if (advancementprogress.grantProgress(criterionName)) {
            // Paper start
            if (!new com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent(this.player.getBukkitEntity(), advancement.toBukkit(), criterionName).callEvent()) {
                advancementprogress.revokeProgress(criterionName);
                return false;
            }
            // Paper end
            this.unregisterListeners(advancement);
            this.progressChanged.add(advancement);
            flag = true;
            if (!flag1 && advancementprogress.isDone()) {
                // Paper start - Add Adventure message to PlayerAdvancementDoneEvent
                final net.kyori.adventure.text.Component message = advancement.value().display().flatMap(info -> java.util.Optional.ofNullable(info.shouldAnnounceChat() ? io.papermc.paper.adventure.PaperAdventure.asAdventure(Component.translatable("chat.type.advancement." + info.getFrame().getName(), this.player.getDisplayName(),  Advancement.name(advancement))) : null)).orElse(null);
                final org.bukkit.event.player.PlayerAdvancementDoneEvent event = new org.bukkit.event.player.PlayerAdvancementDoneEvent(this.player.getBukkitEntity(), advancement.toBukkit(), message);
                this.player.level().getCraftServer().getPluginManager().callEvent(event); // CraftBukkit
                // Paper end
                advancement.value().rewards().grant(this.player);
                advancement.value().display().ifPresent((advancementdisplay) -> {
                    // Paper start - Add Adventure message to PlayerAdvancementDoneEvent
                    if (event.message() != null && this.player.level().getGameRules().getBoolean(GameRules.RULE_ANNOUNCE_ADVANCEMENTS)) {
                        this.playerList.broadcastSystemMessage(io.papermc.paper.adventure.PaperAdventure.asVanilla(event.message()), false);
                        // Paper end
                    }

                });
            }
        }

        if (!flag1 && advancementprogress.isDone()) {
            this.markForVisibilityUpdate(advancement);
        }

        return flag;
    }

    public boolean revoke(AdvancementHolder advancement, String criterionName) {
        boolean flag = false;
        AdvancementProgress advancementprogress = this.getOrStartProgress(advancement);
        boolean flag1 = advancementprogress.isDone();

        if (advancementprogress.revokeProgress(criterionName)) {
            this.registerListeners(advancement);
            this.progressChanged.add(advancement);
            flag = true;
        }

        if (flag1 && !advancementprogress.isDone()) {
            this.markForVisibilityUpdate(advancement);
        }

        return flag;
    }

    private void markForVisibilityUpdate(AdvancementHolder advancement) {
        AdvancementNode advancementnode = this.tree.get(advancement);

        if (advancementnode != null) {
            this.rootsToUpdate.add(advancementnode.root());
        }

    }

    private void registerListeners(AdvancementHolder advancement) {
        AdvancementProgress advancementprogress = this.getOrStartProgress(advancement);

        if (!advancementprogress.isDone()) {
            Iterator iterator = advancement.value().criteria().entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<String, Criterion<?>> entry = (Entry) iterator.next();
                CriterionProgress criterionprogress = advancementprogress.getCriterion((String) entry.getKey());

                if (criterionprogress != null && !criterionprogress.isDone()) {
                    this.registerListener(advancement, (String) entry.getKey(), (Criterion) entry.getValue());
                }
            }

        }
    }

    private <T extends CriterionTriggerInstance> void registerListener(AdvancementHolder advancement, String id, Criterion<T> criterion) {
        criterion.trigger().addPlayerListener(this, new CriterionTrigger.Listener<>(criterion.triggerInstance(), advancement, id));
    }

    private void unregisterListeners(AdvancementHolder advancement) {
        AdvancementProgress advancementprogress = this.getOrStartProgress(advancement);
        Iterator iterator = advancement.value().criteria().entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<String, Criterion<?>> entry = (Entry) iterator.next();
            CriterionProgress criterionprogress = advancementprogress.getCriterion((String) entry.getKey());

            if (criterionprogress != null && (criterionprogress.isDone() || advancementprogress.isDone())) {
                this.removeListener(advancement, (String) entry.getKey(), (Criterion) entry.getValue());
            }
        }

    }

    private <T extends CriterionTriggerInstance> void removeListener(AdvancementHolder advancement, String id, Criterion<T> criterion) {
        criterion.trigger().removePlayerListener(this, new CriterionTrigger.Listener<>(criterion.triggerInstance(), advancement, id));
    }

    public void flushDirty(ServerPlayer player) {
        if (this.isFirstPacket || !this.rootsToUpdate.isEmpty() || !this.progressChanged.isEmpty()) {
            Map<ResourceLocation, AdvancementProgress> map = new HashMap();
            Set<AdvancementHolder> set = new HashSet();
            Set<ResourceLocation> set1 = new HashSet();
            Iterator iterator = this.rootsToUpdate.iterator();

            while (iterator.hasNext()) {
                AdvancementNode advancementnode = (AdvancementNode) iterator.next();

                this.updateTreeVisibility(advancementnode, set, set1);
            }

            this.rootsToUpdate.clear();
            iterator = this.progressChanged.iterator();

            while (iterator.hasNext()) {
                AdvancementHolder advancementholder = (AdvancementHolder) iterator.next();

                if (this.visible.contains(advancementholder)) {
                    map.put(advancementholder.id(), (AdvancementProgress) this.progress.get(advancementholder));
                }
            }

            this.progressChanged.clear();
            if (!map.isEmpty() || !set.isEmpty() || !set1.isEmpty()) {
                player.connection.send(new ClientboundUpdateAdvancementsPacket(this.isFirstPacket, set, set1, map));
            }
        }

        this.isFirstPacket = false;
    }

    public void setSelectedTab(@Nullable AdvancementHolder advancement) {
        AdvancementHolder advancementholder1 = this.lastSelectedTab;

        if (advancement != null && advancement.value().isRoot() && advancement.value().display().isPresent()) {
            this.lastSelectedTab = advancement;
        } else {
            this.lastSelectedTab = null;
        }

        if (advancementholder1 != this.lastSelectedTab) {
            this.player.connection.send(new ClientboundSelectAdvancementsTabPacket(this.lastSelectedTab == null ? null : this.lastSelectedTab.id()));
        }

    }

    public AdvancementProgress getOrStartProgress(AdvancementHolder advancement) {
        AdvancementProgress advancementprogress = (AdvancementProgress) this.progress.get(advancement);

        if (advancementprogress == null) {
            advancementprogress = new AdvancementProgress();
            this.startProgress(advancement, advancementprogress);
        }

        return advancementprogress;
    }

    private void startProgress(AdvancementHolder advancement, AdvancementProgress progress) {
        progress.update(advancement.value().requirements());
        this.progress.put(advancement, progress);
    }

    private void updateTreeVisibility(AdvancementNode root, Set<AdvancementHolder> added, Set<ResourceLocation> removed) {
        AdvancementVisibilityEvaluator.evaluateVisibility(root, (advancementnode1) -> {
            return this.getOrStartProgress(advancementnode1.holder()).isDone();
        }, (advancementnode1, flag) -> {
            AdvancementHolder advancementholder = advancementnode1.holder();

            if (flag) {
                if (this.visible.add(advancementholder)) {
                    added.add(advancementholder);
                    if (this.progress.containsKey(advancementholder)) {
                        this.progressChanged.add(advancementholder);
                    }
                }
            } else if (this.visible.remove(advancementholder)) {
                removed.add(advancementholder.id());
            }

        });
    }

    private static record Data(Map<ResourceLocation, AdvancementProgress> map) {

        public static final Codec<PlayerAdvancements.Data> CODEC = Codec.unboundedMap(ResourceLocation.CODEC, AdvancementProgress.CODEC).xmap(PlayerAdvancements.Data::new, PlayerAdvancements.Data::map);

        public void forEach(BiConsumer<ResourceLocation, AdvancementProgress> consumer) {
            this.map.entrySet().stream().sorted(Entry.comparingByValue()).forEach((entry) -> {
                consumer.accept((ResourceLocation) entry.getKey(), (AdvancementProgress) entry.getValue());
            });
        }
    }
}
