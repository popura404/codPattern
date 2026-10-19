package com.cdp.codpattern.network;

import com.cdp.codpattern.network.handler.ClientPacketBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class SyncAttachmentCandidatesPacket {
    private final UUID sessionId;
    private final int bagId;
    private final String slot;
    private final List<ItemStack> attachmentCandidates;

    public SyncAttachmentCandidatesPacket(int bagId, String slot, List<ItemStack> attachmentCandidates) {
        this(new UUID(0, 0), bagId, slot, attachmentCandidates);
    }

    public SyncAttachmentCandidatesPacket(UUID sessionId, int bagId, String slot, List<ItemStack> attachmentCandidates) {
        this.sessionId = sessionId;
        this.bagId = bagId;
        this.slot = slot;
        this.attachmentCandidates = attachmentCandidates == null ? List.of() : attachmentCandidates;
    }

    public static void encode(SyncAttachmentCandidatesPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeInt(packet.bagId);
        buffer.writeUtf(packet.slot);
        buffer.writeVarInt(packet.attachmentCandidates.size());
        for (ItemStack stack : packet.attachmentCandidates) {
            buffer.writeItem(stack == null ? ItemStack.EMPTY : stack);
        }
    }

    public static SyncAttachmentCandidatesPacket decode(FriendlyByteBuf buffer) {
        UUID sessionId = buffer.readUUID();
        int bagId = buffer.readInt();
        String slot = buffer.readUtf();
        int size = buffer.readVarInt();
        List<ItemStack> attachmentCandidates = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            attachmentCandidates.add(buffer.readItem());
        }
        return new SyncAttachmentCandidatesPacket(sessionId, bagId, slot, attachmentCandidates);
    }

    public static void handle(SyncAttachmentCandidatesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPacketBridge.syncAttachmentCandidates(
                packet.sessionId,
                packet.bagId,
                packet.slot,
                packet.attachmentCandidates));
        ctx.get().setPacketHandled(true);
    }
}
