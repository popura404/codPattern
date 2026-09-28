package com.cdp.codpattern.app.match.management;

import com.cdp.codpattern.app.match.ModeRoomBackedMap;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchemas;
import com.cdp.codpattern.app.match.editor.ModeObjectData;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeMapEditPort;
import com.cdp.codpattern.app.match.runtime.termination.RoomTerminationService;
import com.cdp.codpattern.compat.fpsmatch.data.CodMapPersistence;
import com.cdp.codpattern.config.storage.MapDefaultsStore;
import com.cdp.codpattern.config.storage.ServerMapStorage;
import com.mojang.serialization.JsonOps;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import java.util.Optional;

/** Configuration editing only. Runtime teleport destinations remain map-owned. */
public final class EndTeleportService {
    private EndTeleportService() { }
    public record Settings(RoomId room, Optional<SpawnPointData> point, Optional<SpawnPointData> defaultPoint,
                           SpawnPointData currentPosition, String revision, boolean editable, String reason,
                           boolean defaultsAvailable) { }
    public record Result(String code, Settings settings) { }

    private static void authorize(ServerPlayer player) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Server thread required");
        if (!player.hasPermissions(2)) throw new SecurityException("Administrator permission required");
    }

    public static SpawnPointData currentPosition(ServerPlayer player) {
        authorize(player);
        return new SpawnPointData(player.level().dimension(), player.blockPosition(), Mth.wrapDegrees(player.getYRot()), 0);
    }

    private static Optional<ModeMapEditPort> port(BaseMap map) {
        if (!(map instanceof ModeRoomBackedMap backed) || backed.roomHandle() == null) return Optional.empty();
        return backed.roomHandle().mapEditPort().filter(value -> value.supportsObjectFeature(ModeMapEditorSchemas.MATCH_END_TELEPORT));
    }

    private static Optional<SpawnPointData> point(ModeMapEditPort port) {
        return port.objectFeature(ModeMapEditorSchemas.MATCH_END_TELEPORT).map(ModeObjectData::toSpawnPointData);
    }

    private static void write(ModeMapEditPort port, SpawnPointData point) {
        port.setObjectFeature(ModeMapEditorSchemas.MATCH_END_TELEPORT,
                point == null ? null : ModeObjectData.fromSpawnPointData(ModeMapEditorSchemas.MATCH_END_TELEPORT, point));
        if (!point(port).equals(Optional.ofNullable(point))) throw new IllegalStateException("Mode did not apply end point");
    }

    private static Optional<SpawnPointData> decode(MapDefaultsStore.Snapshot snapshot) {
        return snapshot.point() == null ? Optional.empty() : Optional.of(SpawnPointData.CODEC.parse(JsonOps.INSTANCE, snapshot.point())
                .result().orElseThrow(() -> new MapDefaultsStore.Unavailable(new IllegalArgumentException("Invalid default end point"))));
    }

    /** Call only for newly constructed maps, before their first save. Never during registration/loading. */
    public static void applyCreationDefault(MinecraftServer server, BaseMap map) {
        var target = port(map);
        if (target.isEmpty()) {
            if (ModeMapEditorSchemas.supportsMatchEndTeleport(map.getGameType()))
                throw new IllegalStateException("Mode advertises end teleport without an editor");
            return;
        }
        decode(ServerMapStorage.get(server).defaults().read()).ifPresent(value -> {
            validate(server, value);
            write(target.get(), value);
        });
    }

    public static void registerAndSaveNew(MinecraftServer server, BaseMap map) {
        var core = FPSMCore.getInstance();
        boolean registered = false;
        try {
            applyCreationDefault(server, map);
            core.registerMap(map.getGameType(), map);
            registered = true;
            CodMapPersistence.saveMap(map);
        } catch (RuntimeException failure) {
            if (registered) core.unregisterMap(map);
            map.getMapTeams().retireCreatedScoreboardTeams();
            throw failure;
        }
    }

    public static Settings read(ServerPlayer player, RoomId room) {
        authorize(player);
        var storage = ServerMapStorage.get(player.server);
        Optional<SpawnPointData> defaults = Optional.empty();
        String defaultsRevision = "";
        boolean defaultsAvailable = true;
        try {
            var snapshot = storage.defaults().read();
            defaults = decode(snapshot);
            defaultsRevision = snapshot.revision();
        } catch (MapDefaultsStore.Unavailable failure) {
            if (room == null) throw failure;
            defaultsAvailable = false;
        }
        if (room == null) return new Settings(null, defaults, defaults, currentPosition(player), defaultsRevision, true, "", true);
        var detail = MapManagementService.detail(player.server, room).orElseThrow(() -> new IllegalArgumentException("Missing map"));
        BaseMap map = FPSMCore.getInstance().getMapByTypeWithName(room.gameType(), room.mapName()).orElseThrow();
        String reason = MapManagementService.editBlockedReason(player.server, room, detail.summary().status(),
                RoomTerminationService.get(player.server).progress(room).offlinePending() > 0);
        if (detail.revision().isEmpty() || detail.revision().equals("unsupported") || detail.revision().equals("unpersisted")) reason = "storage_unavailable";
        var target = port(map);
        if (target.isEmpty()) reason = "unsupported";
        return new Settings(room, target.flatMap(EndTeleportService::point), defaults, currentPosition(player),
                detail.revision(), reason.isEmpty(), reason, defaultsAvailable);
    }

    public static Result save(ServerPlayer player, RoomId room, String expectedRevision, SpawnPointData requested) {
        authorize(player);
        validate(player.server, requested);
        SpawnPointData normalized = new SpawnPointData(requested.getDimension(), requested.getPosition(),
                Mth.wrapDegrees(requested.getYaw()), 0);
        Settings before = read(player, room);
        if (!before.editable()) return new Result(before.reason(), before);
        if (!before.revision().equals(expectedRevision)) return new Result("stale", before);
        if (before.point().equals(Optional.of(normalized))) return new Result("unchanged", before);
        var storage = ServerMapStorage.get(player.server);
        if (room == null) {
            var json = SpawnPointData.CODEC.encodeStart(JsonOps.INSTANCE, normalized).result().orElseThrow().getAsJsonObject();
            if (!storage.defaults().save(expectedRevision, json)) return new Result("stale", read(player, null));
        } else {
            BaseMap map = FPSMCore.getInstance().getMapByTypeWithName(room.gameType(), room.mapName()).orElseThrow();
            ModeMapEditPort target = port(map).orElseThrow();
            try (var reservation = storage.beginManagement(room.gameType())) {
                try {
                    write(target, normalized);
                    CodMapPersistence.saveMap(map);
                } catch (RuntimeException failure) {
                    try { write(target, before.point().orElse(null)); }
                    catch (RuntimeException rollback) {
                        reservation.unresolved();
                        failure.addSuppressed(rollback);
                    }
                    throw failure;
                }
            }
            map.syncToClient();
        }
        return new Result("saved", read(player, room));
    }

    public static void validate(MinecraftServer server, SpawnPointData point) {
        if (point == null || !Float.isFinite(point.getYaw()) || !Float.isFinite(point.getPitch()))
            throw new IllegalArgumentException("Invalid end point");
        var level = server.getLevel(point.getDimension());
        if (level == null || !Level.isInSpawnableBounds(point.getPosition())
                || level.isOutsideBuildHeight(point.getPosition()) || !level.getWorldBorder().isWithinBounds(point.getPosition()))
            throw new IllegalArgumentException("End point is outside the destination world");
    }
}
