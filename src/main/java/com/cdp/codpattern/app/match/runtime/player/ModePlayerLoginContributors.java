package com.cdp.codpattern.app.match.runtime.player;

import com.cdp.codpattern.app.match.extension.ModePlayerLoginContributor;
import com.cdp.codpattern.app.match.ModeModules;
import net.minecraft.server.level.ServerPlayer;

/** Read-only login route over the frozen mode catalog. */
public final class ModePlayerLoginContributors {
    private ModePlayerLoginContributors() {
    }

    public static ModePlayerLoginContributor.LoginDisposition route(ServerPlayer player) {
        for (ModePlayerLoginContributor contributor : ModeModules.catalog().playerLoginContributors()) {
            ModePlayerLoginContributor.LoginDisposition disposition = contributor.onPlayerLogin(player);
            if (disposition == ModePlayerLoginContributor.LoginDisposition.STOP_SHARED_LOGIN) {
                return disposition;
            }
        }
        return ModePlayerLoginContributor.LoginDisposition.CONTINUE;
    }
}
