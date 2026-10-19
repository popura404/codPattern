package com.cdp.codpattern.network;

import com.cdp.codpattern.network.handler.ClientPacketBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record SyncAttachmentPresetPacket(UUID sessionId, int bagId, String slot, String presetPayload,
        String expectedGunId, ItemStack workingGun, int gunSlot) {
    public static void encode(SyncAttachmentPresetPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeInt(packet.bagId);
        buffer.writeUtf(packet.slot);
        buffer.writeUtf(packet.presetPayload);
        buffer.writeUtf(packet.expectedGunId);
        buffer.writeItem(packet.workingGun);
        buffer.writeInt(packet.gunSlot);
    }

    public static SyncAttachmentPresetPacket decode(FriendlyByteBuf buffer) {
        return new SyncAttachmentPresetPacket(buffer.readUUID(), buffer.readInt(), buffer.readUtf(),
                buffer.readUtf(), buffer.readUtf(), buffer.readItem(), buffer.readInt());
    }

    public static void handle(SyncAttachmentPresetPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ClientPacketBridge.syncAttachmentPreset(packet.sessionId, packet.bagId,
                packet.slot, packet.presetPayload, packet.expectedGunId, packet.workingGun, packet.gunSlot));
        context.get().setPacketHandled(true);
    }
}
