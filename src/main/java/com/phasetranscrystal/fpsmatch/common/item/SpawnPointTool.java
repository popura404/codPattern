package com.phasetranscrystal.fpsmatch.common.item;

import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.cdp.codpattern.compat.fpsmatch.data.CodMapPersistence;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.tool.CreatorToolItem;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolInteractionAction;
import com.phasetranscrystal.fpsmatch.common.item.tool.WorldToolItem;
import com.phasetranscrystal.fpsmatch.common.packet.AddAreaDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.AddPointDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.RemoveDebugDataByPrefixS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.SpawnPointToolActionC2SPacket;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import com.phasetranscrystal.fpsmatch.core.data.TeamSpawnProfile;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import com.phasetranscrystal.fpsmatch.util.PreviewColorUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class SpawnPointTool extends CreatorToolItem implements WorldToolItem {
    private static final String KIND_TAG = "SelectedSpawnPointKind";
    private static final String HELD_PREVIEW_STATE_TAG = "HeldSpawnPointPreviewState";
    private static final int HELD_PREVIEW_REFRESH_INTERVAL = 10;

    public SpawnPointTool(Properties properties) {
        super(properties);
    }

    @Override
    public void handleWorldInteraction(ServerPlayer player, ItemStack stack, ToolInteractionAction action, BlockPos clickedPos) {
        switch (action) {
            case LEFT_CLICK_BLOCK -> {
                if (clickedPos != null) {
                    addSpawnPoint(player, stack, clickedPos);
                }
            }
            case CTRL_RIGHT_CLICK -> SpawnPointToolActionC2SPacket.sendScreen(
                    player,
                    stack,
                    getSelectedType(stack),
                    getSelectedMap(stack),
                    getSelectedTeam(stack),
                    getSelectedKind(stack),
                    0
            );
            case RIGHT_CLICK_BLOCK -> {
            }
        }
    }

    public void syncHeldPreview(ServerPlayer player, ItemStack stack) {
        String selectedType = getSelectedType(stack).trim();
        String selectedMap = getSelectedMap(stack).trim();
        SpawnPointKind selectedKind = SpawnPointKind.fromSerializedName(getSelectedKind(stack));
        if (selectedType.isBlank() || selectedMap.isBlank()) {
            clearHeldPreview(player);
            return;
        }

        Optional<BaseMap> mapOptional = FPSMCore.getInstance().getMapByTypeWithName(selectedType, selectedMap)
                .filter(map -> map.getServerLevel().dimension().equals(player.serverLevel().dimension()));
        if (mapOptional.isEmpty()) {
            clearHeldPreview(player);
            return;
        }

        BaseMap map = mapOptional.get();
        String signatureWithPoints = buildHeldPreviewSignature(
                selectedType + "|" + selectedMap + "|" + selectedKind.serializedName(),
                map,
                selectedKind
        );
        String previousSignature = player.getPersistentData().getString(HELD_PREVIEW_STATE_TAG);
        if (signatureWithPoints.equals(previousSignature) && player.tickCount % HELD_PREVIEW_REFRESH_INTERVAL != 0) {
            return;
        }

        FPSMatch.sendToPlayer(player, new RemoveDebugDataByPrefixS2CPacket(getHeldPreviewPrefix(player)));
        FPSMatch.sendToPlayer(player, new AddAreaDataS2CPacket(
                getHeldPreviewKey(player),
                Component.literal(map.getMapName()),
                PreviewColorUtil.getMapPreviewColor(selectedType),
                map.getMapArea()
        ));

        List<BaseTeam> orderedTeams = getOrderedNormalTeams(map);
        for (int teamIndex = 0; teamIndex < orderedTeams.size(); teamIndex++) {
            BaseTeam team = orderedTeams.get(teamIndex);
            int pointColor = PreviewColorUtil.getPointPreviewColor(selectedType, teamIndex);
            List<SpawnPointData> spawnPoints = team.getSpawnPointsData(selectedKind);
            for (int i = 0; i < spawnPoints.size(); i++) {
                SpawnPointData data = spawnPoints.get(i);
                FPSMatch.sendToPlayer(player, new AddPointDataS2CPacket(
                        getHeldPreviewPointKey(player, team.name, i),
                        Component.literal(team.name + " #" + (i + 1)),
                        pointColor,
                        Vec3.atCenterOf(data.getPosition())
                ));
            }
        }

        player.getPersistentData().putString(HELD_PREVIEW_STATE_TAG, signatureWithPoints);
    }

    public static void clearHeldPreview(ServerPlayer player) {
        if (!player.getPersistentData().contains(HELD_PREVIEW_STATE_TAG)) {
            return;
        }

        FPSMatch.sendToPlayer(player, new RemoveDebugDataByPrefixS2CPacket(getHeldPreviewPrefix(player)));
        player.getPersistentData().remove(HELD_PREVIEW_STATE_TAG);
    }

    private static String getHeldPreviewPrefix(ServerPlayer player) {
        return "held_tool_preview:spawn_point:" + player.getUUID() + ":";
    }

    private static String getHeldPreviewKey(ServerPlayer player) {
        return getHeldPreviewPrefix(player) + "area";
    }

    private static String getHeldPreviewPointKey(ServerPlayer player, String teamName, int index) {
        return getHeldPreviewPrefix(player) + teamName + ":" + index;
    }

    private static String buildHeldPreviewSignature(String baseSignature, BaseMap map, SpawnPointKind selectedKind) {
        StringBuilder builder = new StringBuilder(baseSignature);
        for (BaseTeam team : getOrderedNormalTeams(map)) {
            builder.append('|').append(team.name);
            for (SpawnPointData point : team.getSpawnPointsData(selectedKind)) {
                builder.append('|')
                        .append(point.getDimension().location())
                        .append('@')
                        .append(point.getX()).append(',')
                        .append(point.getY()).append(',')
                        .append(point.getZ()).append(',')
                        .append(point.getYaw()).append(',')
                        .append(point.getPitch());
            }
        }
        return builder.toString();
    }

    private static List<BaseTeam> getOrderedNormalTeams(BaseMap map) {
        return map.getMapTeams().getTeams().stream()
                .sorted(Comparator.comparing(team -> team.name))
                .toList();
    }

    private void addSpawnPoint(ServerPlayer player, ItemStack stack, BlockPos clickedPos) {
        String selectedType = getSelectedType(stack);
        String selectedMap = getSelectedMap(stack);
        String selectedTeam = getSelectedTeam(stack);
        SpawnPointKind selectedKind = SpawnPointKind.fromSerializedName(getSelectedKind(stack));
        if (selectedType.isBlank() || selectedMap.isBlank() || selectedTeam.isBlank()) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.missing_selection"), false);
            return;
        }

        Optional<BaseMap> mapOptional = FPSMCore.getInstance().getMapByTypeWithName(selectedType, selectedMap);
        if (mapOptional.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.map_not_found", selectedMap), false);
            return;
        }

        BaseMap map = mapOptional.get();
        if (!map.getServerLevel().dimension().equals(player.serverLevel().dimension())) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.dimension_mismatch"), false);
            return;
        }
        if (!map.getMapArea().isBlockPosInArea(clickedPos)) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.outside_map"), false);
            return;
        }

        Optional<BaseTeam> teamOptional = map.getMapTeams().getTeamByName(selectedTeam);
        if (teamOptional.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.team_not_found", selectedTeam), false);
            return;
        }

        BaseTeam team = teamOptional.get();
        TeamSpawnProfile previousSpawnProfile = team.getSpawnProfile();
        SpawnPointData spawnPointData = new SpawnPointData(
                player.serverLevel().dimension(),
                clickedPos.above(),
                player.getYRot(),
                player.getXRot(),
                selectedKind
        );
        if (!team.addSpawnPointDataIfAbsent(spawnPointData)) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.duplicate"), false);
            return;
        }
        if (map.isStart && selectedKind == SpawnPointKind.INITIAL) {
            team.assignNextSpawnPoints(SpawnPointKind.INITIAL);
        }
        try {
            CodMapPersistence.saveMapOrRollback(map, () -> CodMapPersistence.restoreSpawnProfile(map, team, previousSpawnProfile));
        } catch (RuntimeException e) {
            player.displayClientMessage(Component.translatable(
                    "message.codpattern.map.save_failed",
                    map.getGameType(),
                    map.getMapName()), false);
            return;
        }
        map.syncToClient();

        player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.added",
                MapCreatorTool.formatPos(clickedPos.above())).withStyle(ChatFormatting.GREEN), true);
    }

    public static void setSelectedType(ItemStack stack, String selectedType) {
        setStringTag(stack, TYPE_TAG, TdmGameTypes.canonicalize(selectedType));
    }

    public static String getSelectedType(ItemStack stack) {
        return TdmGameTypes.canonicalize(getStringTag(stack, TYPE_TAG));
    }

    public static void setSelectedMap(ItemStack stack, String selectedMap) {
        setStringTag(stack, MAP_TAG, selectedMap);
    }

    public static String getSelectedMap(ItemStack stack) {
        return getStringTag(stack, MAP_TAG);
    }

    public static void setSelectedTeam(ItemStack stack, String selectedTeam) {
        setStringTag(stack, TEAM_TAG, selectedTeam);
    }

    public static String getSelectedTeam(ItemStack stack) {
        return getStringTag(stack, TEAM_TAG);
    }

    public static void setSelectedKind(ItemStack stack, String selectedKind) {
        setStringTag(stack, KIND_TAG, selectedKind);
    }

    public static String getSelectedKind(ItemStack stack) {
        String stored = getStringTag(stack, KIND_TAG);
        return normalizeSelectedKind(getSelectedType(stack), stored);
    }

    public static List<String> availableKindsForType(String gameType) {
        if (TdmGameTypes.supportsDynamicRespawnPoints(gameType)) {
            return List.of(
                    SpawnPointKind.INITIAL.serializedName(),
                    SpawnPointKind.DYNAMIC_CANDIDATE.serializedName());
        }
        return List.of(SpawnPointKind.INITIAL.serializedName());
    }

    public static String normalizeSelectedKind(String gameType, String selectedKind) {
        List<String> availableKinds = availableKindsForType(gameType);
        if (selectedKind != null && availableKinds.contains(selectedKind)) {
            return selectedKind;
        }
        return SpawnPointKind.INITIAL.serializedName();
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltip, isAdvanced);
        tooltip.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.fpsm.spawn_point_tool.selected.type")
                .append(": ")
                .append(Component.literal(getSelectedType(stack).isBlank()
                        ? Component.translatable("tooltip.fpsm.none").getString()
                        : getSelectedType(stack)).withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.translatable("tooltip.fpsm.spawn_point_tool.selected.map")
                .append(": ")
                .append(Component.literal(getSelectedMap(stack).isBlank()
                        ? Component.translatable("tooltip.fpsm.none").getString()
                        : getSelectedMap(stack)).withStyle(ChatFormatting.GREEN)));
        tooltip.add(Component.translatable("tooltip.fpsm.spawn_point_tool.selected.team")
                .append(": ")
                .append(Component.literal(getSelectedTeam(stack).isBlank()
                        ? Component.translatable("tooltip.fpsm.none").getString()
                        : getSelectedTeam(stack)).withStyle(ChatFormatting.YELLOW)));
        tooltip.add(Component.translatable("tooltip.fpsm.spawn_point_tool.selected.kind")
                .append(": ")
                .append(Component.literal(getSelectedKind(stack)).withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.fpsm.spawn_point_tool.left_click"));
        tooltip.add(Component.translatable("tooltip.fpsm.spawn_point_tool.ctrl_right_click"));
    }
}
