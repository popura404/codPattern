package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModeObjectState;
import com.cdp.codpattern.app.zombies.map.object.ZombiesAmmoBoxData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesArmorStationData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesPowerSwitchData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesSodaMachineData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesUltimateMachineData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesWeaponWallData;
import com.cdp.codpattern.app.zombies.sync.ZombiesObjectStateKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class ZombiesObjectStateStore {
    private static final String OBJECT_TYPE_BARRIER = "barrier";
    private static final String OBJECT_TYPE_WEAPON_WALL = "weapon_wall";
    private static final String OBJECT_TYPE_AMMO_BOX = "ammo_box";
    private static final String OBJECT_TYPE_ARMOR_STATION = "armor_station";
    private static final String OBJECT_TYPE_POWER_SWITCH = "power_switch";
    private static final String OBJECT_TYPE_SODA_MACHINE = "soda_machine";
    private static final String OBJECT_TYPE_ULTIMATE_MACHINE = "ultimate_machine";
    private static final String PAYLOAD_OBJECT_ID = "objectId";
    private static final String PAYLOAD_GROUP = "group";
    private static final String PAYLOAD_CLEARED = "cleared";
    private static final String PAYLOAD_GUN_ID = "gunId";
    private static final String PAYLOAD_WEAPON_LEVEL = "weaponLevel";
    private static final String PAYLOAD_MAX_RESERVE_AMMO = "maxReserveAmmo";
    private static final String PAYLOAD_LEVEL_DAMAGE_MULTIPLIER = "levelDamageMultiplier";
    private static final String PAYLOAD_ARMOR_LEVEL = "armorLevel";
    private static final String PAYLOAD_REQUIRES_POWER = "requiresPower";
    private static final String PAYLOAD_POWER_ON = "powerOn";
    private static final String PAYLOAD_BUFF_ID = "buffId";
    private static final String PAYLOAD_MAX_UPGRADE_LEVEL = "maxUpgradeLevel";

    private final Map<String, BarrierRuntimeState> barriersByObjectId = new LinkedHashMap<>();
    private final Map<String, WeaponWallRuntimeState> weaponWallsByObjectId = new LinkedHashMap<>();
    private final Map<String, Long> ammoBoxRevisionsByObjectId = new LinkedHashMap<>();
    private final Map<String, Long> armorStationRevisionsByObjectId = new LinkedHashMap<>();
    private final Map<String, Long> powerSwitchRevisionsByObjectId = new LinkedHashMap<>();
    private final Map<String, Long> sodaMachineRevisionsByObjectId = new LinkedHashMap<>();
    private final Map<String, Long> ultimateMachineRevisionsByObjectId = new LinkedHashMap<>();
    private final BooleanSupplier powerOnSupplier;
    private long revision;

    public ZombiesObjectStateStore() {
        this(() -> false);
    }

    public ZombiesObjectStateStore(BooleanSupplier powerOnSupplier) {
        this.powerOnSupplier = powerOnSupplier == null ? () -> false : powerOnSupplier;
    }

    public synchronized void resetBarriers(Collection<ZombiesBarrierData> barriers) {
        resetObjects(barriers, List.of(), List.of(), List.of());
    }

    public synchronized void resetObjects(
            Collection<ZombiesBarrierData> barriers,
            Collection<ZombiesWeaponWallData> weaponWalls,
            Collection<ZombiesAmmoBoxData> ammoBoxes,
            Collection<ZombiesArmorStationData> armorStations
    ) {
        resetObjects(barriers, weaponWalls, ammoBoxes, armorStations, 1, 0);
    }

    public synchronized void resetObjects(
            Collection<ZombiesBarrierData> barriers,
            Collection<ZombiesWeaponWallData> weaponWalls,
            Collection<ZombiesAmmoBoxData> ammoBoxes,
            Collection<ZombiesArmorStationData> armorStations,
            int currentWave,
            int maxWave
    ) {
        resetObjects(barriers, weaponWalls, ammoBoxes, armorStations, Optional.empty(), List.of(), List.of(), currentWave, maxWave);
    }

    public synchronized void resetObjects(
            Collection<ZombiesBarrierData> barriers,
            Collection<ZombiesWeaponWallData> weaponWalls,
            Collection<ZombiesAmmoBoxData> ammoBoxes,
            Collection<ZombiesArmorStationData> armorStations,
            Optional<ZombiesPowerSwitchData> powerSwitch,
            Collection<ZombiesSodaMachineData> sodaMachines,
            Collection<ZombiesUltimateMachineData> ultimateMachines,
            int currentWave,
            int maxWave
    ) {
        List<ZombiesBarrierData> snapshot = safeBarriers(barriers);
        Map<String, BarrierRuntimeState> next = new LinkedHashMap<>();
        for (ZombiesBarrierData barrier : snapshot) {
            String objectId = objectKey(barrier);
            next.put(objectId, new BarrierRuntimeState(barrier.group(), false, nextRevision()));
        }
        barriersByObjectId.clear();
        barriersByObjectId.putAll(next);
        resetWeaponWallStates(safeWeaponWalls(weaponWalls), currentWave, maxWave);
        resetStableRevisions(ammoBoxRevisionsByObjectId, safeAmmoBoxes(ammoBoxes));
        resetStableRevisions(armorStationRevisionsByObjectId, safeArmorStations(armorStations));
        resetPowerSwitchRevision(powerSwitch);
        resetStableRevisions(sodaMachineRevisionsByObjectId, safeSodaMachines(sodaMachines));
        resetStableRevisions(ultimateMachineRevisionsByObjectId, safeUltimateMachines(ultimateMachines));
    }

    public synchronized ZombiesServiceResult<BarrierGroupUpdate> clearBarrierGroup(
            int group,
            Collection<ZombiesBarrierData> barriers
    ) {
        if (group < 1) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.OBJECT_NOT_FOUND);
        }

        List<ZombiesBarrierData> groupBarriers = safeBarriers(barriers).stream()
                .filter(barrier -> barrier.group() == group)
                .toList();
        if (groupBarriers.isEmpty()) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.OBJECT_NOT_FOUND);
        }

        boolean alreadyCleared = true;
        for (ZombiesBarrierData barrier : groupBarriers) {
            BarrierRuntimeState state = ensureBarrierState(barrier);
            if (!state.cleared()) {
                alreadyCleared = false;
                break;
            }
        }
        if (alreadyCleared) {
            return ZombiesServiceResult.failure(ZombiesErrorCode.of("barrier.already_cleared"));
        }

        long updateRevision = revision;
        List<String> objectIds = new ArrayList<>();
        for (ZombiesBarrierData barrier : groupBarriers) {
            String objectId = objectKey(barrier);
            objectIds.add(objectId);
            updateRevision = nextRevision();
            barriersByObjectId.put(objectId, new BarrierRuntimeState(group, true, updateRevision));
        }
        return ZombiesServiceResult.success(new BarrierGroupUpdate(group, List.copyOf(objectIds), updateRevision));
    }

    public synchronized boolean isBarrierCleared(ZombiesBarrierData barrier) {
        if (barrier == null) {
            return false;
        }
        return ensureBarrierState(barrier).cleared();
    }

    public synchronized List<ModeObjectState> barrierStates(Collection<ZombiesBarrierData> barriers) {
        List<ModeObjectState> states = new ArrayList<>();
        for (ZombiesBarrierData barrier : safeBarriers(barriers)) {
            String objectId = objectKey(barrier);
            BarrierRuntimeState state = ensureBarrierState(barrier);
            states.add(toModeObjectState(objectId, barrier, state));
        }
        return List.copyOf(states);
    }

    public synchronized List<ModeObjectState> objectStates(
            Collection<ZombiesBarrierData> barriers,
            Collection<ZombiesWeaponWallData> weaponWalls,
            Collection<ZombiesAmmoBoxData> ammoBoxes,
            Collection<ZombiesArmorStationData> armorStations
    ) {
        return objectStates(barriers, weaponWalls, ammoBoxes, armorStations, Optional.empty(), List.of(), List.of());
    }

    public synchronized List<ModeObjectState> objectStates(
            Collection<ZombiesBarrierData> barriers,
            Collection<ZombiesWeaponWallData> weaponWalls,
            Collection<ZombiesAmmoBoxData> ammoBoxes,
            Collection<ZombiesArmorStationData> armorStations,
            Optional<ZombiesPowerSwitchData> powerSwitch,
            Collection<ZombiesSodaMachineData> sodaMachines,
            Collection<ZombiesUltimateMachineData> ultimateMachines
    ) {
        List<ModeObjectState> states = new ArrayList<>(barrierStates(barriers));
        for (ZombiesWeaponWallData weaponWall : safeWeaponWalls(weaponWalls)) {
            String objectId = objectKey(weaponWall);
            WeaponWallRuntimeState runtimeState = ensureWeaponWallState(weaponWall);
            states.add(toModeObjectState(objectId, weaponWall, runtimeState));
        }
        for (ZombiesAmmoBoxData ammoBox : safeAmmoBoxes(ammoBoxes)) {
            String objectId = objectKey(ammoBox);
            long objectRevision = ensureStableRevision(ammoBoxRevisionsByObjectId, objectId);
            states.add(toModeObjectState(objectId, ammoBox, objectRevision));
        }
        for (ZombiesArmorStationData armorStation : safeArmorStations(armorStations)) {
            String objectId = objectKey(armorStation);
            long objectRevision = ensureStableRevision(armorStationRevisionsByObjectId, objectId);
            states.add(toModeObjectState(objectId, armorStation, objectRevision));
        }
        for (ZombiesPowerSwitchData switchData : safePowerSwitch(powerSwitch)) {
            String objectId = objectKey(switchData);
            long objectRevision = ensureStableRevision(powerSwitchRevisionsByObjectId, objectId);
            states.add(toModeObjectState(objectId, switchData, objectRevision));
        }
        for (ZombiesSodaMachineData sodaMachine : safeSodaMachines(sodaMachines)) {
            String objectId = objectKey(sodaMachine);
            long objectRevision = ensureStableRevision(sodaMachineRevisionsByObjectId, objectId);
            states.add(toModeObjectState(objectId, sodaMachine, objectRevision));
        }
        for (ZombiesUltimateMachineData ultimateMachine : safeUltimateMachines(ultimateMachines)) {
            String objectId = objectKey(ultimateMachine);
            long objectRevision = ensureStableRevision(ultimateMachineRevisionsByObjectId, objectId);
            states.add(toModeObjectState(objectId, ultimateMachine, objectRevision));
        }
        return List.copyOf(states);
    }

    public synchronized long markWeaponWallPurchased(ZombiesWeaponWallData weaponWall) {
        if (weaponWall == null) {
            return revision;
        }
        long nextRevision = nextRevision();
        WeaponWallRuntimeState state = ensureWeaponWallState(weaponWall);
        weaponWallsByObjectId.put(objectKey(weaponWall), state.withRevision(nextRevision));
        return nextRevision;
    }

    public synchronized WeaponWallOffer currentWeaponWallOffer(ZombiesWeaponWallData weaponWall) {
        if (weaponWall == null) {
            return WeaponWallOffer.empty();
        }
        return ensureWeaponWallState(weaponWall).offer();
    }

    public synchronized long refreshWeaponWallOffersForWave(
            Collection<ZombiesWeaponWallData> weaponWalls,
            int targetWave,
            int maxWave
    ) {
        if (targetWave < 1) {
            return revision;
        }
        long updateRevision = revision;
        for (ZombiesWeaponWallData weaponWall : safeWeaponWalls(weaponWalls)) {
            String objectId = objectKey(weaponWall);
            WeaponWallRuntimeState currentState = ensureWeaponWallState(weaponWall);
            if (!shouldRefreshWeaponWall(weaponWall, targetWave, maxWave)) {
                continue;
            }
            if (currentState.lastRefreshWave() == targetWave) {
                continue;
            }
            updateRevision = nextRevision();
            weaponWallsByObjectId.put(
                    objectId,
                    new WeaponWallRuntimeState(selectOffer(weaponWall, targetWave, maxWave), updateRevision, targetWave));
        }
        return updateRevision;
    }

    public synchronized long markAmmoBoxUsed(ZombiesAmmoBoxData ammoBox) {
        if (ammoBox == null) {
            return revision;
        }
        long nextRevision = nextRevision();
        ammoBoxRevisionsByObjectId.put(objectKey(ammoBox), nextRevision);
        return nextRevision;
    }

    public synchronized long markArmorStationPurchased(ZombiesArmorStationData armorStation) {
        if (armorStation == null) {
            return revision;
        }
        long nextRevision = nextRevision();
        armorStationRevisionsByObjectId.put(objectKey(armorStation), nextRevision);
        return nextRevision;
    }

    public synchronized long markPowerSwitchTurnedOn(ZombiesPowerSwitchData powerSwitch) {
        if (powerSwitch == null) {
            return revision;
        }
        long nextRevision = nextRevision();
        powerSwitchRevisionsByObjectId.put(objectKey(powerSwitch), nextRevision);
        bumpRequiresPowerObjectRevisions();
        return revision;
    }

    public synchronized long markSodaMachinePurchased(ZombiesSodaMachineData sodaMachine) {
        if (sodaMachine == null) {
            return revision;
        }
        long nextRevision = nextRevision();
        sodaMachineRevisionsByObjectId.put(objectKey(sodaMachine), nextRevision);
        return nextRevision;
    }

    public synchronized long markUltimateMachineUsed(ZombiesUltimateMachineData ultimateMachine) {
        if (ultimateMachine == null) {
            return revision;
        }
        long nextRevision = nextRevision();
        ultimateMachineRevisionsByObjectId.put(objectKey(ultimateMachine), nextRevision);
        return nextRevision;
    }

    public synchronized long revision() {
        return revision;
    }

    private BarrierRuntimeState ensureBarrierState(ZombiesBarrierData barrier) {
        String objectId = objectKey(barrier);
        BarrierRuntimeState state = barriersByObjectId.get(objectId);
        if (state == null || state.group() != barrier.group()) {
            state = new BarrierRuntimeState(barrier.group(), false, nextRevision());
            barriersByObjectId.put(objectId, state);
        }
        return state;
    }

    private WeaponWallRuntimeState ensureWeaponWallState(ZombiesWeaponWallData weaponWall) {
        String objectId = objectKey(weaponWall);
        WeaponWallRuntimeState state = weaponWallsByObjectId.get(objectId);
        if (state == null) {
            state = new WeaponWallRuntimeState(selectOffer(weaponWall, 1, 0), 0L, 0);
            weaponWallsByObjectId.put(objectId, state);
        }
        return state;
    }

    private ModeObjectState toModeObjectState(
            String objectId,
            ZombiesBarrierData barrier,
            BarrierRuntimeState state
    ) {
        CompoundTag payload = new CompoundTag();
        payload.putString(PAYLOAD_OBJECT_ID, objectId);
        payload.putString(ZombiesObjectStateKeys.PAYLOAD_TYPE, OBJECT_TYPE_BARRIER);
        payload.putInt(PAYLOAD_GROUP, barrier.group());
        payload.putInt(ZombiesObjectStateKeys.PAYLOAD_COST, Math.max(0, barrier.cost()));
        payload.putBoolean(PAYLOAD_CLEARED, state.cleared());
        payload.putBoolean(ZombiesObjectStateKeys.PAYLOAD_ENABLED, !state.cleared());
        return new ModeObjectState(
                objectId,
                ZombiesObjectStateKeys.STATUS,
                barrier.interactionPos(),
                payload,
                state.revision());
    }

    private ModeObjectState toModeObjectState(
            String objectId,
            ZombiesWeaponWallData weaponWall,
            WeaponWallRuntimeState runtimeState
    ) {
        WeaponWallOffer offer = runtimeState.offer();
        CompoundTag payload = basePurchasePayload(
                objectId,
                OBJECT_TYPE_WEAPON_WALL,
                Math.max(0, offer.price()),
                offer.purchasable());
        payload.putString(PAYLOAD_GUN_ID, offer.gunId());
        payload.putInt(PAYLOAD_WEAPON_LEVEL, Math.max(0, offer.weaponLevel()));
        payload.putInt(PAYLOAD_MAX_RESERVE_AMMO, Math.max(0, offer.maxReserveAmmo()));
        payload.putDouble(PAYLOAD_LEVEL_DAMAGE_MULTIPLIER, offer.levelDamageMultiplier());
        return new ModeObjectState(
                objectId,
                ZombiesObjectStateKeys.STATUS,
                interactionPosition(weaponWall),
                payload,
                runtimeState.revision());
    }

    private ModeObjectState toModeObjectState(
            String objectId,
            ZombiesAmmoBoxData ammoBox,
            long objectRevision
    ) {
        CompoundTag payload = basePurchasePayload(
                objectId,
                OBJECT_TYPE_AMMO_BOX,
                displayAmmoCost(ammoBox),
                !ammoBox.pricesByWeaponLevel().isEmpty());
        return new ModeObjectState(
                objectId,
                ZombiesObjectStateKeys.STATUS,
                interactionPosition(ammoBox),
                payload,
                objectRevision);
    }

    private ModeObjectState toModeObjectState(
            String objectId,
            ZombiesArmorStationData armorStation,
            long objectRevision
    ) {
        CompoundTag payload = basePurchasePayload(
                objectId,
                OBJECT_TYPE_ARMOR_STATION,
                Math.max(0, armorStation.buyCost()),
                armorStation.armorLevel() >= 1
                        && armorStation.armorLevel() <= 3
                        && armorStation.buyCost() >= 0
                        && Double.isFinite(armorStation.damageTakenMultiplier())
                        && armorStation.damageTakenMultiplier() > 0.0D
                        && armorStation.damageTakenMultiplier() <= 1.0D);
        payload.putInt(PAYLOAD_ARMOR_LEVEL, Math.max(0, armorStation.armorLevel()));
        return new ModeObjectState(
                objectId,
                ZombiesObjectStateKeys.STATUS,
                interactionPosition(armorStation),
                payload,
                objectRevision);
    }

    private ModeObjectState toModeObjectState(
            String objectId,
            ZombiesPowerSwitchData powerSwitch,
            long objectRevision
    ) {
        boolean powerOn = isPowerOn();
        CompoundTag payload = basePurchasePayload(
                objectId,
                OBJECT_TYPE_POWER_SWITCH,
                Math.max(0, powerSwitch.cost()),
                !powerOn);
        payload.putBoolean(PAYLOAD_POWER_ON, powerOn);
        return new ModeObjectState(
                objectId,
                ZombiesObjectStateKeys.STATUS,
                interactionPosition(powerSwitch),
                payload,
                objectRevision);
    }

    private ModeObjectState toModeObjectState(
            String objectId,
            ZombiesSodaMachineData sodaMachine,
            long objectRevision
    ) {
        boolean powerOn = isPowerOn();
        boolean enabled = sodaMachine.cost() >= 0 && (!sodaMachine.requiresPower() || powerOn);
        CompoundTag payload = basePurchasePayload(
                objectId,
                OBJECT_TYPE_SODA_MACHINE,
                Math.max(0, sodaMachine.cost()),
                enabled);
        payload.putBoolean(PAYLOAD_REQUIRES_POWER, sodaMachine.requiresPower());
        payload.putBoolean(PAYLOAD_POWER_ON, powerOn);
        payload.putString(PAYLOAD_BUFF_ID, sodaMachine.buffId());
        return new ModeObjectState(
                objectId,
                ZombiesObjectStateKeys.STATUS,
                interactionPosition(sodaMachine),
                payload,
                objectRevision);
    }

    private ModeObjectState toModeObjectState(
            String objectId,
            ZombiesUltimateMachineData ultimateMachine,
            long objectRevision
    ) {
        boolean powerOn = isPowerOn();
        boolean enabled = ultimateMachine.maxUpgradeLevel() > 0
                && !ultimateMachine.levels().isEmpty()
                && (!ultimateMachine.requiresPower() || powerOn);
        CompoundTag payload = basePurchasePayload(
                objectId,
                OBJECT_TYPE_ULTIMATE_MACHINE,
                displayUltimateCost(ultimateMachine),
                enabled);
        payload.putBoolean(PAYLOAD_REQUIRES_POWER, ultimateMachine.requiresPower());
        payload.putBoolean(PAYLOAD_POWER_ON, powerOn);
        payload.putInt(PAYLOAD_MAX_UPGRADE_LEVEL, Math.max(0, ultimateMachine.maxUpgradeLevel()));
        return new ModeObjectState(
                objectId,
                ZombiesObjectStateKeys.STATUS,
                interactionPosition(ultimateMachine),
                payload,
                objectRevision);
    }

    private CompoundTag basePurchasePayload(String objectId, String type, int cost, boolean enabled) {
        CompoundTag payload = new CompoundTag();
        payload.putString(PAYLOAD_OBJECT_ID, objectId);
        payload.putString(ZombiesObjectStateKeys.PAYLOAD_TYPE, type);
        payload.putInt(ZombiesObjectStateKeys.PAYLOAD_COST, Math.max(0, cost));
        payload.putBoolean(ZombiesObjectStateKeys.PAYLOAD_ENABLED, enabled);
        return payload;
    }

    private long nextRevision() {
        revision = revision == Long.MAX_VALUE ? 1L : revision + 1L;
        return revision;
    }

    private <T> void resetStableRevisions(Map<String, Long> revisionsByObjectId, List<T> objects) {
        Map<String, Long> next = new LinkedHashMap<>();
        for (T object : objects) {
            next.put(objectKey(object), nextRevision());
        }
        revisionsByObjectId.clear();
        revisionsByObjectId.putAll(next);
    }

    private void resetPowerSwitchRevision(Optional<ZombiesPowerSwitchData> powerSwitch) {
        Map<String, Long> next = new LinkedHashMap<>();
        safePowerSwitch(powerSwitch).forEach(value -> next.put(objectKey(value), nextRevision()));
        powerSwitchRevisionsByObjectId.clear();
        powerSwitchRevisionsByObjectId.putAll(next);
    }

    private void bumpRequiresPowerObjectRevisions() {
        for (String objectId : new ArrayList<>(sodaMachineRevisionsByObjectId.keySet())) {
            sodaMachineRevisionsByObjectId.put(objectId, nextRevision());
        }
        for (String objectId : new ArrayList<>(ultimateMachineRevisionsByObjectId.keySet())) {
            ultimateMachineRevisionsByObjectId.put(objectId, nextRevision());
        }
    }

    private void resetWeaponWallStates(List<ZombiesWeaponWallData> weaponWalls, int currentWave, int maxWave) {
        Map<String, WeaponWallRuntimeState> next = new LinkedHashMap<>();
        int offerWave = Math.max(1, currentWave);
        for (ZombiesWeaponWallData weaponWall : weaponWalls) {
            next.put(
                    objectKey(weaponWall),
                    new WeaponWallRuntimeState(selectOffer(weaponWall, offerWave, maxWave), nextRevision(), 0));
        }
        weaponWallsByObjectId.clear();
        weaponWallsByObjectId.putAll(next);
    }

    private static long ensureStableRevision(Map<String, Long> revisionsByObjectId, String objectId) {
        Long objectRevision = revisionsByObjectId.get(objectId);
        if (objectRevision == null) {
            revisionsByObjectId.put(objectId, 0L);
            return 0L;
        }
        return Math.max(0L, objectRevision);
    }

    private static List<ZombiesBarrierData> safeBarriers(Collection<ZombiesBarrierData> barriers) {
        if (barriers == null || barriers.isEmpty()) {
            return List.of();
        }
        return barriers.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<ZombiesWeaponWallData> safeWeaponWalls(Collection<ZombiesWeaponWallData> weaponWalls) {
        if (weaponWalls == null || weaponWalls.isEmpty()) {
            return List.of();
        }
        return weaponWalls.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<ZombiesAmmoBoxData> safeAmmoBoxes(Collection<ZombiesAmmoBoxData> ammoBoxes) {
        if (ammoBoxes == null || ammoBoxes.isEmpty()) {
            return List.of();
        }
        return ammoBoxes.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<ZombiesArmorStationData> safeArmorStations(Collection<ZombiesArmorStationData> armorStations) {
        if (armorStations == null || armorStations.isEmpty()) {
            return List.of();
        }
        return armorStations.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<ZombiesPowerSwitchData> safePowerSwitch(Optional<ZombiesPowerSwitchData> powerSwitch) {
        return powerSwitch == null || powerSwitch.isEmpty() ? List.of() : List.of(powerSwitch.get());
    }

    private static List<ZombiesSodaMachineData> safeSodaMachines(Collection<ZombiesSodaMachineData> sodaMachines) {
        if (sodaMachines == null || sodaMachines.isEmpty()) {
            return List.of();
        }
        return sodaMachines.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<ZombiesUltimateMachineData> safeUltimateMachines(Collection<ZombiesUltimateMachineData> ultimateMachines) {
        if (ultimateMachines == null || ultimateMachines.isEmpty()) {
            return List.of();
        }
        return ultimateMachines.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private static boolean shouldRefreshWeaponWall(ZombiesWeaponWallData weaponWall, int targetWave, int maxWave) {
        if (weaponWall == null || targetWave < 1) {
            return false;
        }
        if (targetWave == 1) {
            return true;
        }
        if (maxWave > 0 && targetWave >= maxWave) {
            return true;
        }
        for (Integer refreshWave : weaponWall.refreshWaves()) {
            if (refreshWave != null && refreshWave == targetWave) {
                return true;
            }
        }
        return false;
    }

    private static WeaponWallOffer selectOffer(ZombiesWeaponWallData weaponWall, int currentWave, int maxWave) {
        if (weaponWall == null) {
            return WeaponWallOffer.empty();
        }
        String selectedGunId = selectedGunId(weaponWall, Math.max(1, currentWave), Math.max(0, maxWave));
        return new WeaponWallOffer(
                selectedGunId,
                weaponWall.weaponLevel(),
                weaponWall.price(),
                weaponWall.maxReserveAmmo(),
                weaponWall.levelDamageMultiplier());
    }

    private static String selectedGunId(ZombiesWeaponWallData weaponWall, int currentWave, int maxWave) {
        String fallback = firstValidGunId(weaponWall);
        if (fallback.isBlank()) {
            return "";
        }
        List<ZombiesWeaponWallData.RarityPoolData> pools = weaponWall.rarityPools().stream()
                .filter(Objects::nonNull)
                .filter(pool -> !pool.id().isBlank())
                .toList();
        if (pools.isEmpty()) {
            return fallback;
        }

        if (maxWave > 0 && currentWave >= maxWave) {
            int highestRank = pools.stream()
                    .mapToInt(ZombiesWeaponWallData.RarityPoolData::rank)
                    .max()
                    .orElse(Integer.MIN_VALUE);
            String topRankGunId = selectedGunIdForPools(
                    weaponWall,
                    pools.stream().filter(pool -> pool.rank() == highestRank).toList());
            return topRankGunId.isBlank() ? fallback : topRankGunId;
        }

        ZombiesWeaponWallData.RarityPoolData selectedPool = null;
        double selectedWeight = 0.0D;
        for (ZombiesWeaponWallData.RarityPoolData pool : pools) {
            double weight = poolWeight(pool, currentWave);
            if (weight > selectedWeight && !selectedGunIdForPool(weaponWall, pool.id()).isBlank()) {
                selectedPool = pool;
                selectedWeight = weight;
            }
        }
        if (selectedPool == null) {
            return fallback;
        }
        String selected = selectedGunIdForPool(weaponWall, selectedPool.id());
        return selected.isBlank() ? fallback : selected;
    }

    private static String selectedGunIdForPools(
            ZombiesWeaponWallData weaponWall,
            List<ZombiesWeaponWallData.RarityPoolData> pools
    ) {
        String selected = "";
        double selectedWeight = 0.0D;
        for (ZombiesWeaponWallData.RarityPoolData pool : pools) {
            CandidateWeight candidate = selectedCandidateForRarity(weaponWall, pool.id());
            if (candidate.weight() > selectedWeight) {
                selected = candidate.gunId();
                selectedWeight = candidate.weight();
            }
        }
        return selected;
    }

    private static String selectedGunIdForPool(ZombiesWeaponWallData weaponWall, String rarityId) {
        return selectedCandidateForRarity(weaponWall, rarityId).gunId();
    }

    private static CandidateWeight selectedCandidateForRarity(ZombiesWeaponWallData weaponWall, String rarityId) {
        String selected = "";
        double selectedWeight = 0.0D;
        String cleanedRarity = Objects.requireNonNullElse(rarityId, "").trim();
        if (cleanedRarity.isBlank()) {
            return new CandidateWeight("", 0.0D);
        }
        for (ZombiesWeaponWallData.WeaponCandidateData weapon : weaponWall.weapons()) {
            String gunId = weapon == null ? "" : Objects.requireNonNullElse(weapon.gunId(), "").trim();
            if (gunId.isBlank()) {
                continue;
            }
            Double configuredWeight = weapon.weightsByRarity().get(cleanedRarity);
            double weight = configuredWeight == null || !Double.isFinite(configuredWeight)
                    ? 0.0D
                    : Math.max(0.0D, configuredWeight);
            if (weight > selectedWeight) {
                selected = gunId;
                selectedWeight = weight;
            }
        }
        return new CandidateWeight(selected, selectedWeight);
    }

    private static double poolWeight(ZombiesWeaponWallData.RarityPoolData pool, int currentWave) {
        double baseWeight = Double.isFinite(pool.baseWeight()) ? pool.baseWeight() : 0.0D;
        double waveFactor = Double.isFinite(pool.waveFactor()) ? pool.waveFactor() : 0.0D;
        return Math.max(0.0D, baseWeight + waveFactor * Math.max(1, currentWave));
    }

    static String objectKey(ZombiesBarrierData barrier) {
        String objectId = barrier == null ? "" : Objects.requireNonNullElse(barrier.objectId(), "").trim();
        if (!objectId.isBlank()) {
            return objectId;
        }
        BlockPos pos = barrier == null || barrier.interactionPos() == null ? BlockPos.ZERO : barrier.interactionPos();
        int group = barrier == null ? 0 : barrier.group();
        return "barrier:" + group + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    static String objectKey(ZombiesWeaponWallData weaponWall) {
        String objectId = weaponWall == null ? "" : Objects.requireNonNullElse(weaponWall.objectId(), "").trim();
        if (!objectId.isBlank()) {
            return objectId;
        }
        BlockPos pos = weaponWall == null ? BlockPos.ZERO : interactionPosition(weaponWall);
        return "weapon_wall:" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    static String objectKey(ZombiesAmmoBoxData ammoBox) {
        String objectId = ammoBox == null ? "" : Objects.requireNonNullElse(ammoBox.objectId(), "").trim();
        if (!objectId.isBlank()) {
            return objectId;
        }
        BlockPos pos = ammoBox == null ? BlockPos.ZERO : interactionPosition(ammoBox);
        return "ammo_box:" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    static String objectKey(ZombiesArmorStationData armorStation) {
        String objectId = armorStation == null ? "" : Objects.requireNonNullElse(armorStation.objectId(), "").trim();
        if (!objectId.isBlank()) {
            return objectId;
        }
        BlockPos pos = armorStation == null ? BlockPos.ZERO : interactionPosition(armorStation);
        return "armor_station:" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    static String objectKey(ZombiesPowerSwitchData powerSwitch) {
        String objectId = powerSwitch == null ? "" : Objects.requireNonNullElse(powerSwitch.objectId(), "").trim();
        if (!objectId.isBlank()) {
            return objectId;
        }
        BlockPos pos = powerSwitch == null ? BlockPos.ZERO : interactionPosition(powerSwitch);
        return "power_switch:" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    static String objectKey(ZombiesSodaMachineData sodaMachine) {
        String objectId = sodaMachine == null ? "" : Objects.requireNonNullElse(sodaMachine.objectId(), "").trim();
        if (!objectId.isBlank()) {
            return objectId;
        }
        BlockPos pos = sodaMachine == null ? BlockPos.ZERO : interactionPosition(sodaMachine);
        return "soda_machine:" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    static String objectKey(ZombiesUltimateMachineData ultimateMachine) {
        String objectId = ultimateMachine == null ? "" : Objects.requireNonNullElse(ultimateMachine.objectId(), "").trim();
        if (!objectId.isBlank()) {
            return objectId;
        }
        BlockPos pos = ultimateMachine == null ? BlockPos.ZERO : interactionPosition(ultimateMachine);
        return "ultimate_machine:" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String objectKey(Object object) {
        if (object instanceof ZombiesBarrierData barrier) {
            return objectKey(barrier);
        }
        if (object instanceof ZombiesWeaponWallData weaponWall) {
            return objectKey(weaponWall);
        }
        if (object instanceof ZombiesAmmoBoxData ammoBox) {
            return objectKey(ammoBox);
        }
        if (object instanceof ZombiesArmorStationData armorStation) {
            return objectKey(armorStation);
        }
        if (object instanceof ZombiesPowerSwitchData powerSwitch) {
            return objectKey(powerSwitch);
        }
        if (object instanceof ZombiesSodaMachineData sodaMachine) {
            return objectKey(sodaMachine);
        }
        if (object instanceof ZombiesUltimateMachineData ultimateMachine) {
            return objectKey(ultimateMachine);
        }
        return "";
    }

    private static BlockPos interactionPosition(ZombiesWeaponWallData weaponWall) {
        if (weaponWall == null) {
            return BlockPos.ZERO;
        }
        return weaponWall.interactionPos().orElse(weaponWall.pos());
    }

    private static BlockPos interactionPosition(ZombiesAmmoBoxData ammoBox) {
        if (ammoBox == null) {
            return BlockPos.ZERO;
        }
        return ammoBox.interactionPos().orElse(ammoBox.pos());
    }

    private static BlockPos interactionPosition(ZombiesArmorStationData armorStation) {
        if (armorStation == null) {
            return BlockPos.ZERO;
        }
        return armorStation.interactionPos().orElse(armorStation.pos());
    }

    private static BlockPos interactionPosition(ZombiesPowerSwitchData powerSwitch) {
        if (powerSwitch == null) {
            return BlockPos.ZERO;
        }
        return powerSwitch.interactionPos().orElse(powerSwitch.pos());
    }

    private static BlockPos interactionPosition(ZombiesSodaMachineData sodaMachine) {
        if (sodaMachine == null) {
            return BlockPos.ZERO;
        }
        return sodaMachine.interactionPos().orElse(sodaMachine.pos());
    }

    private static BlockPos interactionPosition(ZombiesUltimateMachineData ultimateMachine) {
        if (ultimateMachine == null) {
            return BlockPos.ZERO;
        }
        return ultimateMachine.interactionPos().orElse(ultimateMachine.pos());
    }

    private static String firstValidGunId(ZombiesWeaponWallData weaponWall) {
        if (weaponWall == null) {
            return "";
        }
        for (ZombiesWeaponWallData.WeaponCandidateData weapon : weaponWall.weapons()) {
            String gunId = weapon == null ? "" : Objects.requireNonNullElse(weapon.gunId(), "").trim();
            if (!gunId.isBlank()) {
                return gunId;
            }
        }
        return "";
    }

    private static int displayAmmoCost(ZombiesAmmoBoxData ammoBox) {
        if (ammoBox == null || ammoBox.pricesByWeaponLevel().isEmpty()) {
            return 0;
        }
        int cost = Integer.MAX_VALUE;
        for (Integer value : ammoBox.pricesByWeaponLevel().values()) {
            if (value != null && value >= 0) {
                cost = Math.min(cost, value);
            }
        }
        return cost == Integer.MAX_VALUE ? 0 : cost;
    }

    private static int displayUltimateCost(ZombiesUltimateMachineData ultimateMachine) {
        if (ultimateMachine == null || ultimateMachine.levels().isEmpty()) {
            return 0;
        }
        int cost = Integer.MAX_VALUE;
        for (ZombiesUltimateMachineData.UpgradeLevelData level : ultimateMachine.levels().values()) {
            if (level != null && level.cost() >= 0) {
                cost = Math.min(cost, level.cost());
            }
        }
        return cost == Integer.MAX_VALUE ? 0 : cost;
    }

    private boolean isPowerOn() {
        return powerOnSupplier.getAsBoolean();
    }

    private record BarrierRuntimeState(
            int group,
            boolean cleared,
            long revision
    ) {
    }

    private record WeaponWallRuntimeState(
            WeaponWallOffer offer,
            long revision,
            int lastRefreshWave
    ) {
        private WeaponWallRuntimeState {
            offer = offer == null ? WeaponWallOffer.empty() : offer;
            revision = Math.max(0L, revision);
            lastRefreshWave = Math.max(0, lastRefreshWave);
        }

        private WeaponWallRuntimeState withRevision(long nextRevision) {
            return new WeaponWallRuntimeState(offer, nextRevision, lastRefreshWave);
        }
    }

    private record CandidateWeight(String gunId, double weight) {
        private CandidateWeight {
            gunId = Objects.requireNonNullElse(gunId, "").trim();
            weight = Double.isFinite(weight) ? Math.max(0.0D, weight) : 0.0D;
        }
    }

    public record WeaponWallOffer(
            String gunId,
            int weaponLevel,
            int price,
            int maxReserveAmmo,
            double levelDamageMultiplier
    ) {
        public WeaponWallOffer {
            gunId = Objects.requireNonNullElse(gunId, "").trim();
        }

        public static WeaponWallOffer empty() {
            return new WeaponWallOffer("", 0, 0, 0, 0.0D);
        }

        public boolean purchasable() {
            return !gunId.isBlank()
                    && weaponLevel > 0
                    && Double.isFinite(levelDamageMultiplier)
                    && levelDamageMultiplier > 0.0D
                    && price >= 0
                    && maxReserveAmmo >= 0;
        }
    }

    public record BarrierGroupUpdate(
            int group,
            List<String> objectIds,
            long revision
    ) {
        public BarrierGroupUpdate {
            objectIds = objectIds == null ? List.of() : List.copyOf(objectIds);
            revision = Math.max(0L, revision);
        }
    }
}
