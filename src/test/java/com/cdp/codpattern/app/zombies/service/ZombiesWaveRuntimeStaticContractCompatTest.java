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
    private static final Path RECYCLE_SERVICE =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesMobRecycleService.java");
    private static final Path WAVE_DIRECTOR =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesWaveDirector.java");
    private static final Path WAVE_DEFINITION =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/model/ZombiesWaveDefinition.java");
    private static final Path PHASE_STATE_MACHINE =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/runtime/ZombiesPhaseStateMachine.java");
    private static final Path ROOM_HANDLE =
            Path.of("src/main/java/com/cdp/codpattern/compat/fpsmatch/map/ZombiesRoomHandleFactory.java");
    private static final Path ZOMBIES_MAP =
            Path.of("src/main/java/com/cdp/codpattern/compat/fpsmatch/map/ZombiesMap.java");
    private static final Path COD_TDM_EVENT_HANDLER =
            Path.of("src/main/java/com/cdp/codpattern/compat/fpsmatch/event/CodTdmEventHandler.java");
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
        String recycleService = read(RECYCLE_SERVICE);
        String waveDirector = read(WAVE_DIRECTOR);
        String waveDefinition = read(WAVE_DEFINITION);
        String phaseStateMachine = read(PHASE_STATE_MACHINE);
        String roomHandle = read(ROOM_HANDLE);
        String zombiesMap = read(ZOMBIES_MAP);
        String codTdmEventHandler = read(COD_TDM_EVENT_HANDLER);
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
                "static final float ROOM_MONSTER_MAX_UP_STEP = 1.25F;",
                "room monsters should be allowed to step over low non-standard obstacle shapes");
        requireContains(spawnService,
                "mob.setMaxUpStep(ROOM_MONSTER_MAX_UP_STEP);",
                "spawned room monsters should receive the zombies obstacle step height");
        requireContains(spawnService,
                "new RoomMonsterObstacleJumpGoal(pathfinderMob));",
                "spawned room monsters should receive the zombies obstacle jump goal");
        requireContains(spawnService,
                "new RoomMonsterObstacleDetourGoal(pathfinderMob));",
                "spawned room monsters should receive the zombies obstacle detour goal");
        requireContains(spawnService,
                "static final int ROOM_MONSTER_DETOUR_STUCK_TICKS = 10;",
                "room monster detours should wait for a short stuck window before taking over movement");
        requireContains(spawnService,
                "static final double ROOM_MONSTER_DETOUR_SIDE_DISTANCE = 1.25D;",
                "room monster detours should test side offsets around blocking collision shapes");
        requireContains(spawnService,
                "stuckTicks < ROOM_MONSTER_DETOUR_STUCK_TICKS",
                "room monster detours should only start after repeated blocked non-progress ticks");
        requireContains(spawnService,
                "detourTarget = chooseDetourTarget(target);",
                "room monster detours should pick a left or right bypass target");
        requireContains(spawnService,
                "mob.getMoveControl().setWantedPosition(\n                    detourTarget.x,",
                "room monster detours should directly drive short side movement around bad pathing");
        requireContains(spawnService,
                "mob.level().noCollision(mob, detourBox)",
                "room monster detours should use collision boxes for non-standard obstacle shapes");
        requireContains(spawnService,
                "return !mob.level().noCollision(mob, detourBox.move(0.0D, -1.0D, 0.0D));",
                "room monster detours should avoid choosing unsupported side positions");
        requireContains(spawnService,
                "mob.getJumpControl().jump();",
                "room monster obstacle handling should force a jump when blocked by low shapes");
        requireContains(spawnService,
                "mob.level().noCollision(mob, probeBox)",
                "room monster obstacle detection should use collision boxes for non-standard block shapes");
        requireContains(spawnService,
                "static final double ROOM_MONSTER_DROP_DOWN_MIN_HEIGHT = 2.0D;",
                "room monsters should actively jump down when the target is at least two blocks lower");
        requireContains(spawnService,
                "pendingJumpImpulse = dropDownImpulse(target);",
                "room monster terrain jumping should attempt a drop-down impulse before low-obstacle checks");
        requireContains(spawnService,
                "if (pendingJumpImpulse != null) {\n                return true;\n            }\n            return hasLowFrontObstacle(target);",
                "drop-down descent should take priority over repeated low-obstacle hops");
        requireContains(spawnService,
                "mob.setDeltaMovement(",
                "drop-down jumping should push the mob forward off ledges");
        requireContains(spawnService,
                "mob.level().noCollision(mob, dropBox)",
                "drop-down jumping should detect an open ledge space below the forward probe");
        requireContains(spawnService,
                "new RoomMonsterDropDownChaseGoal(pathfinderMob));",
                "spawned room monsters should actively chase lower room targets toward ledges");
        requireContains(spawnService,
                "static final int ROOM_MONSTER_DROP_DOWN_RECOVERY_TICKS = 24;",
                "drop-down jumping should have a landing recovery window to prevent jump loops");
        requireContains(spawnService,
                "cooldownTicks = dropDownJump\n                    ? ROOM_MONSTER_DROP_DOWN_RECOVERY_TICKS\n                    : ROOM_MONSTER_OBSTACLE_JUMP_COOLDOWN_TICKS;",
                "drop-down jumping should use a longer recovery than low-obstacle hops");
        requireContains(spawnService,
                "if (mob.onGround()) {\n                    cooldownTicks--;\n                }",
                "drop-down jump recovery should be consumed after landing instead of while falling");
        requireContains(spawnService,
                "2,\n                    new RoomMonsterDropDownChaseGoal(pathfinderMob));",
                "drop-down chase should yield to normal melee movement once a valid path resumes");
        requireContains(spawnService,
                "static final double ROOM_MONSTER_DROP_DOWN_CHASE_SPEED = 1.15D;",
                "drop-down chasing should use a zombies-specific aggressive movement speed");
        requireContains(spawnService,
                "mob.getMoveControl().setWantedPosition(",
                "drop-down chasing should drive monsters toward lower targets even when normal pathing stalls");
        requireContains(spawnService,
                "mob.getY() - target.getY() >= ROOM_MONSTER_DROP_DOWN_MIN_HEIGHT",
                "drop-down chasing should stay scoped to targets substantially below the monster");
        requireContains(spawnService,
                "effectiveSpawnWeight(spawn.weight(), targetDistance, nearestDistance, weighting)",
                "spawn-point selection should combine map weight with distance multiplier");
        requireContains(spawnService,
                "spawnPointWeightingSupplier",
                "spawn-point selection should use an injectable map-scoped rules supplier");
        requireContains(spawnService,
                "case ZombiesWaveValidator.VANILLA_WITHER_SKELETON_ID -> EntityType.WITHER_SKELETON.create(level);",
                "wither skeleton should be a supported zombies wave entity");
        requireContains(spawnService,
                "case ZombiesWaveValidator.VANILLA_CREEPER_ID -> EntityType.CREEPER.create(level);",
                "creeper should be a supported zombies wave entity");
        requireContains(spawnService,
                "case ZombiesWaveValidator.VANILLA_WOLF_ID -> EntityType.WOLF.create(level);",
                "wolf should be a supported zombies wave entity");
        requireContains(spawnService,
                "case ZombiesWaveValidator.VANILLA_SILVERFISH_ID -> EntityType.SILVERFISH.create(level);",
                "silverfish should be a supported zombies wave entity");
        requireContains(spawnService,
                "wolf.setPersistentAngerTarget(target.getUUID());",
                "zombies wolves should keep anger on their room target");
        requireContains(spawnService,
                "wolf.setRemainingPersistentAngerTime(ROOM_MONSTER_WOLF_ANGER_TICKS);",
                "zombies wolves should be spawned and refreshed as angry wolves");
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
        requireContains(combatAdapter,
                "source.is(DamageTypes.FALL)",
                "owned zombies room monsters should reject fall damage");
        requireContains(combatAdapter,
                "entity.fallDistance = 0.0F;",
                "fall-damage rejection should clear accumulated fall distance");
        requireContains(waveState,
                "public Set<UUID> activeZombieEntityIdsSnapshot()",
                "wave runtime must expose active zombie ids for client marker sync");
        requireContains(waveState,
                "public boolean requeueBudget(String mobId, int recycleCount)",
                "wave runtime must support retry recycling by returning budget with a recycle count");
        requireContains(waveState,
                "public int consumeRequeuedRecycleCount(String mobId)",
                "spawned retry mobs must consume the recycle count attached to their returned budget");
        requireContains(waveState,
                "public boolean tickWaveCompleteDelay(int requiredTicks)",
                "wave runtime must track the hard post-completion delay");
        requireContains(recycleService,
                "static final int SCAN_INTERVAL_TICKS = 80;",
                "zombie recycle scanner must run on the hardcoded 80-tick cadence");
        requireContains(recycleService,
                "static final int NO_TARGET_RECYCLE_TICKS = 20 * 20;",
                "zombie recycle scanner must use the hardcoded 20-second no-target timeout");
        requireContains(recycleService,
                "static final int STUCK_RECYCLE_TICKS = 16 * 20;",
                "zombie recycle scanner must use the hardcoded 16-second stuck timeout");
        requireContains(recycleService,
                "static final double MIN_MOVED_DISTANCE = 0.5D;",
                "zombie recycle scanner must treat movement below 0.5 blocks as stuck");
        requireContains(recycleService,
                "static final double STUCK_MIN_TARGET_DISTANCE = 8.0D;",
                "zombie recycle scanner must only stuck-recycle mobs farther than 8 blocks from target");
        requireContains(recycleService,
                "static final int MAX_REQUEUE_RECYCLES_PER_ENTITY = 2;",
                "zombie recycle scanner must only requeue the first two recycle attempts");
        requireContains(recycleService,
                "roomTick % SCAN_INTERVAL_TICKS != 0L",
                "zombie recycle scanner must skip non-scan ticks");
        requireContains(recycleService,
                "waveState.requeueBudget(mobId, nextRecycleCount);",
                "zombie recycle scanner must return budget only for retry-eligible recycles");
        requireContains(recycleService,
                "mob.discard();",
                "zombie recycle scanner must discard recycled mobs instead of awarding kills");
        requireContains(spawnService,
                "public static final String WAVE_RECYCLE_COUNT_TAG",
                "spawned zombies should carry retry recycle count metadata");
        requireContains(spawnService,
                "waveState.consumeRequeuedRecycleCount(mobId)",
                "spawn path should restore the retry recycle count onto requeued mobs");
        requireContains(waveState,
                "nextSpawnIntervalTicks",
                "wave runtime must remember the randomized interval chosen for the next spawn");
        requireContains(waveDirector,
                "definition.chooseSpawnIntervalTicks(level.random)",
                "wave director should choose randomized spawn intervals from the wave range");
        requireContains(waveDefinition,
                "fastestSpawnIntervalTicks",
                "wave definition should expose fastest spawn interval bound");
        requireContains(waveDefinition,
                "slowestSpawnIntervalTicks",
                "wave definition should expose slowest spawn interval bound");
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
        requireContains(zombiesMap,
                "private final ZombiesMobRecycleService mobRecycleService;",
                "zombies map should own the hardcoded no-target/stuck recycle service");
        requireContains(zombiesMap,
                "mobRecycleService.tick(\n                        roomId,\n                        getServerLevel(),\n                        runtimeState.waveState(),\n                        runtimeState.roomTick());",
                "zombies map should scan active mobs for recycle before the wave director spawn tick");
        requireContains(zombiesMap,
                "mobRecycleService.reset();",
                "zombies map should reset recycle monitor state during cleanup/runtime reset");
        requireContains(codTdmEventHandler,
                "public static void onExplosionDetonate(ExplosionEvent.Detonate event)",
                "owned creeper explosions should be handled by the Forge explosion event");
        requireContains(codTdmEventHandler,
                "event.getAffectedBlocks().clear();",
                "owned zombies creeper explosions should not damage terrain");
        requireContains(codTdmEventHandler,
                "port.onEntityDeath(creeper, new EntityDeathContext(",
                "owned zombies creeper self-explosion should count as a killed room entity");
        requireContains(codTdmEventHandler,
                "public static void onLivingDrops(LivingDropsEvent event)",
                "zombies rooms should clear death drops, including player drops");
        requireContains(codTdmEventHandler,
                "event.getDrops().clear();",
                "zombies room death drops should be removed before entering the world");
        requireContains(codTdmEventHandler,
                "public static void onLivingExperienceDrop(LivingExperienceDropEvent event)",
                "zombies rooms should clear death experience, including player experience");
        requireContains(codTdmEventHandler,
                "event.setDroppedExperience(0);",
                "zombies room death experience should be zeroed");
        requireContains(codTdmEventHandler,
                "public static void onItemToss(ItemTossEvent event)",
                "zombies room player tosses should not create item entities");
        requireContains(codTdmEventHandler,
                "public static void onEntityJoinLevel(EntityJoinLevelEvent event)",
                "zombies rooms should reject any item or experience entity that reaches the world join path");
        requireContains(codTdmEventHandler,
                "entity instanceof ItemEntity || entity instanceof ExperienceOrb",
                "zombies room drop suppression should include experience orbs");
        requireContains(codTdmEventHandler,
                "event.getEntity().discard();",
                "zombies room drop entities should be discarded when blocked");
        requireContains(codTdmEventHandler,
                "FpsMatchMapRegistry.listMaps(BuiltInGameModes.ZOMBIES)",
                "item-drop suppression should be scoped to zombies map areas");
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
