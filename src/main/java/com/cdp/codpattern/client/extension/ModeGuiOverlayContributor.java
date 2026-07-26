package com.cdp.codpattern.client.extension;

import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;

/** Client-only overlay registration contribution. */
public interface ModeGuiOverlayContributor {
    String id();

    default int order() {
        return 0;
    }

    void register(RegisterGuiOverlaysEvent event);
}
