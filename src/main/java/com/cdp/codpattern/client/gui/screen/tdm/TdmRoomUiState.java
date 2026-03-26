package com.cdp.codpattern.client.gui.screen.tdm;

import com.cdp.codpattern.client.gui.CodTheme;

public final class TdmRoomUiState {
    public enum PendingAction {
        NONE,
        JOINING,
        LEAVING
    }

    private long pendingLeaveDeadlineMs = 0L;
    private long pendingActionDeadlineMs = 0L;
    private PendingAction pendingAction = PendingAction.NONE;
    private String pendingRoomName = null;
    private String roomNoticeText = "";
    private int roomNoticeColor = CodTheme.TEXT_SECONDARY;
    private long roomNoticeExpireAtMs = 0L;

    public PendingAction pendingAction() {
        return pendingAction;
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
        pendingLeaveDeadlineMs = nowMs + windowMs;
    }

    public void clearLeaveConfirm() {
        pendingLeaveDeadlineMs = 0L;
    }

    public boolean isLeavePending(long nowMs) {
        return pendingLeaveDeadlineMs > 0L && nowMs < pendingLeaveDeadlineMs;
    }

    public boolean shouldAutoExecuteLeave(long nowMs) {
        return pendingLeaveDeadlineMs > 0L && nowMs >= pendingLeaveDeadlineMs;
    }

    public int leaveSecondsRemaining(long nowMs) {
        long remainMs = pendingLeaveDeadlineMs - nowMs;
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
        clearLeaveConfirm();
        clearPendingAction();
        clearNotice();
    }
}
