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
    private static final Path PHASE_STATE_MACHINE =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/runtime/ZombiesPhaseStateMachine.java");
    private static final Path ROOM_HANDLE =
            Path.of("src/main/java/com/cdp/codpattern/compat/fpsmatch/map/ZombiesRoomHandleFactory.java");
    private static final Path ZOMBIES_MAP =
            Path.of("src/main/java/com/cdp/codpattern/compat/fpsmatch/map/ZombiesMap.java");
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
        String phaseStateMachine = read(PHASE_STATE_MACHINE);
        String roomHandle = read(ROOM_HANDLE);
        String zombiesMap = read(ZOMBIES_MAP);
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
                "static final double ROOM_MONSTER_FOLLOW_RANGE = 128.0D;",
                "room monsters should use an extended zombies-mode follow range");
        requireContains(spawnService,
                "mob.setPersistenceRequired();",
                "room monsters should not despawn just because they are far from players");
        requireContains(spawnService,
                "pathfinderMob.targetSelector.addGoal(\n                0,\n                new RoomSurvivorTargetGoal(pathfinderMob, targetSupplier));",
                "spawned room monsters should get a room-scoped target selector");
        requireContains(spawnService,
                "if (!player.level().dimension().equals(mob.level().dimension()))",
                "room target selection must stay dimension scoped");
        requireAbsent(spawnService,
                "hasLineOfSight",
                "room target selection must not drop targets behind cover");
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
        requireContains(waveState,
                "public boolean tickWaveCompleteDelay(int requiredTicks)",
                "wave runtime must track the hard post-completion delay");
        requireContains(phaseStateMachine,
                "public static final int WAVE_COMPLETE_DELAY_SECONDS = 3;",
                "wave state machine must hardcode the three-second post-completion delay");
        requireContains(phaseStateMachine,
                "tickWaveCompleteDelay(WAVE_COMPLETE_DELAY_TICKS)",
                "completed waves must wait out the post-completion delay before phase transition");
        requireContains(phaseStateMachine,
                "state.waveState().resetWaveCompleteDelay();",
                "incomplete waves must reset the post-completion delay counter");
        requireContains(roomHandle,
                "ZombiesRuntimeStateKeys.ACTIVE_ZOMBIE_ENTITY_IDS",
                "zombies runtime snapshot must include active zombie entity ids");
        requireContains(roomHandle,
                "map.runtimeState().phase() == ZombiesGamePhase.INTERMISSION",
                "intermission runtime snapshot should publish the target wave number");
        requireContains(roomHandle,
                "waveState.targetWave()",
                "intermission HUD should receive the upcoming target wave number");
        requireContains(zombiesMap,
                "SoundEvents.BELL_BLOCK",
                "wave intermission start should play the Minecraft bell sound");
        requireContains(zombiesMap,
                "playIntermissionBell();",
                "bell sound must be triggered from the intermission enter hook");
        requireContains(zombiesMap,
                "this::aliveSurvivorPlayers",
                "zombies map should provide room-scoped alive survivor targets to spawned mobs");
        requireContains(zombiesMap,
                "playerStateService.canInteract(player.getUUID())",
                "room monster target source should exclude dead or disconnected survivors");
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
                "zombies markers should inspect every synced active zombie");
        requireContains(zombiesMarkerRenderer,
                "event.getFrustum().isVisible(livingEntity.getBoundingBox().inflate(0.25D))",
                "zombies markers should render only active zombies in the camera view");
        requireContains(zombiesMarkerRenderer,
                "localPlayer.hasLineOfSight(livingEntity)",
                "zombies markers should hide health bars for active zombies the player cannot see");
        requireAbsent(zombiesMarkerRenderer,
                "enemyFocusRequiredTicks",
                "zombies markers must not wait for a focus delay before rendering visible active zombies");

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
