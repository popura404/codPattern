package com.cdp.codpattern.network.match;

import com.cdp.codpattern.app.match.model.ModeObjectState;
import com.cdp.codpattern.network.handler.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ModeObjectStateSyncPacket {
    private final String roomKey;
    private final List<ModeObjectState> states;
    private final long revision;

    public ModeObjectStateSyncPacket(String roomKey, List<ModeObjectState> states, long revision) {
        this.roomKey = roomKey == null ? "" : roomKey;
        this.states = states == null ? List.of() : List.copyOf(states);
        this.revision = Math.max(0L, revision);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(roomKey);
        buf.writeVarLong(revision);
        buf.writeVarInt(states.size());
        for (ModeObjectState state : states) {
            buf.writeUtf(state.objectKey());
            buf.writeUtf(state.stateKey());
            buf.writeBoolean(state.position() != null);
            if (state.position() != null) {
                buf.writeBlockPos(state.position());
            }
            buf.writeNbt(state.payload());
            buf.writeVarLong(state.revision());
        }
    }

    public static ModeObjectStateSyncPacket decode(FriendlyByteBuf buf) {
        String roomKey = buf.readUtf();
        long revision = buf.readVarLong();
        int stateCount = Math.max(0, buf.readVarInt());
        List<ModeObjectState> states = new ArrayList<>(stateCount);
        for (int i = 0; i < stateCount; i++) {
            String objectKey = buf.readUtf();
            String stateKey = buf.readUtf();
            BlockPos position = buf.readBoolean() ? buf.readBlockPos() : null;
            CompoundTag payload = buf.readNbt();
            long stateRevision = buf.readVarLong();
            states.add(new ModeObjectState(objectKey, stateKey, position, payload, stateRevision));
        }
        return new ModeObjectStateSyncPacket(roomKey, states, revision);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPacketHandler.handleModeObjectStates(roomKey, states, revision));
        ctx.get().setPacketHandled(true);
    }
}
