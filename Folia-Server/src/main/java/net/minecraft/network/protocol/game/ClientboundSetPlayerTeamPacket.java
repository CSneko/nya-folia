package net.minecraft.network.protocol.game;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.scores.PlayerTeam;

public class ClientboundSetPlayerTeamPacket implements Packet<ClientGamePacketListener> {
    private static final int METHOD_ADD = 0;
    private static final int METHOD_REMOVE = 1;
    private static final int METHOD_CHANGE = 2;
    private static final int METHOD_JOIN = 3;
    private static final int METHOD_LEAVE = 4;
    private static final int MAX_VISIBILITY_LENGTH = 40;
    private static final int MAX_COLLISION_LENGTH = 40;
    private final int method;
    private final String name;
    private final Collection<String> players;
    private final Optional<ClientboundSetPlayerTeamPacket.Parameters> parameters;

    private ClientboundSetPlayerTeamPacket(String teamName, int packetType, Optional<ClientboundSetPlayerTeamPacket.Parameters> team, Collection<String> playerNames) {
        this.name = teamName;
        this.method = packetType;
        this.parameters = team;
        this.players = ImmutableList.copyOf(playerNames);
    }

    public static ClientboundSetPlayerTeamPacket createAddOrModifyPacket(PlayerTeam team, boolean updatePlayers) {
        return new ClientboundSetPlayerTeamPacket(team.getName(), updatePlayers ? 0 : 2, Optional.of(new ClientboundSetPlayerTeamPacket.Parameters(team)), (Collection<String>)(updatePlayers ? team.getPlayers() : ImmutableList.of()));
    }

    public static ClientboundSetPlayerTeamPacket createRemovePacket(PlayerTeam team) {
        return new ClientboundSetPlayerTeamPacket(team.getName(), 1, Optional.empty(), ImmutableList.of());
    }

    public static ClientboundSetPlayerTeamPacket createPlayerPacket(PlayerTeam team, String playerName, ClientboundSetPlayerTeamPacket.Action operation) {
        return new ClientboundSetPlayerTeamPacket(team.getName(), operation == ClientboundSetPlayerTeamPacket.Action.ADD ? 3 : 4, Optional.empty(), ImmutableList.of(playerName));
    }

    // Paper start
    public static ClientboundSetPlayerTeamPacket createMultiplePlayerPacket(PlayerTeam team, Collection<String> players, ClientboundSetPlayerTeamPacket.Action operation) {
        return new ClientboundSetPlayerTeamPacket(team.getName(), operation == ClientboundSetPlayerTeamPacket.Action.ADD ? 3 : 4, Optional.empty(), players);
    }
    // Paper end

    public ClientboundSetPlayerTeamPacket(FriendlyByteBuf buf) {
        this.name = buf.readUtf();
        this.method = buf.readByte();
        if (shouldHaveParameters(this.method)) {
            this.parameters = Optional.of(new ClientboundSetPlayerTeamPacket.Parameters(buf));
        } else {
            this.parameters = Optional.empty();
        }

        if (shouldHavePlayerList(this.method)) {
            this.players = buf.readList(FriendlyByteBuf::readUtf);
        } else {
            this.players = ImmutableList.of();
        }

    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.name);
        buf.writeByte(this.method);
        if (shouldHaveParameters(this.method)) {
            this.parameters.orElseThrow(() -> {
                return new IllegalStateException("Parameters not present, but method is" + this.method);
            }).write(buf);
        }

        if (shouldHavePlayerList(this.method)) {
            buf.writeCollection(this.players, FriendlyByteBuf::writeUtf);
        }

    }

    private static boolean shouldHavePlayerList(int packetType) {
        return packetType == 0 || packetType == 3 || packetType == 4;
    }

    private static boolean shouldHaveParameters(int packetType) {
        return packetType == 0 || packetType == 2;
    }

    @Nullable
    public ClientboundSetPlayerTeamPacket.Action getPlayerAction() {
        switch (this.method) {
            case 0:
            case 3:
                return ClientboundSetPlayerTeamPacket.Action.ADD;
            case 1:
            case 2:
            default:
                return null;
            case 4:
                return ClientboundSetPlayerTeamPacket.Action.REMOVE;
        }
    }

    @Nullable
    public ClientboundSetPlayerTeamPacket.Action getTeamAction() {
        switch (this.method) {
            case 0:
                return ClientboundSetPlayerTeamPacket.Action.ADD;
            case 1:
                return ClientboundSetPlayerTeamPacket.Action.REMOVE;
            default:
                return null;
        }
    }

    @Override
    public void handle(ClientGamePacketListener listener) {
        listener.handleSetPlayerTeamPacket(this);
    }

    public String getName() {
        return this.name;
    }

    public Collection<String> getPlayers() {
        return this.players;
    }

    public Optional<ClientboundSetPlayerTeamPacket.Parameters> getParameters() {
        return this.parameters;
    }

    public static enum Action {
        ADD,
        REMOVE;
    }

    public static class Parameters {
        private final Component displayName;
        private final Component playerPrefix;
        private final Component playerSuffix;
        private final String nametagVisibility;
        private final String collisionRule;
        private final ChatFormatting color;
        private final int options;

        public Parameters(PlayerTeam team) {
            this.displayName = team.getDisplayName();
            this.options = team.packOptions();
            this.nametagVisibility = team.getNameTagVisibility().name;
            this.collisionRule = team.getCollisionRule().name;
            this.color = team.getColor();
            this.playerPrefix = team.getPlayerPrefix();
            this.playerSuffix = team.getPlayerSuffix();
        }

        public Parameters(FriendlyByteBuf buf) {
            this.displayName = buf.readComponent();
            this.options = buf.readByte();
            this.nametagVisibility = buf.readUtf(40);
            this.collisionRule = buf.readUtf(40);
            this.color = buf.readEnum(ChatFormatting.class);
            this.playerPrefix = buf.readComponent();
            this.playerSuffix = buf.readComponent();
        }

        public Component getDisplayName() {
            return this.displayName;
        }

        public int getOptions() {
            return this.options;
        }

        public ChatFormatting getColor() {
            return this.color;
        }

        public String getNametagVisibility() {
            return this.nametagVisibility;
        }

        public String getCollisionRule() {
            return this.collisionRule;
        }

        public Component getPlayerPrefix() {
            return this.playerPrefix;
        }

        public Component getPlayerSuffix() {
            return this.playerSuffix;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeComponent(this.displayName);
            buf.writeByte(this.options);
            buf.writeUtf(this.nametagVisibility);
            buf.writeUtf(!io.papermc.paper.configuration.GlobalConfiguration.get().collisions.enablePlayerCollisions ? "never" : this.collisionRule); // Paper
            buf.writeEnum(this.color);
            buf.writeComponent(this.playerPrefix);
            buf.writeComponent(this.playerSuffix);
        }
    }
}
