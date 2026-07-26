package com.cdp.codpattern.app.zombies.bootstrap;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.extension.ModeDebugSnapshotContributor;
import com.cdp.codpattern.app.match.model.ModeRuntimeStateSnapshot;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.zombies.service.ZombiesDebugSnapshotService;

import java.util.List;

/** Zombies-specific debug details behind the neutral mode-keyed debug extension. */
public final class ZombiesDebugSnapshotContributor implements ModeDebugSnapshotContributor {
    private final ZombiesDebugSnapshotService service = new ZombiesDebugSnapshotService();

    @Override
    public String id() {
        return "zombies.debug_snapshot";
    }

    @Override
    public boolean supports(String gameType) {
        return BuiltInGameModes.isZombies(gameType);
    }

    @Override
    public List<String> lines(
            ModeRuntimeStateSnapshot snapshot,
            List<ModeEntityOwnershipRegistry.Entry> entities
    ) {
        return service.create(snapshot, entities).lines();
    }
}
