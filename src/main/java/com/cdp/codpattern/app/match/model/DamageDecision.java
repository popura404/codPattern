package com.cdp.codpattern.app.match.model;

import java.util.Optional;

public record DamageDecision(
        boolean cancelEvent,
        Optional<Float> replacementAmount,
        Optional<Integer> invulnerabilityTicks
) {
    public DamageDecision(boolean cancelEvent, Optional<Float> replacementAmount) {
        this(cancelEvent, replacementAmount, Optional.empty());
    }

    public DamageDecision {
        replacementAmount = replacementAmount == null ? Optional.empty() : replacementAmount;
        invulnerabilityTicks = invulnerabilityTicks == null
                ? Optional.empty()
                : invulnerabilityTicks.map(ticks -> Math.max(0, ticks));
    }

    public static DamageDecision passThrough() {
        return new DamageDecision(false, Optional.empty());
    }

    public static DamageDecision cancel() {
        return new DamageDecision(true, Optional.empty());
    }

    public static DamageDecision setAmount(float amount) {
        return new DamageDecision(false, Optional.of(amount));
    }

    public DamageDecision withInvulnerabilityTicks(int ticks) {
        return new DamageDecision(cancelEvent, replacementAmount, Optional.of(Math.max(0, ticks)));
    }
}
