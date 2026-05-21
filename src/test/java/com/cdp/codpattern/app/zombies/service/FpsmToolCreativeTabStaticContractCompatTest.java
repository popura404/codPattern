package com.cdp.codpattern.app.zombies.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FpsmToolCreativeTabStaticContractCompatTest {
    private static final Path MOD_ENTRYPOINT = Path.of("src/main/java/com/cdp/codpattern/CodPattern.java");
    private static final Path ITEM_REGISTER = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/item/FPSMItemRegister.java");

    private FpsmToolCreativeTabStaticContractCompatTest() {
    }

    public static void main(String[] args) throws IOException {
        String entrypoint = Files.readString(MOD_ENTRYPOINT);
        String itemRegister = Files.readString(ITEM_REGISTER);

        requireContains(entrypoint, "modEventBus.addListener(FPSMItemRegister::onBuildCreativeModeTabContents);",
                "FPSM tools creative tab listener must be registered on the mod event bus");
        requireContains(entrypoint, "FPSMItemRegister.ITEMS.register(modEventBus);",
                "FPSM tool items must be registered on the mod event bus");

        requireContains(itemRegister, "\"map_creator_tool\"",
                "map creator tool item id must remain registered");
        requireContains(itemRegister, "\"spawn_point_tool\"",
                "spawn point tool item id must remain registered");
        requireContains(itemRegister, "\"zombies_deploy_tool\"",
                "zombies deploy tool item id must remain registered");
        String creativeTabBody = methodBody(itemRegister, "public static void onBuildCreativeModeTabContents");
        requireContains(creativeTabBody, "CreativeModeTabs.TOOLS_AND_UTILITIES.equals(event.getTabKey())",
                "FPSM tools must be added to the tools and utilities creative tab");
        requireContains(creativeTabBody, "event.accept(MAP_CREATOR_TOOL);",
                "map creator tool must appear in the creative tab");
        requireContains(creativeTabBody, "event.accept(SPAWN_POINT_TOOL);",
                "spawn point tool must appear in the creative tab");
        requireContains(creativeTabBody, "event.accept(ZOMBIES_DEPLOY_TOOL);",
                "zombies deploy tool must appear in the creative tab");
        requireAbsent(creativeTabBody, "hasPermissions",
                "FPSM tools must not be hidden behind the operator-items permission toggle");

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
