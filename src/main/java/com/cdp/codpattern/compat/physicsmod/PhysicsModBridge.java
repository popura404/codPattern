package com.cdp.codpattern.compat.physicsmod;

import java.util.UUID;

public interface PhysicsModBridge {
    void retainEntity(UUID entityId, int ticks);
}
