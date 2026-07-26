package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.extension.ModeEntityReconciliationContributor;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;

/** Keeps the Zombies active-mob projection aligned with authoritative entity ownership. */
public final class ZombiesEntityReconciliationContributor implements ModeEntityReconciliationContributor {
    @Override
    public String id() {
        return "zombies-active-mob-counter";
    }

    @Override
    public boolean supports(RoomId roomId) {
        return roomId != null && BuiltInGameModes.isZombies(roomId.gameType());
    }

    @Override
    public void onMissingEntity(ModeEntityOwnershipRegistry.Entry entry) {
        if (entry != null) {
            ZombiesActiveMobCounter.instance().unregister(entry.roomId(), entry.entityId());
        }
    }
}
