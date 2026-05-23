package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.zombies.model.ZombiesWaveDefinition;
import com.cdp.codpattern.app.zombies.model.ZombiesWaveMobEntry;
import com.cdp.codpattern.config.zombies.ZombiesRulesConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class ZombiesWaveConfigRepositoryCompatTest {
    private static final ZombiesRulesConfig.Defaults DEFAULTS = new ZombiesRulesConfig.Defaults();

    private ZombiesWaveConfigRepositoryCompatTest() {
    }

    public static void main(String[] args) throws IOException {
        missingDirectoryGeneratesDefaultWave();
        existingEmptyDirectoryGeneratesDefaultWave();
        nonWaveFilesGenerateDefaultWave();
        existingValidWaveIsNotOverwritten();
        oversizedWaveFileNumberIsReportedAsInvalid();
    }

    private static void missingDirectoryGeneratesDefaultWave() throws IOException {
        Path tempRoot = Files.createTempDirectory("zombies-wave-repository-missing-");
        try {
            Path wavesDirectory = tempRoot.resolve("waves");

            ZombiesWaveConfigRepository.LoadResult result = new ZombiesWaveConfigRepository(wavesDirectory, DEFAULTS).load();

            requireDefaultWaveLoaded(wavesDirectory, result, "missing waves directory");
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    private static void existingEmptyDirectoryGeneratesDefaultWave() throws IOException {
        Path tempRoot = Files.createTempDirectory("zombies-wave-repository-empty-");
        try {
            Path wavesDirectory = tempRoot.resolve("waves");
            Files.createDirectories(wavesDirectory);

            ZombiesWaveConfigRepository.LoadResult result = new ZombiesWaveConfigRepository(wavesDirectory, DEFAULTS).load();

            requireDefaultWaveLoaded(wavesDirectory, result, "existing empty waves directory");
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    private static void nonWaveFilesGenerateDefaultWave() throws IOException {
        Path tempRoot = Files.createTempDirectory("zombies-wave-repository-non-wave-");
        try {
            Path wavesDirectory = tempRoot.resolve("waves");
            Files.createDirectories(wavesDirectory);
            Files.writeString(wavesDirectory.resolve("readme.txt"), "wave files must be named wave_*.json");

            ZombiesWaveConfigRepository.LoadResult result = new ZombiesWaveConfigRepository(wavesDirectory, DEFAULTS).load();

            requireDefaultWaveLoaded(wavesDirectory, result, "directory with only non-wave files");
            require(Files.isRegularFile(wavesDirectory.resolve("readme.txt")), "non-wave helper file should be preserved");
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    private static void existingValidWaveIsNotOverwritten() throws IOException {
        Path tempRoot = Files.createTempDirectory("zombies-wave-repository-existing-");
        try {
            Path wavesDirectory = tempRoot.resolve("waves");
            Files.createDirectories(wavesDirectory);
            Path existingWave = wavesDirectory.resolve("wave_002.json");
            String existingJson = "{"
                    + "\"wave\":2,"
                    + "\"description\":\"pre-existing valid wave\","
                    + "\"mobs\":[{\"entity\":\"minecraft:zombie\",\"count\":1}]"
                    + "}";
            Files.writeString(existingWave, existingJson);

            ZombiesWaveConfigRepository.LoadResult result = new ZombiesWaveConfigRepository(wavesDirectory, DEFAULTS).load();

            require(!Files.exists(wavesDirectory.resolve("wave_001.json")),
                    "existing wave files should prevent generating wave_001.json");
            require(existingJson.equals(Files.readString(existingWave)), "existing valid wave should not be overwritten");
            require(result.isValid(), "existing valid wave should load without issues: " + firstIssue(result));
            require(result.getMaxWave() == 2, "existing valid wave should keep maxWave at 2");
            require(result.getWaves().size() == 1, "existing valid wave should be the only loaded wave");
            ZombiesWaveDefinition wave = result.getWaves().get(0);
            require(wave.getWave() == 2, "loaded wave should come from the existing file");
            require("minecraft:zombie".equals(wave.getMobs().get(0).getEntity()),
                    "loaded wave should retain the existing mob entry");
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    private static void oversizedWaveFileNumberIsReportedAsInvalid() throws IOException {
        Path tempRoot = Files.createTempDirectory("zombies-wave-repository-oversized-");
        try {
            Path wavesDirectory = tempRoot.resolve("waves");
            Files.createDirectories(wavesDirectory);
            Path oversizedWave = wavesDirectory.resolve("wave_999999999999999999999.json");
            Files.writeString(oversizedWave, "{\"mobs\":[]}");

            ZombiesWaveConfigRepository.LoadResult result = new ZombiesWaveConfigRepository(wavesDirectory, DEFAULTS).load();

            require(!result.isValid(), "oversized wave file number should be reported invalid");
            require(result.getIssues().stream().anyMatch(issue -> ZombiesWaveValidator.INVALID_WAVE.equals(issue.getCode())
                            || "rules.wave_invalid".equals(issue.getCode())),
                    "oversized wave file number should produce a wave invalid issue: " + firstIssue(result));
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    private static void requireDefaultWaveLoaded(
            Path wavesDirectory,
            ZombiesWaveConfigRepository.LoadResult result,
            String context
    ) throws IOException {
        Path generatedWave = wavesDirectory.resolve("wave_001.json");
        require(Files.isRegularFile(generatedWave), context + " should create wave_001.json");
        String generatedJson = Files.readString(generatedWave);
        require(generatedJson.contains("\"description\""), context + " default wave should contain description");
        require(generatedJson.contains("\"fastestSpawnIntervalTicks\": 20"),
                context + " default wave should contain fastest spawn interval");
        require(generatedJson.contains("\"slowestSpawnIntervalTicks\": 50"),
                context + " default wave should contain slowest spawn interval");
        require(!generatedJson.contains("\"spawnIntervalTicks\""),
                context + " default wave should use random interval bounds instead of legacy fixed interval");
        require(generatedJson.contains("\"minecraft:zombie\""), context + " default wave should contain zombie");
        require(generatedJson.contains("\"minecraft:husk\""), context + " default wave should contain husk");
        require(generatedJson.contains("\"minecraft:wither_skeleton\""),
                context + " default wave should contain wither skeleton");
        require(generatedJson.contains("\"minecraft:creeper\""), context + " default wave should contain creeper");
        require(generatedJson.contains("\"minecraft:wolf\""), context + " default wave should contain wolf");
        require(generatedJson.contains("\"minecraft:silverfish\""), context + " default wave should contain silverfish");
        require(generatedJson.contains("\"minecraft:spider\""), context + " default wave should contain spider");
        require(generatedJson.contains("\"minecraft:vindicator\""), context + " default wave should contain vindicator");
        require(generatedJson.contains("\"minecraft:vex\""), context + " default wave should contain vex");
        require(generatedJson.contains("\"minecraft:warden\""), context + " default wave should contain warden");
        require(generatedJson.contains("\"healthMultiplier\": 1.0"),
                context + " default wave should show optional per-mob health multiplier");
        require(generatedJson.contains("\"damageMultiplier\": 1.0"),
                context + " default wave should show optional per-mob damage multiplier");
        require(generatedJson.contains("\"speedMultiplier\": 1.0"),
                context + " default wave should show optional per-mob speed multiplier");
        require(result.isValid(), context + " default wave should load without issues: " + firstIssue(result));
        require(result.getMaxWave() == 1, context + " default wave should set maxWave to 1");
        require(result.getWaves().size() == 1, context + " default wave should load exactly one wave");
        ZombiesWaveDefinition wave = result.getWaves().get(0);
        require(wave.getWave() == 1, context + " default wave should be wave 1");
        require(wave.getFastestSpawnIntervalTicks() == 20,
                context + " default wave should load fastest interval 20");
        require(wave.getSlowestSpawnIntervalTicks() == 50,
                context + " default wave should load slowest interval 50");
        requireReward(wave, "minecraft:zombie", 30, 9, context);
        requireReward(wave, "minecraft:husk", 45, 15, context);
        requireReward(wave, "minecraft:wither_skeleton", 75, 24, context);
        requireReward(wave, "minecraft:creeper", 60, 18, context);
        requireReward(wave, "minecraft:wolf", 36, 12, context);
        requireReward(wave, "minecraft:silverfish", 15, 6, context);
        requireReward(wave, "minecraft:spider", 42, 14, context);
        requireReward(wave, "minecraft:vindicator", 70, 22, context);
        requireReward(wave, "minecraft:vex", 50, 16, context);
        requireReward(wave, "minecraft:warden", 500, 160, context);
        requireMultipliers(wave, "minecraft:zombie", 1.0D, 1.0D, 1.0D, context);
        requireMultipliers(wave, "minecraft:spider", 1.0D, 1.0D, 1.0D, context);
        requireMultipliers(wave, "minecraft:vindicator", 1.0D, 1.0D, 1.0D, context);
        requireMultipliers(wave, "minecraft:vex", 1.0D, 1.0D, 1.0D, context);
        requireMultipliers(wave, "minecraft:warden", 1.0D, 1.0D, 1.0D, context);
    }

    private static void requireReward(
            ZombiesWaveDefinition wave,
            String entity,
            int killPoints,
            int assistPoints,
            String context
    ) {
        for (ZombiesWaveMobEntry mob : wave.getMobs()) {
            if (entity.equals(mob.getEntity())) {
                require(mob.getKillPoints() == killPoints,
                        context + " default reward for " + entity + " should set killPoints=" + killPoints);
                require(mob.getAssistPoints() == assistPoints,
                        context + " default reward for " + entity + " should set assistPoints=" + assistPoints);
                return;
            }
        }
        throw new AssertionError(context + " default wave should contain reward entry for " + entity);
    }

    private static void requireMultipliers(
            ZombiesWaveDefinition wave,
            String entity,
            double healthMultiplier,
            double damageMultiplier,
            double speedMultiplier,
            String context
    ) {
        for (ZombiesWaveMobEntry mob : wave.getMobs()) {
            if (entity.equals(mob.getEntity())) {
                requireClose(mob.getHealthMultiplier(), healthMultiplier,
                        context + " default health multiplier for " + entity);
                requireClose(mob.getDamageMultiplier(), damageMultiplier,
                        context + " default damage multiplier for " + entity);
                requireClose(mob.getSpeedMultiplier(), speedMultiplier,
                        context + " default speed multiplier for " + entity);
                return;
            }
        }
        throw new AssertionError(context + " default wave should contain multiplier entry for " + entity);
    }

    private static String firstIssue(ZombiesWaveConfigRepository.LoadResult result) {
        if (result.getIssues().isEmpty()) {
            return "no issues";
        }
        ZombiesWaveValidator.ValidationIssue issue = result.getIssues().get(0);
        return issue.getCode() + " " + issue.getMessage();
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

    private static void requireClose(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > 0.0001D) {
            throw new AssertionError(message + ": expected " + expected + " got " + actual);
        }
    }
}
