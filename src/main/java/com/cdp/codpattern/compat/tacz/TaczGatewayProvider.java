package com.cdp.codpattern.compat.tacz;

public final class TaczGatewayProvider {
    private static final TaczGateway ACTIVE_GATEWAY = new TaczCoreGateway();

    private TaczGatewayProvider() {
    }

    public static TaczGateway gateway() {
        return ACTIVE_GATEWAY;
    }
}
