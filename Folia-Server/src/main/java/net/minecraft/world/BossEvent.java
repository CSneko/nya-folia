package net.minecraft.world;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public abstract class BossEvent {
    private final UUID id;
    public Component name;
    protected float progress;
    public BossEvent.BossBarColor color;
    public BossEvent.BossBarOverlay overlay;
    protected boolean darkenScreen;
    protected boolean playBossMusic;
    protected boolean createWorldFog;
    public net.kyori.adventure.bossbar.BossBar adventure; // Paper

    public BossEvent(UUID uuid, Component name, BossEvent.BossBarColor color, BossEvent.BossBarOverlay style) {
        this.id = uuid;
        this.name = name;
        this.color = color;
        this.overlay = style;
        this.progress = 1.0F;
    }

    public UUID getId() {
        return this.id;
    }

    public Component getName() {
        if (this.adventure != null) return io.papermc.paper.adventure.PaperAdventure.asVanilla(this.adventure.name()); // Paper
        return this.name;
    }

    public void setName(Component name) {
        if (this.adventure != null) this.adventure.name(io.papermc.paper.adventure.PaperAdventure.asAdventure(name)); // Paper
        this.name = name;
    }

    public float getProgress() {
        if (this.adventure != null) return this.adventure.progress(); // Paper
        return this.progress;
    }

    public void setProgress(float percent) {
        if (this.adventure != null) this.adventure.progress(percent); // Paper
        this.progress = percent;
    }

    public BossEvent.BossBarColor getColor() {
        if (this.adventure != null) return io.papermc.paper.adventure.PaperAdventure.asVanilla(this.adventure.color()); // Paper
        return this.color;
    }

    public void setColor(BossEvent.BossBarColor color) {
        if (this.adventure != null) this.adventure.color(io.papermc.paper.adventure.PaperAdventure.asAdventure(color)); // Paper
        this.color = color;
    }

    public BossEvent.BossBarOverlay getOverlay() {
        if (this.adventure != null) return io.papermc.paper.adventure.PaperAdventure.asVanilla(this.adventure.overlay()); // Paper
        return this.overlay;
    }

    public void setOverlay(BossEvent.BossBarOverlay style) {
        if (this.adventure != null) this.adventure.overlay(io.papermc.paper.adventure.PaperAdventure.asAdventure(style)); // Paper
        this.overlay = style;
    }

    public boolean shouldDarkenScreen() {
        if (this.adventure != null) return this.adventure.hasFlag(net.kyori.adventure.bossbar.BossBar.Flag.DARKEN_SCREEN); // Paper
        return this.darkenScreen;
    }

    public BossEvent setDarkenScreen(boolean darkenSky) {
        if (this.adventure != null) io.papermc.paper.adventure.PaperAdventure.setFlag(this.adventure, net.kyori.adventure.bossbar.BossBar.Flag.DARKEN_SCREEN, darkenSky); // Paper
        this.darkenScreen = darkenSky;
        return this;
    }

    public boolean shouldPlayBossMusic() {
        if (this.adventure != null) return this.adventure.hasFlag(net.kyori.adventure.bossbar.BossBar.Flag.PLAY_BOSS_MUSIC); // Paper
        return this.playBossMusic;
    }

    public BossEvent setPlayBossMusic(boolean dragonMusic) {
        if (this.adventure != null) io.papermc.paper.adventure.PaperAdventure.setFlag(this.adventure, net.kyori.adventure.bossbar.BossBar.Flag.PLAY_BOSS_MUSIC, dragonMusic); // Paper
        this.playBossMusic = dragonMusic;
        return this;
    }

    public BossEvent setCreateWorldFog(boolean thickenFog) {
        if (this.adventure != null) io.papermc.paper.adventure.PaperAdventure.setFlag(this.adventure, net.kyori.adventure.bossbar.BossBar.Flag.CREATE_WORLD_FOG, thickenFog); // Paper
        this.createWorldFog = thickenFog;
        return this;
    }

    public boolean shouldCreateWorldFog() {
        if (this.adventure != null) return this.adventure.hasFlag(net.kyori.adventure.bossbar.BossBar.Flag.CREATE_WORLD_FOG); // Paper
        return this.createWorldFog;
    }

    public static enum BossBarColor {
        PINK("pink", ChatFormatting.RED),
        BLUE("blue", ChatFormatting.BLUE),
        RED("red", ChatFormatting.DARK_RED),
        GREEN("green", ChatFormatting.GREEN),
        YELLOW("yellow", ChatFormatting.YELLOW),
        PURPLE("purple", ChatFormatting.DARK_BLUE),
        WHITE("white", ChatFormatting.WHITE);

        private final String name;
        private final ChatFormatting formatting;

        private BossBarColor(String name, ChatFormatting format) {
            this.name = name;
            this.formatting = format;
        }

        public ChatFormatting getFormatting() {
            return this.formatting;
        }

        public String getName() {
            return this.name;
        }

        public static BossEvent.BossBarColor byName(String name) {
            for(BossEvent.BossBarColor bossBarColor : values()) {
                if (bossBarColor.name.equals(name)) {
                    return bossBarColor;
                }
            }

            return WHITE;
        }
    }

    public static enum BossBarOverlay {
        PROGRESS("progress"),
        NOTCHED_6("notched_6"),
        NOTCHED_10("notched_10"),
        NOTCHED_12("notched_12"),
        NOTCHED_20("notched_20");

        private final String name;

        private BossBarOverlay(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public static BossEvent.BossBarOverlay byName(String name) {
            for(BossEvent.BossBarOverlay bossBarOverlay : values()) {
                if (bossBarOverlay.name.equals(name)) {
                    return bossBarOverlay;
                }
            }

            return PROGRESS;
        }
    }
}
