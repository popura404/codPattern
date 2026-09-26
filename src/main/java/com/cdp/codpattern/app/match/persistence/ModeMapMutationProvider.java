package com.cdp.codpattern.app.match.persistence;

import com.google.gson.JsonObject;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerLevel;

/**
 * Explicit opt-in for lossless map identity changes. The JSON definition must
 * contain every field this mode persists; the management layer never builds a
 * definition from display-only room details. Opting in also declares that all
 * mode-owned files live in the registered map directory and construction uses
 * MapTeams for owned scoreboard resources. Other external resources require a
 * richer adapter and must not opt into this contract.
 */
public interface ModeMapMutationProvider extends ModeMapPersistenceProvider {
    /** Returns a detached, codec-validated definition of the active map. */
    JsonObject captureDefinition(BaseMap map);

    /** Validates and constructs a replacement from the definition and new name. */
    BaseMap createRenamed(ServerLevel level, JsonObject definition, String newName);

    /** Additional mode-owned cleanup evidence, including identities with no loaded map. */
    default boolean hasPendingResources(net.minecraft.server.MinecraftServer server,
                                        com.cdp.codpattern.app.match.model.RoomId room) { return false; }

    /** Releases resources created exclusively for this map instance. */
    void retire(BaseMap map);
}
