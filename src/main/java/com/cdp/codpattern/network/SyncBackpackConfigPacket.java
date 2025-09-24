package com.cdp.codpattern.network;

import com.cdp.codpattern.config.configmanager.BackpackConfigManager;
import com.cdp.codpattern.config.server.BagSelectionConfig;
import com.google.gson.Gson;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncBackpackConfigPacket {
    private final String playerUuid;
    private final String configJson;
    private static final Gson GSON = new Gson();

    public SyncBackpackConfigPacket(String playerUuid, BagSelectionConfig.PlayerBackpackData playerData) {
        this.playerUuid = playerUuid;
        this.configJson = GSON.toJson(playerData);
    }

    public SyncBackpackConfigPacket(FriendlyByteBuf buf) {
        this.playerUuid = buf.readUtf();
        this.configJson = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(playerUuid);
        buf.writeUtf(configJson);
    }

    public static SyncBackpackConfigPacket decode(FriendlyByteBuf buf) {
        return new SyncBackpackConfigPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 在客户端处理
            BagSelectionConfig.PlayerBackpackData playerData =
                    GSON.fromJson(configJson, BagSelectionConfig.PlayerBackpackData.class);

            // 更新客户端缓存
            BagSelectionConfig clientConfig = BackpackConfigManager.getConfig();
            clientConfig.getPlayerData().put(playerUuid, playerData);
            BackpackConfigManager.setClientConfig(clientConfig);
        });
        ctx.get().setPacketHandled(true);
    }
}
