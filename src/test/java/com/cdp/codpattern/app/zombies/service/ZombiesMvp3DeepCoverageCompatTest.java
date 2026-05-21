package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.object.ZombiesUltimateMachineData;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffState;
import com.cdp.codpattern.app.zombies.model.ZombiesBuffType;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;
import com.cdp.codpattern.config.zombies.ZombiesRulesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ZombiesMvp3DeepCoverageCompatTest {
    private ZombiesMvp3DeepCoverageCompatTest() {
    }

    public static void main(String[] args) throws InterruptedException {
        requiresPowerAndRepeatPurchasesRemainNonDestructive();
        concurrentBuffRepeatPurchasesSpendOnlyOnce();
        doubleAmmoClearIsIdempotentAndFloorsBothWeapons();
        ultimateMaxAndInvalidCurrentWeaponFailuresDoNotSpendOrMutate();
        ultimateCommitFailureDoesNotSpendMutateOrRevise();
        pendingEndTeleportRewritesStaleWorkAndConsumesOnce();
    }

    private static void requiresPowerAndRepeatPurchasesRemainNonDestructive() {
        RecordingPowerSwitchSink sink = new RecordingPowerSwitchSink();
        Services services = services(sink);
        UUID playerId = playerId(1);
        Map<String, ZombiesUltimateMachineData.UpgradeLevelData> levels = Map.of(
                "1", new ZombiesUltimateMachineData.UpgradeLevelData(250, 1.60D));
        services.economy.addPoints(playerId, 2_000.0D);
        services.players.getOrCreate(playerId).setPrimaryWeapon(
                new ZombiesWeaponInstanceState("tacz:m4a1", 2, 0, 1.25D, 77, 180));

        ZombiesServiceResult<ZombiesBuffService.BuffPurchaseResult> lockedBuff =
                services.buffs.purchaseBuff(playerId, ZombiesBuffType.DOUBLE_AMMO, 150.0D, true);

        requireFailure(lockedBuff, ZombiesErrorCode.POWER_REQUIRES_POWER,
                "requiresPower buff should fail while power is off");
        requirePoints(services.players, playerId, 2_000.0D,
                "power-gated buff failure should not deduct");
        require(!services.players.getOrCreate(playerId).hasBuff(ZombiesBuffType.DOUBLE_AMMO),
                "power-gated buff failure should not grant buff");

        ZombiesServiceResult<ZombiesUltimateMachineService.WeaponUpgradeResult> lockedUltimate =
                services.ultimate.upgradePrimaryWeapon(playerId, 1, levels, true);

        requireFailure(lockedUltimate, ZombiesErrorCode.POWER_REQUIRES_POWER,
                "requiresPower ultimate should fail while power is off");
        requirePoints(services.players, playerId, 2_000.0D,
                "power-gated ultimate failure should not deduct");
        require(primary(services.players, playerId).upgradeLevel() == 0,
                "power-gated ultimate failure should not alter weapon upgrade");

        sink.failNext = true;
        requireSuccess(services.power.turnOn(playerId, 300.0D),
                "power purchase should succeed even if block-state sink throws");
        require(services.power.isPowerOn(), "power purchase should flip authoritative room power state");
        require(sink.poweredStates.equals(List.of(true)),
                "power purchase should attempt exactly one powered sync");
        requirePoints(services.players, playerId, 1_700.0D, "power purchase should deduct once");

        requireFailure(services.power.turnOn(playerId, 300.0D), ZombiesErrorCode.POWER_ALREADY_ON,
                "repeat power purchase should fail after power is on");
        requirePoints(services.players, playerId, 1_700.0D,
                "repeat power purchase should not deduct");
        require(sink.poweredStates.equals(List.of(true)),
                "repeat power purchase should not emit another powered sync");

        requireSuccess(services.buffs.purchaseBuff(playerId, ZombiesBuffType.DOUBLE_AMMO, 150.0D, true),
                "buff purchase should succeed after power is on");
        requirePoints(services.players, playerId, 1_550.0D,
                "successful buff purchase should deduct configured cost");
        services.players.getOrCreate(playerId).addBuff(new ZombiesBuffState(ZombiesBuffType.DOUBLE_AMMO, 3.0D));

        ZombiesServiceResult<ZombiesBuffService.BuffPurchaseResult> repeatBuff =
                services.buffs.purchaseBuff(playerId, ZombiesBuffType.DOUBLE_AMMO, 150.0D, true);

        requireSuccess(repeatBuff, "repeat buff purchase should be a non-destructive no-op");
        require(repeatBuff.value().orElseThrow().alreadyOwned(),
                "repeat buff purchase should report already owned");
        requireClose(repeatBuff.value().orElseThrow().buff().multiplier(), 3.0D,
                "repeat buff purchase should keep existing buff state");
        requirePoints(services.players, playerId, 1_550.0D,
                "repeat buff purchase should not deduct");
    }

    private static void concurrentBuffRepeatPurchasesSpendOnlyOnce() throws InterruptedException {
        Services services = services();
        UUID playerId = playerId(20);
        services.economy.addPoints(playerId, 2_000.0D);

        int threads = 16;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger alreadyOwned = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> workers = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            Thread worker = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    ZombiesServiceResult<ZombiesBuffService.BuffPurchaseResult> result =
                            services.buffs.purchaseBuff(playerId, ZombiesBuffType.DOUBLE_HEALTH, 125.0D, false);
                    if (result.success()) {
                        successes.incrementAndGet();
                        if (result.value().orElseThrow().alreadyOwned()) {
                            alreadyOwned.incrementAndGet();
                        }
                    } else {
                        failure.compareAndSet(null, new AssertionError("buff purchase failed: " + result.code()));
                    }
                } catch (Throwable throwable) {
                    failure.compareAndSet(null, throwable);
                }
            }, "zombies-buff-repeat-" + index);
            workers.add(worker);
            worker.start();
        }

        ready.await();
        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }
        if (failure.get() != null) {
            throw new AssertionError("concurrent buff purchase worker failed", failure.get());
        }

        require(successes.get() == threads, "every concurrent repeat buff purchase should succeed");
        require(alreadyOwned.get() == threads - 1,
                "all concurrent repeat buff purchases after the first should report already owned");
        requirePoints(services.players, playerId, 1_875.0D,
                "concurrent repeat buff purchase should deduct one configured cost");
    }

    private static void doubleAmmoClearIsIdempotentAndFloorsBothWeapons() {
        Services services = services();
        UUID playerId = playerId(2);
        ZombiesPlayerRuntimeState state = services.players.getOrCreate(playerId);
        state.setStarterWeapon(new ZombiesWeaponInstanceState("tacz:glock_17", 1, 0, 1.0D, 1, 80));
        state.setPrimaryWeapon(new ZombiesWeaponInstanceState("tacz:ak47", 2, 1, 1.75D, 99, 210));
        state.addBuff(ZombiesBuffState.defaultFor(ZombiesBuffType.DOUBLE_AMMO));
        state.addBuff(ZombiesBuffState.defaultFor(ZombiesBuffType.HEADSHOT_DAMAGE));

        ZombiesServiceResult<ZombiesBuffService.BuffClearResult> firstClear =
                services.buffs.clearBuffsForDeathOrRevive(playerId);

        requireSuccess(firstClear, "first death/revive buff clear should succeed");
        require(firstClear.value().orElseThrow().clearedBuffs() == 2,
                "first clear should report all removed buffs");
        require(firstClear.value().orElseThrow().halvedReserveAmmo(),
                "first clear should report double_ammo reserve trimming");
        require(starter(services.players, playerId).reserveAmmo() == 0,
                "double_ammo clear should floor starter reserve / 2");
        require(primary(services.players, playerId).reserveAmmo() == 49,
                "double_ammo clear should floor primary reserve / 2");
        require(state.buffs().isEmpty(), "first clear should remove every buff");

        ZombiesServiceResult<ZombiesBuffService.BuffClearResult> secondClear =
                services.buffs.clearBuffsForDeathOrRevive(playerId);

        requireSuccess(secondClear, "second death/revive buff clear should remain safe");
        require(secondClear.value().orElseThrow().clearedBuffs() == 0,
                "second clear should report no removed buffs");
        require(!secondClear.value().orElseThrow().halvedReserveAmmo(),
                "second clear should not trim ammo again");
        require(starter(services.players, playerId).reserveAmmo() == 0,
                "second clear should preserve already-trimmed starter reserve");
        require(primary(services.players, playerId).reserveAmmo() == 49,
                "second clear should preserve already-trimmed primary reserve");
    }

    private static void ultimateMaxAndInvalidCurrentWeaponFailuresDoNotSpendOrMutate() {
        Services services = services();
        UUID powerBuyer = playerId(30);
        services.economy.addPoints(powerBuyer, 500.0D);
        requireSuccess(services.power.turnOn(powerBuyer, 100.0D), "setup power purchase should succeed");

        UUID maxedPlayer = playerId(31);
        services.economy.addPoints(maxedPlayer, 1_000.0D);
        ZombiesWeaponInstanceState maxedWeapon =
                new ZombiesWeaponInstanceState("tacz:raygun", 3, 2, 2.50D, 60, 120);
        services.players.getOrCreate(maxedPlayer).setPrimaryWeapon(maxedWeapon);

        ZombiesServiceResult<ZombiesUltimateMachineService.WeaponUpgradeResult> maxed =
                services.ultimate.upgradePrimaryWeapon(
                        maxedPlayer,
                        2,
                        Map.of("3", new ZombiesUltimateMachineData.UpgradeLevelData(900, 3.0D)),
                        true);

        requireFailure(maxed, ZombiesErrorCode.WEAPON_MAX_UPGRADE,
                "maxed weapon should fail before reading next level config");
        requirePoints(services.players, maxedPlayer, 1_000.0D,
                "maxed weapon failure should not deduct");
        require(primary(services.players, maxedPlayer).equals(maxedWeapon),
                "maxed weapon failure should not mutate weapon");

        UUID blankGunPlayer = playerId(32);
        services.economy.addPoints(blankGunPlayer, 1_000.0D);
        ZombiesWeaponInstanceState blankGunWeapon =
                new ZombiesWeaponInstanceState(" ", 2, 0, 1.20D, 50, 100);
        services.players.getOrCreate(blankGunPlayer).setPrimaryWeapon(blankGunWeapon);

        ZombiesServiceResult<ZombiesUltimateMachineService.WeaponUpgradeResult> blankGun =
                services.ultimate.upgradePrimaryWeapon(
                        blankGunPlayer,
                        1,
                        Map.of("1", new ZombiesUltimateMachineData.UpgradeLevelData(300, 1.50D)),
                        true);

        requireFailure(blankGun, ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON,
                "blank current gun id should fail as invalid current weapon");
        requirePoints(services.players, blankGunPlayer, 1_000.0D,
                "blank gun failure should not deduct");
        require(primary(services.players, blankGunPlayer).equals(blankGunWeapon),
                "blank gun failure should not mutate weapon");

        UUID zeroLevelPlayer = playerId(33);
        services.economy.addPoints(zeroLevelPlayer, 1_000.0D);
        ZombiesWeaponInstanceState zeroLevelWeapon =
                new ZombiesWeaponInstanceState("tacz:m4a1", 0, 0, 1.20D, 50, 100);
        services.players.getOrCreate(zeroLevelPlayer).setPrimaryWeapon(zeroLevelWeapon);

        ZombiesServiceResult<ZombiesUltimateMachineService.WeaponUpgradeResult> zeroLevel =
                services.ultimate.upgradePrimaryWeapon(
                        zeroLevelPlayer,
                        1,
                        Map.of("1", new ZombiesUltimateMachineData.UpgradeLevelData(300, 1.50D)),
                        true);

        requireFailure(zeroLevel, ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON,
                "zero current weapon level should fail as invalid current weapon");
        requirePoints(services.players, zeroLevelPlayer, 1_000.0D,
                "zero-level weapon failure should not deduct");
        require(primary(services.players, zeroLevelPlayer).equals(zeroLevelWeapon),
                "zero-level weapon failure should not mutate weapon");
    }

    private static void ultimateCommitFailureDoesNotSpendMutateOrRevise() {
        Services services = services();
        ZombiesRulesConfig rules = ultimateRules(1, Map.of(
                "1", new ZombiesRulesConfig.UpgradeLevel(300, 1.75D)));
        ZombiesObjectStateStore store = new ZombiesObjectStateStore(() -> false, null, () -> rules);
        ZombiesUltimateMachineData machine = new ZombiesUltimateMachineData(
                "ultimate-commit-fail",
                9,
                Map.of("1", new ZombiesUltimateMachineData.UpgradeLevelData(999, 9.0D)),
                false,
                dimension(),
                new BlockPos(8, 64, 8),
                Optional.empty());
        store.resetObjects(List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(machine), 1, 1);
        long initialRevision = ultimateRevision(store, machine);

        UUID playerId = playerId(34);
        ZombiesWeaponInstanceState originalWeapon =
                new ZombiesWeaponInstanceState("tacz:m4a1", 2, 0, 1.25D, 70, 210);
        services.economy.addPoints(playerId, 1_000.0D);
        services.players.getOrCreate(playerId).setPrimaryWeapon(originalWeapon);

        ZombiesObjectInteractionService interactionService = interactionService(services, store, List.of(machine), rules);
        ZombiesServiceResult<ZombiesUltimateMachineService.WeaponUpgradeResult> result =
                interactionService.useUltimateMachine(
                        playerId,
                        machine,
                        (currentWeapon, upgradedWeapon) ->
                                ZombiesServiceResult.failure(ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON));

        requireFailure(result, ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON,
                "ultimate item tag commit failure should fail upgrade");
        requirePoints(services.players, playerId, 1_000.0D,
                "ultimate item tag commit failure should not deduct");
        require(primary(services.players, playerId).equals(originalWeapon),
                "ultimate item tag commit failure should preserve runtime primary");
        require(ultimateRevision(store, machine) == initialRevision,
                "ultimate item tag commit failure should not advance object revision");
    }

    private static void pendingEndTeleportRewritesStaleWorkAndConsumesOnce() {
        ZombiesPostGameTeleportService service = new ZombiesPostGameTeleportService();
        UUID offline = playerId(4);
        RoomId firstRoom = RoomId.of(BuiltInGameModes.ZOMBIES, "deep-pending-a");
        RoomId secondRoom = RoomId.of(BuiltInGameModes.ZOMBIES, "deep-pending-b");
        ZombiesPostGameTeleportService.TeleportTarget firstTarget =
                new ZombiesPostGameTeleportService.TeleportTarget(
                        "minecraft:overworld", 1, 65, 2, 90.0F, 0.0F);
        ZombiesPostGameTeleportService.TeleportTarget secondTarget =
                new ZombiesPostGameTeleportService.TeleportTarget(
                        "minecraft:the_nether", -5, 70, 9, 180.0F, 12.5F);

        ZombiesPostGameTeleportService.CleanupPendingSummary first = service.recordPostGameCleanup(
                firstRoom,
                List.of(offline),
                List.of(),
                Optional.of(firstTarget),
                "victory",
                1L);

        require(first.pendingWritten() == 1, "first cleanup should write pending endtp");
        require(service.pendingCount() == 1, "first cleanup should leave one pending record");

        ZombiesPostGameTeleportService.CleanupPendingSummary second = service.recordPostGameCleanup(
                secondRoom,
                List.of(offline),
                List.of(),
                Optional.of(secondTarget),
                "server_stop",
                2L);

        require(second.pendingWritten() == 1, "second cleanup should rewrite pending endtp");
        require(service.pendingCount() == 1, "rewriting pending work should not duplicate player entries");
        ZombiesPostGameTeleportService.PendingEndTeleport pending = service.peekPending(offline).orElseThrow();
        require(pending.roomId().equals(secondRoom), "pending work should retain latest cleanup room");
        require(pending.endTeleport().orElseThrow().equals(secondTarget),
                "pending work should retain latest cleanup endtp");
        require("server_stop".equals(pending.reason()), "pending work should retain latest cleanup reason");
        require(pending.cleanupRevision() == 2L, "pending work should retain latest cleanup revision");

        ZombiesPostGameTeleportService.PendingEndTeleport consumed = service.consumePending(offline).orElseThrow();
        require(consumed.roomId().equals(secondRoom), "consumed pending work should be latest cleanup work");
        require(service.consumePending(offline).isEmpty(), "pending endtp should be one-shot after reconnect");
        require(service.pendingCount() == 0, "pending store should be empty after one-shot consume");
    }

    private static Services services() {
        return services(ZombiesPowerService.PowerSwitchStateSink.noop());
    }

    private static Services services(ZombiesPowerService.PowerSwitchStateSink sink) {
        ZombiesPlayerStateService players = new ZombiesPlayerStateService();
        ZombiesEconomyService economy = new ZombiesEconomyService(players);
        ZombiesPowerService power = new ZombiesPowerService(economy, sink);
        return new Services(
                players,
                economy,
                power,
                new ZombiesBuffService(economy, power),
                new ZombiesUltimateMachineService(economy, power));
    }

    private static ZombiesObjectInteractionService interactionService(
            Services services,
            ZombiesObjectStateStore store,
            List<ZombiesUltimateMachineData> ultimateMachines,
            ZombiesRulesConfig rules
    ) {
        RoomId roomId = RoomId.of(BuiltInGameModes.ZOMBIES, "mvp3-deep");
        return new ZombiesObjectInteractionService(
                roomId,
                List::of,
                List::of,
                List::of,
                List::of,
                Optional::empty,
                List::of,
                () -> ultimateMachines,
                new ZombiesBarrierService(
                        roomId,
                        List::of,
                        services.economy,
                        store,
                        new ZombiesActiveSpawnGroupService(),
                        ignored -> true,
                        () -> com.cdp.codpattern.app.zombies.model.ZombiesGamePhase.WAVE_ACTIVE),
                new ZombiesWeaponInstanceService(services.economy),
                new ZombiesAmmoBoxService(services.economy),
                new ZombiesArmorService(services.economy),
                services.power,
                services.buffs,
                services.ultimate,
                store,
                null,
                () -> rules);
    }

    private static ZombiesRulesConfig ultimateRules(
            int maxUpgradeLevel,
            Map<String, ZombiesRulesConfig.UpgradeLevel> levels
    ) {
        ZombiesRulesConfig config = new ZombiesRulesConfig();
        ZombiesRulesConfig.UltimateMachine ultimate = new ZombiesRulesConfig.UltimateMachine();
        ultimate.setMaxUpgradeLevel(maxUpgradeLevel);
        ultimate.setLevels(levels);
        config.setUltimateMachine(ultimate);
        config.normalize();
        return config;
    }

    private static long ultimateRevision(ZombiesObjectStateStore store, ZombiesUltimateMachineData machine) {
        return store.objectStates(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        List.of(),
                        List.of(machine))
                .stream()
                .filter(state -> "ultimate-commit-fail".equals(state.objectKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing ultimate object state"))
                .revision();
    }

    private static ZombiesWeaponInstanceState primary(ZombiesPlayerStateService players, UUID playerId) {
        return players.get(playerId)
                .flatMap(ZombiesPlayerRuntimeState::primaryWeapon)
                .orElseThrow(() -> new AssertionError("expected primary weapon"));
    }

    private static ZombiesWeaponInstanceState starter(ZombiesPlayerStateService players, UUID playerId) {
        return players.get(playerId)
                .flatMap(ZombiesPlayerRuntimeState::starterWeapon)
                .orElseThrow(() -> new AssertionError("expected starter weapon"));
    }

    private static void requirePoints(ZombiesPlayerStateService players, UUID playerId, double expected, String message) {
        requireClose(players.get(playerId).orElseThrow().points(), expected, message + ": balance");
    }

    private static void requireSuccess(ZombiesServiceResult<?> result, String message) {
        require(result.success(), message + ": " + result.code());
    }

    private static void requireFailure(ZombiesServiceResult<?> result, ZombiesErrorCode expectedCode, String message) {
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

    private record Services(
            ZombiesPlayerStateService players,
            ZombiesEconomyService economy,
            ZombiesPowerService power,
            ZombiesBuffService buffs,
            ZombiesUltimateMachineService ultimate
    ) {
    }

    private static final class RecordingPowerSwitchSink implements ZombiesPowerService.PowerSwitchStateSink {
        private final List<Boolean> poweredStates = new ArrayList<>();
        private boolean failNext;

        @Override
        public void setPowered(boolean powered) {
            poweredStates.add(powered);
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("expected test sink failure");
            }
        }
    }
}
