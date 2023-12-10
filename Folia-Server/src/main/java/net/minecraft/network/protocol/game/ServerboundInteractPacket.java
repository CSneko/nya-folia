package net.minecraft.network.protocol.game;

import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class ServerboundInteractPacket implements Packet<ServerGamePacketListener> {
    private final int entityId;
    private final ServerboundInteractPacket.Action action;
    private final boolean usingSecondaryAction;
    static final ServerboundInteractPacket.Action ATTACK_ACTION = new ServerboundInteractPacket.Action() {
        @Override
        public ServerboundInteractPacket.ActionType getType() {
            return ServerboundInteractPacket.ActionType.ATTACK;
        }

        @Override
        public void dispatch(ServerboundInteractPacket.Handler handler) {
            handler.onAttack();
        }

        @Override
        public void write(FriendlyByteBuf buf) {
        }
    };

    private ServerboundInteractPacket(int entityId, boolean playerSneaking, ServerboundInteractPacket.Action type) {
        this.entityId = entityId;
        this.action = type;
        this.usingSecondaryAction = playerSneaking;
    }

    public static ServerboundInteractPacket createAttackPacket(Entity entity, boolean playerSneaking) {
        return new ServerboundInteractPacket(entity.getId(), playerSneaking, ATTACK_ACTION);
    }

    public static ServerboundInteractPacket createInteractionPacket(Entity entity, boolean playerSneaking, InteractionHand hand) {
        return new ServerboundInteractPacket(entity.getId(), playerSneaking, new ServerboundInteractPacket.InteractionAction(hand));
    }

    public static ServerboundInteractPacket createInteractionPacket(Entity entity, boolean playerSneaking, InteractionHand hand, Vec3 pos) {
        return new ServerboundInteractPacket(entity.getId(), playerSneaking, new ServerboundInteractPacket.InteractionAtLocationAction(hand, pos));
    }

    public ServerboundInteractPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        ServerboundInteractPacket.ActionType actionType = buf.readEnum(ServerboundInteractPacket.ActionType.class);
        this.action = actionType.reader.apply(buf);
        this.usingSecondaryAction = buf.readBoolean();
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeEnum(this.action.getType());
        this.action.write(buf);
        buf.writeBoolean(this.usingSecondaryAction);
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleInteract(this);
    }

    @Nullable
    public Entity getTarget(ServerLevel world) {
        return world.getEntityOrPart(this.entityId);
    }

    public boolean isUsingSecondaryAction() {
        return this.usingSecondaryAction;
    }

    public void dispatch(ServerboundInteractPacket.Handler handler) {
        this.action.dispatch(handler);
    }

    interface Action {
        ServerboundInteractPacket.ActionType getType();

        void dispatch(ServerboundInteractPacket.Handler handler);

        void write(FriendlyByteBuf buf);
    }

    static enum ActionType {
        INTERACT(ServerboundInteractPacket.InteractionAction::new),
        ATTACK((buf) -> {
            return ServerboundInteractPacket.ATTACK_ACTION;
        }),
        INTERACT_AT(ServerboundInteractPacket.InteractionAtLocationAction::new);

        final Function<FriendlyByteBuf, ServerboundInteractPacket.Action> reader;

        private ActionType(Function<FriendlyByteBuf, ServerboundInteractPacket.Action> handlerGetter) {
            this.reader = handlerGetter;
        }
    }

    public interface Handler {
        void onInteraction(InteractionHand hand);

        void onInteraction(InteractionHand hand, Vec3 pos);

        void onAttack();
    }

    static class InteractionAction implements ServerboundInteractPacket.Action {
        private final InteractionHand hand;

        InteractionAction(InteractionHand hand) {
            this.hand = hand;
        }

        private InteractionAction(FriendlyByteBuf buf) {
            this.hand = buf.readEnum(InteractionHand.class);
        }

        @Override
        public ServerboundInteractPacket.ActionType getType() {
            return ServerboundInteractPacket.ActionType.INTERACT;
        }

        @Override
        public void dispatch(ServerboundInteractPacket.Handler handler) {
            handler.onInteraction(this.hand);
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeEnum(this.hand);
        }
    }

    static class InteractionAtLocationAction implements ServerboundInteractPacket.Action {
        private final InteractionHand hand;
        private final Vec3 location;

        InteractionAtLocationAction(InteractionHand hand, Vec3 pos) {
            this.hand = hand;
            this.location = pos;
        }

        private InteractionAtLocationAction(FriendlyByteBuf buf) {
            this.location = new Vec3((double)buf.readFloat(), (double)buf.readFloat(), (double)buf.readFloat());
            this.hand = buf.readEnum(InteractionHand.class);
        }

        @Override
        public ServerboundInteractPacket.ActionType getType() {
            return ServerboundInteractPacket.ActionType.INTERACT_AT;
        }

        @Override
        public void dispatch(ServerboundInteractPacket.Handler handler) {
            handler.onInteraction(this.hand, this.location);
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeFloat((float)this.location.x);
            buf.writeFloat((float)this.location.y);
            buf.writeFloat((float)this.location.z);
            buf.writeEnum(this.hand);
        }
    }

    // Paper start - PlayerUseUnknownEntityEvent
    public int getEntityId() {
        return this.entityId;
    }

    public boolean isAttack() {
        return this.action.getType() == ActionType.ATTACK;
    }
    // Paper end - PlayerUseUnknownEntityEvent
}
