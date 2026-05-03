package com.cdp.codpattern.app.zombies.service;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ZombiesActiveSpawnGroupService {
    private static final int INITIAL_SPAWN_GROUP = 1;

    private final Set<Integer> activeGroups = new LinkedHashSet<>();

    public ZombiesActiveSpawnGroupService() {
        resetToInitial();
    }

    public synchronized void resetToInitial() {
        activeGroups.clear();
        activeGroups.add(INITIAL_SPAWN_GROUP);
    }

    public synchronized boolean activate(int group) {
        if (group < 1) {
            return false;
        }
        return activeGroups.add(group);
    }

    public synchronized Set<Integer> snapshot() {
        return Set.copyOf(activeGroups);
    }
}
