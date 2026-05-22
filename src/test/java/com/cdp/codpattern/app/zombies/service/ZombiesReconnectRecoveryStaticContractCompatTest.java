package com.cdp.codpattern.app.zombies.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZombiesReconnectRecoveryStaticContractCompatTest {
    private static final Path RECOVERY_SERVICE =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesReconnectRecoveryService.java");
    private static final Path PLAYER_STATE_SERVICE =
            Path.of("src/main/java/com/cdp/codpattern/app/zombies/service/ZombiesPlayerStateService.java");
    private static final Path LOGIN_HANDLER =
            Path.of("src/main/java/com/cdp/codpattern/event/PlayerLoggedInEventHandler.java");
    private static final Path ZOMBIES_MAP =
            Path.of("src/main/java/com/cdp/codpattern/compat/fpsmatch/map/ZombiesMap.java");
    private static final Path ROOM_HANDLE =
            Path.of("src/main/java/com/cdp/codpattern/compat/fpsmatch/map/ZombiesRoomHandleFactory.java");

    private ZombiesReconnectRecoveryStaticContractCompatTest() {
    }

    public static void main(String[] args) throws Exception {
        String recoveryService = read(RECOVERY_SERVICE);
        String playerStateService = read(PLAYER_STATE_SERVICE);
        String loginHandler = read(LOGIN_HANDLER);
        String zombiesMap = read(ZOMBIES_MAP);
        String roomHandle = read(ROOM_HANDLE);

        requireContains(recoveryService,
                "if (resolver.restoreActiveRoomPlayer(player, value.roomId()))",
                "active-round marker recovery should try to restore existing members before falling back to normal login flow");
        requireContains(recoveryService,
                "ACTIVE_ROUND_REJOIN",
                "active-round member restore should expose a distinct recovery outcome");
        requireContains(loginHandler,
                ".map(map -> map.restoreActiveRoundReconnect(player))",
                "login recovery resolver should delegate active-round restore to the owning zombies map");
        requireContains(playerStateService,
                "public boolean canRestoreActiveRoundPlayer(UUID playerId)",
                "reconnect restore eligibility should require an existing runtime state");
        requireContains(playerStateService,
                ".map(state -> !state.connectionState().isLeft())",
                "explicitly left players must not be restored as reconnecting active members");
        requireContains(zombiesMap,
                "public boolean restoreActiveRoundReconnect(ServerPlayer player)",
                "zombies map should own the active-round reconnect restore path");
        requireContains(zombiesMap,
                "if (player == null || !isStart || !runtimeState.phase().isRoundRunning())",
                "active-round reconnect restore should only run while the room is still in-game");
        requireContains(zombiesMap,
                "if (!playerStateService.canRestoreActiveRoundPlayer(playerId))",
                "active-round reconnect restore must reject players without preserved runtime state");
        requireContains(zombiesMap,
                "getMapTeams().getTeamByName(ZombiesTeamNames.SURVIVORS)",
                "active-round reconnect restore should reattach the returning member to the survivor roster");
        requireContains(zombiesMap,
                ".ifPresent(team -> team.join(player));",
                "active-round reconnect restore should restore team membership without using the public join path");
        requireContains(zombiesMap,
                "connectionStateService.markOnline(playerId);",
                "active-round reconnect restore should keep the existing stats object and only mark it online");
        requireContains(zombiesMap,
                "if (state.get().lifeState().isDeadSpectating())",
                "active-round reconnect restore should preserve dead spectator state");
        requireContains(roomHandle,
                "if (map.runtimeState().phase() != ZombiesGamePhase.WAITING) {\n                return JoinRoomResult.failure(roomId(), CODE_PHASE_LOCKED, \"\");\n            }",
                "public room join should remain locked outside WAITING so new players cannot mid-match join");

        System.out.println("PASS zombies reconnect recovery static contract compat");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }

    private static void requireContains(String content, String expected, String message) {
        if (!content.contains(expected)) {
            throw new AssertionError(message + ": missing `" + expected + "`");
        }
    }
}
