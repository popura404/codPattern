package com.cdp.codpattern.app.zombies.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZombiesWaveRuntimeStaticContractCompatTest {
    private static final Path SPAWN_SERVICE =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesMobSpawnService.java");
    private static final Path COMBAT_ADAPTER =
            Path.of("src/main/java/com/cdp/codpattern/compat/fpsmatch/map/ZombiesEntityCombatEventAdapter.java");
    private static final Path WAVE_STATE =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/runtime/ZombiesWaveRuntimeState.java");
    private static final Path ROOM_HANDLE =
            Path.of("src/main/java/com/cdp/codpattern/compat/fpsmatch/map/ZombiesRoomHandleFactory.java");
    private static final Path CLIENT_STATE =
            Path.of("src/main/java/com/cdp/codpattern/client/zombies/ClientZombiesState.java");
    private static final Path ZOMBIES_MARKER_RENDERER =
            Path.of("src/main/java/com/cdp/codpattern/event/client/ZombiesCombatMarkerWorldRenderer.java");

    private ZombiesWaveRuntimeStaticContractCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        String spawnService = read(SPAWN_SERVICE);
        String combatAdapter = read(COMBAT_ADAPTER);
        String waveState = read(WAVE_STATE);
        String roomHandle = read(ROOM_HANDLE);
        String clientState = read(CLIENT_STATE);
        String zombiesMarkerRenderer = read(ZOMBIES_MARKER_RENDERER);

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
        requireContains(waveState,
                "public Set<UUID> activeZombieEntityIdsSnapshot()",
                "wave runtime must expose active zombie ids for client marker sync");
        requireContains(roomHandle,
                "ZombiesRuntimeStateKeys.ACTIVE_ZOMBIE_ENTITY_IDS",
                "zombies runtime snapshot must include active zombie entity ids");
        requireContains(clientState,
                "public static Set<UUID> activeZombieEntityIds()",
                "client zombies state must parse active zombie entity ids");
        requireContains(zombiesMarkerRenderer,
                "CombatMarkerWorldRenderer.renderEnemyMarker(",
                "zombies markers should reuse the shared combat marker renderer");
        requireContains(zombiesMarkerRenderer,
                "findClientEntity(level, entityId)",
                "zombies markers should render only synced active zombie ids");
        requireContains(zombiesMarkerRenderer,
                "for (UUID entityId : activeZombieIds)",
                "zombies markers should render immediately for every synced active zombie");
        requireAbsent(zombiesMarkerRenderer,
                "hasLineOfSight",
                "zombies markers must not depend on visibility checks");
        requireAbsent(zombiesMarkerRenderer,
                "getFrustum().isVisible",
                "zombies markers must not cull synced zombies by camera frustum");

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

    private static void requireAbsent(String text, String forbidden, String message) {
        if (text.contains(forbidden)) {
            throw new AssertionError(message + ": found `" + forbidden + "`");
        }
    }
}
