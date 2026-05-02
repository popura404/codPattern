package com.cdp.codpattern.app.match.model;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

public record ModePlayerValue(
        Type type,
        String value
) {
    public enum Type {
        INT,
        LONG,
        DOUBLE,
        BOOLEAN,
        STRING
    }

    public ModePlayerValue {
        type = type == null ? Type.STRING : type;
        value = Objects.requireNonNullElse(value, "");
    }

    public static ModePlayerValue ofInt(int value) {
        return new ModePlayerValue(Type.INT, Integer.toString(value));
    }

    public static ModePlayerValue ofLong(long value) {
        return new ModePlayerValue(Type.LONG, Long.toString(value));
    }

    public static ModePlayerValue ofDouble(double value) {
        return new ModePlayerValue(Type.DOUBLE, Double.toString(value));
    }

    public static ModePlayerValue ofBoolean(boolean value) {
        return new ModePlayerValue(Type.BOOLEAN, Boolean.toString(value));
    }

    public static ModePlayerValue ofString(String value) {
        return new ModePlayerValue(Type.STRING, value);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(type);
        buf.writeUtf(value);
    }

    public static ModePlayerValue read(FriendlyByteBuf buf) {
        return new ModePlayerValue(buf.readEnum(Type.class), buf.readUtf());
    }
}
