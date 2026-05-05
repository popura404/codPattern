package com.cdp.codpattern.app.zombies.deploy;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ZombiesDeployDraft(
        String workspaceStage,
        String selectedMap,
        String draftMapName,
        BlockPos mapPos1,
        BlockPos mapPos2,
        String objectType,
        String capturePreset,
        int selectedIndex,
        String validationView,
        Map<String, String> fields
) {
    public static final String STAGE_MAP_REGISTRATION = "map_registration";
    public static final String STAGE_OBJECT_MARKING = "object_marking";
    public static final String CAPTURE_DEFAULT = "default";
    public static final String CAPTURE_BARRIER_AREA = "barrier_area";
    public static final String CAPTURE_BARRIER_INTERACTION = "barrier_interaction";

    public ZombiesDeployDraft(
            String selectedMap,
            String objectType,
            int selectedIndex,
            String profileKey,
            Map<String, String> fields
    ) {
        this(STAGE_OBJECT_MARKING, selectedMap, "", null, null, objectType, CAPTURE_DEFAULT, selectedIndex, profileKey, fields);
    }

    public ZombiesDeployDraft {
        workspaceStage = normalizeStage(workspaceStage);
        selectedMap = Objects.requireNonNullElse(selectedMap, "").trim();
        draftMapName = Objects.requireNonNullElse(draftMapName, "").trim();
        objectType = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        capturePreset = normalizeCapturePreset(capturePreset, objectType);
        selectedIndex = Math.max(-1, selectedIndex);
        validationView = ZombiesDeployFieldSchema.normalizeProfile(validationView);
        fields = fields == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(fields));
    }

    public static ZombiesDeployDraft empty() {
        return new ZombiesDeployDraft(
                STAGE_MAP_REGISTRATION,
                "",
                "",
                null,
                null,
                ZombiesDeployFieldSchema.INITIAL,
                CAPTURE_DEFAULT,
                -1,
                ZombiesDeployFieldSchema.PROFILE_MVP1,
                Map.of());
    }

    public ZombiesDeployDraft withFields(Map<String, String> newFields) {
        return new ZombiesDeployDraft(workspaceStage, selectedMap, draftMapName, mapPos1, mapPos2, objectType, capturePreset, selectedIndex, validationView, newFields);
    }

    public ZombiesDeployDraft withSelection(String mapName, String type, int index, String profile) {
        return new ZombiesDeployDraft(workspaceStage, mapName, draftMapName, mapPos1, mapPos2, type, capturePreset, index, profile, fields);
    }

    public String profileKey() {
        return validationView;
    }

    public ZombiesDeployDraft withWorkspaceStage(String stage) {
        return new ZombiesDeployDraft(stage, selectedMap, draftMapName, mapPos1, mapPos2, objectType, capturePreset, selectedIndex, validationView, fields);
    }

    public ZombiesDeployDraft withMapDraft(String mapName, BlockPos pos1, BlockPos pos2) {
        return new ZombiesDeployDraft(workspaceStage, selectedMap, mapName, pos1, pos2, objectType, capturePreset, selectedIndex, validationView, fields);
    }

    public ZombiesDeployDraft withSelectedMap(String mapName) {
        return new ZombiesDeployDraft(workspaceStage, mapName, draftMapName, mapPos1, mapPos2, objectType, capturePreset, selectedIndex, validationView, fields);
    }

    public ZombiesDeployDraft withCapturePreset(String preset) {
        return new ZombiesDeployDraft(workspaceStage, selectedMap, draftMapName, mapPos1, mapPos2, objectType, preset, selectedIndex, validationView, fields);
    }

    public static String normalizeStage(String stage) {
        String value = Objects.requireNonNullElse(stage, "").trim();
        return STAGE_OBJECT_MARKING.equals(value) ? STAGE_OBJECT_MARKING : STAGE_MAP_REGISTRATION;
    }

    public static String normalizeCapturePreset(String preset, String objectType) {
        String value = Objects.requireNonNullElse(preset, "").trim();
        String type = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        if (ZombiesDeployFieldSchema.BARRIER.equals(type)) {
            if (CAPTURE_BARRIER_INTERACTION.equals(value)) {
                return CAPTURE_BARRIER_INTERACTION;
            }
            return CAPTURE_BARRIER_AREA;
        }
        return CAPTURE_DEFAULT;
    }
}
