package com.cdp.codpattern.app.match.runtime.object;

/** Monotonic positive revision clock with the existing overflow wrap behavior. */
public final class ModeObjectRevisionClock {
    private long revision;

    public long next() {
        revision = revision == Long.MAX_VALUE ? 1L : revision + 1L;
        return revision;
    }

    public long current() {
        return revision;
    }
}
