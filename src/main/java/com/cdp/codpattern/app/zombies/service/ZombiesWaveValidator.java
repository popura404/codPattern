package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.zombies.model.ZombiesWaveDefinition;
import com.cdp.codpattern.app.zombies.model.ZombiesWaveMobEntry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

public final class ZombiesWaveValidator {
    public static final String NO_VALID_WAVE = "rules.no_valid_wave";
    public static final String WAVE_CONFLICT = "rules.wave_conflict";
    public static final String MISSING_MOBS = "rules.missing_mobs";
    public static final String INVALID_ENTITY = "rules.invalid_entity";
    public static final String INVALID_WAVE = "rules.invalid_wave";
    public static final String INVALID_MULTIPLIER = "rules.invalid_multiplier";
    public static final String INVALID_MAX_ALIVE = "rules.invalid_max_alive";
    public static final String INVALID_SPAWN_INTERVAL = "rules.invalid_spawn_interval";
    public static final String INVALID_MOB_COUNT = "rules.invalid_mob_count";
    public static final String DUPLICATE_MOB_ENTITY = "rules.duplicate_mob_entity";
    public static final String INVALID_REWARD_POINTS = "rules.invalid_reward_points";

    static final String VANILLA_ZOMBIE_ID = "minecraft:zombie";
    static final String VANILLA_HUSK_ID = "minecraft:husk";
    static final String VANILLA_WITHER_SKELETON_ID = "minecraft:wither_skeleton";
    static final String VANILLA_CREEPER_ID = "minecraft:creeper";
    static final String VANILLA_WOLF_ID = "minecraft:wolf";
    static final String VANILLA_SILVERFISH_ID = "minecraft:silverfish";
    static final String VANILLA_VINDICATOR_ID = "minecraft:vindicator";
    static final String VANILLA_VEX_ID = "minecraft:vex";

    private static final String DEFAULT_NAMESPACE = "minecraft";
    private static final List<String> SUPPORTED_ENTITY_IDS = List.of(
            VANILLA_ZOMBIE_ID,
            VANILLA_HUSK_ID,
            VANILLA_WITHER_SKELETON_ID,
            VANILLA_CREEPER_ID,
            VANILLA_WOLF_ID,
            VANILLA_SILVERFISH_ID,
            VANILLA_VINDICATOR_ID,
            VANILLA_VEX_ID);

    public ValidationReport validate(List<ZombiesWaveDefinition> waves) {
        List<ValidationIssue> issues = new ArrayList<>();
        Map<Integer, Path> firstSourceByWave = new HashMap<>();
        int validWaves = 0;

        for (ZombiesWaveDefinition wave : waves == null ? Collections.<ZombiesWaveDefinition>emptyList() : waves) {
            boolean valid = true;
            if (wave.getWave() < 1) {
                issues.add(new ValidationIssue(
                        INVALID_WAVE,
                        wave.getSourcePath(),
                        "Wave number must be 1 or greater"));
                valid = false;
            }
            if (wave.hasWaveConflict()) {
                issues.add(new ValidationIssue(
                        WAVE_CONFLICT,
                        wave.getSourcePath(),
                        "Wave file number " + wave.getFileWave() + " conflicts with wave field " + wave.getConfiguredWave()));
                valid = false;
            }
            if (wave.getWave() >= 1 && !wave.hasWaveConflict()) {
                Path firstSource = firstSourceByWave.putIfAbsent(wave.getWave(), wave.getSourcePath());
                if (firstSource != null) {
                    issues.add(new ValidationIssue(
                            WAVE_CONFLICT,
                            wave.getSourcePath(),
                            "Wave " + wave.getWave() + " is defined more than once; first definition was "
                                    + firstSource));
                    valid = false;
                }
            }
            if (!wave.hasMobsField()) {
                issues.add(new ValidationIssue(
                        MISSING_MOBS,
                        wave.getSourcePath(),
                        "Wave file is missing required mobs field"));
                valid = false;
            }
            if (!isPositiveFiniteIfPresent(wave.getConfiguredHealthMultiplier())) {
                issues.add(new ValidationIssue(
                        INVALID_MULTIPLIER,
                        wave.getSourcePath(),
                        "Wave " + wave.getWave() + " has an invalid health multiplier"));
                valid = false;
            }
            if (!isPositiveFiniteIfPresent(wave.getConfiguredDamageMultiplier())) {
                issues.add(new ValidationIssue(
                        INVALID_MULTIPLIER,
                        wave.getSourcePath(),
                        "Wave " + wave.getWave() + " has an invalid damage multiplier"));
                valid = false;
            }
            if (!isPositiveFiniteIfPresent(wave.getConfiguredSpeedMultiplier())) {
                issues.add(new ValidationIssue(
                        INVALID_MULTIPLIER,
                        wave.getSourcePath(),
                        "Wave " + wave.getWave() + " has an invalid speed multiplier"));
                valid = false;
            }
            if (!isPositiveIfPresent(wave.getConfiguredMaxAlive())) {
                issues.add(new ValidationIssue(
                        INVALID_MAX_ALIVE,
                        wave.getSourcePath(),
                        "Wave " + wave.getWave() + " maxAlive must be positive when configured"));
                valid = false;
            }
            if (!isPositiveIfPresent(wave.getConfiguredSpawnIntervalTicks())
                    || !isPositiveIfPresent(wave.getConfiguredFastestSpawnIntervalTicks())
                    || !isPositiveIfPresent(wave.getConfiguredSlowestSpawnIntervalTicks())) {
                issues.add(new ValidationIssue(
                        INVALID_SPAWN_INTERVAL,
                        wave.getSourcePath(),
                        "Wave " + wave.getWave() + " spawn interval ticks must be positive when configured"));
                valid = false;
            }
            if (wave.getConfiguredFastestSpawnIntervalTicks() != null
                    && wave.getConfiguredSlowestSpawnIntervalTicks() != null
                    && wave.getConfiguredFastestSpawnIntervalTicks() > wave.getConfiguredSlowestSpawnIntervalTicks()) {
                issues.add(new ValidationIssue(
                        INVALID_SPAWN_INTERVAL,
                        wave.getSourcePath(),
                        "Wave " + wave.getWave() + " fastestSpawnIntervalTicks must be <= slowestSpawnIntervalTicks"));
                valid = false;
            }
            List<String> seenMobEntityIds = new ArrayList<>();
            for (ZombiesWaveMobEntry mob : wave.getMobs()) {
                if (mob == null) {
                    continue;
                }
                if (mob.getCount() < 0) {
                    issues.add(new ValidationIssue(
                            INVALID_MOB_COUNT,
                            wave.getSourcePath(),
                            "Wave " + wave.getWave() + " has a mob with negative count"));
                    valid = false;
                }
                if (!isPositiveFiniteIfPresent(mob.getConfiguredHealthMultiplier())) {
                    issues.add(new ValidationIssue(
                            INVALID_MULTIPLIER,
                            wave.getSourcePath(),
                            "Wave " + wave.getWave() + " has a mob with invalid healthMultiplier"));
                    valid = false;
                }
                if (!isPositiveFiniteIfPresent(mob.getConfiguredDamageMultiplier())) {
                    issues.add(new ValidationIssue(
                            INVALID_MULTIPLIER,
                            wave.getSourcePath(),
                            "Wave " + wave.getWave() + " has a mob with invalid damageMultiplier"));
                    valid = false;
                }
                if (!isPositiveFiniteIfPresent(mob.getConfiguredSpeedMultiplier())) {
                    issues.add(new ValidationIssue(
                            INVALID_MULTIPLIER,
                            wave.getSourcePath(),
                            "Wave " + wave.getWave() + " has a mob with invalid speedMultiplier"));
                    valid = false;
                }
                if (mob.getConfiguredKillPoints() != null && mob.getConfiguredKillPoints() < 0) {
                    issues.add(new ValidationIssue(
                            INVALID_REWARD_POINTS,
                            wave.getSourcePath(),
                            "Wave " + wave.getWave() + " has a mob with negative killPoints"));
                    valid = false;
                }
                if (mob.getConfiguredAssistPoints() != null && mob.getConfiguredAssistPoints() < 0) {
                    issues.add(new ValidationIssue(
                            INVALID_REWARD_POINTS,
                            wave.getSourcePath(),
                            "Wave " + wave.getWave() + " has a mob with negative assistPoints"));
                    valid = false;
                }
                if (mob.getCount() > 0) {
                    EntityValidationIssue entityIssue = validateEntity(wave, mob);
                    if (entityIssue != null) {
                        issues.add(entityIssue.toValidationIssue(wave.getSourcePath()));
                        valid = false;
                    } else {
                        String normalizedEntityId = normalizedEntityId(mob.getEntity()).orElse("");
                        if (seenMobEntityIds.contains(normalizedEntityId)) {
                            issues.add(new ValidationIssue(
                                    DUPLICATE_MOB_ENTITY,
                                    wave.getSourcePath(),
                                    "Wave " + wave.getWave() + " defines entity '" + normalizedEntityId
                                            + "' more than once; use one mobs[] entry per entity"));
                            valid = false;
                        } else {
                            seenMobEntityIds.add(normalizedEntityId);
                        }
                    }
                }
            }
            if (valid) {
                validWaves++;
            }
        }

        if (validWaves == 0) {
            issues.add(new ValidationIssue(NO_VALID_WAVE, null, "No valid zombies wave files were found"));
        }

        return new ValidationReport(issues);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static EntityValidationIssue validateEntity(ZombiesWaveDefinition wave, ZombiesWaveMobEntry mob) {
        String entity = mob.getEntity();
        if (isBlank(entity)) {
            return new EntityValidationIssue(
                    "Wave " + wave.getWave() + " has a positive-count mob without an entity id");
        }
        if (!isParsableEntityId(entity)) {
            return new EntityValidationIssue(
                    "Wave " + wave.getWave() + " has invalid entity id '" + entity.trim() + "'");
        }
        if (!isSupportedEntityId(entity)) {
            return new EntityValidationIssue(
                    "Wave " + wave.getWave() + " uses unsupported entity id '" + entity.trim()
                            + "'. Supported entity ids: " + supportedEntityIdsDescription());
        }
        return null;
    }

    private static String supportedEntityIdsDescription() {
        StringJoiner joiner = new StringJoiner(", ");
        for (String entityId : supportedEntityIds()) {
            joiner.add(entityId);
        }
        return joiner.toString();
    }

    static boolean isParsableEntityId(String rawEntityId) {
        return normalizedEntityId(rawEntityId).isPresent();
    }

    static boolean isSupportedEntityId(String rawEntityId) {
        return normalizedEntityId(rawEntityId)
                .map(SUPPORTED_ENTITY_IDS::contains)
                .orElse(false);
    }

    static List<String> supportedEntityIds() {
        return Collections.unmodifiableList(SUPPORTED_ENTITY_IDS);
    }

    static Optional<String> normalizedEntityId(String rawEntityId) {
        if (rawEntityId == null || rawEntityId.trim().isEmpty()) {
            return Optional.empty();
        }
        String trimmed = rawEntityId.trim();
        int separator = trimmed.indexOf(':');
        String namespace = separator >= 0 ? trimmed.substring(0, separator) : DEFAULT_NAMESPACE;
        String path = separator >= 0 ? trimmed.substring(separator + 1) : trimmed;
        if (namespace.isEmpty() || path.isEmpty() || !isValidNamespace(namespace) || !isValidPath(path)) {
            return Optional.empty();
        }
        return Optional.of(namespace + ":" + path);
    }

    private static boolean isValidNamespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '_' && c != '-' && c != '.') {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidPath(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '_' && c != '-' && c != '.' && c != '/') {
                return false;
            }
        }
        return true;
    }

    private static boolean isPositiveIfPresent(Integer value) {
        return value == null || value > 0;
    }

    private static boolean isPositiveFiniteIfPresent(Double value) {
        return value == null || Double.isFinite(value) && value > 0.0;
    }

    private record EntityValidationIssue(String message) {
        private ValidationIssue toValidationIssue(Path path) {
            return new ValidationIssue(INVALID_ENTITY, path, message);
        }
    }

    public static final class ValidationReport {
        private final List<ValidationIssue> issues;

        private ValidationReport(List<ValidationIssue> issues) {
            this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
        }

        public boolean isValid() {
            return issues.isEmpty();
        }

        public List<ValidationIssue> getIssues() {
            return issues;
        }

        public boolean hasIssue(String code) {
            for (ValidationIssue issue : issues) {
                if (issue.getCode().equals(code)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class ValidationIssue {
        private final String code;
        private final Path path;
        private final String message;

        public ValidationIssue(String code, Path path, String message) {
            this.code = code;
            this.path = path;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public Path getPath() {
            return path;
        }

        public String getMessage() {
            return message;
        }
    }
}
