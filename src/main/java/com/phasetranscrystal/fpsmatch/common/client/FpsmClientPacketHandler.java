package com.phasetranscrystal.fpsmatch.common.client;

import com.cdp.codpattern.client.gui.screen.zombies.deploy.ZombiesDeployToolScreen;
import com.phasetranscrystal.fpsmatch.common.client.screen.MapCreatorToolScreen;
import com.phasetranscrystal.fpsmatch.common.client.screen.SpawnPointToolScreen;
import com.phasetranscrystal.fpsmatch.common.packet.OpenMapCreatorToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.OpenSpawnPointToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.OpenZombiesDeployToolScreenS2CPacket;
import net.minecraft.client.Minecraft;

public final class FpsmClientPacketHandler {
    private FpsmClientPacketHandler() {
    }

    public static void handleOpenMapCreatorToolScreen(OpenMapCreatorToolScreenS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MapCreatorToolScreen screen) {
            screen.applyData(packet);
        } else {
            minecraft.setScreen(new MapCreatorToolScreen(packet));
        }
    }

    public static void handleOpenSpawnPointToolScreen(OpenSpawnPointToolScreenS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SpawnPointToolScreen screen) {
            screen.applyData(packet);
        } else {
            minecraft.setScreen(new SpawnPointToolScreen(packet));
        }
    }

    public static void handleOpenZombiesDeployToolScreen(OpenZombiesDeployToolScreenS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ZombiesDeployToolScreen screen) {
            screen.applyData(packet);
        } else {
            minecraft.setScreen(new ZombiesDeployToolScreen(packet));
        }
    }
}
