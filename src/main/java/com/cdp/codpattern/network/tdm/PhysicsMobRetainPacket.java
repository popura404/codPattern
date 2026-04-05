package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.network.handler.ClientPacketBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S->C: 兼容 physicsmod 的“死亡实体保留”触发包。
 * 服务端在玩家被击杀时下发，客户端按死亡快照生成临时玩家副本并触发 physicsmod blockify。
 */
public class PhysicsMobRetainPacket {
    private final int entityId;
    private final double x;
    private final double y;
    private final double z;
    private final float yRot;
    private final float xRot;
    private final float yHeadRot;
    private final float yBodyRot;
    private final double motionX;
    private final double motionY;
    private final double motionZ;

    public PhysicsMobRetainPacket(int entityId, double x, double y, double z, float yRot, float xRot, float yHeadRot,
            float yBodyRot, double motionX, double motionY, double motionZ) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yRot = yRot;
        this.xRot = xRot;
        this.yHeadRot = yHeadRot;
        this.yBodyRot = yBodyRot;
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
    }

    public PhysicsMobRetainPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.yRot = buf.readFloat();
        this.xRot = buf.readFloat();
        this.yHeadRot = buf.readFloat();
        this.yBodyRot = buf.readFloat();
        this.motionX = buf.readDouble();
        this.motionY = buf.readDouble();
        this.motionZ = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yRot);
        buf.writeFloat(xRot);
        buf.writeFloat(yHeadRot);
        buf.writeFloat(yBodyRot);
        buf.writeDouble(motionX);
        buf.writeDouble(motionY);
        buf.writeDouble(motionZ);
    }

    public static PhysicsMobRetainPacket decode(FriendlyByteBuf buf) {
        return new PhysicsMobRetainPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPacketBridge.physicsMobRetain(
                entityId,
                x,
                y,
                z,
                yRot,
                xRot,
                yHeadRot,
                yBodyRot,
                motionX,
                motionY,
                motionZ));
        ctx.get().setPacketHandled(true);
    }
}
