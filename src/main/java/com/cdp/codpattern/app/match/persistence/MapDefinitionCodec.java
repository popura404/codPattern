package com.cdp.codpattern.app.match.persistence;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

/** Strict codec boundary for detached map definitions used by rename. */
public final class MapDefinitionCodec {
    private MapDefinitionCodec() { }

    public static <T> JsonObject encode(Codec<T> codec, T value) {
        var encoded = codec.encodeStart(JsonOps.INSTANCE, value).result()
                .orElseThrow(() -> new IllegalStateException("Cannot encode complete map definition"));
        if (!encoded.isJsonObject()) throw new IllegalStateException("Map definition is not an object");
        JsonObject definition = encoded.getAsJsonObject().deepCopy();
        decode(codec, definition); // The replacement must be readable before any files are moved.
        return definition;
    }

    public static <T> T decode(Codec<T> codec, JsonObject definition) {
        if (definition == null) throw new IllegalArgumentException("Missing map definition");
        return codec.parse(JsonOps.INSTANCE, definition.deepCopy()).result()
                .orElseThrow(() -> new IllegalArgumentException("Invalid map definition"));
    }

    public static JsonObject withName(JsonObject definition, String newName) {
        JsonObject renamed = definition.deepCopy();
        renamed.addProperty("mapName", newName);
        return renamed;
    }
}
