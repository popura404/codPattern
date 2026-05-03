package com.cdp.codpattern.app.zombies.model;

import com.cdp.codpattern.config.zombies.ZombiesRulesConfig;

public class ZombiesWaveMobEntry {
    private String entity;
    // Optional example/documentation metadata; validation and spawn logic ignore it.
    private Object description;
    private int count;
    private Integer killPoints;
    private Integer assistPoints;

    private transient ZombiesRulesConfig.Defaults defaults;

    public String getEntity() {
        return entity;
    }

    public Object getDescription() {
        return description;
    }

    public int getCount() {
        return count;
    }

    public int getKillPoints() {
        return killPoints != null ? killPoints : defaultKillPoints();
    }

    public int getAssistPoints() {
        return assistPoints != null ? assistPoints : defaultAssistPoints();
    }

    public Integer getConfiguredKillPoints() {
        return killPoints;
    }

    public Integer getConfiguredAssistPoints() {
        return assistPoints;
    }

    public void applyDefaults(ZombiesRulesConfig.Defaults defaults) {
        this.defaults = defaults != null ? defaults : new ZombiesRulesConfig.Defaults();
    }

    private int defaultKillPoints() {
        return nonNegativeOrDefault(defaults == null ? null : defaults.getKillPoints(), 10);
    }

    private int defaultAssistPoints() {
        return nonNegativeOrDefault(defaults == null ? null : defaults.getAssistPoints(), 3);
    }

    private static int nonNegativeOrDefault(Integer value, int defaultValue) {
        return value == null || value < 0 ? defaultValue : value;
    }
}
