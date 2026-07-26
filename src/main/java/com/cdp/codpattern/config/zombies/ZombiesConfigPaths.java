package com.cdp.codpattern.config.zombies;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/** Addon-owned construction of the existing map-scoped Zombies config paths. */
public final class ZombiesConfigPaths {
    private static final String SERVER_ZOMBIES_RULES_ROOT = "serverconfig/codpattern/zombies_rules";
    private static final String ZOMBIES_RULES_CONFIG_FILE = "config.json";
    private static final String ZOMBIES_WAVES_DIRECTORY = "waves";
    private static final String ZOMBIES_WEAPON_FILTER_FILE = "zombies_weapon_filter.json";

    private ZombiesConfigPaths() {
    }

    public static Path zombiesMapRulesRoot(MinecraftServer server, String mapName) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve(SERVER_ZOMBIES_RULES_ROOT)
                .resolve(safeMapConfigName(mapName));
    }

    public static Path zombiesMapRulesConfig(MinecraftServer server, String mapName) {
        return zombiesMapRulesRoot(server, mapName).resolve(ZOMBIES_RULES_CONFIG_FILE);
    }

    public static Path zombiesMapWaves(MinecraftServer server, String mapName) {
        return zombiesMapRulesRoot(server, mapName).resolve(ZOMBIES_WAVES_DIRECTORY);
    }

    public static Path zombiesMapWeaponFilter(MinecraftServer server, String mapName) {
        return zombiesMapRulesRoot(server, mapName).resolve(ZOMBIES_WEAPON_FILTER_FILE);
    }

    public static String safeMapConfigName(String mapName) {
        String value = mapName == null ? "" : mapName.trim();
        if (value.isEmpty()) {
            return "default";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch < 32
                    || ch == '/'
                    || ch == '\\'
                    || ch == ':'
                    || ch == '*'
                    || ch == '?'
                    || ch == '"'
                    || ch == '<'
                    || ch == '>'
                    || ch == '|') {
                builder.append('_');
            } else {
                builder.append(ch);
            }
        }
        String sanitized = builder.toString().trim();
        if (sanitized.isEmpty() || ".".equals(sanitized) || "..".equals(sanitized)) {
            return "default";
        }
        return sanitized;
    }
}
