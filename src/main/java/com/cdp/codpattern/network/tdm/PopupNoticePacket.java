package com.cdp.codpattern.network.tdm;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * @deprecated Use {@link com.cdp.codpattern.network.match.PopupNoticePacket}.
 */
@Deprecated(forRemoval = false)
public class PopupNoticePacket extends com.cdp.codpattern.network.match.PopupNoticePacket {
    public PopupNoticePacket(Component title, Component message) {
        super(title, message);
    }

    public PopupNoticePacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static PopupNoticePacket decode(FriendlyByteBuf buf) {
        return new PopupNoticePacket(buf);
    }
}
