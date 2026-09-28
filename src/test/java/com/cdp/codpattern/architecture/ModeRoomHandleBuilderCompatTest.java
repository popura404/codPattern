package com.cdp.codpattern.architecture;

import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.cdp.codpattern.app.match.ModeRoomHandleCapabilityAudit;
import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeCombatEventPort;
import com.cdp.codpattern.app.match.port.ModeEntityCombatEventPort;
import com.cdp.codpattern.app.match.port.ModeEntityLifecyclePort;
import com.cdp.codpattern.app.match.port.ModeInteractableObjectPort;
import com.cdp.codpattern.app.match.port.ModeKitDistributionPort;
import com.cdp.codpattern.app.match.port.ModeMapEditPort;
import com.cdp.codpattern.app.match.port.ModePlayerRuntimeStatePort;
import com.cdp.codpattern.app.match.port.ModeRespawnPolicyPort;
import com.cdp.codpattern.app.match.port.ModeRoomActionPort;
import com.cdp.codpattern.app.match.port.ModeRoomLifecyclePort;
import com.cdp.codpattern.app.match.port.ModeRoomSummaryPort;
import com.cdp.codpattern.app.match.port.ModeRoomTickPort;
import com.cdp.codpattern.app.match.port.ModeRosterPort;
import com.cdp.codpattern.app.match.port.ModeRuntimeStatePort;
import com.cdp.codpattern.app.match.port.ReadyStatePort;
import com.cdp.codpattern.app.match.port.TeamRoomPort;
import com.cdp.codpattern.app.match.port.VoteControlPort;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.Set;

public final class ModeRoomHandleBuilderCompatTest {
    private ModeRoomHandleBuilderCompatTest() {
    }

    public static void main(String[] args) {
        builderPreservesEveryNamedPort();
        legacyConstructorMatchesBuilderComposition();
        capabilityAuditChecksOnlyCharacterizedDirectMappings();
        endTeleportRegistrationContract();
        System.out.println("PASS mode room handle builder compat");
    }

    private static void builderPreservesEveryNamedPort() {
        RoomId roomId = RoomId.of("fixture", "builder");
        ModeRoomSummaryPort summary = stub(ModeRoomSummaryPort.class);
        ModeRoomLifecyclePort lifecycle = stub(ModeRoomLifecyclePort.class);
        ModeRoomActionPort action = stub(ModeRoomActionPort.class);
        TeamRoomPort team = stub(TeamRoomPort.class);
        ReadyStatePort ready = stub(ReadyStatePort.class);
        VoteControlPort vote = stub(VoteControlPort.class);
        ModeCombatEventPort combat = stub(ModeCombatEventPort.class);
        ModeRosterPort roster = stub(ModeRosterPort.class);
        ModeMapEditPort mapEdit = stub(ModeMapEditPort.class);
        ModeKitDistributionPort kits = stub(ModeKitDistributionPort.class);
        ModeEntityCombatEventPort entityCombat = stub(ModeEntityCombatEventPort.class);
        ModeEntityLifecyclePort entityLifecycle = stub(ModeEntityLifecyclePort.class);
        ModeRoomTickPort tick = stub(ModeRoomTickPort.class);
        ModeRuntimeStatePort runtimeState = stub(ModeRuntimeStatePort.class);
        ModeInteractableObjectPort objects = stub(ModeInteractableObjectPort.class);
        ModePlayerRuntimeStatePort playerRuntime = stub(ModePlayerRuntimeStatePort.class);
        ModeRespawnPolicyPort respawn = stub(ModeRespawnPolicyPort.class);

        ModeRoomHandle handle = ModeRoomHandle.builder(roomId, summary, lifecycle)
                .withAction(action)
                .withTeam(team)
                .withReady(ready)
                .withVote(vote)
                .withCombatEvents(combat)
                .withRoster(roster)
                .withMapEdit(mapEdit)
                .withKitDistribution(kits)
                .withEntityCombatEvents(entityCombat)
                .withEntityLifecycle(entityLifecycle)
                .withTick(tick)
                .withRuntimeState(runtimeState)
                .withInteractableObjects(objects)
                .withPlayerRuntimeState(playerRuntime)
                .withRespawnPolicy(respawn)
                .build();

        require(handle.roomId().equals(roomId), "roomId should be preserved");
        require(handle.summaryPort() == summary, "summary port should be preserved");
        require(handle.lifecyclePort() == lifecycle, "lifecycle port should be preserved");
        require(handle.actionPort().orElseThrow() == action, "action port should be preserved");
        require(handle.teamPort().orElseThrow() == team, "team port should be preserved");
        require(handle.readyPort().orElseThrow() == ready, "ready port should be preserved");
        require(handle.votePort().orElseThrow() == vote, "vote port should be preserved");
        require(handle.combatEventPort().orElseThrow() == combat, "combat port should be preserved");
        require(handle.rosterPort().orElseThrow() == roster, "roster port should be preserved");
        require(handle.mapEditPort().orElseThrow() == mapEdit, "map edit port should be preserved");
        require(handle.kitDistributionPort().orElseThrow() == kits, "kit port should be preserved");
        require(handle.entityCombatEventPort().orElseThrow() == entityCombat,
                "entity combat port should be preserved");
        require(handle.entityLifecyclePort().orElseThrow() == entityLifecycle,
                "entity lifecycle port should be preserved");
        require(handle.tickPort().orElseThrow() == tick, "tick port should be preserved");
        require(handle.runtimeStatePort().orElseThrow() == runtimeState,
                "runtime state port should be preserved");
        require(handle.interactableObjectPort().orElseThrow() == objects,
                "object port should be preserved");
        require(handle.playerRuntimeStatePort().orElseThrow() == playerRuntime,
                "player runtime port should be preserved");
        require(handle.respawnPolicyPort().orElseThrow() == respawn,
                "respawn port should be preserved");

        ModeRoomHandle empty = ModeRoomHandle.builder(roomId, summary, lifecycle)
                .withReady(null)
                .build();
        require(empty.readyPort().isEmpty(), "null named ports should remain absent");
    }

    @SuppressWarnings("deprecation")
    private static void legacyConstructorMatchesBuilderComposition() {
        RoomId roomId = RoomId.of("fixture", "legacy");
        ModeRoomSummaryPort summary = stub(ModeRoomSummaryPort.class);
        ModeRoomLifecyclePort lifecycle = stub(ModeRoomLifecyclePort.class);
        ModeRoomActionPort action = stub(ModeRoomActionPort.class);
        TeamRoomPort team = stub(TeamRoomPort.class);
        ReadyStatePort ready = stub(ReadyStatePort.class);
        VoteControlPort vote = stub(VoteControlPort.class);
        ModeCombatEventPort combat = stub(ModeCombatEventPort.class);
        ModeRosterPort roster = stub(ModeRosterPort.class);
        ModeMapEditPort mapEdit = stub(ModeMapEditPort.class);
        ModeKitDistributionPort kits = stub(ModeKitDistributionPort.class);

        ModeRoomHandle legacy = new ModeRoomHandle(
                roomId,
                summary,
                lifecycle,
                Optional.of(action),
                Optional.of(team),
                Optional.of(ready),
                Optional.of(vote),
                Optional.of(combat),
                Optional.of(roster),
                Optional.of(mapEdit),
                Optional.of(kits));
        ModeRoomHandle named = ModeRoomHandle.builder(roomId, summary, lifecycle)
                .withAction(action)
                .withTeam(team)
                .withReady(ready)
                .withVote(vote)
                .withCombatEvents(combat)
                .withRoster(roster)
                .withMapEdit(mapEdit)
                .withKitDistribution(kits)
                .build();

        require(legacy.equals(named), "legacy constructor and named builder should compose the same handle");
    }

    private static void capabilityAuditChecksOnlyCharacterizedDirectMappings() {
        ModeRoomHandle complete = ModeRoomHandle.builder(
                        RoomId.of("fixture", "capabilities"),
                        stub(ModeRoomSummaryPort.class),
                        stub(ModeRoomLifecyclePort.class))
                .withTeam(stub(TeamRoomPort.class))
                .withReady(stub(ReadyStatePort.class))
                .withVote(stub(VoteControlPort.class))
                .build();
        Set<ModeCapability> declared = Set.of(
                ModeCapability.TEAM_SELECTION,
                ModeCapability.TEAM_BALANCE,
                ModeCapability.READY_STATE,
                ModeCapability.START_VOTE,
                ModeCapability.END_VOTE,
                ModeCapability.DYNAMIC_RESPAWN_POINTS,
                ModeCapability.MATCH_END_TELEPORT,
                ModeCapability.MODE_SPECIFIC_MAP_FEATURES);
        require(ModeRoomHandleCapabilityAudit.findMissingPorts(declared, complete).isEmpty(),
                "descriptive capabilities must not create false port requirements");

        ModeRoomHandle empty = ModeRoomHandle.builder(
                        RoomId.of("fixture", "missing"),
                        stub(ModeRoomSummaryPort.class),
                        stub(ModeRoomLifecyclePort.class))
                .build();
        var missing = ModeRoomHandleCapabilityAudit.findMissingPorts(declared, empty);
        require(missing.size() == 5, "all five characterized capability mappings should be diagnosed");
        require(missing.stream().allMatch(issue -> Set.of("teamPort", "readyPort", "votePort")
                        .contains(issue.portName())),
                "only team, ready, and vote ports should be diagnosed");
    }

    private static void endTeleportRegistrationContract() {
        var stored = new java.util.concurrent.atomic.AtomicReference<com.phasetranscrystal.fpsmatch.core.data.SpawnPointData>();
        var editor = com.cdp.codpattern.app.match.editor.ModeEndTeleportSupport.editor(
                () -> java.util.Optional.ofNullable(stored.get()), stored::set);
        var summary = (ModeRoomSummaryPort) Proxy.newProxyInstance(ModeRoomSummaryPort.class.getClassLoader(),
                new Class<?>[]{ModeRoomSummaryPort.class}, (proxy, method, args) ->
                        method.getName().equals("supportsConfiguredEndPoint") ? true : defaultValue(method.getReturnType()));
        var room = RoomId.of("fixture", "end-point");
        var lifecycle = stub(ModeRoomLifecyclePort.class);
        var complete = ModeRoomHandle.builder(room, summary, lifecycle).withMapEdit(editor).build();
        com.cdp.codpattern.app.match.editor.ModeEndTeleportSupport.requireHandle("fixture", complete);
        for (var invalid : java.util.List.of(
                ModeRoomHandle.builder(room, summary, lifecycle).build(),
                ModeRoomHandle.builder(room, summary, lifecycle).withMapEdit(stub(ModeMapEditPort.class)).build(),
                ModeRoomHandle.builder(room, stub(ModeRoomSummaryPort.class), lifecycle).withMapEdit(editor).build())) {
            try {
                com.cdp.codpattern.app.match.editor.ModeEndTeleportSupport.requireHandle("fixture", invalid);
                throw new AssertionError("missing end-point port must be rejected");
            } catch (IllegalStateException expected) {
                require(expected.getMessage().contains("match_end_teleport"), "error must name the required feature");
            }
        }
        String key = com.cdp.codpattern.app.match.editor.ModeMapEditorSchemas.MATCH_END_TELEPORT;
        var point = new com.phasetranscrystal.fpsmatch.core.data.SpawnPointData(net.minecraft.world.level.Level.NETHER,
                new net.minecraft.core.BlockPos(12, 70, -4), 45, 7);
        editor.setObjectFeature(key, com.cdp.codpattern.app.match.editor.ModeObjectData.fromSpawnPointData(key, point));
        require(editor.objectFeature(key).orElseThrow().toSpawnPointData().equals(point), "editor preserves dimension, coordinates and rotation");
        editor.setObjectFeature(key, null);
        require(stored.get() == null && editor.objectFeature(key).isEmpty(), "rollback can restore an unset point");
        require(!editor.supportsPointLayer("INITIAL") && !editor.supportsAreaLayer("barrier"), "end-point adapter does not expose addon-specific objects");
        try {
            editor.setObjectFeature("unknown", null);
            throw new AssertionError("unsupported feature must be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    private static <T> T stub(Class<T> type) {
        Object proxy = Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (ignored, method, args) -> defaultValue(method.getReturnType()));
        return type.cast(proxy);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == com.cdp.codpattern.app.match.runtime.termination.ModeForceEndHandler.class) {
            return new com.cdp.codpattern.app.match.runtime.termination.ModeForceEndHandler() {
                public com.cdp.codpattern.app.match.model.result.ModeOperationResult<Void> stop(com.cdp.codpattern.app.match.runtime.termination.ForceEndContext c) {
                    return com.cdp.codpattern.app.match.model.result.ModeOperationResult.success(null);
                }
                public com.cdp.codpattern.app.match.model.result.ModeOperationResult<Void> cleanup(com.cdp.codpattern.app.match.runtime.termination.ForceEndContext c) {
                    return com.cdp.codpattern.app.match.model.result.ModeOperationResult.success(null);
                }
            };
        }
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
