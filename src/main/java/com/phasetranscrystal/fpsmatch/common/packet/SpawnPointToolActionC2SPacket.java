package com.phasetranscrystal.fpsmatch.common.packet;

import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.cdp.codpattern.app.tdm.service.DynamicSpawnMergeService;
import com.cdp.codpattern.compat.fpsmatch.data.CodMapPersistence;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.SpawnPointTool;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import com.phasetranscrystal.fpsmatch.core.data.TeamSpawnProfile;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
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
        MERGE_DYNAMIC
    }

    private final Action action;
    private final String selectedType;
    private final String selectedMap;
    private final String selectedTeam;
    private final String selectedKind;
    private final int selectedIndex;

    public SpawnPointToolActionC2SPacket(Action action, String selectedType, String selectedMap, String selectedTeam,
            String selectedKind, int selectedIndex) {
        this.action = action;
        this.selectedType = selectedType;
        this.selectedMap = selectedMap;
        this.selectedTeam = selectedTeam;
        this.selectedKind = selectedKind;
        this.selectedIndex = selectedIndex;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(selectedType);
        buf.writeUtf(selectedMap);
        buf.writeUtf(selectedTeam);
        buf.writeUtf(selectedKind);
        buf.writeVarInt(selectedIndex);
    }

    public static SpawnPointToolActionC2SPacket decode(FriendlyByteBuf buf) {
        return new SpawnPointToolActionC2SPacket(
                buf.readEnum(Action.class),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt()
        );
    }

    public static void sendScreen(ServerPlayer player, ItemStack stack, String requestedType, String requestedMap,
            String requestedTeam, String requestedKind, int requestedIndex) {
        SelectionSnapshot snapshot = resolveSelection(
                stack,
                requestedType,
                requestedMap,
                requestedTeam,
                requestedKind,
                requestedIndex
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
                snapshot.spawnPoints()
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

            switch (action) {
                case REFRESH -> sendScreen(player, stack, selectedType, selectedMap, selectedTeam, selectedKind, selectedIndex);
                case SAVE_SELECTIONS -> resolveSelection(stack, selectedType, selectedMap, selectedTeam, selectedKind, selectedIndex);
                case DELETE_SELECTED -> deleteSelected(player, stack);
                case CLEAR_TEAM -> clearTeam(player, stack);
                case MERGE_DYNAMIC -> mergeDynamicCandidates(player, stack);
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
                selectedIndex
        );
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
                    -1
            );
            return;
        }

        BaseTeam team = snapshot.team().get();
        TeamSpawnProfile previousSpawnProfile = team.getSpawnProfile();
        team.removeSpawnPointData(snapshot.spawnPointKind(), snapshot.selectedIndex());
        team.clearPlayerSpawnPointAssignments();
        if (snapshot.spawnPointKind() == SpawnPointKind.INITIAL && !team.getSpawnPointsData().isEmpty()) {
            team.assignNextSpawnPoints(SpawnPointKind.INITIAL);
        }
        try {
            CodMapPersistence.saveMapOrRollback(
                    snapshot.map().orElseThrow(),
                    () -> CodMapPersistence.restoreSpawnProfile(snapshot.map().orElse(null), team, previousSpawnProfile));
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
                    snapshot.selectedIndex()
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
                snapshot.selectedIndex()
        );
    }

    private void clearTeam(ServerPlayer player, ItemStack stack) {
        SelectionSnapshot snapshot = resolveSelection(
                stack,
                selectedType,
                selectedMap,
                selectedTeam,
                selectedKind,
                selectedIndex
        );
        if (snapshot.team().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.team_not_found", snapshot.selectedTeam()), false);
            return;
        }

        BaseTeam team = snapshot.team().get();
        TeamSpawnProfile previousSpawnProfile = team.getSpawnProfile();
        team.resetSpawnPointData(snapshot.spawnPointKind());
        team.clearPlayerSpawnPointAssignments();
        try {
            CodMapPersistence.saveMapOrRollback(
                    snapshot.map().orElseThrow(),
                    () -> CodMapPersistence.restoreSpawnProfile(snapshot.map().orElse(null), team, previousSpawnProfile));
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
                    snapshot.selectedIndex()
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
                -1
        );
    }

    private void mergeDynamicCandidates(ServerPlayer player, ItemStack stack) {
        SelectionSnapshot snapshot = resolveSelection(
                stack,
                selectedType,
                selectedMap,
                selectedTeam,
                selectedKind,
                selectedIndex
        );
        if (snapshot.map().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.map_not_found", snapshot.selectedMap()), false);
            return;
        }

        BaseMap map = snapshot.map().get();
        if (!TdmGameTypes.supportsDynamicRespawnPoints(map.getGameType())) {
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
            String requestedTeam, String requestedKind, int requestedIndex) {
        FPSMCore core = FPSMCore.getInstance();
        List<String> availableTypes = core.getGameTypes();
        String canonicalRequestedType = TdmGameTypes.canonicalize(requestedType);
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
        SpawnPointKind spawnPointKind = SpawnPointKind.fromSerializedName(selectedKind);
        List<SpawnPointData> spawnPoints = team.map(baseTeam -> baseTeam.getSpawnPointsData(spawnPointKind))
                .map(List::copyOf)
                .orElse(List.of());

        SpawnPointTool.setSelectedType(stack, selectedType);
        SpawnPointTool.setSelectedMap(stack, selectedMap);
        SpawnPointTool.setSelectedTeam(stack, selectedTeam);
        SpawnPointTool.setSelectedKind(stack, selectedKind);

        int normalizedIndex = spawnPoints.isEmpty()
                ? -1
                : Math.max(0, Math.min(requestedIndex < 0 ? 0 : requestedIndex, spawnPoints.size() - 1));

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
                spawnPointKind,
                map,
                team
        );
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
            SpawnPointKind spawnPointKind,
            Optional<BaseMap> map,
            Optional<BaseTeam> team
    ) {
    }
}
