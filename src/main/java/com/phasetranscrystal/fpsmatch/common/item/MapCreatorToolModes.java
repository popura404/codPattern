package com.phasetranscrystal.fpsmatch.common.item;

import com.cdp.codpattern.app.match.BuiltInGameModes;
import com.cdp.codpattern.app.match.GameModeRegistry;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;

import java.util.Collection;
import java.util.List;

/** Scope of the built-in creation item, not of the shared map creation service. */
public final class MapCreatorToolModes {
    private static final List<String> ORDER = List.of(
            BuiltInGameModes.TEAM_DEATHMATCH, BuiltInGameModes.FRONTLINE);

    private MapCreatorToolModes() { }

    public static List<String> availableTypes() {
        return availableTypes(FPSMCore.getInstance().getGameTypes());
    }

    public static List<String> availableTypes(Collection<String> registeredTypes) {
        var canonical = registeredTypes.stream().map(GameModeRegistry::canonicalize).toList();
        return ORDER.stream().filter(canonical::contains).toList();
    }

    public static boolean supports(String type) {
        return availableTypes().contains(GameModeRegistry.canonicalize(type));
    }

    public static String selectedType(String savedType, Collection<String> availableTypes) {
        var supported = availableTypes(availableTypes);
        String canonical = GameModeRegistry.canonicalize(savedType);
        return supported.contains(canonical) ? canonical : supported.stream().findFirst().orElse("");
    }
}
