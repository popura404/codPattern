package com.cdp.codpattern.compat.lrtactical;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

public final class LrTacticalGatewayProvider {
    public static final String MOD_ID = "lrtactical";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LrTacticalGateway NOOP_GATEWAY = new NoopLrTacticalGateway();
    private static volatile LrTacticalGateway activeGateway;

    private LrTacticalGatewayProvider() {
    }

    public static LrTacticalGateway gateway() {
        LrTacticalGateway gateway = activeGateway;
        if (gateway != null && (gateway.isLoaded() || !isModLoaded())) {
            return gateway;
        }
        gateway = resolveGateway();
        activeGateway = gateway;
        return gateway;
    }

    private static LrTacticalGateway resolveGateway() {
        if (!isModLoaded()) {
            return NOOP_GATEWAY;
        }
        try {
            return new LrTacticalCoreGateway();
        } catch (LinkageError | RuntimeException e) {
            LOGGER.warn("LR Tactical is loaded but its API gateway could not be initialized; LR features are disabled.", e);
            return NOOP_GATEWAY;
        }
    }

    private static boolean isModLoaded() {
        try {
            return ModList.get().isLoaded(MOD_ID);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
