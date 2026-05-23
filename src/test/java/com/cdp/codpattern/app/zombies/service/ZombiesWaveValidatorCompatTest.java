package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.zombies.model.ZombiesWaveDefinition;
import com.google.gson.Gson;

import java.nio.file.Path;
import java.util.List;

public final class ZombiesWaveValidatorCompatTest {
    private static final Gson GSON = new Gson();
    private static final ZombiesWaveValidator VALIDATOR = new ZombiesWaveValidator();

    private ZombiesWaveValidatorCompatTest() {
    }

    public static void main(String[] args) {
        descriptionFieldsDoNotAffectValidation();
        missingMobsRemainsInvalid();
        explicitEmptyWaveIsValid();
        fastestSlowestSpawnIntervalRangeIsValid();
        reversedSpawnIntervalRangeIsInvalid();
        filenameWaveConflictIsInvalid();
        duplicateWaveNumberIsInvalid();
        supportedDefaultEntitiesAreValid();
        mobAttributeMultipliersDefaultToOneAndValidateWhenPositive();
        invalidMobAttributeMultiplierIsRejected();
        duplicatePositiveCountMobEntityIsRejected();
        invalidEntityIdIsRejected();
        unsupportedEntityIdIsRejected();
    }

    private static void descriptionFieldsDoNotAffectValidation() {
        ZombiesWaveDefinition wave = readWave(
                "wave_001.json",
                1,
                "{"
                        + "\"wave\":1,"
                        + "\"description\":\"example-only wave note\","
                        + "\"mobs\":[{"
                        + "\"entity\":\"minecraft:zombie\","
                        + "\"description\":{\"text\":\"example-only mob note\"},"
                        + "\"count\":2"
                        + "}]"
                        + "}");

        require("example-only wave note".equals(wave.getDescription()), "wave description should be retained");
        require(wave.getMobs().get(0).getDescription() != null, "mob description object should be retained");
        requireValid(wave, "description fields must not produce validation issues");
    }

    private static void missingMobsRemainsInvalid() {
        ZombiesWaveDefinition wave = readWave(
                "wave_001.json",
                1,
                "{\"wave\":1,\"description\":\"missing mobs is still invalid\"}");

        ZombiesWaveValidator.ValidationReport report = VALIDATOR.validate(List.of(wave));
        require(report.hasIssue(ZombiesWaveValidator.MISSING_MOBS), "missing mobs should be reported");
    }

    private static void explicitEmptyWaveIsValid() {
        ZombiesWaveDefinition wave = readWave(
                "wave_001.json",
                1,
                "{\"wave\":1,\"description\":\"empty wave\",\"mobs\":[]}");

        require(wave.isEmptyWave(), "explicit empty mobs list should be an empty wave");
        requireValid(wave, "explicit empty wave should be valid");
    }

    private static void fastestSlowestSpawnIntervalRangeIsValid() {
        ZombiesWaveDefinition wave = readWave(
                "wave_001.json",
                1,
                "{"
                        + "\"wave\":1,"
                        + "\"fastestSpawnIntervalTicks\":20,"
                        + "\"slowestSpawnIntervalTicks\":50,"
                        + "\"mobs\":[{\"entity\":\"minecraft:zombie\",\"count\":1}]"
                        + "}");

        require(wave.getFastestSpawnIntervalTicks() == 20, "fastest interval should be retained");
        require(wave.getSlowestSpawnIntervalTicks() == 50, "slowest interval should be retained");
        requireValid(wave, "fastest/slowest spawn interval range should be valid");
    }

    private static void reversedSpawnIntervalRangeIsInvalid() {
        ZombiesWaveDefinition wave = readWave(
                "wave_001.json",
                1,
                "{"
                        + "\"wave\":1,"
                        + "\"fastestSpawnIntervalTicks\":50,"
                        + "\"slowestSpawnIntervalTicks\":20,"
                        + "\"mobs\":[{\"entity\":\"minecraft:zombie\",\"count\":1}]"
                        + "}");

        ZombiesWaveValidator.ValidationReport report = VALIDATOR.validate(List.of(wave));
        require(report.hasIssue(ZombiesWaveValidator.INVALID_SPAWN_INTERVAL),
                "reversed fastest/slowest interval should be rejected");
        require(firstIssue(report).contains("fastestSpawnIntervalTicks"),
                "reversed interval message should identify fastest/slowest fields");
    }

    private static void filenameWaveConflictIsInvalid() {
        ZombiesWaveDefinition wave = readWave(
                "wave_001.json",
                1,
                "{\"wave\":2,\"description\":\"conflict\",\"mobs\":[]}");

        ZombiesWaveValidator.ValidationReport report = VALIDATOR.validate(List.of(wave));
        require(report.hasIssue(ZombiesWaveValidator.WAVE_CONFLICT), "filename/wave conflict should be reported");
    }

    private static void duplicateWaveNumberIsInvalid() {
        ZombiesWaveDefinition first = readWave(
                "wave_001.json",
                1,
                "{\"wave\":1,\"description\":\"first\",\"mobs\":[]}");
        ZombiesWaveDefinition duplicate = readWave(
                "wave_001_copy.json",
                1,
                "{\"wave\":1,\"description\":\"duplicate\",\"mobs\":[]}");

        ZombiesWaveValidator.ValidationReport report = VALIDATOR.validate(List.of(first, duplicate));
        require(report.hasIssue(ZombiesWaveValidator.WAVE_CONFLICT), "duplicate wave number should be reported");
        require(firstIssue(report).contains("defined more than once"),
                "duplicate wave number message should identify duplication");
    }

    private static void supportedDefaultEntitiesAreValid() {
        ZombiesWaveDefinition wave = readWave(
                "wave_001.json",
                1,
                "{"
                        + "\"wave\":1,"
                        + "\"mobs\":["
                        + "{\"entity\":\"minecraft:zombie\",\"count\":1},"
                        + "{\"entity\":\"minecraft:husk\",\"count\":1},"
                        + "{\"entity\":\"minecraft:wither_skeleton\",\"count\":1},"
                        + "{\"entity\":\"minecraft:creeper\",\"count\":1},"
                        + "{\"entity\":\"minecraft:wolf\",\"count\":1},"
                        + "{\"entity\":\"minecraft:silverfish\",\"count\":1},"
                        + "{\"entity\":\"minecraft:spider\",\"count\":1},"
                        + "{\"entity\":\"minecraft:vindicator\",\"count\":1},"
                        + "{\"entity\":\"minecraft:vex\",\"count\":1},"
                        + "{\"entity\":\"minecraft:warden\",\"count\":1}"
                        + "]"
                        + "}");

        requireValid(wave, "supported zombie-mode hostile entities should be valid");
    }

    private static void mobAttributeMultipliersDefaultToOneAndValidateWhenPositive() {
        ZombiesWaveDefinition wave = readWave(
                "wave_001.json",
                1,
                "{"
                        + "\"wave\":1,"
                        + "\"mobs\":["
                        + "{\"entity\":\"minecraft:zombie\",\"count\":1},"
                        + "{"
                        + "\"entity\":\"minecraft:vindicator\","
                        + "\"count\":1,"
                        + "\"healthMultiplier\":1.5,"
                        + "\"damageMultiplier\":1.25,"
                        + "\"speedMultiplier\":0.9"
                        + "}"
                        + "]"
                        + "}");

        requireValid(wave, "positive per-mob attribute multipliers should be valid");
        requireClose(wave.getMobs().get(0).getHealthMultiplier(), 1.0D,
                "missing mob health multiplier should default to 1");
        requireClose(wave.getMobs().get(0).getDamageMultiplier(), 1.0D,
                "missing mob damage multiplier should default to 1");
        requireClose(wave.getMobs().get(0).getSpeedMultiplier(), 1.0D,
                "missing mob speed multiplier should default to 1");
        requireClose(wave.getMobs().get(1).getHealthMultiplier(), 1.5D,
                "configured mob health multiplier should be retained");
        requireClose(wave.getMobs().get(1).getDamageMultiplier(), 1.25D,
                "configured mob damage multiplier should be retained");
        requireClose(wave.getMobs().get(1).getSpeedMultiplier(), 0.9D,
                "configured mob speed multiplier should be retained");
    }

    private static void invalidMobAttributeMultiplierIsRejected() {
        ZombiesWaveDefinition wave = readWave(
                "wave_001.json",
                1,
                "{"
                        + "\"wave\":1,"
                        + "\"mobs\":[{"
                        + "\"entity\":\"minecraft:zombie\","
                        + "\"count\":1,"
                        + "\"healthMultiplier\":0"
                        + "}]"
                        + "}");

        ZombiesWaveValidator.ValidationReport report = VALIDATOR.validate(List.of(wave));
        require(report.hasIssue(ZombiesWaveValidator.INVALID_MULTIPLIER),
                "invalid per-mob attribute multiplier should be rejected");
        require(firstIssue(report).contains("healthMultiplier"),
                "invalid per-mob multiplier message should identify the field");
    }

    private static void duplicatePositiveCountMobEntityIsRejected() {
        ZombiesWaveDefinition wave = readWave(
                "wave_001.json",
                1,
                "{"
                        + "\"wave\":1,"
                        + "\"mobs\":["
                        + "{\"entity\":\"minecraft:zombie\",\"count\":1,\"healthMultiplier\":1.0},"
                        + "{\"entity\":\"zombie\",\"count\":2,\"healthMultiplier\":1.5}"
                        + "]"
                        + "}");

        ZombiesWaveValidator.ValidationReport report = VALIDATOR.validate(List.of(wave));
        require(report.hasIssue(ZombiesWaveValidator.DUPLICATE_MOB_ENTITY),
                "duplicate positive-count mob entities should be rejected");
        require(firstIssue(report).contains("more than once"),
                "duplicate mob entity message should identify the one-entry-per-entity rule");
    }

    private static void invalidEntityIdIsRejected() {
        ZombiesWaveDefinition wave = readWave(
                "wave_001.json",
                1,
                "{\"wave\":1,\"mobs\":[{\"entity\":\"minecraft:bad id\",\"count\":1}]}");

        ZombiesWaveValidator.ValidationReport report = VALIDATOR.validate(List.of(wave));
        require(report.hasIssue(ZombiesWaveValidator.INVALID_ENTITY), "invalid entity id should be reported");
        require(firstIssue(report).contains("invalid entity id"), "invalid entity message should identify parse failure");
    }

    private static void unsupportedEntityIdIsRejected() {
        ZombiesWaveDefinition wave = readWave(
                "wave_001.json",
                1,
                "{\"wave\":1,\"mobs\":[{\"entity\":\"minecraft:skeleton\",\"count\":1}]}");

        ZombiesWaveValidator.ValidationReport report = VALIDATOR.validate(List.of(wave));
        require(report.hasIssue(ZombiesWaveValidator.INVALID_ENTITY), "unsupported entity id should be reported");
        require(firstIssue(report).contains("unsupported entity id"), "unsupported entity message should identify support list");
    }

    private static ZombiesWaveDefinition readWave(String fileName, int fileWave, String json) {
        ZombiesWaveDefinition wave = GSON.fromJson(json, ZombiesWaveDefinition.class);
        wave.attachSource(Path.of(fileName), fileWave, json.contains("\"mobs\""));
        return wave;
    }

    private static void requireValid(ZombiesWaveDefinition wave, String message) {
        ZombiesWaveValidator.ValidationReport report = VALIDATOR.validate(List.of(wave));
        require(report.isValid(), message + ": " + firstIssue(report));
    }

    private static String firstIssue(ZombiesWaveValidator.ValidationReport report) {
        if (report.getIssues().isEmpty()) {
            return "no issues";
        }
        ZombiesWaveValidator.ValidationIssue issue = report.getIssues().get(0);
        return issue.getCode() + " " + issue.getMessage();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireClose(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > 0.0001D) {
            throw new AssertionError(message + ": expected " + expected + " got " + actual);
        }
    }
}
