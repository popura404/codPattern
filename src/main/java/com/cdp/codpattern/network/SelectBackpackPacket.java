package com.cdp.codpattern.network;

import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.cdp.codpattern.config.backpack.BackpackNameHelper;
import com.cdp.codpattern.config.path.ConfigPath;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.nio.file.Path;
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
                Path path = ConfigPath.SERVERBACKPACK.getPath(player.server);
                String uuid = player.getUUID().toString();
                BackpackConfig.PlayerBackpackData playerData = BackpackConfigRepository.loadOrCreatePlayer(uuid, path);

                // 检查背包是否存在
                if (playerData.getBackpacks_MAP().containsKey(backpackId)) {
                    // 更新选中的背包
                    playerData.setSelectedBackpack(backpackId);
                    BackpackConfigRepository.save();

                    // 获取背包名称
                    Component backpackName = BackpackNameHelper.displayNameComponent(
                            playerData.getBackpacks_MAP().get(backpackId),
                            backpackId);

                    // 发送提示
                    player.connection.send(new ClientboundSetActionBarTextPacket(
                            Component.translatable("message.codpattern.backpack.selected", backpackId, backpackName)
                                    .withStyle(style -> style
                                            .withColor(0xDDDDDD)
                                            .withBold(true)
                                            .withItalic(false))));
                } else {
                    // 背包ID无效，发送错误提示
                    player.connection.send(new ClientboundSetActionBarTextPacket(
                            Component.translatable("message.codpattern.backpack.invalid_id", backpackId)
                                    .append("\n")
                                    .append(Component.translatable("message.codpattern.backpack.check_integrity"))));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
