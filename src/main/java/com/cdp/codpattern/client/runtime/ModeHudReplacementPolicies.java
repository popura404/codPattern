package com.cdp.codpattern.client.runtime;

import com.cdp.codpattern.client.extension.ModeHudReplacementPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Client-only HUD replacement policy registry. */
public final class ModeHudReplacementPolicies {
    private static final List<ModeHudReplacementPolicy> POLICIES = new ArrayList<>();

    private ModeHudReplacementPolicies() {
    }

    public static synchronized void register(ModeHudReplacementPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (POLICIES.stream().anyMatch(existing -> existing.id().equals(policy.id()))) {
            throw new IllegalStateException("Duplicate HUD replacement policy: " + policy.id());
        }
        POLICIES.add(policy);
        POLICIES.sort(Comparator.comparingInt(ModeHudReplacementPolicy::order)
                .thenComparing(ModeHudReplacementPolicy::id));
    }

    public static synchronized boolean shouldReplaceVanillaPlayerHud() {
        return POLICIES.stream().anyMatch(ModeHudReplacementPolicy::shouldReplaceVanillaPlayerHud);
    }
}
