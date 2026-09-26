package com.cdp.codpattern.network.map;

import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.network.handler.ClientPacketBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** One correlated response for a map-management request. */
public record MapAdminResponsePacket(
        MapAdminRequestPacket.Operation operation, UUID session, long requestId,
        Code code, String result, int offset, int total, int fingerprint, int errorCount,
        List<MapAdminData.ModeRow> modes, List<MapAdminData.MapRow> maps,
        MapAdminData.DetailRow detail, RoomId newRoom, int onlinePending, int offlinePending,
        int entitiesPending) {
    public static final int PAGE_SIZE = 32;
    public static final int MAX_MODES = 128;

    public enum Code { OK, DENIED, MISSING, STALE, RATE_LIMIT, PARTIAL, ERROR }

    public MapAdminResponsePacket {
        result = result == null ? "" : result;
        modes = modes == null ? List.of() : List.copyOf(modes);
        maps = maps == null ? List.of() : List.copyOf(maps);
    }

    public static MapAdminResponsePacket status(MapAdminRequestPacket request, Code code, String result) {
        return new MapAdminResponsePacket(request.operation(), request.session(), request.requestId(), code, result,
                0, 0, 0, 0, List.of(), List.of(), null, null, 0, 0, 0);
    }

    public static MapAdminResponsePacket list(MapAdminRequestPacket request, List<MapAdminData.ModeRow> modes,
                                               List<MapAdminData.MapRow> maps, int total, int fingerprint,
                                               int errorCount) {
        return new MapAdminResponsePacket(request.operation(), request.session(), request.requestId(),
                errorCount > 0 ? Code.PARTIAL : Code.OK, "", request.offset(), total, fingerprint,
                errorCount, modes, maps, null, null, 0, 0, 0);
    }

    public static MapAdminResponsePacket detail(MapAdminRequestPacket request, MapAdminData.DetailRow detail) {
        return new MapAdminResponsePacket(request.operation(), request.session(), request.requestId(),
                Code.OK, "", 0, 0, 0, 0, List.of(), List.of(), detail, null, 0, 0, 0);
    }

    public static MapAdminResponsePacket action(MapAdminRequestPacket request, Code code, String result,
                                                RoomId newRoom, int onlinePending, int offlinePending,
                                                int entitiesPending) {
        return new MapAdminResponsePacket(request.operation(), request.session(), request.requestId(), code,
                result, 0, 0, 0, 0, List.of(), List.of(), null, newRoom,
                onlinePending, offlinePending, entitiesPending);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(operation);
        buf.writeUUID(session);
        buf.writeLong(requestId);
        buf.writeEnum(code);
        buf.writeUtf(result, 128);
        buf.writeInt(offset);
        buf.writeInt(total);
        buf.writeInt(fingerprint);
        buf.writeInt(errorCount);
        buf.writeVarInt(modes.size());
        modes.forEach(mode -> mode.write(buf));
        buf.writeVarInt(maps.size());
        maps.forEach(map -> map.write(buf));
        buf.writeBoolean(detail != null);
        if (detail != null) detail.write(buf);
        buf.writeBoolean(newRoom != null);
        if (newRoom != null) MapAdminData.writeRoom(buf, newRoom);
        buf.writeInt(onlinePending);
        buf.writeInt(offlinePending);
        buf.writeInt(entitiesPending);
    }

    public static MapAdminResponsePacket decode(FriendlyByteBuf buf) {
        MapAdminRequestPacket.Operation operation = buf.readEnum(MapAdminRequestPacket.Operation.class);
        UUID session = buf.readUUID();
        long requestId = buf.readLong();
        Code code = buf.readEnum(Code.class);
        String result = buf.readUtf(128);
        int offset = buf.readInt();
        int total = buf.readInt();
        int fingerprint = buf.readInt();
        int errorCount = buf.readInt();
        int modeCount = buf.readVarInt();
        if (modeCount < 0 || modeCount > MAX_MODES) throw new IllegalArgumentException("Invalid mode count");
        List<MapAdminData.ModeRow> modes = new ArrayList<>(modeCount);
        for (int i = 0; i < modeCount; i++) modes.add(MapAdminData.ModeRow.read(buf));
        int mapCount = buf.readVarInt();
        if (mapCount < 0 || mapCount > PAGE_SIZE) throw new IllegalArgumentException("Invalid map page count");
        List<MapAdminData.MapRow> maps = new ArrayList<>(mapCount);
        for (int i = 0; i < mapCount; i++) maps.add(MapAdminData.MapRow.read(buf));
        MapAdminData.DetailRow detail = buf.readBoolean() ? MapAdminData.DetailRow.read(buf) : null;
        RoomId newRoom = buf.readBoolean() ? MapAdminData.readRoom(buf) : null;
        int onlinePending = buf.readInt();
        int offlinePending = buf.readInt();
        int entitiesPending = buf.readInt();
        return new MapAdminResponsePacket(operation, session, requestId, code, result, offset, total,
                fingerprint, errorCount, modes, maps, detail, newRoom,
                onlinePending, offlinePending, entitiesPending);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ClientPacketBridge.mapAdminResponse(this));
        context.get().setPacketHandled(true);
    }
}
