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
import com.cdp.codpattern.common.block.CodPatternBlockRegister;
import com.phasetranscrystal.fpsmatch.common.service.MapCreationService;
import com.phasetranscrystal.fpsmatch.common.item.ZombiesDeployTool;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class ZombiesDeployToolService {
    private static final ZombiesDeployToolService INSTANCE = new ZombiesDeployToolService();
    private static final String LOOK_AT_X = "lookAtX";
    private static final String LOOK_AT_Y = "lookAtY";
    private static final String LOOK_AT_Z = "lookAtZ";

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
                buildSnapshot(player, draft, statusKey, statusCode, statusDetail),
                statusKey,
                statusDetail);
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> saveSelections(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request
    ) {
        ZombiesDeployDraft draft = selectionStateDraft(player, stack, request);
        ZombiesDeployTool.saveDraft(stack, draft);
        return ZombiesDeployServiceResult.success(
                buildSnapshot(
                        player,
                        draft,
                        "message.codpattern.zombies.deploy.selections_saved",
                        "ok",
                        ""),
                "message.codpattern.zombies.deploy.selections_saved",
                "");
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> selectObjectType(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request
    ) {
        ZombiesDeployDraft draft = selectionStateDraft(player, stack, request);
        ZombiesDeployTool.setAreaPos1(stack, null);
        ZombiesDeployTool.setAreaPos2(stack, null);
        ZombiesDeployTool.saveDraft(stack, draft);
        return ZombiesDeployServiceResult.success(
                buildSnapshot(
                        player,
                        draft,
                        "message.codpattern.zombies.deploy.refreshed",
                        "ok",
                        ""),
                "message.codpattern.zombies.deploy.refreshed",
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
        if (ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(draft.workspaceStage())) {
            return failure(player, stack, draft, "select_object_first", "message.codpattern.zombies.deploy.select_object_first", "");
        }
        Map<String, String> fields = new LinkedHashMap<>(draft.fields());
        if (fieldKey != null && !fieldKey.isBlank()) {
            fields.put(fieldKey.trim(), fieldValue == null ? "" : fieldValue);
        }
        ZombiesDeployDraft updated = draft.withFields(fields);
        if (draft.selectedIndex() < 0) {
            ZombiesDeployTool.saveDraft(stack, updated);
            return snapshot(
                    player,
                    stack,
                    updated,
                    "message.codpattern.zombies.deploy.selections_saved",
                    "object.field_staged",
                    fieldKey == null ? "" : fieldKey);
        }
        return editObject(player, stack, updated, ZombiesDeployObjectEditor.Operation.UPDATE, "object.field_updated");
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

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> saveAndValidateMvp1(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request
    ) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        ZombiesDeployTool.saveDraft(stack, draft);
        Optional<ZombiesMap> resolvedMap = resolveMap(draft.selectedMap());
        if (resolvedMap.isEmpty()) {
            return failure(player, stack, draft, "map.not_found", "message.codpattern.zombies.deploy.map_not_found", draft.selectedMap());
        }
        int errors = mvp1Errors(resolvedMap.get());
        String code = errors > 0 ? "validation.mvp1.errors" : "validation.mvp1.ok";
        ZombiesDeploySnapshot snapshot = buildSnapshot(player, draft, "message.codpattern.zombies.deploy.validation_ran", code, Integer.toString(errors));
        if (errors > 0) {
            return ZombiesDeployServiceResult.failure(code, "message.codpattern.zombies.deploy.validation_ran", snapshot, Integer.toString(errors));
        }
        return ZombiesDeployServiceResult.success(snapshot, "message.codpattern.zombies.deploy.validation_ran", Integer.toString(errors));
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> jumpToIssue(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request,
            String issueCode,
            String issueSubject
    ) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        Optional<ZombiesMap> resolvedMap = resolveMap(draft.selectedMap());
        if (resolvedMap.isEmpty()) {
            return failure(player, stack, draft, "map.not_found", "message.codpattern.zombies.deploy.map_not_found", draft.selectedMap());
        }
        ZombiesMapObjects objects = resolvedMap.get().objects();
        IssueTarget target = resolveIssueTarget(issueCode, issueSubject, draft, objects);
        return jumpToResolvedIssueTarget(player, stack, draft, objects, target, issueCode);
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> jumpToIssueTarget(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request,
            String issueCode,
            String issueSubject,
            String workflowStep,
            String targetObjectType,
            int targetIndex,
            boolean mapStage
    ) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        Optional<ZombiesMap> resolvedMap = resolveMap(draft.selectedMap());
        if (resolvedMap.isEmpty()) {
            return failure(player, stack, draft, "map.not_found", "message.codpattern.zombies.deploy.map_not_found", draft.selectedMap());
        }
        ZombiesMapObjects objects = resolvedMap.get().objects();
        IssueTarget target = resolveProvidedIssueTarget(
                workflowStep,
                targetObjectType,
                targetIndex,
                mapStage,
                issueCode,
                issueSubject,
                draft,
                objects);
        return jumpToResolvedIssueTarget(player, stack, draft, objects, target, issueCode);
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

    private ZombiesDeployServiceResult<ZombiesDeploySnapshot> jumpToResolvedIssueTarget(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft draft,
            ZombiesMapObjects objects,
            IssueTarget target,
            String issueCode
    ) {
        ZombiesDeployDraft updated = new ZombiesDeployDraft(
                target.mapStage() ? ZombiesDeployDraft.STAGE_MAP_REGISTRATION : ZombiesDeployDraft.STAGE_OBJECT_MARKING,
                target.workflowStep(),
                draft.selectedMap(),
                draft.draftMapName(),
                draft.mapPos1(),
                draft.mapPos2(),
                target.objectType(),
                ZombiesDeployDraft.normalizeCapturePreset(draft.capturePreset(), target.objectType()),
                target.selectedIndex(),
                draft.validationView(),
                target.selectedIndex() >= 0
                        ? ZombiesDeployObjectEditor.fieldsForSnapshotSelection(objects, target.objectType(), target.selectedIndex())
                        : defaultFields(player, target.objectType()));
        ZombiesDeployTool.saveDraft(stack, updated);
        String code = Objects.requireNonNullElse(issueCode, "").trim();
        return snapshot(player, stack, updated, "message.codpattern.zombies.deploy.refreshed", "ok.jump_to_issue", code);
    }

    private IssueTarget resolveProvidedIssueTarget(
            String workflowStep,
            String targetObjectType,
            int targetIndex,
            boolean mapStage,
            String issueCode,
            String issueSubject,
            ZombiesDeployDraft draft,
            ZombiesMapObjects objects
    ) {
        String step = Objects.requireNonNullElse(workflowStep, "").trim();
        String objectType = Objects.requireNonNullElse(targetObjectType, "").trim();
        if (!isSupportedWorkflowStep(step)) {
            return resolveIssueTarget(issueCode, issueSubject, draft, objects);
        }
        if (!mapStage && !isKnownObjectType(objectType)) {
            return resolveIssueTarget(issueCode, issueSubject, draft, objects);
        }
        if (mapStage && objectType.isBlank()) {
            objectType = draft.objectType();
        }
        if (!mapStage && !isKnownObjectType(objectType)) {
            return resolveIssueTarget(issueCode, issueSubject, draft, objects);
        }
        String normalizedType = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        int normalizedIndex = normalizeTargetIndex(objects, normalizedType, targetIndex);
        return new IssueTarget(mapStage, step, normalizedType, normalizedIndex);
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> selectWorkflowStep(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request
    ) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        ZombiesMapObjects objects = resolveMap(draft.selectedMap())
                .map(ZombiesMap::objects)
                .orElse(ZombiesMapObjects.EMPTY);
        ZombiesDeployDraft updated = applyWorkflowStep(player, draft, draft.workflowStep(), objects);
        ZombiesDeployTool.saveDraft(stack, updated);
        return snapshot(player, stack, updated, "message.codpattern.zombies.deploy.refreshed", "ok", "");
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> goNextStep(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request
    ) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        String next = nextWorkflowStep(draft.workflowStep());
        ZombiesMapObjects objects = resolveMap(draft.selectedMap())
                .map(ZombiesMap::objects)
                .orElse(ZombiesMapObjects.EMPTY);
        ZombiesDeployDraft updated = applyWorkflowStep(player, draft, next, objects);
        ZombiesDeployTool.saveDraft(stack, updated);
        return snapshot(player, stack, updated, "message.codpattern.zombies.deploy.refreshed", "ok.next_step", next);
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> createMap(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request
    ) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        MapCreationService.Result created = MapCreationService.instance().createMap(
                player,
                new MapCreationService.CreateRequest(
                        BuiltInGameModes.ZOMBIES,
                        draft.draftMapName(),
                        draft.mapPos1(),
                        draft.mapPos2()));
        if (!created.success()) {
            ZombiesDeployTool.saveDraft(stack, draft);
            ZombiesDeploySnapshot snapshot = buildSnapshot(player, draft, created.messageKey(), created.code(), String.join(" ", created.arguments()));
            return ZombiesDeployServiceResult.failure(
                    created.code(),
                    created.messageKey(),
                    snapshot,
                    created.arguments().toArray(String[]::new));
        }
        ZombiesDeployDraft updated = new ZombiesDeployDraft(
                ZombiesDeployDraft.STAGE_OBJECT_MARKING,
                ZombiesDeployDraft.WORKFLOW_INITIAL,
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

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> captureWorldClick(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request,
            BlockPos placementPos,
            boolean leftClick
    ) {
        if (placementPos == null) {
            return failure(player, stack, request, "capture.no_block", "message.codpattern.zombies.deploy.no_look_block", "");
        }
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        if (ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(draft.workspaceStage())) {
            ZombiesDeployDraft updated = leftClick
                    ? draft.withMapDraft(draft.draftMapName(), placementPos, draft.mapPos2())
                    : draft.withMapDraft(draft.draftMapName(), draft.mapPos1(), placementPos);
            if (leftClick) {
                ZombiesDeployTool.setAreaPos1(stack, placementPos);
            } else {
                ZombiesDeployTool.setAreaPos2(stack, placementPos);
            }
            ZombiesDeployTool.saveDraft(stack, updated);
            return snapshot(
                    player,
                    stack,
                    updated,
                    leftClick ? "message.codpattern.zombies.deploy.area_pos1" : "message.codpattern.zombies.deploy.area_pos2",
                    leftClick ? "capture.map_pos1" : "capture.map_pos2",
                    formatPos(placementPos));
        }

        return deployWorldClick(player, stack, draft, placementPos, leftClick);
    }

    private ZombiesDeployServiceResult<ZombiesDeploySnapshot> deployWorldClick(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft draft,
            BlockPos placementPos,
            boolean leftClick
    ) {
        Optional<ZombiesMap> resolvedMap = resolveMap(draft.selectedMap());
        if (resolvedMap.isEmpty()) {
            return failure(player, stack, draft, "map.not_found", "message.codpattern.zombies.deploy.map_not_found", draft.selectedMap());
        }

        ZombiesMapObjects objects = resolvedMap.get().objects();
        String objectType = ZombiesDeployFieldSchema.normalizeObjectType(draft.objectType());
        if (!leftClick && isRightClickNoOp(objectType)) {
            ZombiesDeploySnapshot snapshot = buildSnapshot(
                    player,
                    draft,
                    "message.codpattern.zombies.deploy.right_click_noop",
                    "right_click_noop",
                    formatPos(placementPos));
            return ZombiesDeployServiceResult.failure(
                    "right_click_noop",
                    "message.codpattern.zombies.deploy.right_click_noop",
                    snapshot,
                    formatPos(placementPos));
        }

        int selectedIndex = deployTargetIndex(
                objectType,
                normalizeTargetIndex(objects, objectType, draft.selectedIndex()),
                leftClick);
        if (ZombiesDeployFieldSchema.BARRIER.equals(objectType)) {
            return deployBarrierWorldClick(player, stack, draft, objects, selectedIndex, placementPos, leftClick);
        }

        Map<String, String> fields = fieldsForWorldClickBase(player, objects, objectType, selectedIndex, draft);
        applyWorldClickFields(player, objectType, fields, placementPos, leftClick, selectedIndex < 0);

        ZombiesDeployDraft updated = new ZombiesDeployDraft(
                ZombiesDeployDraft.STAGE_OBJECT_MARKING,
                ZombiesDeployDraft.workflowStepForObjectType(objectType),
                draft.selectedMap(),
                draft.draftMapName(),
                draft.mapPos1(),
                draft.mapPos2(),
                objectType,
                draft.capturePreset(),
                selectedIndex,
                draft.validationView(),
                fields);
        ZombiesDeployObjectEditor.Operation operation = selectedIndex >= 0
                ? ZombiesDeployObjectEditor.Operation.UPDATE
                : ZombiesDeployObjectEditor.Operation.ADD;
        return editObject(player, stack, updated, operation, leftClick ? "object.left_click_deployed" : "object.right_click_deployed");
    }

    private ZombiesDeployServiceResult<ZombiesDeploySnapshot> deployBarrierWorldClick(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft draft,
            ZombiesMapObjects objects,
            int selectedIndex,
            BlockPos placementPos,
            boolean leftClick
    ) {
        Map<String, String> fields = fieldsForWorldClickBase(player, objects, ZombiesDeployFieldSchema.BARRIER, selectedIndex, draft);
        fields.put("dimension", player.serverLevel().dimension().location().toString());
        if (selectedIndex < 0) {
            if (leftClick) {
                ZombiesDeployTool.setAreaPos1(stack, placementPos);
                ZombiesDeployTool.setAreaPos2(stack, null);
                setPosition(fields, "areaFrom", placementPos);
                setPosition(fields, "areaTo", placementPos);
                setPosition(fields, "interaction", placementPos);
                ZombiesDeployDraft updated = draftForWorldClick(draft, ZombiesDeployFieldSchema.BARRIER, -1, fields);
                ZombiesDeployTool.saveDraft(stack, updated);
                return snapshot(
                        player,
                        stack,
                        updated,
                        "message.codpattern.zombies.deploy.barrier_area_from",
                        "capture.barrier_area_from",
                        formatPos(placementPos));
            }
            BlockPos first = ZombiesDeployTool.getAreaPos1(stack);
            if (first == null) {
                return failure(
                        player,
                        stack,
                        draft,
                        "barrier_area_first_required",
                        "message.codpattern.zombies.deploy.barrier_area_first_required",
                        formatPos(placementPos));
            }
            ZombiesDeployTool.setAreaPos2(stack, placementPos);
            setPosition(fields, "areaFrom", first);
            setPosition(fields, "areaTo", placementPos);
            setPosition(fields, "interaction", first);
        } else {
            setPosition(fields, leftClick ? "areaFrom" : "areaTo", placementPos);
            if (leftClick) {
                ZombiesDeployTool.setAreaPos1(stack, placementPos);
            } else {
                ZombiesDeployTool.setAreaPos2(stack, placementPos);
            }
        }

        ZombiesDeployDraft updated = draftForWorldClick(draft, ZombiesDeployFieldSchema.BARRIER, selectedIndex, fields);
        ZombiesDeployObjectEditor.Operation operation = selectedIndex >= 0
                ? ZombiesDeployObjectEditor.Operation.UPDATE
                : ZombiesDeployObjectEditor.Operation.ADD;
        ZombiesDeployServiceResult<ZombiesDeploySnapshot> result = editObject(
                player,
                stack,
                updated,
                operation,
                leftClick ? "object.left_click_deployed" : "object.right_click_deployed");
        if (result.success() && selectedIndex < 0) {
            ZombiesDeployTool.setAreaPos1(stack, null);
            ZombiesDeployTool.setAreaPos2(stack, null);
        }
        return result;
    }

    private ZombiesDeployDraft draftForWorldClick(
            ZombiesDeployDraft draft,
            String objectType,
            int selectedIndex,
            Map<String, String> fields
    ) {
        return new ZombiesDeployDraft(
                ZombiesDeployDraft.STAGE_OBJECT_MARKING,
                ZombiesDeployDraft.workflowStepForObjectType(objectType),
                draft.selectedMap(),
                draft.draftMapName(),
                draft.mapPos1(),
                draft.mapPos2(),
                objectType,
                draft.capturePreset(),
                selectedIndex,
                draft.validationView(),
                fields);
    }

    private Map<String, String> fieldsForWorldClickBase(
            ServerPlayer player,
            ZombiesMapObjects objects,
            String objectType,
            int selectedIndex,
            ZombiesDeployDraft draft
    ) {
        String type = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        if (selectedIndex >= 0) {
            return ZombiesDeployObjectEditor.fieldsForSnapshotSelection(objects, type, selectedIndex);
        }
        Map<String, String> fields = draft.fields().isEmpty()
                ? defaultFields(player, type)
                : mergeDefaults(type, draft.fields());
        if (normalizeTargetIndex(objects, type, draft.selectedIndex()) >= 0) {
            fields.computeIfPresent("objectId", (key, ignored) -> "");
        }
        return fields;
    }

    private int deployTargetIndex(String objectType, int selectedIndex, boolean leftClick) {
        if (leftClick && isAlwaysAddOnLeftClick(objectType)) {
            return -1;
        }
        return selectedIndex;
    }

    private boolean isAlwaysAddOnLeftClick(String objectType) {
        String type = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        return switch (type) {
            case ZombiesDeployFieldSchema.INITIAL,
                 ZombiesDeployFieldSchema.ZOMBIE_SPAWN,
                 ZombiesDeployFieldSchema.BARRIER,
                 ZombiesDeployFieldSchema.WEAPON_WALL,
                 ZombiesDeployFieldSchema.AMMO_BOX,
                 ZombiesDeployFieldSchema.ARMOR_STATION,
                 ZombiesDeployFieldSchema.SODA_MACHINE,
                 ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> true;
            default -> false;
        };
    }

    private boolean isRightClickNoOp(String objectType) {
        String type = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        return isSinglePointObject(type);
    }

    private void applyWorldClickFields(
            ServerPlayer player,
            String objectType,
            Map<String, String> fields,
            BlockPos placementPos,
            boolean leftClick,
            boolean newObject
    ) {
        String dimension = player.serverLevel().dimension().location().toString();
        fields.put("dimension", dimension);
        String type = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        switch (type) {
            case ZombiesDeployFieldSchema.INITIAL,
                 ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> {
                setPosition(fields, "pos", placementPos);
                applyHorizontalYawFromPlayer(fields, player);
            }
            case ZombiesDeployFieldSchema.BARRIER -> {
                if (newObject) {
                    setPosition(fields, "areaFrom", placementPos);
                    setPosition(fields, "areaTo", placementPos);
                    setPosition(fields, "interaction", placementPos);
                } else {
                    setPosition(fields, leftClick ? "areaFrom" : "areaTo", placementPos);
                }
            }
            case ZombiesDeployFieldSchema.WEAPON_WALL,
                 ZombiesDeployFieldSchema.AMMO_BOX,
                 ZombiesDeployFieldSchema.ARMOR_STATION,
                 ZombiesDeployFieldSchema.SODA_MACHINE,
                 ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> {
                if (leftClick) {
                    setPosition(fields, "pos", placementPos);
                    setPosition(fields, "interaction", placementPos);
                }
            }
            case ZombiesDeployFieldSchema.POWER_SWITCH -> {
                if (leftClick) {
                    setPosition(fields, "pos", placementPos);
                }
            }
            default -> {
            }
        }
    }

    private boolean requiresSelectedObjectForRightClick(String objectType) {
        return false;
    }

    private int normalizeDeployTargetIndex(ZombiesMapObjects objects, String objectType, int selectedIndex) {
        return normalizeTargetIndex(objects, objectType, selectedIndex);
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

    private ZombiesDeployServiceResult<ZombiesDeploySnapshot> editObject(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request,
            ZombiesDeployObjectEditor.Operation operation,
            String successCode
    ) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        ZombiesDeployObjectEditor.Operation resolvedOperation = operation == null
                ? ZombiesDeployObjectEditor.Operation.ADD
                : operation;
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
                resolvedOperation,
                draft.objectType(),
                draft.selectedIndex(),
                draft.fields());
        ZombiesDeployDraft resultDraft = new ZombiesDeployDraft(
                draft.workspaceStage(),
                draft.workflowStep(),
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
                    player,
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

        if (shouldRejectOccupiedPositionConflict(resolvedOperation)) {
            Optional<DuplicatePosition> duplicate = findOccupiedPositionConflict(edit.objects());
            if (duplicate.isPresent()) {
                String detail = duplicate.get().detail();
                ZombiesDeployTool.saveDraft(stack, resultDraft);
                ZombiesDeploySnapshot snapshot = buildSnapshot(
                        player,
                        resultDraft,
                        "message.codpattern.zombies.deploy.duplicate_position",
                        "object.duplicate_position",
                        detail);
                return ZombiesDeployServiceResult.failure(
                        "object.duplicate_position",
                        "message.codpattern.zombies.deploy.duplicate_position",
                        snapshot,
                        detail);
            }
        }

        map.applyObjects(edit.objects());
        PlacementRollback placementRollback = null;
        try {
            if (shouldSyncPurchasableBlock(player, resolvedOperation, draft, previousObjects, edit.objects())) {
                placementRollback = syncPurchasableBlocks(
                        player.serverLevel(),
                        ZombiesDeployFieldSchema.normalizeObjectType(draft.objectType()),
                        previousObjects,
                        edit.objects(),
                        resolvedOperation);
            }
            PlacementRollback rollback = placementRollback;
            CodMapPersistence.saveMapOrRollback(map, () -> {
                map.applyObjects(previousObjects);
                restorePlacement(rollback);
            });
        } catch (RuntimeException e) {
            map.applyObjects(previousObjects);
            if (placementRollback != null) {
                restorePlacement(placementRollback);
            }
            ZombiesDeployTool.saveDraft(stack, resultDraft);
            ZombiesDeploySnapshot snapshot = buildSnapshot(
                    player,
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
        String statusDetail = activeMap ? draft.selectedMap() : Integer.toString(edit.affectedCount());
        ZombiesDeploySnapshot snapshot = buildSnapshot(player, resultDraft, statusKey, statusCode, statusDetail);
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
        ZombiesDeploySnapshot snapshot = buildSnapshot(player, draft, messageKey, code, detail);
        return ZombiesDeployServiceResult.failure(code, messageKey, snapshot, detail);
    }

    private ZombiesDeployDraft normalizeDraft(ServerPlayer player, ItemStack stack, ZombiesDeployDraft request) {
        ZombiesDeployDraft stored = stack == null ? ZombiesDeployDraft.empty() : ZombiesDeployTool.getDraft(stack);
        ZombiesDeployDraft base = request == null ? stored : request;
        List<String> maps = availableMaps();
        String selectedMap = selectMap(base.selectedMap(), stored.selectedMap(), maps);
        String objectType = ZombiesDeployFieldSchema.normalizeObjectType(base.objectType());
        String capturePreset = ZombiesDeployDraft.normalizeCapturePreset(base.capturePreset(), objectType);
        String workflowStep = ZombiesDeployDraft.normalizeWorkflowStep(base.workflowStep());
        if (ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(base.workspaceStage())) {
            workflowStep = ZombiesDeployDraft.WORKFLOW_MAP;
        } else if (ZombiesDeployDraft.WORKFLOW_MAP.equals(workflowStep)) {
            workflowStep = ZombiesDeployDraft.workflowStepForObjectType(objectType);
        }
        Optional<ZombiesMap> selectedZombiesMap = resolveMap(selectedMap);
        ZombiesMapObjects selectedObjects = selectedZombiesMap.map(ZombiesMap::objects).orElse(ZombiesMapObjects.EMPTY);
        int count = objectSummaries(selectedObjects, objectType).size();
        int selectedIndex = count <= 0 || base.selectedIndex() < 0
                ? -1
                : Math.min(base.selectedIndex(), count - 1);
        Map<String, String> fields = base.fields().isEmpty()
                ? (selectedIndex >= 0
                        ? ZombiesDeployObjectEditor.fieldsForSnapshotSelection(selectedObjects, objectType, selectedIndex)
                        : defaultFields(player, objectType))
                : mergeDefaults(objectType, base.fields());
        return new ZombiesDeployDraft(
                base.workspaceStage(),
                workflowStep,
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

    private ZombiesDeployDraft selectionStateDraft(ServerPlayer player, ItemStack stack, ZombiesDeployDraft request) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        return draft.selectedIndex() < 0 ? draft.withFields(Map.of()) : draft;
    }

    private ZombiesDeploySnapshot buildSnapshot(
            ServerPlayer player,
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
        String currentWorkflowStep = resolveWorkflowStep(draft, objects, map);
        String nextWorkflowStep = nextWorkflowStep(currentWorkflowStep);
        String blockingReason = blockingReason(currentWorkflowStep, objects, map);
        boolean nextActionEnabled = blockingReason.isBlank()
                && !ZombiesDeployDraft.WORKFLOW_VALIDATE.equals(currentWorkflowStep);
        String nextActionLabel = nextActionEnabled
                ? "gui.codpattern.zombies.deploy.next_step"
                : "gui.codpattern.zombies.deploy.step.state.done";
        ZombiesDeployCaptureBinding binding = ZombiesDeployCaptureBinding.forDraft(draft);
        boolean dirty = draftDirty(draft, map, objects);
        String nearestObjectHint = nearestObjectHint(player, draft, map, objects);
        List<ZombiesDeploySnapshot.ValidationLine> selectedValidationLines = map
                .map(value -> validationLines(value, draft.validationView()))
                .orElse(List.of());
        List<ZombiesDeploySnapshot.IssueTarget> issueTargets = buildIssueTargets(
                selectedValidationLines,
                draft,
                objects);
        return new ZombiesDeploySnapshot(
                availableMaps(),
                draft.workspaceStage(),
                currentWorkflowStep,
                nextWorkflowStep,
                blockingReason,
                nextActionLabel,
                nextActionEnabled,
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
                selectedValidationLines,
                issueTargets,
                map.map(this::validationSummaries).orElse(List.of()),
                objectCounts(objects),
                stepStatuses(map.isPresent(), objects),
                dirty,
                nearestObjectHint,
                activeMap,
                map.map(value -> Math.abs(Objects.hash(value.getMapName(), value.objects(), value.matchEndTeleportPoint()))).orElse(0),
                Objects.requireNonNullElse(statusKey, ""),
                Objects.requireNonNullElse(statusCode, ""),
                Objects.requireNonNullElse(statusDetail, ""));
    }

    private List<ZombiesDeploySnapshot.IssueTarget> buildIssueTargets(
            List<ZombiesDeploySnapshot.ValidationLine> lines,
            ZombiesDeployDraft draft,
            ZombiesMapObjects objects
    ) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<ZombiesDeploySnapshot.IssueTarget> targets = new ArrayList<>(lines.size());
        for (ZombiesDeploySnapshot.ValidationLine line : lines) {
            if (line == null) {
                targets.add(new ZombiesDeploySnapshot.IssueTarget(
                        "",
                        "",
                        draft.workflowStep(),
                        draft.objectType(),
                        draft.selectedIndex(),
                        ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(draft.workspaceStage())));
                continue;
            }
            IssueTarget target = resolveIssueTarget(line.code(), line.subject(), draft, objects);
            targets.add(new ZombiesDeploySnapshot.IssueTarget(
                    line.code(),
                    line.subject(),
                    target.workflowStep(),
                    target.objectType(),
                    target.selectedIndex(),
                    target.mapStage()));
        }
        return targets;
    }

    private ZombiesDeployDraft applyWorkflowStep(
            ServerPlayer player,
            ZombiesDeployDraft draft,
            String step,
            ZombiesMapObjects objects
    ) {
        String normalizedStep = ZombiesDeployDraft.normalizeWorkflowStep(step);
        if (ZombiesDeployDraft.WORKFLOW_MAP.equals(normalizedStep)) {
            return new ZombiesDeployDraft(
                    ZombiesDeployDraft.STAGE_MAP_REGISTRATION,
                    ZombiesDeployDraft.WORKFLOW_MAP,
                    draft.selectedMap(),
                    draft.draftMapName(),
                    draft.mapPos1(),
                    draft.mapPos2(),
                    draft.objectType(),
                    draft.capturePreset(),
                    draft.selectedIndex(),
                    draft.validationView(),
                    draft.fields());
        }
        ZombiesMapObjects resolved = objects == null ? ZombiesMapObjects.EMPTY : objects;
        String type = switch (normalizedStep) {
            case ZombiesDeployDraft.WORKFLOW_INITIAL -> ZombiesDeployFieldSchema.INITIAL;
            case ZombiesDeployDraft.WORKFLOW_ZOMBIE_SPAWN -> ZombiesDeployFieldSchema.ZOMBIE_SPAWN;
            case ZombiesDeployDraft.WORKFLOW_BARRIER -> ZombiesDeployFieldSchema.BARRIER;
            case ZombiesDeployDraft.WORKFLOW_INTERACT -> preferredInteractObjectType(resolved);
            case ZombiesDeployDraft.WORKFLOW_VALIDATE -> draft.objectType();
            default -> ZombiesDeployFieldSchema.INITIAL;
        };
        String capturePreset = ZombiesDeployDraft.normalizeCapturePreset(
                ZombiesDeployFieldSchema.BARRIER.equals(type)
                        ? ZombiesDeployDraft.CAPTURE_BARRIER_AREA
                        : ZombiesDeployDraft.CAPTURE_DEFAULT,
                type);
        Map<String, String> fields = type.equals(draft.objectType())
                ? draft.fields()
                : defaultFields(player, type);
        return new ZombiesDeployDraft(
                ZombiesDeployDraft.STAGE_OBJECT_MARKING,
                normalizedStep,
                draft.selectedMap(),
                draft.draftMapName(),
                draft.mapPos1(),
                draft.mapPos2(),
                type,
                capturePreset,
                -1,
                draft.validationView(),
                fields);
    }

    private String preferredInteractObjectType(ZombiesMapObjects objects) {
        ZombiesMapObjects resolved = objects == null ? ZombiesMapObjects.EMPTY : objects;
        if (resolved.weaponWalls().isEmpty()) {
            return ZombiesDeployFieldSchema.WEAPON_WALL;
        }
        if (resolved.ammoBoxes().isEmpty()) {
            return ZombiesDeployFieldSchema.AMMO_BOX;
        }
        if (resolved.armorStations().isEmpty()) {
            return ZombiesDeployFieldSchema.ARMOR_STATION;
        }
        if (resolved.sodaMachines().isEmpty()) {
            return ZombiesDeployFieldSchema.SODA_MACHINE;
        }
        if (resolved.ultimateMachines().isEmpty()) {
            return ZombiesDeployFieldSchema.ULTIMATE_MACHINE;
        }
        if (resolved.powerSwitch().isEmpty()) {
            return ZombiesDeployFieldSchema.POWER_SWITCH;
        }
        return ZombiesDeployFieldSchema.WEAPON_WALL;
    }

    private List<String> availableMaps() {
        return FPSMCore.getInstance()
                .getMapNamesWithType(BuiltInGameModes.ZOMBIES)
                .stream()
                .filter(name -> resolveMap(name).isPresent())
                .toList();
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
            setPosition(fields, "pos", pos.above());
            setPosition(fields, "interaction", pos);
            setPosition(fields, "areaFrom", pos);
            setPosition(fields, "areaTo", pos);
            fields.computeIfPresent("yaw", (key, value) -> Float.toString(player.getYRot()));
        }
        return fields;
    }

    private Map<String, String> mergeDefaults(String objectType, Map<String, String> fields) {
        Map<String, String> merged = new LinkedHashMap<>(ZombiesDeployFieldSchema.defaultFields(objectType));
        if (fields != null) {
            fields.forEach((key, value) -> {
                if (merged.containsKey(key) || isTransientField(key)) {
                    merged.put(key, value == null ? "" : value);
                }
            });
        }
        return merged;
    }

    private boolean isTransientField(String key) {
        return LOOK_AT_X.equals(key) || LOOK_AT_Y.equals(key) || LOOK_AT_Z.equals(key);
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
                    summaries.add(summary(i, type, data.objectId(), "box", detail(data.dimension(), data.pos())));
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
                    summaries.add(summary(i, type, data.objectId(), data.requiresPower() ? "requires power" : "no power", detail(data.dimension(), data.pos())));
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
        return requiredForMvp1(objectType)
                || requiredForMvp2(objectType)
                || requiredForMvp3(objectType);
    }

    private boolean requiredForMvp1(String objectType) {
        return switch (ZombiesDeployFieldSchema.normalizeObjectType(objectType)) {
            case ZombiesDeployFieldSchema.INITIAL,
                 ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> true;
            default -> false;
        };
    }

    private boolean requiredForMvp2(String objectType) {
        return switch (ZombiesDeployFieldSchema.normalizeObjectType(objectType)) {
            case ZombiesDeployFieldSchema.WEAPON_WALL,
                 ZombiesDeployFieldSchema.AMMO_BOX,
                 ZombiesDeployFieldSchema.ARMOR_STATION -> true;
            default -> false;
        };
    }

    private boolean requiredForMvp3(String objectType) {
        return switch (ZombiesDeployFieldSchema.normalizeObjectType(objectType)) {
            case ZombiesDeployFieldSchema.BARRIER,
                 ZombiesDeployFieldSchema.SODA_MACHINE,
                 ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> true;
            default -> false;
        };
    }

    private List<ZombiesDeploySnapshot.StepStatus> stepStatuses(boolean hasMap, ZombiesMapObjects objects) {
        ZombiesMapObjects resolved = objects == null ? ZombiesMapObjects.EMPTY : objects;
        Set<Integer> zombieSpawnGroups = new TreeSet<>();
        for (ZombiesZombieSpawnData spawn : resolved.zombieSpawns()) {
            zombieSpawnGroups.add(spawn.group());
        }
        Set<Integer> barrierGroups = new TreeSet<>();
        for (ZombiesBarrierData barrier : resolved.barriers()) {
            barrierGroups.add(barrier.group());
        }
        Set<Integer> unmatchedBarrierGroups = new TreeSet<>(barrierGroups);
        unmatchedBarrierGroups.removeAll(zombieSpawnGroups);

        boolean hasWeaponWall = !resolved.weaponWalls().isEmpty();
        boolean hasAmmoBox = !resolved.ammoBoxes().isEmpty();
        boolean hasArmorStation = !resolved.armorStations().isEmpty();
        boolean hasPowerSwitch = resolved.powerSwitch().isPresent();
        boolean hasUltimateMachine = !resolved.ultimateMachines().isEmpty();
        boolean hasSodaMachine = !resolved.sodaMachines().isEmpty();
        boolean interactionComplete = hasWeaponWall
                && hasAmmoBox
                && hasArmorStation
                && hasUltimateMachine
                && hasSodaMachine;
        int interactionTotal = resolved.weaponWalls().size()
                + resolved.ammoBoxes().size()
                + resolved.armorStations().size()
                + (hasPowerSwitch ? 1 : 0)
                + resolved.ultimateMachines().size()
                + resolved.sodaMachines().size();
        String interactionDetail = "weaponWall=" + resolved.weaponWalls().size()
                + ";ammoBox=" + resolved.ammoBoxes().size()
                + ";armorStation=" + resolved.armorStations().size()
                + ";powerSwitch=" + (hasPowerSwitch ? "1" : "0")
                + ";ultimateMachine=" + (hasUltimateMachine ? "1" : "0")
                + ";sodaMachine=" + resolved.sodaMachines().size()
                + ";total=" + interactionTotal;
        boolean barrierComplete = !resolved.barriers().isEmpty();
        String barrierDetail = "barrier=" + resolved.barriers().size()
                + ";barrierGroups=" + formatGroupSet(barrierGroups)
                + ";spawnGroups=" + formatGroupSet(zombieSpawnGroups)
                + ";unmatchedGroups=" + formatGroupSet(unmatchedBarrierGroups);
        return List.of(
                new ZombiesDeploySnapshot.StepStatus("map", "", hasMap ? "1" : "0", hasMap),
                new ZombiesDeploySnapshot.StepStatus("initial", "", Integer.toString(resolved.initialSpawns().size()), !resolved.initialSpawns().isEmpty()),
                new ZombiesDeploySnapshot.StepStatus("zombie_spawn", "", Integer.toString(resolved.zombieSpawns().size()), !resolved.zombieSpawns().isEmpty()),
                new ZombiesDeploySnapshot.StepStatus("barrier", "", barrierDetail, barrierComplete),
                new ZombiesDeploySnapshot.StepStatus("interact", "", interactionDetail, interactionComplete),
                new ZombiesDeploySnapshot.StepStatus("validate", "", hasMap ? "1" : "0", hasMap)
        );
    }

    private String formatGroupSet(Set<Integer> groups) {
        if (groups == null || groups.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (Integer group : groups) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(group);
        }
        return builder.toString();
    }

    private String resolveWorkflowStep(
            ZombiesDeployDraft draft,
            ZombiesMapObjects objects,
            Optional<ZombiesMap> map
    ) {
        if (ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(draft.workspaceStage()) || map.isEmpty()) {
            return ZombiesDeployDraft.WORKFLOW_MAP;
        }
        return ZombiesDeployDraft.normalizeWorkflowStep(draft.workflowStep());
    }

    private String nextWorkflowStep(String step) {
        return switch (ZombiesDeployDraft.normalizeWorkflowStep(step)) {
            case ZombiesDeployDraft.WORKFLOW_MAP -> ZombiesDeployDraft.WORKFLOW_INITIAL;
            case ZombiesDeployDraft.WORKFLOW_INITIAL -> ZombiesDeployDraft.WORKFLOW_ZOMBIE_SPAWN;
            case ZombiesDeployDraft.WORKFLOW_ZOMBIE_SPAWN -> ZombiesDeployDraft.WORKFLOW_BARRIER;
            case ZombiesDeployDraft.WORKFLOW_BARRIER -> ZombiesDeployDraft.WORKFLOW_INTERACT;
            case ZombiesDeployDraft.WORKFLOW_INTERACT, ZombiesDeployDraft.WORKFLOW_VALIDATE -> ZombiesDeployDraft.WORKFLOW_VALIDATE;
            default -> ZombiesDeployDraft.WORKFLOW_INITIAL;
        };
    }

    private String blockingReason(
            String step,
            ZombiesMapObjects objects,
            Optional<ZombiesMap> map
    ) {
        ZombiesMapObjects resolved = objects == null ? ZombiesMapObjects.EMPTY : objects;
        String normalized = ZombiesDeployDraft.normalizeWorkflowStep(step);
        if (ZombiesDeployDraft.WORKFLOW_MAP.equals(normalized)) {
            return map.isPresent() ? "" : "missing_map";
        }
        if (map.isEmpty()) {
            return "missing_map";
        }
        return switch (normalized) {
            case ZombiesDeployDraft.WORKFLOW_INITIAL ->
                    resolved.initialSpawns().isEmpty() ? "missing_initial" : "";
            case ZombiesDeployDraft.WORKFLOW_ZOMBIE_SPAWN ->
                    resolved.zombieSpawns().isEmpty() ? "missing_zombie_spawn" : "";
            case ZombiesDeployDraft.WORKFLOW_BARRIER -> {
                if (resolved.barriers().isEmpty()) {
                    yield "missing_barrier";
                }
                yield "";
            }
            case ZombiesDeployDraft.WORKFLOW_INTERACT -> {
                if (resolved.weaponWalls().isEmpty()) {
                    yield "missing_weapon_wall";
                }
                if (resolved.ammoBoxes().isEmpty()) {
                    yield "missing_ammo_box";
                }
                if (resolved.armorStations().isEmpty()) {
                    yield "missing_armor_station";
                }
                boolean hasUltimate = !resolved.ultimateMachines().isEmpty();
                boolean hasSoda = !resolved.sodaMachines().isEmpty();
                if (!hasUltimate) {
                    yield "missing_ultimate_machine";
                }
                if (!hasSoda) {
                    yield "missing_soda_machine";
                }
                yield "";
            }
            case ZombiesDeployDraft.WORKFLOW_VALIDATE ->
                    mvp1Errors(map.get()) > 0 ? "mvp1_has_errors" : "";
            default -> "";
        };
    }

    private int mvp1Errors(ZombiesMap map) {
        int errors = 0;
        for (ZombiesDeploySnapshot.ValidationLine line : validationLines(map, ZombiesDeployFieldSchema.PROFILE_MVP1)) {
            if ("error".equalsIgnoreCase(line.severity())) {
                errors++;
            }
        }
        return errors;
    }

    private IssueTarget resolveIssueTarget(
            String issueCode,
            String issueSubject,
            ZombiesDeployDraft draft,
            ZombiesMapObjects objects
    ) {
        String code = Objects.requireNonNullElse(issueCode, "").trim().toLowerCase(Locale.ROOT);
        String subject = Objects.requireNonNullElse(issueSubject, "").trim();
        String subjectType = parseSubjectType(subject);
        String subjectObjectId = parseSubjectObjectId(subject);

        if (code.contains("missing_map")) {
            return new IssueTarget(true, ZombiesDeployDraft.WORKFLOW_MAP, draft.objectType(), -1);
        }
        if (code.contains("missing_initial_spawn")) {
            return new IssueTarget(false, ZombiesDeployDraft.WORKFLOW_INITIAL, ZombiesDeployFieldSchema.INITIAL, 0);
        }
        if (code.contains("group_1_zombie_spawn")) {
            return new IssueTarget(false, ZombiesDeployDraft.WORKFLOW_ZOMBIE_SPAWN, ZombiesDeployFieldSchema.ZOMBIE_SPAWN, -1);
        }
        if (code.contains("missing_power_switch") || code.contains("multiple_power_switches") || code.contains("invalid_power_switch")) {
            return issueTargetForObject(false, ZombiesDeployDraft.WORKFLOW_INTERACT, ZombiesDeployFieldSchema.POWER_SWITCH, subjectObjectId, objects);
        }
        if (code.contains("missing_soda_machine") || code.contains("invalid_soda_machine")) {
            return issueTargetForObject(false, ZombiesDeployDraft.WORKFLOW_INTERACT, ZombiesDeployFieldSchema.SODA_MACHINE, subjectObjectId, objects);
        }
        if (code.contains("missing_ultimate_machine") || code.contains("invalid_ultimate_machine")) {
            return issueTargetForObject(false, ZombiesDeployDraft.WORKFLOW_INTERACT, ZombiesDeployFieldSchema.ULTIMATE_MACHINE, subjectObjectId, objects);
        }
        if (code.contains("missing_weapon_wall") || code.contains("invalid_weapon_wall")) {
            return issueTargetForObject(false, ZombiesDeployDraft.WORKFLOW_INTERACT, ZombiesDeployFieldSchema.WEAPON_WALL, subjectObjectId, objects);
        }
        if (code.contains("missing_ammo_box") || code.contains("invalid_ammo_box")) {
            return issueTargetForObject(false, ZombiesDeployDraft.WORKFLOW_INTERACT, ZombiesDeployFieldSchema.AMMO_BOX, subjectObjectId, objects);
        }
        if (code.contains("missing_armor_station") || code.contains("invalid_armor_station")) {
            return issueTargetForObject(false, ZombiesDeployDraft.WORKFLOW_INTERACT, ZombiesDeployFieldSchema.ARMOR_STATION, subjectObjectId, objects);
        }
        if (code.contains("barrier_group_without_zombie_spawn")) {
            return issueTargetForObject(false, ZombiesDeployDraft.WORKFLOW_BARRIER, ZombiesDeployFieldSchema.BARRIER, subjectObjectId, objects);
        }
        if (code.contains("invalid_barrier")) {
            return issueTargetForObject(false, ZombiesDeployDraft.WORKFLOW_BARRIER, ZombiesDeployFieldSchema.BARRIER, subjectObjectId, objects);
        }
        if (code.contains("zombie_spawn")) {
            return issueTargetForObject(false, ZombiesDeployDraft.WORKFLOW_ZOMBIE_SPAWN, ZombiesDeployFieldSchema.ZOMBIE_SPAWN, subjectObjectId, objects);
        }

        String mappedObjectType = mapObjectTypeFromSubject(subjectType, subjectObjectId);
        if (mappedObjectType.isBlank()) {
            mappedObjectType = draft.objectType();
        }
        String workflowStep = ZombiesDeployDraft.workflowStepForObjectType(mappedObjectType);
        int index = findObjectIndexByObjectId(objects, mappedObjectType, subjectObjectId);
        return new IssueTarget(false, workflowStep, mappedObjectType, index);
    }

    private IssueTarget issueTargetForObject(
            boolean mapStage,
            String workflowStep,
            String objectType,
            String objectId,
            ZombiesMapObjects objects
    ) {
        int index = findObjectIndexByObjectId(objects, objectType, objectId);
        return new IssueTarget(mapStage, workflowStep, objectType, index);
    }

    private String parseSubjectType(String subject) {
        if (subject == null || subject.isBlank()) {
            return "";
        }
        int split = subject.indexOf('.');
        String token = split < 0 ? subject : subject.substring(0, split);
        return token.trim().toLowerCase(Locale.ROOT);
    }

    private String parseSubjectObjectId(String subject) {
        if (subject == null || subject.isBlank()) {
            return "";
        }
        int split = subject.indexOf('.');
        if (split < 0 || split >= subject.length() - 1) {
            return "";
        }
        return subject.substring(split + 1).trim();
    }

    private String mapObjectTypeFromSubject(String subjectType, String subjectObjectId) {
        if (subjectType == null || subjectType.isBlank()) {
            return "";
        }
        return switch (subjectType) {
            case "initial" -> ZombiesDeployFieldSchema.INITIAL;
            case "spawn" -> "initial".equalsIgnoreCase(subjectObjectId)
                    ? ZombiesDeployFieldSchema.INITIAL
                    : ZombiesDeployFieldSchema.ZOMBIE_SPAWN;
            case "zombie_spawn" -> ZombiesDeployFieldSchema.ZOMBIE_SPAWN;
            case "barrier" -> ZombiesDeployFieldSchema.BARRIER;
            case "weapon_wall" -> ZombiesDeployFieldSchema.WEAPON_WALL;
            case "ammo_box" -> ZombiesDeployFieldSchema.AMMO_BOX;
            case "armor_station" -> ZombiesDeployFieldSchema.ARMOR_STATION;
            case "power_switch" -> ZombiesDeployFieldSchema.POWER_SWITCH;
            case "soda_machine" -> ZombiesDeployFieldSchema.SODA_MACHINE;
            case "ultimate_machine" -> ZombiesDeployFieldSchema.ULTIMATE_MACHINE;
            default -> "";
        };
    }

    private boolean isSupportedWorkflowStep(String step) {
        String normalized = ZombiesDeployDraft.normalizeWorkflowStep(step);
        return normalized.equals(step)
                && (ZombiesDeployDraft.WORKFLOW_MAP.equals(step)
                || ZombiesDeployDraft.WORKFLOW_INITIAL.equals(step)
                || ZombiesDeployDraft.WORKFLOW_ZOMBIE_SPAWN.equals(step)
                || ZombiesDeployDraft.WORKFLOW_BARRIER.equals(step)
                || ZombiesDeployDraft.WORKFLOW_INTERACT.equals(step)
                || ZombiesDeployDraft.WORKFLOW_VALIDATE.equals(step));
    }

    private boolean isKnownObjectType(String objectType) {
        String raw = Objects.requireNonNullElse(objectType, "").trim();
        if (raw.isBlank()) {
            return false;
        }
        String normalized = ZombiesDeployFieldSchema.normalizeObjectType(raw);
        if (normalized.isBlank()) {
            return false;
        }
        return ZombiesDeployFieldSchema.objectTypeKeys().contains(normalized);
    }

    private int normalizeTargetIndex(
            ZombiesMapObjects objects,
            String objectType,
            int targetIndex
    ) {
        if (targetIndex < 0) {
            return -1;
        }
        ZombiesMapObjects resolved = objects == null ? ZombiesMapObjects.EMPTY : objects;
        int size = switch (ZombiesDeployFieldSchema.normalizeObjectType(objectType)) {
            case ZombiesDeployFieldSchema.INITIAL -> resolved.initialSpawns().size();
            case ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> resolved.zombieSpawns().size();
            case ZombiesDeployFieldSchema.BARRIER -> resolved.barriers().size();
            case ZombiesDeployFieldSchema.WEAPON_WALL -> resolved.weaponWalls().size();
            case ZombiesDeployFieldSchema.AMMO_BOX -> resolved.ammoBoxes().size();
            case ZombiesDeployFieldSchema.ARMOR_STATION -> resolved.armorStations().size();
            case ZombiesDeployFieldSchema.POWER_SWITCH -> resolved.powerSwitch().isPresent() ? 1 : 0;
            case ZombiesDeployFieldSchema.SODA_MACHINE -> resolved.sodaMachines().size();
            case ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> resolved.ultimateMachines().size();
            default -> 0;
        };
        if (size <= 0) {
            return -1;
        }
        return Math.min(targetIndex, size - 1);
    }

    private int findObjectIndexByObjectId(
            ZombiesMapObjects objects,
            String objectType,
            String objectId
    ) {
        String targetId = Objects.requireNonNullElse(objectId, "").trim();
        if (targetId.isBlank()) {
            return -1;
        }
        ZombiesMapObjects resolved = objects == null ? ZombiesMapObjects.EMPTY : objects;
        String type = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        switch (type) {
            case ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> {
                for (int i = 0; i < resolved.zombieSpawns().size(); i++) {
                    if (targetId.equalsIgnoreCase(resolved.zombieSpawns().get(i).objectId())) {
                        return i;
                    }
                }
            }
            case ZombiesDeployFieldSchema.BARRIER -> {
                for (int i = 0; i < resolved.barriers().size(); i++) {
                    if (targetId.equalsIgnoreCase(resolved.barriers().get(i).objectId())) {
                        return i;
                    }
                }
            }
            case ZombiesDeployFieldSchema.WEAPON_WALL -> {
                for (int i = 0; i < resolved.weaponWalls().size(); i++) {
                    if (targetId.equalsIgnoreCase(resolved.weaponWalls().get(i).objectId())) {
                        return i;
                    }
                }
            }
            case ZombiesDeployFieldSchema.AMMO_BOX -> {
                for (int i = 0; i < resolved.ammoBoxes().size(); i++) {
                    if (targetId.equalsIgnoreCase(resolved.ammoBoxes().get(i).objectId())) {
                        return i;
                    }
                }
            }
            case ZombiesDeployFieldSchema.ARMOR_STATION -> {
                for (int i = 0; i < resolved.armorStations().size(); i++) {
                    if (targetId.equalsIgnoreCase(resolved.armorStations().get(i).objectId())) {
                        return i;
                    }
                }
            }
            case ZombiesDeployFieldSchema.POWER_SWITCH -> {
                if (resolved.powerSwitch().isPresent()
                        && targetId.equalsIgnoreCase(resolved.powerSwitch().get().objectId())) {
                    return 0;
                }
            }
            case ZombiesDeployFieldSchema.SODA_MACHINE -> {
                for (int i = 0; i < resolved.sodaMachines().size(); i++) {
                    if (targetId.equalsIgnoreCase(resolved.sodaMachines().get(i).objectId())) {
                        return i;
                    }
                }
            }
            case ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> {
                for (int i = 0; i < resolved.ultimateMachines().size(); i++) {
                    if (targetId.equalsIgnoreCase(resolved.ultimateMachines().get(i).objectId())) {
                        return i;
                    }
                }
            }
            case ZombiesDeployFieldSchema.INITIAL -> {
                if ("initial".equalsIgnoreCase(targetId) || targetId.startsWith("INITIAL#")) {
                    return resolved.initialSpawns().isEmpty() ? -1 : 0;
                }
            }
            default -> {
                return -1;
            }
        }
        return -1;
    }

    private boolean draftDirty(
            ZombiesDeployDraft draft,
            Optional<ZombiesMap> map,
            ZombiesMapObjects objects
    ) {
        if (ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(draft.workspaceStage())) {
            if (map.isEmpty()) {
                return !draft.draftMapName().isBlank()
                        || draft.mapPos1() != null
                        || draft.mapPos2() != null;
            }
            if (draft.mapPos1() == null || draft.mapPos2() == null) {
                return false;
            }
            AreaData area = map.get().getMapArea();
            return !draft.mapPos1().equals(area.pos1()) || !draft.mapPos2().equals(area.pos2());
        }
        if (map.isEmpty() || draft.selectedIndex() < 0) {
            return false;
        }
        Map<String, String> base = ZombiesDeployObjectEditor.fieldsForSnapshotSelection(objects, draft.objectType(), draft.selectedIndex());
        Map<String, String> current = mergeDefaults(draft.objectType(), draft.fields());
        for (String key : current.keySet()) {
            if (!Objects.equals(current.get(key), base.getOrDefault(key, ""))) {
                return true;
            }
        }
        return false;
    }

    private String nearestObjectHint(
            ServerPlayer player,
            ZombiesDeployDraft draft,
            Optional<ZombiesMap> map,
            ZombiesMapObjects objects
    ) {
        if (player == null || map.isEmpty() || ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(draft.workspaceStage())) {
            return "";
        }
        NearestObject nearest = nearestObject(player, draft.objectType(), draft.capturePreset(), objects);
        if (nearest == null) {
            return "";
        }
        return nearest.label() + "|" + String.format(Locale.ROOT, "%.1f", nearest.distanceMeters());
    }

    private NearestObject nearestObject(
            ServerPlayer player,
            String objectType,
            String capturePreset,
            ZombiesMapObjects objects
    ) {
        if (player == null) {
            return null;
        }
        ZombiesMapObjects resolved = objects == null ? ZombiesMapObjects.EMPTY : objects;
        Vec3 playerPos = player.position();
        String type = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        NearestObject best = null;
        switch (type) {
            case ZombiesDeployFieldSchema.INITIAL -> {
                for (int i = 0; i < resolved.initialSpawns().size(); i++) {
                    ZombiesInitialSpawnData data = resolved.initialSpawns().get(i);
                    best = nearest(playerPos, best, data.pos(), "INITIAL#" + (i + 1));
                }
            }
            case ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> {
                for (int i = 0; i < resolved.zombieSpawns().size(); i++) {
                    ZombiesZombieSpawnData data = resolved.zombieSpawns().get(i);
                    best = nearest(playerPos, best, data.pos(), data.objectId());
                }
            }
            case ZombiesDeployFieldSchema.BARRIER -> {
                boolean interactionMode = ZombiesDeployDraft.CAPTURE_BARRIER_INTERACTION.equals(
                        ZombiesDeployDraft.normalizeCapturePreset(capturePreset, type));
                for (int i = 0; i < resolved.barriers().size(); i++) {
                    ZombiesBarrierData data = resolved.barriers().get(i);
                    BlockPos anchor = interactionMode && data.interactionPos() != null
                            ? data.interactionPos()
                            : areaCenter(data.areaFrom(), data.areaTo());
                    best = nearest(playerPos, best, anchor, data.objectId());
                }
            }
            case ZombiesDeployFieldSchema.WEAPON_WALL -> {
                for (int i = 0; i < resolved.weaponWalls().size(); i++) {
                    ZombiesWeaponWallData data = resolved.weaponWalls().get(i);
                    best = nearest(playerPos, best, data.pos(), data.objectId());
                }
            }
            case ZombiesDeployFieldSchema.AMMO_BOX -> {
                for (int i = 0; i < resolved.ammoBoxes().size(); i++) {
                    ZombiesAmmoBoxData data = resolved.ammoBoxes().get(i);
                    best = nearest(playerPos, best, data.pos(), data.objectId());
                }
            }
            case ZombiesDeployFieldSchema.ARMOR_STATION -> {
                for (int i = 0; i < resolved.armorStations().size(); i++) {
                    ZombiesArmorStationData data = resolved.armorStations().get(i);
                    best = nearest(playerPos, best, data.pos(), data.objectId());
                }
            }
            case ZombiesDeployFieldSchema.POWER_SWITCH -> {
                if (resolved.powerSwitch().isPresent()) {
                    best = nearest(playerPos, best, resolved.powerSwitch().get().pos(), resolved.powerSwitch().get().objectId());
                }
            }
            case ZombiesDeployFieldSchema.SODA_MACHINE -> {
                for (int i = 0; i < resolved.sodaMachines().size(); i++) {
                    ZombiesSodaMachineData data = resolved.sodaMachines().get(i);
                    best = nearest(playerPos, best, data.pos(), data.objectId());
                }
            }
            case ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> {
                for (int i = 0; i < resolved.ultimateMachines().size(); i++) {
                    ZombiesUltimateMachineData data = resolved.ultimateMachines().get(i);
                    best = nearest(playerPos, best, data.pos(), data.objectId());
                }
            }
            default -> {
                return null;
            }
        }
        return best;
    }

    private NearestObject nearest(Vec3 playerPos, NearestObject current, BlockPos pos, String label) {
        if (playerPos == null || pos == null) {
            return current;
        }
        double distance = Math.sqrt(playerPos.distanceToSqr(Vec3.atCenterOf(pos)));
        NearestObject candidate = new NearestObject(pos, label, distance);
        if (current == null || candidate.distanceMeters() < current.distanceMeters()) {
            return candidate;
        }
        return current;
    }

    private BlockPos areaCenter(BlockPos from, BlockPos to) {
        if (from == null || to == null) {
            return null;
        }
        return new BlockPos(
                (from.getX() + to.getX()) / 2,
                (from.getY() + to.getY()) / 2,
                (from.getZ() + to.getZ()) / 2);
    }

    private boolean shouldRejectOccupiedPositionConflict(
            ZombiesDeployObjectEditor.Operation operation
    ) {
        ZombiesDeployObjectEditor.Operation resolvedOperation = operation == null
                ? ZombiesDeployObjectEditor.Operation.ADD
                : operation;
        return switch (resolvedOperation) {
            case ADD, UPDATE, DUPLICATE -> true;
            case DELETE, CLEAR -> false;
        };
    }

    private boolean isSinglePointObject(String objectType) {
        return switch (ZombiesDeployFieldSchema.normalizeObjectType(objectType)) {
            case ZombiesDeployFieldSchema.INITIAL,
                 ZombiesDeployFieldSchema.ZOMBIE_SPAWN,
                 ZombiesDeployFieldSchema.WEAPON_WALL,
                 ZombiesDeployFieldSchema.AMMO_BOX,
                 ZombiesDeployFieldSchema.ARMOR_STATION,
                 ZombiesDeployFieldSchema.POWER_SWITCH,
                 ZombiesDeployFieldSchema.SODA_MACHINE,
                 ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> true;
            default -> false;
        };
    }

    private Optional<DuplicatePosition> findOccupiedPositionConflict(ZombiesMapObjects objects) {
        ZombiesMapObjects resolved = objects == null ? ZombiesMapObjects.EMPTY : objects;
        Map<String, PointRef> seen = new LinkedHashMap<>();
        for (int i = 0; i < resolved.initialSpawns().size(); i++) {
            Optional<DuplicatePosition> duplicate = addPointRef(seen, new PointRef(
                    ZombiesDeployFieldSchema.INITIAL,
                    i,
                    "pos",
                    dimensionId(resolved.initialSpawns().get(i).dimension()),
                    resolved.initialSpawns().get(i).pos()));
            if (duplicate.isPresent()) {
                return duplicate;
            }
        }
        for (int i = 0; i < resolved.zombieSpawns().size(); i++) {
            Optional<DuplicatePosition> duplicate = addPointRef(seen, new PointRef(
                    ZombiesDeployFieldSchema.ZOMBIE_SPAWN,
                    i,
                    "pos",
                    dimensionId(resolved.zombieSpawns().get(i).dimension()),
                    resolved.zombieSpawns().get(i).pos()));
            if (duplicate.isPresent()) {
                return duplicate;
            }
        }
        for (int i = 0; i < resolved.barriers().size(); i++) {
            ZombiesBarrierData barrier = resolved.barriers().get(i);
            Optional<DuplicatePosition> duplicate = addAreaRefs(seen, ZombiesDeployFieldSchema.BARRIER, i,
                    dimensionId(barrier.dimension()), barrier.areaFrom(), barrier.areaTo());
            if (duplicate.isPresent()) {
                return duplicate;
            }
            duplicate = addPointRef(seen, new PointRef(
                    ZombiesDeployFieldSchema.BARRIER,
                    i,
                    "interaction",
                    dimensionId(barrier.dimension()),
                    barrier.interactionPos()));
            if (duplicate.isPresent()) {
                return duplicate;
            }
        }
        for (int i = 0; i < resolved.weaponWalls().size(); i++) {
            ZombiesWeaponWallData weaponWall = resolved.weaponWalls().get(i);
            Optional<DuplicatePosition> duplicate = addPointRef(seen, new PointRef(
                    ZombiesDeployFieldSchema.WEAPON_WALL,
                    i,
                    "pos",
                    dimensionId(weaponWall.dimension()),
                    weaponWall.pos()));
            if (duplicate.isPresent()) {
                return duplicate;
            }
            duplicate = addPointRef(seen, new PointRef(
                    ZombiesDeployFieldSchema.WEAPON_WALL,
                    i,
                    "interaction",
                    dimensionId(weaponWall.dimension()),
                    weaponWall.interactionPos().orElse(null)));
            if (duplicate.isPresent()) {
                return duplicate;
            }
        }
        for (int i = 0; i < resolved.ammoBoxes().size(); i++) {
            ZombiesAmmoBoxData ammoBox = resolved.ammoBoxes().get(i);
            Optional<DuplicatePosition> duplicate = addPointRef(seen, new PointRef(
                    ZombiesDeployFieldSchema.AMMO_BOX,
                    i,
                    "pos",
                    dimensionId(ammoBox.dimension()),
                    ammoBox.pos()));
            if (duplicate.isPresent()) {
                return duplicate;
            }
            duplicate = addPointRef(seen, new PointRef(
                    ZombiesDeployFieldSchema.AMMO_BOX,
                    i,
                    "interaction",
                    dimensionId(ammoBox.dimension()),
                    ammoBox.interactionPos().orElse(null)));
            if (duplicate.isPresent()) {
                return duplicate;
            }
        }
        for (int i = 0; i < resolved.armorStations().size(); i++) {
            ZombiesArmorStationData armorStation = resolved.armorStations().get(i);
            Optional<DuplicatePosition> duplicate = addPointRef(seen, new PointRef(
                    ZombiesDeployFieldSchema.ARMOR_STATION,
                    i,
                    "pos",
                    dimensionId(armorStation.dimension()),
                    armorStation.pos()));
            if (duplicate.isPresent()) {
                return duplicate;
            }
            duplicate = addPointRef(seen, new PointRef(
                    ZombiesDeployFieldSchema.ARMOR_STATION,
                    i,
                    "interaction",
                    dimensionId(armorStation.dimension()),
                    armorStation.interactionPos().orElse(null)));
            if (duplicate.isPresent()) {
                return duplicate;
            }
        }
        if (resolved.powerSwitch().isPresent()) {
            ZombiesPowerSwitchData powerSwitch = resolved.powerSwitch().get();
            Optional<DuplicatePosition> duplicate = addPointRef(seen, new PointRef(
                    ZombiesDeployFieldSchema.POWER_SWITCH,
                    0,
                    "pos",
                    dimensionId(powerSwitch.dimension()),
                    powerSwitch.pos()));
            if (duplicate.isPresent()) {
                return duplicate;
            }
            duplicate = addPointRef(seen, new PointRef(
                    ZombiesDeployFieldSchema.POWER_SWITCH,
                    0,
                    "interaction",
                    dimensionId(powerSwitch.dimension()),
                    powerSwitch.interactionPos().orElse(null)));
            if (duplicate.isPresent()) {
                return duplicate;
            }
        }
        for (int i = 0; i < resolved.sodaMachines().size(); i++) {
            ZombiesSodaMachineData sodaMachine = resolved.sodaMachines().get(i);
            Optional<DuplicatePosition> duplicate = addPointRef(seen, new PointRef(
                    ZombiesDeployFieldSchema.SODA_MACHINE,
                    i,
                    "pos",
                    dimensionId(sodaMachine.dimension()),
                    sodaMachine.pos()));
            if (duplicate.isPresent()) {
                return duplicate;
            }
            duplicate = addPointRef(seen, new PointRef(
                    ZombiesDeployFieldSchema.SODA_MACHINE,
                    i,
                    "interaction",
                    dimensionId(sodaMachine.dimension()),
                    sodaMachine.interactionPos().orElse(null)));
            if (duplicate.isPresent()) {
                return duplicate;
            }
        }
        for (int i = 0; i < resolved.ultimateMachines().size(); i++) {
            ZombiesUltimateMachineData ultimateMachine = resolved.ultimateMachines().get(i);
            Optional<DuplicatePosition> duplicate = addPointRef(seen, new PointRef(
                    ZombiesDeployFieldSchema.ULTIMATE_MACHINE,
                    i,
                    "pos",
                    dimensionId(ultimateMachine.dimension()),
                    ultimateMachine.pos()));
            if (duplicate.isPresent()) {
                return duplicate;
            }
            duplicate = addPointRef(seen, new PointRef(
                    ZombiesDeployFieldSchema.ULTIMATE_MACHINE,
                    i,
                    "interaction",
                    dimensionId(ultimateMachine.dimension()),
                    ultimateMachine.interactionPos().orElse(null)));
            if (duplicate.isPresent()) {
                return duplicate;
            }
        }
        return Optional.empty();
    }

    private Optional<DuplicatePosition> addAreaRefs(
            Map<String, PointRef> seen,
            String objectType,
            int index,
            String dimension,
            BlockPos from,
            BlockPos to
    ) {
        if (from == null || to == null) {
            return Optional.empty();
        }
        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int maxY = Math.max(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Optional<DuplicatePosition> duplicate = addPointRef(seen, new PointRef(
                            objectType,
                            index,
                            "area",
                            dimension,
                            new BlockPos(x, y, z)));
                    if (duplicate.isPresent()) {
                        return duplicate;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<DuplicatePosition> addPointRef(Map<String, PointRef> seen, PointRef ref) {
        if (ref == null || ref.dimension().isBlank() || ref.pos() == null) {
            return Optional.empty();
        }
        String key = ref.dimension() + "|" + ref.pos().asLong();
        PointRef previous = seen.putIfAbsent(key, ref);
        if (previous == null || previous.sameObject(ref)) {
            return Optional.empty();
        }
        return Optional.of(new DuplicatePosition(previous, ref));
    }

    private String dimensionId(ResourceKey<Level> dimension) {
        return dimension == null || dimension.location() == null ? "" : dimension.location().toString();
    }

    private boolean shouldSyncPurchasableBlock(
            ServerPlayer player,
            ZombiesDeployObjectEditor.Operation operation,
            ZombiesDeployDraft draft,
            ZombiesMapObjects previousObjects,
            ZombiesMapObjects objects
    ) {
        String type = ZombiesDeployFieldSchema.normalizeObjectType(draft.objectType());
        if (player == null || !isPurchasableBlockObject(type)) {
            return false;
        }
        ZombiesDeployObjectEditor.Operation resolvedOperation = operation == null
                ? ZombiesDeployObjectEditor.Operation.ADD
                : operation;
        if (resolvedOperation != ZombiesDeployObjectEditor.Operation.ADD
                && resolvedOperation != ZombiesDeployObjectEditor.Operation.UPDATE
                && resolvedOperation != ZombiesDeployObjectEditor.Operation.DELETE
                && resolvedOperation != ZombiesDeployObjectEditor.Operation.CLEAR) {
            return false;
        }
        ZombiesMapObjects before = previousObjects == null ? ZombiesMapObjects.EMPTY : previousObjects;
        ZombiesMapObjects after = objects == null ? ZombiesMapObjects.EMPTY : objects;
        return !purchasableObjects(before, type).isEmpty() || !purchasableObjects(after, type).isEmpty();
    }

    private PlacementRollback syncPurchasableBlocks(
            ServerLevel level,
            String objectType,
            ZombiesMapObjects previousObjects,
            ZombiesMapObjects nextObjects,
            ZombiesDeployObjectEditor.Operation operation
    ) {
        if (level == null || !isPurchasableBlockObject(objectType)) {
            return null;
        }
        List<PurchasablePlacement> previous = purchasableObjects(previousObjects, objectType);
        List<PurchasablePlacement> next = purchasableObjects(nextObjects, objectType);
        PlacementRollback rollback = null;
        for (PurchasablePlacement previousPlacement : previous) {
            if (!isCurrentLevelPlacement(level, previousPlacement)) {
                continue;
            }
            Optional<PurchasablePlacement> sameObjectNext = next.stream()
                    .filter(candidate -> sameObject(previousPlacement, candidate))
                    .findFirst();
            if (sameObjectNext.isPresent() && samePlacement(previousPlacement, sameObjectNext.get())) {
                continue;
            }
            if (isPositionStillUsedByOther(next, previousPlacement)) {
                continue;
            }
            rollback = appendPlacementRollback(
                    rollback,
                    removeManagedPurchasableBlock(level, previousPlacement.pos(), previousPlacement.block()));
        }
        if (operation != ZombiesDeployObjectEditor.Operation.DELETE
                && operation != ZombiesDeployObjectEditor.Operation.CLEAR) {
            for (PurchasablePlacement nextPlacement : next) {
                if (!isCurrentLevelPlacement(level, nextPlacement)) {
                    continue;
                }
                Optional<PurchasablePlacement> sameObjectPrevious = previous.stream()
                        .filter(candidate -> sameObject(candidate, nextPlacement))
                        .findFirst();
                boolean sameObjectUpdate = sameObjectPrevious.isPresent();
                if (sameObjectPrevious.isPresent() && samePlacement(sameObjectPrevious.get(), nextPlacement)) {
                    continue;
                }
                rollback = appendPlacementRollback(
                        rollback,
                        placePurchasableBlock(level, nextPlacement, sameObjectUpdate));
            }
        }
        return rollback;
    }

    private boolean isPurchasableBlockObject(String objectType) {
        return switch (ZombiesDeployFieldSchema.normalizeObjectType(objectType)) {
            case ZombiesDeployFieldSchema.WEAPON_WALL,
                 ZombiesDeployFieldSchema.AMMO_BOX,
                 ZombiesDeployFieldSchema.ARMOR_STATION,
                 ZombiesDeployFieldSchema.SODA_MACHINE,
                 ZombiesDeployFieldSchema.ULTIMATE_MACHINE,
                 ZombiesDeployFieldSchema.POWER_SWITCH -> true;
            default -> false;
        };
    }

    private List<PurchasablePlacement> purchasableObjects(ZombiesMapObjects objects, String objectType) {
        ZombiesMapObjects resolved = objects == null ? ZombiesMapObjects.EMPTY : objects;
        return switch (ZombiesDeployFieldSchema.normalizeObjectType(objectType)) {
            case ZombiesDeployFieldSchema.WEAPON_WALL -> resolved.weaponWalls().stream()
                    .map(wall -> new PurchasablePlacement(
                            ZombiesDeployFieldSchema.WEAPON_WALL,
                            wall.objectId(),
                            wall.dimension(),
                            wall.pos(),
                            CodPatternBlockRegister.ZOMBIES_WEAPON_WALL_BOX.get()))
                    .toList();
            case ZombiesDeployFieldSchema.AMMO_BOX -> resolved.ammoBoxes().stream()
                    .map(ammoBox -> new PurchasablePlacement(
                            ZombiesDeployFieldSchema.AMMO_BOX,
                            ammoBox.objectId(),
                            ammoBox.dimension(),
                            ammoBox.pos(),
                            CodPatternBlockRegister.ZOMBIES_AMMO_BOX.get()))
                    .toList();
            case ZombiesDeployFieldSchema.ARMOR_STATION -> resolved.armorStations().stream()
                    .map(armorStation -> new PurchasablePlacement(
                            ZombiesDeployFieldSchema.ARMOR_STATION,
                            armorStation.objectId(),
                            armorStation.dimension(),
                            armorStation.pos(),
                            CodPatternBlockRegister.ZOMBIES_ARMOR_STATION_BOX.get()))
                    .toList();
            case ZombiesDeployFieldSchema.SODA_MACHINE -> resolved.sodaMachines().stream()
                    .map(sodaMachine -> new PurchasablePlacement(
                            ZombiesDeployFieldSchema.SODA_MACHINE,
                            sodaMachine.objectId(),
                            sodaMachine.dimension(),
                            sodaMachine.pos(),
                            CodPatternBlockRegister.ZOMBIES_SODA_MACHINE_BOX.get()))
                    .toList();
            case ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> resolved.ultimateMachines().stream()
                    .map(ultimateMachine -> new PurchasablePlacement(
                            ZombiesDeployFieldSchema.ULTIMATE_MACHINE,
                            ultimateMachine.objectId(),
                            ultimateMachine.dimension(),
                            ultimateMachine.pos(),
                            CodPatternBlockRegister.ZOMBIES_ULTIMATE_MACHINE_BOX.get()))
                    .toList();
            case ZombiesDeployFieldSchema.POWER_SWITCH -> resolved.powerSwitch()
                    .map(powerSwitch -> List.of(new PurchasablePlacement(
                            ZombiesDeployFieldSchema.POWER_SWITCH,
                            powerSwitch.objectId(),
                            powerSwitch.dimension(),
                            powerSwitch.pos(),
                            CodPatternBlockRegister.ZOMBIES_POWER_SWITCH.get())))
                    .orElseGet(List::of);
            default -> List.of();
        };
    }

    private boolean isCurrentLevelPlacement(ServerLevel level, PurchasablePlacement placement) {
        return level != null
                && placement != null
                && placement.dimension() != null
                && level.dimension().equals(placement.dimension());
    }

    private boolean sameObject(PurchasablePlacement first, PurchasablePlacement second) {
        return first != null
                && second != null
                && Objects.equals(first.objectType(), second.objectType())
                && Objects.equals(first.objectId(), second.objectId());
    }

    private boolean samePlacement(PurchasablePlacement first, PurchasablePlacement second) {
        return first != null
                && second != null
                && Objects.equals(first.dimension(), second.dimension())
                && Objects.equals(first.pos(), second.pos())
                && first.block() == second.block();
    }

    private boolean isPositionStillUsedByOther(List<PurchasablePlacement> placements, PurchasablePlacement removed) {
        if (placements == null || removed == null) {
            return false;
        }
        for (PurchasablePlacement placement : placements) {
            if (placement == null || sameObject(removed, placement)) {
                continue;
            }
            if (placement.block() == removed.block()
                    && Objects.equals(placement.dimension(), removed.dimension())
                    && Objects.equals(placement.pos(), removed.pos())) {
                return true;
            }
        }
        return false;
    }

    private PlacementRollback placePurchasableBlock(
            ServerLevel level,
            PurchasablePlacement placement,
            boolean sameObjectUpdate
    ) {
        if (level == null || placement == null || placement.pos() == null || placement.block() == null) {
            return null;
        }
        BlockState previousState = level.getBlockState(placement.pos());
        if (!previousState.isAir()
                && !(sameObjectUpdate && previousState.getBlock() == placement.block())) {
            throw new RuntimeException("Cannot place zombies purchasable block over existing block "
                    + blockId(previousState) + " at " + formatPos(placement.pos()));
        }
        boolean placed = level.setBlock(placement.pos(), placement.block().defaultBlockState(), Block.UPDATE_ALL);
        if (!placed) {
            throw new RuntimeException("Failed to place zombies purchasable block at " + formatPos(placement.pos()));
        }
        return new PlacementRollback(level, placement.pos(), previousState);
    }

    private PlacementRollback removeManagedPurchasableBlock(ServerLevel level, BlockPos pos, Block expectedBlock) {
        if (level == null || pos == null || expectedBlock == null) {
            return null;
        }
        BlockState previousState = level.getBlockState(pos);
        if (previousState.getBlock() != expectedBlock) {
            return null;
        }
        boolean removed = level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        if (!removed) {
            throw new RuntimeException("Failed to remove stale zombies purchasable block at " + formatPos(pos));
        }
        return new PlacementRollback(level, pos, previousState);
    }

    private PlacementRollback appendPlacementRollback(
            PlacementRollback current,
            PlacementRollback next
    ) {
        if (next == null) {
            return current;
        }
        if (current == null) {
            return next;
        }
        return new PlacementRollback(next.level(), next.pos(), next.previousState(), current);
    }

    private void restorePlacement(PlacementRollback rollback) {
        if (rollback == null || rollback.level() == null || rollback.pos() == null || rollback.previousState() == null) {
            return;
        }
        rollback.level().setBlock(rollback.pos(), rollback.previousState(), Block.UPDATE_ALL);
        restorePlacement(rollback.next());
    }

    private void setPosition(Map<String, String> fields, String prefix, BlockPos pos) {
        if (!fields.containsKey(prefix + "X")) {
            return;
        }
        fields.put(prefix + "X", Integer.toString(pos.getX()));
        fields.put(prefix + "Y", Integer.toString(pos.getY()));
        fields.put(prefix + "Z", Integer.toString(pos.getZ()));
    }

    private void setPositionLoose(Map<String, String> fields, String prefix, BlockPos pos) {
        if (fields == null || pos == null || prefix == null || prefix.isBlank()) {
            return;
        }
        fields.put(prefix + "X", Integer.toString(pos.getX()));
        fields.put(prefix + "Y", Integer.toString(pos.getY()));
        fields.put(prefix + "Z", Integer.toString(pos.getZ()));
    }

    private BlockPos readPositionIfPresent(Map<String, String> fields, String prefix) {
        if (fields == null || prefix == null || prefix.isBlank()) {
            return null;
        }
        String sx = fields.get(prefix + "X");
        String sy = fields.get(prefix + "Y");
        String sz = fields.get(prefix + "Z");
        if (sx == null || sy == null || sz == null) {
            return null;
        }
        try {
            return new BlockPos(
                    Integer.parseInt(sx.trim()),
                    Integer.parseInt(sy.trim()),
                    Integer.parseInt(sz.trim()));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isYawCaptureType(String objectType) {
        String normalized = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        return ZombiesDeployFieldSchema.INITIAL.equals(normalized)
                || ZombiesDeployFieldSchema.ZOMBIE_SPAWN.equals(normalized);
    }

    private void applyHorizontalYawFromPlayer(Map<String, String> fields, ServerPlayer player) {
        if (fields == null || player == null || !fields.containsKey("yaw")) {
            return;
        }
        fields.put("yaw", Float.toString(player.getYRot()));
    }

    private void applyLookAtYawOnly(Map<String, String> fields, BlockPos fromPos, BlockPos lookAtPos) {
        if (fields == null || fromPos == null || lookAtPos == null) {
            return;
        }
        if (!fields.containsKey("yaw")) {
            return;
        }
        double fromX = fromPos.getX() + 0.5D;
        double fromZ = fromPos.getZ() + 0.5D;
        double toX = lookAtPos.getX() + 0.5D;
        double toZ = lookAtPos.getZ() + 0.5D;
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        fields.put("yaw", Float.toString(yaw));
    }

    private String detail(ResourceKey<Level> dimension, BlockPos pos) {
        return dimensionId(dimension) + " " + formatPos(pos);
    }

    private String formatPos(BlockPos pos) {
        return pos == null ? "-" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private String blockId(BlockState state) {
        return state == null ? "" : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private record IssueTarget(
            boolean mapStage,
            String workflowStep,
            String objectType,
            int selectedIndex
    ) {
        private IssueTarget {
            workflowStep = ZombiesDeployDraft.normalizeWorkflowStep(workflowStep);
            objectType = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
            selectedIndex = Math.max(-1, selectedIndex);
        }
    }

    private record NearestObject(
            BlockPos pos,
            String label,
            double distanceMeters
    ) {
        private NearestObject {
            label = Objects.requireNonNullElse(label, "").trim();
            distanceMeters = Math.max(0.0D, distanceMeters);
        }
    }

    private record PointRef(
            String objectType,
            int index,
            String role,
            String dimension,
            BlockPos pos
    ) {
        private PointRef {
            objectType = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
            index = Math.max(0, index);
            role = Objects.requireNonNullElse(role, "").trim();
            dimension = Objects.requireNonNullElse(dimension, "").trim();
        }

        private boolean sameObject(PointRef other) {
            return other != null
                    && objectType.equals(other.objectType())
                    && index == other.index();
        }

        private String label() {
            return role.isBlank() ? objectType + "[" + index + "]" : objectType + "[" + index + "]." + role;
        }
    }

    private record PurchasablePlacement(
            String objectType,
            String objectId,
            ResourceKey<Level> dimension,
            BlockPos pos,
            Block block
    ) {
        private PurchasablePlacement {
            objectType = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            pos = pos == null ? BlockPos.ZERO : pos;
            Objects.requireNonNull(block, "block");
        }
    }

    private record PlacementRollback(
            ServerLevel level,
            BlockPos pos,
            BlockState previousState,
            PlacementRollback next
    ) {
        private PlacementRollback(ServerLevel level, BlockPos pos, BlockState previousState) {
            this(level, pos, previousState, null);
        }
    }

    private record DuplicatePosition(
            PointRef first,
            PointRef second
    ) {
        private String detail() {
            PointRef ref = second == null ? first : second;
            String dimension = ref == null ? "" : ref.dimension();
            BlockPos pos = ref == null ? null : ref.pos();
            String firstLabel = first == null ? "unknown" : first.label();
            String secondLabel = second == null ? "unknown" : second.label();
            return dimension + " " + format(pos) + " " + firstLabel + " <-> " + secondLabel;
        }

        private static String format(BlockPos pos) {
            return pos == null ? "-" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        }
    }
}
