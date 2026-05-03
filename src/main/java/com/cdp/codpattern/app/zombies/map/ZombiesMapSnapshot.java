package com.cdp.codpattern.app.zombies.map;

import com.cdp.codpattern.app.match.editor.ModeObjectData;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.persistence.CommonModeMapData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesUltimateMachineData;
import com.cdp.codpattern.app.zombies.validation.ZombiesMapValidationContributor;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        List<BarrierSnapshot> barriers,
        List<WeaponWallSnapshot> weaponWalls,
        List<AmmoBoxSnapshot> ammoBoxes,
        List<ArmorStationSnapshot> armorStations,
        List<PowerSwitchSnapshot> powerSwitches,
        List<SodaMachineSnapshot> sodaMachines,
        List<UltimateMachineSnapshot> ultimateMachines,
        List<ObjectIdSnapshot> extraObjects
) {
    public ZombiesMapSnapshot {
        Objects.requireNonNull(roomId, "roomId");
        mapName = Objects.requireNonNullElse(mapName, roomId.mapName()).trim();
        spawns = spawns == null ? List.of() : List.copyOf(spawns);
        barriers = barriers == null ? List.of() : List.copyOf(barriers);
        weaponWalls = weaponWalls == null ? List.of() : List.copyOf(weaponWalls);
        ammoBoxes = ammoBoxes == null ? List.of() : List.copyOf(ammoBoxes);
        armorStations = armorStations == null ? List.of() : List.copyOf(armorStations);
        powerSwitches = powerSwitches == null ? List.of() : List.copyOf(powerSwitches);
        sodaMachines = sodaMachines == null ? List.of() : List.copyOf(sodaMachines);
        ultimateMachines = ultimateMachines == null ? List.of() : List.copyOf(ultimateMachines);
        extraObjects = extraObjects == null ? List.of() : List.copyOf(extraObjects);
    }

    public ZombiesMapSnapshot(
            RoomId roomId,
            String mapName,
            boolean hasEndTeleportPoint,
            List<SpawnSnapshot> spawns,
            List<BarrierSnapshot> barriers
    ) {
        this(
                roomId,
                mapName,
                hasEndTeleportPoint,
                spawns,
                barriers,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
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

    public static ZombiesMapSnapshot of(
            RoomId roomId,
            String mapName,
            boolean hasEndTeleportPoint,
            List<SpawnSnapshot> spawns,
            List<BarrierSnapshot> barriers,
            List<WeaponWallSnapshot> weaponWalls,
            List<AmmoBoxSnapshot> ammoBoxes,
            List<ArmorStationSnapshot> armorStations,
            List<PowerSwitchSnapshot> powerSwitches,
            List<SodaMachineSnapshot> sodaMachines,
            List<UltimateMachineSnapshot> ultimateMachines,
            List<ObjectIdSnapshot> extraObjects
    ) {
        return new ZombiesMapSnapshot(
                roomId,
                mapName,
                hasEndTeleportPoint,
                spawns,
                barriers,
                weaponWalls,
                ammoBoxes,
                armorStations,
                powerSwitches,
                sodaMachines,
                ultimateMachines,
                extraObjects);
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
                .map(barrier -> new BarrierSnapshot(
                        barrier.objectId(),
                        "barrier",
                        barrier.group(),
                        barrier.cost()))
                .toList();
        List<WeaponWallSnapshot> weaponWalls = resolved.weaponWalls().stream()
                .map(weaponWall -> new WeaponWallSnapshot(
                        weaponWall.objectId(),
                        "weaponWall",
                        weaponWall.weaponLevel(),
                        weaponWall.levelDamageMultiplier(),
                        weaponWall.price(),
                        weaponWall.refreshWaves(),
                        weaponWall.rarityPools().stream()
                                .map(pool -> new RarityPoolSnapshot(
                                        pool.id(),
                                        pool.rank(),
                                        pool.baseWeight(),
                                        pool.waveFactor()))
                                .toList(),
                        weaponWall.weapons().stream()
                                .map(candidate -> new WeaponCandidateSnapshot(
                                        candidate.gunId(),
                                        candidate.weightsByRarity()))
                                .toList()))
                .toList();
        List<AmmoBoxSnapshot> ammoBoxes = resolved.ammoBoxes().stream()
                .map(ammoBox -> new AmmoBoxSnapshot(
                        ammoBox.objectId(),
                        "ammoBox",
                        ammoBox.pricesByWeaponLevel()))
                .toList();
        List<ArmorStationSnapshot> armorStations = resolved.armorStations().stream()
                .map(armorStation -> new ArmorStationSnapshot(
                        armorStation.objectId(),
                        "armorStation",
                        armorStation.armorLevel(),
                        armorStation.buyCost(),
                        armorStation.damageTakenMultiplier()))
                .toList();
        List<PowerSwitchSnapshot> powerSwitches = resolved.powerSwitch().stream()
                .map(powerSwitch -> new PowerSwitchSnapshot(
                        powerSwitch.objectId(),
                        "powerSwitch",
                        powerSwitch.cost(),
                        powerSwitch.block()))
                .toList();
        List<SodaMachineSnapshot> sodaMachines = resolved.sodaMachines().stream()
                .map(soda -> new SodaMachineSnapshot(
                        soda.objectId(),
                        "sodaMachine",
                        soda.buffId(),
                        soda.cost(),
                        soda.requiresPower()))
                .toList();
        List<UltimateMachineSnapshot> ultimateMachines = resolved.ultimateMachines().stream()
                .map(ultimate -> new UltimateMachineSnapshot(
                        ultimate.objectId(),
                        "ultimateMachine",
                        ultimate.maxUpgradeLevel(),
                        upgradeLevels(ultimate),
                        ultimate.requiresPower()))
                .toList();
        List<ObjectIdSnapshot> extraObjects = new ArrayList<>();
        resolved.mysteryBoxes().stream()
                .map(mysteryBox -> new ObjectIdSnapshot(mysteryBox.objectId(), "mysteryBox"))
                .forEach(extraObjects::add);
        resolved.windows().stream()
                .map(window -> new ObjectIdSnapshot(window.objectId(), "window"))
                .forEach(extraObjects::add);
        return new ZombiesMapSnapshot(
                roomId,
                mapName,
                hasEndTeleportPoint,
                spawns,
                barriers,
                weaponWalls,
                ammoBoxes,
                armorStations,
                powerSwitches,
                sodaMachines,
                ultimateMachines,
                extraObjects);
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

    private static Map<String, UltimateLevelSnapshot> upgradeLevels(ZombiesUltimateMachineData ultimate) {
        Map<String, UltimateLevelSnapshot> levels = new LinkedHashMap<>();
        ultimate.levels().forEach((level, data) ->
                levels.put(level, new UltimateLevelSnapshot(data.cost(), data.damageMultiplier())));
        return Map.copyOf(levels);
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
                barriers.add(new BarrierSnapshot(
                        objectId(object),
                        object.featureKey(),
                        firstPayloadInt(object.payload(), "group", "barrierGroup").orElse(1),
                        firstPayloadInt(object.payload(), "cost", "buyCost", "price").orElse(0)));
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

    public record BarrierSnapshot(String objectId, String featureKey, int group, int cost) {
        public BarrierSnapshot(String objectId, String featureKey) {
            this(objectId, featureKey, 1, 0);
        }

        public BarrierSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
        }
    }

    public record WeaponWallSnapshot(
            String objectId,
            String featureKey,
            int weaponLevel,
            double levelDamageMultiplier,
            int price,
            List<Integer> refreshWaves,
            List<RarityPoolSnapshot> rarityPools,
            List<WeaponCandidateSnapshot> weapons
    ) {
        public WeaponWallSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
            refreshWaves = refreshWaves == null ? List.of() : List.copyOf(refreshWaves);
            rarityPools = rarityPools == null ? List.of() : List.copyOf(rarityPools);
            weapons = weapons == null ? List.of() : List.copyOf(weapons);
        }
    }

    public record RarityPoolSnapshot(
            String id,
            int rank,
            double baseWeight,
            double waveFactor
    ) {
        public RarityPoolSnapshot {
            id = Objects.requireNonNullElse(id, "").trim();
        }
    }

    public record WeaponCandidateSnapshot(String gunId, Map<String, Double> weightsByRarity) {
        public WeaponCandidateSnapshot {
            gunId = Objects.requireNonNullElse(gunId, "").trim();
            weightsByRarity = weightsByRarity == null ? Map.of() : Map.copyOf(weightsByRarity);
        }
    }

    public record AmmoBoxSnapshot(String objectId, String featureKey, Map<String, Integer> pricesByWeaponLevel) {
        public AmmoBoxSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
            pricesByWeaponLevel = pricesByWeaponLevel == null ? Map.of() : Map.copyOf(pricesByWeaponLevel);
        }
    }

    public record ArmorStationSnapshot(
            String objectId,
            String featureKey,
            int armorLevel,
            int buyCost,
            double damageTakenMultiplier
    ) {
        public ArmorStationSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
        }
    }

    public record PowerSwitchSnapshot(String objectId, String featureKey, int cost, String block) {
        public PowerSwitchSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
            block = Objects.requireNonNullElse(block, "").trim();
        }
    }

    public record SodaMachineSnapshot(
            String objectId,
            String featureKey,
            String buffId,
            int cost,
            boolean requiresPower
    ) {
        public SodaMachineSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
            buffId = Objects.requireNonNullElse(buffId, "").trim();
        }
    }

    public record UltimateMachineSnapshot(
            String objectId,
            String featureKey,
            int maxUpgradeLevel,
            Map<String, UltimateLevelSnapshot> levels,
            boolean requiresPower
    ) {
        public UltimateMachineSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
            levels = levels == null ? Map.of() : Map.copyOf(levels);
        }
    }

    public record UltimateLevelSnapshot(int cost, double damageMultiplier) {
    }

    public record ObjectIdSnapshot(String objectId, String featureKey) {
        public ObjectIdSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
        }
    }
}
