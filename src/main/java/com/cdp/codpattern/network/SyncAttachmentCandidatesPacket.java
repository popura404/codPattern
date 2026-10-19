package com.cdp.codpattern.network;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.core.refit.AttachmentCandidateTransfer;
import com.cdp.codpattern.network.handler.ClientPacketBridge;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** A bounded fragment of a complete list. Offsets are bytes, so even one large item can span packets. */
public record SyncAttachmentCandidatesPacket(UUID sessionId, int bagId, String slot,
        int offset, int totalBytes, boolean complete, byte[] payload) {
    public SyncAttachmentCandidatesPacket {
        payload = payload.clone();
        if (payload.length == 0 || payload.length > AttachmentCandidateTransfer.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Invalid attachment candidate fragment size");
        }
    }

    public static List<SyncAttachmentCandidatesPacket> split(UUID sessionId, int bagId, String slot,
            List<ItemStack> candidates) {
        FriendlyByteBuf snapshot = new FriendlyByteBuf(Unpooled.buffer());
        try {
            List<ItemStack> source = candidates == null ? List.of() : candidates;
            snapshot.writeVarInt(source.size());
            for (ItemStack candidate : source) {
                snapshot.writeItem(candidate == null ? ItemStack.EMPTY : candidate);
            }
            int totalBytes = snapshot.readableBytes();
            List<SyncAttachmentCandidatesPacket> fragments = new ArrayList<>();
            for (int offset = 0; offset < totalBytes;) {
                int size = Math.min(AttachmentCandidateTransfer.MAX_CHUNK_BYTES, totalBytes - offset);
                byte[] bytes = new byte[size];
                snapshot.readBytes(bytes);
                fragments.add(new SyncAttachmentCandidatesPacket(sessionId, bagId, slot,
                        offset, totalBytes, offset + size == totalBytes, bytes));
                offset += size;
            }
            return fragments;
        } finally {
            snapshot.release();
        }
    }

    public static void sendTo(ServerPlayer player, UUID sessionId, int bagId, String slot,
            List<ItemStack> candidates) {
        for (SyncAttachmentCandidatesPacket fragment : split(sessionId, bagId, slot, candidates)) {
            ModNetworkChannel.sendToPlayer(fragment, player);
        }
    }

    public static List<ItemStack> readSnapshot(byte[] bytes) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            int size = buffer.readVarInt();
            // Every serialized ItemStack needs at least its presence byte. Validate before
            // allocating from wire input, without imposing an artificial directory limit.
            if (size < 0 || size > buffer.readableBytes()) {
                throw new IllegalArgumentException("Invalid attachment candidate count");
            }
            List<ItemStack> candidates = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                candidates.add(buffer.readItem());
            }
            if (buffer.isReadable()) {
                throw new IllegalArgumentException("Trailing attachment candidate snapshot data");
            }
            return candidates;
        } finally {
            buffer.release();
        }
    }

    public static void encode(SyncAttachmentCandidatesPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeInt(packet.bagId);
        buffer.writeUtf(packet.slot, 16);
        buffer.writeVarInt(packet.offset);
        buffer.writeVarInt(packet.totalBytes);
        buffer.writeBoolean(packet.complete);
        buffer.writeByteArray(packet.payload);
    }

    public static SyncAttachmentCandidatesPacket decode(FriendlyByteBuf buffer) {
        return new SyncAttachmentCandidatesPacket(buffer.readUUID(), buffer.readInt(), buffer.readUtf(16),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(),
                buffer.readByteArray(AttachmentCandidateTransfer.MAX_CHUNK_BYTES));
    }

    public static void handle(SyncAttachmentCandidatesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPacketBridge.syncAttachmentCandidateChunk(packet.sessionId,
                packet.bagId, packet.slot, packet.offset, packet.totalBytes, packet.complete, packet.payload));
        ctx.get().setPacketHandled(true);
    }
}
