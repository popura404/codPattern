package com.cdp.codpattern.app.zombies.service;

import java.util.Objects;
import java.util.UUID;

/**
 * Pure shared room power state. Minecraft block state/redstone output is handled by integrators.
 */
public final class ZombiesPowerService {
    private final ZombiesEconomyService economyService;
    private final PowerSwitchStateSink powerSwitchStateSink;
    private boolean powerOn;

    public ZombiesPowerService(ZombiesEconomyService economyService) {
        this(economyService, PowerSwitchStateSink.noop());
    }

    public ZombiesPowerService(
            ZombiesEconomyService economyService,
            PowerSwitchStateSink powerSwitchStateSink
    ) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.powerSwitchStateSink = powerSwitchStateSink == null ? PowerSwitchStateSink.noop() : powerSwitchStateSink;
    }

    public synchronized boolean isPowerOn() {
        return powerOn;
    }

    public void reset() {
        synchronized (this) {
            powerOn = false;
        }
        syncPowerSwitchState(false);
    }

    public ZombiesServiceResult<PowerPurchaseResult> turnOn(UUID playerId, double cost) {
        synchronized (this) {
            if (powerOn) {
                return ZombiesServiceResult.failure(ZombiesErrorCode.POWER_ALREADY_ON);
            }
        }

        return economyService.spendAtomically(playerId, cost, ignoredState -> {
            synchronized (this) {
                if (powerOn) {
                    return ZombiesServiceResult.failure(ZombiesErrorCode.POWER_ALREADY_ON);
                }
                powerOn = true;
                syncPowerSwitchState(true);
                return ZombiesServiceResult.success(new PowerPurchaseResult(true, cost));
            }
        });
    }

    private void syncPowerSwitchState(boolean powered) {
        try {
            powerSwitchStateSink.setPowered(powered);
        } catch (RuntimeException ignored) {
            // Block sync is best-effort; room power state and economy results must remain authoritative.
        }
    }

    @FunctionalInterface
    public interface PowerSwitchStateSink {
        void setPowered(boolean powered);

        static PowerSwitchStateSink noop() {
            return ignored -> {
            };
        }
    }

    public record PowerPurchaseResult(
            boolean powerOn,
            double cost
    ) {
        public PowerPurchaseResult {
            cost = Math.max(0.0D, cost);
        }
    }
}
