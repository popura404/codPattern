package com.cdp.codpattern.app.zombies.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FpsmToolCreativeTabStaticContractCompatTest {
    private static final Path CORE_BOOTSTRAP = Path.of("src/main/java/com/cdp/codpattern/bootstrap/CoreBootstrap.java");
    private static final Path ZOMBIES_BOOTSTRAP = Path.of("src/main/java/com/cdp/codpattern/app/zombies/bootstrap/ZombiesBootstrap.java");
    private static final Path ITEM_REGISTER = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/item/FPSMItemRegister.java");
    private static final Path ZOMBIES_ITEM_REGISTER = Path.of(
            "src/main/java/com/cdp/codpattern/app/zombies/bootstrap/ZombiesItemRegister.java");

    private FpsmToolCreativeTabStaticContractCompatTest() {
    }

    public static void main(String[] args) throws IOException {
        String coreBootstrap = Files.readString(CORE_BOOTSTRAP);
        String zombiesBootstrap = Files.readString(ZOMBIES_BOOTSTRAP);
        String itemRegister = Files.readString(ITEM_REGISTER);
        String zombiesItemRegister = Files.readString(ZOMBIES_ITEM_REGISTER);

        requireContains(coreBootstrap, "modEventBus.addListener(FPSMItemRegister::onBuildCreativeModeTabContents);",
                "FPSM tools creative tab listener must be registered on the mod event bus");
        requireContains(coreBootstrap, "FPSMItemRegister.ITEMS.register(modEventBus);",
                "FPSM tool items must be registered on the mod event bus");
        requireContains(zombiesBootstrap, "modEventBus.addListener(ZombiesItemRegister::onBuildCreativeModeTabContents);",
                "Zombies deploy tool creative tab listener must be registered by the addon bootstrap");
        requireContains(zombiesBootstrap, "ZombiesItemRegister.ITEMS.register(modEventBus);",
                "Zombies deploy tool item must be registered by the addon bootstrap");

        requireContains(itemRegister, "\"map_creator_tool\"",
                "map creator tool item id must remain registered");
        requireContains(itemRegister, "\"spawn_point_tool\"",
                "spawn point tool item id must remain registered");
        requireAbsent(itemRegister, "\"zombies_deploy_tool\"",
                "generic FPSM items must not own the Zombies deploy tool");
        requireContains(zombiesItemRegister, "\"zombies_deploy_tool\"",
                "zombies deploy tool item id must remain registered");
        String creativeTabBody = methodBody(itemRegister, "public static void onBuildCreativeModeTabContents");
        String zombiesCreativeTabBody = methodBody(
                zombiesItemRegister,
                "public static void onBuildCreativeModeTabContents");
        requireContains(creativeTabBody, "CreativeModeTabs.TOOLS_AND_UTILITIES.equals(event.getTabKey())",
                "FPSM tools must be added to the tools and utilities creative tab");
        requireContains(creativeTabBody, "event.accept(MAP_CREATOR_TOOL);",
                "map creator tool must appear in the creative tab");
        requireContains(creativeTabBody, "event.accept(SPAWN_POINT_TOOL);",
                "spawn point tool must appear in the creative tab");
        requireContains(zombiesCreativeTabBody, "CreativeModeTabs.TOOLS_AND_UTILITIES.equals(event.getTabKey())",
                "Zombies deploy tool must remain in tools and utilities");
        requireContains(zombiesCreativeTabBody, "event.accept(ZOMBIES_DEPLOY_TOOL);",
                "zombies deploy tool must appear in the creative tab");
        requireAbsent(creativeTabBody, "hasPermissions",
                "FPSM tools must not be hidden behind the operator-items permission toggle");
        requireAbsent(zombiesCreativeTabBody, "hasPermissions",
                "Zombies deploy tool must not be hidden behind the operator-items permission toggle");

        System.out.println("PASS FPSM tool creative tab static contract compat");
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("missing method `" + signature + "`");
        }
        int open = source.indexOf('{', start);
        if (open < 0) {
            throw new AssertionError("missing method body `" + signature + "`");
        }
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open + 1, i);
                }
            }
        }
        throw new AssertionError("unterminated method `" + signature + "`");
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
