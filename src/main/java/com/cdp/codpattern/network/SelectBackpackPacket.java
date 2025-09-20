package com.cdp.codpattern.network;

import com.cdp.codpattern.config.configmanager.BackpackConfigManager;
import com.cdp.codpattern.config.server.BagSelectConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SelectBackpackPacket {
    private final int backpackId;

    public SelectBackpackPacket(int backpackId) {
        this.backpackId = backpackId;
    }

    /**
     * 编码数据包
     */
    public static void encode(SelectBackpackPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.backpackId);
    }

    /**
     * 解码数据包
     */
    public static SelectBackpackPacket decode(FriendlyByteBuf buffer) {
        return new SelectBackpackPacket(buffer.readInt());
    }

    /**
     * 处理数据包 - 在服务端执行
     */
    public static void handle(SelectBackpackPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                String uuid = player.getUUID().toString();

                // 获取玩家数据
                BagSelectConfig.PlayerBackpackData playerData =
                        BackpackConfigManager.getConfig().getOrCreatePlayerData(uuid);

                // 验证背包ID是否有效
                if (playerData.getBackpacks_MAP().containsKey(packet.backpackId)) {
                    // 安全地修改选中的背包
                    playerData.setSelectedBackpack(packet.backpackId);

                    // 保存配置
                    BackpackConfigManager.save();

                    // 获取背包名称
                    String backpackName = playerData.getBackpacks_MAP()
                            .get(packet.backpackId).getName();

                    // 发送ActionBar提示
                    player.connection.send(new ClientboundSetActionBarTextPacket(
                            Component.literal("已选择背包 # " + packet.backpackId +
                                            " [" + backpackName + "] 在下次重生时获得")
                                    .withStyle(style -> style
                                            .withColor(0xFFFFFF)  // 白色
                                            .withBold(true)
                                            .withItalic(false))
                    ));
                } else {
                    // 背包ID无效，发送错误提示
                    player.connection.send(new ClientboundSetActionBarTextPacket(
                            Component.literal("§c无效的背包ID: " + packet.backpackId + "  ##前往服务端config检查背包完整性或询问管理员")
                    ));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
