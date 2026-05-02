package com.phasetranscrystal.fpsmatch.common.packet;

import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenSpawnPointToolScreenS2CPacket {
    private final List<String> availableTypes;
    private final String selectedType;
    private final List<String> availableMaps;
    private final String selectedMap;
    private final List<String> availableTeams;
    private final String selectedTeam;
    private final List<String> availableKinds;
    private final String selectedKind;
    private final int selectedIndex;
    private final List<SpawnPointData> spawnPoints;
    private final String editMode;
    private final List<String> availableAreaLayers;
    private final String selectedAreaLayer;
    private final int selectedAreaIndex;
    private final List<AreaData> areas;
    private final BlockPos areaPos1;
    private final BlockPos areaPos2;

    public OpenSpawnPointToolScreenS2CPacket(List<String> availableTypes, String selectedType, List<String> availableMaps,
            String selectedMap, List<String> availableTeams, String selectedTeam, List<String> availableKinds,
            String selectedKind, int selectedIndex,
            List<SpawnPointData> spawnPoints) {
        this(
                availableTypes,
                selectedType,
                availableMaps,
                selectedMap,
                availableTeams,
                selectedTeam,
                availableKinds,
                selectedKind,
                selectedIndex,
                spawnPoints,
                "POINT",
                List.of(),
                "",
                -1,
                List.of(),
                null,
                null);
    }

    public OpenSpawnPointToolScreenS2CPacket(List<String> availableTypes, String selectedType, List<String> availableMaps,
            String selectedMap, List<String> availableTeams, String selectedTeam, List<String> availableKinds,
            String selectedKind, int selectedIndex,
            List<SpawnPointData> spawnPoints, String editMode, List<String> availableAreaLayers,
            String selectedAreaLayer, int selectedAreaIndex, List<AreaData> areas, BlockPos areaPos1, BlockPos areaPos2) {
        this.availableTypes = List.copyOf(availableTypes);
        this.selectedType = selectedType;
        this.availableMaps = List.copyOf(availableMaps);
        this.selectedMap = selectedMap;
        this.availableTeams = List.copyOf(availableTeams);
        this.selectedTeam = selectedTeam;
        this.availableKinds = List.copyOf(availableKinds);
        this.selectedKind = selectedKind;
        this.selectedIndex = selectedIndex;
        this.spawnPoints = List.copyOf(spawnPoints);
        this.editMode = editMode == null ? "POINT" : editMode;
        this.availableAreaLayers = List.copyOf(availableAreaLayers);
        this.selectedAreaLayer = selectedAreaLayer == null ? "" : selectedAreaLayer;
        this.selectedAreaIndex = selectedAreaIndex;
        this.areas = List.copyOf(areas);
        this.areaPos1 = areaPos1;
        this.areaPos2 = areaPos2;
    }

    public List<String> availableTypes() {
        return availableTypes;
    }

    public String selectedType() {
        return selectedType;
    }

    public List<String> availableMaps() {
        return availableMaps;
    }

    public String selectedMap() {
        return selectedMap;
    }

    public List<String> availableTeams() {
        return availableTeams;
    }

    public String selectedTeam() {
        return selectedTeam;
    }

    public List<String> availableKinds() {
        return availableKinds;
    }

    public String selectedKind() {
        return selectedKind;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public List<SpawnPointData> spawnPoints() {
        return spawnPoints;
    }

    public String editMode() {
        return editMode;
    }

    public List<String> availableAreaLayers() {
        return availableAreaLayers;
    }

    public String selectedAreaLayer() {
        return selectedAreaLayer;
    }

    public int selectedAreaIndex() {
        return selectedAreaIndex;
    }

    public List<AreaData> areas() {
        return areas;
    }

    public BlockPos areaPos1() {
        return areaPos1;
    }

    public BlockPos areaPos2() {
        return areaPos2;
    }

    public void encode(FriendlyByteBuf buf) {
        writeStringList(buf, availableTypes);
        buf.writeUtf(selectedType);
        writeStringList(buf, availableMaps);
        buf.writeUtf(selectedMap);
        writeStringList(buf, availableTeams);
        buf.writeUtf(selectedTeam);
        writeStringList(buf, availableKinds);
        buf.writeUtf(selectedKind);
        buf.writeVarInt(selectedIndex);
        buf.writeVarInt(spawnPoints.size());
        for (SpawnPointData point : spawnPoints) {
            buf.writeUtf(point.getDimension().location().toString());
            buf.writeBlockPos(point.getPosition());
            buf.writeFloat(point.getYaw());
            buf.writeFloat(point.getPitch());
            buf.writeUtf(point.getKind().serializedName());
        }
        buf.writeUtf(editMode);
        writeStringList(buf, availableAreaLayers);
        buf.writeUtf(selectedAreaLayer);
        buf.writeVarInt(selectedAreaIndex);
        buf.writeVarInt(areas.size());
        for (AreaData area : areas) {
            buf.writeBlockPos(area.pos1());
            buf.writeBlockPos(area.pos2());
        }
        writeNullableBlockPos(buf, areaPos1);
        writeNullableBlockPos(buf, areaPos2);
    }

    public static OpenSpawnPointToolScreenS2CPacket decode(FriendlyByteBuf buf) {
        List<String> availableTypes = readStringList(buf);
        String selectedType = buf.readUtf();
        List<String> availableMaps = readStringList(buf);
        String selectedMap = buf.readUtf();
        List<String> availableTeams = readStringList(buf);
        String selectedTeam = buf.readUtf();
        List<String> availableKinds = readStringList(buf);
        String selectedKind = buf.readUtf();
        int selectedIndex = buf.readVarInt();
        int size = buf.readVarInt();
        List<SpawnPointData> spawnPoints = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String dimension = buf.readUtf();
            ResourceLocation location = ResourceLocation.tryParse(dimension);
            if (location == null) {
                location = Level.OVERWORLD.location();
            }
            spawnPoints.add(new SpawnPointData(
                    ResourceKey.create(Registries.DIMENSION, location),
                    buf.readBlockPos(),
                    buf.readFloat(),
                    buf.readFloat(),
                    com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind.fromSerializedName(buf.readUtf())
            ));
        }
        String editMode = buf.readUtf();
        List<String> availableAreaLayers = readStringList(buf);
        String selectedAreaLayer = buf.readUtf();
        int selectedAreaIndex = buf.readVarInt();
        int areaCount = buf.readVarInt();
        List<AreaData> areas = new ArrayList<>(areaCount);
        for (int i = 0; i < areaCount; i++) {
            areas.add(new AreaData(buf.readBlockPos(), buf.readBlockPos()));
        }
        BlockPos areaPos1 = readNullableBlockPos(buf);
        BlockPos areaPos2 = readNullableBlockPos(buf);
        return new OpenSpawnPointToolScreenS2CPacket(
                availableTypes,
                selectedType,
                availableMaps,
                selectedMap,
                availableTeams,
                selectedTeam,
                availableKinds,
                selectedKind,
                selectedIndex,
                spawnPoints,
                editMode,
                availableAreaLayers,
                selectedAreaLayer,
                selectedAreaIndex,
                areas,
                areaPos1,
                areaPos2
        );
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> FpsmClientPacketBridge.openSpawnPointToolScreen(this));
        ctx.get().setPacketHandled(true);
    }

    private static void writeStringList(FriendlyByteBuf buf, List<String> values) {
        buf.writeVarInt(values.size());
        for (String value : values) {
            buf.writeUtf(value);
        }
    }

    private static List<String> readStringList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buf.readUtf());
        }
        return values;
    }

    private static void writeNullableBlockPos(FriendlyByteBuf buf, BlockPos pos) {
        buf.writeBoolean(pos != null);
        if (pos != null) {
            buf.writeBlockPos(pos);
        }
    }

    private static BlockPos readNullableBlockPos(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readBlockPos() : null;
    }
}
