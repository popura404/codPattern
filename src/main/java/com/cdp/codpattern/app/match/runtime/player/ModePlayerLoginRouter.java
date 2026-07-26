package com.cdp.codpattern.app.match.runtime.player;

import com.cdp.codpattern.app.match.extension.ModePlayerLoginContributor;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic login-recovery contributor route. */
public final class ModePlayerLoginRouter {
    private final Map<String, ModePlayerLoginContributor> contributors = new LinkedHashMap<>();

    public ModePlayerLoginRouter() {
    }

    public ModePlayerLoginRouter(Collection<? extends ModePlayerLoginContributor> contributors) {
        if (contributors != null) {
            contributors.forEach(this::register);
        }
    }

    public synchronized void register(ModePlayerLoginContributor contributor) {
        Objects.requireNonNull(contributor, "contributor");
        String id = normalizeId(contributor.id());
        ModePlayerLoginContributor existing = contributors.putIfAbsent(id, contributor);
        if (existing != null && existing != contributor) {
            throw new IllegalStateException("Duplicate player-login contributor: " + id);
        }
    }

    public ModePlayerLoginContributor.LoginDisposition route(ServerPlayer player) {
        for (ModePlayerLoginContributor contributor : snapshot()) {
            ModePlayerLoginContributor.LoginDisposition disposition = contributor.onPlayerLogin(player);
            if (disposition == ModePlayerLoginContributor.LoginDisposition.STOP_SHARED_LOGIN) {
                return disposition;
            }
        }
        return ModePlayerLoginContributor.LoginDisposition.CONTINUE;
    }

    public synchronized List<ModePlayerLoginContributor> snapshot() {
        List<ModePlayerLoginContributor> ordered = new ArrayList<>(contributors.values());
        ordered.sort(Comparator.comparingInt(ModePlayerLoginContributor::order)
                .thenComparing(contributor -> normalizeId(contributor.id())));
        return List.copyOf(ordered);
    }

    private static String normalizeId(String id) {
        String normalized = Objects.requireNonNullElse(id, "").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("contributor id must not be blank");
        }
        return normalized;
    }
}
