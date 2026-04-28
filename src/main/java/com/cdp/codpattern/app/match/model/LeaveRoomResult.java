package com.cdp.codpattern.app.match.model;

public record LeaveRoomResult(
        boolean success,
        RoomId roomId,
        String code,
        String message
) {
    public LeaveRoomResult {
        code = code == null ? "" : code;
        message = message == null ? "" : message;
    }

    public static LeaveRoomResult success(RoomId roomId, String code) {
        return new LeaveRoomResult(true, roomId, code, "");
    }

    public static LeaveRoomResult failure(RoomId roomId, String code, String message) {
        return new LeaveRoomResult(false, roomId, code, message);
    }
}
