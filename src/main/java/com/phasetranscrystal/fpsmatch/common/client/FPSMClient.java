package com.phasetranscrystal.fpsmatch.common.client;

import com.phasetranscrystal.fpsmatch.common.client.data.FPSMClientGlobalData;

public final class FPSMClient {
    private static final FPSMClientGlobalData DATA = new FPSMClientGlobalData();

    private FPSMClient() {
    }

    public static FPSMClientGlobalData getGlobalData() {
        return DATA;
    }

    public static void reset() {
        DATA.reset();
    }
}
