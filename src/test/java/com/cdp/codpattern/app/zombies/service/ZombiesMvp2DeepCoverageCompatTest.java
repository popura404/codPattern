package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModeObjectState;
import com.cdp.codpattern.app.zombies.deploy.ZombiesDeployFieldSchema;
import com.cdp.codpattern.app.zombies.map.ZombiesMapObjects;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesWeaponWallData;
import com.cdp.codpattern.compat.fpsmatch.data.CodMapPersistence;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ZombiesMvp2DeepCoverageCompatTest {
    private ZombiesMvp2DeepCoverageCompatTest() {
    }

    public static void main(String[] args) {
        barrierGroupClearActivatesSpawnGroupAndRepeatFailsWithoutSpend();
        deploySaveFailureRollsBackEditedObjects();
        weaponWallListFieldsRoundTripThroughDeployEditor();
        invalidWeaponWallListUpdatePreservesOriginalObjects();
    }

    private static void barrierGroupClearActivatesSpawnGroupAndRepeatFailsWithoutSpend() {
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        ZombiesEconomyService economy = new ZombiesEconomyService(players);
        ZombiesObjectStateStore store = new ZombiesObjectStateStore();
        ZombiesActiveSpawnGroupService activeGroups = new ZombiesActiveSpawnGroupService();
        List<ZombiesBarrierData> barriers = List.of(
                barrier("barrier-2-a", 2),
                barrier("barrier-2-b", 2),
                barrier("barrier-3-a", 3));
        store.resetObjects(barriers, List.of(), List.of(), List.of());
        long groupThreeRevision = barrierState(store, barriers, "barrier-3-a").revision();

        UUID buyer = playerId(1);
        economy.addPoints(buyer, 1_000.0D);
        ZombiesServiceResult<ZombiesObjectStateStore.BarrierGroupUpdate> purchase =
                clearBarrierGroupPurchase(economy, store, activeGroups, barriers, buyer, 2, 750.0D);

        requireSuccess(purchase, "barrier group 2 purchase should succeed");
        require(purchase.value().orElseThrow().objectIds().equals(List.of("barrier-2-a", "barrier-2-b")),
                "barrier purchase should report every cleared same-group object");
        require(Set.of(1, 2).equals(activeGroups.snapshot()),
                "successful barrier purchase should activate the matching spawn group only");
        requireCleared(store, barriers, "barrier-2-a", true);
        requireCleared(store, barriers, "barrier-2-b", true);
        requireCleared(store, barriers, "barrier-3-a", false);
        require(barrierState(store, barriers, "barrier-3-a").revision() == groupThreeRevision,
                "clearing group 2 should not revise group 3 barrier state");
        requirePoints(players, buyer, 250.0D, "successful barrier purchase should deduct exactly one group cost");

        UUID repeatBuyer = playerId(2);
        economy.addPoints(repeatBuyer, 1_000.0D);
        ZombiesServiceResult<ZombiesObjectStateStore.BarrierGroupUpdate> repeat =
                clearBarrierGroupPurchase(economy, store, activeGroups, barriers, repeatBuyer, 2, 750.0D);

        requireFailure(repeat, ZombiesErrorCode.of("barrier.already_cleared"),
                "repeat barrier purchase should fail after group clear");
        require(Set.of(1, 2).equals(activeGroups.snapshot()),
                "failed repeat purchase should not activate any additional spawn group");
        requirePoints(players, repeatBuyer, 1_000.0D, "failed repeat barrier purchase should not deduct points");
    }

    private static void deploySaveFailureRollsBackEditedObjects() {
        DeployEditResult originalAdd = deployEdit(
                ZombiesMapObjects.EMPTY,
                "ADD",
                ZombiesDeployFieldSchema.WEAPON_WALL,
                -1,
                weaponWallFields("wall-rollback-original", "1", "codpattern:pistol|common=1.0"));
        requireSuccess(originalAdd, "setup weapon_wall add should succeed");
        ZombiesMapObjects original = originalAdd.objects();

        DeployEditResult pendingAdd = deployEdit(
                original,
                "ADD",
                ZombiesDeployFieldSchema.WEAPON_WALL,
                -1,
                weaponWallFields("wall-rollback-pending", "2,5", "codpattern:rifle|common=1.0"));
        requireSuccess(pendingAdd, "pending weapon_wall add should succeed before persistence");

        ObjectHolder holder = new ObjectHolder(original);
        holder.apply(pendingAdd.objects());
        require(holder.objects().weaponWalls().size() == 2,
                "holder should contain pending edit before simulated save failure");

        try {
            CodMapPersistence.saveMapOrRollback(null, () -> holder.apply(original));
            throw new AssertionError("saveMapOrRollback should throw for null map");
        } catch (IllegalArgumentException expected) {
            require(holder.objects() == original,
                    "save failure rollback callback should restore the exact previous objects instance");
        }
        require(holder.objects().weaponWalls().size() == 1,
                "save failure rollback should remove the pending weapon wall from memory");
        require("wall-rollback-original".equals(holder.objects().weaponWalls().get(0).objectId()),
                "save failure rollback should keep the original weapon wall");
    }

    private static void weaponWallListFieldsRoundTripThroughDeployEditor() {
        DeployEditResult add = deployEdit(
                ZombiesMapObjects.EMPTY,
                "ADD",
                ZombiesDeployFieldSchema.WEAPON_WALL,
                -1,
                weaponWallFields(
                        "wall-list",
                        "1;4\n7",
                        "codpattern:pistol|common=1.0;codpattern:rifle|rare=2.0,common=0.5"));

        requireSuccess(add, "weapon_wall add with LIST fields should succeed");
        require(add.selectedIndex() == 0, "weapon_wall add should select inserted object");
        ZombiesWeaponWallData added = only(add.objects().weaponWalls(), "added weapon wall");
        require(List.of(1, 4, 7).equals(added.refreshWaves()),
                "refreshWaves LIST should parse comma, semicolon, and newline delimiters");
        requireWeaponCandidate(added.weapons().get(1), "codpattern:rifle", Map.of("common", 0.5D, "rare", 2.0D));
        require("1,4,7".equals(add.fields().get("refreshWaves")),
                "returned draft fields should serialize refreshWaves canonically");
        require("codpattern:pistol|common=1.0;codpattern:rifle|common=0.5,rare=2.0".equals(add.fields().get("weapons")),
                "returned draft fields should stay aligned with parsed weapon candidates");

        Map<String, String> updateFields = new LinkedHashMap<>(add.fields());
        updateFields.put("price", "725");
        updateFields.put("refreshWaves", "2;5\n8");
        updateFields.put("rarityPools", "common=1,9.0,0.0;epic=4,0.0,3.5");
        updateFields.put("weapons", "codpattern:smg|epic=3.0;codpattern:rifle|common=0.25,epic=1.5");
        DeployEditResult update = deployEdit(
                add.objects(),
                "UPDATE",
                ZombiesDeployFieldSchema.WEAPON_WALL,
                0,
                updateFields);

        requireSuccess(update, "weapon_wall update with LIST fields should succeed");
        require(update.selectedIndex() == 0, "weapon_wall update should keep selected index");
        ZombiesWeaponWallData updated = only(update.objects().weaponWalls(), "updated weapon wall");
        require(updated.price() == 725, "weapon_wall update should apply scalar field with LIST fields");
        require(List.of(2, 5, 8).equals(updated.refreshWaves()),
                "weapon_wall update should replace refreshWaves from LIST rows");
        requireRarityPool(updated.rarityPools().get(1), "epic", 4, 0.0D, 3.5D);
        requireWeaponCandidate(updated.weapons().get(1), "codpattern:rifle", Map.of("common", 0.25D, "epic", 1.5D));
        require("2,5,8".equals(update.fields().get("refreshWaves")),
                "updated draft fields should mirror the stored refreshWaves order");
        require("codpattern:smg|epic=3.0;codpattern:rifle|common=0.25,epic=1.5".equals(update.fields().get("weapons")),
                "updated draft fields should mirror the stored weapon candidates");
    }

    private static void invalidWeaponWallListUpdatePreservesOriginalObjects() {
        DeployEditResult add = deployEdit(
                ZombiesMapObjects.EMPTY,
                "ADD",
                ZombiesDeployFieldSchema.WEAPON_WALL,
                -1,
                weaponWallFields("wall-invalid-list", "1,4", "codpattern:pistol|common=1.0"));
        requireSuccess(add, "setup weapon_wall add should succeed");
        ZombiesMapObjects original = add.objects();

        Map<String, String> badFields = new LinkedHashMap<>(add.fields());
        badFields.put("weapons", "codpattern:bad_without_weights");
        DeployEditResult invalid = deployEdit(
                original,
                "UPDATE",
                ZombiesDeployFieldSchema.WEAPON_WALL,
                0,
                badFields);

        requireFailure(invalid, "field.invalid_list", "invalid weapon LIST row should fail");
        require(invalid.objects() == original,
                "invalid weapon LIST update should return original objects for rollback");
        ZombiesWeaponWallData retained = only(original.weaponWalls(), "retained weapon wall");
        require("wall-invalid-list".equals(retained.objectId()),
                "invalid weapon LIST update should keep original object id");
        require(List.of(1, 4).equals(retained.refreshWaves()),
                "invalid weapon LIST update should not mutate previously parsed refresh waves");
        requireWeaponCandidate(retained.weapons().get(0), "codpattern:pistol", Map.of("common", 1.0D));
    }

    private static ZombiesServiceResult<ZombiesObjectStateStore.BarrierGroupUpdate> clearBarrierGroupPurchase(
            ZombiesEconomyService economy,
            ZombiesObjectStateStore store,
            ZombiesActiveSpawnGroupService activeGroups,
            List<ZombiesBarrierData> barriers,
            UUID playerId,
            int group,
            double cost
    ) {
        return economy.spendAtomically(playerId, cost, ignored -> {
            ZombiesServiceResult<ZombiesObjectStateStore.BarrierGroupUpdate> clear =
                    store.clearBarrierGroup(group, barriers);
            clear.value().ifPresent(update -> activeGroups.activate(update.group()));
            return clear;
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static DeployEditResult deployEdit(
            ZombiesMapObjects objects,
            String operation,
            String objectType,
            int selectedIndex,
            Map<String, String> fields
    ) {
        try {
            Class<?> editorClass = Class.forName("com.cdp.codpattern.app.zombies.deploy.ZombiesDeployObjectEditor");
            Class<?> operationClass = Class.forName("com.cdp.codpattern.app.zombies.deploy.ZombiesDeployObjectEditor$Operation");
            Method edit = editorClass.getDeclaredMethod(
                    "edit",
                    ZombiesMapObjects.class,
                    operationClass,
                    String.class,
                    int.class,
                    Map.class);
            edit.setAccessible(true);
            Object rawOperation = Enum.valueOf(operationClass.asSubclass(Enum.class), operation);
            Object rawResult = edit.invoke(null, objects, rawOperation, objectType, selectedIndex, fields);
            return new DeployEditResult(
                    booleanValue(rawResult, "success"),
                    stringValue(rawResult, "code"),
                    stringValue(rawResult, "detail"),
                    (ZombiesMapObjects) value(rawResult, "objects"),
                    intValue(rawResult, "selectedIndex"),
                    stringMap(rawResult, "fields"),
                    intValue(rawResult, "affectedCount"));
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError("deploy editor invocation failed", cause);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("deploy editor reflection failed", exception);
        }
    }

    private static Object value(Object target, String accessor) throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return method.invoke(target);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> stringMap(Object target, String accessor) throws ReflectiveOperationException {
        return (Map<String, String>) value(target, accessor);
    }

    private static boolean booleanValue(Object target, String accessor) throws ReflectiveOperationException {
        return (Boolean) value(target, accessor);
    }

    private static int intValue(Object target, String accessor) throws ReflectiveOperationException {
        return (Integer) value(target, accessor);
    }

    private static String stringValue(Object target, String accessor) throws ReflectiveOperationException {
        return (String) value(target, accessor);
    }

    private static Map<String, String> weaponWallFields(String objectId, String refreshWaves, String weapons) {
        Map<String, String> fields = new LinkedHashMap<>(ZombiesDeployFieldSchema.defaultFields(ZombiesDeployFieldSchema.WEAPON_WALL));
        fields.put("objectId", objectId);
        fields.put("weaponLevel", "2");
        fields.put("levelDamageMultiplier", "1.25");
        fields.put("price", "650");
        fields.put("maxReserveAmmo", "210");
        fields.put("refreshWaves", refreshWaves);
        fields.put("rarityPools", "common=1,10.0,0.0;rare=2,1.5,0.25");
        fields.put("weapons", weapons);
        fields.put("posX", "5");
        fields.put("posY", "64");
        fields.put("posZ", "6");
        return fields;
    }

    private static ZombiesBarrierData barrier(String objectId, int group) {
        return new ZombiesBarrierData(
                objectId,
                group,
                750,
                true,
                dimension(),
                new BlockPos(5 + group, 64, 5),
                new BlockPos(5 + group, 66, 7),
                new BlockPos(5 + group, 65, 5));
    }

    private static ModeObjectState barrierState(
            ZombiesObjectStateStore store,
            List<ZombiesBarrierData> barriers,
            String objectKey
    ) {
        return store.barrierStates(barriers).stream()
                .filter(state -> objectKey.equals(state.objectKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing barrier state " + objectKey));
    }

    private static void requireCleared(
            ZombiesObjectStateStore store,
            List<ZombiesBarrierData> barriers,
            String objectKey,
            boolean expected
    ) {
        CompoundTag payload = barrierState(store, barriers, objectKey).payload();
        require(payload.getBoolean("cleared") == expected,
                objectKey + " cleared should be " + expected + " but was " + payload.getBoolean("cleared"));
    }

    private static <T> T only(List<T> values, String label) {
        require(values.size() == 1, label + " should have exactly one entry but had " + values.size());
        return values.get(0);
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

    private static void requirePoints(ZombiesPlayerStateService players, UUID playerId, double expected, String message) {
        requireClose(players.get(playerId).orElseThrow().points(), expected, message + ": balance");
    }

    private static void requireSuccess(ZombiesServiceResult<?> result, String message) {
        require(result.success(), message + ": " + result.code());
    }

    private static void requireSuccess(DeployEditResult result, String message) {
        require(result.success(), message + ": " + result.code() + " " + result.detail());
    }

    private static void requireFailure(ZombiesServiceResult<?> result, ZombiesErrorCode expectedCode, String message) {
        require(!result.success(), message + ": expected failure");
        require(expectedCode.equals(result.code()),
                message + ": expected " + expectedCode + " but was " + result.code());
    }

    private static void requireFailure(DeployEditResult result, String expectedCode, String message) {
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

    private static UUID playerId(int suffix) {
        return new UUID(0L, suffix);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ResourceKey<Level> dimension() {
        try {
            Constructor<ResourceKey> constructor =
                    ResourceKey.class.getDeclaredConstructor(ResourceLocation.class, ResourceLocation.class);
            constructor.setAccessible(true);
            return (ResourceKey<Level>) constructor.newInstance(
                    resourceLocation("minecraft:dimension"),
                    resourceLocation("minecraft:overworld"));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("failed to create test dimension key", exception);
        }
    }

    private static ResourceLocation resourceLocation(String value) {
        ResourceLocation location = ResourceLocation.tryParse(value);
        if (location == null) {
            throw new AssertionError("invalid resource location " + value);
        }
        return location;
    }

    private record DeployEditResult(
            boolean success,
            String code,
            String detail,
            ZombiesMapObjects objects,
            int selectedIndex,
            Map<String, String> fields,
            int affectedCount
    ) {
    }

    private static final class ObjectHolder {
        private ZombiesMapObjects objects;

        private ObjectHolder(ZombiesMapObjects objects) {
            this.objects = objects == null ? ZombiesMapObjects.EMPTY : objects;
        }

        private ZombiesMapObjects objects() {
            return objects;
        }

        private void apply(ZombiesMapObjects objects) {
            this.objects = objects == null ? ZombiesMapObjects.EMPTY : objects;
        }
    }
}
