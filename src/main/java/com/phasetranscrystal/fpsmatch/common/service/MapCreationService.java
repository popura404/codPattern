package com.phasetranscrystal.fpsmatch.common.service;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.compat.fpsmatch.data.CodMapPersistence;
import com.mojang.datafixers.util.Function3;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

public final class MapCreationService {
    private static final MapCreationService INSTANCE = new MapCreationService();

    public static MapCreationService instance() {
        return INSTANCE;
    }

    private MapCreationService() {
    }

    public Result createMap(ServerPlayer player, CreateRequest request) {
        CreateRequest resolved = request == null
                ? new CreateRequest("", "", null, null)
                : request;
        return createMap(player, resolved.selectedType(), resolved.draftMapName(), resolved.pos1(), resolved.pos2());
    }

    public Result createMap(ServerPlayer player, String selectedType, String draftMapName, BlockPos pos1, BlockPos pos2) {
        String type = GameModeRegistry.canonicalize(selectedType);
        FPSMCore core = FPSMCore.getInstance();
        if (!core.checkGameType(type)) {
            return Result.failure("invalid_type", "message.fpsm.map_creator_tool.invalid_type");
        }

        String mapName = draftMapName == null ? "" : draftMapName.trim();
        if (mapName.isEmpty()) {
            return Result.failure("invalid_name", "message.fpsm.map_creator_tool.invalid_name");
        }

        Optional<AreaData> area = createArea(pos1, pos2);
        if (area.isEmpty()) {
            return Result.failure("invalid_area", "message.fpsm.map_creator_tool.invalid_area");
        }

        if (core.isRegistered(type, mapName)) {
            return Result.failure("duplicate_map", "message.fpsm.map_creator_tool.duplicate_map", mapName);
        }

        Function3<ServerLevel, String, AreaData, BaseMap> factory = core.getPreBuildGame(type);
        if (factory == null) {
            return Result.failure("invalid_type", "message.fpsm.map_creator_tool.invalid_type");
        }

        BaseMap newMap = factory.apply(player.serverLevel(), mapName, area.get());
        core.registerMap(type, newMap);
        try {
            CodMapPersistence.saveMapOrRollback(newMap, () -> core.unregisterMap(newMap));
        } catch (RuntimeException e) {
            return Result.failure("save_failed_rolled_back", "message.codpattern.map.create_save_failed_rollback", type, mapName);
        }

        return Result.success(type, mapName, newMap, "commands.fpsm.create.success", mapName);
    }

    private Optional<AreaData> createArea(BlockPos pos1, BlockPos pos2) {
        if (pos1 == null || pos2 == null) {
            return Optional.empty();
        }
        return Optional.of(new AreaData(pos1, pos2));
    }

    public record Result(
            boolean success,
            String code,
            String type,
            String mapName,
            BaseMap map,
            String messageKey,
            List<String> arguments
    ) {
        public static Result success(String type, String mapName, BaseMap map, String messageKey, String... args) {
            return new Result(true, "ok", type, mapName, map, messageKey, List.of(args));
        }

        public static Result failure(String code, String messageKey, String... args) {
            return new Result(false, code, "", "", null, messageKey, List.of(args));
        }
    }

    public record CreateRequest(
            String selectedType,
            String draftMapName,
            BlockPos pos1,
            BlockPos pos2
    ) {
        public CreateRequest {
            selectedType = selectedType == null ? "" : selectedType.trim();
            draftMapName = draftMapName == null ? "" : draftMapName;
        }
    }
}
