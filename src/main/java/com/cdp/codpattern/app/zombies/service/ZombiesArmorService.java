package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.zombies.model.ZombiesArmorState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure per-player zombies armor purchase service.
 */
public final class ZombiesArmorService {
    private static final ZombiesErrorCode ARMOR_ALREADY_OWNED = ZombiesErrorCode.of("armor.already_owned");
    private static final ZombiesErrorCode ARMOR_INVALID_LEVEL = ZombiesErrorCode.of("armor.invalid_level");
    private static final ZombiesErrorCode ARMOR_INVALID_MULTIPLIER = ZombiesErrorCode.of("armor.invalid_damage_multiplier");

    private final ZombiesEconomyService economyService;

    public ZombiesArmorService(ZombiesEconomyService economyService) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
    }

    public ZombiesServiceResult<ArmorPurchaseResult> purchaseArmor(
            UUID playerId,
            int armorLevel,
            double damageTakenMultiplier,
            double cost
    ) {
        if (!ZombiesArmorState.isValidOwnedLevel(armorLevel)) {
            return ZombiesServiceResult.failure(ARMOR_INVALID_LEVEL, armorParams(armorLevel, damageTakenMultiplier), "");
        }
        if (!ZombiesArmorState.isValidDamageTakenMultiplier(damageTakenMultiplier)) {
            return ZombiesServiceResult.failure(ARMOR_INVALID_MULTIPLIER, armorParams(armorLevel, damageTakenMultiplier), "");
        }
        if (economyService.state(playerId)
                .flatMap(state -> state.armor())
                .filter(currentArmor -> currentArmor.isAtLeast(armorLevel))
                .isPresent()) {
            return ZombiesServiceResult.failure(ARMOR_ALREADY_OWNED, armorParams(armorLevel, damageTakenMultiplier), "");
        }

        return economyService.spendAtomically(playerId, cost, state -> {
            ZombiesArmorState currentArmor = state.armor().orElse(null);
            if (currentArmor != null && currentArmor.isAtLeast(armorLevel)) {
                return ZombiesServiceResult.failure(ARMOR_ALREADY_OWNED, armorParams(armorLevel, damageTakenMultiplier), "");
            }

            ZombiesArmorState armor = new ZombiesArmorState(armorLevel, damageTakenMultiplier);
            state.setArmor(armor);
            return ZombiesServiceResult.success(new ArmorPurchaseResult(armor, cost));
        });
    }

    public static ZombiesErrorCode armorAlreadyOwnedCode() {
        return ARMOR_ALREADY_OWNED;
    }

    private static Map<String, ModePlayerValue> armorParams(int armorLevel, double damageTakenMultiplier) {
        Map<String, ModePlayerValue> params = new LinkedHashMap<>();
        params.put("armorLevel", ModePlayerValue.ofInt(armorLevel));
        params.put("damageTakenMultiplier", ModePlayerValue.ofDouble(damageTakenMultiplier));
        return params;
    }

    public record ArmorPurchaseResult(
            ZombiesArmorState armor,
            double cost
    ) {
        public ArmorPurchaseResult {
            Objects.requireNonNull(armor, "armor");
            cost = Math.max(0.0D, cost);
        }
    }
}
