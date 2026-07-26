package com.cdp.codpattern.app.match.runtime.object;

import java.util.List;
import java.util.Map;

/** Object-keyed revision index; missing current objects retain revision zero. */
public final class ModeObjectRevisionIndex {
    private final ModeObjectIndex<Long> revisions = new ModeObjectIndex<>();

    public long ensure(String objectId) {
        return revisions.get(objectId).map(value -> Math.max(0L, value)).orElseGet(() -> {
            revisions.put(objectId, 0L);
            return 0L;
        });
    }

    public void put(String objectId, long revision) {
        revisions.put(objectId, Math.max(0L, revision));
    }

    public void reset(Map<String, Long> replacements) {
        revisions.reset(replacements);
    }

    public List<String> objectIds() {
        return revisions.objectIds();
    }

    public void clear() {
        revisions.clear();
    }
}
