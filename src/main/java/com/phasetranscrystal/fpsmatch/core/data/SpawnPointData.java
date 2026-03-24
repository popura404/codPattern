package com.phasetranscrystal.fpsmatch.core.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Objects;

public class SpawnPointData {
    public static final Codec<SpawnPointData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("Dimension").forGetter(data -> data.getDimension().location().toString()),
            BlockPos.CODEC.optionalFieldOf("Position", BlockPos.ZERO).forGetter(SpawnPointData::getPosition),
            Codec.FLOAT.fieldOf("Yaw").forGetter(SpawnPointData::getYaw),
            Codec.FLOAT.fieldOf("Pitch").forGetter(SpawnPointData::getPitch),
            SpawnPointKind.CODEC.optionalFieldOf("Kind", SpawnPointKind.INITIAL).forGetter(SpawnPointData::getKind)
    ).apply(instance, (dimensionId, position, yaw, pitch, kind) -> {
        ResourceLocation location = ResourceLocation.tryParse(dimensionId);
        if (location == null) {
            location = Level.OVERWORLD.location();
        }
        return new SpawnPointData(ResourceKey.create(Registries.DIMENSION, location), position, yaw, pitch, kind);
    }));

    private final ResourceKey<Level> dimension;
    private final BlockPos position;
    private final float yaw;
    private final float pitch;
    private final SpawnPointKind kind;

    public SpawnPointData(ResourceKey<Level> dimension, BlockPos position, float yaw, float pitch) {
        this(dimension, position, yaw, pitch, SpawnPointKind.INITIAL);
    }

    public SpawnPointData(ResourceKey<Level> dimension, BlockPos position, float yaw, float pitch, SpawnPointKind kind) {
        this.dimension = dimension;
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
        this.kind = kind == null ? SpawnPointKind.INITIAL : kind;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public BlockPos getPosition() {
        return position;
    }

    public BlockPos getBlockPos() {
        return position;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public SpawnPointKind getKind() {
        return kind;
    }

    public int getX() {
        return position.getX();
    }

    public int getY() {
        return position.getY();
    }

    public int getZ() {
        return position.getZ();
    }

    public SpawnPointData withKind(SpawnPointKind newKind) {
        SpawnPointKind normalizedKind = newKind == null ? SpawnPointKind.INITIAL : newKind;
        if (kind == normalizedKind) {
            return this;
        }
        return new SpawnPointData(dimension, position, yaw, pitch, normalizedKind);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SpawnPointData other)) {
            return false;
        }
        return Objects.equals(dimension, other.dimension)
                && Objects.equals(position, other.position)
                && Float.compare(yaw, other.yaw) == 0
                && Float.compare(pitch, other.pitch) == 0
                && kind == other.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, position, yaw, pitch, kind);
    }

    @Override
    public String toString() {
        return kind.serializedName() + " " + dimension.location() + " " + position;
    }
}
