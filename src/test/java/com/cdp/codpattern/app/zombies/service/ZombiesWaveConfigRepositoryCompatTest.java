package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.zombies.model.ZombiesWaveDefinition;
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
        emptyDirectoryGeneratesDefaultWave();
        existingValidWaveIsNotOverwritten();
    }

    private static void emptyDirectoryGeneratesDefaultWave() throws IOException {
        Path tempRoot = Files.createTempDirectory("zombies-wave-repository-empty-");
        try {
            Path wavesDirectory = tempRoot.resolve("waves");

            ZombiesWaveConfigRepository.LoadResult result = new ZombiesWaveConfigRepository(wavesDirectory, DEFAULTS).load();
            Path generatedWave = wavesDirectory.resolve("wave_001.json");

            require(Files.isRegularFile(generatedWave), "empty waves directory should create wave_001.json");
            String generatedJson = Files.readString(generatedWave);
            require(generatedJson.contains("\"description\""), "generated default wave should contain description");
            require(generatedJson.contains("\"minecraft:zombie\""), "generated default wave should contain zombie");
            require(generatedJson.contains("\"minecraft:husk\""), "generated default wave should contain husk");
            require(result.isValid(), "generated default wave should load without issues: " + firstIssue(result));
            require(result.getMaxWave() == 1, "generated default wave should set maxWave to 1");
            require(result.getWaves().size() == 1, "generated default wave should load exactly one wave");
            require(result.getWaves().get(0).getWave() == 1, "generated default wave should be wave 1");
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
}
