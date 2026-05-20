package com.cdp.codpattern.app.zombies.model;

import com.cdp.codpattern.config.zombies.ZombiesRulesConfig;
import net.minecraft.util.RandomSource;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class ZombiesWaveDefinition {
    private Integer wave;
    // Optional example/documentation metadata; validation and runtime wave logic ignore it.
    private Object description;
    private Double healthMultiplier;
    private Double damageMultiplier;
    private Double speedMultiplier;
    private Integer maxAlive;
    private Integer fastestSpawnIntervalTicks;
    private Integer slowestSpawnIntervalTicks;
    // Legacy fixed interval. New files should use fastest/slowest spawn interval bounds.
    private Integer spawnIntervalTicks;
    private List<ZombiesWaveMobEntry> mobs;

    private transient int fileWave;
    private transient Path sourcePath;
    private transient boolean mobsFieldPresent;
    private transient ZombiesRulesConfig.Defaults defaults;

    public Integer getConfiguredWave() {
        return wave;
    }

    public int getWave() {
        return wave != null ? wave : fileWave;
    }

    public Object getDescription() {
        return description;
    }

    public Double getHealthMultiplier() {
        return healthMultiplier != null ? healthMultiplier : defaultHealthMultiplier();
    }

    public Double getDamageMultiplier() {
        return damageMultiplier != null ? damageMultiplier : defaultDamageMultiplier();
    }

    public Double getSpeedMultiplier() {
        return speedMultiplier != null ? speedMultiplier : defaultSpeedMultiplier();
    }

    public Integer getMaxAlive() {
        return maxAlive != null ? maxAlive : defaultMaxAlive();
    }

    public Integer getSpawnIntervalTicks() {
        return getFastestSpawnIntervalTicks();
    }

    public Integer getFastestSpawnIntervalTicks() {
        if (fastestSpawnIntervalTicks != null) {
            return fastestSpawnIntervalTicks;
        }
        if (spawnIntervalTicks != null) {
            return spawnIntervalTicks;
        }
        return defaultFastestSpawnIntervalTicks();
    }

    public Integer getSlowestSpawnIntervalTicks() {
        if (slowestSpawnIntervalTicks != null) {
            return slowestSpawnIntervalTicks;
        }
        if (spawnIntervalTicks != null) {
            return spawnIntervalTicks;
        }
        return defaultSlowestSpawnIntervalTicks();
    }

    public Double getConfiguredHealthMultiplier() {
        return healthMultiplier;
    }

    public Double getConfiguredDamageMultiplier() {
        return damageMultiplier;
    }

    public Double getConfiguredSpeedMultiplier() {
        return speedMultiplier;
    }

    public Integer getConfiguredMaxAlive() {
        return maxAlive;
    }

    public Integer getConfiguredSpawnIntervalTicks() {
        return spawnIntervalTicks;
    }

    public Integer getConfiguredFastestSpawnIntervalTicks() {
        return fastestSpawnIntervalTicks;
    }

    public Integer getConfiguredSlowestSpawnIntervalTicks() {
        return slowestSpawnIntervalTicks;
    }

    public int chooseSpawnIntervalTicks(RandomSource random) {
        int fastest = Math.max(1, getFastestSpawnIntervalTicks());
        int slowest = Math.max(1, getSlowestSpawnIntervalTicks());
        if (slowest < fastest) {
            int swappedFastest = slowest;
            slowest = fastest;
            fastest = swappedFastest;
        }
        if (slowest == fastest) {
            return fastest;
        }
        RandomSource safeRandom = random == null ? RandomSource.create() : random;
        return fastest + safeRandom.nextInt(slowest - fastest + 1);
    }

    public List<ZombiesWaveMobEntry> getMobs() {
        return mobs == null ? Collections.emptyList() : Collections.unmodifiableList(mobs);
    }

    public boolean hasMobsField() {
        return mobsFieldPresent;
    }

    public int getFileWave() {
        return fileWave;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public boolean hasWaveConflict() {
        return wave != null && wave != fileWave;
    }

    public boolean isEmptyWave() {
        return hasMobsField() && totalMobCount() == 0;
    }

    public int totalMobCount() {
        int total = 0;
        for (ZombiesWaveMobEntry mob : getMobs()) {
            if (mob != null && mob.getCount() > 0) {
                total += mob.getCount();
            }
        }
        return total;
    }

    public void attachSource(Path sourcePath, int fileWave, boolean mobsFieldPresent) {
        this.sourcePath = sourcePath;
        this.fileWave = fileWave;
        this.mobsFieldPresent = mobsFieldPresent;
    }

    public void applyDefaults(ZombiesRulesConfig.Defaults defaults) {
        this.defaults = defaults != null ? defaults : new ZombiesRulesConfig.Defaults();
        for (ZombiesWaveMobEntry mob : getMobs()) {
            if (mob != null) {
                mob.applyDefaults(this.defaults);
            }
        }
    }

    private double defaultHealthMultiplier() {
        return positiveFiniteOrDefault(defaults == null ? null : defaults.getHealthMultiplier(), 1.0);
    }

    private double defaultDamageMultiplier() {
        return positiveFiniteOrDefault(defaults == null ? null : defaults.getDamageMultiplier(), 1.0);
    }

    private double defaultSpeedMultiplier() {
        return positiveFiniteOrDefault(defaults == null ? null : defaults.getSpeedMultiplier(), 1.0);
    }

    private int defaultMaxAlive() {
        return positiveOrDefault(defaults == null ? null : defaults.getMaxAlive(), 8);
    }

    private int defaultFastestSpawnIntervalTicks() {
        if (defaults == null) {
            return 20;
        }
        Integer fastestInterval = defaults.getFastestSpawnIntervalTicks();
        return positiveOrDefault(fastestInterval != null ? fastestInterval : defaults.getSpawnIntervalTicks(), 20);
    }

    private int defaultSlowestSpawnIntervalTicks() {
        if (defaults == null) {
            return 50;
        }
        Integer slowestInterval = defaults.getSlowestSpawnIntervalTicks();
        return positiveOrDefault(slowestInterval != null ? slowestInterval : defaults.getSpawnIntervalTicks(), 50);
    }

    private static int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private static double positiveFiniteOrDefault(Double value, double defaultValue) {
        return value == null || !Double.isFinite(value) || value <= 0.0 ? defaultValue : value;
    }
}
