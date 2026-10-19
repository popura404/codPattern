package com.cdp.codpattern.network;

import com.cdp.codpattern.network.handler.ClientPacketBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record RefitOperationResultPacket(UUID sessionId, boolean success, boolean finished,
        Component message, ItemStack gun, int gunSlot) {
    public static void encode(RefitOperationResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeBoolean(packet.success);
        buffer.writeBoolean(packet.finished);
        buffer.writeComponent(packet.message);
        buffer.writeItem(packet.gun);
        buffer.writeInt(packet.gunSlot);
    }

    public static RefitOperationResultPacket decode(FriendlyByteBuf buffer) {
        return new RefitOperationResultPacket(buffer.readUUID(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readComponent(), buffer.readItem(), buffer.readInt());
    }

    public static void handle(RefitOperationResultPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ClientPacketBridge.refitOperationResult(packet.sessionId, packet.success,
                packet.finished, packet.message, packet.gun, packet.gunSlot));
        context.get().setPacketHandled(true);
    }
}
