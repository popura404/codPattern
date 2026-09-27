package com.cdp.codpattern.client.gui.screen;

import java.util.Optional;

/** Pure editor state: filling a position never changes the saved baseline. */
public final class EndTeleportDraft {
    public record Value(String dimension, int x, int y, int z, float yaw) {
        public Value {
            yaw %= 360;
            if (yaw >= 180) yaw -= 360;
            if (yaw < -180) yaw += 360;
            if (yaw == 0) yaw = 0; // canonicalize negative zero
        }
    }
    private Value saved;
    private String dimension = "";
    private float yaw;
    private String x = "", y = "", z = "";

    public void load(Value value, Value current) {
        saved = value;
        if (value != null) fill(value);
        else { dimension = current.dimension(); yaw = current.yaw(); x = y = z = ""; }
    }
    public void baseline(Value value) { saved = value; }
    public void fill(Value value) {
        dimension = value.dimension(); yaw = value.yaw();
        x = Integer.toString(value.x()); y = Integer.toString(value.y()); z = Integer.toString(value.z());
    }
    public void coordinates(String x, String y, String z) { this.x = x; this.y = y; this.z = z; }
    public String x() { return x; }
    public String y() { return y; }
    public String z() { return z; }
    public String dimension() { return dimension; }
    public float yaw() { return yaw; }
    public Optional<Value> value() {
        try {
            if (dimension.isEmpty() || !Float.isFinite(yaw)) return Optional.empty();
            return Optional.of(new Value(dimension, Integer.parseInt(x.trim()), Integer.parseInt(y.trim()),
                    Integer.parseInt(z.trim()), yaw));
        } catch (NumberFormatException invalid) { return Optional.empty(); }
    }
    public boolean dirty() {
        if (saved == null && x.isBlank() && y.isBlank() && z.isBlank()) return false;
        return value().map(value -> !value.equals(saved)).orElse(true);
    }
    public boolean canSave() { return dirty() && value().isPresent(); }
}
