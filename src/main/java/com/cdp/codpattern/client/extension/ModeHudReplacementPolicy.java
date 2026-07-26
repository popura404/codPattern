package com.cdp.codpattern.client.extension;

/** Client-only policy declaring that the mode HUD replaces selected vanilla overlays. */
public interface ModeHudReplacementPolicy {
    String id();

    default int order() {
        return 0;
    }

    boolean shouldReplaceVanillaPlayerHud();
}
