package com.cdp.codpattern.app.zombies.map.object;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

final class ZombiesObjectCodecs {
    static final Codec<ResourceKey<Level>> DIMENSION_CODEC = Codec.STRING.xmap(
            ZombiesObjectCodecs::dimensionKey,
            key -> key.location().toString());

    private ZombiesObjectCodecs() {
    }

    private static ResourceKey<Level> dimensionKey(String value) {
        ResourceLocation location = ResourceLocation.tryParse(value);
        if (location == null) {
            location = Level.OVERWORLD.location();
        }
        return ResourceKey.create(Registries.DIMENSION, location);
    }
}
