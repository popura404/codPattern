package com.cdp.codpattern.config.backpack;

import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BackpackNameHelper {
    private static final Pattern LEGACY_DEFAULT_NAME = Pattern.compile(
            "^(?:自定义背包|背包|backpack)\\s*#?\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE);

    private BackpackNameHelper() {
    }

    public static Component displayNameComponent(BackpackConfig.Backpack backpack, int fallbackId) {
        return displayNameComponent(backpack == null ? null : backpack.getName(), fallbackId);
    }

    public static Component displayNameComponent(String rawName, int fallbackId) {
        Integer parsedId = parseLegacyDefaultId(rawName);
        if (parsedId != null) {
            return Component.translatable("message.codpattern.backpack.default_name_numbered", parsedId);
        }
        if (rawName != null && !rawName.isBlank()) {
            return Component.literal(rawName.trim());
        }
        if (fallbackId > 0) {
            return Component.translatable("message.codpattern.backpack.default_name_numbered", fallbackId);
        }
        return Component.translatable("message.codpattern.backpack.default_name");
    }

    public static String displayName(String rawName, int fallbackId) {
        return displayNameComponent(rawName, fallbackId).getString();
    }

    public static boolean isGeneratedName(String rawName) {
        return rawName == null || rawName.isBlank() || parseLegacyDefaultId(rawName) != null;
    }

    private static Integer parseLegacyDefaultId(String rawName) {
        if (rawName == null) {
            return null;
        }
        String normalized = rawName.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        Matcher matcher = LEGACY_DEFAULT_NAME.matcher(normalized.toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
