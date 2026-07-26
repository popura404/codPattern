package com.cdp.codpattern.client.bootstrap;

import com.cdp.codpattern.client.extension.ModeGuiOverlayContributor;
import com.cdp.codpattern.client.extension.ModeHudReplacementPolicy;
import com.cdp.codpattern.client.gui.overlay.TdmHudOverlay;
import com.cdp.codpattern.client.runtime.ModeGuiOverlayContributors;
import com.cdp.codpattern.client.runtime.ModeHudReplacementPolicies;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/** Client-only core contributions installed during combined mod construction. */
public final class CoreClientBootstrap {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private CoreClientBootstrap() {
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        ModeGuiOverlayContributors.register(new ModeGuiOverlayContributor() {
            @Override
            public String id() {
                return "tdm_hud";
            }

            @Override
            public int order() {
                return 10;
            }

            @Override
            public void register(RegisterGuiOverlaysEvent event) {
                event.registerAboveAll("tdm_hud", TdmHudOverlay.INSTANCE);
            }
        });
        ModeHudReplacementPolicies.register(new ModeHudReplacementPolicy() {
            @Override
            public String id() {
                return "tdm";
            }

            @Override
            public int order() {
                return 10;
            }

            @Override
            public boolean shouldReplaceVanillaPlayerHud() {
                return TdmHudOverlay.shouldReplaceVanillaPlayerHud();
            }
        });
    }
}
