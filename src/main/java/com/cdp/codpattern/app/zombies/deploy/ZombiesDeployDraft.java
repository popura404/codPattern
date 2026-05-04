package com.cdp.codpattern.app.zombies.deploy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ZombiesDeployDraft(
        String selectedMap,
        String objectType,
        int selectedIndex,
        String profileKey,
        Map<String, String> fields
) {
    public ZombiesDeployDraft {
        selectedMap = Objects.requireNonNullElse(selectedMap, "").trim();
        objectType = ZombiesDeployFieldSchema.normalizeObjectType(objectType);
        selectedIndex = Math.max(-1, selectedIndex);
        profileKey = ZombiesDeployFieldSchema.normalizeProfile(profileKey);
        fields = fields == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(fields));
    }

    public static ZombiesDeployDraft empty() {
        return new ZombiesDeployDraft(
                "",
                ZombiesDeployFieldSchema.INITIAL,
                -1,
                ZombiesDeployFieldSchema.PROFILE_MVP1,
                Map.of());
    }

    public ZombiesDeployDraft withFields(Map<String, String> newFields) {
        return new ZombiesDeployDraft(selectedMap, objectType, selectedIndex, profileKey, newFields);
    }

    public ZombiesDeployDraft withSelection(String mapName, String type, int index, String profile) {
        return new ZombiesDeployDraft(mapName, type, index, profile, fields);
    }
}
