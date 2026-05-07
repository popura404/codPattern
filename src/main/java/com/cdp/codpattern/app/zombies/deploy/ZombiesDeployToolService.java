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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
        ZombiesDeployDraft updated = applyWorkflowStep(player, draft, draft.workflowStep());
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
        ZombiesDeployDraft updated = applyWorkflowStep(player, draft, next);
        ZombiesDeployTool.saveDraft(stack, updated);
        return snapshot(player, stack, updated, "message.codpattern.zombies.deploy.refreshed", "ok.next_step", next);
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
            return failure(player, stack, draft, "map.active_area_update_deferred", "message.codpattern.zombies.deploy.saved_active_map", oldMap.getMapName());
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
            ZombiesDeploySnapshot snapshot = buildSnapshot(player, draft, "message.codpattern.zombies.deploy.save_failed_rollback", "save_failed_rolled_back", oldMap.getMapName());
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
        if ("lookAt".equals(target)) {
            setPositionLoose(fields, "lookAt", clickedPos);
            BlockPos pos = readPositionIfPresent(fields, "pos");
            applyLookAtYawPitch(fields, pos, clickedPos);
        } else {
            setPosition(fields, target, clickedPos);
            if ("pos".equals(target)) {
                BlockPos lookAt = readPositionIfPresent(fields, "lookAt");
                applyLookAtYawPitch(fields, clickedPos, lookAt);
            }
        }
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

        map.applyObjects(edit.objects());
        try {
            CodMapPersistence.saveMapOrRollback(map, () -> map.applyObjects(previousObjects));
        } catch (RuntimeException e) {
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
        int count = resolveMap(selectedMap).map(map -> objectSummaries(map.objects(), objectType).size()).orElse(0);
        int selectedIndex = count <= 0 ? -1 : Math.max(0, Math.min(base.selectedIndex() < 0 ? 0 : base.selectedIndex(), count - 1));
        Map<String, String> fields = base.fields().isEmpty()
                ? defaultFields(player, objectType)
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

    private ZombiesDeployDraft applyWorkflowStep(ServerPlayer player, ZombiesDeployDraft draft, String step) {
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
        String type = switch (normalizedStep) {
            case ZombiesDeployDraft.WORKFLOW_INITIAL -> ZombiesDeployFieldSchema.INITIAL;
            case ZombiesDeployDraft.WORKFLOW_ZOMBIE_SPAWN -> ZombiesDeployFieldSchema.ZOMBIE_SPAWN;
            case ZombiesDeployDraft.WORKFLOW_BARRIER -> ZombiesDeployFieldSchema.BARRIER;
            case ZombiesDeployDraft.WORKFLOW_INTERACT -> ZombiesDeployFieldSchema.POWER_SWITCH;
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
        int interactionCount = resolved.weaponWalls().size()
                + resolved.ammoBoxes().size()
                + resolved.armorStations().size()
                + resolved.sodaMachines().size()
                + resolved.ultimateMachines().size()
                + (resolved.powerSwitch().isPresent() ? 1 : 0);
        boolean hasPowerSwitch = resolved.powerSwitch().isPresent();
        boolean hasUltimateMachine = !resolved.ultimateMachines().isEmpty();
        String interactionDetail = "powerSwitch=" + (hasPowerSwitch ? "1" : "0")
                + ";ultimateMachine=" + (hasUltimateMachine ? "1" : "0")
                + ";total=" + interactionCount;
        boolean interactionComplete = hasPowerSwitch && hasUltimateMachine;
        return List.of(
                new ZombiesDeploySnapshot.StepStatus("map", "", hasMap ? "1" : "0", hasMap),
                new ZombiesDeploySnapshot.StepStatus("initial", "", Integer.toString(resolved.initialSpawns().size()), !resolved.initialSpawns().isEmpty()),
                new ZombiesDeploySnapshot.StepStatus("zombie_spawn", "", Integer.toString(resolved.zombieSpawns().size()), !resolved.zombieSpawns().isEmpty()),
                new ZombiesDeploySnapshot.StepStatus("barrier", "", Integer.toString(resolved.barriers().size()), !resolved.barriers().isEmpty()),
                new ZombiesDeploySnapshot.StepStatus("interact", "", interactionDetail, interactionComplete),
                new ZombiesDeploySnapshot.StepStatus("validate", "", hasMap ? "1" : "0", hasMap)
        );
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
            case ZombiesDeployDraft.WORKFLOW_BARRIER ->
                    resolved.barriers().isEmpty() ? "missing_barrier" : "";
            case ZombiesDeployDraft.WORKFLOW_INTERACT -> {
                boolean hasPower = resolved.powerSwitch().isPresent();
                boolean hasUltimate = !resolved.ultimateMachines().isEmpty();
                if (!hasPower) {
                    yield "missing_power_switch";
                }
                if (!hasUltimate) {
                    yield "missing_ultimate_machine";
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
        if (code.contains("missing_ultimate_machine") || code.contains("invalid_ultimate_machine")) {
            return issueTargetForObject(false, ZombiesDeployDraft.WORKFLOW_INTERACT, ZombiesDeployFieldSchema.ULTIMATE_MACHINE, subjectObjectId, objects);
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
        if (ZombiesDeployFieldSchema.WEAPON_WALL.equals(mappedObjectType)
                || ZombiesDeployFieldSchema.AMMO_BOX.equals(mappedObjectType)
                || ZombiesDeployFieldSchema.ARMOR_STATION.equals(mappedObjectType)
                || ZombiesDeployFieldSchema.SODA_MACHINE.equals(mappedObjectType)) {
            workflowStep = ZombiesDeployDraft.WORKFLOW_VALIDATE;
        }
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

    private void applyLookAtYawPitch(Map<String, String> fields, BlockPos fromPos, BlockPos lookAtPos) {
        if (fields == null || fromPos == null || lookAtPos == null) {
            return;
        }
        if (!fields.containsKey("yaw") || !fields.containsKey("pitch")) {
            return;
        }
        double fromX = fromPos.getX() + 0.5D;
        double fromY = fromPos.getY() + 1.62D;
        double fromZ = fromPos.getZ() + 0.5D;
        double toX = lookAtPos.getX() + 0.5D;
        double toY = lookAtPos.getY() + 0.5D;
        double toZ = lookAtPos.getZ() + 0.5D;
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, horizontal));
        fields.put("yaw", Float.toString(yaw));
        fields.put("pitch", Float.toString(pitch));
    }

    private String detail(ResourceKey<Level> dimension, BlockPos pos) {
        String dimensionId = dimension == null || dimension.location() == null ? "" : dimension.location().toString();
        return dimensionId + " " + formatPos(pos);
    }

    private String formatPos(BlockPos pos) {
        return pos == null ? "-" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
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
}
