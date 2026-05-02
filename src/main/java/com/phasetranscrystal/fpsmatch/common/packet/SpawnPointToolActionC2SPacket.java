package com.phasetranscrystal.fpsmatch.common.packet;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.ModeRoomBackedMap;
import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.cdp.codpattern.app.match.editor.ModeAreaData;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchemas;
import com.cdp.codpattern.app.match.editor.ModePointData;
import com.cdp.codpattern.app.match.port.ModeMapEditPort;
import com.cdp.codpattern.app.match.service.DynamicSpawnMergeService;
import com.cdp.codpattern.compat.fpsmatch.data.CodMapPersistence;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.SpawnPointTool;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolAccessHelper;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import com.phasetranscrystal.fpsmatch.core.data.TeamSpawnProfile;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class SpawnPointToolActionC2SPacket {
    public enum Action {
        REFRESH,
        SAVE_SELECTIONS,
        DELETE_SELECTED,
        CLEAR_TEAM,
        MERGE_DYNAMIC,
        ADD_AREA
    }

    private final Action action;
    private final String selectedType;
    private final String selectedMap;
    private final String selectedTeam;
    private final String selectedKind;
    private final int selectedIndex;
    private final String editMode;
    private final String selectedAreaLayer;
    private final int selectedAreaIndex;

    public SpawnPointToolActionC2SPacket(Action action, String selectedType, String selectedMap, String selectedTeam,
            String selectedKind, int selectedIndex) {
        this(
                action,
                selectedType,
                selectedMap,
                selectedTeam,
                selectedKind,
                selectedIndex,
                SpawnPointTool.EDIT_MODE_POINT,
                "",
                -1);
    }

    public SpawnPointToolActionC2SPacket(Action action, String selectedType, String selectedMap, String selectedTeam,
            String selectedKind, int selectedIndex, String editMode, String selectedAreaLayer, int selectedAreaIndex) {
        this.action = action;
        this.selectedType = selectedType;
        this.selectedMap = selectedMap;
        this.selectedTeam = selectedTeam;
        this.selectedKind = selectedKind;
        this.selectedIndex = selectedIndex;
        this.editMode = editMode;
        this.selectedAreaLayer = selectedAreaLayer;
        this.selectedAreaIndex = selectedAreaIndex;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(selectedType);
        buf.writeUtf(selectedMap);
        buf.writeUtf(selectedTeam);
        buf.writeUtf(selectedKind);
        buf.writeVarInt(selectedIndex);
        buf.writeUtf(editMode);
        buf.writeUtf(selectedAreaLayer);
        buf.writeVarInt(selectedAreaIndex);
    }

    public static SpawnPointToolActionC2SPacket decode(FriendlyByteBuf buf) {
        return new SpawnPointToolActionC2SPacket(
                buf.readEnum(Action.class),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt()
        );
    }

    public static void sendScreen(ServerPlayer player, ItemStack stack, String requestedType, String requestedMap,
            String requestedTeam, String requestedKind, int requestedIndex) {
        sendScreen(
                player,
                stack,
                requestedType,
                requestedMap,
                requestedTeam,
                requestedKind,
                requestedIndex,
                SpawnPointTool.getEditMode(stack),
                SpawnPointTool.getSelectedAreaLayer(stack),
                0);
    }

    public static void sendScreen(ServerPlayer player, ItemStack stack, String requestedType, String requestedMap,
            String requestedTeam, String requestedKind, int requestedIndex, String requestedEditMode,
            String requestedAreaLayer, int requestedAreaIndex) {
        SelectionSnapshot snapshot = resolveSelection(
                stack,
                requestedType,
                requestedMap,
                requestedTeam,
                requestedKind,
                requestedIndex,
                requestedEditMode,
                requestedAreaLayer,
                requestedAreaIndex
        );
        FPSMatch.sendToPlayer(player, new OpenSpawnPointToolScreenS2CPacket(
                snapshot.availableTypes(),
                snapshot.selectedType(),
                snapshot.availableMaps(),
                snapshot.selectedMap(),
                snapshot.availableTeams(),
                snapshot.selectedTeam(),
                snapshot.availableKinds(),
                snapshot.selectedKind(),
                snapshot.selectedIndex(),
                snapshot.spawnPoints(),
                snapshot.editMode(),
                snapshot.availableAreaLayers(),
                snapshot.selectedAreaLayer(),
                snapshot.selectedAreaIndex(),
                snapshot.areas().stream().map(ModeAreaData::area).toList(),
                SpawnPointTool.getAreaPos1(stack),
                SpawnPointTool.getAreaPos2(stack)
        ));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof SpawnPointTool)) {
                return;
            }
            if (!ToolAccessHelper.ensureAdminAccess(player)) {
                return;
            }

            switch (action) {
                case REFRESH -> sendScreen(
                        player,
                        stack,
                        selectedType,
                        selectedMap,
                        selectedTeam,
                        selectedKind,
                        selectedIndex,
                        editMode,
                        selectedAreaLayer,
                        selectedAreaIndex);
                case SAVE_SELECTIONS -> resolveSelection(
                        stack,
                        selectedType,
                        selectedMap,
                        selectedTeam,
                        selectedKind,
                        selectedIndex,
                        editMode,
                        selectedAreaLayer,
                        selectedAreaIndex);
                case DELETE_SELECTED -> deleteSelected(player, stack);
                case CLEAR_TEAM -> clearTeam(player, stack);
                case MERGE_DYNAMIC -> mergeDynamicCandidates(player, stack);
                case ADD_AREA -> addArea(player, stack);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private void deleteSelected(ServerPlayer player, ItemStack stack) {
        SelectionSnapshot snapshot = resolveSelection(
                stack,
                selectedType,
                selectedMap,
                selectedTeam,
                selectedKind,
                selectedIndex,
                editMode,
                selectedAreaLayer,
                selectedAreaIndex
        );
        if (SpawnPointTool.EDIT_MODE_AREA.equals(snapshot.editMode())) {
            deleteSelectedArea(player, stack, snapshot);
            return;
        }
        if (snapshot.team().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.team_not_found", snapshot.selectedTeam()), false);
            return;
        }
        if (snapshot.selectedIndex() < 0 || snapshot.selectedIndex() >= snapshot.spawnPoints().size()) {
            sendScreen(
                    player,
                    stack,
                    snapshot.selectedType(),
                    snapshot.selectedMap(),
                    snapshot.selectedTeam(),
                    snapshot.selectedKind(),
                    -1,
                    snapshot.editMode(),
                    snapshot.selectedAreaLayer(),
                    snapshot.selectedAreaIndex()
            );
            return;
        }
        if (snapshot.mapEditPort().isEmpty()) {
            player.displayClientMessage(Component.translatable("command.codpattern.map.invalid_kind", snapshot.selectedKind()), false);
            return;
        }

        BaseTeam team = snapshot.team().get();
        ModeMapEditPort editPort = snapshot.mapEditPort().get();
        List<ModePointData> previousPoints = snapshot.pointLayerPoints();
        Optional<ModePointData> removed = editPort.removePointLayerPoint(
                team.name,
                snapshot.selectedKind(),
                snapshot.selectedIndex());
        if (removed.isEmpty()) {
            sendScreen(
                    player,
                    stack,
                    snapshot.selectedType(),
                    snapshot.selectedMap(),
                    snapshot.selectedTeam(),
                    snapshot.selectedKind(),
                    -1,
                    snapshot.editMode(),
                    snapshot.selectedAreaLayer(),
                    snapshot.selectedAreaIndex()
            );
            return;
        }
        try {
            CodMapPersistence.saveMapOrRollback(
                    snapshot.map().orElseThrow(),
                    () -> editPort.replacePointLayerPoints(team.name, snapshot.selectedKind(), previousPoints));
        } catch (RuntimeException e) {
            player.displayClientMessage(Component.translatable(
                    "message.codpattern.map.save_failed",
                    snapshot.selectedType(),
                    snapshot.selectedMap()), false);
            sendScreen(
                    player,
                    stack,
                    snapshot.selectedType(),
                    snapshot.selectedMap(),
                    snapshot.selectedTeam(),
                    snapshot.selectedKind(),
                    snapshot.selectedIndex(),
                    snapshot.editMode(),
                    snapshot.selectedAreaLayer(),
                    snapshot.selectedAreaIndex()
            );
            return;
        }
        snapshot.map().ifPresent(BaseMap::syncToClient);
        sendScreen(
                player,
                stack,
                snapshot.selectedType(),
                snapshot.selectedMap(),
                snapshot.selectedTeam(),
                snapshot.selectedKind(),
                snapshot.selectedIndex(),
                snapshot.editMode(),
                snapshot.selectedAreaLayer(),
                snapshot.selectedAreaIndex()
        );
    }

    private void deleteSelectedArea(ServerPlayer player, ItemStack stack, SelectionSnapshot snapshot) {
        if (snapshot.areaMapEditPort().isEmpty()) {
            player.displayClientMessage(Component.literal("Unsupported area layer: " + snapshot.selectedAreaLayer()), false);
            return;
        }
        if (snapshot.selectedAreaIndex() < 0 || snapshot.selectedAreaIndex() >= snapshot.areas().size()) {
            sendScreen(
                    player,
                    stack,
                    snapshot.selectedType(),
                    snapshot.selectedMap(),
                    snapshot.selectedTeam(),
                    snapshot.selectedKind(),
                    snapshot.selectedIndex(),
                    snapshot.editMode(),
                    snapshot.selectedAreaLayer(),
                    -1
            );
            return;
        }

        ModeMapEditPort editPort = snapshot.areaMapEditPort().get();
        List<ModeAreaData> previousAreas = snapshot.areas();
        Optional<ModeAreaData> removed = editPort.removeAreaLayerArea(
                snapshot.selectedAreaLayer(),
                snapshot.selectedAreaIndex());
        if (removed.isEmpty()) {
            sendScreen(
                    player,
                    stack,
                    snapshot.selectedType(),
                    snapshot.selectedMap(),
                    snapshot.selectedTeam(),
                    snapshot.selectedKind(),
                    snapshot.selectedIndex(),
                    snapshot.editMode(),
                    snapshot.selectedAreaLayer(),
                    -1
            );
            return;
        }
        try {
            CodMapPersistence.saveMapOrRollback(
                    snapshot.map().orElseThrow(),
                    () -> editPort.replaceAreaLayerAreas(snapshot.selectedAreaLayer(), previousAreas));
        } catch (RuntimeException e) {
            player.displayClientMessage(Component.translatable(
                    "message.codpattern.map.save_failed",
                    snapshot.selectedType(),
                    snapshot.selectedMap()), false);
            sendScreen(
                    player,
                    stack,
                    snapshot.selectedType(),
                    snapshot.selectedMap(),
                    snapshot.selectedTeam(),
                    snapshot.selectedKind(),
                    snapshot.selectedIndex(),
                    snapshot.editMode(),
                    snapshot.selectedAreaLayer(),
                    snapshot.selectedAreaIndex()
            );
            return;
        }
        snapshot.map().ifPresent(BaseMap::syncToClient);
        sendScreen(
                player,
                stack,
                snapshot.selectedType(),
                snapshot.selectedMap(),
                snapshot.selectedTeam(),
                snapshot.selectedKind(),
                snapshot.selectedIndex(),
                snapshot.editMode(),
                snapshot.selectedAreaLayer(),
                snapshot.selectedAreaIndex()
        );
    }

    private void clearTeam(ServerPlayer player, ItemStack stack) {
        SelectionSnapshot snapshot = resolveSelection(
                stack,
                selectedType,
                selectedMap,
                selectedTeam,
                selectedKind,
                selectedIndex,
                editMode,
                selectedAreaLayer,
                selectedAreaIndex
        );
        if (SpawnPointTool.EDIT_MODE_AREA.equals(snapshot.editMode())) {
            clearAreaLayer(player, stack, snapshot);
            return;
        }
        if (snapshot.team().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.team_not_found", snapshot.selectedTeam()), false);
            return;
        }
        if (snapshot.mapEditPort().isEmpty()) {
            player.displayClientMessage(Component.translatable("command.codpattern.map.invalid_kind", snapshot.selectedKind()), false);
            return;
        }

        BaseTeam team = snapshot.team().get();
        ModeMapEditPort editPort = snapshot.mapEditPort().get();
        List<ModePointData> previousPoints = snapshot.pointLayerPoints();
        editPort.clearPointLayerPoints(team.name, snapshot.selectedKind());
        try {
            CodMapPersistence.saveMapOrRollback(
                    snapshot.map().orElseThrow(),
                    () -> editPort.replacePointLayerPoints(team.name, snapshot.selectedKind(), previousPoints));
        } catch (RuntimeException e) {
            player.displayClientMessage(Component.translatable(
                    "message.codpattern.map.save_failed",
                    snapshot.selectedType(),
                    snapshot.selectedMap()), false);
            sendScreen(
                    player,
                    stack,
                    snapshot.selectedType(),
                    snapshot.selectedMap(),
                    snapshot.selectedTeam(),
                    snapshot.selectedKind(),
                    snapshot.selectedIndex(),
                    snapshot.editMode(),
                    snapshot.selectedAreaLayer(),
                    snapshot.selectedAreaIndex()
            );
            return;
        }
        snapshot.map().ifPresent(BaseMap::syncToClient);
        sendScreen(
                player,
                stack,
                snapshot.selectedType(),
                snapshot.selectedMap(),
                snapshot.selectedTeam(),
                snapshot.selectedKind(),
                -1,
                snapshot.editMode(),
                snapshot.selectedAreaLayer(),
                snapshot.selectedAreaIndex()
        );
    }

    private void clearAreaLayer(ServerPlayer player, ItemStack stack, SelectionSnapshot snapshot) {
        if (snapshot.areaMapEditPort().isEmpty()) {
            player.displayClientMessage(Component.literal("Unsupported area layer: " + snapshot.selectedAreaLayer()), false);
            return;
        }
        ModeMapEditPort editPort = snapshot.areaMapEditPort().get();
        List<ModeAreaData> previousAreas = snapshot.areas();
        editPort.clearAreaLayerAreas(snapshot.selectedAreaLayer());
        try {
            CodMapPersistence.saveMapOrRollback(
                    snapshot.map().orElseThrow(),
                    () -> editPort.replaceAreaLayerAreas(snapshot.selectedAreaLayer(), previousAreas));
        } catch (RuntimeException e) {
            player.displayClientMessage(Component.translatable(
                    "message.codpattern.map.save_failed",
                    snapshot.selectedType(),
                    snapshot.selectedMap()), false);
            sendScreen(
                    player,
                    stack,
                    snapshot.selectedType(),
                    snapshot.selectedMap(),
                    snapshot.selectedTeam(),
                    snapshot.selectedKind(),
                    snapshot.selectedIndex(),
                    snapshot.editMode(),
                    snapshot.selectedAreaLayer(),
                    snapshot.selectedAreaIndex()
            );
            return;
        }
        snapshot.map().ifPresent(BaseMap::syncToClient);
        sendScreen(
                player,
                stack,
                snapshot.selectedType(),
                snapshot.selectedMap(),
                snapshot.selectedTeam(),
                snapshot.selectedKind(),
                snapshot.selectedIndex(),
                snapshot.editMode(),
                snapshot.selectedAreaLayer(),
                -1
        );
    }

    private void addArea(ServerPlayer player, ItemStack stack) {
        SelectionSnapshot snapshot = resolveSelection(
                stack,
                selectedType,
                selectedMap,
                selectedTeam,
                selectedKind,
                selectedIndex,
                SpawnPointTool.EDIT_MODE_AREA,
                selectedAreaLayer,
                selectedAreaIndex
        );
        if (snapshot.map().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.map_not_found", snapshot.selectedMap()), false);
            return;
        }
        if (snapshot.areaMapEditPort().isEmpty()) {
            player.displayClientMessage(Component.literal("Unsupported area layer: " + snapshot.selectedAreaLayer()), false);
            return;
        }

        BaseMap map = snapshot.map().get();
        BlockPos pos1 = SpawnPointTool.getAreaPos1(stack);
        BlockPos pos2 = SpawnPointTool.getAreaPos2(stack);
        if (pos1 == null || pos2 == null) {
            player.displayClientMessage(Component.literal("Set both area positions before adding an area."), false);
            return;
        }
        if (!map.getServerLevel().dimension().equals(player.serverLevel().dimension())) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.dimension_mismatch"), false);
            return;
        }
        if (!map.getMapArea().isBlockPosInArea(pos1) || !map.getMapArea().isBlockPosInArea(pos2)) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.outside_map"), false);
            return;
        }

        ModeMapEditPort editPort = snapshot.areaMapEditPort().get();
        List<ModeAreaData> previousAreas = snapshot.areas();
        ModeAreaData area = new ModeAreaData(
                snapshot.selectedAreaLayer(),
                player.serverLevel().dimension(),
                new AreaData(pos1, pos2),
                "",
                new CompoundTag());
        if (!editPort.addAreaLayerArea(area)) {
            player.displayClientMessage(Component.literal("Area layer rejected the new area: " + snapshot.selectedAreaLayer()), false);
            return;
        }
        try {
            CodMapPersistence.saveMapOrRollback(
                    map,
                    () -> editPort.replaceAreaLayerAreas(snapshot.selectedAreaLayer(), previousAreas));
        } catch (RuntimeException e) {
            player.displayClientMessage(Component.translatable(
                    "message.codpattern.map.save_failed",
                    map.getGameType(),
                    map.getMapName()), false);
            sendScreen(
                    player,
                    stack,
                    snapshot.selectedType(),
                    snapshot.selectedMap(),
                    snapshot.selectedTeam(),
                    snapshot.selectedKind(),
                    snapshot.selectedIndex(),
                    snapshot.editMode(),
                    snapshot.selectedAreaLayer(),
                    snapshot.selectedAreaIndex()
            );
            return;
        }

        map.syncToClient();
        player.displayClientMessage(Component.literal("Added area to " + snapshot.selectedAreaLayer()), false);
        sendScreen(
                player,
                stack,
                snapshot.selectedType(),
                snapshot.selectedMap(),
                snapshot.selectedTeam(),
                snapshot.selectedKind(),
                snapshot.selectedIndex(),
                snapshot.editMode(),
                snapshot.selectedAreaLayer(),
                snapshot.areas().size()
        );
    }

    private void mergeDynamicCandidates(ServerPlayer player, ItemStack stack) {
        SelectionSnapshot snapshot = resolveSelection(
                stack,
                selectedType,
                selectedMap,
                selectedTeam,
                selectedKind,
                selectedIndex,
                editMode,
                selectedAreaLayer,
                selectedAreaIndex
        );
        if (snapshot.map().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.map_not_found", snapshot.selectedMap()), false);
            return;
        }

        BaseMap map = snapshot.map().get();
        if (!ModeMapEditorSchemas.supportsDynamicRespawnMerge(map.getGameType())) {
            player.displayClientMessage(Component.translatable(
                    "command.codpattern.map.spawn.merge.unsupported_mode",
                    map.getGameType()), false);
            return;
        }

        List<BaseTeam> teams = map.getMapTeams().getTeams();
        if (teams.size() != 2) {
            player.displayClientMessage(Component.translatable(
                    "command.codpattern.map.spawn.merge.invalid_team_count",
                    map.getGameType(),
                    map.getMapName(),
                    teams.size()), false);
            return;
        }

        DynamicSpawnMergeService.MergeResult mergeResult =
                DynamicSpawnMergeService.mergeDynamicSpawnCandidates(teams);
        if (mergeResult.uniqueDynamicPointCount() <= 0) {
            player.displayClientMessage(Component.translatable(
                    "command.codpattern.map.spawn.merge.none",
                    map.getGameType(),
                    map.getMapName()), false);
            return;
        }

        Map<BaseTeam, TeamSpawnProfile> previousProfiles = captureTeamSpawnProfiles(teams);
        for (BaseTeam team : teams) {
            TeamSpawnProfile currentProfile = team.getSpawnProfile();
            team.setSpawnProfile(new TeamSpawnProfile(
                    currentProfile.initialSpawnPoints(),
                    mergeResult.dynamicPointsByTeam().getOrDefault(team.name, List.of())
            ));
            team.clearPlayerSpawnPointAssignments();
        }

        try {
            CodMapPersistence.saveMapOrRollback(map, () -> restoreTeamSpawnProfiles(previousProfiles));
        } catch (RuntimeException e) {
            player.displayClientMessage(Component.translatable(
                    "message.codpattern.map.save_failed",
                    map.getGameType(),
                    map.getMapName()), false);
            sendScreen(
                    player,
                    stack,
                    snapshot.selectedType(),
                    snapshot.selectedMap(),
                    snapshot.selectedTeam(),
                    snapshot.selectedKind(),
                    snapshot.selectedIndex()
            );
            return;
        }

        map.syncToClient();
        BaseTeam firstTeam = teams.get(0);
        BaseTeam secondTeam = teams.get(1);
        player.displayClientMessage(Component.translatable(
                "command.codpattern.map.spawn.merge.success",
                map.getGameType(),
                map.getMapName(),
                mergeResult.uniqueDynamicPointCount(),
                firstTeam.name,
                mergeResult.countForTeam(firstTeam.name),
                secondTeam.name,
                mergeResult.countForTeam(secondTeam.name)), false);
        sendScreen(
                player,
                stack,
                snapshot.selectedType(),
                snapshot.selectedMap(),
                snapshot.selectedTeam(),
                snapshot.selectedKind(),
                snapshot.selectedIndex()
        );
    }

    private static SelectionSnapshot resolveSelection(ItemStack stack, String requestedType, String requestedMap,
            String requestedTeam, String requestedKind, int requestedIndex, String requestedEditMode,
            String requestedAreaLayer, int requestedAreaIndex) {
        FPSMCore core = FPSMCore.getInstance();
        List<String> availableTypes = core.getGameTypes();
        String canonicalRequestedType = GameModeRegistry.canonicalize(requestedType);
        String selectedType = availableTypes.contains(canonicalRequestedType) ? canonicalRequestedType : firstOrBlank(availableTypes);
        List<String> availableMaps = selectedType.isBlank() ? List.of() : core.getMapNamesWithType(selectedType);
        String selectedMap = availableMaps.contains(requestedMap) ? requestedMap : firstOrBlank(availableMaps);
        Optional<BaseMap> map = selectedType.isBlank() || selectedMap.isBlank()
                ? Optional.empty()
                : core.getMapByTypeWithName(selectedType, selectedMap);
        List<String> availableTeams = map.map(baseMap -> baseMap.getMapTeams().getTeams().stream()
                .map(team -> team.name)
                .toList()).orElse(List.of());
        String selectedTeam = availableTeams.contains(requestedTeam) ? requestedTeam : firstOrBlank(availableTeams);
        Optional<BaseTeam> team = map.flatMap(baseMap -> baseMap.getMapTeams().getTeamByName(selectedTeam));
        List<String> availableKinds = SpawnPointTool.availableKindsForType(selectedType);
        String selectedKind = SpawnPointTool.normalizeSelectedKind(selectedType, requestedKind);
        Optional<ModeMapEditPort> editPort = map.flatMap(SpawnPointToolActionC2SPacket::mapEditPort)
                .filter(port -> port.supportsPointLayer(selectedKind));
        List<ModePointData> pointLayerPoints = team
                .flatMap(baseTeam -> editPort.map(port -> port.pointLayerPoints(baseTeam.name, selectedKind)))
                .map(List::copyOf)
                .orElse(List.of());
        List<SpawnPointData> spawnPoints = pointLayerPoints.stream()
                .map(point -> toDisplaySpawnPointData(selectedKind, point))
                .toList();
        List<String> availableAreaLayers = SpawnPointTool.availableAreaLayersForType(selectedType);
        String editMode = SpawnPointTool.normalizeEditMode(requestedEditMode, selectedType);
        String selectedAreaLayer = SpawnPointTool.normalizeSelectedAreaLayer(selectedType, requestedAreaLayer);
        Optional<ModeMapEditPort> areaEditPort = map.flatMap(SpawnPointToolActionC2SPacket::mapEditPort)
                .filter(port -> port.supportsAreaLayer(selectedAreaLayer));
        List<ModeAreaData> areas = areaEditPort
                .map(port -> port.areaLayerAreas(selectedAreaLayer))
                .map(List::copyOf)
                .orElse(List.of());

        SpawnPointTool.setSelectedType(stack, selectedType);
        SpawnPointTool.setSelectedMap(stack, selectedMap);
        SpawnPointTool.setSelectedTeam(stack, selectedTeam);
        SpawnPointTool.setSelectedKind(stack, selectedKind);
        SpawnPointTool.setEditMode(stack, editMode);
        SpawnPointTool.setSelectedAreaLayer(stack, selectedAreaLayer);

        int normalizedIndex = spawnPoints.isEmpty()
                ? -1
                : Math.max(0, Math.min(requestedIndex < 0 ? 0 : requestedIndex, spawnPoints.size() - 1));
        int normalizedAreaIndex = areas.isEmpty()
                ? -1
                : Math.max(0, Math.min(requestedAreaIndex < 0 ? 0 : requestedAreaIndex, areas.size() - 1));

        return new SelectionSnapshot(
                availableTypes,
                selectedType,
                availableMaps,
                selectedMap,
                availableTeams,
                selectedTeam,
                availableKinds,
                selectedKind,
                normalizedIndex,
                spawnPoints,
                pointLayerPoints,
                editPort,
                map,
                team,
                editMode,
                availableAreaLayers,
                selectedAreaLayer,
                normalizedAreaIndex,
                areas,
                areaEditPort
        );
    }

    private static Optional<ModeMapEditPort> mapEditPort(BaseMap map) {
        if (map instanceof ModeRoomBackedMap backedMap) {
            ModeRoomHandle handle = backedMap.roomHandle();
            return handle == null ? Optional.empty() : handle.mapEditPort();
        }
        return Optional.empty();
    }

    private static SpawnPointData toDisplaySpawnPointData(String layerKey, ModePointData point) {
        SpawnPointKind kind = ModeMapEditorSchemas.legacySpawnPointKind(layerKey)
                .orElse(SpawnPointKind.INITIAL);
        return point.toSpawnPointData(kind);
    }

    private static String firstOrBlank(List<String> values) {
        return values.isEmpty() ? "" : values.get(0);
    }

    private static Map<BaseTeam, TeamSpawnProfile> captureTeamSpawnProfiles(List<BaseTeam> teams) {
        Map<BaseTeam, TeamSpawnProfile> previousProfiles = new LinkedHashMap<>();
        for (BaseTeam team : teams) {
            if (team != null) {
                previousProfiles.put(team, team.getSpawnProfile());
            }
        }
        return previousProfiles;
    }

    private static void restoreTeamSpawnProfiles(Map<BaseTeam, TeamSpawnProfile> previousProfiles) {
        if (previousProfiles == null) {
            return;
        }
        previousProfiles.forEach((team, profile) -> {
            if (team == null) {
                return;
            }
            team.setSpawnProfile(profile);
            team.clearPlayerSpawnPointAssignments();
        });
    }

    private record SelectionSnapshot(
            List<String> availableTypes,
            String selectedType,
            List<String> availableMaps,
            String selectedMap,
            List<String> availableTeams,
            String selectedTeam,
            List<String> availableKinds,
            String selectedKind,
            int selectedIndex,
            List<SpawnPointData> spawnPoints,
            List<ModePointData> pointLayerPoints,
            Optional<ModeMapEditPort> mapEditPort,
            Optional<BaseMap> map,
            Optional<BaseTeam> team,
            String editMode,
            List<String> availableAreaLayers,
            String selectedAreaLayer,
            int selectedAreaIndex,
            List<ModeAreaData> areas,
            Optional<ModeMapEditPort> areaMapEditPort
    ) {
    }
}
