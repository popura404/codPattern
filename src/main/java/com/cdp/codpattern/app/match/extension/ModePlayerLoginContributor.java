package com.cdp.codpattern.app.match.extension;

import net.minecraft.server.level.ServerPlayer;

/** Public extension invoked for mode-owned login recovery before shared FPSMatch login handling. */
public interface ModePlayerLoginContributor {
    String id();

    default int order() {
        return 0;
    }

    LoginDisposition onPlayerLogin(ServerPlayer player);

    enum LoginDisposition {
        CONTINUE,
        STOP_SHARED_LOGIN
    }
}
