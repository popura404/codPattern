package com.phasetranscrystal.fpsmatch.core;

import com.mojang.datafixers.util.Function3;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMSaveDataEvent;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMapEvent;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "codpattern", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FPSMCore {
    private static FPSMCore INSTANCE;

    private final String archiveName;
    private final Map<String, List<BaseMap>> games = new LinkedHashMap<>();
    private final Map<String, Function3<ServerLevel, String, AreaData, BaseMap>> registry = new LinkedHashMap<>();
    private final FPSMDataManager fpsmDataManager;

    private FPSMCore(String archiveName) {
        this.archiveName = archiveName;
        this.fpsmDataManager = new FPSMDataManager(archiveName);
    }

    public static FPSMCore getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("FPSM core not initialized");
        }
        return INSTANCE;
    }

    public static boolean initialized() {
        return INSTANCE != null;
    }

    public Optional<BaseMap> getMapByPlayer(Player player) {
        for (List<BaseMap> maps : games.values()) {
            for (BaseMap map : maps) {
                if (map.checkGameHasPlayer(player)) {
                    return Optional.of(map);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<BaseMap> getMapByPlayerWithSpec(Player player) {
        for (List<BaseMap> maps : games.values()) {
            for (BaseMap map : maps) {
                if (map.checkGameHasPlayer(player) || map.checkSpecHasPlayer(player)) {
                    return Optional.of(map);
                }
            }
        }
        return Optional.empty();
    }

    public void registerMap(String type, BaseMap map) {
        games.computeIfAbsent(type, key -> new ArrayList<>()).add(map);
    }

    public Optional<BaseMap> getMapByName(String name) {
        return games.values().stream()
                .flatMap(List::stream)
                .filter(map -> map.getMapName().equals(name))
                .findFirst();
    }

    public void registerGameType(String typeName, Function3<ServerLevel, String, AreaData, BaseMap> mapFactory) {
        registry.put(typeName, mapFactory);
        games.computeIfAbsent(typeName, key -> new ArrayList<>());
    }

    public Map<String, List<BaseMap>> getAllMaps() {
        return games;
    }

    public void onServerTick() {
        for (List<BaseMap> maps : games.values()) {
            for (BaseMap map : maps) {
                try {
                    map.mapTick();
                } catch (Exception e) {
                    map.resetGame();
                }
            }
        }
    }

    public static void checkAndLeaveTeam(ServerPlayer player) {
        if (!initialized()) {
            return;
        }
        getInstance().getMapByPlayerWithSpec(player).ifPresent(map -> map.leave(player));
    }

    public MinecraftServer getServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    public Optional<ServerPlayer> getPlayerByUUID(UUID uuid) {
        MinecraftServer server = getServer();
        if (server == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(server.getPlayerList().getPlayer(uuid));
    }

    public FPSMDataManager getFPSMDataManager() {
        return fpsmDataManager;
    }

    @SubscribeEvent
    public static void onServerStartedEvent(ServerStartedEvent event) {
        INSTANCE = new FPSMCore(event.getServer().getWorldData().getLevelName());
        MinecraftForge.EVENT_BUS.post((Event) new RegisterFPSMapEvent(INSTANCE));
        MinecraftForge.EVENT_BUS.post((Event) new RegisterFPSMSaveDataEvent(INSTANCE.fpsmDataManager));
        INSTANCE.fpsmDataManager.readData();
    }

    @SubscribeEvent
    public static void onServerTickEvent(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && initialized()) {
            getInstance().onServerTick();
        }
    }

    @SubscribeEvent
    public static void onServerStoppingEvent(ServerStoppingEvent event) {
        if (!initialized()) {
            return;
        }
        INSTANCE.fpsmDataManager.saveData();
        INSTANCE.games.clear();
        INSTANCE.registry.clear();
        INSTANCE = null;
    }
}
