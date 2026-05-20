package com.cdp.codpattern.client.gui.overlay;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TdmHudOverlayStaticContractCompatTest {
    private static final Path OVERLAY = Path.of("src/main/java/com/cdp/codpattern/client/gui/overlay/TdmHudOverlay.java");

    private TdmHudOverlayStaticContractCompatTest() {
    }

    public static void main(String[] args) throws IOException {
        String overlay = Files.readString(OVERLAY);

        requireContains(overlay, "BuiltInGameModes.isFrontline(gameType) || BuiltInGameModes.isTeamDeathMatch(gameType)",
                "TDM overlay room gate must include frontline as well as teamdeathmatch");
        requireContains(overlay, "if (!hasCurrentTdmRoomContext())",
                "TDM overlay should stay gated to TDM-family rooms");
        requireContains(overlay, "return isMatchHudPhase(ClientTdmState.currentPhase());",
                "TDM vanilla HUD replacement must still follow match HUD phases");

        System.out.println("PASS TDM HUD overlay static contract compat");
    }

    private static void requireContains(String text, String expected, String message) {
        if (!text.contains(expected)) {
            throw new AssertionError(message + ": missing `" + expected + "`");
        }
    }
}
