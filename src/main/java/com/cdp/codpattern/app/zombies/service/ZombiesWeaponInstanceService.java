package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.zombies.model.ZombiesPlayerRuntimeState;
import com.cdp.codpattern.app.zombies.model.ZombiesWeaponInstanceState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure primary weapon state service for zombies purchases.
 */
public final class ZombiesWeaponInstanceService {
    private static final ZombiesErrorCode WEAPON_INVALID_PURCHASE = ZombiesErrorCode.of("weapon.invalid_purchase");

    private final ZombiesEconomyService economyService;

    public ZombiesWeaponInstanceService(ZombiesEconomyService economyService) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
    }

    public ZombiesServiceResult<WallWeaponPurchaseResult> purchaseWallWeapon(
            UUID playerId,
            String gunId,
            int weaponLevel,
            double damageMultiplier,
            int maxReserveAmmo,
            double cost
    ) {
        return purchaseWallWeapon(playerId, gunId, "", weaponLevel, damageMultiplier, maxReserveAmmo, cost, null);
    }

    public ZombiesServiceResult<WallWeaponPurchaseResult> purchaseWallWeapon(
            UUID playerId,
            String gunId,
            String rarityId,
            int weaponLevel,
            double damageMultiplier,
            int maxReserveAmmo,
            double cost
    ) {
        return purchaseWallWeapon(playerId, gunId, rarityId, weaponLevel, damageMultiplier, maxReserveAmmo, cost, null);
    }

    public ZombiesServiceResult<WallWeaponPurchaseResult> purchaseWallWeapon(
            UUID playerId,
            String gunId,
            String rarityId,
            int weaponLevel,
            double damageMultiplier,
            int maxReserveAmmo,
            double cost,
            WallWeaponCommitGuard commitGuard
    ) {
        if (!ZombiesWeaponInstanceState.isValidGunId(gunId)
                || !ZombiesWeaponInstanceState.isValidWeaponLevel(weaponLevel)
                || !ZombiesWeaponInstanceState.isValidDamageMultiplier(damageMultiplier)
                || maxReserveAmmo < 0) {
            return ZombiesServiceResult.failure(WEAPON_INVALID_PURCHASE, weaponParams(gunId, rarityId, weaponLevel), "");
        }
        if (economyService.state(playerId)
                .flatMap(ZombiesPlayerRuntimeState::primaryWeapon)
                .filter(weapon -> sameOwnedWallWeapon(weapon, gunId, rarityId, weaponLevel))
                .isPresent()) {
            return ZombiesServiceResult.failure(
                    ZombiesErrorCode.WEAPON_ALREADY_OWNED,
                    weaponParams(gunId, rarityId, weaponLevel),
                    "");
        }

        return economyService.spendAtomically(playerId, cost, state -> {
            ZombiesWeaponInstanceState currentWeapon = state.primaryWeapon().orElse(null);
            if (currentWeapon != null && sameOwnedWallWeapon(currentWeapon, gunId, rarityId, weaponLevel)) {
                return ZombiesServiceResult.failure(
                        ZombiesErrorCode.WEAPON_ALREADY_OWNED,
                        weaponParams(gunId, rarityId, weaponLevel),
                        "");
            }

            ZombiesWeaponInstanceState weapon = ZombiesWeaponInstanceState.wallPrimary(
                    gunId,
                    rarityId,
                    weaponLevel,
                    damageMultiplier,
                    maxReserveAmmo);
            ZombiesServiceResult<?> guardResult = commitGuard == null
                    ? ZombiesServiceResult.ok()
                    : commitGuard.beforeCommit(currentWeapon, weapon);
            if (guardResult == null || !guardResult.success()) {
                return ZombiesServiceResult.failure(
                        guardResult == null ? ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON : guardResult.code(),
                        guardResult == null ? Map.of() : guardResult.params(),
                        guardResult == null ? "" : guardResult.logMessage());
            }
            state.setPrimaryWeapon(weapon);
            return ZombiesServiceResult.success(new WallWeaponPurchaseResult(weapon, cost));
        });
    }

    public ZombiesServiceResult<ZombiesWeaponInstanceState> currentPrimaryWeapon(UUID playerId) {
        return economyService.state(playerId)
                .flatMap(ZombiesPlayerRuntimeState::primaryWeapon)
                .map(ZombiesServiceResult::success)
                .orElseGet(() -> ZombiesServiceResult.failure(ZombiesErrorCode.WEAPON_INVALID_CURRENT_WEAPON));
    }

    private static Map<String, ModePlayerValue> weaponParams(String gunId, int weaponLevel) {
        return weaponParams(gunId, "", weaponLevel);
    }

    private static Map<String, ModePlayerValue> weaponParams(String gunId, String rarityId, int weaponLevel) {
        Map<String, ModePlayerValue> params = new LinkedHashMap<>();
        params.put("gunId", ModePlayerValue.ofString(gunId));
        params.put("rarityId", ModePlayerValue.ofString(rarityId));
        params.put("weaponLevel", ModePlayerValue.ofInt(weaponLevel));
        return params;
    }

    private static boolean sameOwnedWallWeapon(
            ZombiesWeaponInstanceState weapon,
            String gunId,
            String rarityId,
            int weaponLevel
    ) {
        String normalizedRarityId = Objects.requireNonNullElse(rarityId, "").trim();
        if (!normalizedRarityId.isBlank()) {
            return weapon.sameGunAndRarity(gunId, normalizedRarityId);
        }
        return weapon.sameGunAndLevel(gunId, weaponLevel);
    }

    public record WallWeaponPurchaseResult(
            ZombiesWeaponInstanceState weapon,
            double cost
    ) {
        public WallWeaponPurchaseResult {
            Objects.requireNonNull(weapon, "weapon");
            cost = Math.max(0.0D, cost);
        }
    }

    @FunctionalInterface
    public interface WallWeaponCommitGuard {
        ZombiesServiceResult<?> beforeCommit(
                ZombiesWeaponInstanceState currentWeapon,
                ZombiesWeaponInstanceState purchasedWeapon);
    }
}
