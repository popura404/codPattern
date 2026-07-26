package com.cdp.codpattern.command;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.ModeRoomBackedMap;
import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchemas;
import com.cdp.codpattern.app.match.model.ModeRuntimeStateSnapshot;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.match.port.ModeMapEditPort;
import com.cdp.codpattern.app.match.port.ModeRoomLifecyclePort;
import com.cdp.codpattern.app.match.port.ModeRoomSummaryPort;
import com.cdp.codpattern.app.match.runtime.ModeEntityOwnershipRegistry;
import com.cdp.codpattern.app.match.runtime.debug.ModeDebugSnapshotContributors;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGateway;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class ModeDebugCommand {
    private static final int DEBUG_PERMISSION_LEVEL = 2;
    private static final SuggestionProvider<CommandSourceStack> ROOM_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(roomKeys(), builder);
    private static final SuggestionProvider<CommandSourceStack> TYPE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(registeredGameTypes(), builder);
    private static final SuggestionProvider<CommandSourceStack> MAP_SUGGESTIONS = ModeDebugCommand::suggestMapsForType;

    private ModeDebugCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("mode")
                .requires(source -> source.hasPermission(DEBUG_PERMISSION_LEVEL))
                .then(Commands.literal("debug")
                        .then(Commands.literal("room")
                                .executes(context -> debugCurrentRoom(context.getSource())))
                        .then(Commands.literal("entities")
                                .then(Commands.argument("room", StringArgumentType.string())
                                        .suggests(ROOM_SUGGESTIONS)
                                        .executes(context -> debugEntities(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "room")))))
                        .then(Commands.literal("clear_entities")
                                .then(Commands.argument("room", StringArgumentType.string())
                                        .suggests(ROOM_SUGGESTIONS)
                                        .executes(context -> clearEntities(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "room")))))
                        .then(Commands.literal("state")
                                .then(Commands.argument("room", StringArgumentType.string())
                                        .suggests(ROOM_SUGGESTIONS)
                                        .executes(context -> debugRuntimeState(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "room")))))
                        .then(Commands.literal("areas")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(TYPE_SUGGESTIONS)
                                        .then(Commands.argument("map", StringArgumentType.string())
                                                .suggests(MAP_SUGGESTIONS)
                                                .executes(context -> debugAreas(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "type"),
                                                        StringArgumentType.getString(context, "map")))))));
    }

    private static int debugCurrentRoom(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<ModeRoomLifecyclePort> lifecyclePort = FpsMatchGatewayProvider.gateway()
                .findPlayerRoomLifecyclePort(player);
        if (lifecyclePort.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No current mode room."), false);
            return 0;
        }
        ModeRoomLifecyclePort port = lifecyclePort.get();
        source.sendSuccess(() -> Component.literal("Current room: " + port.roomId().encode()), false);
        source.sendSuccess(() -> Component.literal("Mode: " + port.gameType() + ", map: " + port.mapName()), false);
        return 1;
    }

    private static int debugEntities(CommandSourceStack source, String roomKey) {
        RoomId roomId = decodeRoom(source, roomKey);
        if (roomId == null) {
            return 0;
        }
        List<ModeEntityOwnershipRegistry.Entry> entries = FpsMatchGatewayProvider.gateway()
                .entityOwnershipRegistry()
                .entitiesInRoom(roomId);
        source.sendSuccess(() -> Component.literal("Room " + roomId.encode() + " has "
                + entries.size() + " registered entity record(s)."), false);
        return entries.size();
    }

    private static int clearEntities(CommandSourceStack source, String roomKey) {
        RoomId roomId = decodeRoom(source, roomKey);
        if (roomId == null) {
            return 0;
        }
        FpsMatchGateway gateway = FpsMatchGatewayProvider.gateway();
        int beforeCount = gateway.entityOwnershipRegistry().entitiesInRoom(roomId).size();
        if (gateway.findRoomEntityLifecyclePort(roomId).isPresent()) {
            gateway.findRoomEntityLifecyclePort(roomId).ifPresent(port -> port.onRoomEntitiesCleared(roomId));
        } else {
            gateway.entityOwnershipRegistry().clearRoom(roomId);
        }
        int afterCount = gateway.entityOwnershipRegistry().entitiesInRoom(roomId).size();
        int cleared = Math.max(0, beforeCount - afterCount);
        source.sendSuccess(() -> Component.literal("Cleared " + cleared
                + " registered entity record(s) from " + roomId.encode() + "."), true);
        return cleared;
    }

    private static int debugRuntimeState(CommandSourceStack source, String roomKey) throws CommandSyntaxException {
        RoomId roomId = decodeRoom(source, roomKey);
        if (roomId == null) {
            return 0;
        }
        ServerPlayer viewer = source.getPlayerOrException();
        Optional<ModeRuntimeStateSnapshot> snapshot = FpsMatchGatewayProvider.gateway()
                .findRoomRuntimeStatePort(roomId)
                .map(port -> port.runtimeStateSnapshot(viewer));
        if (snapshot.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Room " + roomId.encode() + " has no runtime state port."), false);
            return 0;
        }
        ModeRuntimeStateSnapshot state = snapshot.get();
        source.sendSuccess(() -> Component.literal("Runtime state " + state.roomKey()
                + ": phase=" + state.phaseKey()
                + ", revision=" + state.revision()
                + ", metrics=" + state.metrics().size()
                + ", playerValues=" + state.playerValues().size()
                + ", prompts=" + state.prompts().size()), false);
        ModeDebugSnapshotContributors.lines(
                        roomId.gameType(),
                        state,
                        FpsMatchGatewayProvider.gateway().entityOwnershipRegistry().entitiesInRoom(roomId))
                .forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return 1;
    }

    private static int debugAreas(CommandSourceStack source, String rawType, String mapName) {
        String type = GameModeRegistry.canonicalize(rawType);
        Optional<BaseMap> map = FPSMCore.getInstance().getMapByTypeWithName(type, mapName);
        if (map.isEmpty()) {
            source.sendFailure(Component.literal("Map not found: " + type + "/" + mapName));
            return 0;
        }
        List<String> areaLayers = ModeMapEditorSchemas.areaLayerKeys(type);
        Optional<ModeMapEditPort> editPort = mapEditPort(map.get());
        source.sendSuccess(() -> Component.literal("Area layers for " + type + "/" + mapName
                + ": " + areaLayers.size()), false);
        for (String layerKey : areaLayers) {
            int count = editPort
                    .filter(port -> port.supportsAreaLayer(layerKey))
                    .map(port -> port.areaLayerAreas(layerKey).size())
                    .orElse(0);
            source.sendSuccess(() -> Component.literal("- " + layerKey + ": " + count + " area(s)"), false);
        }
        return areaLayers.size();
    }

    private static RoomId decodeRoom(CommandSourceStack source, String roomKey) {
        try {
            return RoomId.decode(roomKey);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Invalid room id: " + roomKey));
            return null;
        }
    }

    private static Optional<ModeMapEditPort> mapEditPort(BaseMap map) {
        if (map instanceof ModeRoomBackedMap backedMap) {
            ModeRoomHandle handle = backedMap.roomHandle();
            return handle == null ? Optional.empty() : handle.mapEditPort();
        }
        return Optional.empty();
    }

    private static List<String> roomKeys() {
        return FpsMatchGatewayProvider.gateway().listRoomSummaryPorts().stream()
                .map(ModeRoomSummaryPort::roomId)
                .map(RoomId::encode)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static List<String> registeredGameTypes() {
        return FPSMCore.getInstance().getGameTypes().stream()
                .map(GameModeRegistry::canonicalize)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestMapsForType(
            CommandContext<CommandSourceStack> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        try {
            String type = GameModeRegistry.canonicalize(StringArgumentType.getString(context, "type"));
            return SharedSuggestionProvider.suggest(FPSMCore.getInstance().getMapNamesWithType(type), builder);
        } catch (IllegalArgumentException ignored) {
            return SharedSuggestionProvider.suggest(List.of(), builder);
        }
    }
}
