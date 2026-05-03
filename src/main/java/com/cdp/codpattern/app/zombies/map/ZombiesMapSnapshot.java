package com.cdp.codpattern.app.zombies.map;

import com.cdp.codpattern.app.match.editor.ModeObjectData;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.persistence.CommonModeMapData;
import com.cdp.codpattern.app.zombies.validation.ZombiesMapValidationContributor;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Lightweight map snapshot consumed by zombies validation.
 *
 * Expected M1-MapData adapter:
 * convert player spawns to {@link SpawnSnapshot} with zombieSpawn=false,
 * zombie spawn definitions to {@link SpawnSnapshot} with zombieSpawn=true, group and weight populated,
 * and barriers/doors/windows to {@link BarrierSnapshot} with stable objectId values.
 */
public record ZombiesMapSnapshot(
        RoomId roomId,
        String mapName,
        boolean hasEndTeleportPoint,
        List<SpawnSnapshot> spawns,
        List<BarrierSnapshot> barriers
) {
    public ZombiesMapSnapshot {
        Objects.requireNonNull(roomId, "roomId");
        mapName = Objects.requireNonNullElse(mapName, roomId.mapName()).trim();
        spawns = spawns == null ? List.of() : List.copyOf(spawns);
        barriers = barriers == null ? List.of() : List.copyOf(barriers);
    }

    public static ZombiesMapSnapshot of(
            RoomId roomId,
            String mapName,
            boolean hasEndTeleportPoint,
            List<SpawnSnapshot> spawns,
            List<BarrierSnapshot> barriers
    ) {
        return new ZombiesMapSnapshot(roomId, mapName, hasEndTeleportPoint, spawns, barriers);
    }

    public static ZombiesMapSnapshot fromMapObjects(
            RoomId roomId,
            String mapName,
            boolean hasEndTeleportPoint,
            ZombiesMapObjects objects
    ) {
        ZombiesMapObjects resolved = objects == null ? ZombiesMapObjects.EMPTY : objects;
        List<SpawnSnapshot> spawns = new ArrayList<>();
        for (int i = 0; i < resolved.initialSpawns().size(); i++) {
            spawns.add(new SpawnSnapshot("", "initialSpawn", "INITIAL", 0, 0.0D, false));
        }
        resolved.zombieSpawns().stream()
                .map(spawn -> new SpawnSnapshot(
                        spawn.objectId(),
                        "zombieSpawn",
                        "",
                        spawn.group(),
                        spawn.weight(),
                        true))
                .forEach(spawns::add);
        List<BarrierSnapshot> barriers = resolved.barriers().stream()
                .map(barrier -> new BarrierSnapshot(barrier.objectId(), "barrier"))
                .toList();
        return new ZombiesMapSnapshot(roomId, mapName, hasEndTeleportPoint, spawns, barriers);
    }

    public static ZombiesMapSnapshot fromContributorContext(
            ZombiesMapValidationContributor.ZombiesMapValidationContext context
    ) {
        Objects.requireNonNull(context, "context");
        CommonModeMapData commonData = context.commonData();
        return new ZombiesMapSnapshot(
                context.roomId(),
                commonData.mapName(),
                commonData.fallbackExitPoint().isPresent(),
                extractSpawns(context.objects()),
                extractBarriers(context.objects()));
    }

    private static List<SpawnSnapshot> extractSpawns(List<ModeObjectData> objects) {
        List<SpawnSnapshot> spawns = new ArrayList<>();
        for (ModeObjectData object : objects) {
            CompoundTag payload = object.payload();
            String featureKey = object.featureKey();
            String feature = normalize(featureKey);
            boolean spawnFeature = feature.contains("spawn");
            boolean zombieSpawn = feature.contains("zombie") || booleanPayload(payload, "zombieSpawn")
                    || booleanPayload(payload, "zombie_spawn");
            if (!spawnFeature && !zombieSpawn && !payload.contains("spawnKind") && !payload.contains("kind")) {
                continue;
            }

            String kind = firstPayloadString(payload, "spawnKind", "kind", "Kind")
                    .orElse(spawnFeature ? "INITIAL" : "");
            int group = firstPayloadInt(payload, "group", "spawnGroup", "zombieGroup").orElse(0);
            double weight = firstPayloadDouble(payload, "weight", "spawnWeight").orElse(0.0D);
            spawns.add(new SpawnSnapshot(
                    objectId(object),
                    featureKey,
                    kind,
                    group,
                    weight,
                    zombieSpawn));
        }
        return spawns;
    }

    private static List<BarrierSnapshot> extractBarriers(List<ModeObjectData> objects) {
        List<BarrierSnapshot> barriers = new ArrayList<>();
        for (ModeObjectData object : objects) {
            String feature = normalize(object.featureKey());
            if (feature.contains("barrier") || feature.contains("door") || feature.contains("window")) {
                barriers.add(new BarrierSnapshot(objectId(object), object.featureKey()));
            }
        }
        return barriers;
    }

    private static String objectId(ModeObjectData object) {
        return firstPayloadString(object.payload(), "objectId", "object_id", "id")
                .orElse("");
    }

    private static Optional<String> firstPayloadString(CompoundTag payload, String... keys) {
        for (String key : keys) {
            if (payload.contains(key)) {
                String value = payload.getString(key).trim();
                if (!value.isEmpty()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> firstPayloadInt(CompoundTag payload, String... keys) {
        for (String key : keys) {
            if (payload.contains(key)) {
                return Optional.of(payload.getInt(key));
            }
        }
        return Optional.empty();
    }

    private static Optional<Double> firstPayloadDouble(CompoundTag payload, String... keys) {
        for (String key : keys) {
            if (payload.contains(key)) {
                return Optional.of(payload.getDouble(key));
            }
        }
        return Optional.empty();
    }

    private static boolean booleanPayload(CompoundTag payload, String key) {
        return payload.contains(key) && payload.getBoolean(key);
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").trim().toLowerCase(Locale.ROOT);
    }

    public record SpawnSnapshot(
            String objectId,
            String featureKey,
            String kind,
            int group,
            double weight,
            boolean zombieSpawn
    ) {
        public SpawnSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
            kind = Objects.requireNonNullElse(kind, "").trim();
        }

        public boolean initialPlayerSpawn() {
            return !zombieSpawn && "INITIAL".equalsIgnoreCase(kind);
        }

        public boolean dynamicPlayerSpawn() {
            String normalizedKind = normalize(kind);
            return !zombieSpawn && !normalizedKind.isEmpty() && !"initial".equals(normalizedKind);
        }
    }

    public record BarrierSnapshot(String objectId, String featureKey) {
        public BarrierSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
        }
    }
}
