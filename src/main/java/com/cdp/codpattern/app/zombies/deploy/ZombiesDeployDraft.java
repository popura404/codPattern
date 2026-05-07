package com.cdp.codpattern.app.zombies.deploy;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ZombiesDeployDraft(
        String workspaceStage,
        String workflowStep,
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
    public static final String WORKFLOW_MAP = "map";
    public static final String WORKFLOW_INITIAL = "initial";
    public static final String WORKFLOW_ZOMBIE_SPAWN = "zombie_spawn";
    public static final String WORKFLOW_BARRIER = "barrier";
    public static final String WORKFLOW_INTERACT = "interact";
    public static final String WORKFLOW_VALIDATE = "validate";
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
        this(
                STAGE_OBJECT_MARKING,
                workflowStepForObjectType(objectType),
                selectedMap,
                "",
                null,
                null,
                objectType,
                CAPTURE_DEFAULT,
                selectedIndex,
                profileKey,
                fields);
    }

    public ZombiesDeployDraft {
        workspaceStage = normalizeStage(workspaceStage);
        workflowStep = normalizeWorkflowStep(workflowStep);
        selectedMap = Objects.requireNonNullElse(selectedMap, "").trim();
        draftMapName = Objects.requireNonNullElse(draftMapName, "").trim();
        objectType = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        capturePreset = normalizeCapturePreset(capturePreset, objectType);
        selectedIndex = Math.max(-1, selectedIndex);
        validationView = ZombiesDeployFieldSchema.normalizeProfile(validationView);
        if (STAGE_MAP_REGISTRATION.equals(workspaceStage)) {
            workflowStep = WORKFLOW_MAP;
        } else if (WORKFLOW_MAP.equals(workflowStep)) {
            workflowStep = workflowStepForObjectType(objectType);
        }
        fields = fields == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(fields));
    }

    public static ZombiesDeployDraft empty() {
        return new ZombiesDeployDraft(
                STAGE_MAP_REGISTRATION,
                WORKFLOW_MAP,
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
        return new ZombiesDeployDraft(workspaceStage, workflowStep, selectedMap, draftMapName, mapPos1, mapPos2, objectType, capturePreset, selectedIndex, validationView, newFields);
    }

    public ZombiesDeployDraft withSelection(String mapName, String type, int index, String profile) {
        String step = STAGE_MAP_REGISTRATION.equals(workspaceStage)
                ? WORKFLOW_MAP
                : workflowStepForObjectType(type);
        return new ZombiesDeployDraft(workspaceStage, step, mapName, draftMapName, mapPos1, mapPos2, type, capturePreset, index, profile, fields);
    }

    public String profileKey() {
        return validationView;
    }

    public ZombiesDeployDraft withWorkspaceStage(String stage) {
        return new ZombiesDeployDraft(stage, workflowStep, selectedMap, draftMapName, mapPos1, mapPos2, objectType, capturePreset, selectedIndex, validationView, fields);
    }

    public ZombiesDeployDraft withWorkflowStep(String step) {
        return new ZombiesDeployDraft(workspaceStage, step, selectedMap, draftMapName, mapPos1, mapPos2, objectType, capturePreset, selectedIndex, validationView, fields);
    }

    public ZombiesDeployDraft withMapDraft(String mapName, BlockPos pos1, BlockPos pos2) {
        return new ZombiesDeployDraft(workspaceStage, workflowStep, selectedMap, mapName, pos1, pos2, objectType, capturePreset, selectedIndex, validationView, fields);
    }

    public ZombiesDeployDraft withSelectedMap(String mapName) {
        return new ZombiesDeployDraft(workspaceStage, workflowStep, mapName, draftMapName, mapPos1, mapPos2, objectType, capturePreset, selectedIndex, validationView, fields);
    }

    public ZombiesDeployDraft withCapturePreset(String preset) {
        return new ZombiesDeployDraft(workspaceStage, workflowStep, selectedMap, draftMapName, mapPos1, mapPos2, objectType, preset, selectedIndex, validationView, fields);
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

    public static String normalizeWorkflowStep(String step) {
        String value = Objects.requireNonNullElse(step, "").trim();
        return switch (value) {
            case WORKFLOW_INITIAL -> WORKFLOW_INITIAL;
            case WORKFLOW_ZOMBIE_SPAWN -> WORKFLOW_ZOMBIE_SPAWN;
            case WORKFLOW_BARRIER -> WORKFLOW_BARRIER;
            case WORKFLOW_INTERACT -> WORKFLOW_INTERACT;
            case WORKFLOW_VALIDATE -> WORKFLOW_VALIDATE;
            default -> WORKFLOW_MAP;
        };
    }

    public static String workflowStepForObjectType(String objectType) {
        return switch (ZombiesDeployFieldSchema.normalizeObjectType(objectType)) {
            case ZombiesDeployFieldSchema.INITIAL -> WORKFLOW_INITIAL;
            case ZombiesDeployFieldSchema.ZOMBIE_SPAWN -> WORKFLOW_ZOMBIE_SPAWN;
            case ZombiesDeployFieldSchema.BARRIER -> WORKFLOW_BARRIER;
            case ZombiesDeployFieldSchema.POWER_SWITCH,
                 ZombiesDeployFieldSchema.ULTIMATE_MACHINE -> WORKFLOW_INTERACT;
            default -> WORKFLOW_VALIDATE;
        };
    }
}
