package com.cdp.codpattern.app.zombies.deploy;

import java.util.List;
import java.util.Objects;

public record ZombiesDeploySnapshot(
        List<String> availableMaps,
        String selectedMap,
        List<ObjectTypeOption> objectTypes,
        String selectedObjectType,
        int selectedIndex,
        List<ObjectSummary> objects,
        List<FieldValue> fields,
        String profileKey,
        List<String> availableProfiles,
        List<ValidationLine> validationLines,
        boolean activeMap,
        int revision,
        String statusKey,
        String statusCode,
        String statusDetail
) {
    public ZombiesDeploySnapshot {
        availableMaps = availableMaps == null ? List.of() : List.copyOf(availableMaps);
        selectedMap = Objects.requireNonNullElse(selectedMap, "").trim();
        objectTypes = objectTypes == null ? List.of() : List.copyOf(objectTypes);
        selectedObjectType = ZombiesDeployFieldSchema.normalizeObjectType(selectedObjectType);
        selectedIndex = Math.max(-1, selectedIndex);
        objects = objects == null ? List.of() : List.copyOf(objects);
        fields = fields == null ? List.of() : List.copyOf(fields);
        profileKey = ZombiesDeployFieldSchema.normalizeProfile(profileKey);
        availableProfiles = availableProfiles == null ? List.of() : List.copyOf(availableProfiles);
        validationLines = validationLines == null ? List.of() : List.copyOf(validationLines);
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
}
