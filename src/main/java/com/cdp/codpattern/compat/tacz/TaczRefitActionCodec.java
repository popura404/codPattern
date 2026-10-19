package com.cdp.codpattern.compat.tacz;

import com.cdp.codpattern.core.refit.RefitAction;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.network.message.ClientMessageLaserColor;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import com.tacz.guns.network.message.ClientMessageUnloadAttachment;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Map;

/** Converts the installed TaCZ wire format without accessing private fields or client classes. */
public final class TaczRefitActionCodec {
    private TaczRefitActionCodec() {
    }

    public static boolean supports(Object message) {
        return message instanceof ClientMessageRefitGun || message instanceof ClientMessageUnloadAttachment
                || message instanceof ClientMessageLaserColor;
    }

    public static RefitAction decode(Object message) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            RefitAction action;
            if (message instanceof ClientMessageRefitGun install) {
                ClientMessageRefitGun.encode(install, buffer);
                action = RefitAction.install(buffer.readInt(), buffer.readInt(), buffer.readEnum(AttachmentType.class));
            } else if (message instanceof ClientMessageUnloadAttachment unload) {
                ClientMessageUnloadAttachment.encode(unload, buffer);
                action = RefitAction.unload(buffer.readInt(), buffer.readEnum(AttachmentType.class));
            } else if (message instanceof ClientMessageLaserColor laser) {
                ClientMessageLaserColor.encode(laser, buffer);
                Map<String, Integer> colors = buffer.readMap(
                        input -> input.readEnum(AttachmentType.class).name(), FriendlyByteBuf::readInt);
                boolean applyGunColor = buffer.readBoolean();
                int gunColor = buffer.readInt();
                action = RefitAction.laser(buffer.readInt(), colors, applyGunColor, gunColor);
            } else {
                throw new IllegalArgumentException("Unsupported refit message");
            }
            if (buffer.isReadable()) {
                throw new IllegalArgumentException("Unsupported trailing TaCZ refit fields");
            }
            return action;
        } finally {
            buffer.release();
        }
    }
}
