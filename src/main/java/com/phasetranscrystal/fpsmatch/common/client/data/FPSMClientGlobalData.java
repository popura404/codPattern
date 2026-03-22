package com.phasetranscrystal.fpsmatch.common.client.data;

public class FPSMClientGlobalData {
    private final DebugData debugData = new DebugData();

    public DebugData getDebugData() {
        return debugData;
    }

    public void reset() {
        debugData.clearAll();
    }
}
