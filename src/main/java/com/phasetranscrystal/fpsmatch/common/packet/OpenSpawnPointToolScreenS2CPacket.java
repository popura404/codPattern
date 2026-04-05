package com.phasetranscrystal.fpsmatch.common.packet;

import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
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

    public OpenSpawnPointToolScreenS2CPacket(List<String> availableTypes, String selectedType, List<String> availableMaps,
            String selectedMap, List<String> availableTeams, String selectedTeam, List<String> availableKinds,
            String selectedKind, int selectedIndex,
            List<SpawnPointData> spawnPoints) {
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
                spawnPoints
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
}
