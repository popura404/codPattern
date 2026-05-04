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
import com.phasetranscrystal.fpsmatch.common.item.ZombiesDeployTool;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ZombiesDeployToolService {
    private static final ZombiesDeployToolService INSTANCE = new ZombiesDeployToolService();
    private static final double BLOCK_PICK_REACH = 8.0D;

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

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> capturePlayerPosition(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request
    ) {
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        Map<String, String> fields = new LinkedHashMap<>(draft.fields());
        BlockPos pos = player.blockPosition();
        fields.put("dimension", player.serverLevel().dimension().location().toString());
        setPosition(fields, "pos", pos);
        setPosition(fields, "interaction", pos);
        fields.put("yaw", Float.toString(player.getYRot()));
        fields.put("pitch", Float.toString(player.getXRot()));
        ZombiesDeployDraft updated = draft.withFields(fields);
        ZombiesDeployTool.saveDraft(stack, updated);
        return snapshot(
                player,
                stack,
                updated,
                "message.codpattern.zombies.deploy.captured_player_pos",
                "ok",
                formatPos(pos));
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> captureLookBlock(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request
    ) {
        Optional<BlockPos> hit = lookBlock(player);
        if (hit.isEmpty()) {
            return failure(player, stack, request, "capture.no_block", "message.codpattern.zombies.deploy.no_look_block", "");
        }
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        Map<String, String> fields = new LinkedHashMap<>(draft.fields());
        fields.put("dimension", player.serverLevel().dimension().location().toString());
        setPosition(fields, "pos", hit.get());
        setPosition(fields, "interaction", hit.get());
        ZombiesDeployDraft updated = draft.withFields(fields);
        ZombiesDeployTool.saveDraft(stack, updated);
        return snapshot(
                player,
                stack,
                updated,
                "message.codpattern.zombies.deploy.captured_look_block",
                "ok",
                formatPos(hit.get()));
    }

    public ZombiesDeployServiceResult<ZombiesDeploySnapshot> setAreaPos(
            ServerPlayer player,
            ItemStack stack,
            ZombiesDeployDraft request,
            boolean first
    ) {
        BlockPos pos = lookBlock(player).orElseGet(player::blockPosition);
        if (first) {
            ZombiesDeployTool.setAreaPos1(stack, pos);
        } else {
            ZombiesDeployTool.setAreaPos2(stack, pos);
        }
        ZombiesDeployDraft draft = normalizeDraft(player, stack, request);
        Map<String, String> fields = new LinkedHashMap<>(draft.fields());
        fields.put("dimension", player.serverLevel().dimension().location().toString());
        setPosition(fields, first ? "areaFrom" : "areaTo", pos);
        ZombiesDeployDraft updated = draft.withFields(fields);
        ZombiesDeployTool.saveDraft(stack, updated);
        return snapshot(
                player,
                stack,
                updated,
                first
                        ? "message.codpattern.zombies.deploy.area_pos1"
                        : "message.codpattern.zombies.deploy.area_pos2",
                "ok",
                formatPos(pos));
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
                draft.selectedMap(),
                draft.objectType(),
                edit.selectedIndex(),
                draft.profileKey(),
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
        boolean activeMap = ZombiesMapOccupancyService.instance().isOccupied(BuiltInGameModes.ZOMBIES, draft.selectedMap());
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
        int count = resolveMap(selectedMap).map(map -> objectSummaries(map.objects(), objectType).size()).orElse(0);
        int selectedIndex = count <= 0 ? -1 : Math.max(0, Math.min(base.selectedIndex() < 0 ? 0 : base.selectedIndex(), count - 1));
        Map<String, String> fields = base.fields().isEmpty()
                ? defaultFields(player, objectType)
                : mergeDefaults(objectType, base.fields());
        return new ZombiesDeployDraft(
                selectedMap,
                objectType,
                selectedIndex,
                ZombiesDeployFieldSchema.normalizeProfile(base.profileKey()),
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
        return new ZombiesDeploySnapshot(
                availableMaps(),
                draft.selectedMap(),
                ZombiesDeployFieldSchema.objectTypes().stream()
                        .map(type -> new ZombiesDeploySnapshot.ObjectTypeOption(type.key(), type.labelKey()))
                        .toList(),
                draft.objectType(),
                draft.selectedIndex(),
                summaries,
                fieldValues(draft.objectType(), draft.fields()),
                draft.profileKey(),
                ZombiesDeployFieldSchema.profiles(),
                map.map(value -> validationLines(value, draft.profileKey())).orElse(List.of()),
                ZombiesMapOccupancyService.instance().isOccupied(BuiltInGameModes.ZOMBIES, draft.selectedMap()),
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

    private Optional<BlockPos> lookBlock(ServerPlayer player) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        BlockHitResult hit = player.serverLevel().clip(new ClipContext(
                eyePosition,
                eyePosition.add(look.scale(BLOCK_PICK_REACH)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player));
        return hit.getType() == HitResult.Type.BLOCK ? Optional.of(hit.getBlockPos()) : Optional.empty();
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
