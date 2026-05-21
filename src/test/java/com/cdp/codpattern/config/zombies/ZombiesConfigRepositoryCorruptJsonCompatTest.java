package com.cdp.codpattern.config.zombies;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ZombiesConfigRepositoryCorruptJsonCompatTest {
    public static void main(String[] args) throws Exception {
        malformedBackpackConfigFallsBackToGeneratedDefault();
        malformedRulesConfigFallsBackToGeneratedDefault();
        malformedWeaponFilterConfigFallsBackToGeneratedDefault();
    }

    private static void malformedBackpackConfigFallsBackToGeneratedDefault() throws Exception {
        Path path = tempFile("zombies-corrupt-backpack-", "zombies_backpack_config.json");
        Files.writeString(path, """
                {
                  "playerData": {"gunId": "tacz:ak47"}
                }
                """);

        ZombiesBackpackConfig config = ZombiesBackpackConfigRepository.loadOrCreate(path);

        require(config.getPlayerData().isEmpty(), "corrupt backpack config should fall back to empty player data");
        require(
                Files.readString(path).contains("\"playerData\""),
                "corrupt backpack config should be replaced with generated JSON");
    }

    private static void malformedRulesConfigFallsBackToGeneratedDefault() throws Exception {
        Path path = tempFile("zombies-corrupt-rules-", "config.json");
        Files.writeString(path, "{\"defaults\": \"bad-shape\"}");

        ZombiesRulesConfig config = ZombiesRulesRepository.loadOrCreate(path);

        require(config.getDefaults() != null, "corrupt rules config should fall back to defaults");
        require(config.getArmor() != null, "corrupt rules config should include armor defaults");
        require(
                Files.readString(path).contains("\"armor\""),
                "corrupt rules config should be replaced with generated JSON");
    }

    private static void malformedWeaponFilterConfigFallsBackToGeneratedDefault() throws Exception {
        Path path = tempFile("zombies-corrupt-filter-", "zombies_weapon_filter.json");
        Files.writeString(path, "{\"defaultWeapon\": \"bad-shape\"}");

        ZombiesWeaponFilterConfig config = ZombiesWeaponFilterRepository.loadOrCreate(path);

        require(
                ZombiesBackpackConfig.DEFAULT_TACZ_GUN_ITEM.equals(config.getDefaultWeapon().getItem()),
                "corrupt weapon filter config should fall back to default TaCZ item");
        require(
                Files.readString(path).contains("\"defaultWeapon\""),
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
