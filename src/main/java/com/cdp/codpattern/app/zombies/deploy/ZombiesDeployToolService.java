package com.cdp.codpattern.app.zombies.deploy;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.model.RoomId;
import com.cdp.codpattern.app.zombies.map.ZombiesMapObjects;
import com.cdp.codpattern.app.zombies.map.ZombiesMapSnapshot;
import com.cdp.codpattern.app.zombies.map.object.ZombiesAmmoBoxData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesArmorStationData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesInitialSpawnData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesPowerSwitchData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesSodaMachineData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesUltimateMachineData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesWeaponWallData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesZombieSpawnData;
import com.cdp.codpattern.app.zombies.service.ZombiesMapOccupancyService;
import com.cdp.codpattern.app.zombies.validation.ZombiesMapValidationProfile;
import com.cdp.codpattern.app.zombies.validation.ZombiesMapValidationReport;
import com.cdp.codpattern.app.zombies.validation.ZombiesMapValidator;
import com.cdp.codpattern.app.zombies.validation.ZombiesValidationIssue;
import com.cdp.codpattern.compat.fpsmatch.data.CodMapPersistence;
import com.cdp.codpattern.compat.fpsmatch.map.ZombiesMap;
import com.phasetranscrystal.fpsmatch.common.service.MapCreationService;
import com.phasetranscrystal.fpsmatch.common.item.ZombiesDeployTool;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ZombiesDeployToolService {
    private static final ZombiesDeployToolService INSTANCE = new ZombiesDeployToolService();

    public static ZombiesDeployToolService instance() {
        return INSTANCE;
    }

    private ZombiesDeployToolService() {
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> snapshot(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request,
            String statusKey,
            String statusCode,
            String statusDetail
    ) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        return ZombiesDeployServiceResult.success(
                buildSnapshot(draft, statusKey, statusCode, statusDetail),
                statusKey,
                statusDetail);
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> saveSelections(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request
    ) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        ZombiesDeployTool.saveDraft(stack, draft);
        return snapshot(
                player,
                stack,
                draft,
                "message.codpattern.zombies.deploy.draft_saved",
                "ok",
                "");
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> setField(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request,
            String fieldKey,
            String fieldValue
    ) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        Map<String, String> fields = new LinkedHashMap<>(draft.fields());
        if (fieldKey != null && !fieldKey.isBlank()) {
            fields.put(fieldKey.trim(), fieldValue == null ? "" : fieldValue);
        }
        return saveSelections(player, stack, draft.withFields(fields));
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> validateMap(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request
    ) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        ZombiesDeployTool.saveDraft(stack, draft);
        return snapshot(
                player,
                stack,
                draft,
                "message.codpattern.zombies.deploy.validation_ran",
                "ok",
                "");
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> selectWorkspaceStage(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request
    ) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        ZombiesDeployTool.saveDraft(stack, draft);
        return snapshot(player, stack, draft, "message.codpattern.zombies.deploy.refreshed", "ok", "");
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> selectCapturePreset(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request
    ) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        ZombiesDeployTool.saveDraft(stack, draft);
        return snapshot(player, stack, draft, "message.codpattern.zombies.deploy.refreshed", "ok", "");
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> createMap(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request
    ) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        MapCreationService.Result created = MapCreationService.instance().createMap(
                player,
                BuiltInGameModes.ZOMBIES,
                draft.draftMapName(),
                draft.mapPos1(),
                draft.mapPos2());
        if (!created.success()) {
            ZombiesDeployTool.saveDraft(stack, draft);
            ZombiesDeploySnapshot snapshot = buildSnapshot(draft, created.messageKey(), created.code(), String.join(" ", created.arguments()));
            return ZombiesDeployServiceResult.failure(
                    created.code(),
                    created.messageKey(),
                    snapshot,
                    created.arguments().toArray(String[]::new));
        }
        ZombiesDeployDraft updated = new ZombiesDeployDraft(
                ZombiesDeployDraft.STAGE_OBJECT_MARKING,
                created.mapName(),
                "",
                draft.mapPos1(),
                draft.mapPos2(),
                ZombiesDeployFieldSchema.INITIAL,
                ZombiesDeployDraft.CAPTURE_DEFAULT,
                -1,
                draft.validationView(),
                defaultFields(player, ZombiesDeployFieldSchema.INITIAL));
        ZombiesDeployTool.saveDraft(stack, updated);
        return snapshot(player, stack, updated, created.messageKey(), "ok.map_created", created.mapName());
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> updateMapArea(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request
    ) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        if (draft.mapPos1() == null || draft.mapPos2() == null) {
            return failure(player, stack, draft, "map.invalid_area", "message.fpsm.map_creator_tool.invalid_area", "");
        }
        Optional<ZombiesMap> resolvedMap = resolveMap(draft.selectedMap());
        if (resolvedMap.isEmpty()) {
            return failure(player, stack, draft, "map.not_found", "message.codpattern.zombies.deploy.map_not_found", draft.selectedMap());
        }
        ZombiesMap oldMap = resolvedMap.get();
        if (ZombiesMapOccupancyService.instance().isOccupied(BuiltInGameModes.ZOMBIES, oldMap.getMapName())) {
            return failure(player, stack, draft, "map.active_area_update_deferred", "message.codpattern.zombies.deploy.saved_active_map", "当前地图进行中，范围更新已拒绝");
        }

        FPSMCore core = FPSMCore.getInstance();
        ZombiesMap replacement = new ZombiesMap(oldMap.getServerLevel(), oldMap.getMapName(), new AreaData(draft.mapPos1(), draft.mapPos2()));
        replacement.applyObjects(oldMap.objects());
        oldMap.matchEndTeleportPoint().ifPresent(replacement::setMatchEndTeleportPoint);
        core.unregisterMap(oldMap);
        core.registerMap(BuiltInGameModes.ZOMBIES, replacement);
        try {
            CodMapPersistence.saveMapOrRollback(replacement, () -> {
                core.unregisterMap(replacement);
                core.registerMap(BuiltInGameModes.ZOMBIES, oldMap);
            });
        } catch (RuntimeException e) {
            ZombiesDeployTool.saveDraft(stack, draft);
            ZombiesDeploySnapshot snapshot = buildSnapshot(draft, "message.codpattern.zombies.deploy.save_failed_rollback", "save_failed_rolled_back", oldMap.getMapName());
            return ZombiesDeployServiceResult.failure("save_failed_rolled_back", "message.codpattern.zombies.deploy.save_failed_rollback", snapshot, oldMap.getMapName());
        }
        replacement.syncToClient();
        ZombiesDeployTool.saveDraft(stack, draft);
        return snapshot(player, stack, draft, "message.codpattern.zombies.deploy.object_saved", "ok.map_area_updated", oldMap.getMapName());
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> captureWorldClick(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request,
            BlockPos clickedPos,
            boolean leftClick
    ) {
        if (clickedPos == null) {
            return failure(player, stack, request, "capture.no_block", "message.codpattern.zombies.deploy.no_look_block", "");
        }
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        if (ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(draft.workspaceStage())) {
            ZombiesDeployDraft updated = leftClick
                    ? draft.withMapDraft(draft.draftMapName(), clickedPos, draft.mapPos2())
                    : draft.withMapDraft(draft.draftMapName(), draft.mapPos1(), clickedPos);
            if (leftClick) {
                ZombiesDeployTool.setAreaPos1(stack, clickedPos);
            } else {
                ZombiesDeployTool.setAreaPos2(stack, clickedPos);
            }
            ZombiesDeployTool.saveDraft(stack, updated);
            return snapshot(
                    player,
                    stack,
                    updated,
                    leftClick ? "message.codpattern.zombies.deploy.area_pos1" : "message.codpattern.zombies.deploy.area_pos2",
                    leftClick ? "capture.map_pos1" : "capture.map_pos2",
                    formatPos(clickedPos));
        }

        String target = ZombiesDeployCaptureBinding.forDraft(draft).target(leftClick
                ? ZombiesDeployCaptureBinding.CaptureSlot.A
                : ZombiesDeployCaptureBinding.CaptureSlot.B);
        if (target.isBlank()) {
            ZombiesDeployTool.saveDraft(stack, draft);
            return snapshot(player, stack, draft, "message.codpattern.zombies.deploy.refreshed", "capture.slot_empty", formatPos(clickedPos));
        }
        Map<String, String> fields = new LinkedHashMap<>(draft.fields());
        fields.put("dimension", player.serverLevel().dimension().location().toString());
        setPosition(fields, target, clickedPos);
        ZombiesDeployDraft updated = draft.withFields(fields);
        ZombiesDeployTool.saveDraft(stack, updated);
        return snapshot(player, stack, updated, "message.codpattern.zombies.deploy.captured_look_block", "capture." + target, formatPos(clickedPos));
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> addObject(ServerPlayer player, ItemStack stack, ZombiesDeployDraft request) {
        return editObject(player, stack, request, ZombiesDeployObjectEditor.Operation.ADD, "object.added");
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> updateObject(ServerPlayer player, ItemStack stack, ZombiesDeployDraft request) {
        return editObject(player, stack, request, ZombiesDeployObjectEditor.Operation.UPDATE, "object.updated");
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> duplicateObject(ServerPlayer player, ItemStack stack, ZombiesDeployDraft request) {
        return editObject(player, stack, request, ZombiesDeployObjectEditor.Operation.DUPLICATE, "object.duplicated");
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> deleteObject(ServerPlayer player, ItemStack stack, ZombiesDeployDraft request) {
        return editObject(player, stack, request, ZombiesDeployObjectEditor.Operation.DELETE, "object.deleted");
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> clearObjectType(ServerPlayer player, ItemStack stack, ZombiesDeployDraft request) {
        return editObject(player, stack, request, ZombiesDeployObjectEditor.Operation.CLEAR, "object.cleared");
    }

    private ZombiesDeployServiceResult<ZombiesDeploySnapshot> editObject(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request,
            ZombiesDeployObjectEditor.Operation operation,
            String successCode
    ) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        Optional<ZombiesMap> resolvedMap = resolveMap(draft.selectedMap());
        if (resolvedMap.isEmpty()) {
            return failure(
                    player,
                    stack,
                    draft,
                    "map.not_found",
                    "message.codpattern.zombies.deploy.map_not_found",
                    draft.selectedMap());
        }

        ZombiesMap map = resolvedMap.get();
        ZombiesMapObjects previousObjects = map.objects();
        ZombiesDeployObjectEditor.EditResult edit = ZombiesDeployObjectEditor.edit(
                previousObjects,
                operation,
                draft.objectType(),
                draft.selectedIndex(),
                draft.fields());
        ZombiesDeployDraft resultDraft = new ZombiesDeployDraft(
                draft.workspaceStage(),
                draft.selectedMap(),
                draft.draftMapName(),
                draft.mapPos1(),
                draft.mapPos2(),
                draft.objectType(),
                draft.capturePreset(),
                edit.selectedIndex(),
                draft.validationView(),
                edit.fields());
        if (!edit.success()) {
            ZombiesDeployTool.saveDraft(stack, resultDraft);
            ZombiesDeploySnapshot snapshot = buildSnapshot(
                    resultDraft,
                    "message.codpattern.zombies.deploy.object_invalid",
                    edit.code(),
                    edit.detail());
            return ZombiesDeployServiceResult.failure(
                    edit.code(),
                    "message.codpattern.zombies.deploy.object_invalid",
                    snapshot,
                    edit.detail());
        }

        map.applyObjects(edit.objects());
        try {
            CodMapPersistence.saveMapOrRollback(map, () -> map.applyObjects(previousObjects));
        } catch (RuntimeException e) {
            ZombiesDeployTool.saveDraft(stack, resultDraft);
            ZombiesDeploySnapshot snapshot = buildSnapshot(
                    resultDraft,
                    "message.codpattern.zombies.deploy.save_failed_rollback",
                    "save_failed_rolled_back",
                    map.getMapName());
            return ZombiesDeployServiceResult.failure(
                    "save_failed_rolled_back",
                    "message.codpattern.zombies.deploy.save_failed_rollback",
                    snapshot,
                    map.getMapName());
        }

        map.syncToClient();
        ZombiesDeployTool.saveDraft(stack, resultDraft);
        boolean activeMap = ZombiesMapOccupancyService.instance().isOccupied(BuiltInGameModes.ZOMBIES, map.getMapName());
        String statusKey = activeMap
                ? "message.codpattern.zombies.deploy.saved_active_map"
                : "message.codpattern.zombies.deploy.object_saved";
        String statusCode = activeMap ? "ok.active_map_next_round" : successCode;
        String statusDetail = activeMap ? "当前局使用快照，下局生效" : Integer.toString(edit.affectedCount());
        ZombiesDeploySnapshot snapshot = buildSnapshot(resultDraft, statusKey, statusCode, statusDetail);
        return ZombiesDeployServiceResult.success(snapshot, statusKey, statusDetail);
    }

    private ZombiesDeployServiceResult<ZombiesDeploySnapshot> failure(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request,
            String code,
            String messageKey,
            String detail
    ) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        ZombiesDeployTool.saveDraft(stack, draft);
        ZombiesDeploySnapshot snapshot = buildSnapshot(draft, messageKey, code, detail);
        return ZombiesDeployServiceResult.failure(code, messageKey, snapshot, detail);
    }

    private ZombiesDeployDraft normalizeDraft(ServerPlayer player, ItemStack stack, ZombiesDeployDraft request) {
        ZombiesDeployDraft stored = stack == null ? ZombiesDeployDraft.empty() : ZombiesDeployTool.getDraft(stack);
        ZombiesDeployDraft base = request == null ? stored : request;
        List<String> maps = availableMaps();
        String selectedMap = selectMap(base.selectedMap(), stored.selectedMap(), maps);
        String objectType = ZombiesDeployFieldSchema.normalizeObjectType(base.objectType());
        String capturePreset = ZombiesDeployDraft.normalizeCapturePreset(base.capturePreset(), objectType);
        int count = resolveMap(selectedMap).map(map -> objectSummaries(map.objects(), objectType).size()).orElse(0);
        int selectedIndex = count <= 0 ? -1 : Math.max(0, Math.min(base.selectedIndex() < 0 ? 0 : base.selectedIndex(), count - 1));
        Map<String, String> fields = base.fields().isEmpty()
                ? defaultFields(player, objectType)
                : mergeDefaults(objectType, base.fields());
        return new ZombiesDeployDraft(
                base.workspaceStage(),
                selectedMap,
                base.draftMapName(),
                base.mapPos1() == null ? stored.mapPos1() : base.mapPos1(),
                base.mapPos2() == null ? stored.mapPos2() : base.mapPos2(),
                objectType,
                capturePreset,
                selectedIndex,
                ZombiesDeployFieldSchema.normalizeProfile(base.validationView()),
                fields);
    }

    private ZombiesDeploySnapshot buildSnapshot(
            ZombiesDeployDraft draft,
            String statusKey,
            String statusCode,
            String statusDetail
    ) {
        Optional<ZombiesMap> map = resolveMap(draft.selectedMap());
        ZombiesMapObjects objects = map.map(ZombiesMap::objects).orElse(ZombiesMapObjects.EMPTY);
        List<ZombiesDeploySnapshot.ObjectSummary> summaries = objectSummaries(objects, draft.objectType());
        boolean activeMap = map
                .map(value -> ZombiesMapOccupancyService.instance().isOccupied(BuiltInGameModes.ZOMBIES, value.getMapName()))
                .orElse(false);
        ZombiesDeployCaptureBinding binding = ZombiesDeployCaptureBinding.forDraft(draft);
        return new ZombiesDeploySnapshot(
                availableMaps(),
                draft.workspaceStage(),
                draft.selectedMap(),
                draft.draftMapName(),
                draft.mapPos1(),
                draft.mapPos2(),
                ZombiesDeployFieldSchema.objectTypes().stream()
                        .map(type -> new ZombiesDeploySnapshot.ObjectTypeOption(type.key(), type.labelKey()))
                        .toList(),
                draft.objectType(),
                draft.capturePreset(),
                binding.slotA(),
                binding.slotB(),
                draft.selectedIndex(),
                summaries,
                fieldValues(draft.objectType(), draft.fields()),
                draft.validationView(),
                ZombiesDeployFieldSchema.profiles(),
                map.map(value -> validationLines(value, draft.validationView())).orElse(List.of()),
                map.map(this::validationSummaries).orElse(List.of()),
                objectCounts(objects),
                stepStatuses(map.isPresent(), objects),
                activeMap,
                map.map(value -> Math.abs(Objects.hash(value.getMapName(), value.objects(), value.matchEndTeleportPoint()))).orElse(0),
                Objects.requireNonNullElse(statusKey, ""),
                Objects.requireNonNullElse(statusCode, ""),
                Objects.requireNonNullElse(statusDetail, ""));
    }

    private List<String> availableMaps() {
        return FPSMCore.getInstance().getMapNamesWithType(BuiltInGameModes.ZOMBIES);
    }

    private Optional<ZombiesMap> resolveMap(String mapName) {
        String selected = Objects.requireNonNullElse(mapName, "").trim();
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        return FPSMCore.getInstance()
                .getMapByTypeWithName(BuiltInGameModes.ZOMBIES, selected)
                .filter(ZombiesMap.class::isInstance)
                .map(ZombiesMap.class::cast);
    }

    private String selectMap(String requested, String stored, List<String> maps) {
        if (maps.isEmpty()) {
            return "";
        }
        String requestedMap = Objects.requireNonNullElse(requested, "").trim();
        if (maps.contains(requestedMap)) {
            return requestedMap;
        }
        String storedMap = Objects.requireNonNullElse(stored, "").trim();
        return maps.contains(storedMap) ? storedMap : maps.get(0);
    }

    private Map<String, String> defaultFields(ServerPlayer player, String objectType) {
        Map<String, String> fields = new LinkedHashMap<>(ZombiesDeployFieldSchema.defaultFields(objectType));
        if (player != null) {
            fields.put("dimension", player.serverLevel().dimension().location().toString());
            BlockPos pos = player.blockPosition();
            setPosition(fields, "pos", pos);
            setPosition(fields, "interaction", pos);
            setPosition(fields, "areaFrom", pos);
            setPosition(fields, "areaTo", pos);
            fields.computeIfPresent("yaw", (key, value) -> Float.toString(player.getYRot()));
            fields.computeIfPresent("pitch", (key, value) -> Float.toString(player.getXRot()));
        }
        return fields;
    }

    private Map<String, String> mergeDefaults(String objectType, Map<String, String> fields) {
        Map<String, String> merged = new LinkedHashMap<>(ZombiesDeployFieldSchema.defaultFields(objectType));
        if (fields != null) {
            fields.forEach((key, value) -> {
                if (merged.containsKey(key)) {
                    merged.put(key, value == null ? "" : value);
                }
            });
        }
        return merged;
    }

    private List<ZombiesDeploySnapshot.FieldValue> fieldValues(String objectType, Map<String, String> fields) {
        Map<String, String> resolvedFields = mergeDefaults(objectType, fields);
        return ZombiesDeployFieldSchema.objectType(objectType)
                .orElse(ZombiesDeployFieldSchema.objectTypes().get(0))
                .fields()
                .stream()
                .map(field -> new ZombiesDeploySnapshot.FieldValue(
                        field.key(),
                        field.labelKey(),
                        field.type(),
                        resolvedFields.getOrDefault(field.key(), field.defaultValue()),
                        field.editable()))
                .toList();
    }

    private List<ZombiesDeploySnapshot.ObjectSummary> objectSummaries(ZombiesMapObjects objects, String objectType) {
        ZombiesMapObjects resolved = objects == null ? ZombiesMapObjects.EMPTY : objects;
        String type = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        List<ZombiesDeploySnapshot.ObjectSummary> summaries = new ArrayList<>();
        switch (type) {
            case ZombiesDeployFieldSchema.INITIAL -> {
                for (int i = 0; i < resolved.initialSpawns().size(); i++) {
                    ZombiesInitialSpawnData data = resolved.initialSpawns().get(i);
                    summaries.add(summary(i, type, "INITIAL#" + (i + 1), "INITIAL #" + (i + 1), detail(data.dimension(), data.pos())));
                }
            }
            case ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> {
                for (int i = 0; i < resolved.zombieSpawns().size(); i++) {
                    ZombiesZombieSpawnData data = resolved.zombieSpawns().get(i);
                    summaries.add(summary(i, type, data.objectId(), "group " + data.group(), detail(data.dimension(), data.pos())));
                }
            }
            case ZombiesDeployFieldSchema.BARRIER -> {
                for (int i = 0; i < resolved.barriers().size(); i++) {
                    ZombiesBarrierData data = resolved.barriers().get(i);
                    summaries.add(summary(i, type, data.objectId(), "group " + data.group(), formatPos(data.areaFrom()) + " -> " + formatPos(data.areaTo())));
                }
            }
            case ZombiesDeployFieldSchema.WEAPON_WALL -> {
                for (int i = 0; i < resolved.weaponWalls().size(); i++) {
                    ZombiesWeaponWallData data = resolved.weaponWalls().get(i);
                    summaries.add(summary(i, type, data.objectId(), "level " + data.weaponLevel(), detail(data.dimension(), data.pos())));
                }
            }
            case ZombiesDeployFieldSchema.AMMO_BOX -> {
                for (int i = 0; i < resolved.ammoBoxes().size(); i++) {
                    ZombiesAmmoBoxData data = resolved.ammoBoxes().get(i);
                    summaries.add(summary(i, type, data.objectId(), "prices " + data.pricesByWeaponLevel().size(), detail(data.dimension(), data.pos())));
                }
            }
            case ZombiesDeployFieldSchema.ARMOR_STATION -> {
                for (int i = 0; i < resolved.armorStations().size(); i++) {
                    ZombiesArmorStationData data = resolved.armorStations().get(i);
                    summaries.add(summary(i, type, data.objectId(), "armor " + data.armorLevel(), detail(data.dimension(), data.pos())));
                }
            }
            case ZombiesDeployFieldSchema.POWER_SWITCH -> resolved.powerSwitch().ifPresent(data ->
                    summaries.add(summary(0, type, data.objectId(), data.objectId(), detail(data.dimension(), data.pos()))));
            case ZombiesDeployFieldSchema.SODA_MACHINE -> {
                for (int i = 0; i < resolved.sodaMachines().size(); i++) {
                    ZombiesSodaMachineData data = resolved.sodaMachines().get(i);
                    summaries.add(summary(i, type, data.objectId(), data.buffId(), detail(data.dimension(), data.pos())));
                }
            }
            case ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> {
                for (int i = 0; i < resolved.ultimateMachines().size(); i++) {
                    ZombiesUltimateMachineData data = resolved.ultimateMachines().get(i);
                    summaries.add(summary(i, type, data.objectId(), "max " + data.maxUpgradeLevel(), detail(data.dimension(), data.pos())));
                }
            }
            default -> {
            }
        }
        return summaries;
    }

    private ZombiesDeploySnapshot.ObjectSummary summary(int index, String type, String objectId, String primary, String detail) {
        return new ZombiesDeploySnapshot.ObjectSummary(index, type, objectId, primary, detail);
    }

    private List<ZombiesDeploySnapshot.ValidationLine> validationLines(ZombiesMap map, String profileKey) {
        try {
            ZombiesMapValidationProfile profile = switch (ZombiesDeployFieldSchema.normalizeProfile(profileKey)) {
                case ZombiesDeployFieldSchema.PROFILE_MVP2 -> ZombiesMapValidationProfile.MVP2_PURCHASES;
                case ZombiesDeployFieldSchema.PROFILE_MVP3 -> ZombiesMapValidationProfile.MVP3_FULL_INITIAL;
                default -> ZombiesMapValidationProfile.MVP1_MINIMAL;
            };
            ZombiesMapSnapshot snapshot = ZombiesMapSnapshot.fromMapObjects(
                    RoomId.of(BuiltInGameModes.ZOMBIES, map.getMapName()),
                    map.getMapName(),
                    map.matchEndTeleportPoint().isPresent(),
                    map.getServerLevel().dimension().location().toString(),
                    ZombiesMapSnapshot.BoundsSnapshot.fromAreaData(map.getMapArea()),
                    map.objects());
            ZombiesMapValidationReport report = new ZombiesMapValidator(profile).validate(snapshot);
            return report.issues().stream().map(this::validationLine).toList();
        } catch (RuntimeException e) {
            return List.of(new ZombiesDeploySnapshot.ValidationLine(
                    "error",
                    "validation.exception",
                    "validation",
                    "Validation failed while opening the deploy tool: " + e.getMessage()));
        }
    }

    private ZombiesDeploySnapshot.ValidationLine validationLine(ZombiesValidationIssue issue) {
        return new ZombiesDeploySnapshot.ValidationLine(
                issue.isError() ? "error" : "warning",
                issue.code().key(),
                issue.subject(),
                issue.message());
    }

    private List<ZombiesDeploySnapshot.ValidationSummary> validationSummaries(ZombiesMap map) {
        List<ZombiesDeploySnapshot.ValidationSummary> summaries = new ArrayList<>();
        for (String profile : ZombiesDeployFieldSchema.profiles()) {
            List<ZombiesDeploySnapshot.ValidationLine> lines = validationLines(map, profile);
            int errors = 0;
            int warnings = 0;
            for (ZombiesDeploySnapshot.ValidationLine line : lines) {
                if ("error".equalsIgnoreCase(line.severity())) {
                    errors++;
                } else if ("warning".equalsIgnoreCase(line.severity())) {
                    warnings++;
                }
            }
            summaries.add(new ZombiesDeploySnapshot.ValidationSummary(profile, errors, warnings));
        }
        return summaries;
    }

    private List<ZombiesDeploySnapshot.ObjectTypeCount> objectCounts(ZombiesMapObjects objects) {
        ZombiesMapObjects resolved = objects == null ? ZombiesMapObjects.EMPTY : objects;
        return ZombiesDeployFieldSchema.objectTypes().stream()
                .map(type -> new ZombiesDeploySnapshot.ObjectTypeCount(
                        type.key(),
                        countObjects(resolved, type.key()),
                        type.singleObject(),
                        requiredObjectType(type.key())))
                .toList();
    }

    private int countObjects(ZombiesMapObjects objects, String objectType) {
        return switch (ZombiesDeployFieldSchema.normalizeObjectType(objectType)) {
            case ZombiesDeployFieldSchema.INITIAL -> objects.initialSpawns().size();
            case ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> objects.zombieSpawns().size();
            case ZombiesDeployFieldSchema.BARRIER -> objects.barriers().size();
            case ZombiesDeployFieldSchema.WEAPON_WALL -> objects.weaponWalls().size();
            case ZombiesDeployFieldSchema.AMMO_BOX -> objects.ammoBoxes().size();
            case ZombiesDeployFieldSchema.ARMOR_STATION -> objects.armorStations().size();
            case ZombiesDeployFieldSchema.POWER_SWITCH -> objects.powerSwitch().isPresent() ? 1 : 0;
            case ZombiesDeployFieldSchema.SODA_MACHINE -> objects.sodaMachines().size();
            case ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> objects.ultimateMachines().size();
            default -> 0;
        };
    }

    private boolean requiredObjectType(String objectType) {
        return switch (ZombiesDeployFieldSchema.normalizeObjectType(objectType)) {
            case ZombiesDeployFieldSchema.INITIAL,
                 ZombiesDeployFieldSchema.ZOMBIE_SPAWN,
                 ZombiesDeployFieldSchema.BARRIER,
                 ZombiesDeployFieldSchema.POWER_SWITCH,
                 ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> true;
            default -> false;
        };
    }

    private List<ZombiesDeploySnapshot.StepStatus> stepStatuses(boolean hasMap, ZombiesMapObjects objects) {
        ZombiesMapObjects resolved = objects == null ? ZombiesMapObjects.EMPTY : objects;
        boolean hasPurchases = !resolved.weaponWalls().isEmpty()
                || !resolved.ammoBoxes().isEmpty()
                || !resolved.armorStations().isEmpty()
                || !resolved.sodaMachines().isEmpty()
                || !resolved.ultimateMachines().isEmpty();
        return List.of(
                new ZombiesDeploySnapshot.StepStatus("map", "1 地图", hasMap ? "已创建" : "未创建", hasMap),
                new ZombiesDeploySnapshot.StepStatus("initial", "2 玩家出生点", Integer.toString(resolved.initialSpawns().size()), !resolved.initialSpawns().isEmpty()),
                new ZombiesDeploySnapshot.StepStatus("zombie_spawn", "3 僵尸刷怪点", Integer.toString(resolved.zombieSpawns().size()), !resolved.zombieSpawns().isEmpty()),
                new ZombiesDeploySnapshot.StepStatus("barrier", "4 屏障", Integer.toString(resolved.barriers().size()), !resolved.barriers().isEmpty()),
                new ZombiesDeploySnapshot.StepStatus("interact", "5 交互对象", hasPurchases ? "已放置" : "缺失", hasPurchases),
                new ZombiesDeploySnapshot.StepStatus("validate", "6 校验", "查看右侧", hasMap)
        );
    }

    private void setPosition(Map<String, String> fields, String prefix, BlockPos pos) {
        if (!fields.containsKey(prefix + "X")) {
            return;
        }
        fields.put(prefix + "X", Integer.toString(pos.getX()));
        fields.put(prefix + "Y", Integer.toString(pos.getY()));
        fields.put(prefix + "Z", Integer.toString(pos.getZ()));
    }

    private String detail(ResourceKey<Level> dimension, BlockPos pos) {
        String dimensionId = dimension == null || dimension.location() == null ? "" : dimension.location().toString();
        return dimensionId + " " + formatPos(pos);
    }

    private String formatPos(BlockPos pos) {
        return pos == null ? "-" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
