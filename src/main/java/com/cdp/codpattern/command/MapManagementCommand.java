package com.cdp.codpattern.command;

import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.cdp.codpattern.compat.fpsmatch.data.CodMapPersistence;
import com.cdp.codpattern.compat.fpsmatch.data.CodTacticalTdmMapData;
import com.cdp.codpattern.compat.fpsmatch.data.CodTdmMapData;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import com.phasetranscrystal.fpsmatch.core.data.TeamSpawnProfile;
import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import com.phasetranscrystal.fpsmatch.common.item.MapCreatorTool;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec2;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class MapManagementCommand {
    private static final SuggestionProvider<CommandSourceStack> REGISTERED_TYPE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(registeredGameTypes(), builder);
    private static final SuggestionProvider<CommandSourceStack> MAP_BY_TYPE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(mapNamesForContextType(context), builder);
    private static final SuggestionProvider<CommandSourceStack> ALL_MAP_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(allMapNames(), builder);
    private static final SuggestionProvider<CommandSourceStack> TEAM_SUGGESTIONS =
            (context, builder) -> {
                BaseMap map = resolveMapFromContext(context);
                if (map == null) {
                    return Suggestions.empty();
                }
                List<String> teamNames = map.getMapTeams().getTeams().stream()
                        .map(team -> team.name)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
                return SharedSuggestionProvider.suggest(teamNames, builder);
            };
    private static final SuggestionProvider<CommandSourceStack> SPAWN_KIND_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    List.of(
                            SpawnPointKind.INITIAL.serializedName(),
                            SpawnPointKind.DYNAMIC_CANDIDATE.serializedName()),
                    builder);

    private MapManagementCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("map")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(context -> listTypes(context.getSource()))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(REGISTERED_TYPE_SUGGESTIONS)
                                .executes(context -> listMaps(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "type")))))
                .then(Commands.literal("create")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(REGISTERED_TYPE_SUGGESTIONS)
                                .then(Commands.argument("map", StringArgumentType.string())
                                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                        .executes(context -> createMap(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "type"),
                                                                StringArgumentType.getString(context, "map"),
                                                                BlockPosArgument.getLoadedBlockPos(context, "from"),
                                                                BlockPosArgument.getLoadedBlockPos(context, "to"))))))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(REGISTERED_TYPE_SUGGESTIONS)
                                .then(Commands.argument("map", StringArgumentType.string())
                                        .suggests(MAP_BY_TYPE_SUGGESTIONS)
                                        .executes(context -> deleteMap(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "type"),
                                                StringArgumentType.getString(context, "map"))))))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("list")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(REGISTERED_TYPE_SUGGESTIONS)
                                        .then(Commands.argument("map", StringArgumentType.string())
                                                .suggests(MAP_BY_TYPE_SUGGESTIONS)
                                                .then(Commands.argument("team", StringArgumentType.word())
                                                        .suggests(TEAM_SUGGESTIONS)
                                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                                .suggests(SPAWN_KIND_SUGGESTIONS)
                                                                .executes(context -> listSpawnPoints(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "type"),
                                                                        StringArgumentType.getString(context, "map"),
                                                                        StringArgumentType.getString(context, "team"),
                                                                        StringArgumentType.getString(context, "kind"))))))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(REGISTERED_TYPE_SUGGESTIONS)
                                        .then(Commands.argument("map", StringArgumentType.string())
                                                .suggests(MAP_BY_TYPE_SUGGESTIONS)
                                                .then(Commands.argument("team", StringArgumentType.word())
                                                        .suggests(TEAM_SUGGESTIONS)
                                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                                .suggests(SPAWN_KIND_SUGGESTIONS)
                                                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                                        .executes(context -> addSpawnPoint(
                                                                                context.getSource(),
                                                                                StringArgumentType.getString(context, "type"),
                                                                                StringArgumentType.getString(context, "map"),
                                                                                StringArgumentType.getString(context, "team"),
                                                                                StringArgumentType.getString(context, "kind"),
                                                                                BlockPosArgument.getLoadedBlockPos(context, "pos")))))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(REGISTERED_TYPE_SUGGESTIONS)
                                        .then(Commands.argument("map", StringArgumentType.string())
                                                .suggests(MAP_BY_TYPE_SUGGESTIONS)
                                                .then(Commands.argument("team", StringArgumentType.word())
                                                        .suggests(TEAM_SUGGESTIONS)
                                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                                .suggests(SPAWN_KIND_SUGGESTIONS)
                                                                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                                        .executes(context -> removeSpawnPoint(
                                                                                context.getSource(),
                                                                                StringArgumentType.getString(context, "type"),
                                                                                StringArgumentType.getString(context, "map"),
                                                                                StringArgumentType.getString(context, "team"),
                                                                                StringArgumentType.getString(context, "kind"),
                                                                                IntegerArgumentType.getInteger(context, "index")))))))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(REGISTERED_TYPE_SUGGESTIONS)
                                        .then(Commands.argument("map", StringArgumentType.string())
                                                .suggests(MAP_BY_TYPE_SUGGESTIONS)
                                                .then(Commands.argument("team", StringArgumentType.word())
                                                        .suggests(TEAM_SUGGESTIONS)
                                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                                .suggests(SPAWN_KIND_SUGGESTIONS)
                                                                .executes(context -> clearSpawnPoints(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "type"),
                                                                        StringArgumentType.getString(context, "map"),
                                                                        StringArgumentType.getString(context, "team"),
                                                                        StringArgumentType.getString(context, "kind")))))))))
                .then(Commands.literal("endtp")
                        .then(Commands.literal("show")
                                .then(Commands.argument("map", StringArgumentType.string())
                                        .suggests(ALL_MAP_SUGGESTIONS)
                                        .executes(context -> showMatchEndTeleport(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "map")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("map", StringArgumentType.string())
                                        .suggests(ALL_MAP_SUGGESTIONS)
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> setMatchEndTeleport(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "map"),
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"))))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("map", StringArgumentType.string())
                                        .suggests(ALL_MAP_SUGGESTIONS)
                                        .executes(context -> clearMatchEndTeleport(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "map"))))));
    }

    private static int listTypes(CommandSourceStack source) {
        List<String> types = FPSMCore.getInstance().getGameTypes();
        source.sendSuccess(() -> Component.translatable(
                "command.codpattern.map.list.types",
                String.join(", ", types)), false);
        return types.size();
    }

    private static int listMaps(CommandSourceStack source, String rawType) {
        String type = resolveGameType(source, rawType);
        if (type == null) {
            return 0;
        }
        List<String> maps = FPSMCore.getInstance().getMapNamesWithType(type);
        if (maps.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.codpattern.map.list.none", type), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "command.codpattern.map.list.maps",
                type,
                String.join(", ", maps)), false);
        return maps.size();
    }

    private static int createMap(CommandSourceStack source, String rawType, String mapName, BlockPos from, BlockPos to) {
        String type = resolveGameType(source, rawType);
        if (type == null) {
            return 0;
        }
        if (mapName == null || mapName.isBlank()) {
            source.sendFailure(Component.translatable("message.fpsm.map_creator_tool.invalid_name"));
            return 0;
        }
        FPSMCore core = FPSMCore.getInstance();
        if (core.isRegistered(type, mapName)) {
            source.sendFailure(Component.translatable("message.fpsm.map_creator_tool.duplicate_map", mapName));
            return 0;
        }
        var factory = core.getPreBuildGame(type);
        if (factory == null) {
            source.sendFailure(Component.translatable("message.fpsm.map_creator_tool.invalid_type"));
            return 0;
        }
        BaseMap newMap = factory.apply(source.getLevel(), mapName, new AreaData(from, to));
        core.registerMap(type, newMap);
        try {
            CodMapPersistence.saveMapOrRollback(newMap, () -> core.unregisterMap(newMap));
        } catch (RuntimeException e) {
            source.sendFailure(Component.translatable("message.codpattern.map.create_save_failed_rollback", type, mapName));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.fpsm.create.success", mapName), true);
        return 1;
    }

    private static int deleteMap(CommandSourceStack source, String rawType, String mapName) {
        BaseMap map = requireMap(source, rawType, mapName);
        if (map == null) {
            return 0;
        }

        FPSMDataManager.DeleteStatus deleteStatus = deletePersistedMapData(map.getGameType(), map.getMapName());
        if (deleteStatus == FPSMDataManager.DeleteStatus.FAILED) {
            source.sendFailure(Component.translatable(
                    "command.codpattern.map.delete.save_failed",
                    map.getGameType(),
                    map.getMapName()));
            return 0;
        }

        int removedPlayerCount = removePlayersFromMap(map);
        map.resetGame();
        if (!FPSMCore.getInstance().unregisterMap(map)) {
            source.sendFailure(Component.translatable(
                    "command.codpattern.map.delete.unregister_failed",
                    map.getGameType(),
                    map.getMapName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "command.codpattern.map.delete.success",
                map.getGameType(),
                map.getMapName(),
                removedPlayerCount), true);
        return 1;
    }

    private static int listSpawnPoints(CommandSourceStack source, String rawType, String mapName, String teamName, String rawKind) {
        BaseMap map = requireMap(source, rawType, mapName);
        if (map == null) {
            return 0;
        }
        BaseTeam team = requireTeam(source, map, teamName);
        if (team == null) {
            return 0;
        }
        SpawnPointKind kind = resolveSpawnKind(source, rawKind);
        if (kind == null) {
            return 0;
        }
        List<SpawnPointData> spawnPoints = team.getSpawnPointsData(kind);
        source.sendSuccess(() -> Component.translatable(
                "command.codpattern.map.spawn.list.header",
                map.getGameType(),
                map.getMapName(),
                team.name,
                kind.serializedName(),
                spawnPoints.size()), false);
        if (spawnPoints.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.codpattern.map.spawn.list.none"), false);
            return 0;
        }
        for (int i = 0; i < spawnPoints.size(); i++) {
            SpawnPointData point = spawnPoints.get(i);
            int index = i + 1;
            source.sendSuccess(() -> Component.translatable(
                    "command.codpattern.map.spawn.list.entry",
                    index,
                    point.getDimension().location(),
                    point.getX(),
                    point.getY(),
                    point.getZ(),
                    formatAngle(point.getYaw()),
                    formatAngle(point.getPitch())), false);
        }
        return spawnPoints.size();
    }

    private static int addSpawnPoint(CommandSourceStack source, String rawType, String mapName, String teamName,
            String rawKind, BlockPos pos) {
        BaseMap map = requireMap(source, rawType, mapName);
        if (map == null) {
            return 0;
        }
        if (!validateMapPosition(source, map, pos)) {
            return 0;
        }
        BaseTeam team = requireTeam(source, map, teamName);
        if (team == null) {
            return 0;
        }
        SpawnPointKind kind = resolveSpawnKind(source, rawKind);
        if (kind == null) {
            return 0;
        }
        TeamSpawnProfile previousSpawnProfile = team.getSpawnProfile();

        SpawnPointData spawnPoint = new SpawnPointData(
                source.getLevel().dimension(),
                pos,
                currentYaw(source),
                currentPitch(source),
                kind);
        if (!team.addSpawnPointDataIfAbsent(spawnPoint)) {
            source.sendFailure(Component.translatable("message.fpsm.spawn_point_tool.duplicate"));
            return 0;
        }
        if (map.isStart && kind == SpawnPointKind.INITIAL) {
            team.assignNextSpawnPoints(SpawnPointKind.INITIAL);
        }
        try {
            CodMapPersistence.saveMapOrRollback(map, () -> CodMapPersistence.restoreSpawnProfile(map, team, previousSpawnProfile));
        } catch (RuntimeException e) {
            source.sendFailure(Component.translatable("message.codpattern.map.save_failed", map.getGameType(), map.getMapName()));
            return 0;
        }
        map.syncToClient();
        source.sendSuccess(() -> Component.translatable(
                "message.fpsm.spawn_point_tool.added",
                MapCreatorTool.formatPos(pos)), true);
        return 1;
    }

    private static int removeSpawnPoint(CommandSourceStack source, String rawType, String mapName, String teamName,
            String rawKind, int oneBasedIndex) {
        BaseMap map = requireMap(source, rawType, mapName);
        if (map == null) {
            return 0;
        }
        BaseTeam team = requireTeam(source, map, teamName);
        if (team == null) {
            return 0;
        }
        SpawnPointKind kind = resolveSpawnKind(source, rawKind);
        if (kind == null) {
            return 0;
        }
        TeamSpawnProfile previousSpawnProfile = team.getSpawnProfile();

        int zeroBasedIndex = oneBasedIndex - 1;
        Optional<SpawnPointData> removed = team.removeSpawnPointData(kind, zeroBasedIndex);
        if (removed.isEmpty()) {
            source.sendFailure(Component.translatable("command.codpattern.map.spawn.invalid_index", oneBasedIndex));
            return 0;
        }
        team.clearPlayerSpawnPointAssignments();
        if (kind == SpawnPointKind.INITIAL && !team.getSpawnPointsData(kind).isEmpty()) {
            team.assignNextSpawnPoints(SpawnPointKind.INITIAL);
        }
        try {
            CodMapPersistence.saveMapOrRollback(map, () -> CodMapPersistence.restoreSpawnProfile(map, team, previousSpawnProfile));
        } catch (RuntimeException e) {
            source.sendFailure(Component.translatable("message.codpattern.map.save_failed", map.getGameType(), map.getMapName()));
            return 0;
        }
        map.syncToClient();
        SpawnPointData point = removed.get();
        source.sendSuccess(() -> Component.translatable(
                "command.codpattern.map.spawn.removed",
                kind.serializedName(),
                oneBasedIndex,
                MapCreatorTool.formatPos(point.getPosition())), true);
        return 1;
    }

    private static int clearSpawnPoints(CommandSourceStack source, String rawType, String mapName, String teamName,
            String rawKind) {
        BaseMap map = requireMap(source, rawType, mapName);
        if (map == null) {
            return 0;
        }
        BaseTeam team = requireTeam(source, map, teamName);
        if (team == null) {
            return 0;
        }
        SpawnPointKind kind = resolveSpawnKind(source, rawKind);
        if (kind == null) {
            return 0;
        }
        TeamSpawnProfile previousSpawnProfile = team.getSpawnProfile();

        int removedCount = team.getSpawnPointsData(kind).size();
        team.resetSpawnPointData(kind);
        team.clearPlayerSpawnPointAssignments();
        try {
            CodMapPersistence.saveMapOrRollback(map, () -> CodMapPersistence.restoreSpawnProfile(map, team, previousSpawnProfile));
        } catch (RuntimeException e) {
            source.sendFailure(Component.translatable("message.codpattern.map.save_failed", map.getGameType(), map.getMapName()));
            return 0;
        }
        map.syncToClient();
        source.sendSuccess(() -> Component.translatable(
                "command.codpattern.map.spawn.cleared",
                team.name,
                kind.serializedName(),
                removedCount), true);
        return removedCount;
    }

    private static int showMatchEndTeleport(CommandSourceStack source, String mapName) {
        BaseMap map = requireUniqueMap(source, mapName);
        if (map == null) {
            return 0;
        }
        Optional<SpawnPointData> point = readMatchEndTeleportPoint(map);
        if (point.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.codpattern.map.endtp.none", map.getMapName()), false);
            return 0;
        }
        SpawnPointData data = point.get();
        source.sendSuccess(() -> Component.translatable(
                "command.codpattern.map.endtp.show",
                map.getMapName(),
                data.getDimension().location(),
                MapCreatorTool.formatPos(data.getPosition()),
                formatAngle(data.getYaw()),
                formatAngle(data.getPitch())), false);
        return 1;
    }

    private static int setMatchEndTeleport(CommandSourceStack source, String mapName, BlockPos pos) {
        BaseMap map = requireUniqueMap(source, mapName);
        if (map == null) {
            return 0;
        }
        if (!validateMapPosition(source, map, pos)) {
            return 0;
        }
        SpawnPointData point = new SpawnPointData(
                source.getLevel().dimension(),
                pos,
                currentYaw(source),
                currentPitch(source));
        Optional<SpawnPointData> previousPoint = readMatchEndTeleportPoint(map);
        writeMatchEndTeleportPoint(map, point);
        try {
            CodMapPersistence.saveMapOrRollback(map, () -> writeMatchEndTeleportPoint(map, previousPoint.orElse(null)));
        } catch (RuntimeException e) {
            source.sendFailure(Component.translatable("message.codpattern.map.save_failed", map.getGameType(), map.getMapName()));
            return 0;
        }
        map.syncToClient();
        source.sendSuccess(() -> Component.translatable(
                "command.codpattern.map.endtp.set",
                map.getMapName(),
                MapCreatorTool.formatPos(pos)), true);
        return 1;
    }

    private static int clearMatchEndTeleport(CommandSourceStack source, String mapName) {
        BaseMap map = requireUniqueMap(source, mapName);
        if (map == null) {
            return 0;
        }
        Optional<SpawnPointData> previousPoint = readMatchEndTeleportPoint(map);
        writeMatchEndTeleportPoint(map, null);
        try {
            CodMapPersistence.saveMapOrRollback(map, () -> writeMatchEndTeleportPoint(map, previousPoint.orElse(null)));
        } catch (RuntimeException e) {
            source.sendFailure(Component.translatable("message.codpattern.map.save_failed", map.getGameType(), map.getMapName()));
            return 0;
        }
        map.syncToClient();
        source.sendSuccess(() -> Component.translatable(
                "command.codpattern.map.endtp.cleared",
                map.getMapName()), true);
        return 1;
    }

    private static String resolveGameType(CommandSourceStack source, String rawType) {
        String type = TdmGameTypes.canonicalize(rawType);
        if (!FPSMCore.getInstance().checkGameType(type)) {
            source.sendFailure(Component.translatable("message.fpsm.map_creator_tool.invalid_type"));
            return null;
        }
        return type;
    }

    private static BaseMap requireMap(CommandSourceStack source, String rawType, String mapName) {
        String type = resolveGameType(source, rawType);
        if (type == null) {
            return null;
        }
        Optional<BaseMap> map = FPSMCore.getInstance().getMapByTypeWithName(type, mapName);
        if (map.isEmpty()) {
            source.sendFailure(Component.translatable("message.fpsm.spawn_point_tool.map_not_found", mapName));
            return null;
        }
        return map.get();
    }

    private static BaseMap requireUniqueMap(CommandSourceStack source, String mapName) {
        List<BaseMap> maps = findMapsByName(mapName);
        if (maps.isEmpty()) {
            source.sendFailure(Component.translatable("message.fpsm.spawn_point_tool.map_not_found", mapName));
            return null;
        }
        if (maps.size() > 1) {
            String matchedTypes = maps.stream()
                    .map(BaseMap::getGameType)
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            source.sendFailure(Component.translatable("command.codpattern.map.ambiguous_map", mapName, matchedTypes));
            return null;
        }
        return maps.get(0);
    }

    private static BaseTeam requireTeam(CommandSourceStack source, BaseMap map, String teamName) {
        Optional<BaseTeam> team = map.getMapTeams().getTeamByName(teamName);
        if (team.isEmpty()) {
            source.sendFailure(Component.translatable("message.fpsm.spawn_point_tool.team_not_found", teamName));
            return null;
        }
        return team.get();
    }

    private static SpawnPointKind resolveSpawnKind(CommandSourceStack source, String rawKind) {
        if (rawKind == null || rawKind.isBlank()) {
            return SpawnPointKind.INITIAL;
        }
        String normalized = rawKind.trim().toUpperCase(Locale.ROOT);
        for (SpawnPointKind kind : SpawnPointKind.values()) {
            if (kind.serializedName().equalsIgnoreCase(normalized)) {
                return kind;
            }
        }
        source.sendFailure(Component.translatable("command.codpattern.map.invalid_kind", rawKind));
        return null;
    }

    private static boolean validateMapPosition(CommandSourceStack source, BaseMap map, BlockPos pos) {
        if (!map.getServerLevel().dimension().equals(source.getLevel().dimension())) {
            source.sendFailure(Component.translatable("message.fpsm.spawn_point_tool.dimension_mismatch"));
            return false;
        }
        if (!map.getMapArea().isBlockPosInArea(pos)) {
            source.sendFailure(Component.translatable("message.fpsm.spawn_point_tool.outside_map"));
            return false;
        }
        return true;
    }

    private static FPSMDataManager.DeleteStatus deletePersistedMapData(String type, String mapName) {
        FPSMDataManager manager = FPSMCore.getInstance().getFPSMDataManager();
        if (TdmGameTypes.isTeamDeathMatch(type)) {
            return manager.deleteData(CodTacticalTdmMapData.MapData.class, mapName);
        }
        return manager.deleteData(CodTdmMapData.MapData.class, mapName);
    }

    private static int removePlayersFromMap(BaseMap map) {
        int removedCount = 0;
        List<UUID> joinedPlayers = List.copyOf(map.getMapTeams().getJoinedPlayersWithSpec());
        for (UUID playerId : joinedPlayers) {
            Optional<ServerPlayer> player = FPSMCore.getInstance().getPlayerByUUID(playerId);
            if (player.isEmpty()) {
                continue;
            }
            map.leave(player.get());
            removedCount++;
        }
        return removedCount;
    }

    private static Optional<SpawnPointData> readMatchEndTeleportPoint(BaseMap map) {
        if (map instanceof com.cdp.codpattern.compat.fpsmatch.map.CodTacticalTdmMap tacticalMap) {
            return com.cdp.codpattern.compat.fpsmatch.map.CodTacticalTdmMapAccess.readPort(tacticalMap)
                    .matchEndTeleportPoint();
        }
        if (map instanceof com.cdp.codpattern.compat.fpsmatch.map.CodTdmMap tdmMap) {
            return com.cdp.codpattern.compat.fpsmatch.map.CodTdmMapAccess.readPort(tdmMap)
                    .matchEndTeleportPoint();
        }
        return Optional.empty();
    }

    private static void writeMatchEndTeleportPoint(BaseMap map, SpawnPointData point) {
        if (map instanceof com.cdp.codpattern.compat.fpsmatch.map.CodTacticalTdmMap tacticalMap) {
            com.cdp.codpattern.compat.fpsmatch.map.CodTacticalTdmMapAccess.actionPort(tacticalMap)
                    .setMatchEndTeleportPoint(point);
            return;
        }
        if (map instanceof com.cdp.codpattern.compat.fpsmatch.map.CodTdmMap tdmMap) {
            com.cdp.codpattern.compat.fpsmatch.map.CodTdmMapAccess.actionPort(tdmMap)
                    .setMatchEndTeleportPoint(point);
        }
    }

    private static float currentYaw(CommandSourceStack source) {
        Vec2 rotation = source.getRotation();
        return rotation == null ? 0.0F : rotation.y;
    }

    private static float currentPitch(CommandSourceStack source) {
        Vec2 rotation = source.getRotation();
        return rotation == null ? 0.0F : rotation.x;
    }

    private static String formatAngle(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static List<String> registeredGameTypes() {
        return FPSMCore.getInstance().getGameTypes().stream()
                .map(TdmGameTypes::canonicalize)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static List<String> mapNamesForContextType(CommandContext<CommandSourceStack> context) {
        try {
            String rawType = StringArgumentType.getString(context, "type");
            String type = TdmGameTypes.canonicalize(rawType);
            if (!FPSMCore.getInstance().checkGameType(type)) {
                return List.of();
            }
            return FPSMCore.getInstance().getMapNamesWithType(type).stream()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
    }

    private static List<String> allMapNames() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(FPSMCore.getInstance().getMapNames());
        return List.copyOf(names);
    }

    private static BaseMap resolveMapFromContext(CommandContext<CommandSourceStack> context) {
        try {
            String rawType = StringArgumentType.getString(context, "type");
            String mapName = StringArgumentType.getString(context, "map");
            String type = TdmGameTypes.canonicalize(rawType);
            if (!FPSMCore.getInstance().checkGameType(type)) {
                return null;
            }
            return FPSMCore.getInstance().getMapByTypeWithName(type, mapName).orElse(null);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static List<BaseMap> findMapsByName(String mapName) {
        if (mapName == null || mapName.isBlank()) {
            return List.of();
        }
        List<BaseMap> matches = new ArrayList<>();
        FPSMCore.getInstance().getAllMaps().values().forEach(maps -> maps.stream()
                .filter(map -> mapName.equals(map.getMapName()))
                .forEach(matches::add));
        return matches;
    }
}
