package com.cdp.codpattern.network;

import com.cdp.codpattern.client.refit.AttachmentRefitClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncAttachmentPresetPacket {
    private final int bagId;
    private final String slot;
    private final String presetPayload;
    private final String expectedGunId;

    public SyncAttachmentPresetPacket(int bagId, String slot, String presetPayload, String expectedGunId) {
        this.bagId = bagId;
        this.slot = slot;
        this.presetPayload = presetPayload;
        this.expectedGunId = expectedGunId;
    }

    public static void encode(SyncAttachmentPresetPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.bagId);
        buffer.writeUtf(packet.slot);
        buffer.writeUtf(packet.presetPayload == null ? "" : packet.presetPayload);
        buffer.writeUtf(packet.expectedGunId == null ? "" : packet.expectedGunId);
    }

    public static SyncAttachmentPresetPacket decode(FriendlyByteBuf buffer) {
        return new SyncAttachmentPresetPacket(buffer.readInt(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(SyncAttachmentPresetPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> onClient(packet));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void onClient(SyncAttachmentPresetPacket packet) {
        AttachmentRefitClientState.onPresetSync(packet.bagId, packet.slot, packet.presetPayload, packet.expectedGunId);
    }
}
