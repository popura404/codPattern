package com.cdp.codpattern.app.match.model;

import java.util.Optional;

public record DamageDecision(
        boolean cancelEvent,
        Optional<Float> replacementAmount
) {
    public DamageDecision {
        replacementAmount = replacementAmount == null ? Optional.empty() : replacementAmount;
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
}
