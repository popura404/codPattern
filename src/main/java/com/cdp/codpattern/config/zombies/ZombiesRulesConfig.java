package com.cdp.codpattern.config.zombies;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ZombiesRulesConfig {
    public static final String DEAD_PLAYER_POLICY_SPECTATE_UNTIL_INTERMISSION = "spectate_until_wave_intermission";
    public static final String RARITY_COMMON = "common";
    public static final String RARITY_RARE = "rare";
    public static final String RARITY_EPIC = "epic";
    public static final String DEFAULT_POOL_GLOCK_17 = "tacz:glock_17";
    public static final String DEFAULT_POOL_AK47 = "tacz:ak47";
    public static final String DEFAULT_POOL_M4A1 = "tacz:m4a1";

    private Room room = new Room();
    private Defaults defaults = new Defaults();
    private Armor armor = new Armor();
    private WeaponWall weaponWall = WeaponWall.defaults();
    private UltimateMachine ultimateMachine = UltimateMachine.defaults();
    private WeaponRules weaponRules = new WeaponRules();
    private SpawnPointWeighting spawnPointWeighting = new SpawnPointWeighting();

    public Room getRoom() {
        if (room == null) {
            room = new Room();
        }
        return room;
    }

    public void setRoom(Room room) {
        this.room = room == null ? new Room() : room;
    }

    public Defaults getDefaults() {
        if (defaults == null) {
            defaults = new Defaults();
        }
        return defaults;
    }

    public void setDefaults(Defaults defaults) {
        this.defaults = defaults == null ? new Defaults() : defaults;
    }

    public Armor getArmor() {
        if (armor == null) {
            armor = new Armor();
        }
        return armor;
    }

    public void setArmor(Armor armor) {
        this.armor = armor == null ? new Armor() : armor;
    }

    public WeaponWall getWeaponWall() {
        if (weaponWall == null) {
            weaponWall = WeaponWall.defaults();
        }
        return weaponWall;
    }

    public void setWeaponWall(WeaponWall weaponWall) {
        this.weaponWall = weaponWall == null ? WeaponWall.defaults() : weaponWall;
    }

    public UltimateMachine getUltimateMachine() {
        if (ultimateMachine == null) {
            ultimateMachine = UltimateMachine.defaults();
        }
        return ultimateMachine;
    }

    public void setUltimateMachine(UltimateMachine ultimateMachine) {
        this.ultimateMachine = ultimateMachine == null ? UltimateMachine.defaults() : ultimateMachine;
    }

    public WeaponRules getWeaponRules() {
        if (weaponRules == null) {
            weaponRules = new WeaponRules();
        }
        return weaponRules;
    }

    public void setWeaponRules(WeaponRules weaponRules) {
        this.weaponRules = weaponRules == null ? new WeaponRules() : weaponRules;
    }

    public SpawnPointWeighting getSpawnPointWeighting() {
        if (spawnPointWeighting == null) {
            spawnPointWeighting = new SpawnPointWeighting();
        }
        return spawnPointWeighting;
    }

    public void setSpawnPointWeighting(SpawnPointWeighting spawnPointWeighting) {
        this.spawnPointWeighting = spawnPointWeighting == null ? new SpawnPointWeighting() : spawnPointWeighting;
    }

    public void normalize() {
        setRoom(room);
        setDefaults(defaults);
        setArmor(armor);
        setWeaponWall(weaponWall);
        setUltimateMachine(ultimateMachine);
        setWeaponRules(weaponRules);
        setSpawnPointWeighting(spawnPointWeighting);
        room.normalize();
        defaults.normalize();
        armor.normalize();
        weaponWall.normalize();
        ultimateMachine.normalize();
        weaponRules.normalize();
        spawnPointWeighting.normalize();
    }

    public static class Room {
        private Integer startVoteTimeoutSeconds = 15;
        private Integer startVoteRequiredPercent = 60;
        private Integer intermissionSeconds = 5;
        private Integer failDelaySeconds = 8;
        private Integer offlineGraceSeconds = 120;
        private String deadPlayerPolicy = DEAD_PLAYER_POLICY_SPECTATE_UNTIL_INTERMISSION;

        public Integer getStartVoteTimeoutSeconds() {
            return startVoteTimeoutSeconds;
        }

        public void setStartVoteTimeoutSeconds(Integer startVoteTimeoutSeconds) {
            this.startVoteTimeoutSeconds = startVoteTimeoutSeconds;
        }

        public Integer getStartVoteRequiredPercent() {
            return startVoteRequiredPercent;
        }

        public void setStartVoteRequiredPercent(Integer startVoteRequiredPercent) {
            this.startVoteRequiredPercent = startVoteRequiredPercent;
        }

        public Integer getIntermissionSeconds() {
            return intermissionSeconds;
        }

        public void setIntermissionSeconds(Integer intermissionSeconds) {
            this.intermissionSeconds = intermissionSeconds;
        }

        public Integer getFailDelaySeconds() {
            return failDelaySeconds;
        }

        public void setFailDelaySeconds(Integer failDelaySeconds) {
            this.failDelaySeconds = failDelaySeconds;
        }

        public Integer getOfflineGraceSeconds() {
            return offlineGraceSeconds;
        }

        public void setOfflineGraceSeconds(Integer offlineGraceSeconds) {
            this.offlineGraceSeconds = offlineGraceSeconds;
        }

        public String getDeadPlayerPolicy() {
            return deadPlayerPolicy;
        }

        public void setDeadPlayerPolicy(String deadPlayerPolicy) {
            this.deadPlayerPolicy = deadPlayerPolicy;
        }

        private void normalize() {
            startVoteTimeoutSeconds = positiveOrDefault(startVoteTimeoutSeconds, 15);
            startVoteRequiredPercent = clampPercent(startVoteRequiredPercent, 60);
            intermissionSeconds = nonNegativeOrDefault(intermissionSeconds, 5);
            failDelaySeconds = nonNegativeOrDefault(failDelaySeconds, 8);
            offlineGraceSeconds = nonNegativeOrDefault(offlineGraceSeconds, 120);
            if (deadPlayerPolicy == null || deadPlayerPolicy.trim().isEmpty()) {
                deadPlayerPolicy = DEAD_PLAYER_POLICY_SPECTATE_UNTIL_INTERMISSION;
            }
        }
    }

    public static class Defaults {
        private Double healthMultiplier = 1.0;
        private Double damageMultiplier = 1.0;
        private Double speedMultiplier = 1.0;
        private Integer maxAlive = 8;
        private Integer fastestSpawnIntervalTicks;
        private Integer slowestSpawnIntervalTicks;
        private Integer spawnIntervalTicks;
        private Integer killPoints = 10;
        private Integer assistPoints = 3;

        public Double getHealthMultiplier() {
            return healthMultiplier;
        }

        public void setHealthMultiplier(Double healthMultiplier) {
            this.healthMultiplier = healthMultiplier;
        }

        public Double getDamageMultiplier() {
            return damageMultiplier;
        }

        public void setDamageMultiplier(Double damageMultiplier) {
            this.damageMultiplier = damageMultiplier;
        }

        public Double getSpeedMultiplier() {
            return speedMultiplier;
        }

        public void setSpeedMultiplier(Double speedMultiplier) {
            this.speedMultiplier = speedMultiplier;
        }

        public Integer getMaxAlive() {
            return maxAlive;
        }

        public void setMaxAlive(Integer maxAlive) {
            this.maxAlive = maxAlive;
        }

        public Integer getSpawnIntervalTicks() {
            return spawnIntervalTicks;
        }

        public void setSpawnIntervalTicks(Integer spawnIntervalTicks) {
            this.spawnIntervalTicks = spawnIntervalTicks;
        }

        public Integer getFastestSpawnIntervalTicks() {
            return fastestSpawnIntervalTicks;
        }

        public void setFastestSpawnIntervalTicks(Integer fastestSpawnIntervalTicks) {
            this.fastestSpawnIntervalTicks = fastestSpawnIntervalTicks;
        }

        public Integer getSlowestSpawnIntervalTicks() {
            return slowestSpawnIntervalTicks;
        }

        public void setSlowestSpawnIntervalTicks(Integer slowestSpawnIntervalTicks) {
            this.slowestSpawnIntervalTicks = slowestSpawnIntervalTicks;
        }

        public Integer getKillPoints() {
            return killPoints;
        }

        public void setKillPoints(Integer killPoints) {
            this.killPoints = killPoints;
        }

        public Integer getAssistPoints() {
            return assistPoints;
        }

        public void setAssistPoints(Integer assistPoints) {
            this.assistPoints = assistPoints;
        }

        private void normalize() {
            healthMultiplier = positiveFiniteOrDefault(healthMultiplier, 1.0);
            damageMultiplier = positiveFiniteOrDefault(damageMultiplier, 1.0);
            speedMultiplier = positiveFiniteOrDefault(speedMultiplier, 1.0);
            maxAlive = positiveOrDefault(maxAlive, 8);
            int defaultFastestInterval = spawnIntervalTicks == null ? 20 : positiveOrDefault(spawnIntervalTicks, 20);
            int defaultSlowestInterval = spawnIntervalTicks == null ? 50 : positiveOrDefault(spawnIntervalTicks, 50);
            fastestSpawnIntervalTicks = positiveOrDefault(fastestSpawnIntervalTicks, defaultFastestInterval);
            slowestSpawnIntervalTicks = positiveOrDefault(slowestSpawnIntervalTicks, defaultSlowestInterval);
            if (fastestSpawnIntervalTicks > slowestSpawnIntervalTicks) {
                int swappedFastest = slowestSpawnIntervalTicks;
                slowestSpawnIntervalTicks = fastestSpawnIntervalTicks;
                fastestSpawnIntervalTicks = swappedFastest;
            }
            killPoints = nonNegativeOrDefault(killPoints, 10);
            assistPoints = nonNegativeOrDefault(assistPoints, 3);
        }
    }

    public static class Armor {
        private Double level1DamageReduction = 0.25D;
        private Double level2DamageReduction = 0.50D;
        private Double level3DamageReduction = 0.75D;

        public Double getLevel1DamageReduction() {
            return level1DamageReduction;
        }

        public void setLevel1DamageReduction(Double level1DamageReduction) {
            this.level1DamageReduction = level1DamageReduction;
        }

        public Double getLevel2DamageReduction() {
            return level2DamageReduction;
        }

        public void setLevel2DamageReduction(Double level2DamageReduction) {
            this.level2DamageReduction = level2DamageReduction;
        }

        public Double getLevel3DamageReduction() {
            return level3DamageReduction;
        }

        public void setLevel3DamageReduction(Double level3DamageReduction) {
            this.level3DamageReduction = level3DamageReduction;
        }

        public double damageTakenMultiplierForLevel(int armorLevel) {
            return 1.0D - switch (armorLevel) {
                case 1 -> validDamageReductionOrDefault(level1DamageReduction, 0.25D);
                case 2 -> validDamageReductionOrDefault(level2DamageReduction, 0.50D);
                case 3 -> validDamageReductionOrDefault(level3DamageReduction, 0.75D);
                default -> 0.0D;
            };
        }

        private void normalize() {
            level1DamageReduction = validDamageReductionOrDefault(level1DamageReduction, 0.25D);
            level2DamageReduction = validDamageReductionOrDefault(level2DamageReduction, 0.50D);
            level3DamageReduction = validDamageReductionOrDefault(level3DamageReduction, 0.75D);
        }
    }

    public static class WeaponWall {
        private Integer refreshIntervalWaves = 5;
        private List<Rarity> rarities = defaultRarities();

        public static WeaponWall defaults() {
            WeaponWall weaponWall = new WeaponWall();
            weaponWall.normalize();
            return weaponWall;
        }

        public Integer getRefreshIntervalWaves() {
            return refreshIntervalWaves;
        }

        public void setRefreshIntervalWaves(Integer refreshIntervalWaves) {
            this.refreshIntervalWaves = refreshIntervalWaves;
        }

        public List<Rarity> getRarities() {
            if (rarities == null) {
                rarities = defaultRarities();
            }
            return rarities;
        }

        public void setRarities(List<Rarity> rarities) {
            this.rarities = rarities == null ? defaultRarities() : new ArrayList<>(rarities);
        }

        private void normalize() {
            refreshIntervalWaves = positiveOrDefault(refreshIntervalWaves, 5);
            List<Rarity> normalized = new ArrayList<>();
            for (Rarity rarity : getRarities()) {
                Rarity resolved = rarity == null ? new Rarity() : rarity;
                resolved.normalize();
                normalized.add(resolved);
            }
            if (normalized.isEmpty()) {
                normalized.addAll(defaultRarities());
            }
            rarities = normalized;
        }

        private static List<Rarity> defaultRarities() {
            return List.of(
                    new Rarity(
                            RARITY_COMMON,
                            70.0D,
                            -8.0D,
                            10.0D,
                            100.0D,
                            500,
                            1.0D,
                            List.of(new GunWeight(DEFAULT_POOL_GLOCK_17, 100.0D))),
                    new Rarity(
                            RARITY_RARE,
                            25.0D,
                            5.0D,
                            0.0D,
                            100.0D,
                            900,
                            1.25D,
                            List.of(new GunWeight(DEFAULT_POOL_AK47, 100.0D))),
                    new Rarity(
                            RARITY_EPIC,
                            5.0D,
                            3.0D,
                            0.0D,
                            100.0D,
                            1500,
                            1.6D,
                            List.of(new GunWeight(DEFAULT_POOL_M4A1, 100.0D))));
        }
    }

    public static class Rarity {
        private String id = RARITY_COMMON;
        private Double initialWeight = 1.0D;
        private Double weightDeltaPerRefresh = 0.0D;
        private Double minWeight = 0.0D;
        private Double maxWeight = 100.0D;
        private Integer price = 500;
        private Double damageMultiplier = 1.0D;
        private List<GunWeight> guns = List.of(new GunWeight(DEFAULT_POOL_GLOCK_17, 1.0D));

        public Rarity() {
        }

        public Rarity(
                String id,
                Double initialWeight,
                Double weightDeltaPerRefresh,
                Double minWeight,
                Double maxWeight,
                Integer price,
                Double damageMultiplier,
                List<GunWeight> guns
        ) {
            this.id = id;
            this.initialWeight = initialWeight;
            this.weightDeltaPerRefresh = weightDeltaPerRefresh;
            this.minWeight = minWeight;
            this.maxWeight = maxWeight;
            this.price = price;
            this.damageMultiplier = damageMultiplier;
            this.guns = guns;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Double getInitialWeight() {
            return initialWeight;
        }

        public void setInitialWeight(Double initialWeight) {
            this.initialWeight = initialWeight;
        }

        public Double getWeightDeltaPerRefresh() {
            return weightDeltaPerRefresh;
        }

        public void setWeightDeltaPerRefresh(Double weightDeltaPerRefresh) {
            this.weightDeltaPerRefresh = weightDeltaPerRefresh;
        }

        public Double getMinWeight() {
            return minWeight;
        }

        public void setMinWeight(Double minWeight) {
            this.minWeight = minWeight;
        }

        public Double getMaxWeight() {
            return maxWeight;
        }

        public void setMaxWeight(Double maxWeight) {
            this.maxWeight = maxWeight;
        }

        public Integer getPrice() {
            return price;
        }

        public void setPrice(Integer price) {
            this.price = price;
        }

        public Double getDamageMultiplier() {
            return damageMultiplier;
        }

        public void setDamageMultiplier(Double damageMultiplier) {
            this.damageMultiplier = damageMultiplier;
        }

        public List<GunWeight> getGuns() {
            if (guns == null) {
                guns = List.of();
            }
            return guns;
        }

        public void setGuns(List<GunWeight> guns) {
            this.guns = guns == null ? List.of() : new ArrayList<>(guns);
        }

        private void normalize() {
            id = Objects.requireNonNullElse(id, "").trim().toLowerCase(Locale.ROOT);
            initialWeight = finiteOrDefault(initialWeight, 0.0D);
            weightDeltaPerRefresh = finiteOrDefault(weightDeltaPerRefresh, 0.0D);
            minWeight = finiteOrDefault(minWeight, 0.0D);
            maxWeight = finiteOrDefault(maxWeight, Math.max(0.0D, minWeight));
            if (maxWeight < minWeight) {
                double temp = minWeight;
                minWeight = maxWeight;
                maxWeight = temp;
            }
            price = nonNegativeOrDefault(price, 0);
            damageMultiplier = positiveFiniteOrDefault(damageMultiplier, 1.0D);
            List<GunWeight> normalizedGuns = new ArrayList<>();
            for (GunWeight gun : getGuns()) {
                GunWeight resolved = gun == null ? new GunWeight() : gun;
                resolved.normalize();
                normalizedGuns.add(resolved);
            }
            guns = normalizedGuns;
        }
    }

    public static class GunWeight {
        private String gunId = "";
        private Double weight = 1.0D;

        public GunWeight() {
        }

        public GunWeight(String gunId, Double weight) {
            this.gunId = gunId;
            this.weight = weight;
        }

        public String getGunId() {
            return gunId;
        }

        public void setGunId(String gunId) {
            this.gunId = gunId;
        }

        public Double getWeight() {
            return weight;
        }

        public void setWeight(Double weight) {
            this.weight = weight;
        }

        private void normalize() {
            gunId = Objects.requireNonNullElse(gunId, "").trim();
            weight = finiteOrDefault(weight, 0.0D);
        }
    }

    public static class UltimateMachine {
        private Integer maxUpgradeLevel = 2;
        private Map<String, UpgradeLevel> levels = defaultLevels();

        public static UltimateMachine defaults() {
            UltimateMachine ultimateMachine = new UltimateMachine();
            ultimateMachine.normalize();
            return ultimateMachine;
        }

        public Integer getMaxUpgradeLevel() {
            return maxUpgradeLevel;
        }

        public void setMaxUpgradeLevel(Integer maxUpgradeLevel) {
            this.maxUpgradeLevel = maxUpgradeLevel;
        }

        public Map<String, UpgradeLevel> getLevels() {
            if (levels == null) {
                levels = defaultLevels();
            }
            return levels;
        }

        public void setLevels(Map<String, UpgradeLevel> levels) {
            this.levels = levels == null ? defaultLevels() : new LinkedHashMap<>(levels);
        }

        private void normalize() {
            maxUpgradeLevel = positiveOrDefault(maxUpgradeLevel, 2);
            Map<String, UpgradeLevel> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, UpgradeLevel> entry : getLevels().entrySet()) {
                String level = Objects.requireNonNullElse(entry.getKey(), "").trim();
                if (level.isBlank()) {
                    continue;
                }
                UpgradeLevel resolved = entry.getValue() == null ? new UpgradeLevel() : entry.getValue();
                resolved.normalize();
                normalized.put(level, resolved);
            }
            if (normalized.isEmpty()) {
                normalized.putAll(defaultLevels());
            }
            levels = normalized;
        }

        private static Map<String, UpgradeLevel> defaultLevels() {
            Map<String, UpgradeLevel> defaults = new LinkedHashMap<>();
            defaults.put("1", new UpgradeLevel(2500, 2.0D));
            defaults.put("2", new UpgradeLevel(5000, 3.0D));
            return defaults;
        }
    }

    public static class UpgradeLevel {
        private Integer cost = 2500;
        private Double damageMultiplier = 2.0D;

        public UpgradeLevel() {
        }

        public UpgradeLevel(Integer cost, Double damageMultiplier) {
            this.cost = cost;
            this.damageMultiplier = damageMultiplier;
        }

        public Integer getCost() {
            return cost;
        }

        public void setCost(Integer cost) {
            this.cost = cost;
        }

        public Double getDamageMultiplier() {
            return damageMultiplier;
        }

        public void setDamageMultiplier(Double damageMultiplier) {
            this.damageMultiplier = damageMultiplier;
        }

        private void normalize() {
            cost = nonNegativeOrDefault(cost, 0);
            damageMultiplier = positiveFiniteOrDefault(damageMultiplier, 1.0D);
        }
    }

    public static class WeaponRules {
        private Integer starterWeaponAmmunitionPerMagazineMultiple = 7;
        private Integer weaponPoolAmmunitionPerMagazineMultiple = 7;

        public Integer getStarterWeaponAmmunitionPerMagazineMultiple() {
            return starterWeaponAmmunitionPerMagazineMultiple;
        }

        public void setStarterWeaponAmmunitionPerMagazineMultiple(Integer starterWeaponAmmunitionPerMagazineMultiple) {
            this.starterWeaponAmmunitionPerMagazineMultiple = starterWeaponAmmunitionPerMagazineMultiple;
        }

        public Integer getWeaponPoolAmmunitionPerMagazineMultiple() {
            return weaponPoolAmmunitionPerMagazineMultiple;
        }

        public void setWeaponPoolAmmunitionPerMagazineMultiple(Integer weaponPoolAmmunitionPerMagazineMultiple) {
            this.weaponPoolAmmunitionPerMagazineMultiple = weaponPoolAmmunitionPerMagazineMultiple;
        }

        private void normalize() {
            starterWeaponAmmunitionPerMagazineMultiple = nonNegativeOrDefault(
                    starterWeaponAmmunitionPerMagazineMultiple,
                    7);
            weaponPoolAmmunitionPerMagazineMultiple = nonNegativeOrDefault(
                    weaponPoolAmmunitionPerMagazineMultiple,
                    7);
        }
    }

    public static class SpawnPointWeighting {
        private Boolean enabled = true;
        private Double tooCloseDistance = 8.0D;
        private Double idealMinDistance = 24.0D;
        private Double idealMaxDistance = 56.0D;
        private Double farDistance = 112.0D;
        private Double minMultiplier = 0.65D;
        private Double idealMultiplier = 1.15D;
        private Double farMultiplier = 0.85D;
        private Double maxMultiplier = 1.20D;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Double getTooCloseDistance() {
            return tooCloseDistance;
        }

        public void setTooCloseDistance(Double tooCloseDistance) {
            this.tooCloseDistance = tooCloseDistance;
        }

        public Double getIdealMinDistance() {
            return idealMinDistance;
        }

        public void setIdealMinDistance(Double idealMinDistance) {
            this.idealMinDistance = idealMinDistance;
        }

        public Double getIdealMaxDistance() {
            return idealMaxDistance;
        }

        public void setIdealMaxDistance(Double idealMaxDistance) {
            this.idealMaxDistance = idealMaxDistance;
        }

        public Double getFarDistance() {
            return farDistance;
        }

        public void setFarDistance(Double farDistance) {
            this.farDistance = farDistance;
        }

        public Double getMinMultiplier() {
            return minMultiplier;
        }

        public void setMinMultiplier(Double minMultiplier) {
            this.minMultiplier = minMultiplier;
        }

        public Double getIdealMultiplier() {
            return idealMultiplier;
        }

        public void setIdealMultiplier(Double idealMultiplier) {
            this.idealMultiplier = idealMultiplier;
        }

        public Double getFarMultiplier() {
            return farMultiplier;
        }

        public void setFarMultiplier(Double farMultiplier) {
            this.farMultiplier = farMultiplier;
        }

        public Double getMaxMultiplier() {
            return maxMultiplier;
        }

        public void setMaxMultiplier(Double maxMultiplier) {
            this.maxMultiplier = maxMultiplier;
        }

        private void normalize() {
            enabled = enabled == null || enabled;
            tooCloseDistance = positiveFiniteOrDefault(tooCloseDistance, 8.0D);
            idealMinDistance = positiveFiniteOrDefault(idealMinDistance, 24.0D);
            idealMaxDistance = positiveFiniteOrDefault(idealMaxDistance, 56.0D);
            farDistance = positiveFiniteOrDefault(farDistance, 112.0D);
            if (idealMinDistance < tooCloseDistance) {
                idealMinDistance = tooCloseDistance;
            }
            if (idealMaxDistance < idealMinDistance) {
                idealMaxDistance = idealMinDistance;
            }
            if (farDistance < idealMaxDistance) {
                farDistance = idealMaxDistance;
            }
            minMultiplier = positiveFiniteOrDefault(minMultiplier, 0.65D);
            idealMultiplier = positiveFiniteOrDefault(idealMultiplier, 1.15D);
            farMultiplier = positiveFiniteOrDefault(farMultiplier, 0.85D);
            maxMultiplier = positiveFiniteOrDefault(maxMultiplier, 1.20D);
            if (minMultiplier > maxMultiplier) {
                double swappedMin = maxMultiplier;
                maxMultiplier = minMultiplier;
                minMultiplier = swappedMin;
            }
            idealMultiplier = clampDouble(idealMultiplier, minMultiplier, maxMultiplier);
            farMultiplier = clampDouble(farMultiplier, minMultiplier, maxMultiplier);
        }
    }

    private static int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private static int nonNegativeOrDefault(Integer value, int defaultValue) {
        return value == null || value < 0 ? defaultValue : value;
    }

    private static int clampPercent(Integer value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(1, Math.min(100, value));
    }

    private static double positiveFiniteOrDefault(Double value, double defaultValue) {
        return value == null || !Double.isFinite(value) || value <= 0.0 ? defaultValue : value;
    }

    private static double validDamageReductionOrDefault(Double value, double defaultValue) {
        return value == null || !Double.isFinite(value) || value < 0.0D || value >= 1.0D ? defaultValue : value;
    }

    private static double finiteOrDefault(Double value, double defaultValue) {
        return value == null || !Double.isFinite(value) ? defaultValue : value;
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
