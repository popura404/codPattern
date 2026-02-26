package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.client.ClientTdmState;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.app.tdm.model.TdmTeamNames;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.network.tdm.JoinGameFromSpectatorPacket;
import com.cdp.codpattern.network.tdm.JoinRoomPacket;
import com.cdp.codpattern.network.tdm.LeaveRoomPacket;
import com.cdp.codpattern.network.tdm.RequestRoomListPacket;
import com.cdp.codpattern.network.tdm.SelectTeamPacket;
import com.cdp.codpattern.network.tdm.SetReadyStatePacket;
import com.cdp.codpattern.network.tdm.VoteEndPacket;
import com.cdp.codpattern.network.tdm.VoteStartPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TdmRoomActionController {
    private static final long LEAVE_CONFIRM_WINDOW_MS = 3000L;
    private static final long JOIN_GAME_CONFIRM_WINDOW_MS = 3000L;
    private static final long ACTION_ACK_TIMEOUT_MS = 5000L;
    private static final long ROOM_NOTICE_DURATION_MS = 4500L;
    private static final long MIN_JOIN_GAME_REQUEST_ID = 1L;

    private final TdmRoomSessionState roomState;
    private final TdmRoomUiState uiState;
    private final Runnable buttonStateUpdater;
    private long nextJoinGameRequestId = MIN_JOIN_GAME_REQUEST_ID;

    public TdmRoomActionController(TdmRoomSessionState roomState, TdmRoomUiState uiState, Runnable buttonStateUpdater) {
        this.roomState = roomState;
        this.uiState = uiState;
        this.buttonStateUpdater = buttonStateUpdater;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        uiState.expireNotice(now);

        boolean localSpectatorInPlaying = isLocalSpectatorInPlaying();
        String roomStateNow = currentRoomState();
        if (!localSpectatorInPlaying && uiState.isJoinGamePending(now)) {
            clearPendingJoinGame();
            buttonStateUpdater.run();
        }
        if (uiState.pendingAction() == TdmRoomUiState.PendingAction.JOINING_GAME) {
            boolean leftRoom = roomState.joinedRoom() == null || roomState.joinedRoom().isBlank();
            boolean knownNotPlaying = roomStateNow != null && !"PLAYING".equals(roomStateNow);
            if (leftRoom || knownNotPlaying) {
                clearPendingAction();
                showRoomNotice("当前阶段不可加入游戏", CodTheme.TEXT_SECONDARY);
                buttonStateUpdater.run();
            }
        }
        if (uiState.shouldAutoExecuteLeave(now)) {
            executeLeaveRoom();
            return;
        }
        if (localSpectatorInPlaying && uiState.shouldAutoExecuteJoinGame(now)) {
            if (isMidMatchJoinTemporarilyDisabled()) {
                clearPendingJoinGame();
                showRoomNotice(Component.translatable("message.codpattern.room.mid_join_disabled").getString(), CodTheme.TEXT_SECONDARY);
                buttonStateUpdater.run();
                return;
            }
            executeJoinGameFromSpectator();
            return;
        }
        if (uiState.isLeavePending(now)) {
            buttonStateUpdater.run();
        }
        if (uiState.isJoinGamePending(now)) {
            buttonStateUpdater.run();
        }
        TdmRoomUiState.PendingAction expiredAction = uiState.consumeExpiredPendingAction(now);
        if (expiredAction != TdmRoomUiState.PendingAction.NONE) {
            String timeoutMessage = switch (expiredAction) {
                case JOINING -> "加入房间请求超时";
                case LEAVING -> "离开房间请求超时";
                case JOINING_GAME -> "加入游戏请求超时";
                default -> "";
            };
            if (!timeoutMessage.isEmpty()) {
                showRoomNotice(timeoutMessage, CodTheme.TEXT_DANGER);
            }
            buttonStateUpdater.run();
        }
    }

    public void requestRoomList() {
        ModNetworkChannel.sendToServer(new RequestRoomListPacket());
    }

    public void joinSelectedRoom() {
        String selectedRoom = roomState.selectedRoom();
        if (selectedRoom == null || selectedRoom.isEmpty()) {
            return;
        }
        if (uiState.hasPendingAction()) {
            return;
        }
        clearPendingLeave();
        clearPendingJoinGame();
        clearRoomNotice();
        ModNetworkChannel.sendToServer(new JoinRoomPacket(selectedRoom, null));
        startPendingAction(TdmRoomUiState.PendingAction.JOINING, selectedRoom);
        buttonStateUpdater.run();
    }

    public void selectTeam(String teamName) {
        if (roomState.joinedRoom() == null) {
            return;
        }
        if (!TdmRoomStateEvaluator.isTeamSwitchAllowed(currentRoomState()) && !isLocalSpectatorInPlaying()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.translatable("message.codpattern.game.team_switch_locked"));
            }
            return;
        }
        if (!TdmTeamNames.KORTAC.equals(teamName) && !TdmTeamNames.SPECGRU.equals(teamName)) {
            return;
        }
        ModNetworkChannel.sendToServer(new SelectTeamPacket(roomState.joinedRoom(), teamName));
    }

    public void leaveRoom() {
        if (roomState.joinedRoom() == null) {
            return;
        }
        if (uiState.hasPendingAction()) {
            return;
        }
        clearPendingJoinGame();
        if (isLeavePending()) {
            clearPendingLeave();
            buttonStateUpdater.run();
            return;
        }
        uiState.startLeaveConfirm(System.currentTimeMillis(), LEAVE_CONFIRM_WINDOW_MS);
        buttonStateUpdater.run();
    }

    public void toggleReady() {
        if (roomState.joinedRoom() == null || uiState.hasPendingAction()) {
            return;
        }
        if (isLocalSpectatorInPlaying()) {
            if (isMidMatchJoinTemporarilyDisabled()) {
                clearPendingJoinGame();
                showRoomNotice(Component.translatable("message.codpattern.room.mid_join_disabled").getString(), CodTheme.TEXT_SECONDARY);
                buttonStateUpdater.run();
                return;
            }
            handleJoinGameButton();
            return;
        }
        if (!"WAITING".equals(currentRoomState())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        UUID localPlayerId = mc.player == null ? null : mc.player.getUUID();
        boolean ready = TdmRoomStateEvaluator.isLocalPlayerReady(localPlayerId, roomState.teamPlayers());
        ModNetworkChannel.sendToServer(new SetReadyStatePacket(!ready));
    }

    public void voteStart() {
        ModNetworkChannel.sendToServer(new VoteStartPacket());
    }

    public void voteEnd() {
        ModNetworkChannel.sendToServer(new VoteEndPacket());
    }

    public void updateRoomList(Map<String, TdmRoomData> rooms) {
        roomState.setRooms(rooms);
        if (roomState.joinedRoom() != null
                && !roomState.rooms().containsKey(roomState.joinedRoom())
                && uiState.pendingAction() != TdmRoomUiState.PendingAction.JOINING) {
            roomState.setJoinedRoom(null);
            roomState.clearTeamPlayers();
        }
        buttonStateUpdater.run();
    }

    public void updatePlayerList(String mapName, Map<String, List<PlayerInfo>> teamPlayers) {
        roomState.setJoinedRoom(mapName);
        roomState.setTeamPlayers(teamPlayers);
        if (uiState.pendingAction() == TdmRoomUiState.PendingAction.JOINING
                && mapName != null
                && mapName.equals(uiState.pendingRoomName())) {
            clearPendingAction();
        } else if (uiState.pendingAction() == TdmRoomUiState.PendingAction.JOINING_GAME) {
            UUID localPlayerId = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
            if (TdmRoomStateEvaluator.isLocalPlayerInTeamRoster(localPlayerId, roomState.teamPlayers())) {
                clearPendingAction();
            }
        }
        buttonStateUpdater.run();
    }

    public void setJoinedRoom(String roomName) {
        roomState.setJoinedRoom(roomName);
        roomState.setSelectedRoom(roomName);
        clearPendingJoinGame();
        clearPendingAction();
        buttonStateUpdater.run();
    }

    public void handleJoinResult(boolean success, String mapName, String reasonCode, String reasonMessage) {
        if (uiState.pendingAction() == TdmRoomUiState.PendingAction.JOINING) {
            clearPendingAction();
        }
        clearPendingJoinGame();
        if (success) {
            roomState.clearTeamPlayers();
            roomState.setJoinedRoom(mapName);
            roomState.setSelectedRoom(mapName);
            clearRoomNotice();
            buttonStateUpdater.run();
            return;
        }
        String message = (reasonMessage == null || reasonMessage.isBlank())
                ? "加入房间失败: " + (reasonCode == null ? "UNKNOWN" : reasonCode)
                : "加入房间失败: " + reasonMessage;
        showRoomNotice(message, CodTheme.TEXT_DANGER);
        buttonStateUpdater.run();
    }

    public void handleLeaveResult(boolean success, String roomName, String reasonCode, String reasonMessage) {
        if (uiState.pendingAction() == TdmRoomUiState.PendingAction.LEAVING) {
            clearPendingAction();
        }
        clearPendingJoinGame();
        if (success) {
            roomState.setJoinedRoom(null);
            roomState.clearTeamPlayers();
            ClientTdmState.resetMatchState();
            showRoomNotice("已离开房间", CodTheme.TEXT_SECONDARY);
            if (roomName != null && roomName.equals(roomState.selectedRoom())) {
                roomState.setSelectedRoom(roomName);
            }
        } else {
            String message = (reasonMessage == null || reasonMessage.isBlank())
                    ? "离开房间失败: " + (reasonCode == null ? "UNKNOWN" : reasonCode)
                    : "离开房间失败: " + reasonMessage;
            showRoomNotice(message, CodTheme.TEXT_DANGER);
        }
        buttonStateUpdater.run();
    }

    public void handleJoinGameResult(boolean success, long requestId, String mapName, String reasonCode, String reasonMessage) {
        if (uiState.pendingAction() != TdmRoomUiState.PendingAction.JOINING_GAME) {
            return;
        }
        if (requestId <= 0L || requestId != uiState.pendingJoinGameRequestId()) {
            return;
        }
        String pendingRoom = uiState.pendingRoomName();
        if (pendingRoom != null && mapName != null && !mapName.isBlank() && !pendingRoom.equals(mapName)) {
            return;
        }
        clearPendingAction();
        clearPendingJoinGame();
        if (success) {
            clearRoomNotice();
            buttonStateUpdater.run();
            return;
        }
        String message = (reasonMessage == null || reasonMessage.isBlank())
                ? "加入游戏失败: " + (reasonCode == null ? "UNKNOWN" : reasonCode)
                : "加入游戏失败: " + reasonMessage;
        showRoomNotice(message, CodTheme.TEXT_DANGER);
        buttonStateUpdater.run();
    }

    public String currentRoomState() {
        return roomState.currentRoomState(ClientTdmState.currentPhase());
    }

    public boolean hasPendingAction() {
        return uiState.hasPendingAction();
    }

    public TdmRoomUiState.PendingAction pendingAction() {
        return uiState.pendingAction();
    }

    public boolean isLeavePending() {
        return uiState.isLeavePending(System.currentTimeMillis());
    }

    public int leaveSecondsRemaining() {
        return uiState.leaveSecondsRemaining(System.currentTimeMillis());
    }

    public boolean isJoinGamePending() {
        return uiState.isJoinGamePending(System.currentTimeMillis());
    }

    public int joinGameSecondsRemaining() {
        return uiState.joinGameSecondsRemaining(System.currentTimeMillis());
    }

    public boolean isLocalSpectatorInPlaying() {
        UUID localPlayerId = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
        return TdmRoomStateEvaluator.isLocalSpectatorInPlaying(
                localPlayerId,
                roomState.joinedRoom() != null,
                currentRoomState(),
                roomState.teamPlayers());
    }

    public boolean hasRoomNotice() {
        return uiState.hasNotice();
    }

    public String roomNoticeText() {
        return uiState.roomNoticeText();
    }

    public int roomNoticeColor() {
        return uiState.roomNoticeColor();
    }

    public void reset() {
        uiState.reset();
    }

    private void executeLeaveRoom() {
        clearPendingLeave();
        ModNetworkChannel.sendToServer(new LeaveRoomPacket());
        startPendingAction(TdmRoomUiState.PendingAction.LEAVING, roomState.joinedRoom());
        buttonStateUpdater.run();
    }

    private void handleJoinGameButton() {
        if (uiState.hasPendingAction()) {
            return;
        }
        if (!isLocalSpectatorInPlaying()) {
            clearPendingJoinGame();
            buttonStateUpdater.run();
            return;
        }
        if (isJoinGamePending()) {
            clearPendingJoinGame();
            buttonStateUpdater.run();
            return;
        }
        uiState.startJoinGameConfirm(System.currentTimeMillis(), JOIN_GAME_CONFIRM_WINDOW_MS);
        buttonStateUpdater.run();
    }

    private void executeJoinGameFromSpectator() {
        clearPendingJoinGame();
        if (isMidMatchJoinTemporarilyDisabled()) {
            showRoomNotice(Component.translatable("message.codpattern.room.mid_join_disabled").getString(), CodTheme.TEXT_SECONDARY);
            buttonStateUpdater.run();
            return;
        }
        String joinedRoom = roomState.joinedRoom();
        if (joinedRoom == null || joinedRoom.isBlank()) {
            buttonStateUpdater.run();
            return;
        }
        if (!isLocalSpectatorInPlaying()) {
            showRoomNotice("当前阶段不可加入游戏", CodTheme.TEXT_DANGER);
            buttonStateUpdater.run();
            return;
        }
        long requestId = consumeJoinGameRequestId();
        uiState.setPendingJoinGameRequestId(requestId);
        ModNetworkChannel.sendToServer(new JoinGameFromSpectatorPacket(joinedRoom, requestId));
        startPendingAction(TdmRoomUiState.PendingAction.JOINING_GAME, joinedRoom);
        buttonStateUpdater.run();
    }

    private long consumeJoinGameRequestId() {
        long current = nextJoinGameRequestId;
        nextJoinGameRequestId = current == Long.MAX_VALUE ? MIN_JOIN_GAME_REQUEST_ID : current + 1L;
        return current;
    }

    private void startPendingAction(TdmRoomUiState.PendingAction action, String roomName) {
        uiState.startPendingAction(action, roomName, ACTION_ACK_TIMEOUT_MS, System.currentTimeMillis());
    }

    private void clearPendingAction() {
        uiState.clearPendingAction();
    }

    private void clearPendingLeave() {
        uiState.clearLeaveConfirm();
    }

    private void clearPendingJoinGame() {
        uiState.clearJoinGameConfirm();
    }

    private void showRoomNotice(String message, int color) {
        showRoomNotice(message, color, ROOM_NOTICE_DURATION_MS);
    }

    private void showRoomNotice(String message, int color, long durationMs) {
        uiState.showNotice(message, color, durationMs, System.currentTimeMillis());
    }

    private void clearRoomNotice() {
        uiState.clearNotice();
    }

    private boolean isMidMatchJoinTemporarilyDisabled() {
        return true;
    }
}
