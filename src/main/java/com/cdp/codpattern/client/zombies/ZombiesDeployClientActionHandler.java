package com.cdp.codpattern.client.zombies;

import com.cdp.codpattern.client.gui.screen.zombies.deploy.ZombiesDeployToolScreen;
import com.phasetranscrystal.fpsmatch.common.packet.OpenZombiesDeployToolScreenS2CPacket;
import net.minecraft.client.Minecraft;

/** Client-only screen handler for the opaque Zombies deploy-tool action route. */
public final class ZombiesDeployClientActionHandler {
    private ZombiesDeployClientActionHandler() {
    }

    public static void handle(Object payload) {
        if (!(payload instanceof OpenZombiesDeployToolScreenS2CPacket packet)) {
            throw new IllegalArgumentException("Unexpected Zombies deploy screen payload: " + payload);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ZombiesDeployToolScreen screen) {
            screen.applyData(packet);
        } else {
            minecraft.setScreen(new ZombiesDeployToolScreen(packet));
        }
    }
}
