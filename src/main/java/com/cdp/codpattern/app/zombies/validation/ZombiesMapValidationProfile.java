package com.cdp.codpattern.app.zombies.validation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record ZombiesMapValidationProfile(
        String key,
        boolean requireEndTeleportPoint,
        boolean requireInitialPlayerSpawn,
        boolean requireGroupOneZombieSpawn,
        boolean rejectDynamicPlayerSpawns,
        boolean requireUniqueObjectIds,
        List<ZombiesMapValidationContributor> contributors
) {
    public static final String MVP1_MINIMAL_KEY = "MVP1_MINIMAL";
    public static final ZombiesMapValidationProfile MVP1_MINIMAL = new ZombiesMapValidationProfile(
            MVP1_MINIMAL_KEY,
            true,
            true,
            true,
            true,
            true,
            List.of());

    public ZombiesMapValidationProfile {
        key = Objects.requireNonNullElse(key, "").trim();
        contributors = contributors == null
                ? List.of()
                : contributors.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(ZombiesMapValidationContributor::order))
                .toList();
    }

    public ZombiesMapValidationProfile withContributors(List<ZombiesMapValidationContributor> extraContributors) {
        return new ZombiesMapValidationProfile(
                key,
                requireEndTeleportPoint,
                requireInitialPlayerSpawn,
                requireGroupOneZombieSpawn,
                rejectDynamicPlayerSpawns,
                requireUniqueObjectIds,
                extraContributors);
    }
}
