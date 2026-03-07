package com.cdp.codpattern.network.tdm;

import com.cdp.codpattern.network.handler.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class KillFeedPacket {
    private final String killerName;
    private final String victimName;
    private final ItemStack weaponStack;
    private final boolean blunder;

    public KillFeedPacket(String killerName, String victimName, ItemStack weaponStack, boolean blunder) {
        this.killerName = killerName == null ? "" : killerName;
        this.victimName = victimName == null ? "" : victimName;
        this.weaponStack = weaponStack == null ? ItemStack.EMPTY : weaponStack.copy();
        this.blunder = blunder;
    }

    public KillFeedPacket(FriendlyByteBuf buf) {
        this.killerName = buf.readUtf();
        this.victimName = buf.readUtf();
        this.weaponStack = buf.readItem();
        this.blunder = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(killerName);
        buf.writeUtf(victimName);
        buf.writeItem(weaponStack);
        buf.writeBoolean(blunder);
    }

    public static KillFeedPacket decode(FriendlyByteBuf buf) {
        return new KillFeedPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientPacketHandler.handleKillFeed(killerName, victimName, weaponStack, blunder)));
        ctx.get().setPacketHandled(true);
    }
}
