package com.cdp.codpattern.app.match.runtime.termination;

import com.cdp.codpattern.app.match.model.result.ModeOperationResult;

/** Required mode-specific work. Shared recovery never depends on these callbacks succeeding. */
public interface ModeForceEndHandler {
    ModeOperationResult<Void> stop(ForceEndContext context);

    default ModeOperationResult<Void> settle(ForceEndContext context) {
        return ModeOperationResult.success(null);
    }

    ModeOperationResult<Void> cleanup(ForceEndContext context);
}
