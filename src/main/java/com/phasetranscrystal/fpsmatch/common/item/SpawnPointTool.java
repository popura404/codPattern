package com.phasetranscrystal.fpsmatch.common.item;

import com.cdp.codpattern.app.match.GameModeRegistry;
import com.cdp.codpattern.app.match.ModeRoomBackedMap;
import com.cdp.codpattern.app.match.ModeRoomHandle;
import com.cdp.codpattern.app.match.editor.ModeAreaData;
import com.cdp.codpattern.app.match.editor.ModeMapEditorSchemas;
import com.cdp.codpattern.app.match.editor.ModePointData;
import com.cdp.codpattern.app.match.port.ModeMapEditPort;
import com.cdp.codpattern.compat.fpsmatch.data.CodMapPersistence;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.tool.CreatorToolItem;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolInteractionAction;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolInteractionHit;
import com.phasetranscrystal.fpsmatch.common.item.tool.WorldToolItem;
import com.phasetranscrystal.fpsmatch.common.packet.AddAreaDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.AddPointDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.RemoveDebugDataByPrefixS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.SpawnPointToolActionC2SPacket;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointKind;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import com.phasetranscrystal.fpsmatch.util.PreviewColorUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
    public static final String EDIT_MODE_POINT = "POINT";
    public static final String EDIT_MODE_AREA = "AREA";

    private static final String EDIT_MODE_TAG = "SelectedEditMode";
    private static final String KIND_TAG = "SelectedSpawnPointKind";
    private static final String AREA_LAYER_TAG = "SelectedAreaLayer";
    private static final String AREA_POS_1_TAG = "SelectedAreaPos1";
    private static final String AREA_POS_2_TAG = "SelectedAreaPos2";
    private static final String HELD_PREVIEW_STATE_TAG = "HeldSpawnPointPreviewState";
    private static final int HELD_PREVIEW_REFRESH_INTERVAL = 10;

    public SpawnPointTool(Properties properties) {
        super(properties);
    }

    @Override
    public void handleWorldInteraction(ServerPlayer player, ItemStack stack, ToolInteractionAction action, ToolInteractionHit hit) {
        if (isAreaMode(stack)) {
            handleAreaWorldInteraction(player, stack, action, hit);
            return;
        }
        switch (action) {
            case LEFT_CLICK_BLOCK -> {
                if (hit != null) {
                    addSpawnPoint(player, stack, hit.clickedBlockPos());
                }
            }
            case CTRL_RIGHT_CLICK -> SpawnPointToolActionC2SPacket.sendScreen(
                    player,
                    stack,
                    getSelectedType(stack),
                    getSelectedMap(stack),
                    getSelectedTeam(stack),
                    getSelectedKind(stack),
                    0,
                    getEditMode(stack),
                    getSelectedAreaLayer(stack),
                    0
            );
            case RIGHT_CLICK_BLOCK -> {
            }
        }
    }

    public void syncHeldPreview(ServerPlayer player, ItemStack stack) {
        if (isAreaMode(stack)) {
            syncAreaHeldPreview(player, stack);
            return;
        }
        String selectedType = getSelectedType(stack).trim();
        String selectedMap = getSelectedMap(stack).trim();
        String selectedLayerKey = getSelectedKind(stack);
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
        Optional<ModeMapEditPort> editPort = mapEditPort(map)
                .filter(port -> port.supportsPointLayer(selectedLayerKey));
        if (editPort.isEmpty()) {
            clearHeldPreview(player);
            return;
        }
        String signatureWithPoints = buildHeldPreviewSignature(
                selectedType + "|" + selectedMap + "|" + selectedLayerKey,
                map,
                selectedLayerKey,
                editPort.get()
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
            List<ModePointData> spawnPoints = editPort.get().pointLayerPoints(team.name, selectedLayerKey);
            for (int i = 0; i < spawnPoints.size(); i++) {
                ModePointData data = spawnPoints.get(i);
                FPSMatch.sendToPlayer(player, new AddPointDataS2CPacket(
                        getHeldPreviewPointKey(player, team.name, i),
                        Component.literal(team.name + " #" + (i + 1)),
                        pointColor,
                        Vec3.atCenterOf(data.position()),
                        data.yaw()
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

    private void handleAreaWorldInteraction(ServerPlayer player, ItemStack stack, ToolInteractionAction action, ToolInteractionHit hit) {
        switch (action) {
            case LEFT_CLICK_BLOCK -> {
                if (hit == null) {
                    return;
                }
                BlockPos clickedPos = hit.clickedBlockPos();
                setBlockPos(stack, AREA_POS_1_TAG, clickedPos);
                player.displayClientMessage(Component.literal("Set area pos 1: " + MapCreatorTool.formatPos(clickedPos))
                        .withStyle(ChatFormatting.AQUA), true);
            }
            case RIGHT_CLICK_BLOCK -> {
                if (hit == null) {
                    return;
                }
                BlockPos clickedPos = hit.clickedBlockPos();
                setBlockPos(stack, AREA_POS_2_TAG, clickedPos);
                player.displayClientMessage(Component.literal("Set area pos 2: " + MapCreatorTool.formatPos(clickedPos))
                        .withStyle(ChatFormatting.AQUA), true);
            }
            case CTRL_RIGHT_CLICK -> SpawnPointToolActionC2SPacket.sendScreen(
                    player,
                    stack,
                    getSelectedType(stack),
                    getSelectedMap(stack),
                    getSelectedTeam(stack),
                    getSelectedKind(stack),
                    0,
                    getEditMode(stack),
                    getSelectedAreaLayer(stack),
                    0
            );
        }
    }

    private void syncAreaHeldPreview(ServerPlayer player, ItemStack stack) {
        String selectedType = getSelectedType(stack).trim();
        String selectedMap = getSelectedMap(stack).trim();
        String selectedLayerKey = getSelectedAreaLayer(stack);
        if (selectedType.isBlank() || selectedMap.isBlank() || selectedLayerKey.isBlank()) {
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
        Optional<ModeMapEditPort> editPort = mapEditPort(map)
                .filter(port -> port.supportsAreaLayer(selectedLayerKey));
        if (editPort.isEmpty()) {
            clearHeldPreview(player);
            return;
        }

        BlockPos pos1 = getBlockPos(stack, AREA_POS_1_TAG);
        BlockPos pos2 = getBlockPos(stack, AREA_POS_2_TAG);
        List<ModeAreaData> areas = editPort.get().areaLayerAreas(selectedLayerKey);
        String signatureWithAreas = buildAreaHeldPreviewSignature(
                selectedType + "|" + selectedMap + "|" + selectedLayerKey,
                areas,
                pos1,
                pos2
        );
        String previousSignature = player.getPersistentData().getString(HELD_PREVIEW_STATE_TAG);
        if (signatureWithAreas.equals(previousSignature) && player.tickCount % HELD_PREVIEW_REFRESH_INTERVAL != 0) {
            return;
        }

        FPSMatch.sendToPlayer(player, new RemoveDebugDataByPrefixS2CPacket(getHeldPreviewPrefix(player)));
        FPSMatch.sendToPlayer(player, new AddAreaDataS2CPacket(
                getHeldPreviewKey(player),
                Component.literal(map.getMapName()),
                PreviewColorUtil.getMapPreviewColor(selectedType),
                map.getMapArea()
        ));

        int areaColor = PreviewColorUtil.getPointPreviewColor(selectedType);
        for (int i = 0; i < areas.size(); i++) {
            ModeAreaData area = areas.get(i);
            FPSMatch.sendToPlayer(player, new AddAreaDataS2CPacket(
                    getHeldPreviewAreaKey(player, i),
                    Component.literal(selectedLayerKey + " #" + (i + 1)),
                    areaColor,
                    area.area()
            ));
        }

        if (pos1 != null && pos2 != null) {
            FPSMatch.sendToPlayer(player, new AddAreaDataS2CPacket(
                    getHeldPreviewDraftAreaKey(player),
                    Component.literal(selectedLayerKey + " draft"),
                    0xFFFFFFFF,
                    new AreaData(pos1, pos2)
            ));
        }

        player.getPersistentData().putString(HELD_PREVIEW_STATE_TAG, signatureWithAreas);
    }

    private static String getHeldPreviewPrefix(ServerPlayer player) {
        return "held_tool_preview:spawn_point:" + player.getUUID() + ":";
    }

    private static String getHeldPreviewKey(ServerPlayer player) {
        return getHeldPreviewPrefix(player) + "area";
    }

    private static String getHeldPreviewAreaKey(ServerPlayer player, int index) {
        return getHeldPreviewPrefix(player) + "area_layer:" + index;
    }

    private static String getHeldPreviewDraftAreaKey(ServerPlayer player) {
        return getHeldPreviewPrefix(player) + "area_layer:draft";
    }

    private static String getHeldPreviewPointKey(ServerPlayer player, String teamName, int index) {
        return getHeldPreviewPrefix(player) + teamName + ":" + index;
    }

    private static String buildHeldPreviewSignature(
            String baseSignature,
            BaseMap map,
            String selectedLayerKey,
            ModeMapEditPort editPort
    ) {
        StringBuilder builder = new StringBuilder(baseSignature);
        for (BaseTeam team : getOrderedNormalTeams(map)) {
            builder.append('|').append(team.name);
            for (ModePointData point : editPort.pointLayerPoints(team.name, selectedLayerKey)) {
                builder.append('|')
                        .append(point.dimension().location())
                        .append('@')
                        .append(point.position().getX()).append(',')
                        .append(point.position().getY()).append(',')
                        .append(point.position().getZ()).append(',')
                        .append(point.yaw());
            }
        }
        return builder.toString();
    }

    private static String buildAreaHeldPreviewSignature(
            String baseSignature,
            List<ModeAreaData> areas,
            BlockPos pos1,
            BlockPos pos2
    ) {
        StringBuilder builder = new StringBuilder(baseSignature);
        if (pos1 != null) {
            builder.append("|pos1=").append(pos1.asLong());
        }
        if (pos2 != null) {
            builder.append("|pos2=").append(pos2.asLong());
        }
        for (ModeAreaData area : areas) {
            if (area == null) {
                continue;
            }
            builder.append('|')
                    .append(area.dimension().location())
                    .append('@')
                    .append(area.area().pos1().asLong())
                    .append(',')
                    .append(area.area().pos2().asLong())
                    .append(',')
                    .append(area.scopeKey());
        }
        return builder.toString();
    }

    private static List<BaseTeam> getOrderedNormalTeams(BaseMap map) {
        return map.getMapTeams().getTeams().stream()
                .sorted(Comparator.comparing(team -> team.name))
                .toList();
    }

    private static Optional<ModeMapEditPort> mapEditPort(BaseMap map) {
        if (map instanceof ModeRoomBackedMap backedMap) {
            ModeRoomHandle handle = backedMap.roomHandle();
            return handle == null ? Optional.empty() : handle.mapEditPort();
        }
        return Optional.empty();
    }

    private void addSpawnPoint(ServerPlayer player, ItemStack stack, BlockPos clickedPos) {
        String selectedType = getSelectedType(stack);
        String selectedMap = getSelectedMap(stack);
        String selectedTeam = getSelectedTeam(stack);
        String selectedLayerKey = getSelectedKind(stack);
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
        Optional<ModeMapEditPort> editPort = mapEditPort(map)
                .filter(port -> port.supportsPointLayer(selectedLayerKey));
        if (editPort.isEmpty()) {
            player.displayClientMessage(Component.translatable("command.codpattern.map.invalid_kind", selectedLayerKey), false);
            return;
        }
        List<ModePointData> previousPoints = editPort.get().pointLayerPoints(team.name, selectedLayerKey);
        ModePointData spawnPointData = new ModePointData(
                selectedLayerKey,
                player.serverLevel().dimension(),
                clickedPos.above(),
                player.getYRot(),
                0.0F
        );
        if (!editPort.get().addPointLayerPoint(team.name, spawnPointData)) {
            player.displayClientMessage(Component.translatable("message.fpsm.spawn_point_tool.duplicate"), false);
            return;
        }
        try {
            CodMapPersistence.saveMapOrRollback(
                    map,
                    () -> editPort.get().replacePointLayerPoints(team.name, selectedLayerKey, previousPoints));
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
        setStringTag(stack, TYPE_TAG, GameModeRegistry.canonicalize(selectedType));
    }

    public static String getSelectedType(ItemStack stack) {
        return GameModeRegistry.canonicalize(getStringTag(stack, TYPE_TAG));
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
        return ModeMapEditorSchemas.spawnPointLayerKeys(gameType);
    }

    public static String normalizeSelectedKind(String gameType, String selectedKind) {
        List<String> availableKinds = availableKindsForType(gameType);
        if (selectedKind != null && availableKinds.contains(selectedKind)) {
            return selectedKind;
        }
        return SpawnPointKind.INITIAL.serializedName();
    }

    public static void setEditMode(ItemStack stack, String editMode) {
        setStringTag(stack, EDIT_MODE_TAG, normalizeEditMode(editMode, getSelectedType(stack)));
    }

    public static String getEditMode(ItemStack stack) {
        return normalizeEditMode(getStringTag(stack, EDIT_MODE_TAG), getSelectedType(stack));
    }

    public static boolean isAreaMode(ItemStack stack) {
        return EDIT_MODE_AREA.equals(getEditMode(stack));
    }

    public static String normalizeEditMode(String editMode, String gameType) {
        if (EDIT_MODE_AREA.equalsIgnoreCase(editMode) && !availableAreaLayersForType(gameType).isEmpty()) {
            return EDIT_MODE_AREA;
        }
        return EDIT_MODE_POINT;
    }

    public static void setSelectedAreaLayer(ItemStack stack, String selectedAreaLayer) {
        setStringTag(stack, AREA_LAYER_TAG, selectedAreaLayer);
    }

    public static String getSelectedAreaLayer(ItemStack stack) {
        String stored = getStringTag(stack, AREA_LAYER_TAG);
        return normalizeSelectedAreaLayer(getSelectedType(stack), stored);
    }

    public static List<String> availableAreaLayersForType(String gameType) {
        return ModeMapEditorSchemas.areaLayerKeys(gameType);
    }

    public static String normalizeSelectedAreaLayer(String gameType, String selectedAreaLayer) {
        List<String> availableLayers = availableAreaLayersForType(gameType);
        if (selectedAreaLayer != null && availableLayers.contains(selectedAreaLayer)) {
            return selectedAreaLayer;
        }
        return availableLayers.isEmpty() ? "" : availableLayers.get(0);
    }

    public static void setAreaPos1(ItemStack stack, BlockPos pos) {
        setBlockPos(stack, AREA_POS_1_TAG, pos);
    }

    public static void setAreaPos2(ItemStack stack, BlockPos pos) {
        setBlockPos(stack, AREA_POS_2_TAG, pos);
    }

    public static BlockPos getAreaPos1(ItemStack stack) {
        return getBlockPos(stack, AREA_POS_1_TAG);
    }

    public static BlockPos getAreaPos2(ItemStack stack) {
        return getBlockPos(stack, AREA_POS_2_TAG);
    }

    private static void setBlockPos(ItemStack stack, String tag, BlockPos pos) {
        CompoundTag compoundTag = stack.getOrCreateTag();
        if (pos == null) {
            compoundTag.remove(tag);
            return;
        }
        compoundTag.putLong(tag, pos.asLong());
    }

    private static BlockPos getBlockPos(ItemStack stack, String tag) {
        CompoundTag compoundTag = stack.getTag();
        if (compoundTag == null || !compoundTag.contains(tag, Tag.TAG_LONG)) {
            return null;
        }
        return BlockPos.of(compoundTag.getLong(tag));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltip, isAdvanced);
        tooltip.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Edit mode")
                .append(": ")
                .append(Component.literal(getEditMode(stack)).withStyle(ChatFormatting.AQUA)));
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
        if (isAreaMode(stack)) {
            tooltip.add(Component.literal("Area layer")
                    .append(": ")
                    .append(Component.literal(getSelectedAreaLayer(stack)).withStyle(ChatFormatting.GOLD)));
            tooltip.add(Component.literal("Area pos 1")
                    .append(": ")
                    .append(Component.literal(MapCreatorTool.formatPos(getAreaPos1(stack))).withStyle(ChatFormatting.YELLOW)));
            tooltip.add(Component.literal("Area pos 2")
                    .append(": ")
                    .append(Component.literal(MapCreatorTool.formatPos(getAreaPos2(stack))).withStyle(ChatFormatting.YELLOW)));
        }
        tooltip.add(Component.translatable("tooltip.fpsm.separator").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.fpsm.spawn_point_tool.left_click"));
        tooltip.add(Component.translatable("tooltip.fpsm.spawn_point_tool.ctrl_right_click"));
    }
}
