package com.cdp.codpattern.app.zombies.deploy;

import com.cdp.codpattern.app.zombies.map.ZombiesMapObjects;
import com.cdp.codpattern.app.zombies.map.object.ZombiesAmmoBoxData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesArmorStationData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesInitialSpawnData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesPowerSwitchData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesSodaMachineData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesUltimateMachineData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesWeaponWallData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesZombieSpawnData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class ZombiesDeployObjectEditor {
    private static final ObjectRef NO_EXCLUSION = new ObjectRef("", -1);

    private ZombiesDeployObjectEditor() {
    }

    enum Operation {
        ADD,
        UPDATE,
        DUPLICATE,
        DELETE,
        CLEAR
    }

    static EditResult edit(
            ZombiesMapObjects current,
            Operation operation,
            String objectType,
            int selectedIndex,
            Map<String, String> fields
    ) {
        ZombiesMapObjects objects = current == null ? ZombiesMapObjects.EMPTY : current;
        Operation resolvedOperation = operation == null ? Operation.ADD : operation;
        String type = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        Map<String, String> resolvedFields = mergedFields(type, fields);
        try {
            return switch (resolvedOperation) {
                case ADD -> add(objects, type, resolvedFields);
                case UPDATE -> update(objects, type, selectedIndex, resolvedFields);
                case DUPLICATE -> duplicate(objects, type, selectedIndex);
                case DELETE -> delete(objects, type, selectedIndex);
                case CLEAR -> clear(objects, type);
            };
        } catch (EditFailure failure) {
            return EditResult.failure(failure.code, failure.getMessage(), objects, selectedIndex, resolvedFields);
        }
    }

    private static EditResult add(ZombiesMapObjects objects, String type, Map<String, String> fields) {
        return switch (type) {
            case ZombiesDeployFieldSchema.INITIAL -> {
                ZombiesInitialSpawnData data = parseInitial(fields);
                List<ZombiesInitialSpawnData> next = mutable(objects.initialSpawns());
                next.add(data);
                yield success(withInitialSpawns(objects, next), type, next.size() - 1, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> {
                ZombiesZombieSpawnData data = parseZombieSpawn(objects, NO_EXCLUSION, fields);
                List<ZombiesZombieSpawnData> next = mutable(objects.zombieSpawns());
                next.add(data);
                yield success(withZombieSpawns(objects, next), type, next.size() - 1, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.BARRIER -> {
                ZombiesBarrierData data = parseBarrier(objects, NO_EXCLUSION, fields);
                List<ZombiesBarrierData> next = mutable(objects.barriers());
                next.add(data);
                yield success(withBarriers(objects, next), type, next.size() - 1, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.WEAPON_WALL -> {
                ZombiesWeaponWallData data = parseWeaponWall(objects, NO_EXCLUSION, fields);
                List<ZombiesWeaponWallData> next = mutable(objects.weaponWalls());
                next.add(data);
                yield success(withWeaponWalls(objects, next), type, next.size() - 1, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.AMMO_BOX -> {
                ZombiesAmmoBoxData data = parseAmmoBox(objects, NO_EXCLUSION, fields);
                List<ZombiesAmmoBoxData> next = mutable(objects.ammoBoxes());
                next.add(data);
                yield success(withAmmoBoxes(objects, next), type, next.size() - 1, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.ARMOR_STATION -> {
                ZombiesArmorStationData data = parseArmorStation(objects, NO_EXCLUSION, fields);
                List<ZombiesArmorStationData> next = mutable(objects.armorStations());
                next.add(data);
                yield success(withArmorStations(objects, next), type, next.size() - 1, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.POWER_SWITCH -> {
                if (objects.powerSwitch().isPresent()) {
                    throw failure("object.single_exists", "power_switch already exists; update or delete it first");
                }
                ZombiesPowerSwitchData data = parsePowerSwitch(objects, NO_EXCLUSION, fields);
                yield success(withPowerSwitch(objects, Optional.of(data)), type, 0, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.SODA_MACHINE -> {
                ZombiesSodaMachineData data = parseSodaMachine(objects, NO_EXCLUSION, fields);
                List<ZombiesSodaMachineData> next = mutable(objects.sodaMachines());
                next.add(data);
                yield success(withSodaMachines(objects, next), type, next.size() - 1, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> {
                ZombiesUltimateMachineData data = parseUltimateMachine(objects, NO_EXCLUSION, fields);
                List<ZombiesUltimateMachineData> next = mutable(objects.ultimateMachines());
                next.add(data);
                yield success(withUltimateMachines(objects, next), type, next.size() - 1, fieldsFrom(data), 1);
            }
            default -> unsupported(type, objects, fields);
        };
    }

    private static EditResult update(
            ZombiesMapObjects objects,
            String type,
            int selectedIndex,
            Map<String, String> fields
    ) {
        return switch (type) {
            case ZombiesDeployFieldSchema.INITIAL -> {
                requireIndex(type, selectedIndex, objects.initialSpawns().size());
                ZombiesInitialSpawnData data = parseInitial(fields);
                List<ZombiesInitialSpawnData> next = mutable(objects.initialSpawns());
                next.set(selectedIndex, data);
                yield success(withInitialSpawns(objects, next), type, selectedIndex, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> {
                requireIndex(type, selectedIndex, objects.zombieSpawns().size());
                ZombiesZombieSpawnData data = parseZombieSpawn(objects, new ObjectRef(type, selectedIndex), fields);
                List<ZombiesZombieSpawnData> next = mutable(objects.zombieSpawns());
                next.set(selectedIndex, data);
                yield success(withZombieSpawns(objects, next), type, selectedIndex, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.BARRIER -> {
                requireIndex(type, selectedIndex, objects.barriers().size());
                ZombiesBarrierData data = parseBarrier(objects, new ObjectRef(type, selectedIndex), fields);
                List<ZombiesBarrierData> next = mutable(objects.barriers());
                next.set(selectedIndex, data);
                yield success(withBarriers(objects, next), type, selectedIndex, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.WEAPON_WALL -> {
                requireIndex(type, selectedIndex, objects.weaponWalls().size());
                ZombiesWeaponWallData data = parseWeaponWall(objects, new ObjectRef(type, selectedIndex), fields);
                List<ZombiesWeaponWallData> next = mutable(objects.weaponWalls());
                next.set(selectedIndex, data);
                yield success(withWeaponWalls(objects, next), type, selectedIndex, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.AMMO_BOX -> {
                requireIndex(type, selectedIndex, objects.ammoBoxes().size());
                ZombiesAmmoBoxData data = parseAmmoBox(objects, new ObjectRef(type, selectedIndex), fields);
                List<ZombiesAmmoBoxData> next = mutable(objects.ammoBoxes());
                next.set(selectedIndex, data);
                yield success(withAmmoBoxes(objects, next), type, selectedIndex, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.ARMOR_STATION -> {
                requireIndex(type, selectedIndex, objects.armorStations().size());
                ZombiesArmorStationData data = parseArmorStation(objects, new ObjectRef(type, selectedIndex), fields);
                List<ZombiesArmorStationData> next = mutable(objects.armorStations());
                next.set(selectedIndex, data);
                yield success(withArmorStations(objects, next), type, selectedIndex, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.POWER_SWITCH -> {
                if (objects.powerSwitch().isEmpty()) {
                    throw failure("object.invalid_index", "power_switch does not exist");
                }
                ZombiesPowerSwitchData data = parsePowerSwitch(objects, new ObjectRef(type, 0), fields);
                yield success(withPowerSwitch(objects, Optional.of(data)), type, 0, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.SODA_MACHINE -> {
                requireIndex(type, selectedIndex, objects.sodaMachines().size());
                ZombiesSodaMachineData data = parseSodaMachine(objects, new ObjectRef(type, selectedIndex), fields);
                List<ZombiesSodaMachineData> next = mutable(objects.sodaMachines());
                next.set(selectedIndex, data);
                yield success(withSodaMachines(objects, next), type, selectedIndex, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> {
                requireIndex(type, selectedIndex, objects.ultimateMachines().size());
                ZombiesUltimateMachineData data = parseUltimateMachine(objects, new ObjectRef(type, selectedIndex), fields);
                List<ZombiesUltimateMachineData> next = mutable(objects.ultimateMachines());
                next.set(selectedIndex, data);
                yield success(withUltimateMachines(objects, next), type, selectedIndex, fieldsFrom(data), 1);
            }
            default -> unsupported(type, objects, fields);
        };
    }

    private static EditResult duplicate(ZombiesMapObjects objects, String type, int selectedIndex) {
        return switch (type) {
            case ZombiesDeployFieldSchema.INITIAL -> {
                requireIndex(type, selectedIndex, objects.initialSpawns().size());
                ZombiesInitialSpawnData data = objects.initialSpawns().get(selectedIndex);
                List<ZombiesInitialSpawnData> next = mutable(objects.initialSpawns());
                next.add(data);
                yield success(withInitialSpawns(objects, next), type, next.size() - 1, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> {
                requireIndex(type, selectedIndex, objects.zombieSpawns().size());
                ZombiesZombieSpawnData source = objects.zombieSpawns().get(selectedIndex);
                ZombiesZombieSpawnData data = new ZombiesZombieSpawnData(
                        copyObjectId(objects, source.objectId(), type),
                        source.group(),
                        source.weight(),
                        source.dimension(),
                        source.pos(),
                        source.yaw(),
                        source.pitch());
                List<ZombiesZombieSpawnData> next = mutable(objects.zombieSpawns());
                next.add(data);
                yield success(withZombieSpawns(objects, next), type, next.size() - 1, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.BARRIER -> {
                requireIndex(type, selectedIndex, objects.barriers().size());
                ZombiesBarrierData source = objects.barriers().get(selectedIndex);
                ZombiesBarrierData data = new ZombiesBarrierData(
                        copyObjectId(objects, source.objectId(), type),
                        source.group(),
                        source.cost(),
                        source.blocksPlayersOnly(),
                        source.dimension(),
                        source.areaFrom(),
                        source.areaTo(),
                        source.interactionPos());
                List<ZombiesBarrierData> next = mutable(objects.barriers());
                next.add(data);
                yield success(withBarriers(objects, next), type, next.size() - 1, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.WEAPON_WALL -> {
                requireIndex(type, selectedIndex, objects.weaponWalls().size());
                ZombiesWeaponWallData source = objects.weaponWalls().get(selectedIndex);
                ZombiesWeaponWallData data = new ZombiesWeaponWallData(
                        copyObjectId(objects, source.objectId(), type),
                        source.weaponLevel(),
                        source.levelDamageMultiplier(),
                        source.price(),
                        source.maxReserveAmmo(),
                        source.refreshWaves(),
                        source.rarityPools(),
                        source.weapons(),
                        source.dimension(),
                        source.pos(),
                        source.interactionPos());
                List<ZombiesWeaponWallData> next = mutable(objects.weaponWalls());
                next.add(data);
                yield success(withWeaponWalls(objects, next), type, next.size() - 1, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.AMMO_BOX -> {
                requireIndex(type, selectedIndex, objects.ammoBoxes().size());
                ZombiesAmmoBoxData source = objects.ammoBoxes().get(selectedIndex);
                ZombiesAmmoBoxData data = new ZombiesAmmoBoxData(
                        copyObjectId(objects, source.objectId(), type),
                        source.pricesByWeaponLevel(),
                        source.dimension(),
                        source.pos(),
                        source.interactionPos());
                List<ZombiesAmmoBoxData> next = mutable(objects.ammoBoxes());
                next.add(data);
                yield success(withAmmoBoxes(objects, next), type, next.size() - 1, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.ARMOR_STATION -> {
                requireIndex(type, selectedIndex, objects.armorStations().size());
                ZombiesArmorStationData source = objects.armorStations().get(selectedIndex);
                ZombiesArmorStationData data = new ZombiesArmorStationData(
                        copyObjectId(objects, source.objectId(), type),
                        source.armorLevel(),
                        source.buyCost(),
                        source.damageTakenMultiplier(),
                        source.dimension(),
                        source.pos(),
                        source.interactionPos());
                List<ZombiesArmorStationData> next = mutable(objects.armorStations());
                next.add(data);
                yield success(withArmorStations(objects, next), type, next.size() - 1, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.POWER_SWITCH ->
                    EditResult.failure("object.single_type", "power_switch cannot be duplicated", objects, 0, fieldsForSelection(objects, type, 0));
            case ZombiesDeployFieldSchema.SODA_MACHINE -> {
                requireIndex(type, selectedIndex, objects.sodaMachines().size());
                ZombiesSodaMachineData source = objects.sodaMachines().get(selectedIndex);
                ZombiesSodaMachineData data = new ZombiesSodaMachineData(
                        copyObjectId(objects, source.objectId(), type),
                        source.buffId(),
                        source.cost(),
                        source.requiresPower(),
                        source.dimension(),
                        source.pos(),
                        source.interactionPos());
                List<ZombiesSodaMachineData> next = mutable(objects.sodaMachines());
                next.add(data);
                yield success(withSodaMachines(objects, next), type, next.size() - 1, fieldsFrom(data), 1);
            }
            case ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> {
                requireIndex(type, selectedIndex, objects.ultimateMachines().size());
                ZombiesUltimateMachineData source = objects.ultimateMachines().get(selectedIndex);
                ZombiesUltimateMachineData data = new ZombiesUltimateMachineData(
                        copyObjectId(objects, source.objectId(), type),
                        source.maxUpgradeLevel(),
                        source.levels(),
                        source.requiresPower(),
                        source.dimension(),
                        source.pos(),
                        source.interactionPos());
                List<ZombiesUltimateMachineData> next = mutable(objects.ultimateMachines());
                next.add(data);
                yield success(withUltimateMachines(objects, next), type, next.size() - 1, fieldsFrom(data), 1);
            }
            default -> unsupported(type, objects, fieldsForSelection(objects, type, selectedIndex));
        };
    }

    private static EditResult delete(ZombiesMapObjects objects, String type, int selectedIndex) {
        return switch (type) {
            case ZombiesDeployFieldSchema.INITIAL -> {
                requireIndex(type, selectedIndex, objects.initialSpawns().size());
                List<ZombiesInitialSpawnData> next = mutable(objects.initialSpawns());
                next.remove(selectedIndex);
                ZombiesMapObjects updated = withInitialSpawns(objects, next);
                int nextIndex = nextIndexAfterDelete(selectedIndex, next.size());
                yield success(updated, type, nextIndex, fieldsForSelection(updated, type, nextIndex), 1);
            }
            case ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> {
                requireIndex(type, selectedIndex, objects.zombieSpawns().size());
                List<ZombiesZombieSpawnData> next = mutable(objects.zombieSpawns());
                next.remove(selectedIndex);
                ZombiesMapObjects updated = withZombieSpawns(objects, next);
                int nextIndex = nextIndexAfterDelete(selectedIndex, next.size());
                yield success(updated, type, nextIndex, fieldsForSelection(updated, type, nextIndex), 1);
            }
            case ZombiesDeployFieldSchema.BARRIER -> {
                requireIndex(type, selectedIndex, objects.barriers().size());
                List<ZombiesBarrierData> next = mutable(objects.barriers());
                next.remove(selectedIndex);
                ZombiesMapObjects updated = withBarriers(objects, next);
                int nextIndex = nextIndexAfterDelete(selectedIndex, next.size());
                yield success(updated, type, nextIndex, fieldsForSelection(updated, type, nextIndex), 1);
            }
            case ZombiesDeployFieldSchema.WEAPON_WALL -> {
                requireIndex(type, selectedIndex, objects.weaponWalls().size());
                List<ZombiesWeaponWallData> next = mutable(objects.weaponWalls());
                next.remove(selectedIndex);
                ZombiesMapObjects updated = withWeaponWalls(objects, next);
                int nextIndex = nextIndexAfterDelete(selectedIndex, next.size());
                yield success(updated, type, nextIndex, fieldsForSelection(updated, type, nextIndex), 1);
            }
            case ZombiesDeployFieldSchema.AMMO_BOX -> {
                requireIndex(type, selectedIndex, objects.ammoBoxes().size());
                List<ZombiesAmmoBoxData> next = mutable(objects.ammoBoxes());
                next.remove(selectedIndex);
                ZombiesMapObjects updated = withAmmoBoxes(objects, next);
                int nextIndex = nextIndexAfterDelete(selectedIndex, next.size());
                yield success(updated, type, nextIndex, fieldsForSelection(updated, type, nextIndex), 1);
            }
            case ZombiesDeployFieldSchema.ARMOR_STATION -> {
                requireIndex(type, selectedIndex, objects.armorStations().size());
                List<ZombiesArmorStationData> next = mutable(objects.armorStations());
                next.remove(selectedIndex);
                ZombiesMapObjects updated = withArmorStations(objects, next);
                int nextIndex = nextIndexAfterDelete(selectedIndex, next.size());
                yield success(updated, type, nextIndex, fieldsForSelection(updated, type, nextIndex), 1);
            }
            case ZombiesDeployFieldSchema.POWER_SWITCH -> {
                if (objects.powerSwitch().isEmpty()) {
                    throw failure("object.invalid_index", "power_switch does not exist");
                }
                ZombiesMapObjects updated = withPowerSwitch(objects, Optional.empty());
                yield success(updated, type, -1, mergedFields(type, Map.of()), 1);
            }
            case ZombiesDeployFieldSchema.SODA_MACHINE -> {
                requireIndex(type, selectedIndex, objects.sodaMachines().size());
                List<ZombiesSodaMachineData> next = mutable(objects.sodaMachines());
                next.remove(selectedIndex);
                ZombiesMapObjects updated = withSodaMachines(objects, next);
                int nextIndex = nextIndexAfterDelete(selectedIndex, next.size());
                yield success(updated, type, nextIndex, fieldsForSelection(updated, type, nextIndex), 1);
            }
            case ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> {
                requireIndex(type, selectedIndex, objects.ultimateMachines().size());
                List<ZombiesUltimateMachineData> next = mutable(objects.ultimateMachines());
                next.remove(selectedIndex);
                ZombiesMapObjects updated = withUltimateMachines(objects, next);
                int nextIndex = nextIndexAfterDelete(selectedIndex, next.size());
                yield success(updated, type, nextIndex, fieldsForSelection(updated, type, nextIndex), 1);
            }
            default -> unsupported(type, objects, fieldsForSelection(objects, type, selectedIndex));
        };
    }

    private static EditResult clear(ZombiesMapObjects objects, String type) {
        return switch (type) {
            case ZombiesDeployFieldSchema.INITIAL -> success(
                    withInitialSpawns(objects, List.of()),
                    type,
                    -1,
                    mergedFields(type, Map.of()),
                    objects.initialSpawns().size());
            case ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> success(
                    withZombieSpawns(objects, List.of()),
                    type,
                    -1,
                    mergedFields(type, Map.of()),
                    objects.zombieSpawns().size());
            case ZombiesDeployFieldSchema.BARRIER -> success(
                    withBarriers(objects, List.of()),
                    type,
                    -1,
                    mergedFields(type, Map.of()),
                    objects.barriers().size());
            case ZombiesDeployFieldSchema.WEAPON_WALL -> success(
                    withWeaponWalls(objects, List.of()),
                    type,
                    -1,
                    mergedFields(type, Map.of()),
                    objects.weaponWalls().size());
            case ZombiesDeployFieldSchema.AMMO_BOX -> success(
                    withAmmoBoxes(objects, List.of()),
                    type,
                    -1,
                    mergedFields(type, Map.of()),
                    objects.ammoBoxes().size());
            case ZombiesDeployFieldSchema.ARMOR_STATION -> success(
                    withArmorStations(objects, List.of()),
                    type,
                    -1,
                    mergedFields(type, Map.of()),
                    objects.armorStations().size());
            case ZombiesDeployFieldSchema.POWER_SWITCH -> success(
                    withPowerSwitch(objects, Optional.empty()),
                    type,
                    -1,
                    mergedFields(type, Map.of()),
                    objects.powerSwitch().isPresent() ? 1 : 0);
            case ZombiesDeployFieldSchema.SODA_MACHINE -> success(
                    withSodaMachines(objects, List.of()),
                    type,
                    -1,
                    mergedFields(type, Map.of()),
                    objects.sodaMachines().size());
            case ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> success(
                    withUltimateMachines(objects, List.of()),
                    type,
                    -1,
                    mergedFields(type, Map.of()),
                    objects.ultimateMachines().size());
            default -> unsupported(type, objects, mergedFields(type, Map.of()));
        };
    }

    private static ZombiesInitialSpawnData parseInitial(Map<String, String> fields) {
        return new ZombiesInitialSpawnData(
                dimension(fields),
                blockPos(fields, "pos"),
                floatField(fields, "yaw"),
                floatField(fields, "pitch"));
    }

    private static ZombiesZombieSpawnData parseZombieSpawn(
            ZombiesMapObjects objects,
            ObjectRef excluded,
            Map<String, String> fields
    ) {
        String objectId = resolveObjectId(objects, excluded, text(fields, "objectId"), ZombiesDeployFieldSchema.ZOMBIE_SPAWN);
        return new ZombiesZombieSpawnData(
                objectId,
                intField(fields, "group"),
                doubleField(fields, "weight"),
                dimension(fields),
                blockPos(fields, "pos"),
                floatField(fields, "yaw"),
                floatField(fields, "pitch"));
    }

    private static ZombiesBarrierData parseBarrier(
            ZombiesMapObjects objects,
            ObjectRef excluded,
            Map<String, String> fields
    ) {
        String objectId = resolveObjectId(objects, excluded, text(fields, "objectId"), ZombiesDeployFieldSchema.BARRIER);
        return new ZombiesBarrierData(
                objectId,
                intField(fields, "group"),
                intField(fields, "cost"),
                booleanField(fields, "blocksPlayersOnly"),
                dimension(fields),
                blockPos(fields, "areaFrom"),
                blockPos(fields, "areaTo"),
                blockPos(fields, "interaction"));
    }

    private static ZombiesAmmoBoxData parseAmmoBox(
            ZombiesMapObjects objects,
            ObjectRef excluded,
            Map<String, String> fields
    ) {
        String objectId = resolveObjectId(objects, excluded, text(fields, "objectId"), ZombiesDeployFieldSchema.AMMO_BOX);
        return new ZombiesAmmoBoxData(
                objectId,
                intMap(fields, "pricesByWeaponLevel"),
                dimension(fields),
                blockPos(fields, "pos"),
                Optional.of(blockPos(fields, "interaction")));
    }

    private static ZombiesWeaponWallData parseWeaponWall(
            ZombiesMapObjects objects,
            ObjectRef excluded,
            Map<String, String> fields
    ) {
        String objectId = resolveObjectId(objects, excluded, text(fields, "objectId"), ZombiesDeployFieldSchema.WEAPON_WALL);
        return new ZombiesWeaponWallData(
                objectId,
                intField(fields, "weaponLevel"),
                doubleField(fields, "levelDamageMultiplier"),
                intField(fields, "price"),
                intField(fields, "maxReserveAmmo"),
                intList(fields, "refreshWaves"),
                rarityPools(fields, "rarityPools"),
                weaponCandidates(fields, "weapons"),
                dimension(fields),
                blockPos(fields, "pos"),
                Optional.of(blockPos(fields, "interaction")));
    }

    private static ZombiesArmorStationData parseArmorStation(
            ZombiesMapObjects objects,
            ObjectRef excluded,
            Map<String, String> fields
    ) {
        String objectId = resolveObjectId(objects, excluded, text(fields, "objectId"), ZombiesDeployFieldSchema.ARMOR_STATION);
        return new ZombiesArmorStationData(
                objectId,
                intField(fields, "armorLevel"),
                intField(fields, "buyCost"),
                doubleField(fields, "damageTakenMultiplier"),
                dimension(fields),
                blockPos(fields, "pos"),
                Optional.of(blockPos(fields, "interaction")));
    }

    private static ZombiesPowerSwitchData parsePowerSwitch(
            ZombiesMapObjects objects,
            ObjectRef excluded,
            Map<String, String> fields
    ) {
        String objectId = resolveObjectId(objects, excluded, text(fields, "objectId"), ZombiesDeployFieldSchema.POWER_SWITCH);
        return new ZombiesPowerSwitchData(
                objectId,
                text(fields, "block"),
                intField(fields, "cost"),
                booleanField(fields, "emitsRedstoneWhenPowered"),
                dimension(fields),
                blockPos(fields, "pos"),
                Optional.empty());
    }

    private static ZombiesSodaMachineData parseSodaMachine(
            ZombiesMapObjects objects,
            ObjectRef excluded,
            Map<String, String> fields
    ) {
        String objectId = resolveObjectId(objects, excluded, text(fields, "objectId"), ZombiesDeployFieldSchema.SODA_MACHINE);
        return new ZombiesSodaMachineData(
                objectId,
                text(fields, "buffId"),
                intField(fields, "cost"),
                booleanField(fields, "requiresPower"),
                dimension(fields),
                blockPos(fields, "pos"),
                Optional.of(blockPos(fields, "interaction")));
    }

    private static ZombiesUltimateMachineData parseUltimateMachine(
            ZombiesMapObjects objects,
            ObjectRef excluded,
            Map<String, String> fields
    ) {
        String objectId = resolveObjectId(objects, excluded, text(fields, "objectId"), ZombiesDeployFieldSchema.ULTIMATE_MACHINE);
        return new ZombiesUltimateMachineData(
                objectId,
                intField(fields, "maxUpgradeLevel"),
                ultimateLevels(fields, "levels"),
                booleanField(fields, "requiresPower"),
                dimension(fields),
                blockPos(fields, "pos"),
                Optional.of(blockPos(fields, "interaction")));
    }

    private static EditResult unsupported(String type, ZombiesMapObjects objects, Map<String, String> fields) {
        return EditResult.failure("object.unsupported_type", "unsupported deploy object type: " + type, objects, -1, fields);
    }

    private static EditResult success(
            ZombiesMapObjects objects,
            String type,
            int selectedIndex,
            Map<String, String> fields,
            int affectedCount
    ) {
        return EditResult.success(objects, selectedIndex, mergedFields(type, fields), affectedCount);
    }

    private static void requireIndex(String type, int selectedIndex, int size) {
        if (selectedIndex < 0 || selectedIndex >= size) {
            throw failure("object.invalid_index", type + " index " + selectedIndex + " is outside 0.." + Math.max(0, size - 1));
        }
    }

    private static int nextIndexAfterDelete(int deletedIndex, int newSize) {
        return newSize <= 0 ? -1 : Math.min(deletedIndex, newSize - 1);
    }

    private static Map<String, String> mergedFields(String objectType, Map<String, String> fields) {
        Map<String, String> merged = new LinkedHashMap<>(ZombiesDeployFieldSchema.defaultFields(objectType));
        if (fields != null) {
            fields.forEach((key, value) -> {
                if (key != null && merged.containsKey(key)) {
                    merged.put(key, value == null ? "" : value);
                }
            });
        }
        return merged;
    }

    private static Map<String, String> fieldsForSelection(ZombiesMapObjects objects, String type, int selectedIndex) {
        if (selectedIndex < 0) {
            return mergedFields(type, Map.of());
        }
        return switch (type) {
            case ZombiesDeployFieldSchema.INITIAL -> selectedIndex < objects.initialSpawns().size()
                    ? fieldsFrom(objects.initialSpawns().get(selectedIndex))
                    : mergedFields(type, Map.of());
            case ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> selectedIndex < objects.zombieSpawns().size()
                    ? fieldsFrom(objects.zombieSpawns().get(selectedIndex))
                    : mergedFields(type, Map.of());
            case ZombiesDeployFieldSchema.BARRIER -> selectedIndex < objects.barriers().size()
                    ? fieldsFrom(objects.barriers().get(selectedIndex))
                    : mergedFields(type, Map.of());
            case ZombiesDeployFieldSchema.WEAPON_WALL -> selectedIndex < objects.weaponWalls().size()
                    ? fieldsFrom(objects.weaponWalls().get(selectedIndex))
                    : mergedFields(type, Map.of());
            case ZombiesDeployFieldSchema.AMMO_BOX -> selectedIndex < objects.ammoBoxes().size()
                    ? fieldsFrom(objects.ammoBoxes().get(selectedIndex))
                    : mergedFields(type, Map.of());
            case ZombiesDeployFieldSchema.ARMOR_STATION -> selectedIndex < objects.armorStations().size()
                    ? fieldsFrom(objects.armorStations().get(selectedIndex))
                    : mergedFields(type, Map.of());
            case ZombiesDeployFieldSchema.POWER_SWITCH -> objects.powerSwitch()
                    .map(ZombiesDeployObjectEditor::fieldsFrom)
                    .orElseGet(() -> mergedFields(type, Map.of()));
            case ZombiesDeployFieldSchema.SODA_MACHINE -> selectedIndex < objects.sodaMachines().size()
                    ? fieldsFrom(objects.sodaMachines().get(selectedIndex))
                    : mergedFields(type, Map.of());
            case ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> selectedIndex < objects.ultimateMachines().size()
                    ? fieldsFrom(objects.ultimateMachines().get(selectedIndex))
                    : mergedFields(type, Map.of());
            default -> mergedFields(type, Map.of());
        };
    }

    static Map<String, String> fieldsForSnapshotSelection(ZombiesMapObjects objects, String objectType, int selectedIndex) {
        return fieldsForSelection(objects == null ? ZombiesMapObjects.EMPTY : objects,
                ZombiesDeployFieldSchema.normalizeObjectType(objectType),
                selectedIndex);
    }

    private static Map<String, String> fieldsFrom(ZombiesInitialSpawnData data) {
        Map<String, String> fields = basePositionFields(ZombiesDeployFieldSchema.INITIAL, data.dimension(), data.pos());
        fields.put("yaw", Float.toString(data.yaw()));
        fields.put("pitch", Float.toString(data.pitch()));
        return fields;
    }

    private static Map<String, String> fieldsFrom(ZombiesZombieSpawnData data) {
        Map<String, String> fields = basePositionFields(ZombiesDeployFieldSchema.ZOMBIE_SPAWN, data.dimension(), data.pos());
        fields.put("objectId", data.objectId());
        fields.put("group", Integer.toString(data.group()));
        fields.put("weight", Double.toString(data.weight()));
        fields.put("yaw", Float.toString(data.yaw()));
        fields.put("pitch", Float.toString(data.pitch()));
        return fields;
    }

    private static Map<String, String> fieldsFrom(ZombiesBarrierData data) {
        Map<String, String> fields = new LinkedHashMap<>(ZombiesDeployFieldSchema.defaultFields(ZombiesDeployFieldSchema.BARRIER));
        fields.put("objectId", data.objectId());
        fields.put("group", Integer.toString(data.group()));
        fields.put("cost", Integer.toString(data.cost()));
        fields.put("blocksPlayersOnly", Boolean.toString(data.blocksPlayersOnly()));
        fields.put("dimension", dimensionId(data.dimension()));
        putPosition(fields, "areaFrom", data.areaFrom());
        putPosition(fields, "areaTo", data.areaTo());
        putPosition(fields, "interaction", data.interactionPos());
        return fields;
    }

    private static Map<String, String> fieldsFrom(ZombiesAmmoBoxData data) {
        Map<String, String> fields = basePositionFields(ZombiesDeployFieldSchema.AMMO_BOX, data.dimension(), data.pos());
        fields.put("objectId", data.objectId());
        fields.put("pricesByWeaponLevel", serializeIntMap(data.pricesByWeaponLevel()));
        putPosition(fields, "interaction", data.interactionPos().orElse(data.pos()));
        return fields;
    }

    private static Map<String, String> fieldsFrom(ZombiesWeaponWallData data) {
        Map<String, String> fields = basePositionFields(ZombiesDeployFieldSchema.WEAPON_WALL, data.dimension(), data.pos());
        fields.put("objectId", data.objectId());
        fields.put("weaponLevel", Integer.toString(data.weaponLevel()));
        fields.put("levelDamageMultiplier", Double.toString(data.levelDamageMultiplier()));
        fields.put("price", Integer.toString(data.price()));
        fields.put("maxReserveAmmo", Integer.toString(data.maxReserveAmmo()));
        fields.put("refreshWaves", serializeIntList(data.refreshWaves()));
        fields.put("rarityPools", serializeRarityPools(data.rarityPools()));
        fields.put("weapons", serializeWeaponCandidates(data.weapons()));
        putPosition(fields, "interaction", data.interactionPos().orElse(data.pos()));
        return fields;
    }

    private static Map<String, String> fieldsFrom(ZombiesArmorStationData data) {
        Map<String, String> fields = basePositionFields(ZombiesDeployFieldSchema.ARMOR_STATION, data.dimension(), data.pos());
        fields.put("objectId", data.objectId());
        fields.put("armorLevel", Integer.toString(data.armorLevel()));
        fields.put("buyCost", Integer.toString(data.buyCost()));
        fields.put("damageTakenMultiplier", Double.toString(data.damageTakenMultiplier()));
        putPosition(fields, "interaction", data.interactionPos().orElse(data.pos()));
        return fields;
    }

    private static Map<String, String> fieldsFrom(ZombiesPowerSwitchData data) {
        Map<String, String> fields = basePositionFields(ZombiesDeployFieldSchema.POWER_SWITCH, data.dimension(), data.pos());
        fields.put("objectId", data.objectId());
        fields.put("block", data.block());
        fields.put("cost", Integer.toString(data.cost()));
        fields.put("emitsRedstoneWhenPowered", Boolean.toString(data.emitsRedstoneWhenPowered()));
        return fields;
    }

    private static Map<String, String> fieldsFrom(ZombiesSodaMachineData data) {
        Map<String, String> fields = basePositionFields(ZombiesDeployFieldSchema.SODA_MACHINE, data.dimension(), data.pos());
        fields.put("objectId", data.objectId());
        fields.put("buffId", data.buffId());
        fields.put("cost", Integer.toString(data.cost()));
        fields.put("requiresPower", Boolean.toString(data.requiresPower()));
        putPosition(fields, "interaction", data.interactionPos().orElse(data.pos()));
        return fields;
    }

    private static Map<String, String> fieldsFrom(ZombiesUltimateMachineData data) {
        Map<String, String> fields = basePositionFields(ZombiesDeployFieldSchema.ULTIMATE_MACHINE, data.dimension(), data.pos());
        fields.put("objectId", data.objectId());
        fields.put("maxUpgradeLevel", Integer.toString(data.maxUpgradeLevel()));
        fields.put("levels", serializeUltimateLevels(data.levels()));
        fields.put("requiresPower", Boolean.toString(data.requiresPower()));
        putPosition(fields, "interaction", data.interactionPos().orElse(data.pos()));
        return fields;
    }

    private static Map<String, String> basePositionFields(
            String objectType,
            ResourceKey<Level> dimension,
            BlockPos pos
    ) {
        Map<String, String> fields = new LinkedHashMap<>(ZombiesDeployFieldSchema.defaultFields(objectType));
        fields.put("dimension", dimensionId(dimension));
        putPosition(fields, "pos", pos);
        return fields;
    }

    private static void putPosition(Map<String, String> fields, String prefix, BlockPos pos) {
        BlockPos resolved = pos == null ? BlockPos.ZERO : pos;
        fields.put(prefix + "X", Integer.toString(resolved.getX()));
        fields.put(prefix + "Y", Integer.toString(resolved.getY()));
        fields.put(prefix + "Z", Integer.toString(resolved.getZ()));
    }

    private static String text(Map<String, String> fields, String key) {
        return Objects.requireNonNullElse(fields.get(key), "").trim();
    }

    private static int intField(Map<String, String> fields, String key) {
        String value = text(fields, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw failure("field.invalid_integer", "field " + key + " must be an integer: " + value);
        }
    }

    private static double doubleField(Map<String, String> fields, String key) {
        String value = text(fields, key);
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw failure("field.invalid_decimal", "field " + key + " must be finite: " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw failure("field.invalid_decimal", "field " + key + " must be a decimal: " + value);
        }
    }

    private static float floatField(Map<String, String> fields, String key) {
        double parsed = doubleField(fields, key);
        if (parsed < -Float.MAX_VALUE || parsed > Float.MAX_VALUE) {
            throw failure("field.invalid_decimal", "field " + key + " is outside float range: " + parsed);
        }
        return (float) parsed;
    }

    private static boolean booleanField(Map<String, String> fields, String key) {
        String value = text(fields, key).toLowerCase(Locale.ROOT);
        return switch (value) {
            case "true", "1", "yes", "y" -> true;
            case "false", "0", "no", "n" -> false;
            default -> throw failure("field.invalid_boolean", "field " + key + " must be true or false: " + value);
        };
    }

    private static ResourceKey<Level> dimension(Map<String, String> fields) {
        String value = text(fields, "dimension");
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw failure("field.invalid_dimension", "field dimension must be a resource location: " + value);
        }
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    private static BlockPos blockPos(Map<String, String> fields, String prefix) {
        return new BlockPos(
                intField(fields, prefix + "X"),
                intField(fields, prefix + "Y"),
                intField(fields, prefix + "Z"));
    }

    private static Map<String, Integer> intMap(Map<String, String> fields, String key) {
        String value = text(fields, key);
        Map<String, Integer> parsed = new LinkedHashMap<>();
        if (value.isEmpty()) {
            return parsed;
        }
        for (String entry : value.split("[,;]")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0 || separator == trimmed.length() - 1) {
                throw failure("field.invalid_list", "field " + key + " entry must be key=value: " + trimmed);
            }
            String entryKey = trimmed.substring(0, separator).trim();
            String entryValue = trimmed.substring(separator + 1).trim();
            if (entryKey.isEmpty()) {
                throw failure("field.invalid_list", "field " + key + " contains an empty key");
            }
            try {
                parsed.put(entryKey, Integer.parseInt(entryValue));
            } catch (NumberFormatException e) {
                throw failure("field.invalid_integer", "field " + key + " value must be an integer: " + entryValue);
            }
        }
        return parsed;
    }

    private static List<Integer> intList(Map<String, String> fields, String key) {
        String value = text(fields, key);
        List<Integer> parsed = new ArrayList<>();
        if (value.isEmpty()) {
            return parsed;
        }
        for (String entry : value.split("[,;\\n\\r]")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                parsed.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException e) {
                throw failure("field.invalid_integer", "field " + key + " entry must be an integer: " + trimmed);
            }
        }
        return parsed;
    }

    private static List<ZombiesWeaponWallData.RarityPoolData> rarityPools(Map<String, String> fields, String key) {
        String value = text(fields, key);
        List<ZombiesWeaponWallData.RarityPoolData> parsed = new ArrayList<>();
        if (value.isEmpty()) {
            return parsed;
        }
        for (String entry : value.split("[;\\n\\r]")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int equals = trimmed.indexOf('=');
            if (equals <= 0 || equals == trimmed.length() - 1) {
                throw failure("field.invalid_list", "field " + key + " entry must be id=rank,baseWeight,waveFactor: " + trimmed);
            }
            String id = trimmed.substring(0, equals).trim();
            String[] parts = trimmed.substring(equals + 1).split(",");
            if (id.isEmpty() || parts.length != 3) {
                throw failure("field.invalid_list", "field " + key + " entry must be id=rank,baseWeight,waveFactor: " + trimmed);
            }
            try {
                double baseWeight = Double.parseDouble(parts[1].trim());
                double waveFactor = Double.parseDouble(parts[2].trim());
                if (!Double.isFinite(baseWeight) || !Double.isFinite(waveFactor)) {
                    throw failure("field.invalid_decimal", "field " + key + " weights must be finite: " + trimmed);
                }
                parsed.add(new ZombiesWeaponWallData.RarityPoolData(
                        id,
                        Integer.parseInt(parts[0].trim()),
                        baseWeight,
                        waveFactor));
            } catch (NumberFormatException e) {
                throw failure("field.invalid_list", "field " + key + " entry has invalid numeric values: " + trimmed);
            }
        }
        return parsed;
    }

    private static List<ZombiesWeaponWallData.WeaponCandidateData> weaponCandidates(Map<String, String> fields, String key) {
        String value = text(fields, key);
        List<ZombiesWeaponWallData.WeaponCandidateData> parsed = new ArrayList<>();
        if (value.isEmpty()) {
            return parsed;
        }
        for (String entry : value.split("[;\\n\\r]")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.indexOf('|');
            if (separator <= 0 || separator == trimmed.length() - 1) {
                throw failure("field.invalid_list", "field " + key + " entry must be gunId|rarity=weight,...: " + trimmed);
            }
            String gunId = trimmed.substring(0, separator).trim();
            if (gunId.isEmpty()) {
                throw failure("field.invalid_list", "field " + key + " contains an empty gunId");
            }
            Map<String, Double> weights = new LinkedHashMap<>();
            for (String weightEntry : trimmed.substring(separator + 1).split(",")) {
                String weightText = weightEntry.trim();
                if (weightText.isEmpty()) {
                    continue;
                }
                int equals = weightText.indexOf('=');
                if (equals <= 0 || equals == weightText.length() - 1) {
                    throw failure("field.invalid_list", "field " + key + " weight must be rarity=weight: " + weightText);
                }
                String rarity = weightText.substring(0, equals).trim();
                String weightValue = weightText.substring(equals + 1).trim();
                if (rarity.isEmpty()) {
                    throw failure("field.invalid_list", "field " + key + " contains an empty rarity id");
                }
                try {
                    double weight = Double.parseDouble(weightValue);
                    if (!Double.isFinite(weight)) {
                        throw failure("field.invalid_decimal", "field " + key + " weight must be finite: " + weightValue);
                    }
                    weights.put(rarity, weight);
                } catch (NumberFormatException e) {
                    throw failure("field.invalid_decimal", "field " + key + " weight must be a decimal: " + weightValue);
                }
            }
            parsed.add(new ZombiesWeaponWallData.WeaponCandidateData(gunId, weights));
        }
        return parsed;
    }

    private static Map<String, ZombiesUltimateMachineData.UpgradeLevelData> ultimateLevels(
            Map<String, String> fields,
            String key
    ) {
        String value = text(fields, key);
        Map<String, ZombiesUltimateMachineData.UpgradeLevelData> parsed = new LinkedHashMap<>();
        if (value.isEmpty()) {
            return parsed;
        }
        for (String entry : value.split("[,;]")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int equals = trimmed.indexOf('=');
            int colon = trimmed.indexOf(':', equals + 1);
            if (equals <= 0 || colon <= equals + 1 || colon == trimmed.length() - 1) {
                throw failure("field.invalid_list", "field " + key + " entry must be level=cost:damageMultiplier: " + trimmed);
            }
            String level = trimmed.substring(0, equals).trim();
            String cost = trimmed.substring(equals + 1, colon).trim();
            String multiplier = trimmed.substring(colon + 1).trim();
            if (level.isEmpty()) {
                throw failure("field.invalid_list", "field " + key + " contains an empty level");
            }
            try {
                double parsedMultiplier = Double.parseDouble(multiplier);
                if (!Double.isFinite(parsedMultiplier)) {
                    throw failure("field.invalid_decimal", "field " + key + " damage multiplier must be finite: " + multiplier);
                }
                parsed.put(level, new ZombiesUltimateMachineData.UpgradeLevelData(
                        Integer.parseInt(cost),
                        parsedMultiplier));
            } catch (NumberFormatException e) {
                throw failure("field.invalid_list", "field " + key + " entry has invalid numeric values: " + trimmed);
            }
        }
        return parsed;
    }

    private static String resolveObjectId(
            ZombiesMapObjects objects,
            ObjectRef excluded,
            String requested,
            String fallbackBase
    ) {
        String normalized = Objects.requireNonNullElse(requested, "").trim();
        if (normalized.isEmpty()) {
            return uniqueObjectId(objects, fallbackBase, excluded);
        }
        if (objectIdExists(objects, normalized, excluded)) {
            throw failure("object.duplicate_id", "objectId already exists: " + normalized);
        }
        return normalized;
    }

    private static String copyObjectId(ZombiesMapObjects objects, String sourceObjectId, String fallbackBase) {
        String source = Objects.requireNonNullElse(sourceObjectId, "").trim();
        String base = source.isEmpty() ? fallbackBase + "_copy" : source + "_copy";
        return uniqueObjectId(objects, base, NO_EXCLUSION);
    }

    private static String uniqueObjectId(ZombiesMapObjects objects, String requestedBase, ObjectRef excluded) {
        String base = generatedBase(requestedBase);
        if (!objectIdExists(objects, base, excluded)) {
            return base;
        }
        for (int suffix = 2; suffix < Integer.MAX_VALUE; suffix++) {
            String candidate = base + "_" + suffix;
            if (!objectIdExists(objects, candidate, excluded)) {
                return candidate;
            }
        }
        throw failure("object.id_exhausted", "could not generate a unique objectId for " + base);
    }

    private static String generatedBase(String requestedBase) {
        String base = Objects.requireNonNullElse(requestedBase, "").trim().replaceAll("\\s+", "_");
        return base.isEmpty() ? "zombies_object" : base;
    }

    private static boolean objectIdExists(ZombiesMapObjects objects, String objectId, ObjectRef excluded) {
        if (objectId == null || objectId.isBlank()) {
            return false;
        }
        for (int i = 0; i < objects.zombieSpawns().size(); i++) {
            if (matches(objectId, objects.zombieSpawns().get(i).objectId(), excluded, ZombiesDeployFieldSchema.ZOMBIE_SPAWN, i)) {
                return true;
            }
        }
        for (int i = 0; i < objects.barriers().size(); i++) {
            if (matches(objectId, objects.barriers().get(i).objectId(), excluded, ZombiesDeployFieldSchema.BARRIER, i)) {
                return true;
            }
        }
        for (int i = 0; i < objects.weaponWalls().size(); i++) {
            if (matches(objectId, objects.weaponWalls().get(i).objectId(), excluded, ZombiesDeployFieldSchema.WEAPON_WALL, i)) {
                return true;
            }
        }
        for (int i = 0; i < objects.ammoBoxes().size(); i++) {
            if (matches(objectId, objects.ammoBoxes().get(i).objectId(), excluded, ZombiesDeployFieldSchema.AMMO_BOX, i)) {
                return true;
            }
        }
        for (int i = 0; i < objects.armorStations().size(); i++) {
            if (matches(objectId, objects.armorStations().get(i).objectId(), excluded, ZombiesDeployFieldSchema.ARMOR_STATION, i)) {
                return true;
            }
        }
        if (objects.powerSwitch().isPresent()
                && matches(objectId, objects.powerSwitch().get().objectId(), excluded, ZombiesDeployFieldSchema.POWER_SWITCH, 0)) {
            return true;
        }
        for (int i = 0; i < objects.sodaMachines().size(); i++) {
            if (matches(objectId, objects.sodaMachines().get(i).objectId(), excluded, ZombiesDeployFieldSchema.SODA_MACHINE, i)) {
                return true;
            }
        }
        for (int i = 0; i < objects.ultimateMachines().size(); i++) {
            if (matches(objectId, objects.ultimateMachines().get(i).objectId(), excluded, ZombiesDeployFieldSchema.ULTIMATE_MACHINE, i)) {
                return true;
            }
        }
        for (int i = 0; i < objects.mysteryBoxes().size(); i++) {
            if (matches(objectId, objects.mysteryBoxes().get(i).objectId(), excluded, "mystery_box", i)) {
                return true;
            }
        }
        for (int i = 0; i < objects.windows().size(); i++) {
            if (matches(objectId, objects.windows().get(i).objectId(), excluded, "window", i)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String requested, String existing, ObjectRef excluded, String type, int index) {
        return requested.equals(existing) && !excluded.matches(type, index);
    }

    private static String dimensionId(ResourceKey<Level> dimension) {
        if (dimension == null || dimension.location() == null) {
            return Level.OVERWORLD.location().toString();
        }
        return dimension.location().toString();
    }

    private static String serializeIntMap(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String serializeIntList(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String serializeRarityPools(List<ZombiesWeaponWallData.RarityPoolData> pools) {
        if (pools == null || pools.isEmpty()) {
            return "";
        }
        return pools.stream()
                .map(pool -> pool.id()
                        + "="
                        + pool.rank()
                        + ","
                        + pool.baseWeight()
                        + ","
                        + pool.waveFactor())
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
    }

    private static String serializeWeaponCandidates(List<ZombiesWeaponWallData.WeaponCandidateData> weapons) {
        if (weapons == null || weapons.isEmpty()) {
            return "";
        }
        return weapons.stream()
                .map(candidate -> candidate.gunId() + "|" + serializeWeights(candidate.weightsByRarity()))
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
    }

    private static String serializeWeights(Map<String, Double> weights) {
        if (weights == null || weights.isEmpty()) {
            return "";
        }
        return weights.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String serializeUltimateLevels(Map<String, ZombiesUltimateMachineData.UpgradeLevelData> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        return map.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, ZombiesUltimateMachineData.UpgradeLevelData> entry) ->
                        positiveIntOrMax(entry.getKey())).thenComparing(Map.Entry::getKey))
                .map(entry -> entry.getKey()
                        + "="
                        + entry.getValue().cost()
                        + ":"
                        + entry.getValue().damageMultiplier())
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
    }

    private static int positiveIntOrMax(String value) {
        try {
            int parsed = Integer.parseInt(Objects.requireNonNullElse(value, "").trim());
            return parsed > 0 ? parsed : Integer.MAX_VALUE;
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static <T> List<T> mutable(List<T> list) {
        return new ArrayList<>(list == null ? List.of() : list);
    }

    private static ZombiesMapObjects withInitialSpawns(ZombiesMapObjects objects, List<ZombiesInitialSpawnData> initialSpawns) {
        return new ZombiesMapObjects(
                initialSpawns,
                objects.zombieSpawns(),
                objects.barriers(),
                objects.weaponWalls(),
                objects.ammoBoxes(),
                objects.armorStations(),
                objects.powerSwitch(),
                objects.sodaMachines(),
                objects.ultimateMachines(),
                objects.mysteryBoxes(),
                objects.windows());
    }

    private static ZombiesMapObjects withZombieSpawns(ZombiesMapObjects objects, List<ZombiesZombieSpawnData> zombieSpawns) {
        return new ZombiesMapObjects(
                objects.initialSpawns(),
                zombieSpawns,
                objects.barriers(),
                objects.weaponWalls(),
                objects.ammoBoxes(),
                objects.armorStations(),
                objects.powerSwitch(),
                objects.sodaMachines(),
                objects.ultimateMachines(),
                objects.mysteryBoxes(),
                objects.windows());
    }

    private static ZombiesMapObjects withBarriers(ZombiesMapObjects objects, List<ZombiesBarrierData> barriers) {
        return new ZombiesMapObjects(
                objects.initialSpawns(),
                objects.zombieSpawns(),
                barriers,
                objects.weaponWalls(),
                objects.ammoBoxes(),
                objects.armorStations(),
                objects.powerSwitch(),
                objects.sodaMachines(),
                objects.ultimateMachines(),
                objects.mysteryBoxes(),
                objects.windows());
    }

    private static ZombiesMapObjects withWeaponWalls(ZombiesMapObjects objects, List<ZombiesWeaponWallData> weaponWalls) {
        return new ZombiesMapObjects(
                objects.initialSpawns(),
                objects.zombieSpawns(),
                objects.barriers(),
                weaponWalls,
                objects.ammoBoxes(),
                objects.armorStations(),
                objects.powerSwitch(),
                objects.sodaMachines(),
                objects.ultimateMachines(),
                objects.mysteryBoxes(),
                objects.windows());
    }

    private static ZombiesMapObjects withAmmoBoxes(ZombiesMapObjects objects, List<ZombiesAmmoBoxData> ammoBoxes) {
        return new ZombiesMapObjects(
                objects.initialSpawns(),
                objects.zombieSpawns(),
                objects.barriers(),
                objects.weaponWalls(),
                ammoBoxes,
                objects.armorStations(),
                objects.powerSwitch(),
                objects.sodaMachines(),
                objects.ultimateMachines(),
                objects.mysteryBoxes(),
                objects.windows());
    }

    private static ZombiesMapObjects withArmorStations(ZombiesMapObjects objects, List<ZombiesArmorStationData> armorStations) {
        return new ZombiesMapObjects(
                objects.initialSpawns(),
                objects.zombieSpawns(),
                objects.barriers(),
                objects.weaponWalls(),
                objects.ammoBoxes(),
                armorStations,
                objects.powerSwitch(),
                objects.sodaMachines(),
                objects.ultimateMachines(),
                objects.mysteryBoxes(),
                objects.windows());
    }

    private static ZombiesMapObjects withPowerSwitch(ZombiesMapObjects objects, Optional<ZombiesPowerSwitchData> powerSwitch) {
        return new ZombiesMapObjects(
                objects.initialSpawns(),
                objects.zombieSpawns(),
                objects.barriers(),
                objects.weaponWalls(),
                objects.ammoBoxes(),
                objects.armorStations(),
                powerSwitch,
                objects.sodaMachines(),
                objects.ultimateMachines(),
                objects.mysteryBoxes(),
                objects.windows());
    }

    private static ZombiesMapObjects withSodaMachines(ZombiesMapObjects objects, List<ZombiesSodaMachineData> sodaMachines) {
        return new ZombiesMapObjects(
                objects.initialSpawns(),
                objects.zombieSpawns(),
                objects.barriers(),
                objects.weaponWalls(),
                objects.ammoBoxes(),
                objects.armorStations(),
                objects.powerSwitch(),
                sodaMachines,
                objects.ultimateMachines(),
                objects.mysteryBoxes(),
                objects.windows());
    }

    private static ZombiesMapObjects withUltimateMachines(ZombiesMapObjects objects, List<ZombiesUltimateMachineData> ultimateMachines) {
        return new ZombiesMapObjects(
                objects.initialSpawns(),
                objects.zombieSpawns(),
                objects.barriers(),
                objects.weaponWalls(),
                objects.ammoBoxes(),
                objects.armorStations(),
                objects.powerSwitch(),
                objects.sodaMachines(),
                ultimateMachines,
                objects.mysteryBoxes(),
                objects.windows());
    }

    private static EditFailure failure(String code, String message) {
        return new EditFailure(code, message);
    }

    record EditResult(
            boolean success,
            String code,
            String detail,
            ZombiesMapObjects objects,
            int selectedIndex,
            Map<String, String> fields,
            int affectedCount
    ) {
        EditResult {
            code = Objects.requireNonNullElse(code, success ? "ok" : "error").trim();
            detail = Objects.requireNonNullElse(detail, "").trim();
            objects = objects == null ? ZombiesMapObjects.EMPTY : objects;
            selectedIndex = Math.max(-1, selectedIndex);
            fields = fields == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(fields));
            affectedCount = Math.max(0, affectedCount);
        }

        static EditResult success(
                ZombiesMapObjects objects,
                int selectedIndex,
                Map<String, String> fields,
                int affectedCount
        ) {
            return new EditResult(true, "ok", "", objects, selectedIndex, fields, affectedCount);
        }

        static EditResult failure(
                String code,
                String detail,
                ZombiesMapObjects objects,
                int selectedIndex,
                Map<String, String> fields
        ) {
            return new EditResult(false, code, detail, objects, selectedIndex, fields, 0);
        }
    }

    private record ObjectRef(String type, int index) {
        boolean matches(String candidateType, int candidateIndex) {
            return type.equals(candidateType) && index == candidateIndex;
        }
    }

    private static final class EditFailure extends RuntimeException {
        private final String code;

        private EditFailure(String code, String message) {
            super(message);
            this.code = Objects.requireNonNullElse(code, "error").trim();
        }
    }
}
