package com.cdp.codpattern.client.zombies;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class ZombiesRarityDisplay {
    private static final int COMMON_COLOR = 0xFF22C55E;
    private static final int RARE_COLOR = 0xFF3B82F6;
    private static final int EPIC_COLOR = 0xFFA855F7;

    private ZombiesRarityDisplay() {
    }

    public static Optional<Entry> fromRarityId(String rarityId) {
        return switch (normalize(rarityId)) {
            case "common" -> Optional.of(new Entry("common", "普通", COMMON_COLOR));
            case "rare" -> Optional.of(new Entry("rare", "稀有", RARE_COLOR));
            case "epic" -> Optional.of(new Entry("epic", "史诗", EPIC_COLOR));
            default -> Optional.empty();
        };
    }

    private static String normalize(String rarityId) {
        return Objects.requireNonNullElse(rarityId, "").trim().toLowerCase(Locale.ROOT);
    }

    public record Entry(String id, String label, int color) {
    }
}
