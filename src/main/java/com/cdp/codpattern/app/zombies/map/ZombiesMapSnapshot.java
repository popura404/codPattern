package com.cdp.codpattern.app.zombies.map;

import com.cdp.codpattern.app.match.editor.ModeObjectData;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.persistence.CommonModeMapData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesUltimateMachineData;
import com.cdp.codpattern.app.zombies.validation.ZombiesMapValidationContributor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;

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
        String mapDimensionId,
        BoundsSnapshot mapBounds,
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
        mapDimensionId = normalizeDimensionId(mapDimensionId);
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
                "",
                null,
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

    public ZombiesMapSnapshot(
            RoomId roomId,
            String mapName,
            boolean hasEndTeleportPoint,
            String mapDimensionId,
            BoundsSnapshot mapBounds,
            List<SpawnSnapshot> spawns,
            List<BarrierSnapshot> barriers
    ) {
        this(
                roomId,
                mapName,
                hasEndTeleportPoint,
                mapDimensionId,
                mapBounds,
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
            String mapDimensionId,
            BoundsSnapshot mapBounds,
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
                mapDimensionId,
                mapBounds,
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
                "",
                null,
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
        return fromMapObjects(roomId, mapName, hasEndTeleportPoint, "", null, objects);
    }

    public static ZombiesMapSnapshot fromMapObjects(
            RoomId roomId,
            String mapName,
            boolean hasEndTeleportPoint,
            String mapDimensionId,
            BoundsSnapshot mapBounds,
            ZombiesMapObjects objects
    ) {
        ZombiesMapObjects resolved = objects == null ? ZombiesMapObjects.EMPTY : objects;
        List<SpawnSnapshot> spawns = new ArrayList<>();
        for (int i = 0; i < resolved.initialSpawns().size(); i++) {
            var initialSpawn = resolved.initialSpawns().get(i);
            spawns.add(new SpawnSnapshot(
                    "",
                    "initialSpawn",
                    "INITIAL",
                    0,
                    0.0D,
                    false,
                    initialSpawn.dimension(),
                    initialSpawn.pos()));
        }
        resolved.zombieSpawns().stream()
                .map(spawn -> new SpawnSnapshot(
                        spawn.objectId(),
                        "zombieSpawn",
                        "",
                        spawn.group(),
                        spawn.weight(),
                        true,
                        spawn.dimension(),
                        spawn.pos()))
                .forEach(spawns::add);
        List<BarrierSnapshot> barriers = resolved.barriers().stream()
                .map(barrier -> new BarrierSnapshot(
                        barrier.objectId(),
                        "barrier",
                        barrier.group(),
                        barrier.cost(),
                        barrier.dimension(),
                        barrier.interactionPos(),
                        barrier.areaFrom(),
                        barrier.areaTo()))
                .toList();
        List<WeaponWallSnapshot> weaponWalls = resolved.weaponWalls().stream()
                .map(weaponWall -> new WeaponWallSnapshot(
                        weaponWall.objectId(),
                        "weaponWall",
                        weaponWall.weaponLevel(),
                        weaponWall.levelDamageMultiplier(),
                        weaponWall.price(),
                        weaponWall.maxReserveAmmo(),
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
                                .toList(),
                        weaponWall.dimension(),
                        weaponWall.pos()))
                .toList();
        List<AmmoBoxSnapshot> ammoBoxes = resolved.ammoBoxes().stream()
                .map(ammoBox -> new AmmoBoxSnapshot(
                        ammoBox.objectId(),
                        "ammoBox",
                        ammoBox.pricesByWeaponLevel(),
                        ammoBox.dimension(),
                        ammoBox.pos()))
                .toList();
        List<ArmorStationSnapshot> armorStations = resolved.armorStations().stream()
                .map(armorStation -> new ArmorStationSnapshot(
                        armorStation.objectId(),
                        "armorStation",
                        armorStation.armorLevel(),
                        armorStation.buyCost(),
                        armorStation.damageTakenMultiplier(),
                        armorStation.dimension(),
                        armorStation.pos()))
                .toList();
        List<PowerSwitchSnapshot> powerSwitches = resolved.powerSwitch().stream()
                .map(powerSwitch -> new PowerSwitchSnapshot(
                        powerSwitch.objectId(),
                        "powerSwitch",
                        powerSwitch.cost(),
                        powerSwitch.block(),
                        powerSwitch.dimension(),
                        powerSwitch.pos()))
                .toList();
        List<SodaMachineSnapshot> sodaMachines = resolved.sodaMachines().stream()
                .map(soda -> new SodaMachineSnapshot(
                        soda.objectId(),
                        "sodaMachine",
                        soda.buffId(),
                        soda.cost(),
                        soda.requiresPower(),
                        soda.dimension(),
                        soda.pos()))
                .toList();
        List<UltimateMachineSnapshot> ultimateMachines = resolved.ultimateMachines().stream()
                .map(ultimate -> new UltimateMachineSnapshot(
                        ultimate.objectId(),
                        "ultimateMachine",
                        ultimate.maxUpgradeLevel(),
                        upgradeLevels(ultimate),
                        ultimate.requiresPower(),
                        ultimate.dimension(),
                        ultimate.pos()))
                .toList();
        List<ObjectIdSnapshot> extraObjects = new ArrayList<>();
        resolved.mysteryBoxes().stream()
                .map(mysteryBox -> new ObjectIdSnapshot(
                        mysteryBox.objectId(),
                        "mysteryBox",
                        mysteryBox.dimension(),
                        mysteryBox.pos()))
                .forEach(extraObjects::add);
        resolved.windows().stream()
                .map(window -> new ObjectIdSnapshot(
                        window.objectId(),
                        "window",
                        window.dimension(),
                        window.interactionPos().orElse(window.areaFrom())))
                .forEach(extraObjects::add);
        return new ZombiesMapSnapshot(
                roomId,
                mapName,
                hasEndTeleportPoint,
                mapDimensionId,
                mapBounds,
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
                commonData.levelName(),
                BoundsSnapshot.fromAreaData(commonData.areaData()),
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
                    zombieSpawn,
                    object.dimension(),
                    object.position()));
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
                        firstPayloadInt(object.payload(), "cost", "buyCost", "price").orElse(0),
                        object.dimension(),
                        object.position()));
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

    private static String dimensionId(ResourceKey<Level> dimension) {
        return dimension == null || dimension.location() == null ? "" : dimension.location().toString();
    }

    private static String normalizeDimensionId(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }

    public record BoundsSnapshot(BlockPos min, BlockPos max) {
        public BoundsSnapshot {
            Objects.requireNonNull(min, "min");
            Objects.requireNonNull(max, "max");
            BlockPos first = min;
            BlockPos second = max;
            min = new BlockPos(
                    Math.min(first.getX(), second.getX()),
                    Math.min(first.getY(), second.getY()),
                    Math.min(first.getZ(), second.getZ()));
            max = new BlockPos(
                    Math.max(first.getX(), second.getX()),
                    Math.max(first.getY(), second.getY()),
                    Math.max(first.getZ(), second.getZ()));
        }

        public static BoundsSnapshot fromAreaData(AreaData areaData) {
            return areaData == null ? null : new BoundsSnapshot(areaData.pos1(), areaData.pos2());
        }

        public boolean contains(BlockPos pos) {
            return pos != null
                    && pos.getX() >= min.getX()
                    && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY()
                    && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ()
                    && pos.getZ() <= max.getZ();
        }
    }

    public record SpawnSnapshot(
            String objectId,
            String featureKey,
            String kind,
            int group,
            double weight,
            boolean zombieSpawn,
            String dimensionId,
            BlockPos pos
    ) {
        public SpawnSnapshot(
                String objectId,
                String featureKey,
                String kind,
                int group,
                double weight,
                boolean zombieSpawn
        ) {
            this(objectId, featureKey, kind, group, weight, zombieSpawn, "", null);
        }

        public SpawnSnapshot(
                String objectId,
                String featureKey,
                String kind,
                int group,
                double weight,
                boolean zombieSpawn,
                ResourceKey<Level> dimension,
                BlockPos pos
        ) {
            this(objectId, featureKey, kind, group, weight, zombieSpawn, ZombiesMapSnapshot.dimensionId(dimension), pos);
        }

        public SpawnSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
            kind = Objects.requireNonNullElse(kind, "").trim();
            dimensionId = normalizeDimensionId(dimensionId);
        }

        public boolean initialPlayerSpawn() {
            return !zombieSpawn && "INITIAL".equalsIgnoreCase(kind);
        }

        public boolean dynamicPlayerSpawn() {
            String normalizedKind = normalize(kind);
            return !zombieSpawn && !normalizedKind.isEmpty() && !"initial".equals(normalizedKind);
        }
    }

    public record BarrierSnapshot(
            String objectId,
            String featureKey,
            int group,
            int cost,
            String dimensionId,
            BlockPos pos,
            BlockPos areaFrom,
            BlockPos areaTo
    ) {
        public BarrierSnapshot(String objectId, String featureKey) {
            this(objectId, featureKey, 1, 0);
        }

        public BarrierSnapshot(String objectId, String featureKey, int group, int cost) {
            this(objectId, featureKey, group, cost, "", null, null, null);
        }

        public BarrierSnapshot(
                String objectId,
                String featureKey,
                int group,
                int cost,
                ResourceKey<Level> dimension,
                BlockPos pos
        ) {
            this(objectId, featureKey, group, cost, dimension, pos, pos, pos);
        }

        public BarrierSnapshot(
                String objectId,
                String featureKey,
                int group,
                int cost,
                ResourceKey<Level> dimension,
                BlockPos pos,
                BlockPos areaFrom,
                BlockPos areaTo
        ) {
            this(
                    objectId,
                    featureKey,
                    group,
                    cost,
                    ZombiesMapSnapshot.dimensionId(dimension),
                    pos,
                    areaFrom,
                    areaTo);
        }

        public BarrierSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
            dimensionId = normalizeDimensionId(dimensionId);
        }
    }

    public record WeaponWallSnapshot(
            String objectId,
            String featureKey,
            int weaponLevel,
            double levelDamageMultiplier,
            int price,
            int maxReserveAmmo,
            List<Integer> refreshWaves,
            List<RarityPoolSnapshot> rarityPools,
            List<WeaponCandidateSnapshot> weapons,
            String dimensionId,
            BlockPos pos
    ) {
        public WeaponWallSnapshot(
                String objectId,
                String featureKey,
                int weaponLevel,
                double levelDamageMultiplier,
                int price,
                List<Integer> refreshWaves,
                List<RarityPoolSnapshot> rarityPools,
                List<WeaponCandidateSnapshot> weapons
        ) {
            this(
                    objectId,
                    featureKey,
                    weaponLevel,
                    levelDamageMultiplier,
                    price,
                    0,
                    refreshWaves,
                    rarityPools,
                    weapons);
        }

        public WeaponWallSnapshot(
                String objectId,
                String featureKey,
                int weaponLevel,
                double levelDamageMultiplier,
                int price,
                int maxReserveAmmo,
                List<Integer> refreshWaves,
                List<RarityPoolSnapshot> rarityPools,
                List<WeaponCandidateSnapshot> weapons
        ) {
            this(
                    objectId,
                    featureKey,
                    weaponLevel,
                    levelDamageMultiplier,
                    price,
                    maxReserveAmmo,
                    refreshWaves,
                    rarityPools,
                    weapons,
                    "",
                    null);
        }

        public WeaponWallSnapshot(
                String objectId,
                String featureKey,
                int weaponLevel,
                double levelDamageMultiplier,
                int price,
                int maxReserveAmmo,
                List<Integer> refreshWaves,
                List<RarityPoolSnapshot> rarityPools,
                List<WeaponCandidateSnapshot> weapons,
                ResourceKey<Level> dimension,
                BlockPos pos
        ) {
            this(
                    objectId,
                    featureKey,
                    weaponLevel,
                    levelDamageMultiplier,
                    price,
                    maxReserveAmmo,
                    refreshWaves,
                    rarityPools,
                    weapons,
                    ZombiesMapSnapshot.dimensionId(dimension),
                    pos);
        }

        public WeaponWallSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
            refreshWaves = refreshWaves == null ? List.of() : List.copyOf(refreshWaves);
            rarityPools = rarityPools == null ? List.of() : List.copyOf(rarityPools);
            weapons = weapons == null ? List.of() : List.copyOf(weapons);
            dimensionId = normalizeDimensionId(dimensionId);
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

    public record AmmoBoxSnapshot(
            String objectId,
            String featureKey,
            Map<String, Integer> pricesByWeaponLevel,
            String dimensionId,
            BlockPos pos
    ) {
        public AmmoBoxSnapshot(String objectId, String featureKey, Map<String, Integer> pricesByWeaponLevel) {
            this(objectId, featureKey, pricesByWeaponLevel, "", null);
        }

        public AmmoBoxSnapshot(
                String objectId,
                String featureKey,
                Map<String, Integer> pricesByWeaponLevel,
                ResourceKey<Level> dimension,
                BlockPos pos
        ) {
            this(objectId, featureKey, pricesByWeaponLevel, ZombiesMapSnapshot.dimensionId(dimension), pos);
        }

        public AmmoBoxSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
            pricesByWeaponLevel = pricesByWeaponLevel == null ? Map.of() : Map.copyOf(pricesByWeaponLevel);
            dimensionId = normalizeDimensionId(dimensionId);
        }
    }

    public record ArmorStationSnapshot(
            String objectId,
            String featureKey,
            int armorLevel,
            int buyCost,
            double damageTakenMultiplier,
            String dimensionId,
            BlockPos pos
    ) {
        public ArmorStationSnapshot(
                String objectId,
                String featureKey,
                int armorLevel,
                int buyCost,
                double damageTakenMultiplier
        ) {
            this(objectId, featureKey, armorLevel, buyCost, damageTakenMultiplier, "", null);
        }

        public ArmorStationSnapshot(
                String objectId,
                String featureKey,
                int armorLevel,
                int buyCost,
                double damageTakenMultiplier,
                ResourceKey<Level> dimension,
                BlockPos pos
        ) {
            this(objectId, featureKey, armorLevel, buyCost, damageTakenMultiplier, ZombiesMapSnapshot.dimensionId(dimension), pos);
        }

        public ArmorStationSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
            dimensionId = normalizeDimensionId(dimensionId);
        }
    }

    public record PowerSwitchSnapshot(String objectId, String featureKey, int cost, String block, String dimensionId, BlockPos pos) {
        public PowerSwitchSnapshot(String objectId, String featureKey, int cost, String block) {
            this(objectId, featureKey, cost, block, "", null);
        }

        public PowerSwitchSnapshot(
                String objectId,
                String featureKey,
                int cost,
                String block,
                ResourceKey<Level> dimension,
                BlockPos pos
        ) {
            this(objectId, featureKey, cost, block, ZombiesMapSnapshot.dimensionId(dimension), pos);
        }

        public PowerSwitchSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
            block = Objects.requireNonNullElse(block, "").trim();
            dimensionId = normalizeDimensionId(dimensionId);
        }
    }

    public record SodaMachineSnapshot(
            String objectId,
            String featureKey,
            String buffId,
            int cost,
            boolean requiresPower,
            String dimensionId,
            BlockPos pos
    ) {
        public SodaMachineSnapshot(
                String objectId,
                String featureKey,
                String buffId,
                int cost,
                boolean requiresPower
        ) {
            this(objectId, featureKey, buffId, cost, requiresPower, "", null);
        }

        public SodaMachineSnapshot(
                String objectId,
                String featureKey,
                String buffId,
                int cost,
                boolean requiresPower,
                ResourceKey<Level> dimension,
                BlockPos pos
        ) {
            this(objectId, featureKey, buffId, cost, requiresPower, ZombiesMapSnapshot.dimensionId(dimension), pos);
        }

        public SodaMachineSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
            buffId = Objects.requireNonNullElse(buffId, "").trim();
            dimensionId = normalizeDimensionId(dimensionId);
        }
    }

    public record UltimateMachineSnapshot(
            String objectId,
            String featureKey,
            int maxUpgradeLevel,
            Map<String, UltimateLevelSnapshot> levels,
            boolean requiresPower,
            String dimensionId,
            BlockPos pos
    ) {
        public UltimateMachineSnapshot(
                String objectId,
                String featureKey,
                int maxUpgradeLevel,
                Map<String, UltimateLevelSnapshot> levels,
                boolean requiresPower
        ) {
            this(objectId, featureKey, maxUpgradeLevel, levels, requiresPower, "", null);
        }

        public UltimateMachineSnapshot(
                String objectId,
                String featureKey,
                int maxUpgradeLevel,
                Map<String, UltimateLevelSnapshot> levels,
                boolean requiresPower,
                ResourceKey<Level> dimension,
                BlockPos pos
        ) {
            this(objectId, featureKey, maxUpgradeLevel, levels, requiresPower, ZombiesMapSnapshot.dimensionId(dimension), pos);
        }

        public UltimateMachineSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
            levels = levels == null ? Map.of() : Map.copyOf(levels);
            dimensionId = normalizeDimensionId(dimensionId);
        }
    }

    public record UltimateLevelSnapshot(int cost, double damageMultiplier) {
    }

    public record ObjectIdSnapshot(String objectId, String featureKey, String dimensionId, BlockPos pos) {
        public ObjectIdSnapshot(String objectId, String featureKey) {
            this(objectId, featureKey, "", null);
        }

        public ObjectIdSnapshot(String objectId, String featureKey, ResourceKey<Level> dimension, BlockPos pos) {
            this(objectId, featureKey, ZombiesMapSnapshot.dimensionId(dimension), pos);
        }

        public ObjectIdSnapshot {
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            featureKey = Objects.requireNonNullElse(featureKey, "").trim();
            dimensionId = normalizeDimensionId(dimensionId);
        }
    }
}
