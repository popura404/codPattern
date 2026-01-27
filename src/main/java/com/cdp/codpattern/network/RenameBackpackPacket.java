package com.cdp.codpattern.network;

import com.cdp.codpattern.config.BackPackConfig.BackpackConfig;
import com.cdp.codpattern.config.BackPackConfig.BackpackConfigManager;
import com.cdp.codpattern.core.ConfigPath.ConfigPath;
import com.cdp.codpattern.network.handler.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.nio.file.Path;
import java.util.function.Supplier;

public class RenameBackpackPacket {

    private final int backpackId;
    private final String newName;

    public RenameBackpackPacket(int backpackId, String newName) {
        this.backpackId = backpackId;
        this.newName = newName == null ? "" : newName;
    }

    public RenameBackpackPacket(FriendlyByteBuf buf) {
        this.backpackId = buf.readInt();
        this.newName = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(backpackId);
        buf.writeUtf(newName);
    }

    public static RenameBackpackPacket decode(FriendlyByteBuf buf) {
        return new RenameBackpackPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            String name = newName.trim();
            if (name.isEmpty()) {
                player.sendSystemMessage(Component.literal("§c背包名称不能为空"));
                return;
            }
            if (name.length() > 32) {
                name = name.substring(0, 32);
            }

            Path path = ConfigPath.SERVERBACKPACK.getPath(player.server);
            String uuid = player.getStringUUID();
            BackpackConfig.PlayerBackpackData playerData = BackpackConfigManager.LoadorCreatePlayer(uuid, path);
            BackpackConfig.Backpack backpack = playerData.getBackpacks_MAP().get(backpackId);
            if (backpack == null) {
                player.sendSystemMessage(Component.literal("§c背包不存在: #" + backpackId));
                return;
            }

            backpack.setName(name);
            BackpackConfigManager.save();
            PacketHandler.sendToPlayer(new SyncBackpackConfigPacket(playerData), player);
            player.sendSystemMessage(Component.literal("§a已重命名背包 #" + backpackId + " -> " + name));
        });
        ctx.get().setPacketHandled(true);
    }
}
