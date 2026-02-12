package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.compat.physicsmod.PhysicsModClientBridge;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
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
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> onClient(this)));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void onClient(PhysicsMobRetainPacket packet) {
        if (!PhysicsModClientBridge.isPhysicsModLoaded()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        Entity source = level.getEntity(packet.entityId);
        if (!(source instanceof Player sourcePlayer)) {
            return;
        }

        GameProfile profile = sourcePlayer.getGameProfile();
        RemotePlayer snapshot = new RemotePlayer(level, profile);
        snapshot.setPos(packet.x, packet.y, packet.z);
        snapshot.setYRot(packet.yRot);
        snapshot.setXRot(packet.xRot);
        snapshot.yRotO = packet.yRot;
        snapshot.xRotO = packet.xRot;
        snapshot.xo = packet.x;
        snapshot.yo = packet.y;
        snapshot.zo = packet.z;
        snapshot.setOldPosAndRot();
        snapshot.yHeadRot = packet.yHeadRot;
        snapshot.yHeadRotO = packet.yHeadRot;
        snapshot.yBodyRot = packet.yBodyRot;
        snapshot.yBodyRotO = packet.yBodyRot;
        snapshot.setDeltaMovement(packet.motionX, packet.motionY, packet.motionZ);
        snapshot.setHealth(Math.max(0.01f, sourcePlayer.getHealth()));

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            snapshot.setItemSlot(slot, sourcePlayer.getItemBySlot(slot).copy());
        }

        PhysicsModClientBridge.blockifySnapshot(level, snapshot);
    }
}
