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
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ZombiesDeployObjectEditorCompatTest {
    private ZombiesDeployObjectEditorCompatTest() {
    }

    public static void main(String[] args) {
        if (!bootstrapMinecraft()) {
            return;
        }
        initialSpawnsAllowFourAndRejectFifth();
        barrierAreaFieldsParseUpdateAndDuplicate();
        ammoBoxPricesByWeaponLevelParseAndUpdateFromListField();
        armorStationFieldsParseAndUpdate();
        powerSwitchSingleObjectCrudKeepsFieldValues();
        sodaMachineFieldsParseAndDuplicate();
        ultimateMachineLevelsParseFromListField();
        duplicateDeleteAndClearKeepSelectionAndCountsStable();
        failurePathsKeepOriginalObjects();
        weaponWallListFieldsParseAndUpdateFromListFields();
        weaponWallDuplicateCreatesNonConflictingObjectId();
    }

    private static void initialSpawnsAllowFourAndRejectFifth() {
        ZombiesMapObjects objects = ZombiesMapObjects.EMPTY;
        for (int i = 0; i < ZombiesDeployObjectEditor.MAX_INITIAL_PLAYER_SPAWNS; i++) {
            ZombiesDeployObjectEditor.EditResult add = edit(
                    objects,
                    ZombiesDeployObjectEditor.Operation.ADD,
                    ZombiesDeployFieldSchema.INITIAL,
                    -1,
                    fields(ZombiesDeployFieldSchema.INITIAL,
                            "posX", Integer.toString(i + 1),
                            "posY", "64",
                            "posZ", "1"));
            requireSuccess(add, "INITIAL add " + (i + 1) + " should succeed");
            require(add.objects().initialSpawns().size() == i + 1,
                    "INITIAL add should append through the fourth spawn");
            require(add.selectedIndex() == i, "INITIAL add should select the inserted point");
            ZombiesInitialSpawnData added = add.objects().initialSpawns().get(i);
            require(added.pos().equals(new BlockPos(i + 1, 64, 1)), "INITIAL position should parse");
            objects = add.objects();
        }

        ZombiesDeployObjectEditor.EditResult fifth = edit(
                objects,
                ZombiesDeployObjectEditor.Operation.ADD,
                ZombiesDeployFieldSchema.INITIAL,
                -1,
                fields(ZombiesDeployFieldSchema.INITIAL,
                        "posX", "5",
                        "posY", "64",
                        "posZ", "1"));
        requireFailure(fifth, "object.max_initial_spawns", "fifth INITIAL add should fail");
        require(fifth.objects() == objects, "fifth INITIAL add should keep original objects");

        ZombiesDeployObjectEditor.EditResult duplicateAtLimit = edit(
                objects,
                ZombiesDeployObjectEditor.Operation.DUPLICATE,
                ZombiesDeployFieldSchema.INITIAL,
                0,
                Map.of());
        requireFailure(duplicateAtLimit, "object.max_initial_spawns", "INITIAL duplicate at limit should fail");
        require(duplicateAtLimit.objects() == objects, "INITIAL duplicate failure should keep original objects");
    }

    private static boolean bootstrapMinecraft() {
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            return true;
        } catch (Throwable throwable) {
            System.err.println("Skipping ZombiesDeployObjectEditorCompatTest outside a bootstrapped Minecraft runtime: "
                    + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            return false;
        }
    }

    private static void barrierAreaFieldsParseUpdateAndDuplicate() {
        ZombiesDeployObjectEditor.EditResult add = edit(
                ZombiesMapObjects.EMPTY,
                ZombiesDeployObjectEditor.Operation.ADD,
                ZombiesDeployFieldSchema.BARRIER,
                -1,
                fields(ZombiesDeployFieldSchema.BARRIER,
                        "objectId", "barrier-2-a",
                        "group", "2",
                        "cost", "750",
                        "blocksPlayersOnly", "true",
                        "areaFromX", "5",
                        "areaFromY", "64",
                        "areaFromZ", "5",
                        "areaToX", "7",
                        "areaToY", "66",
                        "areaToZ", "5",
                        "interactionX", "6",
                        "interactionY", "65",
                        "interactionZ", "4"));

        requireSuccess(add, "barrier add should succeed");
        require(add.selectedIndex() == 0, "barrier add should select inserted object");
        ZombiesBarrierData added = only(add.objects().barriers(), "added barrier");
        requireBarrier(added, "barrier-2-a", 2, 750, true,
                new BlockPos(5, 64, 5), new BlockPos(7, 66, 5), new BlockPos(6, 65, 4));

        Map<String, String> updateFields = new LinkedHashMap<>(add.fields());
        updateFields.put("objectId", "barrier-3-a");
        updateFields.put("group", "3");
        updateFields.put("cost", "950");
        updateFields.put("blocksPlayersOnly", "false");
        updateFields.put("areaToZ", "8");
        ZombiesDeployObjectEditor.EditResult update = edit(
                add.objects(),
                ZombiesDeployObjectEditor.Operation.UPDATE,
                ZombiesDeployFieldSchema.BARRIER,
                0,
                updateFields);

        requireSuccess(update, "barrier update should succeed");
        ZombiesBarrierData updated = only(update.objects().barriers(), "updated barrier");
        requireBarrier(updated, "barrier-3-a", 3, 950, false,
                new BlockPos(5, 64, 5), new BlockPos(7, 66, 8), new BlockPos(6, 65, 4));

        ZombiesDeployObjectEditor.EditResult duplicate = edit(
                update.objects(),
                ZombiesDeployObjectEditor.Operation.DUPLICATE,
                ZombiesDeployFieldSchema.BARRIER,
                0,
                Map.of());

        requireSuccess(duplicate, "barrier duplicate should succeed");
        require(duplicate.objects().barriers().size() == 2, "barrier duplicate should append one object");
        require("barrier-3-a_copy".equals(duplicate.objects().barriers().get(1).objectId()),
                "barrier duplicate should generate non-conflicting object id");
        require(duplicate.objects().barriers().get(1).areaTo().equals(new BlockPos(7, 66, 8)),
                "barrier duplicate should preserve area bounds");
    }

    private static void ammoBoxPricesByWeaponLevelParseAndUpdateFromListField() {
        ZombiesDeployObjectEditor.EditResult add = edit(
                ZombiesMapObjects.EMPTY,
                ZombiesDeployObjectEditor.Operation.ADD,
                ZombiesDeployFieldSchema.AMMO_BOX,
                -1,
                fields(ZombiesDeployFieldSchema.AMMO_BOX,
                        "objectId", "ammo-1",
                        "pricesByWeaponLevel", "1=0;2=250,3=500",
                        "posX", "2",
                        "posY", "64",
                        "posZ", "8"));

        requireSuccess(add, "ammo_box add should succeed");
        require(add.selectedIndex() == 0, "ammo_box add should select the inserted object");
        ZombiesAmmoBoxData added = only(add.objects().ammoBoxes(), "added ammo box");
        require("ammo-1".equals(added.objectId()), "ammo_box objectId should parse");
        requireMapValue(added.pricesByWeaponLevel(), "1", 0, "ammo_box level 1 price should parse");
        requireMapValue(added.pricesByWeaponLevel(), "2", 250, "ammo_box level 2 price should parse");
        requireMapValue(added.pricesByWeaponLevel(), "3", 500, "ammo_box level 3 price should parse");

        Map<String, String> updateFields = new LinkedHashMap<>(add.fields());
        updateFields.put("pricesByWeaponLevel", "1=0;2=300;4=900");
        updateFields.put("posZ", "9");
        ZombiesDeployObjectEditor.EditResult update = edit(
                add.objects(),
                ZombiesDeployObjectEditor.Operation.UPDATE,
                ZombiesDeployFieldSchema.AMMO_BOX,
                0,
                updateFields);

        requireSuccess(update, "ammo_box update should succeed");
        require(update.selectedIndex() == 0, "ammo_box update should keep selected index");
        ZombiesAmmoBoxData updated = only(update.objects().ammoBoxes(), "updated ammo box");
        require("ammo-1".equals(updated.objectId()), "ammo_box update should retain unchanged objectId");
        requireMapValue(updated.pricesByWeaponLevel(), "2", 300, "ammo_box update should change level 2 price");
        requireMapValue(updated.pricesByWeaponLevel(), "4", 900, "ammo_box update should add level 4 price");
        require(updated.pos().getZ() == 9, "ammo_box update should change requested position field");
    }

    private static void armorStationFieldsParseAndUpdate() {
        ZombiesDeployObjectEditor.EditResult add = edit(
                ZombiesMapObjects.EMPTY,
                ZombiesDeployObjectEditor.Operation.ADD,
                ZombiesDeployFieldSchema.ARMOR_STATION,
                -1,
                fields(ZombiesDeployFieldSchema.ARMOR_STATION,
                        "objectId", "armor-2",
                        "armorLevel", "2",
                        "buyCost", "750",
                        "damageTakenMultiplier", "0.5",
                        "posX", "4",
                        "posY", "64",
                        "posZ", "9"));

        requireSuccess(add, "armor_station add should succeed");
        ZombiesArmorStationData added = only(add.objects().armorStations(), "added armor station");
        requireArmorStation(added, "armor-2", 2, 750, 0.5D, new BlockPos(4, 64, 9));

        Map<String, String> updateFields = new LinkedHashMap<>(add.fields());
        updateFields.put("armorLevel", "3");
        updateFields.put("buyCost", "1200");
        updateFields.put("damageTakenMultiplier", "0.35");
        ZombiesDeployObjectEditor.EditResult update = edit(
                add.objects(),
                ZombiesDeployObjectEditor.Operation.UPDATE,
                ZombiesDeployFieldSchema.ARMOR_STATION,
                0,
                updateFields);

        requireSuccess(update, "armor_station update should succeed");
        ZombiesArmorStationData updated = only(update.objects().armorStations(), "updated armor station");
        requireArmorStation(updated, "armor-2", 3, 1200, 0.35D, new BlockPos(4, 64, 9));
    }

    private static void powerSwitchSingleObjectCrudKeepsFieldValues() {
        ZombiesDeployObjectEditor.EditResult add = edit(
                ZombiesMapObjects.EMPTY,
                ZombiesDeployObjectEditor.Operation.ADD,
                ZombiesDeployFieldSchema.POWER_SWITCH,
                -1,
                fields(ZombiesDeployFieldSchema.POWER_SWITCH,
                        "objectId", "power-main",
                        "block", "codpattern:zombies_power_switch",
                        "cost", "1000",
                        "posX", "8",
                        "posY", "64",
                        "posZ", "8"));

        requireSuccess(add, "power_switch add should succeed");
        ZombiesPowerSwitchData power = add.objects().powerSwitch()
                .orElseThrow(() -> new AssertionError("power_switch should be present"));
        requirePowerSwitch(power, "power-main", "codpattern:zombies_power_switch", 1000, new BlockPos(8, 64, 8));

        ZombiesDeployObjectEditor.EditResult duplicateAdd = edit(
                add.objects(),
                ZombiesDeployObjectEditor.Operation.ADD,
                ZombiesDeployFieldSchema.POWER_SWITCH,
                -1,
                fields(ZombiesDeployFieldSchema.POWER_SWITCH, "objectId", "power-other"));
        requireFailure(duplicateAdd, "object.single_exists", "second power_switch add should fail");
        require(duplicateAdd.objects() == add.objects(), "second power_switch failure should keep original objects");

        Map<String, String> updateFields = new LinkedHashMap<>(add.fields());
        updateFields.put("cost", "1250");
        ZombiesDeployObjectEditor.EditResult update = edit(
                add.objects(),
                ZombiesDeployObjectEditor.Operation.UPDATE,
                ZombiesDeployFieldSchema.POWER_SWITCH,
                0,
                updateFields);

        requireSuccess(update, "power_switch update should succeed");
        requirePowerSwitch(update.objects().powerSwitch().orElseThrow(), "power-main",
                "codpattern:zombies_power_switch", 1250, new BlockPos(8, 64, 8));

        ZombiesDeployObjectEditor.EditResult duplicate = edit(
                update.objects(),
                ZombiesDeployObjectEditor.Operation.DUPLICATE,
                ZombiesDeployFieldSchema.POWER_SWITCH,
                0,
                Map.of());
        requireFailure(duplicate, "object.single_type", "power_switch duplicate should fail");

        ZombiesDeployObjectEditor.EditResult delete = edit(
                update.objects(),
                ZombiesDeployObjectEditor.Operation.DELETE,
                ZombiesDeployFieldSchema.POWER_SWITCH,
                0,
                Map.of());
        requireSuccess(delete, "power_switch delete should succeed");
        require(delete.objects().powerSwitch().isEmpty(), "power_switch delete should remove single object");
        require(delete.selectedIndex() == -1, "power_switch delete should clear selected index");
    }

    private static void sodaMachineFieldsParseAndDuplicate() {
        ZombiesDeployObjectEditor.EditResult add = edit(
                ZombiesMapObjects.EMPTY,
                ZombiesDeployObjectEditor.Operation.ADD,
                ZombiesDeployFieldSchema.SODA_MACHINE,
                -1,
                fields(ZombiesDeployFieldSchema.SODA_MACHINE,
                        "objectId", "soda-health",
                        "buffId", "double_health",
                        "cost", "1500",
                        "requiresPower", "true",
                        "posX", "10",
                        "posY", "64",
                        "posZ", "8"));

        requireSuccess(add, "soda_machine add should succeed");
        ZombiesSodaMachineData added = only(add.objects().sodaMachines(), "added soda machine");
        requireSodaMachine(added, "soda-health", "double_health", 1500, true, new BlockPos(10, 64, 8));

        Map<String, String> updateFields = new LinkedHashMap<>(add.fields());
        updateFields.put("buffId", "score_multiplier");
        updateFields.put("cost", "2000");
        updateFields.put("requiresPower", "false");
        ZombiesDeployObjectEditor.EditResult update = edit(
                add.objects(),
                ZombiesDeployObjectEditor.Operation.UPDATE,
                ZombiesDeployFieldSchema.SODA_MACHINE,
                0,
                updateFields);

        requireSuccess(update, "soda_machine update should succeed");
        ZombiesSodaMachineData updated = only(update.objects().sodaMachines(), "updated soda machine");
        requireSodaMachine(updated, "soda-health", "score_multiplier", 2000, false, new BlockPos(10, 64, 8));

        ZombiesDeployObjectEditor.EditResult duplicate = edit(
                update.objects(),
                ZombiesDeployObjectEditor.Operation.DUPLICATE,
                ZombiesDeployFieldSchema.SODA_MACHINE,
                0,
                Map.of());

        requireSuccess(duplicate, "soda_machine duplicate should succeed");
        require(duplicate.objects().sodaMachines().size() == 2, "soda_machine duplicate should append one object");
        require("soda-health_copy".equals(duplicate.objects().sodaMachines().get(1).objectId()),
                "soda_machine duplicate should generate non-conflicting object id");
    }

    private static void ultimateMachineLevelsParseFromListField() {
        ZombiesDeployObjectEditor.EditResult add = edit(
                ZombiesMapObjects.EMPTY,
                ZombiesDeployObjectEditor.Operation.ADD,
                ZombiesDeployFieldSchema.ULTIMATE_MACHINE,
                -1,
                fields(ZombiesDeployFieldSchema.ULTIMATE_MACHINE,
                        "objectId", "ultimate-1",
                        "maxUpgradeLevel", "3",
                        "levels", "1=2500:1.5;2=5000:2.0,3=7500:2.5",
                        "requiresPower", "false"));

        requireSuccess(add, "ultimate_machine add should succeed");
        ZombiesUltimateMachineData ultimate = only(add.objects().ultimateMachines(), "ultimate machine");
        require("ultimate-1".equals(ultimate.objectId()), "ultimate_machine objectId should parse");
        require(ultimate.maxUpgradeLevel() == 3, "ultimate_machine maxUpgradeLevel should parse");
        require(!ultimate.requiresPower(), "ultimate_machine requiresPower should parse");
        requireUltimateLevel(ultimate.levels(), "1", 2500, 1.5D);
        requireUltimateLevel(ultimate.levels(), "2", 5000, 2.0D);
        requireUltimateLevel(ultimate.levels(), "3", 7500, 2.5D);
    }

    private static void duplicateDeleteAndClearKeepSelectionAndCountsStable() {
        ZombiesDeployObjectEditor.EditResult first = addAmmoBox(ZombiesMapObjects.EMPTY, "ammo-1");
        ZombiesDeployObjectEditor.EditResult second = addAmmoBox(first.objects(), "ammo-1_copy");
        ZombiesMapObjects objects = second.objects();

        ZombiesDeployObjectEditor.EditResult duplicate = edit(
                objects,
                ZombiesDeployObjectEditor.Operation.DUPLICATE,
                ZombiesDeployFieldSchema.AMMO_BOX,
                0,
                Map.of());

        requireSuccess(duplicate, "ammo_box duplicate should succeed");
        require(duplicate.objects().ammoBoxes().size() == 3, "ammo_box duplicate should append one object");
        require(duplicate.selectedIndex() == 2, "ammo_box duplicate should select the copy");
        require("ammo-1_copy_2".equals(duplicate.objects().ammoBoxes().get(2).objectId()),
                "ammo_box duplicate should generate a non-conflicting objectId");

        ZombiesDeployObjectEditor.EditResult deleteMiddle = edit(
                duplicate.objects(),
                ZombiesDeployObjectEditor.Operation.DELETE,
                ZombiesDeployFieldSchema.AMMO_BOX,
                1,
                Map.of());

        requireSuccess(deleteMiddle, "ammo_box delete should succeed");
        require(deleteMiddle.objects().ammoBoxes().size() == 2, "ammo_box delete should remove one object");
        require(deleteMiddle.selectedIndex() == 1, "ammo_box delete should select the next object at the same index");
        require("ammo-1_copy_2".equals(deleteMiddle.objects().ammoBoxes().get(1).objectId()),
                "ammo_box delete should keep the next object selected");

        ZombiesDeployObjectEditor.EditResult deleteTail = edit(
                deleteMiddle.objects(),
                ZombiesDeployObjectEditor.Operation.DELETE,
                ZombiesDeployFieldSchema.AMMO_BOX,
                1,
                Map.of());

        requireSuccess(deleteTail, "ammo_box tail delete should succeed");
        require(deleteTail.objects().ammoBoxes().size() == 1, "ammo_box tail delete should remove one object");
        require(deleteTail.selectedIndex() == 0, "ammo_box tail delete should select previous object");

        ZombiesDeployObjectEditor.EditResult clear = edit(
                deleteTail.objects(),
                ZombiesDeployObjectEditor.Operation.CLEAR,
                ZombiesDeployFieldSchema.AMMO_BOX,
                0,
                Map.of());

        requireSuccess(clear, "ammo_box clear should succeed");
        require(clear.objects().ammoBoxes().isEmpty(), "ammo_box clear should remove all objects");
        require(clear.selectedIndex() == -1, "ammo_box clear should clear selected index");
        require(clear.affectedCount() == 1, "ammo_box clear should report removed object count");
    }

    private static void failurePathsKeepOriginalObjects() {
        ZombiesDeployObjectEditor.EditResult add = addAmmoBox(ZombiesMapObjects.EMPTY, "ammo-1");
        ZombiesMapObjects original = add.objects();

        ZombiesDeployObjectEditor.EditResult duplicateId = edit(
                original,
                ZombiesDeployObjectEditor.Operation.ADD,
                ZombiesDeployFieldSchema.AMMO_BOX,
                -1,
                fields(ZombiesDeployFieldSchema.AMMO_BOX,
                        "objectId", "ammo-1",
                        "pricesByWeaponLevel", "1=0"));

        requireFailure(duplicateId, "object.duplicate_id", "duplicate objectId should fail");
        require(duplicateId.objects() == original, "duplicate objectId failure should return original objects");
        require(original.ammoBoxes().size() == 1, "duplicate objectId failure should not append objects");
        require("ammo-1".equals(original.ammoBoxes().get(0).objectId()),
                "duplicate objectId failure should not modify existing objectId");

        Map<String, String> badListFields = new LinkedHashMap<>(add.fields());
        badListFields.put("pricesByWeaponLevel", "1=0;2=not_an_integer");
        ZombiesDeployObjectEditor.EditResult invalidList = edit(
                original,
                ZombiesDeployObjectEditor.Operation.UPDATE,
                ZombiesDeployFieldSchema.AMMO_BOX,
                0,
                badListFields);

        requireFailure(invalidList, "field.invalid_integer", "invalid pricesByWeaponLevel list should fail");
        require(invalidList.objects() == original, "invalid LIST failure should return original objects");
        requireMapValue(original.ammoBoxes().get(0).pricesByWeaponLevel(), "1", 0,
                "invalid LIST failure should keep original parsed value");
        require(!original.ammoBoxes().get(0).pricesByWeaponLevel().containsKey("2"),
                "invalid LIST failure should not apply partially parsed entries");
    }

    private static void weaponWallListFieldsParseAndUpdateFromListFields() {
        ZombiesDeployObjectEditor.EditResult add = edit(
                ZombiesMapObjects.EMPTY,
                ZombiesDeployObjectEditor.Operation.ADD,
                ZombiesDeployFieldSchema.WEAPON_WALL,
                -1,
                fields(ZombiesDeployFieldSchema.WEAPON_WALL,
                        "objectId", "wall-1",
                        "weaponLevel", "2",
                        "levelDamageMultiplier", "1.25",
                        "price", "650",
                        "maxReserveAmmo", "210",
                        "refreshWaves", "1,4;7",
                        "rarityPools", "common=1,10.0,0.0;rare=2,1.5,0.25",
                        "weapons", "codpattern:pistol|common=1.0;codpattern:rifle|common=0.5,rare=2.0",
                        "posX", "5",
                        "posY", "64",
                        "posZ", "6"));

        requireSuccess(add, "weapon_wall add should succeed");
        require(add.selectedIndex() == 0, "weapon_wall add should select the inserted object");
        ZombiesWeaponWallData added = only(add.objects().weaponWalls(), "added weapon wall");
        requireWeaponWallBase(added, "wall-1", 2, 1.25D, 650, 210);
        require(List.of(1, 4, 7).equals(added.refreshWaves()), "weapon_wall refreshWaves should parse");
        requireRarityPool(added.rarityPools().get(0), "common", 1, 10.0D, 0.0D);
        requireRarityPool(added.rarityPools().get(1), "rare", 2, 1.5D, 0.25D);
        requireWeaponCandidate(added.weapons().get(0), "codpattern:pistol", Map.of("common", 1.0D));
        requireWeaponCandidate(added.weapons().get(1), "codpattern:rifle", Map.of("common", 0.5D, "rare", 2.0D));

        Map<String, String> updateFields = new LinkedHashMap<>(add.fields());
        updateFields.put("price", "725");
        updateFields.put("rarityPools", "common=1,9.0,0.0;epic=4,0.0,3.5");
        ZombiesDeployObjectEditor.EditResult update = edit(
                add.objects(),
                ZombiesDeployObjectEditor.Operation.UPDATE,
                ZombiesDeployFieldSchema.WEAPON_WALL,
                0,
                updateFields);

        requireSuccess(update, "weapon_wall update should succeed");
        ZombiesWeaponWallData updated = only(update.objects().weaponWalls(), "updated weapon wall");
        requireWeaponWallBase(updated, "wall-1", 2, 1.25D, 725, 210);
        require(List.of(1, 4, 7).equals(updated.refreshWaves()),
                "weapon_wall update should preserve unchanged refreshWaves");
        requireWeaponCandidate(updated.weapons().get(0), "codpattern:pistol", Map.of("common", 1.0D));
        requireWeaponCandidate(updated.weapons().get(1), "codpattern:rifle", Map.of("common", 0.5D, "rare", 2.0D));
        requireRarityPool(updated.rarityPools().get(0), "common", 1, 9.0D, 0.0D);
        requireRarityPool(updated.rarityPools().get(1), "epic", 4, 0.0D, 3.5D);
    }

    private static void weaponWallDuplicateCreatesNonConflictingObjectId() {
        ZombiesDeployObjectEditor.EditResult first = addWeaponWall(ZombiesMapObjects.EMPTY, "wall-1");
        ZombiesDeployObjectEditor.EditResult second = addWeaponWall(first.objects(), "wall-1_copy");

        ZombiesDeployObjectEditor.EditResult duplicate = edit(
                second.objects(),
                ZombiesDeployObjectEditor.Operation.DUPLICATE,
                ZombiesDeployFieldSchema.WEAPON_WALL,
                0,
                Map.of());

        requireSuccess(duplicate, "weapon_wall duplicate should succeed");
        require(duplicate.objects().weaponWalls().size() == 3,
                "weapon_wall duplicate should append one object");
        require(duplicate.selectedIndex() == 2, "weapon_wall duplicate should select the copy");
        ZombiesWeaponWallData copy = duplicate.objects().weaponWalls().get(2);
        require("wall-1_copy_2".equals(copy.objectId()),
                "weapon_wall duplicate should generate a non-conflicting objectId");
        requireWeaponWallBase(copy, "wall-1_copy_2", 1, 1.0D, 500, 90);
    }

    private static ZombiesDeployObjectEditor.EditResult addAmmoBox(ZombiesMapObjects objects, String objectId) {
        ZombiesDeployObjectEditor.EditResult result = edit(
                objects,
                ZombiesDeployObjectEditor.Operation.ADD,
                ZombiesDeployFieldSchema.AMMO_BOX,
                -1,
                fields(ZombiesDeployFieldSchema.AMMO_BOX,
                        "objectId", objectId,
                        "pricesByWeaponLevel", "1=0"));
        requireSuccess(result, "setup ammo_box should add " + objectId);
        return result;
    }

    private static ZombiesDeployObjectEditor.EditResult addWeaponWall(ZombiesMapObjects objects, String objectId) {
        ZombiesDeployObjectEditor.EditResult result = edit(
                objects,
                ZombiesDeployObjectEditor.Operation.ADD,
                ZombiesDeployFieldSchema.WEAPON_WALL,
                -1,
                fields(ZombiesDeployFieldSchema.WEAPON_WALL,
                        "objectId", objectId,
                        "refreshWaves", "1",
                        "rarityPools", "common=1,1.0,0.0",
                        "weapons", "codpattern:pistol|common=1.0"));
        requireSuccess(result, "setup weapon_wall should add " + objectId);
        return result;
    }

    private static ZombiesDeployObjectEditor.EditResult edit(
            ZombiesMapObjects objects,
            ZombiesDeployObjectEditor.Operation operation,
            String objectType,
            int selectedIndex,
            Map<String, String> fields
    ) {
        return ZombiesDeployObjectEditor.edit(objects, operation, objectType, selectedIndex, fields);
    }

    private static Map<String, String> fields(String objectType, String... pairs) {
        require(pairs.length % 2 == 0, "fields should be key/value pairs");
        Map<String, String> fields = new LinkedHashMap<>(ZombiesDeployFieldSchema.defaultFields(objectType));
        for (int index = 0; index < pairs.length; index += 2) {
            fields.put(pairs[index], pairs[index + 1]);
        }
        return fields;
    }

    private static <T> T only(List<T> values, String label) {
        require(values.size() == 1, label + " should have exactly one entry but had " + values.size());
        return values.get(0);
    }

    private static void requireWeaponWallBase(
            ZombiesWeaponWallData wall,
            String objectId,
            int weaponLevel,
            double damageMultiplier,
            int price,
            int maxReserveAmmo
    ) {
        require(objectId.equals(wall.objectId()), "weapon_wall objectId should match");
        require(wall.weaponLevel() == weaponLevel, "weapon_wall weaponLevel should match");
        requireClose(wall.levelDamageMultiplier(), damageMultiplier, "weapon_wall damage multiplier should match");
        require(wall.price() == price, "weapon_wall price should match");
        require(wall.maxReserveAmmo() == maxReserveAmmo, "weapon_wall maxReserveAmmo should match");
    }

    private static void requireBarrier(
            ZombiesBarrierData barrier,
            String objectId,
            int group,
            int cost,
            boolean blocksPlayersOnly,
            BlockPos areaFrom,
            BlockPos areaTo,
            BlockPos interaction
    ) {
        require(objectId.equals(barrier.objectId()), "barrier objectId should match");
        require(barrier.group() == group, "barrier group should match");
        require(barrier.cost() == cost, "barrier cost should match");
        require(barrier.blocksPlayersOnly() == blocksPlayersOnly, "barrier blocksPlayersOnly should match");
        require(areaFrom.equals(barrier.areaFrom()), "barrier areaFrom should match");
        require(areaTo.equals(barrier.areaTo()), "barrier areaTo should match");
        require(interaction.equals(barrier.interactionPos()), "barrier interaction position should match");
    }

    private static void requireArmorStation(
            ZombiesArmorStationData station,
            String objectId,
            int armorLevel,
            int buyCost,
            double damageTakenMultiplier,
            BlockPos pos
    ) {
        require(objectId.equals(station.objectId()), "armor_station objectId should match");
        require(station.armorLevel() == armorLevel, "armor_station armorLevel should match");
        require(station.buyCost() == buyCost, "armor_station buyCost should match");
        requireClose(station.damageTakenMultiplier(), damageTakenMultiplier,
                "armor_station damageTakenMultiplier should match");
        require(pos.equals(station.pos()), "armor_station position should match");
    }

    private static void requirePowerSwitch(
            ZombiesPowerSwitchData power,
            String objectId,
            String block,
            int cost,
            BlockPos pos
    ) {
        require(objectId.equals(power.objectId()), "power_switch objectId should match");
        require(block.equals(power.block()), "power_switch block should match");
        require(power.cost() == cost, "power_switch cost should match");
        require(pos.equals(power.pos()), "power_switch position should match");
    }

    private static void requireSodaMachine(
            ZombiesSodaMachineData soda,
            String objectId,
            String buffId,
            int cost,
            boolean requiresPower,
            BlockPos pos
    ) {
        require(objectId.equals(soda.objectId()), "soda_machine objectId should match");
        require(buffId.equals(soda.buffId()), "soda_machine buffId should match");
        require(soda.cost() == cost, "soda_machine cost should match");
        require(soda.requiresPower() == requiresPower, "soda_machine requiresPower should match");
        require(pos.equals(soda.pos()), "soda_machine position should match");
    }

    private static void requireRarityPool(
            ZombiesWeaponWallData.RarityPoolData pool,
            String id,
            int rank,
            double baseWeight,
            double waveFactor
    ) {
        require(id.equals(pool.id()), "rarity pool id should match");
        require(pool.rank() == rank, "rarity pool rank should match");
        requireClose(pool.baseWeight(), baseWeight, "rarity pool baseWeight should match");
        requireClose(pool.waveFactor(), waveFactor, "rarity pool waveFactor should match");
    }

    private static void requireWeaponCandidate(
            ZombiesWeaponWallData.WeaponCandidateData candidate,
            String gunId,
            Map<String, Double> weightsByRarity
    ) {
        require(gunId.equals(candidate.gunId()), "weapon candidate gunId should match");
        require(candidate.weightsByRarity().size() == weightsByRarity.size(),
                "weapon candidate rarity weight count should match");
        weightsByRarity.forEach((rarity, weight) -> requireClose(
                candidate.weightsByRarity().getOrDefault(rarity, Double.NaN),
                weight,
                "weapon candidate " + rarity + " weight should match"));
    }

    private static void requireUltimateLevel(
            Map<String, ZombiesUltimateMachineData.UpgradeLevelData> levels,
            String level,
            int cost,
            double damageMultiplier
    ) {
        ZombiesUltimateMachineData.UpgradeLevelData data = levels.get(level);
        require(data != null, "ultimate_machine should include level " + level);
        require(data.cost() == cost, "ultimate_machine level " + level + " cost should parse");
        requireClose(data.damageMultiplier(), damageMultiplier,
                "ultimate_machine level " + level + " damage multiplier should parse");
    }

    private static void requireMapValue(Map<String, Integer> values, String key, int expected, String message) {
        Integer actual = values.get(key);
        require(actual != null, message + ": missing key " + key);
        require(actual == expected, message + ": expected " + expected + " but was " + actual);
    }

    private static void requireSuccess(ZombiesDeployObjectEditor.EditResult result, String message) {
        require(result.success(), message + ": " + result.code() + " " + result.detail());
    }

    private static void requireFailure(ZombiesDeployObjectEditor.EditResult result, String expectedCode, String message) {
        require(!result.success(), message + ": expected failure");
        require(expectedCode.equals(result.code()),
                message + ": expected " + expectedCode + " but was " + result.code());
    }

    private static void requireClose(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 0.000001D,
                message + ": expected " + expected + " but was " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
