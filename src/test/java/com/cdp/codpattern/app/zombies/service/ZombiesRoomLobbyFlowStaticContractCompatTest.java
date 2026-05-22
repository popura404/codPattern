package com.cdp.codpattern.app.zombies.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZombiesRoomLobbyFlowStaticContractCompatTest {
    private static final Path ZOMBIES_MAP =
            Path.of("src/main/java/com/cdp/codpattern/compat/fpsmatch/map/ZombiesMap.java");
    private static final Path ROOM_HANDLE =
            Path.of("src/main/java/com/cdp/codpattern/compat/fpsmatch/map/ZombiesRoomHandleFactory.java");
    private static final Path OBJECT_INTERACTION_SERVICE =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesObjectInteractionService.java");
    private static final Path CLIENT_PACKET_HANDLER =
            Path.of("src/main/java/com/cdp/codpattern/network/handler/ClientPacketHandler.java");
    private static final Path EN_US_LANG = Path.of("src/main/resources/assets/codpattern/lang/en_us.json");
    private static final Path ZH_CN_LANG = Path.of("src/main/resources/assets/codpattern/lang/zh_cn.json");
    private static final Path ZH_TW_LANG = Path.of("src/main/resources/assets/codpattern/lang/zh_tw.json");
    private static final Path JA_JP_LANG = Path.of("src/main/resources/assets/codpattern/lang/ja_jp.json");

    private ZombiesRoomLobbyFlowStaticContractCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        String zombiesMap = read(ZOMBIES_MAP);
        String roomHandle = read(ROOM_HANDLE);
        String objectInteractionService = read(OBJECT_INTERACTION_SERVICE);
        String clientPacketHandler = read(CLIENT_PACKET_HANDLER);

        requireContains(roomHandle, "Optional.of(ports)",
                "zombies room handle should route ready-state writes through its ports");
        requireContains(roomHandle,
                "ZombiesPorts implements ModeRoomSummaryPort, ModeRoomLifecyclePort, ModeRosterPort, ModeRuntimeStatePort, ReadyStatePort",
                "zombies room ports should implement ReadyStatePort");
        String readyWrite = methodBody(roomHandle, "public boolean setPlayerReady(ServerPlayer player, boolean ready)");
        requireContains(readyWrite, "boolean changed = map.readyService().setPlayerReady(player, ready);",
                "ready writes should still delegate to the ready service");
        requireContains(readyWrite, "sendRosterSnapshotToSurvivors();",
                "ready changes should immediately sync roster ready flags to survivors");

        String logout = methodBody(zombiesMap, "public void onPlayerLoggedOut(ServerPlayer player)");
        requireContains(logout,
                "runtimeState.phase() == ZombiesGamePhase.WAITING || runtimeState.phase() == ZombiesGamePhase.START_VOTE",
                "waiting/start-vote logout should be treated as leaving the room");
        requireContains(logout, "leaveRoomPlayer(player);",
                "waiting/start-vote logout should remove the survivor from the roster and vote snapshot");
        requireContains(logout, "syncRosterToSurvivors();",
                "logout roster changes should be pushed to remaining survivors immediately");
        requireContains(zombiesMap, "Set<UUID> onlineSurvivorPlayerIds()",
                "start votes should have an online survivor snapshot source");
        requireContains(zombiesMap, "return onlineSurvivorPlayerIds();",
                "start-vote snapshots should exclude offline retained survivor records");
        requireContains(zombiesMap, "() -> runtimeState.phase().allowsPurchases()",
                "object interactions should be phase-gated by the zombies runtime phase");

        requireContains(objectInteractionService, "BooleanSupplier purchasesAllowedSupplier",
                "object interaction service should accept a purchase phase gate");
        requireContains(objectInteractionService,
                "if (!purchasesAllowedSupplier.getAsBoolean()) {\n            sendMessage(player, FAILURE_PHASE_LOCKED, target.objectId());\n            return InteractionResult.FAIL;\n        }",
                "object interactions should fail before mutation when the phase does not allow purchases");
        requireContains(objectInteractionService,
                "purchasesAllowedSupplier.getAsBoolean() && canHandleBoxStyleInteraction(target, context)",
                "box prompts should become non-interactable while purchases are phase-locked");
        requireContains(objectInteractionService, "FAILURE_PHASE_LOCKED",
                "phase-locked interaction failures should use a dedicated translation key");

        requireContains(clientPacketHandler, "closeStaleModeVoteDialog(minecraft, snapshot);",
                "runtime state sync should close stale vote dialogs after a failed/cancelled start vote");
        requireContains(clientPacketHandler, "\"START_VOTE\".equalsIgnoreCase(snapshot.phaseKey())",
                "vote dialogs should remain open only while the room is still in START_VOTE");
        requireContains(clientPacketHandler, "minecraft.setScreen(restorePreviousScreen ? previousScreen : null);",
                "failed-vote dialog cleanup should restore the previous screen instead of leaving no UI context");

        requireContains(read(EN_US_LANG), "message.codpattern.zombies.interaction.failure.phase_locked",
                "English phase-locked interaction message should exist");
        requireContains(read(ZH_CN_LANG), "message.codpattern.zombies.interaction.failure.phase_locked",
                "Simplified Chinese phase-locked interaction message should exist");
        requireContains(read(ZH_TW_LANG), "message.codpattern.zombies.interaction.failure.phase_locked",
                "Traditional Chinese phase-locked interaction message should exist");
        requireContains(read(JA_JP_LANG), "message.codpattern.zombies.interaction.failure.phase_locked",
                "Japanese phase-locked interaction message should exist");

        System.out.println("PASS zombies room lobby flow static contract compat");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path);
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

    private static void requireContains(String content, String expected, String message) {
        if (!content.contains(expected)) {
            throw new AssertionError(message + ": missing `" + expected + "`");
        }
    }
}
