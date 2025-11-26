package com.cdp.codpattern.network;

import com.cdp.codpattern.config.BackPackConfig.BackpackConfigManager;
import com.cdp.codpattern.config.BackPackConfig.BackpackConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class UpdateWeaponPacket {
    private final int backpackId;
    private final String weaponSlot;
    private final String itemId;
    private final String nbt;

    public UpdateWeaponPacket(int backpackId, String weaponSlot,
                              String itemId, String nbt) {
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

    public static void handle(UpdateWeaponPacket packet,
                              Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                String uuid = player.getUUID().toString();
                BackpackConfig.PlayerBackpackData playerData =
                        BackpackConfigManager.getConfig().getOrCreatePlayerData(uuid);

                BackpackConfig.Backpack backpack =
                        playerData.getBackpacks_MAP().get(packet.backpackId);

                if (backpack != null) {
                    backpack.getItem_MAP().put(packet.weaponSlot,
                            new BackpackConfig.Backpack.ItemData(
                                    packet.itemId, 1, packet.nbt));
                    BackpackConfigManager.save();
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
