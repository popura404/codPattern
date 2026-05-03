package com.cdp.codpattern.app.zombies.map;

import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesInitialSpawnData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesZombieSpawnData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

public record ZombiesMapObjects(
        List<ZombiesInitialSpawnData> initialSpawns,
        List<ZombiesZombieSpawnData> zombieSpawns,
        List<ZombiesBarrierData> barriers,
        List<ReservedObjectData> weaponWalls,
        List<ReservedObjectData> ammoBoxes,
        List<ReservedObjectData> armorStations,
        Optional<ReservedObjectData> powerSwitch,
        List<ReservedObjectData> sodaMachines,
        List<ReservedObjectData> ultimateMachines,
        List<ReservedObjectData> mysteryBoxes,
        List<ReservedObjectData> windows
) {
    public static final ZombiesMapObjects EMPTY = new ZombiesMapObjects(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    public static final MapCodec<ZombiesMapObjects> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ZombiesInitialSpawnData.CODEC.listOf().optionalFieldOf("initialSpawns", List.of()).forGetter(ZombiesMapObjects::initialSpawns),
            ZombiesZombieSpawnData.CODEC.listOf().optionalFieldOf("zombieSpawns", List.of()).forGetter(ZombiesMapObjects::zombieSpawns),
            ZombiesBarrierData.CODEC.listOf().optionalFieldOf("barriers", List.of()).forGetter(ZombiesMapObjects::barriers),
            ReservedObjectData.CODEC.listOf().optionalFieldOf("weaponWalls", List.of()).forGetter(ZombiesMapObjects::weaponWalls),
            ReservedObjectData.CODEC.listOf().optionalFieldOf("ammoBoxes", List.of()).forGetter(ZombiesMapObjects::ammoBoxes),
            ReservedObjectData.CODEC.listOf().optionalFieldOf("armorStations", List.of()).forGetter(ZombiesMapObjects::armorStations),
            ReservedObjectData.CODEC.optionalFieldOf("powerSwitch").forGetter(ZombiesMapObjects::powerSwitch),
            ReservedObjectData.CODEC.listOf().optionalFieldOf("sodaMachines", List.of()).forGetter(ZombiesMapObjects::sodaMachines),
            ReservedObjectData.CODEC.listOf().optionalFieldOf("ultimateMachines", List.of()).forGetter(ZombiesMapObjects::ultimateMachines),
            ReservedObjectData.CODEC.listOf().optionalFieldOf("mysteryBoxes", List.of()).forGetter(ZombiesMapObjects::mysteryBoxes),
            ReservedObjectData.CODEC.listOf().optionalFieldOf("windows", List.of()).forGetter(ZombiesMapObjects::windows)
    ).apply(instance, ZombiesMapObjects::new));

    public ZombiesMapObjects {
        initialSpawns = initialSpawns == null ? List.of() : List.copyOf(initialSpawns);
        zombieSpawns = zombieSpawns == null ? List.of() : List.copyOf(zombieSpawns);
        barriers = barriers == null ? List.of() : List.copyOf(barriers);
        weaponWalls = weaponWalls == null ? List.of() : List.copyOf(weaponWalls);
        ammoBoxes = ammoBoxes == null ? List.of() : List.copyOf(ammoBoxes);
        armorStations = armorStations == null ? List.of() : List.copyOf(armorStations);
        powerSwitch = powerSwitch == null ? Optional.empty() : powerSwitch;
        sodaMachines = sodaMachines == null ? List.of() : List.copyOf(sodaMachines);
        ultimateMachines = ultimateMachines == null ? List.of() : List.copyOf(ultimateMachines);
        mysteryBoxes = mysteryBoxes == null ? List.of() : List.copyOf(mysteryBoxes);
        windows = windows == null ? List.of() : List.copyOf(windows);
    }

    public record ReservedObjectData() {
        public static final Codec<ReservedObjectData> CODEC = Codec.unit(new ReservedObjectData());
    }
}
