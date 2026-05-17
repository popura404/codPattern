package com.cdp.codpattern.client.gui.overlay;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZombiesHudOverlayStaticContractCompatTest {
    private static final Path OVERLAY = Path.of("src/main/java/com/cdp/codpattern/client/gui/overlay/ZombiesHudOverlay.java");

    private ZombiesHudOverlayStaticContractCompatTest() {
    }

    public static void main(String[] args) throws IOException {
        String overlay = Files.readString(OVERLAY);

        requireAbsent(overlay, "hud.codpattern.zombies.combat", "zombies overlay must not render combat K/A/D text");
        requireAbsent(overlay, "renderPlayerStats", "zombies overlay must not render the bottom-right player stats panel");
        requireAbsent(overlay, "Power \"", "zombies overlay must not render the bottom-right power text");
        requireAbsent(overlay, "Buffs \"", "zombies overlay must not render the bottom-right buffs text");
        requireContains(overlay, "private static final int PLAYER_STATUS_BAR_WIDTH = 210;",
                "local player health bar width must stay 210");
        requireContains(overlay, "private static final int TEAMMATE_BAR_WIDTH = 150;",
                "teammate health bar width must stay 150");
        requireContains(overlay, "private static final int TEAMMATE_BAR_HEIGHT = 2;",
                "teammate health bar height must stay 2");
        requireContains(overlay, "ClientZombiesState.roomTeammates()",
                "teammate rows must use room teammate filtering");
        requireContains(overlay, "renderRoomTeammateStatus",
                "zombies overlay must render the room teammate status block");
        requireContains(overlay, "new GameProfile(playerId, name)",
                "teammate avatar should use a room roster identity, not a world entity lookup");
        requireContains(overlay, "getInsecureSkinLocation",
                "teammate avatar should use skin manager lookup");
        requireAbsent(overlay, "getPlayerByUUID",
                "teammate avatar must not depend on a world player entity");
        requireContains(overlay, "Integer.toString(Math.max(0, teammate.points()))",
                "teammate points should render as a bare number");
        requireContains(overlay, "Integer.toString(Math.max(0, teammate.armorLevel()))",
                "teammate armor should render as a right-aligned numeric value");

        System.out.println("PASS zombies HUD overlay static contract compat");
    }

    private static void requireContains(String text, String expected, String message) {
        if (!text.contains(expected)) {
            throw new AssertionError(message + ": missing `" + expected + "`");
        }
    }

    private static void requireAbsent(String text, String unexpected, String message) {
        if (text.contains(unexpected)) {
            throw new AssertionError(message + ": found `" + unexpected + "`");
        }
    }
}
