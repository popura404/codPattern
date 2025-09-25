package com.cdp.codpattern.network;

import com.cdp.codpattern.config.configmanager.BackpackConfigManager;
import com.cdp.codpattern.config.server.BackpackSelectionConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SelectBackpackPacket {
    public int backpackId;

    public SelectBackpackPacket(int backpackId) {
        this.backpackId = backpackId;
    }

    public SelectBackpackPacket(FriendlyByteBuf buf) {
        this.backpackId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(backpackId);
    }

    public static SelectBackpackPacket decode(FriendlyByteBuf buf) {
        return new SelectBackpackPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                String uuid = player.getUUID().toString();
                BackpackSelectionConfig.PlayerBackpackData playerData =
                        BackpackConfigManager.getConfig().getOrCreatePlayerData(uuid);

                // 检查背包是否存在
                if (playerData.getBackpacks_MAP().containsKey(backpackId)) {
                    // 更新选中的背包
                    playerData.setSelectedBackpack(backpackId);
                    BackpackConfigManager.save();

                    // 获取背包名称
                    String backpackName = playerData.getBackpacks_MAP().get(backpackId).getName();

                    // 发送ActionBar提示（保留你的代码）
                    player.connection.send(new ClientboundSetActionBarTextPacket(
                            Component.literal("已选择背包 # " + backpackId +
                                            " [" + backpackName + "] 在下次重生时获得")
                                    .withStyle(style -> style
                                            .withColor(0xFFFFFF)  // 白色
                                            .withBold(true)
                                            .withItalic(false))
                    ));
                } else {
                    // 背包ID无效，发送错误提示
                    player.connection.send(new ClientboundSetActionBarTextPacket(
                            Component.literal("§c无效的背包ID: " + backpackId +
                                    "  ##前往服务端config检查背包完整性或询问管理员")
                    ));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
