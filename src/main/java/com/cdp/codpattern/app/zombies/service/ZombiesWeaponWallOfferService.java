package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.zombies.map.object.ZombiesWeaponWallData;
import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.cdp.codpattern.config.zombies.ZombiesRulesConfig;
import com.cdp.codpattern.config.zombies.ZombiesRulesRepository;
import com.cdp.codpattern.config.zombies.ZombiesRulesValidator;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.function.Function;
import java.util.function.Supplier;

public class ZombiesWeaponWallOfferService {
    private static final int INTERNAL_COMPAT_WEAPON_LEVEL = 1;

    private final Supplier<ZombiesRulesConfig> rulesSupplier;
    private final RandomGenerator random;
    private final Function<String, ItemStack> weaponStackFactory;

    public ZombiesWeaponWallOfferService() {
        this(ZombiesRulesRepository::getConfig, new Random(), ZombiesWeaponInventoryService::createDefaultTaczGunStackForRules);
    }

    public ZombiesWeaponWallOfferService(
            Supplier<ZombiesRulesConfig> rulesSupplier,
            RandomGenerator random,
            Function<String, ItemStack> weaponStackFactory
    ) {
        this.rulesSupplier = rulesSupplier == null ? ZombiesRulesRepository::getConfig : rulesSupplier;
        this.random = random == null ? new Random() : random;
        this.weaponStackFactory = weaponStackFactory == null
                ? ZombiesWeaponInventoryService::createDefaultTaczGunStackForRules
                : weaponStackFactory;
    }

    public ZombiesObjectStateStore.WeaponWallOffer createOffer(
            ZombiesWeaponWallData weaponWall,
            int currentWave
    ) {
        if (weaponWall == null) {
            return ZombiesObjectStateStore.WeaponWallOffer.empty();
        }
        ZombiesRulesConfig rules = rulesSupplier.get();
        if (rules == null) {
            rules = new ZombiesRulesConfig();
        }
        ZombiesRulesConfig.WeaponWall weaponWallRules = rules.getWeaponWall();
        ZombiesRulesConfig.WeaponRules weaponRules = rules.getWeaponRules();
        int refreshCount = refreshCount(currentWave, weaponWallRules.getRefreshIntervalWaves());
        List<WeightedRarity> rarities = weightedRarities(weaponWallRules, refreshCount);
        WeightedRarity rarity = pickWeightedRarity(rarities, random);
        if (rarity == null) {
            return ZombiesObjectStateStore.WeaponWallOffer.empty();
        }
        ZombiesRulesConfig.GunWeight gun = pickWeightedGun(guns(rarity.rarity()), random);
        if (gun == null || gun.getGunId() == null || gun.getGunId().trim().isBlank()) {
            return ZombiesObjectStateStore.WeaponWallOffer.empty();
        }
        int maxReserveAmmo = maxReserveAmmo(gun.getGunId(), weaponRules.getWeaponPoolAmmunitionPerMagazineMultiple());
        return new ZombiesObjectStateStore.WeaponWallOffer(
                weaponWall.objectId(),
                rarity.id(),
                gun.getGunId(),
                rarity.rarity().getPrice() == null ? 0 : rarity.rarity().getPrice(),
                maxReserveAmmo,
                rarity.rarity().getDamageMultiplier() == null ? 0.0D : rarity.rarity().getDamageMultiplier());
    }

    public int refreshIntervalWaves() {
        ZombiesRulesConfig rules = rulesSupplier.get();
        if (rules == null) {
            return 1;
        }
        return Math.max(1, rules.getWeaponWall().getRefreshIntervalWaves());
    }

    public boolean shouldRefreshForWave(int targetWave) {
        if (targetWave < 1) {
            return false;
        }
        if (targetWave == 1) {
            return true;
        }
        return (targetWave - 1) % refreshIntervalWaves() == 0;
    }

    private int maxReserveAmmo(String gunId, int magazineMultiple) {
        ItemStack stack = weaponStackFactory.apply(gunId);
        if (stack == null || stack.isEmpty() || !TaczGatewayProvider.gateway().isGun(stack)) {
            return 0;
        }
        int magazineAmmo = TaczGatewayProvider.gateway().resolveMagazineAmmo(stack);
        if (magazineAmmo <= 0) {
            TaczGatewayProvider.gateway().configureGunAmmo(stack, 0);
            magazineAmmo = TaczGatewayProvider.gateway().resolveMagazineAmmo(stack);
        }
        return Math.max(0, magazineAmmo * Math.max(0, magazineMultiple));
    }

    private static int refreshCount(int currentWave, int refreshIntervalWaves) {
        int wave = Math.max(1, currentWave);
        int interval = Math.max(1, refreshIntervalWaves);
        return Math.max(0, (wave - 1) / interval);
    }

    private static List<WeightedRarity> weightedRarities(
            ZombiesRulesConfig.WeaponWall weaponWallRules,
            int refreshCount
    ) {
        List<WeightedRarity> rarities = new ArrayList<>();
        if (weaponWallRules == null) {
            return rarities;
        }
        for (ZombiesRulesConfig.Rarity rarity : weaponWallRules.getRarities()) {
            if (rarity == null) {
                continue;
            }
            String rarityId = normalizeRarityId(rarity.getId());
            if (!ZombiesRulesValidator.supportedRarityId(rarityId) || guns(rarity).isEmpty()) {
                continue;
            }
            double currentWeight = clamp(
                    finiteOrZero(rarity.getInitialWeight())
                            + refreshCount * finiteOrZero(rarity.getWeightDeltaPerRefresh()),
                    finiteOrZero(rarity.getMinWeight()),
                    finiteOrZero(rarity.getMaxWeight()));
            if (currentWeight > 0.0D) {
                rarities.add(new WeightedRarity(rarityId, rarity, currentWeight));
            }
        }
        return rarities;
    }

    private static List<ZombiesRulesConfig.GunWeight> guns(ZombiesRulesConfig.Rarity rarity) {
        if (rarity == null || rarity.getGuns() == null) {
            return List.of();
        }
        return rarity.getGuns().stream()
                .filter(Objects::nonNull)
                .filter(gun -> !Objects.requireNonNullElse(gun.getGunId(), "").trim().isBlank())
                .filter(gun -> gun.getWeight() != null && Double.isFinite(gun.getWeight()) && gun.getWeight() > 0.0D)
                .toList();
    }

    private static WeightedRarity pickWeightedRarity(List<WeightedRarity> values, RandomGenerator random) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        double total = 0.0D;
        for (WeightedRarity value : values) {
            total += value.weight();
        }
        if (total <= 0.0D || !Double.isFinite(total)) {
            return null;
        }
        double cursor = random.nextDouble(total);
        double running = 0.0D;
        for (WeightedRarity value : values) {
            running += value.weight();
            if (cursor < running) {
                return value;
            }
        }
        return values.get(values.size() - 1);
    }

    private static ZombiesRulesConfig.GunWeight pickWeightedGun(
            List<ZombiesRulesConfig.GunWeight> values,
            RandomGenerator random
    ) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        double total = 0.0D;
        for (ZombiesRulesConfig.GunWeight value : values) {
            total += value.getWeight();
        }
        if (total <= 0.0D || !Double.isFinite(total)) {
            return null;
        }
        double cursor = random.nextDouble(total);
        double running = 0.0D;
        for (ZombiesRulesConfig.GunWeight value : values) {
            running += value.getWeight();
            if (cursor < running) {
                return value;
            }
        }
        return values.get(values.size() - 1);
    }

    private static double finiteOrZero(Double value) {
        return value == null || !Double.isFinite(value) ? 0.0D : value;
    }

    private static double clamp(double value, double min, double max) {
        double low = Math.min(min, max);
        double high = Math.max(min, max);
        return Math.max(low, Math.min(high, value));
    }

    private static String normalizeRarityId(String rarityId) {
        return Objects.requireNonNullElse(rarityId, "").trim().toLowerCase(Locale.ROOT);
    }

    private record WeightedRarity(
            String id,
            ZombiesRulesConfig.Rarity rarity,
            double weight
    ) {
        private WeightedRarity {
            id = normalizeRarityId(id);
            Objects.requireNonNull(rarity, "rarity");
            weight = Double.isFinite(weight) ? Math.max(0.0D, weight) : 0.0D;
        }
    }
}
