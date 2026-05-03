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
        boolean validatePurchases,
        boolean validateFullInitial,
        List<ZombiesMapValidationContributor> contributors
) {
    public static final String MVP1_MINIMAL_KEY = "MVP1_MINIMAL";
    public static final String MVP2_PURCHASES_KEY = "MVP2_PURCHASES";
    public static final String MVP3_FULL_INITIAL_KEY = "MVP3_FULL_INITIAL";

    public static final ZombiesMapValidationProfile MVP1_MINIMAL = new ZombiesMapValidationProfile(
            MVP1_MINIMAL_KEY,
            true,
            true,
            true,
            true,
            true,
            false,
            false,
            List.of());
    public static final ZombiesMapValidationProfile MVP2_PURCHASES = new ZombiesMapValidationProfile(
            MVP2_PURCHASES_KEY,
            MVP1_MINIMAL.requireEndTeleportPoint,
            MVP1_MINIMAL.requireInitialPlayerSpawn,
            MVP1_MINIMAL.requireGroupOneZombieSpawn,
            MVP1_MINIMAL.rejectDynamicPlayerSpawns,
            MVP1_MINIMAL.requireUniqueObjectIds,
            true,
            false,
            List.of());
    public static final ZombiesMapValidationProfile MVP3_FULL_INITIAL = new ZombiesMapValidationProfile(
            MVP3_FULL_INITIAL_KEY,
            MVP2_PURCHASES.requireEndTeleportPoint,
            MVP2_PURCHASES.requireInitialPlayerSpawn,
            MVP2_PURCHASES.requireGroupOneZombieSpawn,
            MVP2_PURCHASES.rejectDynamicPlayerSpawns,
            MVP2_PURCHASES.requireUniqueObjectIds,
            MVP2_PURCHASES.validatePurchases,
            true,
            List.of());

    public ZombiesMapValidationProfile(
            String key,
            boolean requireEndTeleportPoint,
            boolean requireInitialPlayerSpawn,
            boolean requireGroupOneZombieSpawn,
            boolean rejectDynamicPlayerSpawns,
            boolean requireUniqueObjectIds,
            List<ZombiesMapValidationContributor> contributors
    ) {
        this(
                key,
                requireEndTeleportPoint,
                requireInitialPlayerSpawn,
                requireGroupOneZombieSpawn,
                rejectDynamicPlayerSpawns,
                requireUniqueObjectIds,
                false,
                false,
                contributors);
    }

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
                validatePurchases,
                validateFullInitial,
                extraContributors);
    }
}
