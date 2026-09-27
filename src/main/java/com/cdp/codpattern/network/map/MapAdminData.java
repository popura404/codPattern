package com.cdp.codpattern.network.map;

import com.cdp.codpattern.app.match.management.MapManagementService;
import com.cdp.codpattern.app.match.model.RoomId;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Optional;
import java.util.UUID;

/** Small, bounded values transported to the administrator screen. */
public final class MapAdminData {
    private MapAdminData() { }

    public static void writeRoom(FriendlyByteBuf buf, RoomId room) {
        buf.writeUtf(room.gameType(), 128);
        buf.writeUtf(room.mapName(), 128);
    }

    public static RoomId readRoom(FriendlyByteBuf buf) {
        return RoomId.of(buf.readUtf(128), buf.readUtf(128));
    }

    public record ModeRow(String id, String displayNameKey) {
        public static ModeRow from(MapManagementService.ModeOption option) {
            return new ModeRow(option.id(), option.displayNameKey());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(id, 128);
            buf.writeUtf(displayNameKey, 256);
        }

        public static ModeRow read(FriendlyByteBuf buf) {
            return new ModeRow(buf.readUtf(128), buf.readUtf(256));
        }
    }

    public record MapRow(RoomId roomId, String modeNameKey, String status,
                         boolean canRename, boolean canDelete, String disabledReason) {
        public static MapRow from(MapManagementService.MapSummary summary) {
            return new MapRow(summary.roomId(), summary.modeNameKey(), summary.status(),
                    summary.canRename(), summary.canDelete(), summary.disabledReason());
        }

        public void write(FriendlyByteBuf buf) {
            writeRoom(buf, roomId);
            buf.writeUtf(modeNameKey, 256);
            buf.writeUtf(status, 64);
            buf.writeBoolean(canRename);
            buf.writeBoolean(canDelete);
            buf.writeUtf(disabledReason, 128);
        }

        public static MapRow read(FriendlyByteBuf buf) {
            return new MapRow(readRoom(buf), buf.readUtf(256), buf.readUtf(64),
                    buf.readBoolean(), buf.readBoolean(), buf.readUtf(128));
        }
    }

    public record EndPoint(String dimensionId, BlockPos position, float yaw, float pitch) {
        public SpawnPointData toPoint() {
            var id = net.minecraft.resources.ResourceLocation.tryParse(dimensionId);
            if (id == null) throw new IllegalArgumentException("Invalid dimension");
            return new SpawnPointData(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id),
                    position, yaw, pitch);
        }

        public static EndPoint from(SpawnPointData point) {
            return new EndPoint(point.getDimension().location().toString(), point.getPosition(), point.getYaw(), point.getPitch());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(dimensionId, 128);
            buf.writeInt(position.getX());
            buf.writeInt(position.getY());
            buf.writeInt(position.getZ());
            buf.writeFloat(yaw);
            buf.writeFloat(pitch);
        }

        public static EndPoint read(FriendlyByteBuf buf) {
            return new EndPoint(buf.readUtf(128), new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()), buf.readFloat(), buf.readFloat());
        }
    }

    public record TeleportSettings(RoomId room, Optional<EndPoint> point, Optional<EndPoint> defaultPoint,
                                   EndPoint currentPosition, String revision, boolean editable, String reason,
                                   boolean defaultsAvailable) {
        public static TeleportSettings from(com.cdp.codpattern.app.match.management.EndTeleportService.Settings value) {
            return new TeleportSettings(value.room(), value.point().map(EndPoint::from), value.defaultPoint().map(EndPoint::from),
                    EndPoint.from(value.currentPosition()), value.revision(), value.editable(), value.reason(), value.defaultsAvailable());
        }
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(room != null);
            if (room != null) writeRoom(buf, room);
            buf.writeBoolean(point.isPresent());
            point.ifPresent(value -> value.write(buf));
            buf.writeBoolean(defaultPoint.isPresent());
            defaultPoint.ifPresent(value -> value.write(buf));
            currentPosition.write(buf);
            buf.writeUtf(revision, 128);
            buf.writeBoolean(editable);
            buf.writeUtf(reason, 128);
            buf.writeBoolean(defaultsAvailable);
        }
        public static TeleportSettings read(FriendlyByteBuf buf) {
            RoomId room = buf.readBoolean() ? readRoom(buf) : null;
            Optional<EndPoint> point = buf.readBoolean() ? Optional.of(EndPoint.read(buf)) : Optional.empty();
            Optional<EndPoint> defaults = buf.readBoolean() ? Optional.of(EndPoint.read(buf)) : Optional.empty();
            return new TeleportSettings(room, point, defaults, EndPoint.read(buf), buf.readUtf(128),
                    buf.readBoolean(), buf.readUtf(128), buf.readBoolean());
        }
    }

    public record DetailRow(MapRow summary, String dimensionId, String lifecycleStateKey, BlockPos pos1, BlockPos pos2,
                            int sizeX, int sizeY, int sizeZ, Optional<EndPoint> endPoint,
                            boolean endPointSupported, UUID generation, String revision) {
        public DetailRow {
            endPoint = endPoint == null ? Optional.empty() : endPoint;
        }

        public static DetailRow from(MapManagementService.MapDetail detail) {
            return new DetailRow(MapRow.from(detail.summary()), detail.dimensionId(), detail.lifecycleStateKey(), detail.pos1(), detail.pos2(),
                    detail.sizeX(), detail.sizeY(), detail.sizeZ(), detail.endPoint().map(EndPoint::from),
                    detail.endPointSupported(), detail.generation(), detail.revision());
        }

        public void write(FriendlyByteBuf buf) {
            summary.write(buf);
            buf.writeUtf(dimensionId, 128);
            buf.writeUtf(lifecycleStateKey, 256);
            buf.writeBlockPos(pos1);
            buf.writeBlockPos(pos2);
            buf.writeInt(sizeX);
            buf.writeInt(sizeY);
            buf.writeInt(sizeZ);
            buf.writeBoolean(endPoint.isPresent());
            endPoint.ifPresent(point -> point.write(buf));
            buf.writeBoolean(endPointSupported);
            buf.writeUUID(generation);
            buf.writeUtf(revision, 128);
        }

        public static DetailRow read(FriendlyByteBuf buf) {
            MapRow summary = MapRow.read(buf);
            String dimension = buf.readUtf(128);
            String lifecycle = buf.readUtf(256);
            BlockPos one = buf.readBlockPos();
            BlockPos two = buf.readBlockPos();
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            Optional<EndPoint> end = buf.readBoolean() ? Optional.of(EndPoint.read(buf)) : Optional.empty();
            return new DetailRow(summary, dimension, lifecycle, one, two, x, y, z, end,
                    buf.readBoolean(), buf.readUUID(), buf.readUtf(128));
        }
    }
}
