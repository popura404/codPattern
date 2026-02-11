package com.cdp.codpattern.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C: 配装写入结果回执
 */
public class UpdateWeaponResultPacket {
    private final boolean success;
    private final String slot;
    private final String code;
    private final String message;

    public UpdateWeaponResultPacket(boolean success, String slot, String code, String message) {
        this.success = success;
        this.slot = slot == null ? "" : slot;
        this.code = code == null ? "" : code;
        this.message = message == null ? "" : message;
    }

    public UpdateWeaponResultPacket(FriendlyByteBuf buf) {
        this.success = buf.readBoolean();
        this.slot = buf.readUtf();
        this.code = buf.readUtf();
        this.message = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(success);
        buf.writeUtf(slot);
        buf.writeUtf(code);
        buf.writeUtf(message);
    }

    public static UpdateWeaponResultPacket decode(FriendlyByteBuf buf) {
        return new UpdateWeaponResultPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (success || Minecraft.getInstance().player == null) {
                return;
            }
            String text = message.isBlank() ? "配装写入被拒绝: " + code : message;
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c" + text));
        });
        ctx.get().setPacketHandled(true);
    }
}
