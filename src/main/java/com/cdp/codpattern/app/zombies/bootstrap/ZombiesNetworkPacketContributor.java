package com.cdp.codpattern.app.zombies.bootstrap;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.app.match.runtime.network.ModeNetworkPacketContributions;
import com.cdp.codpattern.app.match.runtime.network.ModeNetworkPacketSlots;
import com.phasetranscrystal.fpsmatch.common.packet.OpenZombiesDeployToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.ZombiesDeployToolActionC2SPacket;
import net.minecraftforge.network.NetworkDirection;

/** Installs the two Zombies packet codecs into their fixed legacy discriminator slots. */
public final class ZombiesNetworkPacketContributor {
    private ZombiesNetworkPacketContributor() {
    }

    public static void install() {
        ModeNetworkPacketContributions.install(ModeNetworkPacketSlots.FPSM_MODE_TOOL_ACTION, () ->
                ModNetworkChannel.channel().messageBuilder(
                                ZombiesDeployToolActionC2SPacket.class,
                                ModNetworkChannel.nextMessageId(),
                                NetworkDirection.PLAY_TO_SERVER)
                        .decoder(ZombiesDeployToolActionC2SPacket::decode)
                        .encoder(ZombiesDeployToolActionC2SPacket::encode)
                        .consumerMainThread(ZombiesDeployToolActionC2SPacket::handle)
                        .add());
        ModeNetworkPacketContributions.install(ModeNetworkPacketSlots.FPSM_MODE_TOOL_SCREEN, () ->
                ModNetworkChannel.channel().messageBuilder(
                                OpenZombiesDeployToolScreenS2CPacket.class,
                                ModNetworkChannel.nextMessageId(),
                                NetworkDirection.PLAY_TO_CLIENT)
                        .decoder(OpenZombiesDeployToolScreenS2CPacket::decode)
                        .encoder(OpenZombiesDeployToolScreenS2CPacket::encode)
                        .consumerMainThread(OpenZombiesDeployToolScreenS2CPacket::handle)
                        .add());
    }
}
