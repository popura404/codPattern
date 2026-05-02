package com.cdp.codpattern.network.match;

import com.cdp.codpattern.app.match.model.MetricDisplay;
import com.cdp.codpattern.app.match.model.ModePlayerValue;
import com.cdp.codpattern.app.match.model.ModePrompt;
import com.cdp.codpattern.app.match.model.ModeRuntimeStateSnapshot;
import com.cdp.codpattern.app.match.model.RoomSummaryMetric;
import com.cdp.codpattern.network.handler.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ModeRuntimeStatePacket {
    private final ModeRuntimeStateSnapshot snapshot;

    public ModeRuntimeStatePacket(ModeRuntimeStateSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(snapshot.roomKey());
        buf.writeUtf(snapshot.phaseKey());
        buf.writeVarInt(snapshot.remainingTimeTicks());
        buf.writeVarInt(snapshot.metrics().size());
        for (RoomSummaryMetric metric : snapshot.metrics()) {
            buf.writeUtf(metric.key());
            buf.writeUtf(metric.translationKey());
            buf.writeVarInt(metric.value());
            buf.writeEnum(metric.display());
        }
        buf.writeVarInt(snapshot.playerValues().size());
        for (Map.Entry<String, ModePlayerValue> entry : snapshot.playerValues().entrySet()) {
            buf.writeUtf(entry.getKey());
            entry.getValue().write(buf);
        }
        buf.writeVarInt(snapshot.prompts().size());
        for (ModePrompt prompt : snapshot.prompts()) {
            buf.writeUtf(prompt.key());
            buf.writeUtf(prompt.translationKey());
        }
        buf.writeVarLong(snapshot.revision());
    }

    public static ModeRuntimeStatePacket decode(FriendlyByteBuf buf) {
        String roomKey = buf.readUtf();
        String phaseKey = buf.readUtf();
        int remainingTimeTicks = buf.readVarInt();
        int metricCount = Math.max(0, buf.readVarInt());
        List<RoomSummaryMetric> metrics = new java.util.ArrayList<>(metricCount);
        for (int i = 0; i < metricCount; i++) {
            metrics.add(new RoomSummaryMetric(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readEnum(MetricDisplay.class)));
        }
        int playerValueCount = Math.max(0, buf.readVarInt());
        Map<String, ModePlayerValue> playerValues = new LinkedHashMap<>();
        for (int i = 0; i < playerValueCount; i++) {
            playerValues.put(buf.readUtf(), ModePlayerValue.read(buf));
        }
        int promptCount = Math.max(0, buf.readVarInt());
        List<ModePrompt> prompts = new java.util.ArrayList<>(promptCount);
        for (int i = 0; i < promptCount; i++) {
            prompts.add(new ModePrompt(buf.readUtf(), buf.readUtf()));
        }
        long revision = buf.readVarLong();
        return new ModeRuntimeStatePacket(new ModeRuntimeStateSnapshot(
                roomKey,
                phaseKey,
                remainingTimeTicks,
                metrics,
                playerValues,
                prompts,
                revision));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPacketHandler.handleModeRuntimeState(snapshot));
        ctx.get().setPacketHandled(true);
    }
}
