package com.cdp.codpattern.app.match.model;

public record JoinRoomResult(
        boolean success,
        RoomId roomId,
        String code,
        String message
) {
    public JoinRoomResult {
        code = code == null ? "" : code;
        message = message == null ? "" : message;
    }

    public static JoinRoomResult success(RoomId roomId, String code) {
        return new JoinRoomResult(true, roomId, code, "");
    }

    public static JoinRoomResult failure(RoomId roomId, String code, String message) {
        return new JoinRoomResult(false, roomId, code, message);
    }
}
