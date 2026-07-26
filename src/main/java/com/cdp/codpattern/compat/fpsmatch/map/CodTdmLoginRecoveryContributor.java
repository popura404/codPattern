package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.match.extension.ModePlayerLoginContributor;
import net.minecraft.server.level.ServerPlayer;

/** Applies the existing one-shot TDM deferred-leave recovery. */
public final class CodTdmLoginRecoveryContributor implements ModePlayerLoginContributor {
    @Override
    public String id() {
        return "tdm-deferred-leave";
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public LoginDisposition onPlayerLogin(ServerPlayer player) {
        return CodTdmDeferredLeaveRegistry.applyIfPresent(player)
                ? LoginDisposition.STOP_SHARED_LOGIN
                : LoginDisposition.CONTINUE;
    }
}
