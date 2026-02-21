package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.network.handler.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C: 战斗标识参数同步数据包
 */
public class CombatMarkerConfigPacket {
    private final boolean enemyMarkerHealthBar;
    private final float focusHalfAngleDegrees;
    private final int focusRequiredTicks;
    private final double barMaxDistance;
    private final int barVisibleGraceTicks;

    public CombatMarkerConfigPacket(boolean enemyMarkerHealthBar,
            float focusHalfAngleDegrees,
            int focusRequiredTicks,
            double barMaxDistance,
            int barVisibleGraceTicks) {
        this.enemyMarkerHealthBar = enemyMarkerHealthBar;
        this.focusHalfAngleDegrees = focusHalfAngleDegrees;
        this.focusRequiredTicks = focusRequiredTicks;
        this.barMaxDistance = barMaxDistance;
        this.barVisibleGraceTicks = barVisibleGraceTicks;
    }

    public CombatMarkerConfigPacket(FriendlyByteBuf buf) {
        this.enemyMarkerHealthBar = buf.readBoolean();
        this.focusHalfAngleDegrees = buf.readFloat();
        this.focusRequiredTicks = buf.readInt();
        this.barMaxDistance = buf.readDouble();
        this.barVisibleGraceTicks = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enemyMarkerHealthBar);
        buf.writeFloat(focusHalfAngleDegrees);
        buf.writeInt(focusRequiredTicks);
        buf.writeDouble(barMaxDistance);
        buf.writeInt(barVisibleGraceTicks);
    }

    public static CombatMarkerConfigPacket decode(FriendlyByteBuf buf) {
        return new CombatMarkerConfigPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handleCombatMarkerConfig(
                        enemyMarkerHealthBar,
                        focusHalfAngleDegrees,
                        focusRequiredTicks,
                        barMaxDistance,
                        barVisibleGraceTicks)));
        ctx.get().setPacketHandled(true);
    }
}
