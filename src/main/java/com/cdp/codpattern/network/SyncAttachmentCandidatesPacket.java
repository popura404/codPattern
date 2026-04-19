package com.cdp.codpattern.network;

import com.cdp.codpattern.network.handler.ClientPacketBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncAttachmentCandidatesPacket {
    private final int bagId;
    private final String slot;
    private final List<ItemStack> attachmentCandidates;

    public SyncAttachmentCandidatesPacket(int bagId, String slot, List<ItemStack> attachmentCandidates) {
        this.bagId = bagId;
        this.slot = slot;
        this.attachmentCandidates = attachmentCandidates == null ? List.of() : attachmentCandidates;
    }

    public static void encode(SyncAttachmentCandidatesPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.bagId);
        buffer.writeUtf(packet.slot);
        buffer.writeVarInt(packet.attachmentCandidates.size());
        for (ItemStack stack : packet.attachmentCandidates) {
            buffer.writeItem(stack == null ? ItemStack.EMPTY : stack);
        }
    }

    public static SyncAttachmentCandidatesPacket decode(FriendlyByteBuf buffer) {
        int bagId = buffer.readInt();
        String slot = buffer.readUtf();
        int size = buffer.readVarInt();
        List<ItemStack> attachmentCandidates = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            attachmentCandidates.add(buffer.readItem());
        }
        return new SyncAttachmentCandidatesPacket(bagId, slot, attachmentCandidates);
    }

    public static void handle(SyncAttachmentCandidatesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPacketBridge.syncAttachmentCandidates(
                packet.bagId,
                packet.slot,
                packet.attachmentCandidates));
        ctx.get().setPacketHandled(true);
    }
}
