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
        filenameWaveConflictIsInvalid();
        duplicateWaveNumberIsInvalid();
        supportedDefaultEntitiesAreValid();
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
                        + "{\"entity\":\"minecraft:husk\",\"count\":1}"
                        + "]"
                        + "}");

        requireValid(wave, "default zombie and husk entities should be valid");
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
}
