package com.cdp.codpattern.compat.physicsmod;

import java.util.UUID;

public final class NoopPhysicsModBridge implements PhysicsModBridge {
    @Override
    public void retainEntity(UUID entityId, int ticks) {
        // no-op fallback when PhysicsMod is absent
    }
}
