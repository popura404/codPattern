package com.cdp.codpattern.network;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.backpack.service.UpdateWeaponService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdateWeaponPacket {
    private final int backpackId;
    private final String weaponSlot;
    private final String itemId;
    private final String nbt;

    public UpdateWeaponPacket(int backpackId, String weaponSlot, String itemId, String nbt) {
        this.backpackId = backpackId;
        this.weaponSlot = weaponSlot;
        this.itemId = itemId;
        this.nbt = nbt;
    }

    public static void encode(UpdateWeaponPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.backpackId);
        buffer.writeUtf(packet.weaponSlot);
        buffer.writeUtf(packet.itemId);
        buffer.writeUtf(packet.nbt);
    }

    public static UpdateWeaponPacket decode(FriendlyByteBuf buffer) {
        return new UpdateWeaponPacket(
                buffer.readInt(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf()
        );
    }

    public static void handle(UpdateWeaponPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.server == null) {
                return;
            }

            UpdateWeaponService.Result result = UpdateWeaponService.process(
                    player,
                    packet.backpackId,
                    packet.weaponSlot,
                    packet.itemId,
                    packet.nbt
            );

            ModNetworkChannel.sendToPlayer(new SyncBackpackConfigPacket(result.playerData()), player);
            ModNetworkChannel.sendToPlayer(
                    new UpdateWeaponResultPacket(result.success(), result.slot(), result.code(), result.message()),
                    player
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
