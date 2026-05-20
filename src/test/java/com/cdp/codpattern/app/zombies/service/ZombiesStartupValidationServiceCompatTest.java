package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.ZombiesMapSnapshot;
import com.cdp.codpattern.app.zombies.runtime.ZombiesWaveRuntimeState;
import com.cdp.codpattern.app.zombies.validation.ZombiesMapValidationProfile;
import com.cdp.codpattern.app.zombies.validation.ZombiesMapValidator;
import com.cdp.codpattern.config.zombies.ZombiesRulesConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class ZombiesStartupValidationServiceCompatTest {
    private static final RoomId ROOM_ID = RoomId.of("zombies", "compat");
    private static final ZombiesRulesConfig.Defaults DEFAULTS = new ZombiesRulesConfig.Defaults();

    private ZombiesStartupValidationServiceCompatTest() {
    }

    public static void main(String[] args) throws IOException {
        validMapAndValidWavePreflightSucceeds();
        missingEndTeleportPointAllowedForMvp1();
        missingInitialSpawnFails();
        missingGroupOneZombieSpawnFails();
        missingWavesDirectoryGeneratesDefaultWaveAndPreflightSucceeds();
        missingMobsFails();
        filenameWaveConflictFails();
        waveThreeOnlyPreflightSucceedsWithMaxWaveThree();
        defaultPathConstructorUsesMvp3FullInitial();
    }

    private static void validMapAndValidWavePreflightSucceeds() throws IOException {
        withWaves("zombies-startup-valid-", wavesDirectory -> {
            writeWave(wavesDirectory, "wave_001.json", "{\"wave\":1,\"mobs\":[]}");

            ZombiesServiceResult<ZombiesStartupPreflightSnapshot> result = service(wavesDirectory)
                    .preflight(validMap());

            require(result.success(), "valid map and wave_001 should pass preflight: " + firstIssue(result));
            ZombiesStartupPreflightSnapshot snapshot = requireSnapshot(result);
            require(snapshot.valid(), "valid map and wave_001 should produce a valid snapshot");
            require(snapshot.maxWave() == 1, "valid wave_001 should set maxWave to 1");
        });
    }

    private static void missingEndTeleportPointAllowedForMvp1() throws IOException {
        withWaves("zombies-startup-missing-endtp-", wavesDirectory -> {
            writeWave(wavesDirectory, "wave_001.json", "{\"wave\":1,\"mobs\":[]}");

            ZombiesServiceResult<ZombiesStartupPreflightSnapshot> result = service(wavesDirectory)
                    .preflight(snapshot(false, List.of(initialSpawn(), zombieSpawn(1, 1.0D))));

            require(result.success(), "missing endtp should pass preflight under MVP1: " + firstIssue(result));
            ZombiesStartupPreflightSnapshot snapshot = requireSnapshot(result);
            require(snapshot.valid(), "missing endtp should still produce a valid MVP1 snapshot");
            requireNoIssue(result, "map.missing_endtp");
        });
    }

    private static void missingInitialSpawnFails() throws IOException {
        withWaves("zombies-startup-missing-initial-", wavesDirectory -> {
            writeWave(wavesDirectory, "wave_001.json", "{\"wave\":1,\"mobs\":[]}");

            ZombiesServiceResult<ZombiesStartupPreflightSnapshot> result = service(wavesDirectory)
                    .preflight(snapshot(true, List.of(zombieSpawn(1, 1.0D))));

            require(!result.success(), "missing INITIAL spawn should fail preflight");
            requireIssue(result, "map.missing_initial_spawn");
        });
    }

    private static void missingGroupOneZombieSpawnFails() throws IOException {
        withWaves("zombies-startup-missing-group-one-", wavesDirectory -> {
            writeWave(wavesDirectory, "wave_001.json", "{\"wave\":1,\"mobs\":[]}");

            ZombiesServiceResult<ZombiesStartupPreflightSnapshot> result = service(wavesDirectory)
                    .preflight(snapshot(true, List.of(initialSpawn(), zombieSpawn(2, 1.0D))));

            require(!result.success(), "missing group=1 zombie spawn should fail preflight");
            requireIssue(result, "map.missing_group_1_zombie_spawn");
        });
    }

    private static void missingWavesDirectoryGeneratesDefaultWaveAndPreflightSucceeds() throws IOException {
        withWaves("zombies-startup-default-wave-", wavesDirectory -> {
            ZombiesServiceResult<ZombiesStartupPreflightSnapshot> result = service(wavesDirectory)
                    .preflight(validMap());

            require(result.success(), "missing waves directory should generate default wave and pass preflight: "
                    + firstIssue(result));
            ZombiesStartupPreflightSnapshot snapshot = requireSnapshot(result);
            require(snapshot.valid(), "generated default wave should produce a valid snapshot");
            require(snapshot.maxWave() == 1, "generated default wave should set maxWave to 1");
            require(Files.isRegularFile(wavesDirectory.resolve("wave_001.json")),
                    "preflight should create default wave_001.json");
            require(snapshot.waveLoadResult().getWaves().size() == 1,
                    "preflight should load generated default wave");
            require(snapshot.waveLoadResult().getWaves().get(0).totalMobCount() == 23,
                    "generated default wave should be usable by runtime budget initialization");
            ZombiesWaveRuntimeState waveState = new ZombiesWaveRuntimeState();
            new ZombiesWaveDirector(snapshot.waveLoadResult().getWaves()).enterTargetWave(waveState);
            require(waveState.maxWave() == 1, "generated default wave should configure director maxWave");
            require(waveState.currentWave() == 1, "generated default wave should enter wave 1");
            require(waveState.remainingBudget() == 23, "generated default wave should initialize runtime budget");
        });
    }

    private static void missingMobsFails() throws IOException {
        withWaves("zombies-startup-missing-mobs-", wavesDirectory -> {
            writeWave(wavesDirectory, "wave_001.json", "{\"wave\":1}");

            ZombiesServiceResult<ZombiesStartupPreflightSnapshot> result = service(wavesDirectory)
                    .preflight(validMap());

            require(!result.success(), "wave missing mobs should fail preflight");
            requireIssue(result, "rules.missing_mobs");
        });
    }

    private static void filenameWaveConflictFails() throws IOException {
        withWaves("zombies-startup-wave-conflict-", wavesDirectory -> {
            writeWave(wavesDirectory, "wave_001.json", "{\"wave\":2,\"mobs\":[]}");

            ZombiesServiceResult<ZombiesStartupPreflightSnapshot> result = service(wavesDirectory)
                    .preflight(validMap());

            require(!result.success(), "filename/wave conflict should fail preflight");
            requireIssue(result, "rules.wave_conflict");
        });
    }

    private static void waveThreeOnlyPreflightSucceedsWithMaxWaveThree() throws IOException {
        withWaves("zombies-startup-wave-three-", wavesDirectory -> {
            writeWave(wavesDirectory, "wave_003.json", "{\"wave\":3,\"mobs\":[]}");

            ZombiesServiceResult<ZombiesStartupPreflightSnapshot> result = service(wavesDirectory)
                    .preflight(validMap());

            require(result.success(), "single wave_003 should pass preflight: " + firstIssue(result));
            ZombiesStartupPreflightSnapshot snapshot = requireSnapshot(result);
            require(snapshot.valid(), "single wave_003 should produce a valid snapshot");
            require(snapshot.maxWave() == 3, "single wave_003 should set maxWave to 3");
        });
    }

    private static void defaultPathConstructorUsesMvp3FullInitial() throws IOException {
        withWaves("zombies-startup-default-mvp3-", wavesDirectory -> {
            writeWave(wavesDirectory, "wave_001.json", "{\"wave\":1,\"mobs\":[]}");

            ZombiesServiceResult<ZombiesStartupPreflightSnapshot> result =
                    new ZombiesStartupValidationService(wavesDirectory)
                            .preflight(validMap());

            require(!result.success(), "default Path constructor should fail MVP1-only map under MVP3");
            ZombiesStartupPreflightSnapshot snapshot = requireSnapshot(result);
            require(ZombiesMapValidationProfile.MVP3_FULL_INITIAL_KEY.equals(snapshot.mapReport().profileKey()),
                    "default Path constructor should use MVP3_FULL_INITIAL, got "
                            + snapshot.mapReport().profileKey());
            requireIssue(result, "map.missing_weapon_wall");
            requireIssue(result, "map.missing_ammo_box");
            requireIssue(result, "map.missing_armor_station");
            requireIssue(result, "map.missing_barrier");
            requireIssue(result, "map.missing_soda_machine");
            requireIssue(result, "map.missing_ultimate_machine");
        });
    }

    private static ZombiesStartupValidationService service(Path wavesDirectory) {
        return new ZombiesStartupValidationService(
                new ZombiesMapValidator(ZombiesMapValidationProfile.MVP1_MINIMAL),
                new ZombiesWaveConfigRepository(
                        wavesDirectory,
                        DEFAULTS,
                        new ZombiesWaveValidator()));
    }

    private static ZombiesMapSnapshot validMap() {
        return snapshot(true, List.of(initialSpawn(), zombieSpawn(1, 1.0D)));
    }

    private static ZombiesMapSnapshot snapshot(
            boolean hasEndTeleportPoint,
            List<ZombiesMapSnapshot.SpawnSnapshot> spawns
    ) {
        return ZombiesMapSnapshot.of(ROOM_ID, ROOM_ID.mapName(), hasEndTeleportPoint, spawns, List.of());
    }

    private static ZombiesMapSnapshot.SpawnSnapshot initialSpawn() {
        return new ZombiesMapSnapshot.SpawnSnapshot("initial-1", "spawn", "INITIAL", 0, 0.0D, false);
    }

    private static ZombiesMapSnapshot.SpawnSnapshot zombieSpawn(int group, double weight) {
        return new ZombiesMapSnapshot.SpawnSnapshot(
                "zombie-" + group,
                "zombieSpawn",
                "",
                group,
                weight,
                true);
    }

    private static void writeWave(Path wavesDirectory, String fileName, String json) throws IOException {
        Files.createDirectories(wavesDirectory);
        Files.writeString(wavesDirectory.resolve(fileName), json);
    }

    private static void withWaves(String prefix, ThrowingConsumer<Path> test) throws IOException {
        Path tempRoot = Files.createTempDirectory(prefix);
        try {
            test.accept(tempRoot.resolve("waves"));
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    private static ZombiesStartupPreflightSnapshot requireSnapshot(
            ZombiesServiceResult<ZombiesStartupPreflightSnapshot> result
    ) {
        require(result.value().isPresent(), "preflight result should carry a snapshot");
        return result.value().get();
    }

    private static void requireIssue(
            ZombiesServiceResult<ZombiesStartupPreflightSnapshot> result,
            String code
    ) {
        ZombiesStartupPreflightSnapshot snapshot = requireSnapshot(result);
        require(snapshot.issues().stream().anyMatch(issue -> code.equals(issue.code().key())),
                "expected issue " + code + ", got " + issueCodes(snapshot));
    }

    private static void requireNoIssue(
            ZombiesServiceResult<ZombiesStartupPreflightSnapshot> result,
            String code
    ) {
        ZombiesStartupPreflightSnapshot snapshot = requireSnapshot(result);
        require(snapshot.issues().stream().noneMatch(issue -> code.equals(issue.code().key())),
                "expected no issue " + code + ", got " + issueCodes(snapshot));
    }

    private static String issueCodes(ZombiesStartupPreflightSnapshot snapshot) {
        return snapshot.issues().stream()
                .map(issue -> issue.code().key())
                .toList()
                .toString();
    }

    private static String firstIssue(ZombiesServiceResult<ZombiesStartupPreflightSnapshot> result) {
        if (result.value().isEmpty()) {
            return "no snapshot";
        }
        ZombiesStartupPreflightSnapshot snapshot = result.value().get();
        return snapshot.firstError()
                .map(issue -> issue.code().key() + " " + issue.message())
                .orElse("no issues");
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> sortedPaths = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path path : sortedPaths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws IOException;
    }
}
