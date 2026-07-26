package com.cdp.codpattern.app.zombies.bootstrap;

import com.cdp.codpattern.client.extension.ModeGuiOverlayContributor;
import com.cdp.codpattern.client.extension.ModeHudReplacementPolicy;
import com.cdp.codpattern.client.gui.overlay.ZombiesHudOverlay;
import com.cdp.codpattern.client.runtime.ModeClientActionHandlers;
import com.cdp.codpattern.client.runtime.ModeGuiOverlayContributors;
import com.cdp.codpattern.client.runtime.ModeHudReplacementPolicies;
import com.cdp.codpattern.client.zombies.ClientZombiesState;
import com.cdp.codpattern.client.zombies.ZombiesDeployClientActionHandler;
import com.phasetranscrystal.fpsmatch.common.packet.OpenZombiesDeployToolScreenS2CPacket;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/** Client-only Zombies contributions installed without loading client classes on a dedicated server. */
public final class ZombiesClientBootstrap {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private ZombiesClientBootstrap() {
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        ModeClientActionHandlers.register(
                OpenZombiesDeployToolScreenS2CPacket.CLIENT_ACTION_ID,
                ZombiesDeployClientActionHandler::handle);
        ModeGuiOverlayContributors.register(new ModeGuiOverlayContributor() {
            @Override
            public String id() {
                return "zombies_hud";
            }

            @Override
            public int order() {
                return 20;
            }

            @Override
            public void register(RegisterGuiOverlaysEvent event) {
                event.registerAboveAll("zombies_hud", ZombiesHudOverlay.INSTANCE);
            }
        });
        ModeHudReplacementPolicies.register(new ModeHudReplacementPolicy() {
            @Override
            public String id() {
                return "zombies";
            }

            @Override
            public int order() {
                return 20;
            }

            @Override
            public boolean shouldReplaceVanillaPlayerHud() {
                return ClientZombiesState.shouldReplaceVanillaPlayerHud();
            }
        });
    }
}
