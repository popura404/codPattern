package com.cdp.codpattern.app.match.model;

import java.util.Map;

public record ModeClientPayload(Map<String, String> values) {
    public ModeClientPayload {
        values = values == null ? Map.of() : Map.copyOf(values);
    }
}
