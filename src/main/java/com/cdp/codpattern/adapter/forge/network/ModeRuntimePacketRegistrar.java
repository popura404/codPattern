package com.cdp.codpattern.adapter.forge.network;

import com.cdp.codpattern.network.match.CombatMarkerConfigPacket;
import com.cdp.codpattern.network.match.CountdownPacket;
import com.cdp.codpattern.network.match.DeathCamPacket;
import com.cdp.codpattern.network.match.GamePhasePacket;
import com.cdp.codpattern.network.match.KillFeedPacket;
import com.cdp.codpattern.network.match.ModeObjectStateSyncPacket;
import com.cdp.codpattern.network.match.ModeRuntimeStatePacket;
import com.cdp.codpattern.network.match.PhysicsMobRetainPacket;
import com.cdp.codpattern.network.match.ScoreUpdatePacket;
import net.minecraftforge.network.NetworkDirection;

final class ModeRuntimePacketRegistrar {
    private ModeRuntimePacketRegistrar() {
    }

    static void register() {
        ModNetworkChannel.CHANNEL.messageBuilder(DeathCamPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(DeathCamPacket::decode)
                .encoder(DeathCamPacket::encode)
                .consumerMainThread(DeathCamPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(KillFeedPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(KillFeedPacket::decode)
                .encoder(KillFeedPacket::encode)
                .consumerMainThread(KillFeedPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(PhysicsMobRetainPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PhysicsMobRetainPacket::decode)
                .encoder(PhysicsMobRetainPacket::encode)
                .consumerMainThread(PhysicsMobRetainPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(GamePhasePacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(GamePhasePacket::decode)
                .encoder(GamePhasePacket::encode)
                .consumerMainThread(GamePhasePacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(CountdownPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(CountdownPacket::decode)
                .encoder(CountdownPacket::encode)
                .consumerMainThread(CountdownPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(ScoreUpdatePacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ScoreUpdatePacket::decode)
                .encoder(ScoreUpdatePacket::encode)
                .consumerMainThread(ScoreUpdatePacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(CombatMarkerConfigPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(CombatMarkerConfigPacket::decode)
                .encoder(CombatMarkerConfigPacket::encode)
                .consumerMainThread(CombatMarkerConfigPacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(ModeRuntimeStatePacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ModeRuntimeStatePacket::decode)
                .encoder(ModeRuntimeStatePacket::encode)
                .consumerMainThread(ModeRuntimeStatePacket::handle)
                .add();

        ModNetworkChannel.CHANNEL.messageBuilder(ModeObjectStateSyncPacket.class, ModNetworkChannel.nextMessageId(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ModeObjectStateSyncPacket::decode)
                .encoder(ModeObjectStateSyncPacket::encode)
                .consumerMainThread(ModeObjectStateSyncPacket::handle)
                .add();
    }
}
