package com.cdp.codpattern.app.match.extension;

/** Registration callback installed into one deterministic legacy packet slot. */
@FunctionalInterface
public interface ModeNetworkPacketRegistration {
    void register();
}
