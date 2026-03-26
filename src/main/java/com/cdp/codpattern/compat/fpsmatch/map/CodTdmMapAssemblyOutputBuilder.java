package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class CodTdmMapAssemblyOutputBuilder {
    private CodTdmMapAssemblyOutputBuilder() {
    }

    static CodTdmMapAssemblyOutput build(
            CodTdmMap map,
            CodTdmMatchRuntimeState matchState,
            CodTdmRespawnRuntime respawnRuntime,
            CodTdmEndTeleportRuntime endTeleportRuntime,
            CodTdmMapRuntimeBundleBuilder.RuntimeBundle runtimeBundle,
            Supplier<String> mapNameSupplier,
            BooleanSupplier startedSupplier,
            Runnable markStartedAction,
            Runnable markStoppedAction
    ) {
        CodTdmMapLifecycleRuntime lifecycleRuntime = new CodTdmMapLifecycleRuntime(
                runtimeBundle.clientSyncCoordinator(),
                runtimeBundle.phaseRuntime(),
                runtimeBundle.tickRuntime(),
                runtimeBundle.resetRuntime(),
                runtimeBundle.teamMembershipCoordinator(),
                respawnRuntime,
                endTeleportRuntime,
                runtimeBundle.matchProgressRuntime(),
                matchState::phase,
                markStartedAction,
                markStoppedAction
        );
        CodTdmActionPort actionPort = CodTdmMapActions.fromRuntimes(
                runtimeBundle.combatRuntime(),
                runtimeBundle.teamMembershipCoordinator(),
                runtimeBundle.mapMutationRuntime(),
                endTeleportRuntime,
                runtimeBundle.voteRuntime(),
                respawnRuntime,
                mapNameSupplier
        );
        CodTdmMapReadRuntime readRuntime = new CodTdmMapReadRuntime(
                matchState,
                runtimeBundle.combatRuntime(),
                respawnRuntime,
                endTeleportRuntime,
                runtimeBundle.matchProgressRuntime()
        );
        CodTdmReadPort readPort = CodTdmMapReadPortAdapter.fromMap(
                map,
                readRuntime,
                mapNameSupplier,
                startedSupplier
        );
        return new CodTdmMapAssemblyOutput(lifecycleRuntime, actionPort, readPort);
    }
}
