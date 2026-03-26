package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.client.ClientTdmState;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
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
    private static final long ACTION_ACK_TIMEOUT_MS = 5000L;
    private static final long ROOM_NOTICE_DURATION_MS = 4500L;

    private final TdmRoomSessionState roomState;
    private final TdmRoomUiState uiState;
    private final Runnable buttonStateUpdater;
    private String queuedJoinRoomAfterLeave = null;
    private String switchOriginRoom = null;
    private String switchTargetRoom = null;

    public TdmRoomActionController(TdmRoomSessionState roomState, TdmRoomUiState uiState, Runnable buttonStateUpdater) {
        this.roomState = roomState;
        this.uiState = uiState;
        this.buttonStateUpdater = buttonStateUpdater;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        uiState.expireNotice(now);

        if (uiState.shouldAutoExecuteLeave(now)) {
            executeLeaveRoom();
            return;
        }
        if (uiState.isLeavePending(now)) {
            buttonStateUpdater.run();
        }

        TdmRoomUiState.PendingAction expiredAction = uiState.consumeExpiredPendingAction(now);
        if (expiredAction != TdmRoomUiState.PendingAction.NONE) {
            String timeoutMessage = switch (expiredAction) {
                case JOINING -> Component.translatable("screen.codpattern.tdm_room.notice.timeout.join_room").getString();
                case LEAVING -> Component.translatable("screen.codpattern.tdm_room.notice.timeout.leave_room").getString();
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
        if (selectedRoom == null || selectedRoom.isEmpty() || uiState.hasPendingAction()) {
            return;
        }
        clearPendingLeave();
        clearSwitchFlow();
        clearRoomNotice();
        ModNetworkChannel.sendToServer(new JoinRoomPacket(selectedRoom, null));
        startPendingAction(TdmRoomUiState.PendingAction.JOINING, selectedRoom);
        buttonStateUpdater.run();
    }

    public void switchToRoom(String targetRoom) {
        if (targetRoom == null || targetRoom.isBlank() || uiState.hasPendingAction()) {
            return;
        }
        String joinedRoom = roomState.joinedRoom();
        if (joinedRoom == null || joinedRoom.isBlank()) {
            roomState.setSelectedRoom(targetRoom);
            joinSelectedRoom();
            return;
        }
        if (joinedRoom.equals(targetRoom)) {
            return;
        }

        clearPendingLeave();
        clearRoomNotice();
        startSwitchFlow(joinedRoom, targetRoom);
        executeLeaveRoom();
    }

    public void selectTeam(String teamName) {
        if (roomState.joinedRoom() == null) {
            return;
        }
        if (!TdmRoomStateEvaluator.isTeamSwitchAllowed(currentRoomState())) {
            showRoomNotice(Component.translatable("message.codpattern.game.team_switch_locked").getString(),
                    CodTheme.TEXT_DANGER);
            return;
        }
        if (teamName == null || teamName.isBlank()) {
            return;
        }
        ModNetworkChannel.sendToServer(new SelectTeamPacket(roomState.joinedRoom(), teamName));
    }

    public void leaveRoom() {
        if (roomState.joinedRoom() == null || uiState.hasPendingAction()) {
            return;
        }
        clearSwitchFlow();
        if (isLeavePending()) {
            clearPendingLeave();
            buttonStateUpdater.run();
            return;
        }
        uiState.startLeaveConfirm(System.currentTimeMillis(), LEAVE_CONFIRM_WINDOW_MS);
        buttonStateUpdater.run();
    }

    public void toggleReady() {
        if (roomState.joinedRoom() == null || uiState.hasPendingAction() || !"WAITING".equals(currentRoomState())) {
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

    public void updatePlayerList(String roomKey, Map<String, List<PlayerInfo>> teamPlayers) {
        roomState.setJoinedRoom(roomKey);
        roomState.setTeamPlayers(teamPlayers);
        if (uiState.pendingAction() == TdmRoomUiState.PendingAction.JOINING
                && roomKey != null
                && roomKey.equals(uiState.pendingRoomName())) {
            clearPendingAction();
        }
        buttonStateUpdater.run();
    }

    public void setJoinedRoom(String roomKey) {
        roomState.setJoinedRoom(roomKey);
        roomState.setSelectedRoom(roomKey);
        clearSwitchFlow();
        clearPendingAction();
        buttonStateUpdater.run();
    }

    public void handleJoinResult(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        String pendingRoom = uiState.pendingRoomName();
        boolean switchJoinAttempt = isSwitchJoinAttempt(pendingRoom);
        if (uiState.pendingAction() == TdmRoomUiState.PendingAction.JOINING) {
            clearPendingAction();
        }
        if (success) {
            roomState.clearTeamPlayers();
            roomState.setJoinedRoom(roomKey);
            roomState.setSelectedRoom(roomKey);
            clearRoomNotice();
            if (switchJoinAttempt && roomKey != null && !roomKey.isBlank()) {
                showRoomNotice(
                        Component.translatable("screen.codpattern.tdm_room.switch_success", roomLabel(roomKey))
                                .getString(),
                        CodTheme.TEXT_SECONDARY);
            }
            clearSwitchFlow();
            buttonStateUpdater.run();
            return;
        }
        String reason = resolveReasonText(reasonCode, reasonMessage);
        String message = switchJoinAttempt
                ? Component.translatable("screen.codpattern.tdm_room.switch_failed_join_retry", reason).getString()
                : Component.translatable("screen.codpattern.tdm_room.error.join_failed", reason).getString();
        clearSwitchFlow();
        showRoomNotice(message, CodTheme.TEXT_DANGER);
        buttonStateUpdater.run();
    }

    public void handleLeaveResult(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        if (uiState.pendingAction() == TdmRoomUiState.PendingAction.LEAVING) {
            clearPendingAction();
        }
        String queuedJoinTarget = queuedJoinRoomAfterLeave;
        queuedJoinRoomAfterLeave = null;
        if (success) {
            roomState.setJoinedRoom(null);
            roomState.clearTeamPlayers();
            ClientTdmState.resetMatchState();

            if (queuedJoinTarget != null && !queuedJoinTarget.isBlank()) {
                roomState.setSelectedRoom(queuedJoinTarget);
                clearRoomNotice();
                ModNetworkChannel.sendToServer(new JoinRoomPacket(queuedJoinTarget, null));
                startPendingAction(TdmRoomUiState.PendingAction.JOINING, queuedJoinTarget);
                buttonStateUpdater.run();
                return;
            }

            showRoomNotice(Component.translatable("screen.codpattern.tdm_room.notice.left_room").getString(),
                    CodTheme.TEXT_SECONDARY);
            clearSwitchFlow();
            if (roomKey != null && roomKey.equals(roomState.selectedRoom())) {
                roomState.setSelectedRoom(roomKey);
            }
        } else {
            String reason = resolveReasonText(reasonCode, reasonMessage);
            String message = hasActiveSwitchFlow()
                    ? Component.translatable("screen.codpattern.tdm_room.switch_failed_leave_retry", reason).getString()
                    : Component.translatable("screen.codpattern.tdm_room.error.leave_failed", reason).getString();
            clearSwitchFlow();
            showRoomNotice(message, CodTheme.TEXT_DANGER);
        }
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
        clearSwitchFlow();
        uiState.reset();
    }

    private void executeLeaveRoom() {
        clearPendingLeave();
        ModNetworkChannel.sendToServer(new LeaveRoomPacket());
        startPendingAction(TdmRoomUiState.PendingAction.LEAVING, roomState.joinedRoom());
        buttonStateUpdater.run();
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

    private void showRoomNotice(String message, int color) {
        showRoomNotice(message, color, ROOM_NOTICE_DURATION_MS);
    }

    private void showRoomNotice(String message, int color, long durationMs) {
        if (message == null || message.isBlank()) {
            return;
        }
        uiState.showNotice(message, color, durationMs, System.currentTimeMillis());
    }

    private void clearRoomNotice() {
        uiState.clearNotice();
    }

    private void startSwitchFlow(String originRoom, String targetRoom) {
        switchOriginRoom = originRoom;
        switchTargetRoom = targetRoom;
        queuedJoinRoomAfterLeave = targetRoom;
    }

    private void clearSwitchFlow() {
        switchOriginRoom = null;
        switchTargetRoom = null;
        queuedJoinRoomAfterLeave = null;
    }

    private boolean hasActiveSwitchFlow() {
        return switchTargetRoom != null && !switchTargetRoom.isBlank();
    }

    private boolean isSwitchJoinAttempt(String pendingRoom) {
        return hasActiveSwitchFlow()
                && pendingRoom != null
                && switchTargetRoom.equals(pendingRoom)
                && switchOriginRoom != null
                && !switchOriginRoom.equals(switchTargetRoom);
    }

    private String resolveReasonText(String reasonCode, String reasonMessage) {
        String code = reasonCode == null ? "" : reasonCode.trim();
        if (!code.isEmpty()) {
            return switch (code) {
                case "MAP_NOT_FOUND" -> Component.translatable("screen.codpattern.tdm_room.error.map_not_found")
                        .getString();
                case "PHASE_LOCKED" -> Component.translatable("screen.codpattern.tdm_room.error.phase_locked")
                        .getString();
                case "TEAM_NOT_FOUND" -> Component.translatable("screen.codpattern.tdm_room.error.team_not_found")
                        .getString();
                case "TEAM_FULL" -> Component.translatable("screen.codpattern.tdm_room.error.team_full").getString();
                case "TEAM_BALANCE_EXCEEDED" -> Component.translatable(
                        "screen.codpattern.tdm_room.error.team_balance_exceeded").getString();
                case "NOT_IN_ROOM" -> Component.translatable("screen.codpattern.tdm_room.error.not_in_room")
                        .getString();
                case "UNKNOWN" -> Component.translatable("screen.codpattern.tdm_room.error.unknown").getString();
                default -> {
                    if (reasonMessage != null && !reasonMessage.isBlank()) {
                        yield reasonMessage;
                    }
                    yield Component.translatable("screen.codpattern.tdm_room.error.unknown").getString();
                }
            };
        }
        if (reasonMessage != null && !reasonMessage.isBlank()) {
            return reasonMessage;
        }
        return Component.translatable("screen.codpattern.tdm_room.error.unknown").getString();
    }

    private String roomLabel(String roomKey) {
        TdmRoomData room = roomKey == null ? null : roomState.rooms().get(roomKey);
        if (room == null || room.mapName == null || room.mapName.isBlank()) {
            return roomKey == null ? "" : roomKey;
        }
        return room.mapName;
    }
}
