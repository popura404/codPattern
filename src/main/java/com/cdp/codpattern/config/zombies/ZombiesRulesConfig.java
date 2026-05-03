package com.cdp.codpattern.config.zombies;

public class ZombiesRulesConfig {
    public static final String DEAD_PLAYER_POLICY_SPECTATE_UNTIL_INTERMISSION = "spectate_until_wave_intermission";

    private Room room = new Room();
    private Defaults defaults = new Defaults();

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

    public void normalize() {
        setRoom(room);
        setDefaults(defaults);
        room.normalize();
        defaults.normalize();
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
        private Integer spawnIntervalTicks = 40;
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
            spawnIntervalTicks = positiveOrDefault(spawnIntervalTicks, 40);
            killPoints = nonNegativeOrDefault(killPoints, 10);
            assistPoints = nonNegativeOrDefault(assistPoints, 3);
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
}
