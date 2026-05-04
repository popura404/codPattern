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
        String type = ZombiesDeployFieldSchema.normalizeObjectType(draft.objectType());
        Map<String, String> fields = draft.fields().isEmpty()
                ? defaultFields(player, type)
                : mergeDefaults(type, draft.fields());
        return refreshPreview(player, new PreviewRequest(
                draft.selectedMap(),
                type,
                draft.selectedIndex(),
                fields));
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
                snapshot.selectedIndex(),
                fieldMap(snapshot)));
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

        DraftPreview draftPreview;
        try {
            draftPreview = parseDraftPreview(request.selectedObjectType(), request.fields(), player.serverLevel().dimension());
        } catch (PreviewParseException e) {
            clearHeldPreview(player);
            return ZombiesDeployServiceResult.failure(
                    e.code(),
                    "message.codpattern.zombies.deploy.preview_field_invalid",
                    null,
                    e.getMessage());
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
        sendDraft(player, request.selectedObjectType(), draftPreview);

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
            return DraftPreview.area(
                    dimension,
                    blockPos(fields, "areaFrom"),
                    blockPos(fields, "areaTo"));
        }
        float yaw = switch (type) {
            case ZombiesDeployFieldSchema.INITIAL, ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> floatField(fields, "yaw");
            default -> Float.NaN;
        };
        return DraftPreview.point(dimension, blockPos(fields, "pos"), yaw);
    }

    private void sendCurrentObjectList(
            ServerPlayer player,
            PreviewRequest request,
            ZombiesMapObjects objects
    ) {
        ZombiesMapObjects resolved = objects == null ? ZombiesMapObjects.EMPTY : objects;
        String type = request.selectedObjectType();
        int selectedIndex = request.selectedIndex();
        switch (type) {
            case ZombiesDeployFieldSchema.INITIAL -> {
                for (int i = 0; i < resolved.initialSpawns().size(); i++) {
                    ZombiesInitialSpawnData data = resolved.initialSpawns().get(i);
                    sendPoint(
                            player,
                            getHeldPreviewObjectKey(player, type, i),
                            "INITIAL #" + (i + 1),
                            objectColor(type, selectedIndex == i),
                            data.dimension(),
                            data.pos(),
                            data.yaw());
                }
            }
            case ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> {
                for (int i = 0; i < resolved.zombieSpawns().size(); i++) {
                    ZombiesZombieSpawnData data = resolved.zombieSpawns().get(i);
                    sendPoint(
                            player,
                            getHeldPreviewObjectKey(player, type, i),
                            label(type, data.objectId(), i),
                            objectColor(type, selectedIndex == i),
                            data.dimension(),
                            data.pos(),
                            data.yaw());
                }
            }
            case ZombiesDeployFieldSchema.BARRIER -> {
                for (int i = 0; i < resolved.barriers().size(); i++) {
                    ZombiesBarrierData data = resolved.barriers().get(i);
                    sendArea(
                            player,
                            getHeldPreviewObjectKey(player, type, i),
                            label(type, data.objectId(), i),
                            objectColor(type, selectedIndex == i),
                            data.dimension(),
                            data.areaFrom(),
                            data.areaTo());
                }
            }
            case ZombiesDeployFieldSchema.WEAPON_WALL -> {
                for (int i = 0; i < resolved.weaponWalls().size(); i++) {
                    ZombiesWeaponWallData data = resolved.weaponWalls().get(i);
                    sendPoint(
                            player,
                            getHeldPreviewObjectKey(player, type, i),
                            label(type, data.objectId(), i),
                            objectColor(type, selectedIndex == i),
                            data.dimension(),
                            data.pos(),
                            Float.NaN);
                }
            }
            case ZombiesDeployFieldSchema.AMMO_BOX -> {
                for (int i = 0; i < resolved.ammoBoxes().size(); i++) {
                    ZombiesAmmoBoxData data = resolved.ammoBoxes().get(i);
                    sendPoint(
                            player,
                            getHeldPreviewObjectKey(player, type, i),
                            label(type, data.objectId(), i),
                            objectColor(type, selectedIndex == i),
                            data.dimension(),
                            data.pos(),
                            Float.NaN);
                }
            }
            case ZombiesDeployFieldSchema.ARMOR_STATION -> {
                for (int i = 0; i < resolved.armorStations().size(); i++) {
                    ZombiesArmorStationData data = resolved.armorStations().get(i);
                    sendPoint(
                            player,
                            getHeldPreviewObjectKey(player, type, i),
                            label(type, data.objectId(), i),
                            objectColor(type, selectedIndex == i),
                            data.dimension(),
                            data.pos(),
                            Float.NaN);
                }
            }
            case ZombiesDeployFieldSchema.POWER_SWITCH -> resolved.powerSwitch().ifPresent(data -> sendPoint(
                    player,
                    getHeldPreviewObjectKey(player, type, 0),
                    label(type, data.objectId(), 0),
                    objectColor(type, selectedIndex == 0),
                    data.dimension(),
                    data.pos(),
                    Float.NaN));
            case ZombiesDeployFieldSchema.SODA_MACHINE -> {
                for (int i = 0; i < resolved.sodaMachines().size(); i++) {
                    ZombiesSodaMachineData data = resolved.sodaMachines().get(i);
                    sendPoint(
                            player,
                            getHeldPreviewObjectKey(player, type, i),
                            label(type, data.objectId(), i),
                            objectColor(type, selectedIndex == i),
                            data.dimension(),
                            data.pos(),
                            Float.NaN);
                }
            }
            case ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> {
                for (int i = 0; i < resolved.ultimateMachines().size(); i++) {
                    ZombiesUltimateMachineData data = resolved.ultimateMachines().get(i);
                    sendPoint(
                            player,
                            getHeldPreviewObjectKey(player, type, i),
                            label(type, data.objectId(), i),
                            objectColor(type, selectedIndex == i),
                            data.dimension(),
                            data.pos(),
                            Float.NaN);
                }
            }
            default -> {
            }
        }
    }

    private void sendDraft(ServerPlayer player, String objectType, DraftPreview draftPreview) {
        String type = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        if (draftPreview.area()) {
            sendArea(
                    player,
                    getHeldPreviewDraftKey(player),
                    type + " draft",
                    draftColor(type),
                    draftPreview.dimension(),
                    draftPreview.areaFrom(),
                    draftPreview.areaTo());
            return;
        }
        sendPoint(
                player,
                getHeldPreviewDraftKey(player),
                type + " draft",
                draftColor(type),
                draftPreview.dimension(),
                draftPreview.pos(),
                draftPreview.yaw());
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
                .append('|').append(draftPreview.signature());
        request.fields().forEach((key, value) -> builder.append('|').append(key).append('=').append(value));
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

    private static String getHeldPreviewObjectKey(ServerPlayer player, String type, int index) {
        return getHeldPreviewPrefix(player) + "object:" + type + ":" + index;
    }

    private static String getHeldPreviewDraftKey(ServerPlayer player) {
        return getHeldPreviewPrefix(player) + "draft";
    }

    private record PreviewRequest(
            String selectedMap,
            String selectedObjectType,
            int selectedIndex,
            Map<String, String> fields
    ) {
        private PreviewRequest {
            selectedMap = Objects.requireNonNullElse(selectedMap, "").trim();
            selectedObjectType = ZombiesDeployFieldSchema.normalizeObjectType(selectedObjectType);
            selectedIndex = Math.max(-1, selectedIndex);
            fields = fields == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(fields));
        }
    }

    private record DraftPreview(
            ResourceKey<Level> dimension,
            BlockPos pos,
            BlockPos areaFrom,
            BlockPos areaTo,
            float yaw,
            boolean area
    ) {
        static DraftPreview point(ResourceKey<Level> dimension, BlockPos pos, float yaw) {
            return new DraftPreview(dimension, pos, null, null, yaw, false);
        }

        static DraftPreview area(ResourceKey<Level> dimension, BlockPos areaFrom, BlockPos areaTo) {
            return new DraftPreview(dimension, null, areaFrom, areaTo, Float.NaN, true);
        }

        String signature() {
            if (area) {
                return "area@" + dimension.location() + ":" + areaFrom.asLong() + ":" + areaTo.asLong();
            }
            return "point@" + dimension.location() + ":" + pos.asLong() + ":" + yaw;
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
