package com.cdp.codpattern.app.match.runtime.termination;

import com.cdp.codpattern.app.match.model.RoomId;
import java.util.UUID;

/** Identity captured by the server, never supplied as an authority by a client. */
public record ForceEndContext(RoomId roomId, UUID generation, UUID operationId,
                              String actor, String reason) {
}
