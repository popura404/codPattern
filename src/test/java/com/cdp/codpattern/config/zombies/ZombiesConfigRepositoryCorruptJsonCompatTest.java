package com.cdp.codpattern.config.zombies;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ZombiesConfigRepositoryCorruptJsonCompatTest {
    public static void main(String[] args) throws Exception {
        malformedRulesConfigFallsBackToGeneratedDefault();
        rulesConfigMissingStarterWeaponBackfillsGeneratedDefault();
        malformedWeaponFilterConfigFallsBackToGeneratedDefault();
    }

    private static void malformedRulesConfigFallsBackToGeneratedDefault() throws Exception {
        Path path = tempFile("zombies-corrupt-rules-", "config.json");
        Files.writeString(path, "{\"defaults\": \"bad-shape\"}");

        ZombiesRulesConfig config = ZombiesRulesRepository.loadOrCreate(path);

        require(config.getDefaults() != null, "corrupt rules config should fall back to defaults");
        require(config.getArmor() != null, "corrupt rules config should include armor defaults");
        require(
                ZombiesRulesConfig.DEFAULT_STARTER_GUN_ITEM.equals(config.getStarterWeapon().getItem()),
                "corrupt rules config should fall back to default starter weapon");
        require(
                Files.readString(path).contains("\"armor\""),
                "corrupt rules config should be replaced with generated JSON");
        require(
                Files.readString(path).contains("\"starterWeapon\""),
                "corrupt rules config should include generated starter weapon JSON");
    }

    private static void rulesConfigMissingStarterWeaponBackfillsGeneratedDefault() throws Exception {
        Path path = tempFile("zombies-rules-missing-starter-", "config.json");
        Files.writeString(path, """
                {
                  "room": {
                    "intermissionSeconds": 5
                  }
                }
                """);

        ZombiesRulesConfig config = ZombiesRulesRepository.loadOrCreate(path);

        require(
                ZombiesRulesConfig.DEFAULT_STARTER_GUN_ITEM.equals(config.getStarterWeapon().getItem()),
                "rules config missing starter weapon should use generated default starter weapon");
        require(
                Files.readString(path).contains("\"starterWeapon\""),
                "rules config missing starter weapon should be backfilled to config.json");
    }

    private static void malformedWeaponFilterConfigFallsBackToGeneratedDefault() throws Exception {
        Path path = tempFile("zombies-corrupt-filter-", "zombies_weapon_filter.json");
        Files.writeString(path, "{\"weaponTabs\": \"bad-shape\"}");

        ZombiesWeaponFilterConfig config = ZombiesWeaponFilterRepository.loadOrCreate(path);

        require(
                Math.abs(config.getAmmunitionPerMagazineMultiple() - 10.0D) < 0.0001D,
                "corrupt weapon filter config should fall back to scaled ammo default");
        require(
                Files.readString(path).contains("\"weaponTabs\""),
                "corrupt weapon filter config should be replaced with generated JSON");
    }

    private static Path tempFile(String prefix, String fileName) throws Exception {
        Path dir = Files.createTempDirectory(prefix);
        return dir.resolve(fileName);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private ZombiesConfigRepositoryCorruptJsonCompatTest() {
    }
}
