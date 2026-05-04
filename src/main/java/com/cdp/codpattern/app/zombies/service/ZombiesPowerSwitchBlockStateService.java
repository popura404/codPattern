package com.cdp.codpattern.app.zombies.service;

import com.cdp.codpattern.app.zombies.map.object.ZombiesPowerSwitchData;
import com.cdp.codpattern.common.block.ZombiesPowerSwitchBlock;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Optional;

public final class ZombiesPowerSwitchBlockStateService {
    static final String POWER_SWITCH_BLOCK_ID = "codpattern:zombies_power_switch";

    private final LevelResolver levelResolver;

    public ZombiesPowerSwitchBlockStateService(LevelResolver levelResolver) {
        this.levelResolver = levelResolver;
    }

    public boolean setPowered(Optional<ZombiesPowerSwitchData> powerSwitch, boolean powered) {
        if (powerSwitch == null || powerSwitch.isEmpty()) {
            return false;
        }
        return setPowered(powerSwitch.get(), powered);
    }

    public boolean setPowered(ZombiesPowerSwitchData powerSwitch, boolean powered) {
        if (!isSupportedPowerSwitch(powerSwitch) || levelResolver == null) {
            return false;
        }
        try {
            ServerLevel level = levelResolver.level(powerSwitch.dimension());
            if (level == null) {
                return false;
            }
            return ZombiesPowerSwitchBlock.setPowered(level, powerSwitch.pos(), powered);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean isSupportedPowerSwitch(ZombiesPowerSwitchData powerSwitch) {
        return powerSwitch != null
                && POWER_SWITCH_BLOCK_ID.equals(Objects.requireNonNullElse(powerSwitch.block(), "").trim());
    }

    @FunctionalInterface
    public interface LevelResolver {
        ServerLevel level(ResourceKey<Level> dimension);
    }
}
