package com.cdp.codpattern.app.zombies.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZombiesWaveRuntimeStaticContractCompatTest {
    private static final Path SPAWN_SERVICE =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesMobSpawnService.java");
    private static final Path COMBAT_ADAPTER =
            Path.of("src/main/java/com/cdp/codpattern/compat/fpsmatch/map/ZombiesEntityCombatEventAdapter.java");

    private ZombiesWaveRuntimeStaticContractCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        String spawnService = read(SPAWN_SERVICE);
        String combatAdapter = read(COMBAT_ADAPTER);

        requireContains(spawnService,
                "public static final String WAVE_KILL_POINTS_TAG",
                "spawned zombies should carry wave kill reward metadata");
        requireContains(spawnService,
                "public static final String WAVE_ASSIST_POINTS_TAG",
                "spawned zombies should carry wave assist reward metadata");
        requireContains(spawnService,
                "attachWaveRewardMetadata(mob, mobId.get(), waveDefinition);",
                "spawn path should attach wave reward metadata before entity ownership is used");
        requireContains(spawnService,
                "mob.getPersistentData().putDouble(WAVE_KILL_POINTS_TAG, entry.getKillPoints());",
                "spawn path should persist per-entry kill reward");
        requireContains(spawnService,
                "mob.getPersistentData().putDouble(WAVE_ASSIST_POINTS_TAG, entry.getAssistPoints());",
                "spawn path should persist per-entry assist reward");
        requireContains(combatAdapter,
                "persistentRewardOrDefault(\n                            entity,\n                            ZombiesMobSpawnService.WAVE_KILL_POINTS_TAG",
                "death reward resolver should read wave kill reward metadata");
        requireContains(combatAdapter,
                "persistentRewardOrDefault(\n                            entity,\n                            ZombiesMobSpawnService.WAVE_ASSIST_POINTS_TAG",
                "death reward resolver should read wave assist reward metadata");

        System.out.println("PASS zombies wave runtime static contract compat");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }

    private static void requireContains(String text, String expected, String message) {
        if (!text.contains(expected)) {
            throw new AssertionError(message + ": missing `" + expected + "`");
        }
    }
}
