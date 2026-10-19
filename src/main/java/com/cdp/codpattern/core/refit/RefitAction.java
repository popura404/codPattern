package com.cdp.codpattern.core.refit;

import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Map;

/** An operation on the server's current editing session, never a client-supplied gun. */
public record RefitAction(Kind kind, int candidateSlot, int gunSlot, String attachmentType,
        Map<String, Integer> laserColors, boolean applyGunColor, int gunColor) {
    public enum Kind { INSTALL, UNLOAD, LASER, SAVE, CANCEL }

    public RefitAction {
        laserColors = laserColors == null ? Map.of() : Map.copyOf(laserColors);
        attachmentType = attachmentType == null ? "" : attachmentType;
    }

    public static RefitAction install(int candidate, int gun, AttachmentType type) {
        return new RefitAction(Kind.INSTALL, candidate, gun, type.name(), Map.of(), false, 0);
    }

    public static RefitAction unload(int gun, AttachmentType type) {
        return new RefitAction(Kind.UNLOAD, -1, gun, type.name(), Map.of(), false, 0);
    }

    public static RefitAction laser(int gun, Map<String, Integer> colors, boolean apply, int color) {
        return new RefitAction(Kind.LASER, -1, gun, "", colors, apply, color);
    }

    public static RefitAction save() {
        return new RefitAction(Kind.SAVE, -1, -1, "", Map.of(), false, 0);
    }

    public static RefitAction cancel() {
        return new RefitAction(Kind.CANCEL, -1, -1, "", Map.of(), false, 0);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(kind);
        switch (kind) {
            case INSTALL, UNLOAD -> {
                buffer.writeInt(candidateSlot);
                buffer.writeInt(gunSlot);
                buffer.writeUtf(attachmentType);
            }
            case LASER -> {
                buffer.writeInt(gunSlot);
                buffer.writeMap(laserColors, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeInt);
                buffer.writeBoolean(applyGunColor);
                buffer.writeInt(gunColor);
            }
            default -> { }
        }
    }

    public static RefitAction decode(FriendlyByteBuf buffer) {
        Kind kind = buffer.readEnum(Kind.class);
        return switch (kind) {
            case INSTALL, UNLOAD -> new RefitAction(kind, buffer.readInt(), buffer.readInt(),
                    buffer.readUtf(), Map.of(), false, 0);
            case LASER -> laser(buffer.readInt(), buffer.readMap(FriendlyByteBuf::readUtf,
                    FriendlyByteBuf::readInt), buffer.readBoolean(), buffer.readInt());
            case SAVE -> save();
            case CANCEL -> cancel();
        };
    }
}
