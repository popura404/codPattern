package com.phasetranscrystal.fpsmatch.common.packet;

import com.phasetranscrystal.fpsmatch.common.client.FPSMClient;
import com.phasetranscrystal.fpsmatch.common.client.data.RenderablePoint;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AddPointDataS2CPacket {
    private final String key;
    private final Component name;
    private final int color;
    private final Vec3 position;
    private final float yaw;

    public AddPointDataS2CPacket(String key, Component name, int color, Vec3 position) {
        this(key, name, color, position, Float.NaN);
    }

    public AddPointDataS2CPacket(String key, Component name, int color, Vec3 position, float yaw) {
        this.key = key;
        this.name = name;
        this.color = color;
        this.position = position;
        this.yaw = yaw;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(key);
        buf.writeComponent(name);
        buf.writeInt(color);
        buf.writeDouble(position.x);
        buf.writeDouble(position.y);
        buf.writeDouble(position.z);
        buf.writeBoolean(hasYaw());
        if (hasYaw()) {
            buf.writeFloat(yaw);
        }
    }

    public static AddPointDataS2CPacket decode(FriendlyByteBuf buf) {
        String key = buf.readUtf();
        Component name = buf.readComponent();
        int color = buf.readInt();
        Vec3 position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        float yaw = buf.readBoolean() ? buf.readFloat() : Float.NaN;
        return new AddPointDataS2CPacket(
                key,
                name,
                color,
                position,
                yaw
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> FPSMClient.getGlobalData().getDebugData()
                .upsertRenderablePoint(key, new RenderablePoint(key, name, color, position, yaw)));
        ctx.get().setPacketHandled(true);
    }

    private boolean hasYaw() {
        return !Float.isNaN(yaw);
    }
}
