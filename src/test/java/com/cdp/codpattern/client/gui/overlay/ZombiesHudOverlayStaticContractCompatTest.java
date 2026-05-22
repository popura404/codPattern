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
        requireContains(overlay, "renderScoreLeaderboard",
                "zombies overlay must render the left-side match score leaderboard");
        requireContains(overlay, "ClientZombiesState.leaderboardRows()",
                "zombies leaderboard and settlement pages must use client result rows");
        requireContains(overlay, "LEADERBOARD_LEFT = 8",
                "leaderboard should stay anchored to the left edge like the TDM score panel");
        requireContains(overlay, "screenHeight >= 500 ? 92 : 70",
                "leaderboard vertical position should mirror the TDM left score panel");
        requireContains(overlay, "renderZombiesResultOverlay",
                "zombies overlay must render a zombies-specific settlement overlay");
        requireContains(overlay, "RESULT_PAGE_COUNT = 2",
                "zombies settlement must use two pages");
        requireContains(overlay, "renderResultSurvivalPage",
                "zombies settlement must include a survival information page");
        requireContains(overlay, "renderResultSettlementPage",
                "zombies settlement must include a score-sorted settlement page");
        requireAbsent(overlay, "renderMvpSvpPage",
                "zombies settlement must not render TDM MVP/SVP spotlight pages");
        requireAbsent(overlay, "hud.codpattern.tdm.result.mvp",
                "zombies settlement must not use TDM MVP localization");
        requireAbsent(overlay, "hud.codpattern.tdm.result.svp",
                "zombies settlement must not use TDM SVP localization");
        requireContains(overlay, "renderIntermissionWaveAnnouncement",
                "zombies overlay must render the wave-only intermission announcement");
        requireContains(overlay, "INTERMISSION_WAVE_FADE_IN_MS = 1000L",
                "intermission wave announcement should fade in over one second");
        requireContains(overlay, "INTERMISSION_WAVE_HOLD_MS = 3500L",
                "intermission wave announcement should hold for three and a half seconds");
        requireContains(overlay, "INTERMISSION_WAVE_FADE_OUT_MS = 500L",
                "intermission wave announcement should fade out over half a second");
        requireContains(overlay, "clearIntermissionWaveAnnouncement();\n            renderTopStats",
                "top-right wave stats should only return outside intermission");
        requireContains(overlay, "\"WAVE_ACTIVE\".equals(phase) || \"INTERMISSION\".equals(phase)",
                "intermission countdown phase notice should stay hidden");
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
        requireContains(overlay, "renderHeldWeaponRarity",
                "zombies overlay must render the held weapon rarity marker");
        requireContains(overlay, "ZombiesWeaponItemStackService.TAG_RARITY_ID",
                "held weapon rarity marker must read the explicit zombies rarity tag");
        requireContains(overlay, "ZombiesRarityDisplay.fromRarityId(rarityId)",
                "held weapon rarity marker must ignore blank or unsupported rarity ids");
        requireContains(overlay, "graphics.fillGradient(",
                "held weapon rarity marker must use a translucent gradient color panel");
        requireContains(overlay, "renderHeldWeaponUpgradeLevel",
                "zombies overlay must render the held weapon upgrade level marker");
        requireContains(overlay, "HELD_UPGRADE_LEVEL_BOTTOM_MARGIN = 28",
                "held weapon upgrade level marker should stay in the lower-right corner below rarity");
        requireContains(overlay, "int upgradeLevel = heldWeaponUpgradeLevel(player.getMainHandItem(), roomKey);",
                "held weapon upgrade level marker must read the currently held weapon");
        requireContains(overlay, "return positiveIntTag(tag, ZombiesWeaponItemStackService.TAG_UPGRADE_LEVEL);",
                "held weapon upgrade level marker must use the explicit zombies upgrade-level tag");
        requireContains(overlay, "graphics.drawString(font, text, x, y, TEXT_PRIMARY, true);",
                "held weapon upgrade level marker must render as white text");
        requireContains(overlay, "renderInteractionPrompt",
                "zombies overlay must render crosshair interaction prompts");
        requireContains(overlay, "ClientModeObjectState.roomStates(roomKey)",
                "interaction prompt must use synced object state instead of a hover request packet");
        requireContains(overlay, "minecraft.hitResult instanceof BlockHitResult",
                "interaction prompt must be driven by the block under the crosshair");
        requireContains(overlay, "int targetCenterY = (screenHeight / 2 + screenHeight) / 2;",
                "interaction prompt vertical center must stay midway between screen center and bottom edge");
        requireContains(overlay, "String actionText = fit(font, line.text(), maxPromptWidth);",
                "interaction prompt must render action text on its own row");
        requireContains(overlay, "String keyText = fit(font, line.interactable() ? \"[\" + keyLabel + \"] 交互\" : \"不可交互\", maxPromptWidth);",
                "interaction prompt must render key/status text on a second row");
        requireContains(overlay, "int totalHeight = font.lineHeight * 2 + 3;",
                "interaction prompt must reserve two text rows");
        requireContains(overlay, "graphics.drawString(font, actionText, x, y, line.color(), true);",
                "interaction prompt must render per-state action text colors");
        requireContains(overlay, "graphics.drawString(font, keyText, x, y + font.lineHeight + 3,",
                "interaction prompt must render key/status text below the action row");
        requireContains(overlay, "pricesByWeaponLevel",
                "ammo prompt must use synced level prices rather than a minimum fallback cost");
        requireContains(overlay, "ZombiesWeaponItemStackService.TAG_WEAPON_LEVEL",
                "ammo prompt must compute exact price from the held zombies weapon level");
        requireContains(overlay, "TaczClientApi.resolveReserveAmmo(stack)",
                "ammo prompt must use live TaCZ reserve ammo instead of stale starter weapon tag reserve");
        requireContains(overlay, "TaczClientApi.resolveMaxReserveAmmo(stack)",
                "ammo prompt must use live TaCZ max reserve ammo for full-ammo checks");
        requireContains(overlay, "ultimateMachinePrompt",
                "interaction prompt must include ultimate machines");
        requireContains(overlay, "sodaMachinePrompt",
                "interaction prompt must include soda machines");
        requireContains(overlay, "ZOMBIES_SODA_MACHINE_BOX",
                "soda machine prompt must recognize the registered soda machine box block");
        requireContains(overlay, "return new InteractionPromptLine(text + \"（需要电源）\", false, false, TEXT_DANGER);",
                "power-gated box-style prompts must render missing-power text in red");
        requireContains(overlay, "ZombiesWeaponItemStackService.TAG_UPGRADE_LEVEL",
                "ultimate machine prompt must read the held zombies weapon upgrade level");
        requireContains(overlay, "ZOMBIES_ULTIMATE_MACHINE_BOX",
                "ultimate machine prompt must recognize the registered ultimate machine box block");
        requireContains(overlay, "minecraft.options.keyUse",
                "interaction prompt must show the user's current use-key binding");
        requireContains(overlay, "InteractKey.INTERACT_KEY",
                "interaction prompt must switch to the TaCZ interact key while a TaCZ gun is held");
        requireContains(overlay, "TaczClientApi.isGun(mainHand) && isTaczInteractKeyPromptObjectType(type)",
                "box prompts must use TaCZ interact-key labeling only for held TaCZ guns");
        requireContains(overlay, "barrierPrompt",
                "interaction prompt must include barriers");
        requireContains(overlay, "case \"barrier\" -> barrierPrompt(payload, taczInteractKey);",
                "barrier prompt must use TaCZ interact-key labeling while a TaCZ gun is held");
        requireContains(overlay, "return \"barrier\".equals(type)",
                "barriers must be part of the TaCZ interact-key prompt object set");
        requireContains(overlay, "ZOMBIES_PLAYER_BARRIER",
                "barrier prompt must recognize runtime barrier blocks under the crosshair");
        requireContains(overlay, "barrierContains",
                "barrier prompt must match runtime barrier area cells without a hover request packet");
        requireContains(overlay, "areaFromX",
                "barrier prompt must use synced barrier area endpoints");

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
