package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.TdmTeamDefaults;
import com.cdp.codpattern.app.tdm.model.TdmTeamNames;
import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class CodTdmMapRuntimeAssembly {
    private CodTdmMapRuntimeAssembly() {
    }

    record BootstrapResult(
            CodTdmKitsRuntime kitsRuntime,
            CodTdmMapLifecycleRuntime lifecycleRuntime,
            CodTdmActionPort actionPort,
            CodTdmReadPort readPort
    ) {
    }

    static BootstrapResult bootstrap(
            CodTdmMap map,
            Consumer<ServerPlayer> leaveFromBaseMapAction,
            Supplier<String> mapNameSupplier,
            BooleanSupplier startedSupplier,
            Runnable markStartedAction,
            Runnable markStoppedAction
    ) {
        CodTdmMatchRuntimeState matchState = new CodTdmMatchRuntimeState();
        CodTdmPlayerRuntimeState playerState = new CodTdmPlayerRuntimeState();
        CodTdmMapSetupState mapSetupState = new CodTdmMapSetupState();
        CodTdmKitsRuntime kitsRuntime = new CodTdmKitsRuntime(mapSetupState, () -> map.getMapTeams().getTeams());
        CodTdmEndTeleportRuntime endTeleportRuntime = new CodTdmEndTeleportRuntime(mapSetupState, map::teleportToPoint);
        CodTdmRespawnRuntime respawnRuntime = new CodTdmRespawnRuntime(
                playerState,
                map::getServerLevel,
                map::teleportPlayerToReSpawnPoint,
                map::givePlayerKits
        );
        CodTdmMapAssemblyOutput assemblyResult = assemble(
                map,
                playerState,
                matchState,
                respawnRuntime,
                endTeleportRuntime,
                leaveFromBaseMapAction,
                mapNameSupplier,
                startedSupplier,
                markStartedAction,
                markStoppedAction
        );
        return new BootstrapResult(
                kitsRuntime,
                assemblyResult.lifecycleRuntime(),
                assemblyResult.actionPort(),
                assemblyResult.readPort()
        );
    }

    private static CodTdmMapAssemblyOutput assemble(
            CodTdmMap map,
            CodTdmPlayerRuntimeState playerState,
            CodTdmMatchRuntimeState matchState,
            CodTdmRespawnRuntime respawnRuntime,
            CodTdmEndTeleportRuntime endTeleportRuntime,
            Consumer<ServerPlayer> leaveFromBaseMapAction,
            Supplier<String> mapNameSupplier,
            BooleanSupplier startedSupplier,
            Runnable markStartedAction,
            Runnable markStoppedAction
    ) {
        CodTdmMapComposition.Components components = CodTdmMapComponentsAssembler.compose(
                map,
                playerState,
                matchState,
                respawnRuntime,
                endTeleportRuntime,
                mapNameSupplier,
                leaveFromBaseMapAction
        );
        CodTdmMapRuntimeBundleBuilder.RuntimeBundle runtimeBundle = CodTdmMapRuntimeBundleBuilder.build(
                map,
                playerState,
                matchState,
                respawnRuntime,
                components
        );
        initializeDefaultTeamsAndScores(map, matchState);
        return CodTdmMapAssemblyOutputBuilder.build(
                map,
                matchState,
                respawnRuntime,
                endTeleportRuntime,
                runtimeBundle,
                mapNameSupplier,
                startedSupplier,
                markStartedAction,
                markStoppedAction
        );
    }

    private static void initializeDefaultTeamsAndScores(CodTdmMap map, CodTdmMatchRuntimeState matchState) {
        map.addTeam(TdmTeamNames.KORTAC, TdmTeamDefaults.DEFAULT_TEAM_LIMIT);
        map.addTeam(TdmTeamNames.SPECGRU, TdmTeamDefaults.DEFAULT_TEAM_LIMIT);
        matchState.putTeamScore(TdmTeamNames.KORTAC, 0);
        matchState.putTeamScore(TdmTeamNames.SPECGRU, 0);
    }
}
