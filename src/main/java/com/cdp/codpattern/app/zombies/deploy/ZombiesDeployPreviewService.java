package com.cdp.codpattern.app.zombies.deploy;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.zombies.map.ZombiesMapObjects;
import com.cdp.codpattern.app.zombies.map.object.ZombiesAmmoBoxData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesArmorStationData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesBarrierData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesInitialSpawnData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesPowerSwitchData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesSodaMachineData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesUltimateMachineData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesWeaponWallData;
import com.cdp.codpattern.app.zombies.map.object.ZombiesZombieSpawnData;
import com.cdp.codpattern.compat.fpsmatch.map.ZombiesMap;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.packet.AddAreaDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.AddPointDataS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.RemoveDebugDataByPrefixS2CPacket;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.util.PreviewColorUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ZombiesDeployPreviewService {
    private static final ZombiesDeployPreviewService INSTANCE = new ZombiesDeployPreviewService();
    private static final String HELD_PREVIEW_STATE_TAG = "HeldZombiesDeployPreviewState";
    private static final int HELD_PREVIEW_REFRESH_INTERVAL = 10;

    private static final int INITIAL_COLOR = 0xFF4BB56C;
    private static final int ZOMBIE_COLOR = 0xFFE85D5D;
    private static final int BARRIER_COLOR = 0xFFF1C94B;
    private static final int SHOP_COLOR = 0xFF3A7BFF;
    private static final int POWER_COLOR = 0xFFE05DB1;
    private static final int SLOT_A_COLOR = 0xFF4EE0B5;
    private static final int SLOT_B_COLOR = 0xFFFFAD5A;
    private static final int SINGLETON_MISSING_COLOR = 0xFFFF5D73;
    private static final int SINGLETON_DUPLICATE_COLOR = 0xFFFFD166;
    private static final int MAP_REGISTRATION_DRAFT_COLOR = 0xFFFFFFFF;

    public static ZombiesDeployPreviewService instance() {
        return INSTANCE;
    }

    private ZombiesDeployPreviewService() {
    }

    public ZombiesDeployServiceResult<Void> refreshPreview(ServerPlayer player, ZombiesDeployDraft draft) {
        if (player == null) {
            return ZombiesDeployServiceResult.failure(
                    "player.missing",
                    "message.codpattern.zombies.deploy.player_missing",
                    null);
        }
        if (draft == null) {
            clearHeldPreview(player);
            return ZombiesDeployServiceResult.failure(
                    "draft.missing",
                    "message.codpattern.zombies.deploy.snapshot_missing",
                    null);
        }
        if (ZombiesDeployDraft.STAGE_MAP_REGISTRATION.equals(draft.workspaceStage())) {
            return refreshMapRegistrationPreview(player, draft);
        }
        String type = ZombiesDeployFieldSchema.normalizeObjectType(draft.objectType());
        boolean showDraft = draft.selectedIndex() >= 0 || !draft.fields().isEmpty();
        Map<String, String> fields = showDraft
                ? (draft.fields().isEmpty()
                        ? defaultFields(player, type)
                        : mergeDefaults(type, draft.fields()))
                : Map.of();
        return refreshPreview(player, new PreviewRequest(
                draft.selectedMap(),
                type,
                draft.capturePreset(),
                draft.selectedIndex(),
                fields,
                showDraft));
    }

    public ZombiesDeployServiceResult<Void> refreshPreview(ServerPlayer player, ZombiesDeploySnapshot snapshot) {
        if (player == null) {
            return ZombiesDeployServiceResult.failure(
                    "player.missing",
                    "message.codpattern.zombies.deploy.player_missing",
                    null);
        }
        if (snapshot == null) {
            clearHeldPreview(player);
            return ZombiesDeployServiceResult.failure(
                    "snapshot.missing",
                    "message.codpattern.zombies.deploy.snapshot_missing",
                    null);
        }
        return refreshPreview(player, new PreviewRequest(
                snapshot.selectedMap(),
                snapshot.selectedObjectType(),
                snapshot.capturePreset(),
                snapshot.selectedIndex(),
                fieldMap(snapshot),
                true));
    }

    private ZombiesDeployServiceResult<Void> refreshPreview(ServerPlayer player, PreviewRequest request) {
        if (request.selectedMap().isBlank()) {
            clearHeldPreview(player);
            return ZombiesDeployServiceResult.failure(
                    "preview.map_missing",
                    "message.codpattern.zombies.deploy.preview_map_missing",
                    null);
        }

        Optional<ZombiesMap> mapOptional = resolveMap(request.selectedMap());
        if (mapOptional.isEmpty()) {
            clearHeldPreview(player);
            return ZombiesDeployServiceResult.failure(
                    "map.not_found",
                    "message.codpattern.zombies.deploy.map_not_found",
                    null,
                    request.selectedMap());
        }

        ZombiesMap map = mapOptional.get();
        if (!map.getServerLevel().dimension().equals(player.serverLevel().dimension())) {
            clearHeldPreview(player);
            return ZombiesDeployServiceResult.failure(
                    "preview.dimension_mismatch",
                    "message.codpattern.zombies.deploy.preview_dimension_mismatch",
                    null,
                    request.selectedMap());
        }

        DraftPreview draftPreview = null;
        if (request.showDraft()) {
            try {
                draftPreview = parseDraftPreview(
                        request.selectedObjectType(),
                        request.capturePreset(),
                        request.fields(),
                        player.serverLevel().dimension());
            } catch (PreviewParseException e) {
                clearHeldPreview(player);
                return ZombiesDeployServiceResult.failure(
                        e.code(),
                        "message.codpattern.zombies.deploy.preview_field_invalid",
                        null,
                        e.getMessage());
            }
        }

        String signature = buildSignature(player, map, request, draftPreview);
        CompoundTag data = player.getPersistentData();
        String previousSignature = data.getString(HELD_PREVIEW_STATE_TAG);
        if (signature.equals(previousSignature) && player.tickCount % HELD_PREVIEW_REFRESH_INTERVAL != 0) {
            return ZombiesDeployServiceResult.success(
                    null,
                    "message.codpattern.zombies.deploy.preview_ready");
        }

        FPSMatch.sendToPlayer(player, new RemoveDebugDataByPrefixS2CPacket(getHeldPreviewPrefix(player)));
        FPSMatch.sendToPlayer(player, new AddAreaDataS2CPacket(
                getHeldPreviewMapKey(player),
                Component.literal(map.getMapName()),
                PreviewColorUtil.getMapPreviewColor(BuiltInGameModes.ZOMBIES),
                map.getMapArea()));

        sendCurrentObjectList(player, request, map.objects());
        if (draftPreview != null) {
            sendDraft(player, request, draftPreview);
        }
        sendSingletonStatusHint(player, map, request.selectedObjectType(), map.objects());
        sendNearestObjectHint(player, request, map.objects());

        data.putString(HELD_PREVIEW_STATE_TAG, signature);
        return ZombiesDeployServiceResult.success(
                null,
                "message.codpattern.zombies.deploy.preview_ready");
    }

    public static void clearHeldPreview(ServerPlayer player) {
        if (player == null || !player.getPersistentData().contains(HELD_PREVIEW_STATE_TAG)) {
            return;
        }
        FPSMatch.sendToPlayer(player, new RemoveDebugDataByPrefixS2CPacket(getHeldPreviewPrefix(player)));
        player.getPersistentData().remove(HELD_PREVIEW_STATE_TAG);
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

    private List<ZombiesMap> availableZombiesMaps() {
        return FPSMCore.getInstance()
                .getMapNamesWithType(BuiltInGameModes.ZOMBIES)
                .stream()
                .map(this::resolveMap)
                .flatMap(Optional::stream)
                .toList();
    }

    private ZombiesDeployServiceResult<Void> refreshMapRegistrationPreview(ServerPlayer player, ZombiesDeployDraft draft) {
        List<ZombiesMap> maps = availableZombiesMaps();
        String signature = buildMapRegistrationSignature(player, maps, draft);
        CompoundTag data = player.getPersistentData();
        String previousSignature = data.getString(HELD_PREVIEW_STATE_TAG);
        if (signature.equals(previousSignature) && player.tickCount % HELD_PREVIEW_REFRESH_INTERVAL != 0) {
            return ZombiesDeployServiceResult.success(
                    null,
                    "message.codpattern.zombies.deploy.preview_ready");
        }

        FPSMatch.sendToPlayer(player, new RemoveDebugDataByPrefixS2CPacket(getHeldPreviewPrefix(player)));
        for (int i = 0; i < maps.size(); i++) {
            ZombiesMap map = maps.get(i);
            AreaData area = map.getMapArea();
            sendArea(
                    player,
                    getHeldPreviewMapKey(player, i),
                    map.getMapName(),
                    PreviewColorUtil.getMapPreviewColor(BuiltInGameModes.ZOMBIES),
                    map.getServerLevel().dimension(),
                    area.pos1(),
                    area.pos2());
        }
        if (draft.mapPos1() != null && draft.mapPos2() != null) {
            sendArea(
                    player,
                    getHeldPreviewMapDraftKey(player),
                    "new zombies map",
                    MAP_REGISTRATION_DRAFT_COLOR,
                    player.serverLevel().dimension(),
                    draft.mapPos1(),
                    draft.mapPos2());
        }

        data.putString(HELD_PREVIEW_STATE_TAG, signature);
        return ZombiesDeployServiceResult.success(
                null,
                "message.codpattern.zombies.deploy.preview_ready");
    }

    private Map<String, String> fieldMap(ZombiesDeploySnapshot snapshot) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (ZombiesDeploySnapshot.FieldValue field : snapshot.fields()) {
            fields.put(field.key(), field.value());
        }
        return fields;
    }

    private Map<String, String> defaultFields(ServerPlayer player, String objectType) {
        Map<String, String> fields = new LinkedHashMap<>(ZombiesDeployFieldSchema.defaultFields(objectType));
        fields.put("dimension", player.serverLevel().dimension().location().toString());
        BlockPos pos = player.blockPosition();
        putPosition(fields, "pos", pos);
        putPosition(fields, "interaction", pos);
        putPosition(fields, "areaFrom", pos);
        putPosition(fields, "areaTo", pos);
        fields.computeIfPresent("yaw", (key, value) -> Float.toString(player.getYRot()));
        fields.computeIfPresent("pitch", (key, value) -> Float.toString(player.getXRot()));
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

    private void putPosition(Map<String, String> fields, String prefix, BlockPos pos) {
        if (!fields.containsKey(prefix + "X")) {
            return;
        }
        fields.put(prefix + "X", Integer.toString(pos.getX()));
        fields.put(prefix + "Y", Integer.toString(pos.getY()));
        fields.put(prefix + "Z", Integer.toString(pos.getZ()));
    }

    private DraftPreview parseDraftPreview(
            String objectType,
            String capturePreset,
            Map<String, String> fields,
            ResourceKey<Level> expectedDimension
    ) {
        String type = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        ResourceKey<Level> dimension = dimension(fields);
        if (!dimension.equals(expectedDimension)) {
            throw new PreviewParseException(
                    "preview.field_dimension_mismatch",
                    "field dimension does not match the selected map dimension: " + dimension.location());
        }
        if (ZombiesDeployFieldSchema.BARRIER.equals(type)) {
            BlockPos interactionPos = optionalBlockPos(fields, "interaction");
            if (ZombiesDeployDraft.CAPTURE_BARRIER_INTERACTION.equals(
                    ZombiesDeployDraft.normalizeCapturePreset(capturePreset, type))) {
                return DraftPreview.point(dimension, interactionPos, interactionPos, null, Float.NaN);
            }
            return DraftPreview.area(
                    dimension,
                    blockPos(fields, "areaFrom"),
                    blockPos(fields, "areaTo"),
                    interactionPos);
        }
        BlockPos lookAtPos = optionalBlockPos(fields, "lookAt");
        float yaw = switch (type) {
            case ZombiesDeployFieldSchema.INITIAL, ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> floatField(fields, "yaw");
            default -> Float.NaN;
        };
        return DraftPreview.point(dimension, blockPos(fields, "pos"), optionalBlockPos(fields, "interaction"), lookAtPos, yaw);
    }

    private void sendCurrentObjectList(
            ServerPlayer player,
            PreviewRequest request,
            ZombiesMapObjects objects
    ) {
        ZombiesMapObjects resolved = objects == null ? ZombiesMapObjects.EMPTY : objects;
        String type = request.selectedObjectType();
        ZombiesDeployCaptureBinding binding = ZombiesDeployCaptureBinding.forObject(type, request.capturePreset());
        int selectedIndex = request.selectedIndex();
        switch (type) {
            case ZombiesDeployFieldSchema.INITIAL -> {
                for (int i = 0; i < resolved.initialSpawns().size(); i++) {
                    ZombiesInitialSpawnData data = resolved.initialSpawns().get(i);
                    String key = getHeldPreviewObjectKey(player, type, i);
                    String label = "INITIAL #" + (i + 1);
                    sendPoint(
                            player,
                            key,
                            label,
                            objectColor(type, selectedIndex == i),
                            data.dimension(),
                            data.pos(),
                            data.yaw());
                    sendSlotPointForField(player, key, label, binding, "pos", data.dimension(), data.pos(), selectedIndex == i);
                }
            }
            case ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> {
                for (int i = 0; i < resolved.zombieSpawns().size(); i++) {
                    ZombiesZombieSpawnData data = resolved.zombieSpawns().get(i);
                    String key = getHeldPreviewObjectKey(player, type, i);
                    String label = label(type, data.objectId(), i);
                    sendPoint(
                            player,
                            key,
                            label,
                            objectColor(type, selectedIndex == i),
                            data.dimension(),
                            data.pos(),
                            data.yaw());
                    sendSlotPointForField(player, key, label, binding, "pos", data.dimension(), data.pos(), selectedIndex == i);
                }
            }
            case ZombiesDeployFieldSchema.BARRIER -> {
                for (int i = 0; i < resolved.barriers().size(); i++) {
                    ZombiesBarrierData data = resolved.barriers().get(i);
                    String key = getHeldPreviewObjectKey(player, type, i);
                    String label = label(type, data.objectId(), i);
                    sendArea(
                            player,
                            key,
                            label,
                            objectColor(type, selectedIndex == i),
                            data.dimension(),
                            data.areaFrom(),
                            data.areaTo());
                    sendSlotPointForField(player, key, label, binding, "areaFrom", data.dimension(), data.areaFrom(), selectedIndex == i);
                    sendSlotPointForField(player, key, label, binding, "areaTo", data.dimension(), data.areaTo(), selectedIndex == i);
                    sendSlotPointForField(player, key, label, binding, "interaction", data.dimension(), data.interactionPos(), selectedIndex == i);
                }
            }
            case ZombiesDeployFieldSchema.WEAPON_WALL -> {
                for (int i = 0; i < resolved.weaponWalls().size(); i++) {
                    ZombiesWeaponWallData data = resolved.weaponWalls().get(i);
                    String key = getHeldPreviewObjectKey(player, type, i);
                    String label = label(type, data.objectId(), i);
                    sendPoint(
                            player,
                            key,
                            label,
                            objectColor(type, selectedIndex == i),
                            data.dimension(),
                            data.pos(),
                            Float.NaN);
                    sendSlotPointForField(player, key, label, binding, "pos", data.dimension(), data.pos(), selectedIndex == i);
                    sendSlotPointForField(player, key, label, binding, "interaction", data.dimension(), data.interactionPos().orElse(null), selectedIndex == i);
                }
            }
            case ZombiesDeployFieldSchema.AMMO_BOX -> {
                for (int i = 0; i < resolved.ammoBoxes().size(); i++) {
                    ZombiesAmmoBoxData data = resolved.ammoBoxes().get(i);
                    String key = getHeldPreviewObjectKey(player, type, i);
                    String label = label(type, data.objectId(), i);
                    sendPoint(
                            player,
                            key,
                            label,
                            objectColor(type, selectedIndex == i),
                            data.dimension(),
                            data.pos(),
                            Float.NaN);
                    sendSlotPointForField(player, key, label, binding, "pos", data.dimension(), data.pos(), selectedIndex == i);
                    sendSlotPointForField(player, key, label, binding, "interaction", data.dimension(), data.interactionPos().orElse(null), selectedIndex == i);
                }
            }
            case ZombiesDeployFieldSchema.ARMOR_STATION -> {
                for (int i = 0; i < resolved.armorStations().size(); i++) {
                    ZombiesArmorStationData data = resolved.armorStations().get(i);
                    String key = getHeldPreviewObjectKey(player, type, i);
                    String label = label(type, data.objectId(), i);
                    sendPoint(
                            player,
                            key,
                            label,
                            objectColor(type, selectedIndex == i),
                            data.dimension(),
                            data.pos(),
                            Float.NaN);
                    sendSlotPointForField(player, key, label, binding, "pos", data.dimension(), data.pos(), selectedIndex == i);
                    sendSlotPointForField(player, key, label, binding, "interaction", data.dimension(), data.interactionPos().orElse(null), selectedIndex == i);
                }
            }
            case ZombiesDeployFieldSchema.POWER_SWITCH -> resolved.powerSwitch().ifPresent(data -> {
                String key = getHeldPreviewObjectKey(player, type, 0);
                String label = label(type, data.objectId(), 0);
                sendPoint(
                        player,
                        key,
                        label,
                        objectColor(type, selectedIndex == 0),
                        data.dimension(),
                        data.pos(),
                        Float.NaN);
                sendSlotPointForField(player, key, label, binding, "pos", data.dimension(), data.pos(), selectedIndex == 0);
            });
            case ZombiesDeployFieldSchema.SODA_MACHINE -> {
                for (int i = 0; i < resolved.sodaMachines().size(); i++) {
                    ZombiesSodaMachineData data = resolved.sodaMachines().get(i);
                    String key = getHeldPreviewObjectKey(player, type, i);
                    String label = label(type, data.objectId(), i);
                    sendPoint(
                            player,
                            key,
                            label,
                            objectColor(type, selectedIndex == i),
                            data.dimension(),
                            data.pos(),
                            Float.NaN);
                    sendSlotPointForField(player, key, label, binding, "pos", data.dimension(), data.pos(), selectedIndex == i);
                    sendSlotPointForField(player, key, label, binding, "interaction", data.dimension(), data.interactionPos().orElse(null), selectedIndex == i);
                }
            }
            case ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> {
                for (int i = 0; i < resolved.ultimateMachines().size(); i++) {
                    ZombiesUltimateMachineData data = resolved.ultimateMachines().get(i);
                    String key = getHeldPreviewObjectKey(player, type, i);
                    String label = label(type, data.objectId(), i);
                    sendPoint(
                            player,
                            key,
                            label,
                            objectColor(type, selectedIndex == i),
                            data.dimension(),
                            data.pos(),
                            Float.NaN);
                    sendSlotPointForField(player, key, label, binding, "pos", data.dimension(), data.pos(), selectedIndex == i);
                    sendSlotPointForField(player, key, label, binding, "interaction", data.dimension(), data.interactionPos().orElse(null), selectedIndex == i);
                }
            }
            default -> {
            }
        }
    }

    private void sendDraft(ServerPlayer player, PreviewRequest request, DraftPreview draftPreview) {
        String type = request.selectedObjectType();
        ZombiesDeployCaptureBinding binding = ZombiesDeployCaptureBinding.forObject(type, request.capturePreset());
        String key = getHeldPreviewDraftKey(player);
        String label = type + " draft";
        if (draftPreview.area()) {
            sendArea(
                    player,
                    key,
                    label,
                    draftColor(type),
                    draftPreview.dimension(),
                    draftPreview.areaFrom(),
                    draftPreview.areaTo());
            sendSlotPointForField(player, key, label, binding, "areaFrom", draftPreview.dimension(), draftPreview.areaFrom(), true);
            sendSlotPointForField(player, key, label, binding, "areaTo", draftPreview.dimension(), draftPreview.areaTo(), true);
            sendSlotPointForField(player, key, label, binding, "interaction", draftPreview.dimension(), draftPreview.interactionPos(), true);
            return;
        }
        sendPoint(
                player,
                key,
                label,
                draftColor(type),
                draftPreview.dimension(),
                draftPreview.pos(),
                draftPreview.yaw());
        sendSlotPointForField(player, key, label, binding, "pos", draftPreview.dimension(), draftPreview.pos(), true);
        sendSlotPointForField(player, key, label, binding, "interaction", draftPreview.dimension(), draftPreview.interactionPos(), true);
        sendSlotPointForField(player, key, label, binding, "lookAt", draftPreview.dimension(), draftPreview.lookAtPos(), true);
    }

    private void sendPoint(
            ServerPlayer player,
            String key,
            String label,
            int color,
            ResourceKey<Level> dimension,
            BlockPos pos,
            float yaw
    ) {
        if (!isCurrentDimension(player, dimension) || pos == null) {
            return;
        }
        FPSMatch.sendToPlayer(player, new AddPointDataS2CPacket(
                key,
                Component.literal(label),
                color,
                Vec3.atCenterOf(pos),
                yaw));
    }

    private void sendArea(
            ServerPlayer player,
            String key,
            String label,
            int color,
            ResourceKey<Level> dimension,
            BlockPos pos1,
            BlockPos pos2
    ) {
        if (!isCurrentDimension(player, dimension) || pos1 == null || pos2 == null) {
            return;
        }
        FPSMatch.sendToPlayer(player, new AddAreaDataS2CPacket(
                key,
                Component.literal(label),
                color,
                new AreaData(pos1, pos2)));
    }

    private boolean isCurrentDimension(ServerPlayer player, ResourceKey<Level> dimension) {
        return dimension != null && dimension.equals(player.serverLevel().dimension());
    }

    private String buildSignature(
            ServerPlayer player,
            ZombiesMap map,
            PreviewRequest request,
            DraftPreview draftPreview
    ) {
        StringBuilder builder = new StringBuilder()
                .append(player.serverLevel().dimension().location())
                .append('|').append(request.selectedMap())
                .append('|').append(request.selectedObjectType())
                .append('|').append(request.selectedIndex())
                .append('|').append(Objects.hash(map.objects()))
                .append('|').append(map.getMapArea().pos1().asLong())
                .append('|').append(map.getMapArea().pos2().asLong())
                .append('|').append(draftPreview == null ? "no_draft" : draftPreview.signature());
        request.fields().forEach((key, value) -> builder.append('|').append(key).append('=').append(value));
        return builder.toString();
    }

    private String buildMapRegistrationSignature(ServerPlayer player, List<ZombiesMap> maps, ZombiesDeployDraft draft) {
        StringBuilder builder = new StringBuilder()
                .append(player.serverLevel().dimension().location())
                .append("|map_registration")
                .append('|').append(draft.mapPos1() == null ? "-" : draft.mapPos1().asLong())
                .append('|').append(draft.mapPos2() == null ? "-" : draft.mapPos2().asLong());
        for (ZombiesMap map : maps) {
            AreaData area = map.getMapArea();
            builder.append('|')
                    .append(map.getMapName())
                    .append('@').append(map.getServerLevel().dimension().location())
                    .append(':').append(area.pos1().asLong())
                    .append(':').append(area.pos2().asLong());
        }
        return builder.toString();
    }

    private ResourceKey<Level> dimension(Map<String, String> fields) {
        String value = text(fields, "dimension");
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new PreviewParseException(
                    "preview.invalid_dimension",
                    "field dimension must be a resource location: " + value);
        }
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    private BlockPos blockPos(Map<String, String> fields, String prefix) {
        return new BlockPos(
                intField(fields, prefix + "X"),
                intField(fields, prefix + "Y"),
                intField(fields, prefix + "Z"));
    }

    private BlockPos optionalBlockPos(Map<String, String> fields, String prefix) {
        if (!fields.containsKey(prefix + "X")
                || !fields.containsKey(prefix + "Y")
                || !fields.containsKey(prefix + "Z")) {
            return null;
        }
        return blockPos(fields, prefix);
    }

    private int intField(Map<String, String> fields, String key) {
        String value = text(fields, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new PreviewParseException(
                    "preview.invalid_integer",
                    "field " + key + " must be an integer: " + value);
        }
    }

    private float floatField(Map<String, String> fields, String key) {
        String value = text(fields, key);
        try {
            float parsed = Float.parseFloat(value);
            if (!Float.isFinite(parsed)) {
                throw new NumberFormatException("not finite");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new PreviewParseException(
                    "preview.invalid_decimal",
                    "field " + key + " must be a finite decimal: " + value);
        }
    }

    private String text(Map<String, String> fields, String key) {
        return Objects.requireNonNullElse(fields.get(key), "").trim();
    }

    private String label(String type, String objectId, int index) {
        String id = Objects.requireNonNullElse(objectId, "").trim();
        return id.isEmpty() ? type + " #" + (index + 1) : id;
    }

    private void sendSlotPointForField(
            ServerPlayer player,
            String keyBase,
            String labelBase,
            ZombiesDeployCaptureBinding binding,
            String field,
            ResourceKey<Level> dimension,
            BlockPos pos,
            boolean selected
    ) {
        if (pos == null || binding == null || field == null || field.isBlank()) {
            return;
        }
        if (field.equals(binding.slotA())) {
            sendPoint(
                    player,
                    keyBase + ":slotA:" + field,
                    labelBase + " [A] " + field,
                    slotColor(ZombiesDeployCaptureBinding.CaptureSlot.A, selected),
                    dimension,
                    pos,
                    Float.NaN);
        }
        if (field.equals(binding.slotB())) {
            sendPoint(
                    player,
                    keyBase + ":slotB:" + field,
                    labelBase + " [B] " + field,
                    slotColor(ZombiesDeployCaptureBinding.CaptureSlot.B, selected),
                    dimension,
                    pos,
                    Float.NaN);
        }
    }

    private int slotColor(ZombiesDeployCaptureBinding.CaptureSlot slot, boolean selected) {
        int color = slot == ZombiesDeployCaptureBinding.CaptureSlot.A ? SLOT_A_COLOR : SLOT_B_COLOR;
        return selected ? mix(color, 0xFFFFFFFF, 0.30F) : color;
    }

    private void sendNearestObjectHint(
            ServerPlayer player,
            PreviewRequest request,
            ZombiesMapObjects objects
    ) {
        NearestObjectHint hint = nearestObjectHint(player, request, objects);
        if (hint == null || hint.pos() == null) {
            return;
        }
        String key = getHeldPreviewNearestKey(player, request.selectedObjectType());
        String label = "nearest " + request.selectedObjectType()
                + " " + hint.objectLabel()
                + " "
                + String.format(Locale.ROOT, "%.1fm", hint.distanceMeters());
        sendPoint(
                player,
                key,
                label,
                mix(baseColor(request.selectedObjectType()), 0xFFFFFFFF, 0.80F),
                player.serverLevel().dimension(),
                hint.pos(),
                Float.NaN);
    }

    private NearestObjectHint nearestObjectHint(
            ServerPlayer player,
            PreviewRequest request,
            ZombiesMapObjects objects
    ) {
        if (player == null || request == null) {
            return null;
        }
        ZombiesMapObjects resolved = objects == null ? ZombiesMapObjects.EMPTY : objects;
        Vec3 playerPos = player.position();
        String type = request.selectedObjectType();
        String preset = request.capturePreset();
        NearestObjectHint best = null;
        switch (type) {
            case ZombiesDeployFieldSchema.INITIAL -> {
                for (int i = 0; i < resolved.initialSpawns().size(); i++) {
                    ZombiesInitialSpawnData data = resolved.initialSpawns().get(i);
                    best = nearest(best, playerPos, data.pos(), label(type, "INITIAL#" + (i + 1), i));
                }
            }
            case ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> {
                for (int i = 0; i < resolved.zombieSpawns().size(); i++) {
                    ZombiesZombieSpawnData data = resolved.zombieSpawns().get(i);
                    best = nearest(best, playerPos, data.pos(), label(type, data.objectId(), i));
                }
            }
            case ZombiesDeployFieldSchema.BARRIER -> {
                boolean interactionMode = ZombiesDeployDraft.CAPTURE_BARRIER_INTERACTION.equals(
                        ZombiesDeployDraft.normalizeCapturePreset(preset, type));
                for (int i = 0; i < resolved.barriers().size(); i++) {
                    ZombiesBarrierData data = resolved.barriers().get(i);
                    BlockPos anchor = interactionMode
                            ? optionalNonOrigin(data.interactionPos()).orElse(areaCenter(data.areaFrom(), data.areaTo()))
                            : areaCenter(data.areaFrom(), data.areaTo());
                    best = nearest(best, playerPos, anchor, label(type, data.objectId(), i));
                }
            }
            case ZombiesDeployFieldSchema.WEAPON_WALL -> {
                for (int i = 0; i < resolved.weaponWalls().size(); i++) {
                    ZombiesWeaponWallData data = resolved.weaponWalls().get(i);
                    best = nearest(best, playerPos, data.pos(), label(type, data.objectId(), i));
                }
            }
            case ZombiesDeployFieldSchema.AMMO_BOX -> {
                for (int i = 0; i < resolved.ammoBoxes().size(); i++) {
                    ZombiesAmmoBoxData data = resolved.ammoBoxes().get(i);
                    best = nearest(best, playerPos, data.pos(), label(type, data.objectId(), i));
                }
            }
            case ZombiesDeployFieldSchema.ARMOR_STATION -> {
                for (int i = 0; i < resolved.armorStations().size(); i++) {
                    ZombiesArmorStationData data = resolved.armorStations().get(i);
                    best = nearest(best, playerPos, data.pos(), label(type, data.objectId(), i));
                }
            }
            case ZombiesDeployFieldSchema.POWER_SWITCH -> {
                if (resolved.powerSwitch().isPresent()) {
                    ZombiesPowerSwitchData data = resolved.powerSwitch().get();
                    best = nearest(best, playerPos, data.pos(), label(type, data.objectId(), 0));
                }
            }
            case ZombiesDeployFieldSchema.SODA_MACHINE -> {
                for (int i = 0; i < resolved.sodaMachines().size(); i++) {
                    ZombiesSodaMachineData data = resolved.sodaMachines().get(i);
                    best = nearest(best, playerPos, data.pos(), label(type, data.objectId(), i));
                }
            }
            case ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> {
                for (int i = 0; i < resolved.ultimateMachines().size(); i++) {
                    ZombiesUltimateMachineData data = resolved.ultimateMachines().get(i);
                    best = nearest(best, playerPos, data.pos(), label(type, data.objectId(), i));
                }
            }
            default -> {
                return null;
            }
        }
        return best;
    }

    private NearestObjectHint nearest(
            NearestObjectHint current,
            Vec3 playerPos,
            BlockPos candidatePos,
            String objectLabel
    ) {
        if (candidatePos == null) {
            return current;
        }
        double distance = Math.sqrt(playerPos.distanceToSqr(Vec3.atCenterOf(candidatePos)));
        NearestObjectHint candidate = new NearestObjectHint(candidatePos, objectLabel, distance);
        if (current == null || candidate.distanceMeters() < current.distanceMeters()) {
            return candidate;
        }
        return current;
    }

    private Optional<BlockPos> optionalNonOrigin(BlockPos pos) {
        if (pos == null) {
            return Optional.empty();
        }
        return pos.equals(BlockPos.ZERO) ? Optional.empty() : Optional.of(pos);
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

    private void sendSingletonStatusHint(
            ServerPlayer player,
            ZombiesMap map,
            String objectType,
            ZombiesMapObjects objects
    ) {
        String type = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        boolean singleton = ZombiesDeployFieldSchema.objectType(type)
                .map(ZombiesDeployFieldSchema.ObjectTypeSchema::singleObject)
                .orElse(false);
        if (!singleton) {
            return;
        }
        int count = countObjects(objects, type);
        if (count == 1) {
            return;
        }
        AreaData area = map.getMapArea();
        BlockPos center = new BlockPos(
                (area.pos1().getX() + area.pos2().getX()) / 2,
                (area.pos1().getY() + area.pos2().getY()) / 2,
                (area.pos1().getZ() + area.pos2().getZ()) / 2);
        String key = getHeldPreviewSingletonKey(player, type);
        if (count <= 0) {
            sendPoint(
                    player,
                    key,
                    type + " missing",
                    SINGLETON_MISSING_COLOR,
                    player.serverLevel().dimension(),
                    center,
                    Float.NaN);
            return;
        }
        sendPoint(
                player,
                key,
                type + " duplicated x" + count,
                SINGLETON_DUPLICATE_COLOR,
                player.serverLevel().dimension(),
                center,
                Float.NaN);
    }

    private int countObjects(ZombiesMapObjects objects, String objectType) {
        ZombiesMapObjects resolved = objects == null ? ZombiesMapObjects.EMPTY : objects;
        return switch (ZombiesDeployFieldSchema.normalizeObjectType(objectType)) {
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
    }

    private int objectColor(String type, boolean selected) {
        int color = baseColor(type);
        return selected ? mix(color, 0xFFFFFFFF, 0.45F) : color;
    }

    private int draftColor(String type) {
        return mix(baseColor(type), 0xFFFFFFFF, 0.70F);
    }

    private int baseColor(String type) {
        return switch (ZombiesDeployFieldSchema.normalizeObjectType(type)) {
            case ZombiesDeployFieldSchema.INITIAL -> INITIAL_COLOR;
            case ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> ZOMBIE_COLOR;
            case ZombiesDeployFieldSchema.BARRIER -> BARRIER_COLOR;
            case ZombiesDeployFieldSchema.POWER_SWITCH -> POWER_COLOR;
            case ZombiesDeployFieldSchema.WEAPON_WALL,
                    ZombiesDeployFieldSchema.AMMO_BOX,
                    ZombiesDeployFieldSchema.ARMOR_STATION,
                    ZombiesDeployFieldSchema.SODA_MACHINE,
                    ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> SHOP_COLOR;
            default -> PreviewColorUtil.getPointPreviewColor(BuiltInGameModes.ZOMBIES);
        };
    }

    private int mix(int source, int target, float ratio) {
        ratio = Math.max(0.0F, Math.min(1.0F, ratio));
        int sr = (source >> 16) & 0xFF;
        int sg = (source >> 8) & 0xFF;
        int sb = source & 0xFF;
        int tr = (target >> 16) & 0xFF;
        int tg = (target >> 8) & 0xFF;
        int tb = target & 0xFF;
        int red = sr + Math.round((tr - sr) * ratio);
        int green = sg + Math.round((tg - sg) * ratio);
        int blue = sb + Math.round((tb - sb) * ratio);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static String getHeldPreviewPrefix(ServerPlayer player) {
        return "held_tool_preview:zombies_deploy:" + player.getUUID() + ":";
    }

    private static String getHeldPreviewMapKey(ServerPlayer player) {
        return getHeldPreviewPrefix(player) + "map";
    }

    private static String getHeldPreviewMapKey(ServerPlayer player, int index) {
        return getHeldPreviewPrefix(player) + "map:" + index;
    }

    private static String getHeldPreviewObjectKey(ServerPlayer player, String type, int index) {
        return getHeldPreviewPrefix(player) + "object:" + type + ":" + index;
    }

    private static String getHeldPreviewSingletonKey(ServerPlayer player, String type) {
        return getHeldPreviewPrefix(player) + "singleton:" + type;
    }

    private static String getHeldPreviewNearestKey(ServerPlayer player, String type) {
        return getHeldPreviewPrefix(player) + "nearest:" + type;
    }

    private static String getHeldPreviewDraftKey(ServerPlayer player) {
        return getHeldPreviewPrefix(player) + "draft";
    }

    private static String getHeldPreviewMapDraftKey(ServerPlayer player) {
        return getHeldPreviewPrefix(player) + "map:draft";
    }

    private record NearestObjectHint(
            BlockPos pos,
            String objectLabel,
            double distanceMeters
    ) {
        private NearestObjectHint {
            objectLabel = Objects.requireNonNullElse(objectLabel, "").trim();
            distanceMeters = Math.max(0.0D, distanceMeters);
        }
    }

    private record PreviewRequest(
            String selectedMap,
            String selectedObjectType,
            String capturePreset,
            int selectedIndex,
            Map<String, String> fields,
            boolean showDraft
    ) {
        private PreviewRequest {
            selectedMap = Objects.requireNonNullElse(selectedMap, "").trim();
            selectedObjectType = ZombiesDeployFieldSchema.normalizeObjectType(selectedObjectType);
            capturePreset = ZombiesDeployDraft.normalizeCapturePreset(capturePreset, selectedObjectType);
            selectedIndex = Math.max(-1, selectedIndex);
            fields = fields == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(fields));
        }
    }

    private record DraftPreview(
            ResourceKey<Level> dimension,
            BlockPos pos,
            BlockPos interactionPos,
            BlockPos lookAtPos,
            BlockPos areaFrom,
            BlockPos areaTo,
            float yaw,
            boolean area
    ) {
        static DraftPreview point(ResourceKey<Level> dimension, BlockPos pos, BlockPos interactionPos, BlockPos lookAtPos, float yaw) {
            return new DraftPreview(dimension, pos, interactionPos, lookAtPos, null, null, yaw, false);
        }

        static DraftPreview area(ResourceKey<Level> dimension, BlockPos areaFrom, BlockPos areaTo, BlockPos interactionPos) {
            return new DraftPreview(dimension, null, interactionPos, null, areaFrom, areaTo, Float.NaN, true);
        }

        String signature() {
            if (area) {
                return "area@" + dimension.location()
                        + ":" + areaFrom.asLong()
                        + ":" + areaTo.asLong()
                        + ":" + posSignature(interactionPos);
            }
            return "point@" + dimension.location()
                    + ":" + posSignature(pos)
                    + ":" + posSignature(interactionPos)
                    + ":" + posSignature(lookAtPos)
                    + ":" + yaw;
        }

        private String posSignature(BlockPos value) {
            return value == null ? "-" : Long.toString(value.asLong());
        }
    }

    private static final class PreviewParseException extends RuntimeException {
        private final String code;

        private PreviewParseException(String code, String message) {
            super(message);
            this.code = code;
        }

        private String code() {
            return code;
        }
    }
}
