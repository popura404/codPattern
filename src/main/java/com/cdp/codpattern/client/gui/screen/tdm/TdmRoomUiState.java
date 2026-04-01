package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.client.gui.CodTheme;

public final class TdmRoomUiState {
    public enum ConfirmAction {
        NONE,
        LEAVE_ROOM,
        SWITCH_ROOM
    }

    public enum PendingAction {
        NONE,
        JOINING,
        LEAVING
    }

    private long pendingConfirmDeadlineMs = 0L;
    private ConfirmAction pendingConfirmAction = ConfirmAction.NONE;
    private String pendingConfirmTargetRoom = null;
    private long pendingActionDeadlineMs = 0L;
    private PendingAction pendingAction = PendingAction.NONE;
    private String pendingRoomName = null;
    private String roomNoticeText = "";
    private int roomNoticeColor = CodTheme.TEXT_SECONDARY;
    private long roomNoticeExpireAtMs = 0L;

    public PendingAction pendingAction() {
        return pendingAction;
    }

    public ConfirmAction confirmAction() {
        return pendingConfirmAction;
    }

    public String confirmTargetRoom() {
        return pendingConfirmTargetRoom;
    }

    public boolean hasPendingAction() {
        return pendingAction != PendingAction.NONE;
    }

    public String pendingRoomName() {
        return pendingRoomName;
    }

    public void startPendingAction(PendingAction action, String roomName, long timeoutMs, long nowMs) {
        pendingAction = action;
        pendingRoomName = roomName;
        pendingActionDeadlineMs = nowMs + timeoutMs;
    }

    public void clearPendingAction() {
        pendingAction = PendingAction.NONE;
        pendingRoomName = null;
        pendingActionDeadlineMs = 0L;
    }

    public PendingAction consumeExpiredPendingAction(long nowMs) {
        if (pendingAction == PendingAction.NONE || pendingActionDeadlineMs <= 0L || nowMs < pendingActionDeadlineMs) {
            return PendingAction.NONE;
        }
        PendingAction expired = pendingAction;
        clearPendingAction();
        return expired;
    }

    public void startLeaveConfirm(long nowMs, long windowMs) {
        startConfirm(ConfirmAction.LEAVE_ROOM, null, nowMs, windowMs);
    }

    public void startSwitchConfirm(String targetRoom, long nowMs, long windowMs) {
        startConfirm(ConfirmAction.SWITCH_ROOM, targetRoom, nowMs, windowMs);
    }

    public void clearConfirm() {
        pendingConfirmDeadlineMs = 0L;
        pendingConfirmAction = ConfirmAction.NONE;
        pendingConfirmTargetRoom = null;
    }

    public boolean hasConfirmPending(long nowMs) {
        return pendingConfirmAction != ConfirmAction.NONE
                && pendingConfirmDeadlineMs > 0L
                && nowMs < pendingConfirmDeadlineMs;
    }

    public boolean isLeavePending(long nowMs) {
        return pendingConfirmAction == ConfirmAction.LEAVE_ROOM && hasConfirmPending(nowMs);
    }

    public boolean isSwitchPending(long nowMs) {
        return pendingConfirmAction == ConfirmAction.SWITCH_ROOM && hasConfirmPending(nowMs);
    }

    public ConfirmAction consumeExpiredConfirm(long nowMs) {
        if (pendingConfirmAction == ConfirmAction.NONE
                || pendingConfirmDeadlineMs <= 0L
                || nowMs < pendingConfirmDeadlineMs) {
            return ConfirmAction.NONE;
        }
        ConfirmAction expired = pendingConfirmAction;
        clearConfirm();
        return expired;
    }

    public int confirmSecondsRemaining(long nowMs) {
        long remainMs = pendingConfirmDeadlineMs - nowMs;
        if (remainMs <= 0L) {
            return 0;
        }
        return (int) Math.ceil(remainMs / 1000.0);
    }

    public void showNotice(String message, int color, long durationMs, long nowMs) {
        if (message == null || message.isBlank()) {
            return;
        }
        roomNoticeText = message;
        roomNoticeColor = color;
        roomNoticeExpireAtMs = nowMs + Math.max(800L, durationMs);
    }

    public void expireNotice(long nowMs) {
        if (roomNoticeExpireAtMs > 0L && nowMs >= roomNoticeExpireAtMs) {
            clearNotice();
        }
    }

    public boolean hasNotice() {
        return !roomNoticeText.isBlank();
    }

    public String roomNoticeText() {
        return roomNoticeText;
    }

    public int roomNoticeColor() {
        return roomNoticeColor;
    }

    public void clearNotice() {
        roomNoticeText = "";
        roomNoticeExpireAtMs = 0L;
    }

    public void reset() {
        clearConfirm();
        clearPendingAction();
        clearNotice();
    }

    private void startConfirm(ConfirmAction action, String targetRoom, long nowMs, long windowMs) {
        pendingConfirmAction = action == null ? ConfirmAction.NONE : action;
        pendingConfirmTargetRoom = targetRoom;
        pendingConfirmDeadlineMs = nowMs + Math.max(0L, windowMs);
    }
}
