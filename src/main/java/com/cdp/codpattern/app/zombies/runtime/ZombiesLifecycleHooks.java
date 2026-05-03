package com.cdp.codpattern.app.zombies.runtime;

import com.cdp.codpattern.app.zombies.service.ZombiesServiceResult;

/**
 * Extension point for phase enter, tick, exit, and cleanup logic; a failure result interrupts progression.
 */
public interface ZombiesLifecycleHooks {
    default int order() {
        return 0;
    }

    default ZombiesServiceResult<Void> onEnter(ZombiesPhaseTransitionContext context) {
        return ZombiesServiceResult.ok();
    }

    default ZombiesServiceResult<Void> onTick(ZombiesPhaseTransitionContext context) {
        return ZombiesServiceResult.ok();
    }

    default ZombiesServiceResult<Void> onExit(ZombiesPhaseTransitionContext context) {
        return ZombiesServiceResult.ok();
    }

    default ZombiesServiceResult<Void> onCleanup(ZombiesPhaseTransitionContext context) {
        return ZombiesServiceResult.ok();
    }
}
