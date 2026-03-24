package com.phasetranscrystal.fpsmatch.core.data;

import com.mojang.serialization.Codec;

import java.util.Locale;

public enum SpawnPointKind {
    INITIAL,
    DYNAMIC_CANDIDATE;

    public static final Codec<SpawnPointKind> CODEC = Codec.STRING.xmap(
            SpawnPointKind::fromSerializedName,
            SpawnPointKind::serializedName
    );

    public String serializedName() {
        return name();
    }

    public static SpawnPointKind fromSerializedName(String raw) {
        if (raw == null || raw.isBlank()) {
            return INITIAL;
        }
        try {
            return SpawnPointKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return INITIAL;
        }
    }
}
