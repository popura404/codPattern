package com.cdp.codpattern.app.match.runtime.player;

import com.cdp.codpattern.app.match.extension.ModePlayerLoginContributor;
import net.minecraft.server.level.ServerPlayer;

/** Combined-distribution holder for login contributors installed by the composition shim. */
public final class ModePlayerLoginContributors {
    private static final ModePlayerLoginRouter ROUTER = new ModePlayerLoginRouter();

    private ModePlayerLoginContributors() {
    }

    public static void register(ModePlayerLoginContributor contributor) {
        ROUTER.register(contributor);
    }

    public static ModePlayerLoginContributor.LoginDisposition route(ServerPlayer player) {
        return ROUTER.route(player);
    }
}
