package com.cdp.codpattern.app.match.model;

import java.util.Objects;

public record RoomSummaryMetric(
        String key,
        String translationKey,
        int value,
        MetricDisplay display
) {
    public RoomSummaryMetric {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(translationKey, "translationKey");
        display = display == null ? MetricDisplay.NUMBER : display;
    }
}
