package com.cdp.codpattern.app.zombies.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FpsmToolPermissionStaticContractCompatTest {
    private static final Path TOOL_ACCESS_HELPER = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/item/tool/ToolAccessHelper.java");
    private static final Path TOOL_INTERACTION_PACKET = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/packet/ToolInteractionC2SPacket.java");
    private static final Path MAP_CREATOR_ACTION_PACKET = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/packet/MapCreatorToolActionC2SPacket.java");
    private static final Path SPAWN_POINT_ACTION_PACKET = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/packet/SpawnPointToolActionC2SPacket.java");
    private static final Path ZOMBIES_DEPLOY_ACTION_PACKET = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/packet/ZombiesDeployToolActionC2SPacket.java");
    private static final Path FPSM_EVENTS = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/FPSMEvents.java");
    private static final Path CORE_BOOTSTRAP = Path.of("src/main/java/com/cdp/codpattern/bootstrap/CoreBootstrap.java");
    private static final Path ZOMBIES_PREVIEW_CONTRIBUTOR = Path.of(
            "src/main/java/com/cdp/codpattern/app/zombies/bootstrap/ZombiesHeldToolPreviewContributor.java");
    private static final Path MAP_CREATOR_TOOL = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/item/MapCreatorTool.java");
    private static final Path SPAWN_POINT_TOOL = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/item/SpawnPointTool.java");
    private static final Path ZOMBIES_DEPLOY_TOOL = Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/item/ZombiesDeployTool.java");
    private static final Path[] LANG_FILES = {
            Path.of("src/main/resources/assets/codpattern/lang/en_us.json"),
            Path.of("src/main/resources/assets/codpattern/lang/zh_cn.json"),
            Path.of("src/main/resources/assets/codpattern/lang/zh_tw.json"),
            Path.of("src/main/resources/assets/codpattern/lang/ja_jp.json")
    };

    private FpsmToolPermissionStaticContractCompatTest() {
    }

    public static void main(String[] args) throws IOException {
        assertHelperRequiresLevelTwoOp();
        assertWorldInteractionPacketGate();
        assertActionPacketGates();
        assertToolMethodGates();
        assertPreviewTickGate();
        assertPermissionMessageLocalized();

        System.out.println("PASS FPSM tool permission static contract compat");
    }

    private static void assertHelperRequiresLevelTwoOp() throws IOException {
        String helper = Files.readString(TOOL_ACCESS_HELPER);
        requireContains(helper, "private static final int ADMIN_PERMISSION_LEVEL = 2",
                "tool permission helper must require operator level 2");
        requireContains(helper, "player.hasPermissions(ADMIN_PERMISSION_LEVEL)",
                "tool permission helper must use Minecraft operator permission checks");
        requireContains(helper, "message.fpsm.tool.admin_required",
                "tool permission helper must notify denied players");
    }

    private static void assertWorldInteractionPacketGate() throws IOException {
        String packet = Files.readString(TOOL_INTERACTION_PACKET);
        String handleBody = methodBody(packet, "public void handle");
        requireContains(handleBody, "ToolAccessHelper.ensureAdminAccess(player)",
                "world tool interaction packet must gate use behind op level 2");
        requireOrder(handleBody, "ToolAccessHelper.ensureAdminAccess(player)", "worldToolItem.handleWorldInteraction",
                "permission gate must run before dispatching world tool interaction");
    }

    private static void assertActionPacketGates() throws IOException {
        assertPacketHandleGate(MAP_CREATOR_ACTION_PACKET, "map creator action packet");

        String spawnPacket = Files.readString(SPAWN_POINT_ACTION_PACKET);
        assertPacketHandleGate(spawnPacket, "spawn point action packet");
        requireOccurrencesAtLeast(spawnPacket, "ToolAccessHelper.ensureAdminAccess(player)", 2,
                "spawn point screen send and action handling must both require op level 2");

        String zombiesPacket = Files.readString(ZOMBIES_DEPLOY_ACTION_PACKET);
        assertPacketHandleGate(zombiesPacket, "zombies deploy action packet");
        requireOccurrencesAtLeast(zombiesPacket, "ToolAccessHelper.ensureAdminAccess(player)", 2,
                "zombies deploy screen send and action handling must both require op level 2");
    }

    private static void assertPacketHandleGate(Path path, String label) throws IOException {
        assertPacketHandleGate(Files.readString(path), label);
    }

    private static void assertPacketHandleGate(String source, String label) {
        String handleBody = methodBody(source, "public void handle");
        requireContains(handleBody, "ToolAccessHelper.ensureAdminAccess(player)",
                label + " must gate server-side mutations behind op level 2");
    }

    private static void assertToolMethodGates() throws IOException {
        assertToolMethodGate(MAP_CREATOR_TOOL, "map creator tool");
        assertToolMethodGate(SPAWN_POINT_TOOL, "spawn point tool");
        assertToolMethodGate(ZOMBIES_DEPLOY_TOOL, "zombies deploy tool");
    }

    private static void assertToolMethodGate(Path path, String label) throws IOException {
        String source = Files.readString(path);
        String handleBody = methodBody(source, "public void handleWorldInteraction");
        requireContains(handleBody, "ToolAccessHelper.ensureAdminAccess(player)",
                label + " must reject direct world interaction without op level 2");
        String previewBody = methodBody(source, "public void syncHeldPreview");
        requireContains(previewBody, "ToolAccessHelper.hasAdminAccess(player)",
                label + " preview sync must not expose editor overlays to non-ops");
    }

    private static void assertPreviewTickGate() throws IOException {
        String events = Files.readString(FPSM_EVENTS);
        String coreBootstrap = Files.readString(CORE_BOOTSTRAP);
        String zombiesContributor = Files.readString(ZOMBIES_PREVIEW_CONTRIBUTOR);
        requireContains(events, "ToolAccessHelper.hasAdminAccess(player)",
                "tool preview tick must be gated behind op level 2");
        requireContains(events, "ModeHeldToolPreviewContributors.route(player, stack",
                "tool preview tick must route through installed preview contributors");
        requireContains(coreBootstrap, "MapCreatorTool.clearHeldPreview(player)",
                "map creator preview must clear for non-op players");
        requireContains(coreBootstrap, "SpawnPointTool.clearHeldPreview(player)",
                "spawn point preview must clear for non-op players");
        requireContains(zombiesContributor, "ZombiesDeployTool.clearHeldPreview(player)",
                "zombies deploy preview must clear for non-op players");
    }

    private static void assertPermissionMessageLocalized() throws IOException {
        for (Path langFile : LANG_FILES) {
            requireContains(Files.readString(langFile), "\"message.fpsm.tool.admin_required\"",
                    "tool permission denial message must be localized in " + langFile);
        }
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

    private static void requireOrder(String text, String first, String second, String message) {
        int firstIndex = text.indexOf(first);
        int secondIndex = text.indexOf(second);
        if (firstIndex < 0 || secondIndex < 0 || firstIndex > secondIndex) {
            throw new AssertionError(message + ": expected `" + first + "` before `" + second + "`");
        }
    }

    private static void requireOccurrencesAtLeast(String text, String needle, int expectedCount, String message) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        if (count < expectedCount) {
            throw new AssertionError(message + ": found " + count + ", expected at least " + expectedCount);
        }
    }
}
