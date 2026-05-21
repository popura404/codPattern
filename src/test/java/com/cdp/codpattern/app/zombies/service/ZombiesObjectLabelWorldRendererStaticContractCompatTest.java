package com.cdp.codpattern.app.zombies.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZombiesObjectLabelWorldRendererStaticContractCompatTest {
    private static final Path RENDERER =
            Path.of("src/main/java/com/cdp/codpattern/event/client/ZombiesObjectLabelWorldRenderer.java");

    private ZombiesObjectLabelWorldRendererStaticContractCompatTest() {
    }

    public static void main(String[] args) throws IOException {
        String renderer = Files.readString(RENDERER);

        requireContains(renderer,
                "@Mod.EventBusSubscriber(modid = CodPattern.MODID, value = Dist.CLIENT",
                "object labels must be a client-only Forge event renderer");
        requireContains(renderer,
                "RenderLevelStageEvent.Stage.AFTER_PARTICLES",
                "object labels must render in world space after particles");
        requireContains(renderer,
                "ClientModeObjectState.roomStates(roomKey)",
                "object labels must consume synced room object state");
        requireContains(renderer,
                "ClientZombiesState.shouldRenderHud()",
                "object labels must only render while the zombies HUD is active");
        requireContains(renderer,
                "PAYLOAD_TYPE_BARRIER.equals(type)",
                "object labels must include barriers");
        requireContains(renderer,
                "PAYLOAD_TYPE_WEAPON_WALL.equals(type)",
                "object labels must include weapon walls");
        requireContains(renderer,
                "ZombiesRarityDisplay.fromRarityId(rarityId)",
                "weapon-wall labels must color only supported synced rarity ids");
        requireContains(renderer,
                "label.titleColor()",
                "object labels must support per-title colors for weapon-wall rarity text");
        requireContains(renderer,
                "PAYLOAD_TYPE_AMMO_BOX.equals(type)",
                "object labels must include ammo boxes");
        requireContains(renderer,
                "PAYLOAD_TYPE_ARMOR_STATION.equals(type)",
                "object labels must include armor stations");
        requireContains(renderer,
                "PAYLOAD_TYPE_POWER_SWITCH.equals(type)",
                "object labels must include power switches");
        requireContains(renderer,
                "PAYLOAD_TYPE_SODA_MACHINE.equals(type)",
                "object labels must include soda machines");
        requireContains(renderer,
                "PAYLOAD_TYPE_ULTIMATE_MACHINE.equals(type)",
                "object labels must include ultimate machines");
        requireContains(renderer,
                "ZOMBIES_POWER_SWITCH",
                "power-switch labels must validate the registered power-switch block");
        requireContains(renderer,
                "ZOMBIES_SODA_MACHINE_BOX",
                "soda-machine labels must validate the registered soda-machine block");
        requireContains(renderer,
                "ZOMBIES_ULTIMATE_MACHINE_BOX",
                "ultimate-machine labels must validate the registered ultimate-machine block");
        requireContains(renderer,
                "event.getFrustum().isVisible",
                "object labels must be view-frustum gated");
        requireContains(renderer,
                "MAX_RENDER_DISTANCE",
                "object labels must be distance gated");
        requireContains(renderer,
                "new ClipContext(",
                "object labels must avoid rendering through walls");
        requireContains(renderer,
                "MAX_RENDERED_LABELS",
                "object labels must cap per-frame label count");

        System.out.println("PASS zombies object label world renderer static contract compat");
    }

    private static void requireContains(String text, String expected, String message) {
        if (!text.contains(expected)) {
            throw new AssertionError(message + ": missing `" + expected + "`");
        }
    }
}
