package com.cdp.codpattern.compat.fpsmatch;

public final class FpsMatchGatewayProvider {
    private static final FpsMatchGateway ACTIVE_GATEWAY = new FpsMatchCoreGateway();

    private FpsMatchGatewayProvider() {
    }

    public static FpsMatchGateway gateway() {
        return ACTIVE_GATEWAY;
    }
}
