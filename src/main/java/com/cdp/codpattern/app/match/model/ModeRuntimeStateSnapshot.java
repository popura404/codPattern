package com.cdp.codpattern.app.match.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ModeRuntimeStateSnapshot(
        String roomKey,
        String phaseKey,
        int remainingTimeTicks,
        List<RoomSummaryMetric> metrics,
        Map<String, ModePlayerValue> playerValues,
        List<ModePrompt> prompts,
        long revision
) {
    public ModeRuntimeStateSnapshot {
        roomKey = Objects.requireNonNullElse(roomKey, "").trim();
        phaseKey = Objects.requireNonNullElse(phaseKey, "").trim();
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        playerValues = playerValues == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(playerValues));
        prompts = prompts == null ? List.of() : List.copyOf(prompts);
        revision = Math.max(0L, revision);
    }

    public static ModeRuntimeStateSnapshot empty(RoomId roomId) {
        return new ModeRuntimeStateSnapshot(
                roomId == null ? "" : roomId.encode(),
                "",
                0,
                List.of(),
                Map.of(),
                List.of(),
                0L);
    }
}
