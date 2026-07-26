package com.cdp.codpattern.app.zombies.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZombiesMapScopedConfigStaticContractCompatTest {
    private static final Path CONFIG_PATH =
            Path.of("src/main/java/com/cdp/codpattern/config/zombies/ZombiesConfigPaths.java");
    private static final Path RULES_REPOSITORY =
            Path.of("src/main/java/com/cdp/codpattern/config/zombies/ZombiesRulesRepository.java");
    private static final Path FILTER_REPOSITORY =
            Path.of("src/main/java/com/cdp/codpattern/config/zombies/ZombiesWeaponFilterRepository.java");
    private static final Path RULES_CONFIG =
            Path.of("src/main/java/com/cdp/codpattern/config/zombies/ZombiesRulesConfig.java");
    private static final Path ZOMBIES_MAP =
            Path.of("src/main/java/com/cdp/codpattern/compat/fpsmatch/map/ZombiesMap.java");
    private static final Path STARTUP_VALIDATION =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesStartupValidationService.java");
    private static final Path STARTER_KIT =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesStarterKitDistributor.java");
    private static final Path SPAWN_SERVICE =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesMobSpawnService.java");

    private ZombiesMapScopedConfigStaticContractCompatTest() {
    }

    public static void main(String[] args) throws IOException {
        String configPath = read(CONFIG_PATH);
        String rulesRepository = read(RULES_REPOSITORY);
        String filterRepository = read(FILTER_REPOSITORY);
        String rulesConfig = read(RULES_CONFIG);
        String zombiesMap = read(ZOMBIES_MAP);
        String startupValidation = read(STARTUP_VALIDATION);
        String starterKit = read(STARTER_KIT);
        String spawnService = read(SPAWN_SERVICE);

        requireContains(configPath,
                "SERVER_ZOMBIES_RULES_ROOT = \"serverconfig/codpattern/zombies_rules\"",
                "map-scoped zombies configs must live below zombies_rules");
        requireContains(configPath,
                "public static Path zombiesMapRulesConfig(MinecraftServer server, String mapName)",
                "rules config path must be map-scoped");
        requireContains(configPath,
                "public static Path zombiesMapWaves(MinecraftServer server, String mapName)",
                "wave directory path must be map-scoped");
        requireContains(configPath,
                "public static Path zombiesMapWeaponFilter(MinecraftServer server, String mapName)",
                "starter weapon filter path must be map-scoped");
        requireAbsent(configPath,
                "zombiesMapBackpackConfig",
                "starter weapon must not use a separate backpack config path");
        requireAbsent(configPath,
                "zombies_backpack_config.json",
                "starter weapon must not generate a separate backpack config file");
        requireContains(configPath,
                "safeMapConfigName(mapName)",
                "map-scoped config paths must sanitize map names");
        requireAbsent(configPath,
                "SERVER_ZOMBIES_RULES_CONFIG",
                "old global zombies rules config path must not remain");
        requireAbsent(configPath,
                "SERVER_ZOMBIES_WAVES",
                "old global zombies waves path must not remain");
        requireAbsent(configPath,
                "SERVER_ZOMBIES_BACKPACK",
                "old global zombies backpack path must not remain");
        requireAbsent(configPath,
                "SERVER_ZOMBIES_FILTER",
                "old global zombies weapon-filter path must not remain");

        requireContains(rulesRepository,
                "loadOrCreate(ZombiesConfigPaths.zombiesMapRulesConfig(server, mapName))",
                "rules repository must support map-scoped config loading");
        requireAbsent(rulesRepository,
                "loadOrCreate(MinecraftServer server) {\n        return loadOrCreate(ZombiesConfigPaths.",
                "rules repository must not retain the old global server loader");
        requireContains(rulesConfig,
                "private StarterWeapon starterWeapon = StarterWeapon.defaults();",
                "config.json must own the single starter weapon definition");
        requireContains(rulesConfig,
                "public StarterWeapon getStarterWeapon()",
                "rules config must expose starter weapon settings");
        requireContains(filterRepository,
                "loadOrCreate(ZombiesConfigPaths.zombiesMapWeaponFilter(server, mapName))",
                "weapon filter repository must support map-scoped loading");
        requireAbsent(filterRepository,
                "loadOrCreate(MinecraftServer server) {\n        return loadOrCreate(ZombiesConfigPaths.",
                "weapon filter repository must not retain the old global server loader");

        requireContains(zombiesMap,
                "loadStartupConfigs(serverLevel == null ? null : serverLevel.getServer());",
                "map instance construction must bootstrap map-scoped config files");
        requireContains(zombiesMap,
                "ZombiesRulesRepository.loadOrCreate(server, mapName)",
                "map startup must load map-scoped rules");
        requireAbsent(zombiesMap,
                "ZombiesBackpackConfigRepository",
                "map startup must not load a separate starter weapon backpack config");
        requireContains(zombiesMap,
                "ZombiesWeaponFilterRepository.loadOrCreate(server, mapName)",
                "map startup must load map-scoped weapon filters");
        requireContains(zombiesMap,
                "ZombiesConfigPaths.zombiesMapWaves(server, mapName)",
                "map startup must generate/load map-scoped wave files");
        requireContains(zombiesMap,
                "new ZombiesStartupValidationService(\n                        ZombiesConfigPaths.zombiesMapWaves(server, getMapName()),\n                        this::rulesConfig,\n                        this::rulesValidationIssues)",
                "startup validation must read map-scoped waves and rules");
        requireContains(zombiesMap,
                "new ZombiesStarterKitDistributor(this::rulesConfig)",
                "starter weapon ammo rules must use the map instance rules");
        requireContains(zombiesMap,
                "new ZombiesWeaponWallOfferService(this::rulesConfig, null, null)",
                "weapon wall offers must use the map instance rules");
        requireContains(zombiesMap,
                "new ZombiesObjectStateStore(\n                powerService::isPowerOn,\n                new ZombiesWeaponWallOfferService(this::rulesConfig, null, null),\n                this::rulesConfig)",
                "object state payloads must use map-scoped rules for weapon wall and ultimate machine config");
        requireContains(zombiesMap,
                "new ZombiesRoomAnnouncementService(this::survivorPlayers),\n                this::rulesConfig,\n                () -> runtimeState.phase().allowsPurchases())",
                "object interactions must use the map instance rules for armor and ultimate machine config");
        requireContains(zombiesMap,
                "() -> rulesConfig().getSpawnPointWeighting()",
                "spawn point weighting must use the map instance rules");
        requireAbsent(zombiesMap,
                "backpackConfig()",
                "startup flow must receive starter weapons from config.json rules, not backpack config");

        requireContains(startupValidation,
                "Supplier<ZombiesRulesConfig> rulesSupplier",
                "startup validation must be able to use map-scoped wave defaults");
        requireContains(startupValidation,
                "Supplier<List<ZombiesValidationIssue>> rulesIssuesSupplier",
                "startup validation must be able to use map-scoped rules issues");
        requireContains(starterKit,
                "private final Supplier<ZombiesRulesConfig> rulesSupplier;",
                "starter kit distributor must use injected map-scoped rules");
        requireContains(starterKit,
                "rulesConfig().getStarterWeapon()",
                "starter kit distributor must read the unified starter weapon from rules config");
        requireContains(spawnService,
                "private final Supplier<ZombiesRulesConfig.SpawnPointWeighting> spawnPointWeightingSupplier;",
                "spawn service must use injected map-scoped spawn weighting");

        System.out.println("PASS zombies map-scoped config static contract compat");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }

    private static void requireContains(String text, String expected, String message) {
        if (!text.contains(expected)) {
            throw new AssertionError(message + ": missing `" + expected + "`");
        }
    }

    private static void requireAbsent(String text, String unexpected, String message) {
        if (text.contains(unexpected)) {
            throw new AssertionError(message + ": found `" + unexpected + "`");
        }
    }
}
