package com.phasetranscrystal.fpsmatch.common.packet;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.client.data.RenderableArea;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AddAreaDataS2CPacket {
    private final String key;
    private final Component name;
    private final int color;
    private final AreaData areaData;

    public AddAreaDataS2CPacket(String key, Component name, int color, AreaData areaData) {
        this.key = key;
        this.name = name;
        this.color = color;
        this.areaData = areaData;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(key);
        buf.writeComponent(name);
        buf.writeInt(color);
        buf.writeBlockPos(areaData.pos1());
        buf.writeBlockPos(areaData.pos2());
    }

    public static AddAreaDataS2CPacket decode(FriendlyByteBuf buf) {
        String key = buf.readUtf();
        Component name = buf.readComponent();
        int color = buf.readInt();
        BlockPos pos1 = buf.readBlockPos();
        BlockPos pos2 = buf.readBlockPos();
        return new AddAreaDataS2CPacket(key, name, color, new AreaData(pos1, pos2));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> FPSMClient.getGlobalData().getDebugData()
                .upsertRenderableArea(key, new RenderableArea(key, name, color, areaData)));
        ctx.get().setPacketHandled(true);
    }
}
