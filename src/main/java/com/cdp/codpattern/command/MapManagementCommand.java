package com.cdp.codpattern.command;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.ModeRoomBackedMap;
import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchemas;
import com.cdp.codpattern.app.match.editor.ModeObjectData;
import com.cdp.codpattern.app.match.port.ModeMapEditPort;
import com.cdp.codpattern.compat.fpsmatch.data.CodMapPersistence;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.common.item.MapCreatorTool;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class MapManagementCommand {
    private static final int MAP_PERMISSION_LEVEL = 2;
    private static final int END_TELEPORT_PERMISSION_LEVEL = 3;

    private static final SuggestionProvider<CommandSourceStack> REGISTERED_TYPE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(registeredGameTypes(), builder);
    private static final SuggestionProvider<CommandSourceStack> MAP_BY_TYPE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(mapNamesForContextType(context), builder);
    private static final SuggestionProvider<CommandSourceStack> ALL_MAP_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(allMapNames(), builder);

    private MapManagementCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("map")
                .requires(source -> source.hasPermission(MAP_PERMISSION_LEVEL));

        root.then(MapMigrationCommand.buildCommand());

        root.then(Commands.literal("list")
                .executes(context -> listTypes(context.getSource()))
                .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(REGISTERED_TYPE_SUGGESTIONS)
                        .executes(context -> listMaps(
                                context.getSource(),
                                StringArgumentType.getString(context, "type")))));

        root.then(Commands.literal("delete")
                .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(REGISTERED_TYPE_SUGGESTIONS)
                        .then(Commands.argument("map", StringArgumentType.string())
                                .suggests(MAP_BY_TYPE_SUGGESTIONS)
                                .executes(context -> deleteMap(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "type"),
                                        StringArgumentType.getString(context, "map"))))));

        LiteralArgumentBuilder<CommandSourceStack> endtp = Commands.literal("endtp")
                .requires(source -> source.hasPermission(END_TELEPORT_PERMISSION_LEVEL));
        endtp.then(Commands.literal("show")
                .then(Commands.argument("map", StringArgumentType.string())
                        .suggests(ALL_MAP_SUGGESTIONS)
                        .executes(context -> showMatchEndTeleport(
                                context.getSource(),
                                StringArgumentType.getString(context, "map")))));
        endtp.then(Commands.literal("set")
                .executes(context -> setMatchEndTeleportForAllMaps(context.getSource())));
        root.then(endtp);

        return root;
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

    private static int deleteMap(CommandSourceStack source, String rawType, String mapName) {
        BaseMap map = requireMap(source, rawType, mapName);
        if (map == null) {
            return 0;
        }

        var room = com.cdp.codpattern.app.match.runtime.termination.RoomTerminationService.id(map);
        var service = com.cdp.codpattern.app.match.management.MapDeletionCoordinator.get(source.getServer());
        var view = service.submit(source, room,
                com.cdp.codpattern.app.match.management.MapManagementService.revision(source.getServer(), room),
                com.cdp.codpattern.app.match.runtime.termination.RoomTerminationService.get(source.getServer()).generation(room),
                UUID.randomUUID(), 0);
        source.sendSuccess(() -> Component.translatable("screen.codpattern.map_admin.deletion_notification", mapName,
                view.id().toString(), Component.translatable("screen.codpattern.map_admin.deletion." + view.stage().name()),
                Component.translatable("screen.codpattern.map_admin.deletion_reason." + (view.reason().isEmpty() ? "none" : view.reason()))), false);
        return 1;
    }

    private static int showMatchEndTeleport(CommandSourceStack source, String mapName) {
        BaseMap map = requireUniqueMap(source, mapName);
        if (map == null) {
            return 0;
        }
        if (!ModeMapEditorSchemas.supportsMatchEndTeleport(map.getGameType())) {
            source.sendFailure(Component.translatable(
                    "command.codpattern.map.endtp.unsupported_mode",
                    map.getGameType()));
            return 0;
        }
        Optional<SpawnPointData> point = readObjectFeaturePoint(map, ModeMapEditorSchemas.MATCH_END_TELEPORT);
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
                formatAngle(data.getYaw())), false);
        return 1;
    }

    private static int setMatchEndTeleportForAllMaps(CommandSourceStack source) {
        List<BaseMap> maps = FPSMCore.getInstance().getAllMaps().values().stream()
                .flatMap(List::stream)
                .filter(map -> ModeMapEditorSchemas.supportsMatchEndTeleport(map.getGameType()))
                .distinct()
                .toList();
        if (maps.isEmpty()) {
            source.sendFailure(Component.translatable("command.codpattern.map.endtp.set_all.none"));
            return 0;
        }

        BlockPos pos = BlockPos.containing(source.getPosition());
        SpawnPointData point = new SpawnPointData(
                source.getLevel().dimension(),
                pos,
                currentYaw(source),
                0.0F);

        for (BaseMap map : maps) {
            if (com.cdp.codpattern.app.match.management.MapDeletionCoordinator.get(source.getServer())
                    .blocks(com.cdp.codpattern.app.match.runtime.termination.RoomTerminationService.id(map))) {
                source.sendFailure(Component.translatable("screen.codpattern.map_admin.disabled.deletion_pending"));
                return 0;
            }
        }
        Map<BaseMap, SpawnPointData> previousPoints = new LinkedHashMap<>();
        List<BaseMap> savedMaps = new ArrayList<>();
        for (BaseMap map : maps) {
            previousPoints.put(map, readObjectFeaturePoint(map, ModeMapEditorSchemas.MATCH_END_TELEPORT).orElse(null));
            writeObjectFeaturePoint(map, ModeMapEditorSchemas.MATCH_END_TELEPORT, point);
        }

        try {
            for (BaseMap map : maps) {
                CodMapPersistence.saveMap(map);
                savedMaps.add(map);
            }
        } catch (RuntimeException e) {
            previousPoints.forEach((map, previousPoint) ->
                    writeObjectFeaturePoint(map, ModeMapEditorSchemas.MATCH_END_TELEPORT, previousPoint));
            for (BaseMap savedMap : savedMaps) {
                try {
                    CodMapPersistence.saveMap(savedMap);
                } catch (RuntimeException ignored) {
                }
            }
            previousPoints.keySet().forEach(BaseMap::syncToClient);
            source.sendFailure(Component.translatable("message.codpattern.map.save_failed_batch"));
            return 0;
        }

        maps.forEach(BaseMap::syncToClient);
        source.sendSuccess(() -> Component.translatable(
                "command.codpattern.map.endtp.set_all",
                maps.size(),
                MapCreatorTool.formatPos(pos)), true);
        return maps.size();
    }

    private static String resolveGameType(CommandSourceStack source, String rawType) {
        String type = GameModeRegistry.canonicalize(rawType);
        if (!FPSMCore.getInstance().checkGameType(type)) {
            source.sendFailure(Component.translatable("message.fpsm.map_creator_tool.invalid_type"));
            return null;
        }
        return type;
    }

    private static BaseMap requireMap(CommandSourceStack source, String rawType, String mapName) {
        if (!com.cdp.codpattern.config.storage.ServerMapStorage.canUse(GameModeRegistry.canonicalize(rawType))) {
            source.sendFailure(Component.translatable("message.codpattern.storage.locked")); return null;
        }
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

    private static Optional<ModeMapEditPort> mapEditPort(BaseMap map) {
        if (map instanceof ModeRoomBackedMap backedMap) {
            ModeRoomHandle handle = backedMap.roomHandle();
            return handle == null ? Optional.empty() : handle.mapEditPort();
        }
        return Optional.empty();
    }

    private static Optional<SpawnPointData> readObjectFeaturePoint(BaseMap map, String featureKey) {
        return mapEditPort(map)
                .filter(port -> port.supportsObjectFeature(featureKey))
                .flatMap(port -> port.objectFeature(featureKey))
                .map(ModeObjectData::toSpawnPointData);
    }

    private static void writeObjectFeaturePoint(BaseMap map, String featureKey, SpawnPointData point) {
        mapEditPort(map)
                .filter(port -> port.supportsObjectFeature(featureKey))
                .ifPresent(port -> port.setObjectFeature(
                        featureKey,
                        point == null ? null : ModeObjectData.fromSpawnPointData(featureKey, point)));
    }

    private static float currentYaw(CommandSourceStack source) {
        Vec2 rotation = source.getRotation();
        return rotation == null ? 0.0F : rotation.y;
    }

    private static String formatAngle(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static List<String> registeredGameTypes() {
        return FPSMCore.getInstance().getGameTypes().stream()
                .map(GameModeRegistry::canonicalize)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static List<String> mapNamesForContextType(CommandContext<CommandSourceStack> context) {
        try {
            String rawType = StringArgumentType.getString(context, "type");
            String type = GameModeRegistry.canonicalize(rawType);
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
