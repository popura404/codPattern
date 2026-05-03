package com.cdp.codpattern.app.zombies.map;

import com.cdp.codpattern.app.zombies.map.object.ZombiesAmmoBoxData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesArmorStationData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesInitialSpawnData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesMysteryBoxData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesPowerSwitchData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesSodaMachineData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesUltimateMachineData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesWeaponWallData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesWindowData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesZombieSpawnData;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

public record ZombiesMapObjects(
        List<ZombiesInitialSpawnData> initialSpawns,
        List<ZombiesZombieSpawnData> zombieSpawns,
        List<ZombiesBarrierData> barriers,
        List<ZombiesWeaponWallData> weaponWalls,
        List<ZombiesAmmoBoxData> ammoBoxes,
        List<ZombiesArmorStationData> armorStations,
        Optional<ZombiesPowerSwitchData> powerSwitch,
        List<ZombiesSodaMachineData> sodaMachines,
        List<ZombiesUltimateMachineData> ultimateMachines,
        List<ZombiesMysteryBoxData> mysteryBoxes,
        List<ZombiesWindowData> windows
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
            ZombiesWeaponWallData.CODEC.listOf().optionalFieldOf("weaponWalls", List.of()).forGetter(ZombiesMapObjects::weaponWalls),
            ZombiesAmmoBoxData.CODEC.listOf().optionalFieldOf("ammoBoxes", List.of()).forGetter(ZombiesMapObjects::ammoBoxes),
            ZombiesArmorStationData.CODEC.listOf().optionalFieldOf("armorStations", List.of()).forGetter(ZombiesMapObjects::armorStations),
            ZombiesPowerSwitchData.CODEC.optionalFieldOf("powerSwitch").forGetter(ZombiesMapObjects::powerSwitch),
            ZombiesSodaMachineData.CODEC.listOf().optionalFieldOf("sodaMachines", List.of()).forGetter(ZombiesMapObjects::sodaMachines),
            ZombiesUltimateMachineData.CODEC.listOf().optionalFieldOf("ultimateMachines", List.of()).forGetter(ZombiesMapObjects::ultimateMachines),
            ZombiesMysteryBoxData.CODEC.listOf().optionalFieldOf("mysteryBoxes", List.of()).forGetter(ZombiesMapObjects::mysteryBoxes),
            ZombiesWindowData.CODEC.listOf().optionalFieldOf("windows", List.of()).forGetter(ZombiesMapObjects::windows)
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
}
