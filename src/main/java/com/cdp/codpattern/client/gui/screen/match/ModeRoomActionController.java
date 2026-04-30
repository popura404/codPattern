package com.cdp.codpattern.client.gui.screen.match;

import com.cdp.codpattern.app.match.model.ModeCapability;
import com.cdp.codpattern.client.ClientMatchState;
import com.cdp.codpattern.client.gui.CodTheme;
import com.cdp.codpattern.client.network.ModeRoomClientPackets;
import com.cdp.codpattern.fpsmatch.room.PlayerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ModeRoomActionController {
    private static final long LEAVE_CONFIRM_WINDOW_MS = 3000L;
    private static final long ACTION_ACK_TIMEOUT_MS = 5000L;
    private static final long ROOM_NOTICE_DURATION_MS = 4500L;
    private static final long PREVIEW_ROSTER_REFRESH_MS = 2500L;
    private static final long PREVIEW_ROSTER_RETRY_MS = 900L;

    private final ModeRoomSessionState roomState;
    private final ModeRoomUiState uiState;
    private final Runnable buttonStateUpdater;
    private String queuedJoinRoomAfterLeave = null;
    private String switchOriginRoom = null;
    private String switchTargetRoom = null;

    public ModeRoomActionController(ModeRoomSessionState roomState, ModeRoomUiState uiState, Runnable buttonStateUpdater) {
        this.roomState = roomState;
        this.uiState = uiState;
        this.buttonStateUpdater = buttonStateUpdater;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        uiState.expireNotice(now);

        ModeRoomUiState.ConfirmAction expiredConfirm = uiState.consumeExpiredConfirm(now);
        if (expiredConfirm != ModeRoomUiState.ConfirmAction.NONE) {
            executeLeaveRoom();
            return;
        }
        if (uiState.hasConfirmPending(now)) {
            buttonStateUpdater.run();
        }

        requestSelectedPreviewRosterIfNeeded(false);

        ModeRoomUiState.PendingAction expiredAction = uiState.consumeExpiredPendingAction(now);
        if (expiredAction != ModeRoomUiState.PendingAction.NONE) {
            String timeoutMessage = switch (expiredAction) {
                case JOINING -> Component.translatable("screen.codpattern.room.notice.timeout.join_room").getString();
                case LEAVING -> Component.translatable("screen.codpattern.room.notice.timeout.leave_room").getString();
                default -> "";
            };
            if (!timeoutMessage.isEmpty()) {
                showRoomNotice(timeoutMessage, CodTheme.TEXT_DANGER);
            }
            buttonStateUpdater.run();
        }
    }

    public void requestRoomList() {
        roomState.lobbySummaryState().beginLoading();
        ModeRoomClientPackets.subscribeRoomList();
    }

    public void unsubscribeRoomList() {
        ModeRoomClientPackets.unsubscribeRoomList();
    }

    public void joinSelectedRoom() {
        String selectedRoom = roomState.selectedRoom();
        if (selectedRoom == null || selectedRoom.isEmpty() || uiState.hasPendingAction()) {
            return;
        }
        clearPendingConfirm();
        clearSwitchFlow();
        clearRoomNotice();
        ModeRoomClientPackets.joinRoom(selectedRoom);
        startPendingAction(ModeRoomUiState.PendingAction.JOINING, selectedRoom);
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

        long now = System.currentTimeMillis();
        if (uiState.isSwitchPending(now) && targetRoom.equals(uiState.confirmTargetRoom())) {
            clearPendingConfirm();
            clearSwitchFlow();
            buttonStateUpdater.run();
            return;
        }

        clearPendingConfirm();
        clearRoomNotice();
        startSwitchFlow(joinedRoom, targetRoom);
        uiState.startSwitchConfirm(targetRoom, now, LEAVE_CONFIRM_WINDOW_MS);
        buttonStateUpdater.run();
    }

    public void selectTeam(String teamName) {
        if (roomState.joinedRoom() == null) {
            return;
        }
        if (!joinedRoomHasCapability(ModeCapability.TEAM_SELECTION)) {
            return;
        }
        if (!ModeRoomStateEvaluator.isTeamSwitchAllowed(currentRoomState())) {
            showRoomNotice(Component.translatable("message.codpattern.game.team_switch_locked").getString(),
                    CodTheme.TEXT_DANGER);
            return;
        }
        if (teamName == null || teamName.isBlank()) {
            return;
        }
        ModeRoomClientPackets.selectTeam(roomState.joinedRoom(), teamName);
    }

    public void leaveRoom() {
        if (roomState.joinedRoom() == null || uiState.hasPendingAction()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (uiState.isLeavePending(now)) {
            clearPendingConfirm();
            clearSwitchFlow();
            buttonStateUpdater.run();
            return;
        }
        clearPendingConfirm();
        clearSwitchFlow();
        uiState.startLeaveConfirm(now, LEAVE_CONFIRM_WINDOW_MS);
        buttonStateUpdater.run();
    }

    public void toggleReady() {
        if (roomState.joinedRoom() == null || uiState.hasPendingAction() || !"WAITING".equals(currentRoomState())) {
            return;
        }
        if (!joinedRoomHasCapability(ModeCapability.READY_STATE)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        UUID localPlayerId = mc.player == null ? null : mc.player.getUUID();
        boolean ready = ModeRoomStateEvaluator.isLocalPlayerReady(localPlayerId, roomState.teamPlayers());
        ModeRoomClientPackets.setReadyState(!ready);
    }

    public void voteStart() {
        if (!joinedRoomHasCapability(ModeCapability.START_VOTE)) {
            return;
        }
        ModeRoomClientPackets.voteStart();
    }

    public void voteEnd() {
        if (!joinedRoomHasCapability(ModeCapability.END_VOTE)) {
            return;
        }
        ModeRoomClientPackets.voteEnd();
    }

    public void updateRoomList(long snapshotVersion, Map<String, ModeRoomData> rooms) {
        long now = System.currentTimeMillis();
        roomState.lobbySummaryState().applySnapshot(rooms, snapshotVersion, now);
        roomState.selectedRoomPreviewState().syncFromLobby(roomState.lobbySummaryState(), now);
        if (uiState.isSwitchPending(now)) {
            String pendingTarget = uiState.confirmTargetRoom();
            if (pendingTarget == null || !roomState.rooms().containsKey(pendingTarget)) {
                clearPendingConfirm();
                clearSwitchFlow();
                showRoomNotice(
                        Component.translatable("screen.codpattern.room.error.map_not_found").getString(),
                        CodTheme.TEXT_DANGER);
            }
        }
        String selectedRoom = roomState.selectedRoom();
        if (selectedRoom != null
                && !selectedRoom.isBlank()
                && !roomState.rooms().containsKey(selectedRoom)
                && !selectedRoom.equals(roomState.joinedRoom())) {
            roomState.setSelectedRoom(null);
        }
        requestSelectedPreviewRosterIfNeeded(false);
        buttonStateUpdater.run();
    }

    public void updatePlayerList(String roomKey, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
        roomState.setJoinedRoom(roomKey);
        roomState.refreshJoinedRoomLiveState();
        if (uiState.pendingAction() == ModeRoomUiState.PendingAction.JOINING
                && roomKey != null
                && roomKey.equals(uiState.pendingRoomName())) {
            clearPendingAction();
        }
        buttonStateUpdater.run();
    }

    public void updatePlayerDelta(String roomKey, int rosterVersion) {
        if (roomKey == null || !roomKey.equals(roomState.joinedRoom())) {
            return;
        }
        roomState.refreshJoinedRoomLiveState();
        buttonStateUpdater.run();
    }

    public void updatePreviewPlayerList(String roomKey, int rosterVersion, Map<String, List<PlayerInfo>> teamPlayers) {
        roomState.updateSelectedRoomPreviewRoster(roomKey, rosterVersion, teamPlayers);
        buttonStateUpdater.run();
    }

    public void setJoinedRoom(String roomKey) {
        roomState.setJoinedRoom(roomKey);
        roomState.setSelectedRoom(roomKey);
        roomState.selectedRoomPreviewState().clearRoster();
        roomState.refreshJoinedRoomLiveState();
        clearSwitchFlow();
        clearPendingAction();
        buttonStateUpdater.run();
    }

    public void handleJoinResult(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        String pendingRoom = uiState.pendingRoomName();
        boolean switchJoinAttempt = isSwitchJoinAttempt(pendingRoom);
        if (uiState.pendingAction() == ModeRoomUiState.PendingAction.JOINING) {
            clearPendingAction();
        }
        if (success) {
            roomState.clearTeamPlayers();
            roomState.setJoinedRoom(roomKey);
            roomState.setSelectedRoom(roomKey);
            roomState.refreshJoinedRoomLiveState();
            clearRoomNotice();
            if (switchJoinAttempt && roomKey != null && !roomKey.isBlank()) {
                showRoomNotice(
                        Component.translatable("screen.codpattern.room.switch_success", roomLabel(roomKey))
                                .getString(),
                        CodTheme.TEXT_SECONDARY);
            }
            clearSwitchFlow();
            buttonStateUpdater.run();
            return;
        }
        String reason = resolveReasonText(reasonCode, reasonMessage);
        String message = switchJoinAttempt
                ? Component.translatable("screen.codpattern.room.switch_failed_join_retry", reason).getString()
                : Component.translatable("screen.codpattern.room.error.join_failed", reason).getString();
        clearSwitchFlow();
        showRoomNotice(message, CodTheme.TEXT_DANGER);
        buttonStateUpdater.run();
    }

    public void handleLeaveResult(boolean success, String roomKey, String reasonCode, String reasonMessage) {
        if (uiState.pendingAction() == ModeRoomUiState.PendingAction.LEAVING) {
            clearPendingAction();
        }
        String queuedJoinTarget = queuedJoinRoomAfterLeave;
        queuedJoinRoomAfterLeave = null;
        if (success) {
            roomState.setJoinedRoom(null);
            roomState.clearTeamPlayers();
            ClientMatchState.resetMatchState();

            if (queuedJoinTarget != null && !queuedJoinTarget.isBlank()) {
                roomState.setSelectedRoom(queuedJoinTarget);
                clearRoomNotice();
                ModeRoomClientPackets.joinRoom(queuedJoinTarget);
                startPendingAction(ModeRoomUiState.PendingAction.JOINING, queuedJoinTarget);
                buttonStateUpdater.run();
                return;
            }

            showRoomNotice(Component.translatable("screen.codpattern.room.notice.left_room").getString(),
                    CodTheme.TEXT_SECONDARY);
            clearSwitchFlow();
            if (roomKey != null && roomKey.equals(roomState.selectedRoom())) {
                roomState.setSelectedRoom(roomKey);
            }
        } else {
            String reason = resolveReasonText(reasonCode, reasonMessage);
            String message = hasActiveSwitchFlow()
                    ? Component.translatable("screen.codpattern.room.switch_failed_leave_retry", reason).getString()
                    : Component.translatable("screen.codpattern.room.error.leave_failed", reason).getString();
            clearSwitchFlow();
            showRoomNotice(message, CodTheme.TEXT_DANGER);
        }
        buttonStateUpdater.run();
    }

    public String currentRoomState() {
        return roomState.currentRoomState(ClientMatchState.currentPhase());
    }

    public boolean hasPendingAction() {
        return uiState.hasPendingAction();
    }

    public boolean isLeavePending() {
        return uiState.isLeavePending(System.currentTimeMillis());
    }

    public boolean hasConfirmPending() {
        return uiState.hasConfirmPending(System.currentTimeMillis());
    }

    public boolean isPreviewingOtherRoom() {
        String joinedRoom = roomState.joinedRoom();
        String selectedRoom = roomState.selectedRoom();
        return joinedRoom != null
                && !joinedRoom.isBlank()
                && selectedRoom != null
                && !selectedRoom.isBlank()
                && !selectedRoom.equals(joinedRoom)
                && roomState.selectedRoomPreviewState().hasPreview();
    }

    private boolean joinedRoomHasCapability(ModeCapability capability) {
        String joinedRoom = roomState.joinedRoom();
        if (joinedRoom == null || joinedRoom.isBlank()) {
            return false;
        }
        ModeRoomData roomData = roomState.rooms().get(joinedRoom);
        return roomData == null || roomData.hasCapability(capability);
    }

    public String pendingSwitchTargetRoom() {
        return uiState.confirmTargetRoom();
    }

    public String confirmHintText() {
        long now = System.currentTimeMillis();
        if (!uiState.hasConfirmPending(now)) {
            return "";
        }
        int secondsRemaining = uiState.confirmSecondsRemaining(now);
        return switch (uiState.confirmAction()) {
            case LEAVE_ROOM -> Component.translatable(
                    "screen.codpattern.room.leave_room_pending",
                    secondsRemaining).getString();
            case SWITCH_ROOM -> Component.translatable(
                    "screen.codpattern.room.switch_room_pending",
                    roomLabel(uiState.confirmTargetRoom()),
                    secondsRemaining).getString();
            default -> "";
        };
    }

    public int confirmHintColor() {
        return uiState.confirmAction() == ModeRoomUiState.ConfirmAction.SWITCH_ROOM
                ? CodTheme.SELECTED_BORDER
                : 0xFFFFD75E;
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

    public void selectRoom(String roomName) {
        long now = System.currentTimeMillis();
        if (uiState.isSwitchPending(now)) {
            String pendingTarget = uiState.confirmTargetRoom();
            if (roomName == null || !roomName.equals(pendingTarget)) {
                clearPendingConfirm();
                clearSwitchFlow();
            }
        }
        roomState.setSelectedRoom(roomName);
        requestSelectedPreviewRosterIfNeeded(true);
        buttonStateUpdater.run();
    }

    private void executeLeaveRoom() {
        clearPendingConfirm();
        ModeRoomClientPackets.leaveRoom();
        startPendingAction(ModeRoomUiState.PendingAction.LEAVING, roomState.joinedRoom());
        buttonStateUpdater.run();
    }

    private void startPendingAction(ModeRoomUiState.PendingAction action, String roomName) {
        uiState.startPendingAction(action, roomName, ACTION_ACK_TIMEOUT_MS, System.currentTimeMillis());
    }

    private void clearPendingAction() {
        uiState.clearPendingAction();
    }

    private void clearPendingConfirm() {
        uiState.clearConfirm();
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

    private void requestSelectedPreviewRosterIfNeeded(boolean force) {
        String selectedRoom = roomState.selectedRoom();
        String joinedRoom = roomState.joinedRoom();
        SelectedRoomPreviewState previewState = roomState.selectedRoomPreviewState();
        if (selectedRoom == null || selectedRoom.isBlank() || selectedRoom.equals(joinedRoom)) {
            previewState.clearRoster();
            return;
        }
        ModeRoomData summary = previewState.summarySnapshot();
        long now = System.currentTimeMillis();
        if (!force && !previewState.shouldRequestRoster(
                now,
                summary == null ? -1 : summary.playerCount,
                PREVIEW_ROSTER_REFRESH_MS,
                PREVIEW_ROSTER_RETRY_MS)) {
            return;
        }
        roomState.beginSelectedRoomPreviewRosterLoad();
        ModeRoomClientPackets.requestRoomPreviewRoster(selectedRoom);
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
                case "MAP_NOT_FOUND" -> Component.translatable("screen.codpattern.room.error.map_not_found")
                        .getString();
                case "PHASE_LOCKED" -> Component.translatable("screen.codpattern.room.error.phase_locked")
                        .getString();
                case "TEAM_NOT_FOUND" -> Component.translatable("screen.codpattern.room.error.team_not_found")
                        .getString();
                case "TEAM_FULL" -> Component.translatable("screen.codpattern.room.error.team_full").getString();
                case "TEAM_BALANCE_EXCEEDED" -> Component.translatable(
                        "screen.codpattern.room.error.team_balance_exceeded").getString();
                case "NOT_IN_ROOM" -> Component.translatable("screen.codpattern.room.error.not_in_room")
                        .getString();
                case "UNKNOWN" -> Component.translatable("screen.codpattern.room.error.unknown").getString();
                default -> {
                    if (reasonMessage != null && !reasonMessage.isBlank()) {
                        yield reasonMessage;
                    }
                    yield Component.translatable("screen.codpattern.room.error.unknown").getString();
                }
            };
        }
        if (reasonMessage != null && !reasonMessage.isBlank()) {
            return reasonMessage;
        }
        return Component.translatable("screen.codpattern.room.error.unknown").getString();
    }

    private String roomLabel(String roomKey) {
        ModeRoomData room = roomKey == null ? null : roomState.rooms().get(roomKey);
        if (room == null || room.mapName == null || room.mapName.isBlank()) {
            return roomKey == null ? "" : roomKey;
        }
        return room.mapName;
    }

}
