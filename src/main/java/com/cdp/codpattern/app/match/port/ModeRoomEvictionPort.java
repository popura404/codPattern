package com.cdp.codpattern.app.match.port;

import java.util.Set;
import java.util.UUID;

/** Opt-in deletion contract. Called only after shared recovery for this member completed.
 * Implementations must clear mode membership by UUID, including offline members and spectators,
 * without repeating inventory restoration, teleportation or changing another room's player state.
 * A thrown exception retains the member for retry. Return an immutable, complete snapshot.
 */
public interface ModeRoomEvictionPort {
    Set<UUID> deletionMembers();
    void evictRecoveredMember(UUID player);
}
