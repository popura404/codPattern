package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.KillFeedPacket}.
 */
@Deprecated(forRemoval = false)
public class KillFeedPacket extends com.cdp.codpattern.network.match.KillFeedPacket {
    public KillFeedPacket(String killerName, String victimName, ItemStack weaponStack, boolean blunder) {
        super(killerName, victimName, weaponStack, blunder);
    }

    public KillFeedPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static KillFeedPacket decode(FriendlyByteBuf buf) {
        return new KillFeedPacket(buf);
    }
}
