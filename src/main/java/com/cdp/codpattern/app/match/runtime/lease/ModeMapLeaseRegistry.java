package com.cdp.codpattern.app.match.runtime.lease;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Token-matched mode/map leases that reject stale lifecycle releases. */
public final class ModeMapLeaseRegistry {
    private final Map<LeaseKey, Lease> leases = new LinkedHashMap<>();
    private final AtomicLong generations = new AtomicLong();

    public synchronized AcquireResult acquire(LeaseKey key, String owner) {
        Objects.requireNonNull(key, "key");
        String normalizedOwner = normalizeOwner(owner);
        Lease existing = leases.get(key);
        if (existing == null) {
            Lease acquired = new Lease(key, normalizedOwner, generations.incrementAndGet());
            leases.put(key, acquired);
            return new AcquireResult(AcquireStatus.ACQUIRED, acquired);
        }
        if (existing.owner().equals(normalizedOwner)) {
            return new AcquireResult(AcquireStatus.ALREADY_HELD, existing);
        }
        return new AcquireResult(AcquireStatus.REJECTED, existing);
    }

    public synchronized boolean release(Lease lease) {
        return lease != null && leases.remove(lease.key(), lease);
    }

    public synchronized Optional<Lease> forceInvalidate(LeaseKey key) {
        return Optional.ofNullable(key == null ? null : leases.remove(key));
    }

    public synchronized int clear() {
        int cleared = leases.size();
        leases.clear();
        return cleared;
    }

    public synchronized Optional<Lease> current(LeaseKey key) {
        return Optional.ofNullable(key == null ? null : leases.get(key));
    }

    public synchronized int size() {
        return leases.size();
    }

    private static String normalizeOwner(String owner) {
        String normalized = Objects.requireNonNullElse(owner, "").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("owner must not be blank");
        }
        return normalized;
    }

    public record LeaseKey(String gameType, String mapName) {
        public LeaseKey {
            gameType = Objects.requireNonNullElse(gameType, "").trim();
            mapName = Objects.requireNonNullElse(mapName, "").trim();
            if (gameType.isEmpty() || mapName.isEmpty()) {
                throw new IllegalArgumentException("gameType and mapName must not be blank");
            }
        }
    }

    public record Lease(LeaseKey key, String owner, long generation) {
        public Lease {
            Objects.requireNonNull(key, "key");
            owner = normalizeOwner(owner);
            if (generation <= 0L) {
                throw new IllegalArgumentException("generation must be positive");
            }
        }
    }

    public enum AcquireStatus {
        ACQUIRED,
        ALREADY_HELD,
        REJECTED
    }

    public record AcquireResult(AcquireStatus status, Lease lease) {
        public AcquireResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(lease, "lease");
        }

        public boolean acquired() {
            return status != AcquireStatus.REJECTED;
        }
    }
}
