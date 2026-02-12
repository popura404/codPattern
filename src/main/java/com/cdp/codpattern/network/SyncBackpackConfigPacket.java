package com.cdp.codpattern.network;

import com.cdp.codpattern.client.gui.screen.BackpackMenuScreen;
import com.cdp.codpattern.config.backpack.BackpackClientCache;
import com.cdp.codpattern.config.backpack.BackpackConfig;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;
//s2c
public class SyncBackpackConfigPacket {

    private final String configJson;
    private static final Gson GSON = new Gson();

    public SyncBackpackConfigPacket(BackpackConfig.PlayerBackpackData playerData) {
        this.configJson = GSON.toJson(playerData);
    }

    public SyncBackpackConfigPacket(FriendlyByteBuf buf) {
        this.configJson = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(configJson);
    }

    public static SyncBackpackConfigPacket decode(FriendlyByteBuf buf) {
        return new SyncBackpackConfigPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            //  更新c端缓存
            BackpackConfig.PlayerBackpackData playerData =
                    GSON.fromJson(configJson, BackpackConfig.PlayerBackpackData.class);
            BackpackClientCache.set(playerData);

            // 如果正在打开 BackpackMenuScreen，就刷新按钮
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof BackpackMenuScreen screen) {
                screen.reloadFromPlayerData();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
