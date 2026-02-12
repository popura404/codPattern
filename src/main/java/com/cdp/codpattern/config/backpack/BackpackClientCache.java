package com.cdp.codpattern.config.backpack;

public final class BackpackClientCache {
    private static BackpackConfig.PlayerBackpackData playerData;

    private BackpackClientCache() {
    }

    public static void set(BackpackConfig.PlayerBackpackData data) {
        playerData = data;
    }

    public static BackpackConfig.PlayerBackpackData get() {
        return playerData;
    }

    public static void clear() {
        playerData = null;
    }
}
