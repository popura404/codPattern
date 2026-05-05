package com.cdp.codpattern.app.zombies.deploy;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Objects;

public record ZombiesDeploySnapshot(
        List<String> availableMaps,
        String workspaceStage,
        String selectedMap,
        String draftMapName,
        BlockPos mapPos1,
        BlockPos mapPos2,
        List<ObjectTypeOption> objectTypes,
        String selectedObjectType,
        String capturePreset,
        String captureSlotA,
        String captureSlotB,
        int selectedIndex,
        List<ObjectSummary> objects,
        List<FieldValue> fields,
        String profileKey,
        List<String> availableProfiles,
        List<ValidationLine> validationLines,
        List<ValidationSummary> validationSummaries,
        List<ObjectTypeCount> objectCounts,
        List<StepStatus> stepStatuses,
        boolean activeMap,
        int revision,
        String statusKey,
        String statusCode,
        String statusDetail
) {
    public ZombiesDeploySnapshot {
        availableMaps = availableMaps == null ? List.of() : List.copyOf(availableMaps);
        workspaceStage = ZombiesDeployDraft.normalizeStage(workspaceStage);
        selectedMap = Objects.requireNonNullElse(selectedMap, "").trim();
        draftMapName = Objects.requireNonNullElse(draftMapName, "").trim();
        objectTypes = objectTypes == null ? List.of() : List.copyOf(objectTypes);
        selectedObjectType = ZombiesDeployFieldSchema.normalizeObjectType(selectedObjectType);
        capturePreset = ZombiesDeployDraft.normalizeCapturePreset(capturePreset, selectedObjectType);
        captureSlotA = Objects.requireNonNullElse(captureSlotA, "").trim();
        captureSlotB = Objects.requireNonNullElse(captureSlotB, "").trim();
        selectedIndex = Math.max(-1, selectedIndex);
        objects = objects == null ? List.of() : List.copyOf(objects);
        fields = fields == null ? List.of() : List.copyOf(fields);
        profileKey = ZombiesDeployFieldSchema.normalizeProfile(profileKey);
        availableProfiles = availableProfiles == null ? List.of() : List.copyOf(availableProfiles);
        validationLines = validationLines == null ? List.of() : List.copyOf(validationLines);
        validationSummaries = validationSummaries == null ? List.of() : List.copyOf(validationSummaries);
        objectCounts = objectCounts == null ? List.of() : List.copyOf(objectCounts);
        stepStatuses = stepStatuses == null ? List.of() : List.copyOf(stepStatuses);
        revision = Math.max(0, revision);
        statusKey = Objects.requireNonNullElse(statusKey, "").trim();
        statusCode = Objects.requireNonNullElse(statusCode, "").trim();
        statusDetail = Objects.requireNonNullElse(statusDetail, "").trim();
    }

    public record ObjectTypeOption(String key, String labelKey) {
        public ObjectTypeOption {
            key = ZombiesDeployFieldSchema.normalizeObjectType(key);
            labelKey = Objects.requireNonNullElse(labelKey, "").trim();
        }
    }

    public record ObjectSummary(
            int index,
            String objectType,
            String objectId,
            String primary,
            String detail
    ) {
        public ObjectSummary {
            index = Math.max(0, index);
            objectType = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
            objectId = Objects.requireNonNullElse(objectId, "").trim();
            primary = Objects.requireNonNullElse(primary, "").trim();
            detail = Objects.requireNonNullElse(detail, "").trim();
        }
    }

    public record FieldValue(
            String key,
            String labelKey,
            ZombiesDeployFieldSchema.FieldType type,
            String value,
            boolean editable
    ) {
        public FieldValue {
            key = Objects.requireNonNullElse(key, "").trim();
            labelKey = Objects.requireNonNullElse(labelKey, "").trim();
            type = type == null ? ZombiesDeployFieldSchema.FieldType.TEXT : type;
            value = Objects.requireNonNullElse(value, "");
        }
    }

    public record ValidationLine(
            String severity,
            String code,
            String subject,
            String message
    ) {
        public ValidationLine {
            severity = Objects.requireNonNullElse(severity, "info").trim();
            code = Objects.requireNonNullElse(code, "").trim();
            subject = Objects.requireNonNullElse(subject, "").trim();
            message = Objects.requireNonNullElse(message, "").trim();
        }
    }

    public record ObjectTypeCount(String objectType, int count, boolean singleton, boolean required) {
        public ObjectTypeCount {
            objectType = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
            count = Math.max(0, count);
        }
    }

    public record StepStatus(String key, String label, String detail, boolean complete) {
        public StepStatus {
            key = Objects.requireNonNullElse(key, "").trim();
            label = Objects.requireNonNullElse(label, "").trim();
            detail = Objects.requireNonNullElse(detail, "").trim();
        }
    }

    public record ValidationSummary(String profileKey, int errors, int warnings) {
        public ValidationSummary {
            profileKey = ZombiesDeployFieldSchema.normalizeProfile(profileKey);
            errors = Math.max(0, errors);
            warnings = Math.max(0, warnings);
        }
    }
}
