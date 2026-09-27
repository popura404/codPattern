package com.cdp.codpattern.network.map;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.match.management.MapManagementService;
import com.cdp.codpattern.app.match.management.MapMutationService;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.runtime.termination.ForceEndCoordinator;
import com.cdp.codpattern.app.match.runtime.termination.RoomTerminationService;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Administrator requests. The trusted sender supplies identity and permission; all edits are validated on the server. */
public record MapAdminRequestPacket(Operation operation, UUID session, long requestId, int offset,
                                    int expectedFingerprint, RoomId room, String expectedRevision,
                                    String newName, UUID generation, MapAdminData.EndPoint endPoint) {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Window> REQUEST_WINDOWS = new HashMap<>();
    private static final int REQUESTS_PER_SECOND = 40;
    private record RequestKey(UUID player, UUID session, long id) { }
    private record Cached(MapAdminRequestPacket request, MapAdminResponsePacket response, long created) { }
    private static final Map<net.minecraft.server.MinecraftServer, java.util.LinkedHashMap<RequestKey, Cached>> RESULTS
            = new java.util.WeakHashMap<>();

    public enum Operation { LIST, DETAIL, RENAME, DELETE, FORCE_END, END_STATUS,
        DEFAULTS, END_POINT_DETAIL, CURRENT_POSITION, SAVE_DEFAULTS, SAVE_END_POINT }

    public MapAdminRequestPacket(Operation operation, UUID session, long requestId, int offset,
                                 int fingerprint, RoomId room, String revision, String name, UUID generation) {
        this(operation, session, requestId, offset, fingerprint, room, revision, name, generation, null);
    }

    public static MapAdminRequestPacket teleport(Operation operation, UUID session, long requestId, RoomId room,
                                                 String revision, MapAdminData.EndPoint point) {
        return new MapAdminRequestPacket(operation, session, requestId, 0, 0, room, revision, "", null, point);
    }


    private record Window(long startedAt, int count) { }

    public MapAdminRequestPacket {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(session, "session");
        expectedRevision = expectedRevision == null ? "" : expectedRevision;
        newName = newName == null ? "" : newName;
    }

    public static MapAdminRequestPacket list(UUID session, long requestId, int offset, int fingerprint) {
        return new MapAdminRequestPacket(Operation.LIST, session, requestId, offset, fingerprint,
                null, "", "", null);
    }

    public static MapAdminRequestPacket detail(UUID session, long requestId, RoomId room) {
        return new MapAdminRequestPacket(Operation.DETAIL, session, requestId, 0, 0,
                room, "", "", null);
    }

    public static MapAdminRequestPacket rename(UUID session, long requestId, RoomId room,
                                                String revision, String newName) {
        return new MapAdminRequestPacket(Operation.RENAME, session, requestId, 0, 0,
                room, revision, newName, null);
    }

    public static MapAdminRequestPacket delete(UUID session, long requestId, RoomId room, String revision) {
        return new MapAdminRequestPacket(Operation.DELETE, session, requestId, 0, 0,
                room, revision, "", null);
    }

    public static MapAdminRequestPacket forceEnd(UUID session, long requestId, RoomId room, UUID generation) {
        return new MapAdminRequestPacket(Operation.FORCE_END, session, requestId, 0, 0,
                room, "", "", generation);
    }

    public static MapAdminRequestPacket endStatus(UUID session, long requestId, RoomId room, UUID generation) {
        return new MapAdminRequestPacket(Operation.END_STATUS, session, requestId, 0, 0,
                room, "", "", generation);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(operation);
        buf.writeUUID(session);
        buf.writeLong(requestId);
        buf.writeInt(offset);
        buf.writeInt(expectedFingerprint);
        buf.writeBoolean(room != null);
        if (room != null) MapAdminData.writeRoom(buf, room);
        buf.writeUtf(expectedRevision, 128);
        buf.writeUtf(newName, 128);
        buf.writeBoolean(generation != null);
        if (generation != null) buf.writeUUID(generation);
        buf.writeBoolean(endPoint != null);
        if (endPoint != null) endPoint.write(buf);
    }

    public static MapAdminRequestPacket decode(FriendlyByteBuf buf) {
        Operation operation = buf.readEnum(Operation.class);
        UUID session = buf.readUUID();
        long requestId = buf.readLong();
        int offset = buf.readInt();
        int fingerprint = buf.readInt();
        RoomId room = buf.readBoolean() ? MapAdminData.readRoom(buf) : null;
        String revision = buf.readUtf(128);
        String name = buf.readUtf(128);
        UUID generation = buf.readBoolean() ? buf.readUUID() : null;
        return new MapAdminRequestPacket(operation, session, requestId, offset, fingerprint,
                room, revision, name, generation, buf.readBoolean() ? MapAdminData.EndPoint.read(buf) : null);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player == null) return;
            ModNetworkChannel.sendToPlayer(process(player), player);
        });
        context.get().setPacketHandled(true);
    }

    /** Server-thread dispatch; actor and permission always come from the trusted sender. */
    public MapAdminResponsePacket process(ServerPlayer player) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Administrator request requires server thread");
        MapAdminResponsePacket response;
        if (!player.hasPermissions(2)) {
            response = MapAdminResponsePacket.status(this, MapAdminResponsePacket.Code.DENIED, "permission");
        } else if (!withinRateLimit(player.getUUID())) {
            response = MapAdminResponsePacket.status(this, MapAdminResponsePacket.Code.RATE_LIMIT, "rate_limit");
        } else {
            try {
                response = executeOnce(player);
            } catch (com.cdp.codpattern.config.storage.MapDefaultsStore.Unavailable failure) {
                LOGGER.warn("Map defaults unavailable for administrator request {}", operation, failure);
                response = MapAdminResponsePacket.status(this, MapAdminResponsePacket.Code.ERROR, "defaults_unavailable");
            } catch (SecurityException failure) {
                response = MapAdminResponsePacket.status(this, MapAdminResponsePacket.Code.DENIED, "permission");
            } catch (IllegalArgumentException failure) {
                response = MapAdminResponsePacket.status(this,
                        operation == Operation.SAVE_DEFAULTS || operation == Operation.SAVE_END_POINT
                                ? MapAdminResponsePacket.Code.ERROR : MapAdminResponsePacket.Code.MISSING, "invalid_target");
            } catch (RuntimeException | LinkageError failure) {
                LOGGER.warn("Map administrator request {} failed for {}", operation, player.getGameProfile().getName(), failure);
                response = MapAdminResponsePacket.status(this, MapAdminResponsePacket.Code.ERROR, "server_error");
            }
        }
        return response;
    }

    private MapAdminResponsePacket executeOnce(ServerPlayer player) {
        if (operation != Operation.RENAME && operation != Operation.DELETE
                && operation != Operation.SAVE_DEFAULTS && operation != Operation.SAVE_END_POINT) return execute(player);
        var cache = RESULTS.computeIfAbsent(player.server, ignored -> new java.util.LinkedHashMap<>());
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> now - entry.getValue().created() > 300_000L);
        RequestKey key = new RequestKey(player.getUUID(), session, requestId);
        Cached previous = cache.get(key);
        if (previous != null) {
            return previous.request().equals(this) ? previous.response()
                    : MapAdminResponsePacket.status(this, MapAdminResponsePacket.Code.STALE, "stale_request");
        }
        MapAdminResponsePacket response = execute(player);
        if (cache.size() >= 256) cache.remove(cache.keySet().iterator().next());
        cache.put(key, new Cached(this, response, now));
        return response;
    }

    private MapAdminResponsePacket execute(ServerPlayer player) {
        return switch (operation) {
            case DEFAULTS, END_POINT_DETAIL -> MapAdminResponsePacket.teleport(this, MapAdminResponsePacket.Code.OK, "",
                    MapAdminData.TeleportSettings.from(com.cdp.codpattern.app.match.management.EndTeleportService.read(
                            player, operation == Operation.DEFAULTS ? null : requireRoom())));
            case CURRENT_POSITION -> MapAdminResponsePacket.position(this,
                    MapAdminData.EndPoint.from(com.cdp.codpattern.app.match.management.EndTeleportService.currentPosition(player)));
            case SAVE_DEFAULTS, SAVE_END_POINT -> {
                if (endPoint == null) throw new IllegalArgumentException("End point required");
                var result = com.cdp.codpattern.app.match.management.EndTeleportService.save(player,
                        operation == Operation.SAVE_DEFAULTS ? null : requireRoom(), expectedRevision, endPoint.toPoint());
                var code = switch (result.code()) {
                    case "saved", "unchanged" -> MapAdminResponsePacket.Code.OK;
                    case "stale" -> MapAdminResponsePacket.Code.STALE;
                    default -> MapAdminResponsePacket.Code.ERROR;
                };
                yield MapAdminResponsePacket.teleport(this, code, result.code(), MapAdminData.TeleportSettings.from(result.settings()));
            }
            case LIST -> listPage(player);
            case DETAIL -> MapManagementService.detail(player.server, requireRoom())
                    .map(detail -> MapAdminResponsePacket.detail(this, MapAdminData.DetailRow.from(detail)))
                    .orElseGet(() -> MapAdminResponsePacket.status(this,
                            MapAdminResponsePacket.Code.MISSING, "map_missing"));
            case RENAME -> mutation(MapMutationService.rename(player.server, requireRoom(), expectedRevision, newName));
            case DELETE -> mutation(MapMutationService.delete(player.server, requireRoom(), expectedRevision));
            case FORCE_END -> {
                RoomId target = requireRoom();
                if (!MapManagementService.isRegistered(player.server, target)) {
                    yield MapAdminResponsePacket.status(this, MapAdminResponsePacket.Code.MISSING, "map_missing");
                }
                yield endReport(player,
                        MapManagementService.forceEnd(player.createCommandSourceStack(), target, requireGeneration()));
            }
            case END_STATUS -> {
                RoomId target = requireRoom();
                if (!MapManagementService.isRegistered(player.server, target)) {
                    yield MapAdminResponsePacket.status(this, MapAdminResponsePacket.Code.MISSING, "map_missing");
                }
                var service = RoomTerminationService.get(player.server);
                if (!service.generation(target).equals(requireGeneration())) {
                    yield MapAdminResponsePacket.status(this, MapAdminResponsePacket.Code.STALE, "stale_generation");
                }
                yield endReport(player, service.status(target));
            }
        };
    }

    private MapAdminResponsePacket listPage(ServerPlayer player) {
        if (offset < 0) return MapAdminResponsePacket.status(this, MapAdminResponsePacket.Code.ERROR, "invalid_page");
        var modeOptions = MapManagementService.modes(player.server);
        if (modeOptions.size() > MapAdminResponsePacket.MAX_MODES) {
            return MapAdminResponsePacket.status(this, MapAdminResponsePacket.Code.ERROR, "too_many_modes");
        }
        var listed = MapManagementService.listWithErrors(player.server);
        List<MapManagementService.MapSummary> all = listed.maps();
        int fingerprint = Objects.hash(modeOptions, all, listed.errors());
        if (offset > all.size() || (offset > 0 && expectedFingerprint != fingerprint)) {
            return MapAdminResponsePacket.status(this, MapAdminResponsePacket.Code.STALE, "list_changed");
        }
        int end = Math.min(all.size(), offset + MapAdminResponsePacket.PAGE_SIZE);
        List<MapAdminData.MapRow> page = new ArrayList<>(end - offset);
        for (int i = offset; i < end; i++) page.add(MapAdminData.MapRow.from(all.get(i)));
        return MapAdminResponsePacket.list(this, modeOptions.stream().map(MapAdminData.ModeRow::from).toList(),
                page, all.size(), fingerprint, listed.errors().size());
    }

    private MapAdminResponsePacket mutation(MapMutationService.Result result) {
        String outcome = result.outcome().name();
        MapAdminResponsePacket.Code code = switch (result.outcome()) {
            case RENAMED, DELETED, UNCHANGED -> MapAdminResponsePacket.Code.OK;
            case STALE -> MapAdminResponsePacket.Code.STALE;
            case NOT_FOUND -> MapAdminResponsePacket.Code.MISSING;
            default -> MapAdminResponsePacket.Code.ERROR;
        };
        return MapAdminResponsePacket.action(this, code, outcome, result.newRoom(), 0, 0, 0);
    }

    private MapAdminResponsePacket endReport(ServerPlayer player, ForceEndCoordinator.Report report) {
        var progress = RoomTerminationService.get(player.server).progress(requireRoom());
        MapAdminResponsePacket.Code code = report.outcome() == ForceEndCoordinator.Outcome.STALE
                ? MapAdminResponsePacket.Code.STALE : MapAdminResponsePacket.Code.OK;
        return MapAdminResponsePacket.action(this, code, report.outcome().name(), null,
                saturated(progress.onlinePending()), saturated(progress.offlinePending()),
                saturated(progress.pendingEntities()));
    }

    private RoomId requireRoom() {
        if (room == null) throw new IllegalArgumentException("Map target required");
        return RoomId.of(com.cdp.codpattern.app.match.GameModeRegistry.canonicalize(room.gameType()), room.mapName());
    }

    private UUID requireGeneration() {
        if (generation == null) throw new IllegalArgumentException("Generation required");
        return generation;
    }

    private static int saturated(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0, count);
    }

    private static boolean withinRateLimit(UUID player) {
        long now = System.currentTimeMillis();
        Window prior = REQUEST_WINDOWS.get(player);
        if (prior == null || now - prior.startedAt() >= 1000L) {
            REQUEST_WINDOWS.put(player, new Window(now, 1));
            if (REQUEST_WINDOWS.size() > 256) {
                REQUEST_WINDOWS.entrySet().removeIf(entry -> now - entry.getValue().startedAt() > 60_000L);
            }
            return true;
        }
        if (prior.count() >= REQUESTS_PER_SECOND) return false;
        REQUEST_WINDOWS.put(player, new Window(prior.startedAt(), prior.count() + 1));
        return true;
    }
}
